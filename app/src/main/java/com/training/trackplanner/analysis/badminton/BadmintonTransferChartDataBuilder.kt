package com.training.trackplanner.analysis.badminton

import java.util.Locale

class BadmintonTransferChartDataBuilder {
    fun build(metrics: BadmintonTransferMetrics): BadmintonTransferChartData =
        BadmintonTransferChartData(
            axisShareBars = axisShareBars(metrics.axisShare7d),
            transferTypeShareBars = transferTypeShareBars(metrics.transferTypeShare7d),
            windowComparisonBars = windowComparisonBars(metrics.axisShare7d, metrics.axisShare28d),
            topExerciseBars = topExerciseBars(metrics.topTransferExercises7d)
        )

    private fun axisShareBars(shares: Map<BadmintonTransferAxis, Double>): List<BadmintonTransferBarItem> =
        BadmintonTransferAxis.entries.map { axis ->
            percentBar(axis.displayName, shares[axis] ?: 0.0, axis.name)
        }

    private fun transferTypeShareBars(shares: Map<BadmintonTransferType, Double>): List<BadmintonTransferBarItem> =
        listOf(
            BadmintonTransferType.DIRECT,
            BadmintonTransferType.SUPPORTIVE,
            BadmintonTransferType.GENERAL_STRENGTH,
            BadmintonTransferType.LOW
        ).map { type ->
            percentBar(type.displayName, shares[type] ?: 0.0, type.name)
        }

    private fun windowComparisonBars(
        shares7d: Map<BadmintonTransferAxis, Double>,
        shares28d: Map<BadmintonTransferAxis, Double>
    ): List<BadmintonTransferBarItem> =
        BadmintonTransferConstants.recommendationPriority.flatMap { axis ->
            listOf(
                percentBar("${axis.displayName} 7일", shares7d[axis] ?: 0.0, axis.name),
                percentBar("${axis.displayName} 28일", shares28d[axis] ?: 0.0, axis.name)
            )
        }

    private fun topExerciseBars(
        exercises: List<BadmintonTransferExerciseStimulus>
    ): List<BadmintonTransferBarItem> {
        val max = exercises.maxOfOrNull { exercise -> exercise.stimulus } ?: 0.0
        return exercises.map { exercise ->
            BadmintonTransferBarItem(
                label = exercise.exerciseName,
                value = if (max <= BadmintonTransferConstants.EPSILON) 0.0 else exercise.stimulus / max,
                valueLabel = formatStimulus(exercise.stimulus)
            )
        }
    }

    private fun percentBar(label: String, share: Double, colorKey: String): BadmintonTransferBarItem =
        BadmintonTransferBarItem(
            label = label,
            value = share.coerceIn(0.0, 1.0),
            valueLabel = formatPercent(share),
            colorKey = colorKey
        )

    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.0f%%", value.coerceIn(0.0, 1.0) * 100.0)

    private fun formatStimulus(value: Double): String =
        String.format(Locale.US, "%.1f", value)
}
