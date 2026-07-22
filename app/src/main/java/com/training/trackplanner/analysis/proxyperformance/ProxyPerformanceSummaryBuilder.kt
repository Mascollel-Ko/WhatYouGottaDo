package com.training.trackplanner.analysis.proxyperformance

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

object ProxyPerformanceSummaryBuilder {
    fun build(
        today: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile? = null,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
    ): ProxyPerformanceSummary {
        val observationResult = ProxyPerformanceObservationBuilder.build(
            entriesWithSets = entriesWithSets,
            exercises = exercises,
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            dailyMetrics = dailyMetrics,
            initialProfile = initialProfile
        )
        return ProxyPerformanceEstimator.build(
            observations = observationResult.observations,
            generatedAtDate = today,
            initialDiagnostics = observationResult.diagnostics
        )
    }
}
