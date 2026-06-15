package com.training.trackplanner.analysis.engine

import com.training.trackplanner.analysis.core.AnalysisDateProvider
import com.training.trackplanner.analysis.core.AnalysisInputCollector
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.metrics.CommonLoadMetrics
import com.training.trackplanner.analysis.metrics.CommonPlanProjectionMetrics
import com.training.trackplanner.analysis.metrics.CommonStrengthMetrics
import com.training.trackplanner.analysis.metrics.CommonTaxonomyMetrics
import com.training.trackplanner.analysis.methods.AnalysisMethodRegistry

class AnalysisEngineV3(
    private val inputCollector: AnalysisInputCollector,
    private val dateProvider: AnalysisDateProvider = SystemAnalysisDateProvider(),
    private val methodRegistry: AnalysisMethodRegistry = AnalysisMethodRegistry.disabledFor300()
) {
    suspend fun analyze(): AnalysisDashboardV3Result {
        val today = dateProvider.today()
        val input = inputCollector.collect(today)
        val commonLoadMetrics = CommonLoadMetrics.calculate(input)
        val commonStrengthMetrics = CommonStrengthMetrics.calculate(input)
        val commonTaxonomyMetrics = CommonTaxonomyMetrics.calculate(input)
        val commonPlanProjectionMetrics = CommonPlanProjectionMetrics.calculate(
            input = input,
            loadMetrics = commonLoadMetrics
        )
        val methodResults = methodRegistry
            .enabledMethods()
            .map { method -> method.analyze(input) }
        val debugWarnings = buildList {
            if (methodResults.isNotEmpty()) {
                add("AnalysisEngineV3 methods are enabled before UI review.")
            }
            if (input.exerciseMetadataMap.isEmpty()) {
                add("No exercise metadata was available for V3 metrics.")
            }
        }

        return AnalysisDashboardV3Result(
            today = today,
            commonLoadMetrics = commonLoadMetrics,
            commonStrengthMetrics = commonStrengthMetrics,
            commonTaxonomyMetrics = commonTaxonomyMetrics,
            commonPlanProjectionMetrics = commonPlanProjectionMetrics,
            methodResults = methodResults,
            debugWarnings = debugWarnings
        )
    }
}
