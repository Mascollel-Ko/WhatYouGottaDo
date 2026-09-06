package com.training.trackplanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class ProgressionRole { AUTO, MAIN, ASSISTANCE }
enum class ProgressionMode { APP, CUSTOM, DIRECT, OFF }
enum class ProgressionBase { UNIFORM, ANCHOR, REVIEW }
enum class ProgressionLinkMode { AUTO, EXISTING, SEPARATE, OFF }
enum class ProgressionDirection { INCREASE, HOLD, DECREASE, REVIEW }
enum class ProgressionResolution { PENDING, ACCEPTED, KEPT_CURRENT_PLAN, MANUAL_OVERRIDE, SUPERSEDED, STALE }
enum class MissingProgressionRpe { HOLD, COMPLETION_ONLY }
enum class ProgressionRpePolicy { MAX_WORKING, ANCHOR }
enum class FirstProgressionFailure { HOLD, REVIEW }

enum class ProgressionAuthority { USER_EXPLICIT, PLANNER_EXPLICIT, AUTO_INFERRED }

/** Session identity is independent of any member item; settings have one shared owner. */
data class DraftProgressionSession(val track: ProgramProgressionTrack, val source: ProgressionAuthority) {
    val key: String get() = track.id
}

data class DraftProgressionBinding(
    val sessionKey: String,
    val logicalItemId: String = UUID.randomUUID().toString(),
    val linkMode: ProgressionLinkMode = ProgressionLinkMode.AUTO,
    val signature: ProgressionSignature,
    val persisted: Boolean = false
)

/** Versioned engineering defaults, not physiological thresholds. Copied into each applied link. */
data class ProgressionRule(
    val version: Int = 1,
    val requireCompletion: Boolean = true,
    val rpePolicy: ProgressionRpePolicy = ProgressionRpePolicy.MAX_WORKING,
    val rpeThreshold: Double = 8.0,
    val successesRequired: Int = 1,
    val incrementKg: Double? = null,
    val firstFailure: FirstProgressionFailure = FirstProgressionFailure.HOLD,
    val failuresBeforeDecrease: Int = 2,
    val decreasePercent: Double = 5.0,
    val missingRpe: MissingProgressionRpe = MissingProgressionRpe.HOLD
) {
    fun validate() {
        require(version == 1 && rpeThreshold.isFinite() && rpeThreshold in 1.0..10.0)
        require(successesRequired in 1..3 && failuresBeforeDecrease in 2..5)
        require(incrementKg == null || incrementKg.isFinite() && incrementKg > 0)
        require(decreasePercent.isFinite() && decreasePercent in 0.1..25.0)
    }
}

/** No FK to the template: its deletion must not erase execution history. */
@Entity(tableName = "program_progression_tracks", indices = [Index("programStableKey")])
data class ProgramProgressionTrack(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val programStableKey: String,
    val exerciseStableKey: String,
    val label: String,
    val role: ProgressionRole = ProgressionRole.AUTO,
    val roleOverride: ProgressionRole = ProgressionRole.AUTO,
    val mode: ProgressionMode = ProgressionMode.APP,
    val basePolicy: ProgressionBase = ProgressionBase.REVIEW,
    val anchorSetIndex: Int? = null,
    val needsReview: Boolean = false,
    @Embedded(prefix = "rule_") val rule: ProgressionRule = ProgressionRule()
)

/** RPE intentionally absent: author-time intent must not depend on performed effort. */
data class ProgressionSignature(
    val exerciseStableKey: String,
    val setCount: Int,
    val repsPattern: String,
    val baseKg: Double?,
    val oneRmSnapshotKg: Double?,
    val relativeIntensity: Double?,
    val style: String = "",
    val variant: String = "",
    val trainingSlot: String = "",
    val dayIntensity: String = "",
    val basePolicy: ProgressionBase = ProgressionBase.REVIEW,
    val anchorSetIndex: Int? = null,
    val plannerRole: ProgressionRole = ProgressionRole.AUTO
)

@Entity(tableName = "program_progression_items", foreignKeys = [
    ForeignKey(entity = TrainingProgramItem::class, parentColumns = ["id"], childColumns = ["programItemId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProgramProgressionTrack::class, parentColumns = ["id"], childColumns = ["trackId"])
], indices = [Index("trackId")])
data class ProgramProgressionItem(
    @PrimaryKey val programItemId: Long,
    val logicalItemId: String = UUID.randomUUID().toString(),
    val trackId: String,
    val linkMode: ProgressionLinkMode = ProgressionLinkMode.AUTO,
    @Embedded(prefix = "signature_") val signature: ProgressionSignature
)

@Entity(tableName = "program_applications")
data class ProgramApplication(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val programStableKey: String,
    val programName: String,
    val startDate: String,
    val appliedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "program_workout_links", foreignKeys = [
    ForeignKey(entity = WorkoutEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProgramApplication::class, parentColumns = ["id"], childColumns = ["applicationId"])
], indices = [Index(value = ["applicationId", "trackId", "sequence"], unique = true)])
data class ProgramWorkoutLink(
    @PrimaryKey val entryId: Long,
    val applicationId: String,
    val sourceProgramStableKey: String,
    val sourceItemId: String,
    val programName: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val trackId: String,
    val trackLabel: String,
    val sequence: Int,
    val role: ProgressionRole,
    val mode: ProgressionMode,
    val basePolicy: ProgressionBase,
    val anchorSetIndex: Int?,
    val needsReview: Boolean,
    @Embedded(prefix = "rule_") val rule: ProgressionRule
)

/** Actuals remain exclusively in WorkoutSet. Original and current targets are independent. */
@Entity(tableName = "program_prescription_sets", primaryKeys = ["entryId", "setIndex"], foreignKeys = [
    ForeignKey(entity = WorkoutEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)
])
data class ProgramPrescriptionSet(
    val entryId: Long,
    val setIndex: Int,
    val originalReps: Int,
    val originalKg: Double,
    val originalSeconds: Int,
    val plannedReps: Int = originalReps,
    val plannedKg: Double = originalKg,
    val plannedSeconds: Int = originalSeconds,
    val plannedSetIndex: Int? = setIndex,
    val originalExists: Boolean = true
)

/** Evidence and resolution are immutable history; superseded/stale rows are retained. */
@Entity(tableName = "progression_suggestions", indices = [Index("targetEntryId"), Index("sourceEntryId")])
data class ProgressionSuggestion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val applicationId: String,
    val trackId: String,
    val sourceEntryId: Long,
    val targetEntryId: Long,
    val previousActualKg: Double?,
    val currentPlanKg: Double?,
    val suggestedKg: Double?,
    val judgmentRpe: Double?,
    val direction: ProgressionDirection,
    val reasons: String,
    val evidenceHash: String,
    val resolution: ProgressionResolution = ProgressionResolution.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedKg: Double? = null,
    @Embedded(prefix = "rule_") val rule: ProgressionRule
)

@Dao
interface ProgramProgressionDao {
    @Upsert suspend fun putTrack(value: ProgramProgressionTrack)
    @Upsert suspend fun putItem(value: ProgramProgressionItem)
    @Upsert suspend fun putApplication(value: ProgramApplication)
    @Upsert suspend fun putLink(value: ProgramWorkoutLink)
    @Upsert suspend fun putPrescription(value: ProgramPrescriptionSet)
    @Upsert suspend fun putSuggestion(value: ProgressionSuggestion)
    @Query("SELECT * FROM program_progression_tracks") suspend fun tracks(): List<ProgramProgressionTrack>
    @Query("SELECT * FROM program_progression_items") suspend fun items(): List<ProgramProgressionItem>
    @Query("DELETE FROM program_progression_items WHERE programItemId = :itemId") suspend fun deleteItem(itemId: Long)
    @Query("SELECT * FROM program_applications") suspend fun applications(): List<ProgramApplication>
    @Query("SELECT * FROM program_workout_links") suspend fun links(): List<ProgramWorkoutLink>
    @Query("SELECT * FROM program_prescription_sets") suspend fun prescriptions(): List<ProgramPrescriptionSet>
    @Query("SELECT * FROM progression_suggestions") suspend fun suggestions(): List<ProgressionSuggestion>
    @Query("SELECT * FROM program_progression_tracks") fun observeTracks(): Flow<List<ProgramProgressionTrack>>
    @Query("SELECT * FROM program_progression_items") fun observeItems(): Flow<List<ProgramProgressionItem>>
    @Query("SELECT * FROM program_workout_links") fun observeLinks(): Flow<List<ProgramWorkoutLink>>
    @Query("SELECT * FROM progression_suggestions") fun observeSuggestions(): Flow<List<ProgressionSuggestion>>
    @Query("SELECT * FROM program_workout_links WHERE entryId = :entryId") suspend fun link(entryId: Long): ProgramWorkoutLink?
    @Query("SELECT * FROM program_prescription_sets WHERE entryId = :entryId ORDER BY setIndex") suspend fun prescriptions(entryId: Long): List<ProgramPrescriptionSet>
}
