package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudAuthSessionStoreTest {
    @Before fun prepareCipher() { CloudSessionCipherProvider.testOverride = TestCloudSessionCipher }
    @After fun resetCipher() { CloudSessionCipherProvider.testOverride = null }

    @Test fun migratesOldPlaintextToEncryptedBlobWithoutLosingLogin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("cloud_auth_session", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("user_id", "11111111-1111-4111-8111-111111111111")
            .putString("access_token", "original-private-access")
            .putString("refresh_token", "original-private-refresh")
            .putLong("expires_at", 123456L).commit()
        val restored = CloudAuthSessionStore(context).read()
        assertEquals("original-private-access", restored?.accessToken)
        assertTrue(prefs.contains("secure_session_v1"))
        assertTrue(!prefs.contains("access_token"))
        assertTrue(!prefs.contains("refresh_token"))
        assertTrue(!prefs.getString("secure_session_v1", "")!!.contains("original-private-access"))
    }

    @Test fun sessionPersistsAndLogoutKeepsInstallationChoice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = CloudAuthSessionStore(context)
        store.clear()
        store.setFirstLaunchChoice()
        val session = CloudAuthSession(
            userId = "11111111-1111-4111-8111-111111111111",
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = 4_000L,
            displayEmail = "person@example.com"
        )
        store.write(session)
        assertEquals(session, store.read())
        store.clear()
        assertNull(store.read())
        assertTrue(store.hasFirstLaunchChoice())
    }
}
