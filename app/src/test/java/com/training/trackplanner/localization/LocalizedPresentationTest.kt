package com.training.trackplanner.localization

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo
import com.training.trackplanner.analysis.tissue.TissueEducationalInfoScope
import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizedPresentationTest {
    @Test
    fun builtInAndHistoryNamesResolveByStableKeyWhileCustomNamesPassThrough() {
        val english = context(Locale.ENGLISH)

        assertEquals(
            "Barbell Deadlift",
            LocalizedPresentation.exerciseName(english, "barbell_deadlift", "데드리프트")
        )
        assertEquals(
            "내 운동",
            LocalizedPresentation.exerciseName(
                english,
                Exercise(stableKey = "user_123", name = "내 운동", category = "근력운동", isCustom = true)
            )
        )
    }

    @Test
    fun tissueEducationUsesStableIdentityOverlay() {
        val source = TissueEducationalInfo(
            stableKey = "jc_bb531a278d",
            displayNameKo = "경추 복합체",
            anatomicalLocationKo = "위치",
            primaryFunctionsKo = listOf("기능"),
            commonLoadContextsKo = listOf("맥락"),
            shortDescriptionKo = null,
            scope = TissueEducationalInfoScope.JOINT_COMPLEX,
            metadataVersion = "test"
        )

        val english = LocalizedPresentation.tissueEducation(context(Locale.ENGLISH), source)
        assertEquals("Cervical spine complex", english.name)
        assertEquals(
            "The entire neck region extending from below the head to above the shoulders.",
            english.location
        )
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
