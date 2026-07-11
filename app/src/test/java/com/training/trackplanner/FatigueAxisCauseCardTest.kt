package com.training.trackplanner

import com.training.trackplanner.analysis.coach.CoachFatigueCause
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachFatigueCauseType
import com.training.trackplanner.analysis.fatigue.FatigueTarget
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

    private fun summary(vararg causes: Pair<String, Double>): CoachFatigueCauseSummary {
        val items = causes.mapIndexed { index, (label, score) ->
            CoachFatigueCause(
                rank = index + 1,
                label = label,
                detail = "test",
                contributionScore = score,
                affectedAxes = listOf(localAxis.label),
                sourceType = CoachFatigueCauseType.EXERCISE,
                axisContributionScores = mapOf(localAxis.label to score)
            )
        }
        return CoachFatigueCauseSummary(
            windowDays = 14,
            causes = items,
            headline = "test",
            isDataSufficient = true
        )
    }
}
