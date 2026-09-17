package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudBackupClientTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    private fun db() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }

    @After fun close() = databases.forEach { it.close() }

    @Test fun codecProducesDeterministicGzipAndSha256() {
        val bytes = "canonical csv".toByteArray()
        val compressed = CloudBackupCodec.gzip(bytes)
        assertTrue(compressed.size > bytes.size)
        assertEquals(64, CloudBackupCodec.sha256Hex(compressed).length)
        assertEquals(CloudBackupCodec.gzip(bytes).toList(), compressed.toList())
    }

    @Test fun uploadUsesCanonicalBytesFinalizesThenAcknowledgesSnapshot() = runBlocking {
        val db = db()
        val session = CloudAuthSession("user-1", "token")
        val backupId = "11111111-1111-4111-8111-111111111111"
        val transport = RecordingTransport(backupId)
        val content = CanonicalBackupContent("canonical csv", RecordCsvTransferResult("restore-v14"), emptyList())
        val result = CloudBackupClient(
            db = db,
            canonicalBackup = { content },
            config = CloudBackupConfig("https://example.supabase.co", "publishable"),
            http = transport
        ).uploadNow(session, now = 1234L)
        assertEquals(backupId, result.backupId)
        assertTrue(result.acknowledged)
        assertEquals(2, transport.posts.size)
        assertEquals(14, transport.posts[0].getInt("backup_format_version"))
        assertEquals(13, transport.posts[0].getInt("schema_version"))
        assertEquals(backupId, transport.posts[1].getString("backup_id"))
        assertEquals(backupId, db.cloudBackupStateDao().get()!!.localBaseBackupId)
        assertEquals(0L, db.cloudBackupStateDao().get()!!.localRevision)
    }

    private class RecordingTransport(private val backupId: String) : CloudHttpTransport {
        val posts = mutableListOf<JSONObject>()
        override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse {
            val json = JSONObject(body)
            posts += json
            return if (url.endsWith("upload-url")) {
                CloudHttpResponse(200, JSONObject().put("backup_id", backupId).put("upload_url", "https://r2.invalid/$backupId").toString())
            } else {
                CloudHttpResponse(200, JSONObject().put("backup_id", backupId).put("status", "CURRENT").toString())
            }
        }
        override suspend fun putBytes(url: String, bytes: ByteArray): CloudHttpResponse = CloudHttpResponse(200)
    }
}
