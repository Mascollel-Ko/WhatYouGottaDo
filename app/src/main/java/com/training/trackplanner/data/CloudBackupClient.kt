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

internal data class CloudAuthSession(val userId: String, val accessToken: String)

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
