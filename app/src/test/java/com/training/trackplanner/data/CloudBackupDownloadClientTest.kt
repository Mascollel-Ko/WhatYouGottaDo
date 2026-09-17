package com.training.trackplanner.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupDownloadClientTest {
    private val session = CloudAuthSession("11111111-1111-4111-8111-111111111111", "token")
    private val config = CloudBackupConfig("https://example.supabase.co", "publishable")

    @Test fun checksumMismatchFailsBeforeParsing() = runBlocking {
        val compressed = CloudBackupCodec.gzip("not-a-canonical-backup".toByteArray())
        val client = client(compressed, JSONObject().apply {
            put("download_url", "https://r2.invalid/object")
            put("compressed_size_bytes", compressed.size)
            put("checksum", "0".repeat(64))
            put("checksum_algorithm", "SHA-256")
            put("status", "CURRENT")
        })
        val error = runCatching { client.download(session, "backup") }.exceptionOrNull()
        assertEquals("DOWNLOAD_CHECKSUM_MISMATCH", error?.message)
    }

    @Test fun currentCanonicalGzipDownloadsAndParses() = runBlocking {
        val csv = RecordCsvBackupRestore.wrapWithManifest(
            body = RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = emptyList(), metrics = emptyList(), exercises = emptyList(),
                portableAppMeta = listOf(AppMeta("personalized_planning_preferences_v1", "{}", 1L))
            ),
            appVersion = "test",
            exportedAt = 1L,
            entityCounts = emptyMap(),
            capabilities = setOf(ProgramProgressionBackup.CAPABILITY),
            representedExerciseStableKeys = emptySet(),
            semanticCanonicalRevision = "test",
            sourceDatabaseLineageId = "test"
        )
        val compressed = CloudBackupCodec.gzip(csv.toByteArray())
        val metadata = JSONObject().apply {
            put("backup_id", "backup")
            put("download_url", "https://r2.invalid/object")
            put("compressed_size_bytes", compressed.size)
            put("uncompressed_size_bytes", csv.toByteArray().size)
            put("checksum", CloudBackupCodec.sha256Hex(compressed))
            put("checksum_algorithm", "SHA-256")
            put("backup_format_version", 14)
            put("schema_version", 13)
            put("status", "CURRENT")
        }
        val result = client(compressed, metadata).download(session, "backup")
        assertEquals(csv, result.csv)
        assertEquals("CURRENT", result.metadata.status)
    }

    @Test fun invalidGzipFailsClosed() = runBlocking {
        val bytes = "plain text".toByteArray()
        val client = client(bytes, JSONObject().apply {
            put("download_url", "https://r2.invalid/object")
            put("compressed_size_bytes", bytes.size)
            put("status", "CURRENT")
        })
        val error = runCatching { client.download(session, "backup") }.exceptionOrNull()
        assertEquals("INVALID_GZIP", error?.message)
    }

    @Test fun oversizedMetadataIsRejectedBeforeNetworkDownload() = runBlocking {
        var fetched = false
        val post = FakePost(JSONObject().apply {
            put("download_url", "https://r2.invalid/object")
            put("compressed_size_bytes", 20 * 1024 * 1024 + 1)
            put("status", "CURRENT")
        })
        val download = object : CloudDownloadTransport {
            override suspend fun getBytes(url: String): CloudBytesResponse {
                fetched = true
                return CloudBytesResponse(200, ByteArray(0))
            }
        }
        val client = CloudBackupDownloadClient(config, post, download)
        val error = runCatching { client.download(session, "backup") }.exceptionOrNull()
        assertEquals("BACKUP_TOO_LARGE", error?.message)
        assertTrue(!fetched)
    }

    private fun client(bytes: ByteArray, metadata: JSONObject) = CloudBackupDownloadClient(
        config = config,
        postTransport = FakePost(metadata),
        downloadTransport = object : CloudDownloadTransport {
            override suspend fun getBytes(url: String) = CloudBytesResponse(200, bytes)
        }
    )

    private class FakePost(private val metadata: JSONObject) : CloudHttpTransport {
        override suspend fun postJson(url: String, session: CloudAuthSession, body: String) =
            CloudHttpResponse(200, metadata.toString())
        override suspend fun putBytes(url: String, bytes: ByteArray) = CloudHttpResponse(200)
    }
}
