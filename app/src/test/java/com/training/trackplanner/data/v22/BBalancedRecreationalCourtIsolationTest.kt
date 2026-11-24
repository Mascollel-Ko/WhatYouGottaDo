package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepositoryProvider
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BBalancedRecreationalCourtIsolationTest {
    @Test
    fun recordedBadmintonRemainsHistoryButIsNotPrescribedAsRepsAndSets() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val metadata = CanonicalExerciseMetadataRepositoryProvider.get(context)
        val exercises = SeedData.exercises(context).associateBy { it.stableKey }
        val court = exercises.getValue("ex_ae9ecdbc")
        assertTrue(court.activityKind == "SPORT_SESSION")
        assertTrue(court.planningEligibility == "FATIGUE_ONLY")
        val history = listOf(
            V22HistoryRow(LocalDate.of(2026, 11, 10), "배드민턴", court.stableKey,
                "스포츠", 1, 0.0, null, 5400.0, 7.0)
        )
        val request = V22GenerationRequest(
            name = "court context, strength plan",
            weeks = 4, days = 4, mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14),
            sessionMinutes = 60,
            blockProgram = BBlockProgramDefinition(
                listOf(BBlockDefinition(weeks = 4, goal = BBlockGoal.BALANCED))
            )
        )
        val generated = BBalanced12WeekEngine.build(
            history, request, exercises, metadata.muscleRelations(),
            metadata.coreCatalog(), metadata.badmintonObjectiveCatalog(),
            BBalancedTaskCatalog.fromAssets(context)
        )
        assertTrue(generated.weeks.flatMap { it.sessions }
            .flatMap { it.items }.none {
                exercises[it.stableKey]?.activityKind == "SPORT_SESSION" ||
                    exercises[it.stableKey]?.planningEligibility in setOf("FATIGUE_ONLY", "ANALYSIS_ONLY")
            })
    }
}
