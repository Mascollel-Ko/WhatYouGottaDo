package com.training.trackplanner.localization

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo
import com.training.trackplanner.analysis.tissue.TissueEducationalInfoScope
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.coach.CoachingSignalSeverity
import com.training.trackplanner.analysis.coach.CourtDurationRecoveryMessageCode
import com.training.trackplanner.analysis.coach.CourtDurationRecoverySignal
import com.training.trackplanner.analysis.coach.JointTendonWarningMessageCode
import com.training.trackplanner.analysis.coach.JointTendonWarningSignal
import com.training.trackplanner.analysis.coach.RpeAutoregulationSignal
import com.training.trackplanner.analysis.coach.SleepRecoveryMessageCode
import com.training.trackplanner.analysis.coach.SleepRecoverySignal
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.TrainingProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.YearMonth
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
    fun builtInDescriptionsResolveByStableKeyWhileCustomDescriptionsPassThrough() {
        val english = context(Locale.ENGLISH)

        assertEquals(
            "Hold the EZ-bar with your elbows fixed and curl it upward. This variation builds biceps volume while reducing wrist strain.",
            LocalizedPresentation.exerciseDescription(
                english,
                Exercise(
                    stableKey = "ex_8633d8db",
                    name = "EZ바 컬",
                    category = "근력운동",
                    description = "EZ바를 잡고 팔꿈치를 고정해 말아 올린다. 손목 부담을 줄이면서 이두 볼륨을 쌓기 좋다."
                )
            )
        )
        assertEquals(
            "내가 적은 설명",
            LocalizedPresentation.exerciseDescription(
                english,
                Exercise(
                    stableKey = "user_123",
                    name = "내 운동",
                    category = "근력운동",
                    description = "내가 적은 설명",
                    isCustom = true
                )
            )
        )
    }

    @Test
    fun seededProgramNamesResolveByStableIdentityWhileUserNamesPassThrough() {
        val english = context(Locale.ENGLISH)

        assertEquals(
            "Badminton Strength Support - 4 Weeks",
            LocalizedPresentation.programName(
                english,
                TrainingProgram(stableKey = "3", name = "배드민턴 웨이트 보조 4주", durationDays = 28)
            )
        )
        assertEquals(
            "내 프로그램",
            LocalizedPresentation.programName(
                english,
                TrainingProgram(stableKey = "user_program_test", name = "내 프로그램", durationDays = 28)
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

    @Test
    fun dynamicUiTextPreservesRuntimeValues() {
        val english = context(Locale.ENGLISH)

        assertEquals(
            "Currently RPE: 8.5",
            LocalizedPresentation.uiText(english, "현재 RPE: 8.5")
        )
    }

    @Test
    fun coachingSignalsLocalizeFromSemanticCodesInsteadOfKoreanSentenceMatching() {
        val english = context(Locale.ENGLISH)
        val korean = context(Locale.KOREAN)
        val insufficient = CoachingSignalsSummary.empty().sleep

        assertEquals(
            "Insufficient sleep data",
            LocalizedPresentation.sleepRecoverySignal(english, insufficient).headline
        )
        assertEquals(
            "There is not enough recent sleep data to calculate a sleep-adjusted signal.",
            LocalizedPresentation.sleepRecoverySignal(english, insufficient).detail
        )
        assertEquals("수면 데이터 부족", LocalizedPresentation.sleepRecoverySignal(korean, insufficient).headline)

        val signalPairs = buildList {
            SleepRecoveryMessageCode.entries.forEach { code ->
                val signal = SleepRecoverySignal(
                    recentAverageHours = 6.5,
                    baselineAverageHours = 7.5,
                    sleepDeficitHours = 1.0,
                    severity = when (code) {
                        SleepRecoveryMessageCode.INSUFFICIENT_DATA -> CoachingSignalSeverity.NONE
                        SleepRecoveryMessageCode.CAUTION -> CoachingSignalSeverity.CAUTION
                        SleepRecoveryMessageCode.WATCH -> CoachingSignalSeverity.WATCH
                        SleepRecoveryMessageCode.INFO -> CoachingSignalSeverity.INFO
                    },
                    headline = "ignored source",
                    detail = "ignored source",
                    messageCode = code
                )
                add(
                    LocalizedPresentation.sleepRecoverySignal(english, signal).combined() to
                        LocalizedPresentation.sleepRecoverySignal(korean, signal).combined()
                )
            }

            JointTendonWarningMessageCode.entries.forEach { code ->
                val signal = JointTendonWarningSignal(
                    severity = CoachingSignalSeverity.WATCH,
                    headline = "ignored source",
                    detail = "ignored source",
                    relatedStressLabels = listOf("impact"),
                    sleepContext = null,
                    sampleSize = 2,
                    messageCode = code
                )
                add(
                    LocalizedPresentation.jointTendonSignal(english, signal).combined() to
                        LocalizedPresentation.jointTendonSignal(korean, signal).combined()
                )
            }

            CourtDurationRecoveryMessageCode.entries.forEach { code ->
                val signal = CourtDurationRecoverySignal(
                    severity = CoachingSignalSeverity.WATCH,
                    headline = "ignored source",
                    detail = "ignored source",
                    observedThresholdMinutes = if (code == CourtDurationRecoveryMessageCode.INSUFFICIENT_DATA) null else 120,
                    sampleSize = 3,
                    sleepContext = null,
                    messageCode = code
                )
                add(
                    LocalizedPresentation.courtDurationSignal(english, signal).combined() to
                        LocalizedPresentation.courtDurationSignal(korean, signal).combined()
                )
            }

            val rpe = RpeAutoregulationSignal(
                exerciseName = "데드리프트",
                severity = CoachingSignalSeverity.WATCH,
                headline = "ignored source",
                detail = "ignored source",
                sleepContext = "ignored source",
                sampleSize = 5,
                recentAverageRpe = 8.5,
                baselineAverageRpe = 7.0
            )
            add(
                LocalizedPresentation.rpeAutoregulationSignal(english, rpe).combined() to
                    LocalizedPresentation.rpeAutoregulationSignal(korean, rpe).combined()
            )
        }
        assertFalse(signalPairs.joinToString { it.first }.contains(Regex("[가-힣]")))
        assertTrue(signalPairs.all { (_, koreanText) -> koreanText.contains(Regex("[가-힣]")) })

        val localized = listOf(
            LocalizedPresentation.rpeAutoregulationSignal(
                english,
                RpeAutoregulationSignal(
                    exerciseName = "스쿼트",
                    severity = CoachingSignalSeverity.WATCH,
                    headline = "ignored source",
                    detail = "ignored source",
                    sleepContext = "ignored source",
                    sampleSize = 5,
                    recentAverageRpe = 8.5,
                    baselineAverageRpe = 7.0
                )
            ).combined(),
            LocalizedPresentation.jointTendonSignal(
                english,
                JointTendonWarningSignal(
                    severity = CoachingSignalSeverity.WATCH,
                    headline = "ignored source",
                    detail = "ignored source",
                    relatedStressLabels = listOf("impact"),
                    sleepContext = null,
                    sampleSize = 2,
                    messageCode = JointTendonWarningMessageCode.RELATED_EXERCISE_STRESS
                )
            ).combined(),
            LocalizedPresentation.courtDurationSignal(
                english,
                CourtDurationRecoverySignal(
                    severity = CoachingSignalSeverity.CAUTION,
                    headline = "ignored source",
                    detail = "ignored source",
                    observedThresholdMinutes = 120,
                    sampleSize = 3,
                    sleepContext = null,
                    messageCode = CourtDurationRecoveryMessageCode.LONG_DURATION_CAUTION
                )
            ).combined()
        )
        assertFalse(localized.joinToString().contains(Regex("[가-힣]")))
    }

    @Test
    fun datesUseTheActiveEnglishLocaleWithoutChangingKoreanSources() {
        val english = context(Locale.ENGLISH)
        val korean = context(Locale.KOREAN)

        assertEquals("Oct 11, 2027", LocalizedPresentation.uiText(english, "2027년 10월 11일"))
        assertEquals("Oct 11 – Oct 17", LocalizedPresentation.uiText(english, "10월 11일~10월 17일"))
        assertEquals("October 2027 · Week 2", LocalizedPresentation.uiText(english, "2027년 10월 2주"))
        assertEquals("2027년 10월 11일", LocalizedPresentation.uiText(korean, "2027년 10월 11일"))
        assertEquals("Aug 2026", LocalizedPresentation.yearMonth(english, YearMonth.of(2026, 8)))
        assertEquals("2026년 8월", LocalizedPresentation.yearMonth(korean, YearMonth.of(2026, 8)))
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            DayOfWeek.entries.map { LocalizedPresentation.weekday(english, it) }
        )
        assertEquals(
            listOf("월", "화", "수", "목", "금", "토", "일"),
            DayOfWeek.entries.map { LocalizedPresentation.weekday(korean, it) }
        )
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
