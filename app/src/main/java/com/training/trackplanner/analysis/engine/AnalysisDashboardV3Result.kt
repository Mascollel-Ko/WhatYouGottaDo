package com.training.trackplanner.analysis.engine

import com.training.trackplanner.analysis.metrics.CommonLoadMetricsResult
import com.training.trackplanner.analysis.metrics.CommonPlanProjectionMetricsResult
import com.training.trackplanner.analysis.metrics.CommonStrengthMetricsResult
import com.training.trackplanner.analysis.metrics.CommonTaxonomyMetricsResult
import com.training.trackplanner.analysis.methods.AnalysisMethodResult
import java.time.LocalDate

data class AnalysisDashboardV3Result(
    val today: LocalDate,
    val commonLoadMetrics: CommonLoadMetricsResult,
    val commonStrengthMetrics: CommonStrengthMetricsResult,
    val commonTaxonomyMetrics: CommonTaxonomyMetricsResult,
    val commonPlanProjectionMetrics: CommonPlanProjectionMetricsResult,
    val methodResults: List<AnalysisMethodResult>,
    val debugWarnings: List<String> = emptyList()
)
