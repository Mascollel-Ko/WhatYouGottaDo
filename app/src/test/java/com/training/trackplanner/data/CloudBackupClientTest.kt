package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun discoveredGuestAbsenceAllowsFirstUploadAfterSafeBind() = runBlocking {
        val db = db()
        db.initialUserProfileDao().upsert(InitialUserProfile(bodyWeightKg = 72.0))
        val session = CloudAuthSession("user-1", "token")
        val repository = TrainingRepository(db, context) { null }
        val snapshot = repository.cloudAccountEntrySnapshot(session)
        assertEquals(CloudAccountEntryAction.ADOPT_GUEST_LOCAL_DATA,
            CloudAccountEntryClassifier.classify(snapshot, session.userId))

        repository.bindCloudAccountIfSafe(session.userId)
        val backupId = "11111111-1111-4111-8111-111111111111"
        val result = CloudBackupClient(
            db = db,
            canonicalBackup = { CanonicalBackupContent("canonical csv", RecordCsvTransferResult("restore-v14"), emptyList()) },
            config = CloudBackupConfig("https://example.supabase.co", "publishable"),
            http = RecordingTransport(backupId)
        ).uploadNow(session, now = 1234L)

        assertEquals(backupId, result.backupId)
        assertTrue(result.acknowledged)
        assertEquals(session.userId, db.cloudBackupStateDao().get()!!.accountUserId)
    }

    @Test fun currentDiscoveryReturnsMinimalServerMetadata() = runBlocking {
        val transport = DiscoveryTransport(
            CloudHttpResponse(200, JSONObject().put(
                "current", JSONObject()
                    .put("backup_id", "11111111-1111-4111-8111-111111111111")
                    .put("status", "CURRENT")
                    .put("backup_format_version", 14)
                    .put("schema_version", 13)
                    .put("local_revision", 4)
            ).toString())
        )
        val current = CloudCurrentBackupClient(
            CloudBackupConfig("https://example.supabase.co", "publishable"),
            transport
        ).discover(CloudAuthSession("user-1", "token"))
        assertEquals("11111111-1111-4111-8111-111111111111", current?.backupId)
        assertEquals("https://example.supabase.co/functions/v1/cloud-backup-current", transport.url)
        assertEquals("{}", transport.body)
    }

    @Test fun currentDiscoveryReportsNoCurrentWithoutInventingMetadata() = runBlocking {
        val current = CloudCurrentBackupClient(
            CloudBackupConfig("https://example.supabase.co", "publishable"),
            DiscoveryTransport(CloudHttpResponse(200, "{\"current\":null}"))
        ).discover(CloudAuthSession("user-1", "token"))
        assertFalse(current != null)
    }

    @Test fun currentDiscoveryFailureDoesNotProduceACloudResult() = runBlocking {
        val result = runCatching {
            CloudCurrentBackupClient(
                CloudBackupConfig("https://example.supabase.co", "publishable"),
                DiscoveryTransport(CloudHttpResponse(503, "{\"error\":\"TEMPORARY_FAILURE\"}"))
            ).discover(CloudAuthSession("user-1", "token"))
        }
        assertTrue(result.isFailure)
        assertEquals("TEMPORARY_FAILURE", result.exceptionOrNull()?.message)
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

    @Test fun explicitLocalWinsIncludesServerCurrentCasAndOrdinaryUploadDoesNot() = runBlocking {
        val backupId = "11111111-1111-4111-8111-111111111111"
        val expected = "44444444-4444-4444-8444-444444444444"
        val transport = RecordingTransport(backupId)
        val result = CloudBackupClient(
            db = db(),
            canonicalBackup = {
                CanonicalBackupContent("canonical csv",
                    RecordCsvTransferResult("restore-v14"), emptyList())
            },
            config = CloudBackupConfig("https://example.supabase.co", "publishable"),
            http = transport
        ).uploadNow(
            CloudAuthSession("user-1", "token"),
            now = 1234L,
            expectedCloudCurrentForLocalWins = expected
        )
        assertTrue(result.acknowledged)
        assertEquals("LOCAL_WINS", transport.posts[1].getString("resolution"))
        assertEquals(expected, transport.posts[1].getString("expected_current_backup_id"))
        assertEquals(backupId, transport.posts[1].getString("backup_id"))

        val ordinary = RecordingTransport(backupId)
        CloudBackupClient(
            db = db(),
            canonicalBackup = {
                CanonicalBackupContent("canonical csv",
                    RecordCsvTransferResult("restore-v14"), emptyList())
            },
            config = CloudBackupConfig("https://example.supabase.co", "publishable"),
            http = ordinary
        ).uploadNow(CloudAuthSession("user-1", "token"), now = 1234L)
        assertFalse(ordinary.posts[1].has("resolution"))
        assertFalse(ordinary.posts[1].has("expected_current_backup_id"))
    }

    @Test fun verifiedSameInstallChildRepairsMissedAckWithoutDiscardingLaterEdits() = runBlocking {
        val db = db()
        val dao = db.cloudBackupStateDao()
        dao.getOrCreate()
        dao.bindAccount("user-1")
        dao.recordLocalChange(100L)
        val old = "11111111-1111-4111-8111-111111111111"
        val confirmed = "22222222-2222-4222-8222-222222222222"
        val next = "33333333-3333-4333-8333-333333333333"
        assertEquals(1, dao.acknowledgeIfSnapshotUnchanged("user-1", null, 1L, old, 101L))
        dao.recordLocalChange(102L)
        dao.recordLocalChange(103L)
        val state = dao.get()!!
        val transport = RecoveryTransport(next, verifiedChild(confirmed, old, state.installId, 1L))
        val result = CloudBackupClient(
            db, { CanonicalBackupContent("canonical csv", RecordCsvTransferResult("restore-v14"), emptyList()) },
            CloudBackupConfig("https://example.supabase.co", "publishable"), transport
        ).uploadNow(CloudAuthSession("user-1", "token"), 104L)

        assertTrue(result.acknowledged)
        // The new upload carries only changes made AFTER the recovered snapshot.
        assertEquals(confirmed, transport.uploadParent)
        assertEquals(1L, transport.uploadRevision)
        assertEquals(next, dao.get()!!.localBaseBackupId)
        assertEquals(0L, dao.get()!!.localRevision)
        assertFalse(dao.get()!!.cloudBackupPending)
    }

    @Test fun anotherInstallOrParentOrUnverifiedCurrentCannotBeAutoAdopted() = runBlocking {
        val db = db()
        val dao = db.cloudBackupStateDao()
        dao.getOrCreate()
        dao.bindAccount("user-1")
        dao.recordLocalChange(100L)
        val old = "11111111-1111-4111-8111-111111111111"
        assertEquals(1, dao.acknowledgeIfSnapshotUnchanged("user-1", null, 1L, old, 101L))
        dao.recordLocalChange(102L)
        val state = dao.get()!!
        val remoteId = "22222222-2222-4222-8222-222222222222"
        val next = "33333333-3333-4333-8333-333333333333"
        val candidates = listOf(
            verifiedChild(remoteId, old, UUID.randomUUID().toString(), 1L),
            verifiedChild(remoteId, UUID.randomUUID().toString(), state.installId, 1L),
            verifiedChild(remoteId, old, state.installId, 9L),
            verifiedChild(remoteId, old, state.installId, 1L).put("verified_at", JSONObject.NULL),
            verifiedChild(remoteId, old, state.installId, 1L).put("checksum", JSONObject.NULL)
        )
        for (candidate in candidates) {
            val transport = RecoveryTransport(next, candidate)
            val failure = runCatching {
                CloudBackupClient(
                    db, { CanonicalBackupContent("canonical csv", RecordCsvTransferResult("restore-v14"), emptyList()) },
                    CloudBackupConfig("https://example.supabase.co", "publishable"), transport
                ).uploadNow(CloudAuthSession("user-1", "token"), 104L)
            }.exceptionOrNull()
            assertEquals("LINEAGE_CONFLICT", failure?.message)
            assertEquals(null, transport.uploadParent)
            assertEquals(old, dao.get()!!.localBaseBackupId)
            assertEquals(1L, dao.get()!!.localRevision)
        }
    }

    private fun verifiedChild(id: String, parent: String, install: String, revision: Long) =
        JSONObject()
            .put("backup_id", id)
            .put("status", "CURRENT")
            .put("backup_format_version", 14)
            .put("schema_version", 13)
            .put("local_revision", revision)
            .put("parent_backup_id", parent)
            .put("install_id", install)
            .put("verified_at", "2026-11-09T00:44:39Z")
            .put("checksum", "a".repeat(64))
            .put("checksum_algorithm", "SHA-256")

    private class RecoveryTransport(
        private val backupId: String,
        private val remoteCurrent: JSONObject
    ) : CloudHttpTransport {
        var uploadParent: String? = null
        var uploadRevision: Long? = null
        override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse =
            when {
                url.endsWith("cloud-backup-current") ->
                    CloudHttpResponse(200, JSONObject().put("current", remoteCurrent).toString())
                url.endsWith("cloud-backup-upload-url") -> {
                    val request = JSONObject(body)
                    uploadParent = request.optString("parent_backup_id")
                    uploadRevision = request.getLong("local_revision")
                    CloudHttpResponse(200, JSONObject().put("backup_id", backupId)
                        .put("upload_url", "https://r2.invalid/" + backupId).toString())
                }
                else -> CloudHttpResponse(200, JSONObject().put("backup_id", backupId)
                    .put("status", "CURRENT").toString())
            }
        override suspend fun putBytes(url: String, bytes: ByteArray) = CloudHttpResponse(200)
    }

    private class DiscoveryTransport(
        private val response: CloudHttpResponse
    ) : CloudHttpTransport {
        var url: String = ""
        var body: String = ""

        override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse {
            this.url = url
            this.body = body
            return response
        }

        override suspend fun putBytes(url: String, bytes: ByteArray): CloudHttpResponse =
            error("PUT is not used by current discovery")
    }

    private class RecordingTransport(private val backupId: String) : CloudHttpTransport {
        val posts = mutableListOf<JSONObject>()
        override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse {
            if (url.endsWith("cloud-backup-current")) {
                return CloudHttpResponse(200, "{\"current\":null}")
            }
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
