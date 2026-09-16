package com.training.trackplanner.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Comparison scope, not a serializer. Only explicitly selected user operations use it.
 * Small record edits read their affected date(s); bulk operations inspect their domain.
 * No-op SQL updates and bookkeeping-only timestamps do not create logical revisions.
 */
internal class CloudMutationScope(private val queries: List<SimpleSQLiteQuery>) {
    fun read(db: TrainingDatabase): List<List<List<String?>>> = queries.map { query ->
        db.openHelper.writableDatabase.query(query).use { cursor ->
            val columns = (0 until cursor.columnCount).filter { cursor.getColumnName(it) != "updatedAt" }
            buildList {
                while (cursor.moveToNext()) {
                    // app_meta is a mixed-authority table. Reuse its canonical portability policy.
                    val key = cursor.getColumnIndex("key")
                    if (key >= 0 && !BackupAppMetaPolicy.isSourceOverwriteAllowed(cursor.getString(key))) continue
                    add(columns.map { if (cursor.isNull(it)) null else cursor.getString(it) })
                }
            }
        }
    }

    companion object {
        fun tables(vararg names: String) = CloudMutationScope(names.map { SimpleSQLiteQuery("SELECT * FROM `$it` ORDER BY rowid") })
        fun workouts(dates: Collection<String>): CloudMutationScope {
            val unique = dates.distinct().sorted()
            if (unique.isEmpty()) return CloudMutationScope(emptyList())
            // Large date-range operations must stay below SQLite's bind-parameter limit.
            return CloudMutationScope(unique.chunked(400).flatMap { chunk ->
                val params = chunk.joinToString(",") { "?" }
                listOf(
                    SimpleSQLiteQuery("SELECT * FROM workout_entries WHERE date IN ($params) ORDER BY id", chunk.toTypedArray()),
                    SimpleSQLiteQuery("SELECT * FROM workout_sets WHERE entryId IN (SELECT id FROM workout_entries WHERE date IN ($params)) ORDER BY id", chunk.toTypedArray())
                )
            })
        }
        val PROGRAMS = tables("training_programs", "training_program_items", "training_program_item_sets",
            "training_program_tombstones", "program_progression_tracks", "program_progression_items",
            "program_applications", "program_workout_links", "program_prescription_sets", "progression_suggestions", "app_meta", "workout_entries", "workout_sets")
        val APPLICATION = tables("workout_entries", "workout_sets", "program_applications", "program_workout_links", "program_prescription_sets")
        val EXERCISES = tables("exercises", "runtime_exercise_metadata", "exercise_metadata_user_overrides",
            "exercise_training_role_relations", "exercise_program_slot_capability_relations")
        val DAILY = tables("daily_metrics", "daily_check_ins")
        val PROFILE = tables("initial_user_profiles")
        val SMASH = tables("smash_speed_records")
        val PORTABLE_META = tables("app_meta")
    }
}

private class LogicalCloudMutation(val db: TrainingDatabase) : AbstractCoroutineContextElement(Key) {
    var changed = false
    companion object Key : CoroutineContext.Key<LogicalCloudMutation>
}

/** Reuses Room transactions; nested logical operations on this DB coalesce to +1. */
internal suspend fun <T> TrainingDatabase.withCloudRevision(
    scope: CloudMutationScope,
    mutation: suspend () -> T
): T = withLocalDataGate { withTransaction {
    val parent = coroutineContext[LogicalCloudMutation]?.takeIf { it.db === this@withCloudRevision }
    suspend fun runOperation(owner: LogicalCloudMutation): T {
        val before = scope.read(this@withCloudRevision)
        val result = mutation()
        if (before != scope.read(this@withCloudRevision)) owner.changed = true
        return result
    }
    if (parent != null) runOperation(parent) else {
        val owner = LogicalCloudMutation(this@withCloudRevision)
        withContext(owner) {
            val result = runOperation(owner)
            if (owner.changed) {
                cloudBackupStateDao().getOrCreate()
                cloudBackupStateDao().recordLocalChange(System.currentTimeMillis())
            }
            result
        }
    }
} }

/** External/manual import branch boundary. Call only inside the protected import
 * transaction after preflight/recovery; never import trusted lineage from CSV. */
internal suspend fun TrainingDatabase.startExternalCloudBranchInTransaction() {
    check(inTransaction()) { "External branch reset must share the import Room transaction" }
    cloudBackupStateDao().getOrCreate()
    cloudBackupStateDao().startExternalBranch(System.currentTimeMillis())
}
