package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepositoryProvider
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BBalancedFocusSearchPolicyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val metadata = CanonicalExerciseMetadataRepositoryProvider.get(context)
    private val exercises = SeedData.exercises(context).associateBy { it.stableKey }

    @Test
    fun `T07 T08 T12 fixed and balanced blocks never run hidden multi-focus search`() {
        fun oneBlock(goal: BBlockGoal) = V22GenerationRequest(
            name = "focus policy", weeks = 2, days = 3, mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14), sessionMinutes = 60,
            bGoalImportance = BBalancedGoalImportance(strength = 5, hypertrophy = 1),
            blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 2, goal = goal)))
        )
        fun build(goal: BBlockGoal) = BBalanced12WeekEngine.build(
            rows = emptyList(), request = oneBlock(goal), exercises = exercises,
            muscleRelations = metadata.muscleRelations(), coreCatalog = metadata.coreCatalog(),
            badmintonCatalog = metadata.badmintonObjectiveCatalog(),
            taskCatalog = BBalancedTaskCatalog.fromAssets(context)
        )

        val hypertrophy = build(BBlockGoal.HYPERTROPHY)
        assertEquals(1, hypertrophy.search.evaluatedCandidates)
        assertEquals(BBalancedGoal.HYPERTROPHY, hypertrophy.blocks.single().focus)

        val balanced = build(BBlockGoal.BALANCED)
        assertEquals(1, balanced.search.evaluatedCandidates)
        assertTrue("BALANCED_NO_HIDDEN_FOCUS" in balanced.blocks.single().focusReasons)
        assertEquals("BALANCED_NO_HIDDEN_FOCUS", JSONObject(balanced.metadataJson)
            .getJSONArray("blockSpecs").getJSONObject(0).getString("focusPolicy"))

        val auto = build(BBlockGoal.AUTO)
        assertEquals(BBalancedGoal.entries.size, auto.search.evaluatedCandidates)
        assertNotEquals("Joint production candidate must be material for the public cold-start case",
            auto.reviewBaselineWeeks, auto.weeks)
    }
}
