package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class CoachingSignalsBuilder(
    private val sleepAnalyzer: SleepRecoverySignalAnalyzer = SleepRecoverySignalAnalyzer(),
    private val rpeAnalyzer: RpeAutoregulationAnalyzer = RpeAutoregulationAnalyzer(),
    private val jointTendonAnalyzer: JointTendonWarningAnalyzer = JointTendonWarningAnalyzer(),
    private val courtAnalyzer: CourtDurationRecoveryAnalyzer = CourtDurationRecoveryAnalyzer()
) {
    fun build(
        today: LocalDate,
        dailyMetrics: List<DailyMetric>,
        checkIns: List<DailyCheckIn>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
        history: List<DailyFatigueResult>
    ): CoachingSignalsSummary {
        val sleep = sleepAnalyzer.analyze(today, dailyMetrics, checkIns)
        return CoachingSignalsSummary(
            sleep = sleep,
            rpe = rpeAnalyzer.analyze(today, entriesWithSets, exercises, sleep),
            jointTendon = jointTendonAnalyzer.analyze(
                today = today,
                checkIns = checkIns,
                history = history,
                entriesWithSets = entriesWithSets,
                exercises = exercises,
                runtimeMetadataCatalog = runtimeMetadataCatalog,
                sleepSignal = sleep
            ),
            courtRecovery = courtAnalyzer.analyze(
                today = today,
                entriesWithSets = entriesWithSets,
                exercises = exercises,
                runtimeMetadataCatalog = runtimeMetadataCatalog,
                checkIns = checkIns,
                history = history,
                sleepSignal = sleep
            )
        )
    }
}
