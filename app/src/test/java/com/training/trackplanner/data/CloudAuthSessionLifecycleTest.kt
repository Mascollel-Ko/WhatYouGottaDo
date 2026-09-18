package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudAuthSessionLifecycleTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val config = CloudBackupConfig("https://example.supabase.co", "publishable")
    private val session = CloudAuthSession(
        userId = "11111111-1111-4111-8111-111111111111",
        accessToken = "access",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 1_000L,
        displayEmail = "person@example.com"
    )

    @Before
    fun clearSession() {
        CloudAuthSessionManager.forApplication(context).clear()
    }

    @Test
    fun homeAndCommunityRepositoriesObserveOneCanonicalSession() {
        val home = CloudAuthRepository(context)
        val community = CloudAuthRepository(context)

        CloudAuthSessionManager.forApplication(context).persist(session)

        assertEquals(session, home.session.value)
        assertEquals(session, community.session.value)
    }

    @Test
    fun processRecreationAndAppUpdateEquivalentKeepPersistedSession() {
        val store = CloudAuthSessionStore(context)
        store.write(session)

        val recreatedManager = CloudAuthSessionManager(context, store)

        assertEquals(session, recreatedManager.currentSession())
        assertEquals(session, store.read())
    }

    @Test
    fun validAccessTokenDoesNotRefresh() = runBlocking {
        val manager = CloudAuthSessionManager(context)
        manager.persist(session.copy(expiresAtEpochSeconds = 10_000L))
        var requests = 0
        val repository = repository(manager) { requests += 1; error("unexpected refresh") }

        val result = repository.refreshIfNeededDetailed(nowEpochSeconds = 1_000L)

        assertEquals(CloudAuthRefreshResult.Valid(session.copy(expiresAtEpochSeconds = 10_000L)), result)
        assertEquals(0, requests)
    }

    @Test
    fun transientNetworkFailurePreservesSessionAndDoesNotLogOut() = runBlocking {
        val manager = CloudAuthSessionManager(context)
        manager.persist(session)
        val repository = repository(manager) { throw IOException("offline") }

        val result = repository.refreshIfNeededDetailed(nowEpochSeconds = 1_000L)

        assertTrue(result is CloudAuthRefreshResult.TransientFailure)
        assertEquals(session, manager.currentSession())
        assertEquals(session, CloudAuthSessionStore(context).read())
        assertEquals(session, repository.refreshIfNeeded(nowEpochSeconds = 1_000L))
    }

    @Test
    fun serverFiveHundredPreservesSessionAndCanRetry() = runBlocking {
        val manager = CloudAuthSessionManager(context)
        manager.persist(session)
        val repository = repository(manager) {
            CloudAuthTokenResponse(503, "{\"error\":\"temporarily unavailable\"}")
        }

        val result = repository.refreshIfNeededDetailed(nowEpochSeconds = 1_000L)

        assertTrue(result is CloudAuthRefreshResult.TransientFailure)
        assertEquals(session, manager.currentSession())
    }

    @Test
    fun definitiveInvalidRefreshResponseClearsCanonicalSession() = runBlocking {
        val manager = CloudAuthSessionManager(context)
        manager.persist(session)
        val repository = repository(manager) {
            CloudAuthTokenResponse(400, "{\"error_code\":\"refresh_token_not_found\"}")
        }

        val result = repository.refreshIfNeededDetailed(nowEpochSeconds = 1_000L)

        assertEquals(CloudAuthRefreshResult.TerminalFailure("SESSION_EXPIRED"), result)
        assertNull(manager.currentSession())
        assertNull(CloudAuthSessionStore(context).read())
    }

    @Test
    fun explicitLogoutClearsTheSharedSessionForAllConsumers() {
        val home = CloudAuthRepository(context)
        val community = CloudAuthRepository(context)
        CloudAuthSessionManager.forApplication(context).persist(session)

        home.logout()

        assertNull(home.session.value)
        assertNull(community.session.value)
        assertFalse(home.currentSession() != null)
    }

    @Test
    fun successfulRefreshPublishesNewAccessTokenToSharedConsumers() = runBlocking {
        val manager = CloudAuthSessionManager(context)
        manager.persist(session)
        val refreshedAccess = "refreshed-access"
        val repository = repository(manager) {
            CloudAuthTokenResponse(
                200,
                JSONObject()
                    .put("access_token", refreshedAccess)
                    .put("refresh_token", "refreshed-refresh")
                    .put("expires_in", 3600)
                    .put("user", JSONObject()
                        .put("id", session.userId)
                        .put("email", session.displayEmail))
                    .toString()
            )
        }
        val observer = CloudAuthRepository(context, config, manager)

        repository.refreshIfNeededDetailed(nowEpochSeconds = 1_000L)

        assertEquals(refreshedAccess, observer.session.value?.accessToken)
        assertEquals("refreshed-refresh", observer.session.value?.refreshToken)
    }

    private fun repository(
        manager: CloudAuthSessionManager,
        response: suspend () -> CloudAuthTokenResponse
    ): CloudAuthRepository = CloudAuthRepository(
        context = context,
        config = config,
        sessionManager = manager,
        tokenClient = object : CloudAuthTokenClient {
            override suspend fun post(
                config: CloudBackupConfig,
                grantType: String,
                body: JSONObject
            ): CloudAuthTokenResponse = response()
        }
    )
}
