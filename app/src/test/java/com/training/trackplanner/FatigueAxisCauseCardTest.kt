package com.training.trackplanner

import com.training.trackplanner.analysis.coach.CoachFatigueCause
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachFatigueCauseType
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.readiness.BodyPartPressure
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.TodayFatigueAxisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueAxisCauseCardTest {
    private val localAxis = TodayFatigueAxisState(FatigueTarget.LOCAL_MUSCULAR.label, FatigueLevel.HIGH)

    @Test
    fun dominantExerciseShowsAloneWhenItIsAtLeastOneAndHalfTimesSecond() {
        assertEquals(listOf("스쿼트"), axisContributorLabels(localAxis, summary("스쿼트" to 45.0, "런지" to 30.0)))
    }

    @Test
    fun closeTopExercisesShowTogetherAndDuplicateDisplayNamesAreMerged() {
        assertEquals(
            listOf("스쿼트", "런지"),
            axisContributorLabels(localAxis, summary("스쿼트" to 22.0, "스쿼트" to 22.0, "런지" to 30.0))
        )
    }

    @Test
    fun normalAxisDoesNotExposeContributors() {
        val normalAxis = localAxis.copy(level = FatigueLevel.NORMAL)

        assertTrue(axisContributorLabels(normalAxis, summary("스쿼트" to 45.0)).isEmpty())
    }

    @Test
    fun localMusclePrimaryDetailUsesTopThreeMusclesInsteadOfExercises() {
        val values = axisPrimaryDetailValues(
            axis = localAxis,
            summary = summary("스쿼트" to 90.0),
            bodyPartPressures = listOf(
                bodyPart("quads", 90),
                bodyPart("hamstrings", 80),
                bodyPart("glutes", 70),
                bodyPart("shoulders", 60)
            )
        )

        assertEquals(listOf("대퇴사두", "햄스트링", "둔근"), values)
        assertTrue("스쿼트" !in values)
    }

    @Test
    fun localMuscleRankingDeduplicatesKeysAndUsesStableKeyForTies() {
        assertEquals(
            listOf("대퇴사두", "둔근", "햄스트링"),
            localMuscleLabels(
                listOf(
                    bodyPart("quads", 90),
                    bodyPart("quads", 85),
                    bodyPart("hamstrings", 70),
                    bodyPart("glutes", 70)
                )
            )
        )
    }

    @Test
    fun localMuscleDetailHandlesFewerThanThreeAndExactEmptyWording() {
        assertEquals(listOf("회전근개"), localMuscleLabels(listOf(bodyPart("rotator_cuff", 75))))
        assertTrue(localMuscleLabels(emptyList()).isEmpty())
        assertEquals("피로한 근육", LOCAL_MUSCLE_DETAIL_LABEL)
        assertEquals("두드러지게 피로한 근육이 없습니다.", LOCAL_MUSCLE_EMPTY_TEXT)
        assertEquals("주의할 피로 축과 주요 내용", FATIGUE_AXIS_CAUSE_CARD_TITLE)
    }

    @Test
    fun otherAxisStillUsesContributingExercises() {
        val neuralAxis = TodayFatigueAxisState(FatigueTarget.NEUROMUSCULAR.label, FatigueLevel.HIGH)
        val summary = summaryForAxis(neuralAxis, "데드리프트" to 50.0)

        assertEquals(
            listOf("데드리프트"),
            axisPrimaryDetailValues(neuralAxis, summary, listOf(bodyPart("quads", 90)))
        )
    }

    private fun summary(vararg causes: Pair<String, Double>): CoachFatigueCauseSummary {
        return summaryForAxis(localAxis, *causes)
    }

    private fun summaryForAxis(
        axis: TodayFatigueAxisState,
        vararg causes: Pair<String, Double>
    ): CoachFatigueCauseSummary {
        val items = causes.mapIndexed { index, (label, score) ->
            CoachFatigueCause(
                rank = index + 1,
                label = label,
                detail = "test",
                contributionScore = score,
                affectedAxes = listOf(axis.label),
                sourceType = CoachFatigueCauseType.EXERCISE,
                axisContributionScores = mapOf(axis.label to score)
            )
        }
        return CoachFatigueCauseSummary(
            windowDays = 14,
            causes = items,
            headline = "test",
            isDataSufficient = true
        )
    }

    private fun bodyPart(key: String, score: Int): BodyPartPressure =
        BodyPartPressure(
            key = key,
            score = score,
            level = FatigueLevel.HIGH,
            pressure = score / 100.0
        )
}
