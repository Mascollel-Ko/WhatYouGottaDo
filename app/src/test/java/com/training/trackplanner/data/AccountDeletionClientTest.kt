package com.training.trackplanner.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountDeletionClientTest {
    private class RecordingTransport(private val response: CloudHttpResponse) : CloudHttpTransport {
        var url = ""
        var body = ""
        var session: CloudAuthSession? = null
        override suspend fun postJson(url: String, session: CloudAuthSession, body: String): CloudHttpResponse {
            this.url = url
            this.session = session
            this.body = body
            return response
        }
        override suspend fun putBytes(url: String, bytes: ByteArray) = CloudHttpResponse(405)
    }

    private val config = CloudBackupConfig("https://project.supabase.co", "public-anon-key")
    private val fresh = CloudAuthSession("5ae7879a-91e0-4a7c-86b4-3d5809873639", "fresh_access_token")

    @Test fun postsExplicitDeletionConfirmationOnlyToPrivilegedEndpoint() = runBlocking {
        val fake = RecordingTransport(CloudHttpResponse(200, """{"deleted":true}"""))
        val result = AccountDeletionClient(config, fake).delete(fresh, "fresh-google-id-token", "fresh-nonce-for-recheck")
        assertTrue(!result.backupCleanupPending)
        assertEquals("https://project.supabase.co/functions/v1/account-delete", fake.url)
        assertEquals(fresh, fake.session)
        assertTrue(fake.body.contains("DELETE_ACCOUNT"))
        assertTrue(fake.body.contains("google_id_token"))
        assertTrue(fake.body.contains("nonce"))
        assertTrue(!fake.body.contains("service_role"))
    }

    @Test fun serverErrorNeverLooksLikeSuccess() = runBlocking {
        val fake = RecordingTransport(CloudHttpResponse(409, """{"error":"UPLOAD_URLS_STILL_ACTIVE"}"""))
        try {
            AccountDeletionClient(config, fake).delete(fresh, "fresh-google-id-token", "fresh-nonce-for-recheck")
            fail("Expected a retryable failure")
        } catch (error: IllegalStateException) {
            assertEquals("UPLOAD_URLS_STILL_ACTIVE", error.message)
        }
    }

    @Test fun failedOrMalformedSuccessResponsesDoNotDeleteLocally() = runBlocking {
        val fake = RecordingTransport(CloudHttpResponse(200, """{"ok":true}"""))
        try {
            AccountDeletionClient(config, fake).delete(fresh, "fresh-google-id-token", "fresh-nonce-for-recheck")
            fail("Expected failure")
        } catch (error: IllegalStateException) {
            assertEquals("ACCOUNT_DELETION_FAILED", error.message)
        }
    }

    @Test fun errorsAreHumanReadableAndDoNotLeakInternals() {
        assertTrue(AccountDeletionError.message("GOOGLE_ACCOUNT_MISMATCH").contains("다른 Google"))
        assertTrue(AccountDeletionError.message("UPLOAD_URLS_STILL_ACTIVE").contains("아직 삭제되지"))
        assertTrue(!AccountDeletionError.message("SECRET_EXCEPTION").contains("SECRET"))
    }
}
