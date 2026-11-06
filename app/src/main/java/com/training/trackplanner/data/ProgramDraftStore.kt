package com.training.trackplanner.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Installation-local program drafts, separate from programs, sessions and Cloud Backup. */
internal data class SavedProgramDraft(
    val schemaVersion: Int = ProgramDraftStore.SCHEMA_VERSION,
    val draftId: String,
    val sourceProgramId: Long? = null,
    val sourceProgramStableKey: String? = null,
    val programName: String,
    val updatedAt: Long = 0L,
    val skeleton: GeneratedProgramSkeleton? = null,
    val wasBBlockGenerated: Boolean = false
) {
    fun summary(): ProgramDraftSummary = ProgramDraftSummary(
        draftId = draftId,
        sourceProgramId = sourceProgramId,
        sourceProgramStableKey = sourceProgramStableKey,
        programName = programName,
        updatedAt = updatedAt
    )
}

internal data class ProgramDraftSummary(
    val draftId: String,
    val sourceProgramId: Long?,
    val sourceProgramStableKey: String?,
    val programName: String,
    val updatedAt: Long
)

internal object ProgramDraftIdentity {
    const val AUTO_SAVE_INTERVAL_MILLIS = 2 * 60 * 1000L

    fun forExistingProgram(stableKey: String): String {
        require(stableKey.isNotBlank())
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(stableKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "existing_${hash.take(32)}"
    }
}

/** Atomic, versioned private files; no Room migration or implicit program apply. */
internal class ProgramDraftStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "program_drafts")

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, stringAdapter { LocalDate.parse(it) })
        .registerTypeAdapter(LocalDateTime::class.java, stringAdapter { LocalDateTime.parse(it) })
        .registerTypeAdapter(Instant::class.java, stringAdapter { Instant.parse(it) })
        .serializeNulls()
        .create()

    @Synchronized
    fun save(value: SavedProgramDraft): SavedProgramDraft {
        require(value.schemaVersion == SCHEMA_VERSION) { "DRAFT_VERSION_UNSUPPORTED" }
        require(validId(value.draftId)) { "DRAFT_ID_INVALID" }
        require(value.programName.length <= 160) { "DRAFT_NAME_TOO_LONG" }
        val previous = load(value.draftId)
        if (previous != null &&
            gson.toJson(previous.copy(updatedAt = 0L)) ==
            gson.toJson(value.copy(updatedAt = 0L))
        ) return previous

        check(root.isDirectory || root.mkdirs()) { "DRAFT_STORAGE_UNAVAILABLE" }
        val updated = value.copy(updatedAt = System.currentTimeMillis())
        val bytes = gson.toJson(updated).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_DRAFT_BYTES) { "DRAFT_TOO_LARGE" }

        val target = AtomicFile(pathFor(value.draftId))
        val out = target.startWrite()
        try {
            out.write(bytes)
            target.finishWrite(out)
        } catch (error: Throwable) {
            target.failWrite(out)
            throw error
        }
        return updated
    }

    @Synchronized
    fun load(draftId: String): SavedProgramDraft? {
        if (!validId(draftId)) return null
        val file = pathFor(draftId)
        if (!file.exists() || file.length() > MAX_DRAFT_BYTES) return null
        return runCatching {
            AtomicFile(file).openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val value = gson.fromJson(reader, SavedProgramDraft::class.java)
                value?.takeIf { it.schemaVersion == SCHEMA_VERSION && it.draftId == draftId }
            }
        }.getOrNull()
    }

    @Synchronized
    fun list(): List<ProgramDraftSummary> =
        root.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file -> load(file.nameWithoutExtension)?.summary() }
            .sortedByDescending(ProgramDraftSummary::updatedAt)
            .toList()

    @Synchronized
    fun delete(draftId: String) {
        if (!validId(draftId)) return
        AtomicFile(pathFor(draftId)).delete()
    }

    private fun pathFor(draftId: String) = File(root, "${draftId}.json")

    private fun validId(id: String): Boolean = id.matches(Regex("[A-Za-z0-9_-]{1,100}"))

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_DRAFT_BYTES = 8 * 1024 * 1024
    }
}

private fun <T> stringAdapter(parse: (String) -> T): TypeAdapter<T> =
    object : TypeAdapter<T>() {
        override fun write(out: JsonWriter, value: T?) {
            if (value == null) out.nullValue() else out.value(value.toString())
        }

        override fun read(input: JsonReader): T? =
            if (input.peek() == JsonToken.NULL) {
                input.nextNull()
                null
            } else {
                parse(input.nextString())
            }
    }
