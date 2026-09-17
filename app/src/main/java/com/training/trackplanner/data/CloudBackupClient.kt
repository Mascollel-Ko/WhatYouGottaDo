package com.training.trackplanner.data

import com.training.trackplanner.BuildConfig
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

internal data class CloudAuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAtEpochSeconds: Long = 0L,
    val displayEmail: String? = null
)

internal data class CloudBackupConfig(val supabaseUrl: String, val publishableKey: String) {
    companion object {
        fun fromBuildConfig() = CloudBackupConfig(
            BuildConfig.SUPABASE_URL.trimEnd('/'), BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )
    }
    val configured: Boolean get() = supabaseUrl.startsWith("https://") && publishableKey.isNotBlank()
}

internal data class CloudHttpResponse(val status: Int, val body: String = "")

internal interface CloudHttpTransport {
    suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse
    suspend fun putBytes(url: String, bytes: ByteArray): CloudHttpResponse
}

internal data class CloudBytesResponse(val status: Int, val bytes: ByteArray = ByteArray(0))

/** Download transport is separate from the upload interface so existing upload test fakes
 * remain source compatible. The URL is a short-lived R2 capability and is never logged. */
internal interface CloudDownloadTransport {
    suspend fun getBytes(url: String): CloudBytesResponse
}

internal class UrlConnectionCloudDownloadTransport(
    private val maxBytes: Int = 20 * 1024 * 1024
) : CloudDownloadTransport {
    override suspend fun getBytes(url: String): CloudBytesResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw CloudBackupException("BACKUP_TOO_LARGE")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            CloudBytesResponse(status, bytes)
        } finally {
            connection.disconnect()
        }
    }
}

internal class UrlConnectionCloudHttpTransport(
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) : CloudHttpTransport {
    override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse =
        withContext(Dispatchers.IO) {
            request(url, "POST", body.toByteArray(Charsets.UTF_8), mapOf(
                "Authorization" to "Bearer ${session.accessToken}",
                "apikey" to publishableKey,
                "Content-Type" to "application/json"
            ))
        }

    override suspend fun putBytes(url: String, bytes: ByteArray): CloudHttpResponse =
        withContext(Dispatchers.IO) {
            request(url, "PUT", bytes, mapOf("Content-Type" to "application/gzip"))
        }

    private fun request(url: String, method: String, bytes: ByteArray, headers: Map<String, String>): CloudHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setFixedLengthStreamingMode(bytes.size)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            CloudHttpResponse(connection.responseCode, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

internal data class CloudBackupUploadResult(
    val backupId: String, val compressedSizeBytes: Int, val checksum: String, val acknowledged: Boolean
)

internal object CloudBackupCodec {
    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
        output.toByteArray()
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun gunzip(bytes: ByteArray, maxUncompressedBytes: Int = 256 * 1024 * 1024): ByteArray {
        if (bytes.isEmpty()) throw CloudBackupException("EMPTY_DOWNLOAD")
        val output = ByteArrayOutputStream()
        GZIPInputStream(bytes.inputStream()).use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxUncompressedBytes) throw CloudBackupException("BACKUP_TOO_LARGE")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }
}

internal data class CloudBackupDownloadMetadata(
    val backupId: String,
    val compressedSizeBytes: Int,
    val uncompressedSizeBytes: Int?,
    val checksum: String?,
    val checksumAlgorithm: String?,
    val status: String,
    val backupFormatVersion: Int,
    val schemaVersion: Int
)

internal data class CloudBackupDownloadResult(
    val metadata: CloudBackupDownloadMetadata,
    val csv: String
)

internal class CloudBackupDownloadClient(
    private val config: CloudBackupConfig,
    private val postTransport: CloudHttpTransport,
    private val downloadTransport: CloudDownloadTransport = UrlConnectionCloudDownloadTransport(),
    private val maxCompressedBytes: Int = 20 * 1024 * 1024,
    private val maxUncompressedBytes: Int = 256 * 1024 * 1024
) {
    suspend fun download(session: CloudAuthSession, backupId: String): CloudBackupDownloadResult =
        withContext(Dispatchers.IO) {
            require(config.configured) { "Cloud Backup is not configured for this build." }
            require(session.userId.isNotBlank() && session.accessToken.isNotBlank()) {
                "Cloud Backup requires an authenticated session."
            }
            val request = postTransport.postJson(
                "${config.supabaseUrl}/functions/v1/cloud-backup-download-url",
                session,
                JSONObject().put("backup_id", backupId).toString()
            )
            if (request.status !in 200..299) throw CloudBackupException(parseError(request.body))
            val metadata = runCatching { JSONObject(request.body) }
                .getOrElse { throw CloudBackupException("INVALID_SERVER_RESPONSE") }
            val returnedBackupId = metadata.optString("backup_id")
            if (returnedBackupId.isNotBlank() && returnedBackupId != backupId) {
                throw CloudBackupException("INVALID_SERVER_RESPONSE")
            }
            val status = metadata.optString("status")
            if (status !in setOf("CURRENT", "RETAINED", "VERIFIED", "CONFLICT_RECOVERY")) {
                throw CloudBackupException("BACKUP_NOT_AVAILABLE")
            }
            val url = metadata.optString("download_url")
                .takeIf(String::isNotBlank) ?: throw CloudBackupException("INVALID_SERVER_RESPONSE")
            val compressedSize = metadata.optInt("compressed_size_bytes", -1)
            if (compressedSize !in 0..maxCompressedBytes) throw CloudBackupException("BACKUP_TOO_LARGE")
            val checksum = metadata.optString("checksum").takeIf(String::isNotBlank)
            val algorithm = metadata.optString("checksum_algorithm").takeIf(String::isNotBlank)
            if (checksum != null && (algorithm != "SHA-256" || !checksum.matches(Regex("[0-9a-fA-F]{64}")))) {
                throw CloudBackupException("INVALID_CHECKSUM")
            }
            val fetched = downloadTransport.getBytes(url)
            if (fetched.status !in 200..299) throw CloudBackupException("R2_GET_FAILED")
            if (fetched.bytes.size != compressedSize) throw CloudBackupException("DOWNLOAD_SIZE_MISMATCH")
            if (checksum != null && !checksum.equals(CloudBackupCodec.sha256Hex(fetched.bytes), ignoreCase = true)) {
                throw CloudBackupException("DOWNLOAD_CHECKSUM_MISMATCH")
            }
            val uncompressed = try {
                CloudBackupCodec.gunzip(fetched.bytes, maxUncompressedBytes)
            } catch (error: CloudBackupException) {
                throw error
            } catch (_: Throwable) {
                throw CloudBackupException("INVALID_GZIP")
            }
            val expectedUncompressed = metadata.optInt("uncompressed_size_bytes", -1)
                .takeIf { it >= 0 }
            if (expectedUncompressed != null && uncompressed.size != expectedUncompressed) {
                throw CloudBackupException("UNCOMPRESSED_SIZE_MISMATCH")
            }
            val csv = uncompressed.toString(Charsets.UTF_8)
            val parsed = runCatching { RecordCsvBackupRestore.parse(csv) }.getOrElse {
                throw CloudBackupException("INVALID_CANONICAL_BACKUP")
            }
            val restore = parsed as? RecordCsvImportData.Restore
                ?: throw CloudBackupException("INVALID_CANONICAL_BACKUP")
            if (metadata.optInt("backup_format_version", -1) != RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION ||
                metadata.optInt("schema_version", -1) != RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION
            ) throw CloudBackupException("UNSUPPORTED_BACKUP_VERSION")
            if (restore.manifest?.formatVersion != RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION ||
                restore.backupSchemaVersion != RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION
            ) throw CloudBackupException("UNSUPPORTED_BACKUP_VERSION")
            CloudBackupDownloadResult(
                metadata = CloudBackupDownloadMetadata(
                    backupId = returnedBackupId.ifBlank { backupId },
                    compressedSizeBytes = compressedSize,
                    uncompressedSizeBytes = expectedUncompressed,
                    checksum = checksum,
                    checksumAlgorithm = algorithm,
                    status = status,
                    backupFormatVersion = metadata.optInt("backup_format_version", -1),
                    schemaVersion = metadata.optInt("schema_version", -1)
                ),
                csv = csv
            )
        }

    private fun parseError(body: String): String = runCatching { JSONObject(body).optString("error") }
        .getOrNull()?.takeIf(String::isNotBlank) ?: "CLOUD_REQUEST_FAILED"
}

internal class CloudBackupClient(
    private val db: TrainingDatabase,
    private val canonicalBackup: suspend () -> CanonicalBackupContent,
    private val config: CloudBackupConfig,
    private val http: CloudHttpTransport? = null
) {
    private val transport: CloudHttpTransport by lazy {
        http ?: UrlConnectionCloudHttpTransport(config.publishableKey)
    }
    suspend fun uploadNow(session: CloudAuthSession, now: Long = System.currentTimeMillis()): CloudBackupUploadResult =
        withContext(Dispatchers.IO) {
            require(config.configured) { "Cloud Backup is not configured for this build." }
            require(session.userId.isNotBlank() && session.accessToken.isNotBlank()) {
                "Cloud Backup requires an authenticated session."
            }
            val stateDao = db.cloudBackupStateDao()
            var state = stateDao.getOrCreate()
            if (state.accountUserId == null) {
                stateDao.bindAccount(session.userId)
                state = stateDao.getOrCreate()
            }
            check(state.accountUserId == session.userId) {
                "Cloud account changed; refresh authentication before uploading."
            }
            val snapshotBase = state.localBaseBackupId
            val snapshotRevision = state.localRevision
            val canonical = canonicalBackup()
            val uncompressed = canonical.utf8Bytes()
            val compressed = CloudBackupCodec.gzip(uncompressed)
            val checksum = CloudBackupCodec.sha256Hex(compressed)
            try {
                val uploadRequest = JSONObject().apply {
                    put("parent_backup_id", snapshotBase)
                    put("install_id", state.installId)
                    put("compressed_size_bytes", compressed.size)
                    put("uncompressed_size_bytes", uncompressed.size)
                    put("checksum", checksum)
                    put("checksum_algorithm", "SHA-256")
                    put("backup_format_version", RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION)
                    put("schema_version", RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION)
                    put("app_version", BuildConfig.VERSION_NAME)
                    put("local_revision", snapshotRevision)
                    put("compression", "GZIP")
                }
                val uploadAuth = post("cloud-backup-upload-url", session, uploadRequest.toString())
                val backupId = uploadAuth.getString("backup_id")
                val uploadUrl = uploadAuth.getString("upload_url")
                val put = transport.putBytes(uploadUrl, compressed)
                if (put.status !in 200..299) throw CloudBackupException("R2_PUT_FAILED")
                val finalized = post(
                    "cloud-backup-finalize", session,
                    JSONObject().put("backup_id", backupId).toString()
                )
                if (finalized.optString("status") != "CURRENT") {
                    throw CloudBackupException(finalized.optString("error", "FINALIZE_FAILED"))
                }
                val acknowledged = db.withTransaction {
                    stateDao.acknowledgeIfSnapshotUnchanged(
                        session.userId, snapshotBase, snapshotRevision, backupId, now
                    ) > 0
                }
                CloudBackupUploadResult(backupId, compressed.size, checksum, acknowledged)
            } catch (error: Throwable) {
                stateDao.updateRetry(state.retryAttempt + 1, now + 60_000L, error.message?.take(120) ?: "CLOUD_UPLOAD_FAILED")
                throw error
            }
        }

    private suspend fun post(function: String, session: CloudAuthSession, body: String): JSONObject {
        val response = transport.postJson("${config.supabaseUrl}/functions/v1/$function", session, body)
        if (response.status !in 200..299) throw CloudBackupException(parseError(response.body))
        return runCatching { JSONObject(response.body) }
            .getOrElse { throw CloudBackupException("INVALID_SERVER_RESPONSE") }
    }

    private fun parseError(body: String): String = runCatching { JSONObject(body).optString("error") }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "CLOUD_REQUEST_FAILED"
}

internal class CloudBackupException(code: String) : IOException(code)
