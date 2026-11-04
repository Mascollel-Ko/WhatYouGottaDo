package com.training.trackplanner

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BBlockPlateProfileSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun profileAndPlanShareTheSameMinimumPlateSetting() {
        context.getSharedPreferences("training_profile", Context.MODE_PRIVATE)
            .edit().clear().commit()
        assertEquals(1.25, BBlockPlateProfileSettings.load(context), 0.001)
        BBlockPlateProfileSettings.save(context, 0.5)
        assertEquals(0.5, BBlockPlateProfileSettings.load(context), 0.001)
        assertTrue(runCatching { BBlockPlateProfileSettings.save(context, -1.0) }.isFailure)
    }
}
