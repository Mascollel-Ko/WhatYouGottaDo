package com.training.trackplanner.data

import android.content.ContentValues
import android.database.Cursor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * A narrow, lossless recovery archive for *only* the rows deleted by
 * ProgramPlanService.deleteProgram. Used if the global canonical CSV recovery
 * refuses to serialize unrelated historical data.
 *
 * The archive is durable and verified before the mutation. A failed archive
 * blocks deletion. No workout entry, set, progression execution history, or
 * other user's program is mutated by this code.
 *
 * Snapshot rows use native SQLite value types and column names rather than
 * today's Kotlin entity constructor: all nullable legacy columns survive.
 */
internal class ProgramDeleteRecoveryStore(
    private val db: TrainingDatabase,
    private val root: File
) {
    suspend fun protect(programId: Long, mutation: suspend () -> Unit) {
        // Caller holds LocalDataGate for both capture and the following mutation.
        val snapshot = db.withTransaction {
            val program = db.programDao().findProgram(programId)
            if (program == null) null else JSONObject().apply {
                put("version", 1)
                put("programId", programId)
                put("programStableKey", program.stableKey)
                val tables = JSONArray()
                tables.put(capture("training_programs", "id = ?", arrayOf(programId)))
                tables.put(capture("training_program_items", "programId = ?", arrayOf(programId)))
                val itemIds = db.programDao().itemsForProgram(programId).map { it.id }
                val args = itemIds.map { it as Any }.toTypedArray()
                val condition = if (itemIds.isEmpty()) "1 = 0" else "programItemId IN (${itemIds.joinToString(",") { "?" }})"
                tables.put(capture("training_program_item_sets", condition, args))
                tables.put(capture("program_progression_items", condition, args))
                tables.put(capture("community_program_imports", "localProgramStableKey = ?", arrayOf(program.stableKey)))
                tables.put(capture("training_program_tombstones", "programStableKey = ?", arrayOf(program.stableKey)))
                put("tables", tables)
            }
        }
        if (snapshot != null) {
            persistVerified(snapshot)
        }
        mutation()
    }

    private fun capture(table: String, where: String, args: Array<Any>): JSONObject {
        val query = SimpleSQLiteQuery("SELECT * FROM `$table` WHERE $where", args)
        return db.openHelper.writableDatabase.query(query).use { cursor ->
            val cols = JSONArray()
            for (index in 0 until cursor.columnCount) cols.put(cursor.getColumnName(index))
            val rows = JSONArray()
            while (cursor.moveToNext()) {
                val row = JSONArray()
                for (index in 0 until cursor.columnCount) {
                    val value = JSONObject()
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> value.put("t", "n")
                        Cursor.FIELD_TYPE_INTEGER -> value.put("t", "i").put("v", cursor.getLong(index).toString())
                        Cursor.FIELD_TYPE_FLOAT -> value.put("t", "f").put("v", cursor.getDouble(index).toString())
                        Cursor.FIELD_TYPE_BLOB -> value.put("t", "b").put("v", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                        else -> value.put("t", "s").put("v", cursor.getString(index))
                    }
                    row.put(value)
                }
                rows.put(row)
            }
            JSONObject().put("table", table).put("columns", cols).put("rows", rows)
        }
    }

    private fun persistVerified(body: JSONObject): File {
        check(root.isDirectory || root.mkdirs()) { "Unable to create program deletion recovery directory" }
        val bodyText = body.toString()
        val bodyBytes = bodyText.toByteArray(Charsets.UTF_8)
        require(bodyBytes.size <= MAX_BYTES) { "Program deletion recovery archive exceeds limit" }
        val envelope = JSONObject()
            .put("sha256", sha256(bodyBytes))
            .put("body", bodyText)
            .toString().toByteArray(Charsets.UTF_8)
        val name = UUID.randomUUID().toString()
        val tmp = File(root, "$name.tmp")
        val target = File(root, "$name.json")
        try {
            FileOutputStream(tmp).use {
                it.write(envelope)
                it.fd.sync()
            }
            check(tmp.renameTo(target)) { "Unable to atomically promote program deletion archive" }
            syncDirectory(root)
            val recovered = readValidated(target)
            require(recovered.getLong("programId") == body.getLong("programId"))
            require(recovered.getString("programStableKey") == body.getString("programStableKey"))
            val tables = recovered.getJSONArray("tables")
            require(tables.length() == TABLES.size)
            for (index in TABLES.indices) {
                require(tables.getJSONObject(index).getString("table") == TABLES[index])
            }
            require(tables.getJSONObject(0).getJSONArray("rows").length() == 1)
            return target
        } finally {
            tmp.delete()
        }
    }

    internal fun readValidated(file: File): JSONObject {
        require(file.canonicalFile.parentFile == root.canonicalFile)
        require(file.isFile && file.length() in 1L..(MAX_BYTES + 1024L))
        val envelope = JSONObject(file.readText(Charsets.UTF_8))
        val bodyText = envelope.getString("body")
        require(sha256(bodyText.toByteArray(Charsets.UTF_8)) == envelope.getString("sha256")) {
            "Program deletion recovery archive checksum mismatch"
        }
        val body = JSONObject(bodyText)
        require(body.getInt("version") == 1)
        return body
    }

    /**
     * Verified, transactionally reversible recovery. Kept internal; UI must not
     * silently resurrect an explicitly deleted program. A program ID or key
     * conflict aborts the entire restore instead of overwriting current data.
     */
    suspend fun restore(file: File) {
        val snapshot = readValidated(file)
        val stableKey = snapshot.getString("programStableKey")
        val id = snapshot.getLong("programId")
        db.withTransaction {
            require(db.programDao().findProgram(id) == null)
            require(db.programDao().findProgramByStableKey(stableKey) == null)
            // A built-in tombstone may have been written by the deletion.
            db.programDao().deleteProgramTombstone(stableKey)
            val tables = snapshot.getJSONArray("tables")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val name = table.getString("table")
                require(name == TABLES[i])
                val columns = table.getJSONArray("columns")
                val rows = table.getJSONArray("rows")
                for (j in 0 until rows.length()) {
                    val fields = rows.getJSONArray(j)
                    require(fields.length() == columns.length())
                    val content = ContentValues()
                    for (k in 0 until columns.length()) {
                        val column = columns.getString(k)
                        val field = fields.getJSONObject(k)
                        when (field.getString("t")) {
                            "n" -> content.putNull(column)
                            "i" -> content.put(column, field.getString("v").toLong())
                            "f" -> content.put(column, field.getString("v").toDouble())
                            "b" -> content.put(column, Base64.decode(field.getString("v"), Base64.DEFAULT))
                            "s" -> content.put(column, field.getString("v"))
                            else -> error("Invalid archive cell type")
                        }
                    }
                    db.openHelper.writableDatabase.insert(name, androidx.sqlite.db.SupportSQLiteDatabase.CONFLICT_ABORT, content)
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun syncDirectory(dir: File) {
        runCatching {
            val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        }
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024 * 1024
        private val TABLES = listOf(
            "training_programs", "training_program_items", "training_program_item_sets",
            "program_progression_items", "community_program_imports", "training_program_tombstones"
        )
    }
}
