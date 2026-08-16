package com.training.trackplanner.analysis.trends

object TrendMetricSelectionPolicy {
    private val retiredMetricNames = setOf(
        "BADMINTON_TRAINING",
        "COURT_VOLUME",
        "FOOTWORK_REACTIVE",
        "BADMINTON_SUPPORT"
    )

    fun restore(
        savedName: String?,
        available: List<TrendMetricId>,
        preferred: TrendMetricId,
        fallbackIndex: Int = 0
    ): TrendMetricId {
        val restored = savedName
            ?.takeUnless(retiredMetricNames::contains)
            ?.let { name -> TrendMetricId.entries.firstOrNull { metric -> metric.name == name } }
            ?.takeIf(available::contains)
        return restored
            ?: preferred.takeIf(available::contains)
            ?: available.getOrNull(fallbackIndex)
            ?: available.firstOrNull()
            ?: preferred
    }
}
