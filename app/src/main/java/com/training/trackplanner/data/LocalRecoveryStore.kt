package com.training.trackplanner.data

import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class LocalRecoveryMetadata(
    val snapshotId: String,
    val createdAt: Long,
    val accountUserId: String?,
    val localBaseBackupId: String?,
    val localRevision: Long,
    val cloudBackupPending: Boolean,
    val lastLocalChangeAt: Long?,
    val checksum: String,
    val compressedSizeBytes: Long,
    val backupFormatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String
) {
    fun json(): String = JSONObject().apply {
        put("snapshotId", snapshotId); put("snapshotType", "LOCAL_RECOVERY")
        put("createdAt", createdAt); put("accountUserId", accountUserId ?: JSONObject.NULL)
        put("localBaseBackupId", localBaseBackupId ?: JSONObject.NULL)
        put("localRevision", localRevision); put("cloudBackupPending", cloudBackupPending)
        put("lastLocalChangeAt", lastLocalChangeAt ?: JSONObject.NULL)
        put("checksum", checksum); put("checksumAlgorithm", "SHA-256")
        put("compressedSizeBytes", compressedSizeBytes); put("backupFormatVersion", backupFormatVersion)
        put("schemaVersion", schemaVersion); put("appVersion", appVersion)
    }.toString()

    companion object {
        fun parse(text: String): LocalRecoveryMetadata {
            val j = JSONObject(text)
            require(j.getString("snapshotType") == "LOCAL_RECOVERY")
            require(j.getString("checksumAlgorithm") == "SHA-256")
            fun nullable(key: String) = if (j.isNull(key)) null else j.getString(key)
            return LocalRecoveryMetadata(j.getString("snapshotId"), j.getLong("createdAt"),
                nullable("accountUserId"), nullable("localBaseBackupId"), j.getLong("localRevision"),
                j.getBoolean("cloudBackupPending"),
                if (j.isNull("lastLocalChangeAt")) null else j.getLong("lastLocalChangeAt"),
                j.getString("checksum"), j.getLong("compressedSizeBytes"),
                j.getInt("backupFormatVersion"), j.getInt("schemaVersion"), j.getString("appVersion"))
                .also { require(it.localRevision >= 0 && it.createdAt >= 0) }
        }
    }
}

internal data class ValidatedLocalRecovery(
    val metadata: LocalRecoveryMetadata,
    val data: RecordCsvImportData.Restore
)

/** Immutable, fsynced generation pairs. Visibility is exclusively the Room pointer.
 * The caller holds LocalDataGate for creation, access and conservative cleanup.
 */
internal class LocalRecoveryStore(
    private val root: File,
    private val preflight: suspend (String) -> RecordCsvImportData.Restore,
    private val syncDirectory: (File) -> Unit = ::syncRecoveryDirectory,
    private val fault: (String) -> Unit = {}
) {
    suspend fun candidate(content: CanonicalBackupContent, state: CloudBackupState, now: Long): ValidatedLocalRecovery {
        require(root.exists() || root.mkdirs()) { "Cannot create recovery directory" }
        syncDirectory(checkNotNull(root.parentFile))
        val id = UUID.randomUUID().toString()
        val generation = directory(id)
        check(generation.mkdir())
        val payload = File(generation, PAYLOAD)
        FileOutputStream(payload).use { out ->
            val gzip = GZIPOutputStream(out)
            gzip.write(content.utf8Bytes()); gzip.finish(); gzip.flush(); out.fd.sync()
        }
        fault("payload")
        val parsed = preflight(content.csv)
        val manifest = checkNotNull(parsed.manifest)
        val meta = LocalRecoveryMetadata(id, now, state.accountUserId, state.localBaseBackupId,
            state.localRevision, state.cloudBackupPending, state.lastLocalChangeAt,
            sha256(payload.readBytes()), payload.length(), manifest.formatVersion,
            RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION, manifest.appVersion)
        FileOutputStream(File(generation, SIDECAR)).use { out ->
            out.write(meta.json().toByteArray(Charsets.UTF_8)); out.fd.sync()
        }
        fault("sidecar")
        syncDirectory(generation); syncDirectory(root)
        val validated = validate(id)
        fault("validated")
        return validated
    }

    suspend fun validate(id: String): ValidatedLocalRecovery {
        val dir = directory(id)
        val sidecar = File(dir, SIDECAR)
        require(sidecar.length() in 1..MAX_SIDECAR_BYTES)
        val meta = LocalRecoveryMetadata.parse(sidecar.readText(Charsets.UTF_8))
        require(meta.snapshotId == id)
        require(meta.backupFormatVersion == RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION &&
            meta.schemaVersion == RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION)
        val payload = File(dir, PAYLOAD)
        require(payload.length() in 1..MAX_PAYLOAD_BYTES && payload.length() == meta.compressedSizeBytes)
        val compressed = payload.readBytes()
        require(sha256(compressed) == meta.checksum) { "Recovery checksum mismatch" }
        val csv = GZIPInputStream(compressed.inputStream()).use { input ->
            val bytes = input.readBytesBounded(MAX_CSV_BYTES)
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
        val data = preflight(csv)
        require(data.manifest?.formatVersion == meta.backupFormatVersion && data.manifest?.appVersion == meta.appVersion)
        return ValidatedLocalRecovery(meta, data)
    }

    fun cleanup(activeId: String?) {
        // Only our UUID directories, never arbitrary files or symlinks; called under the writer gate.
        root.listFiles()?.filter { it.name != activeId && isId(it.name) && it.isDirectory &&
            it.canonicalFile.parentFile == root.canonicalFile }?.forEach { generation ->
            generation.listFiles()?.filter { it.name in setOf(PAYLOAD, SIDECAR) &&
                it.canonicalFile.parentFile == generation.canonicalFile }?.forEach { it.delete() }
            generation.delete()
        }
    }

    private fun directory(id: String): File {
        require(isId(id)) { "Invalid recovery generation" }
        return File(root, id).also { require(it.canonicalFile.parentFile == root.canonicalFile) }
    }
    companion object {
        const val PAYLOAD = "local_recovery_previous.csv.gz"
        const val SIDECAR = "metadata.json"
        const val MAX_PAYLOAD_BYTES = 128L * 1024 * 1024
        const val MAX_CSV_BYTES = 512L * 1024 * 1024
        const val MAX_SIDECAR_BYTES = 64L * 1024
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun isId(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(output.size().toLong() + n <= limit) { "Recovery CSV exceeds size limit" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}

private fun syncRecoveryDirectory(directory: File) {
    runCatching {
        val fd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }
}
