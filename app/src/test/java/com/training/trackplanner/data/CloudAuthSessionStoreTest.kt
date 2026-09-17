package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudAuthSessionStoreTest {
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
