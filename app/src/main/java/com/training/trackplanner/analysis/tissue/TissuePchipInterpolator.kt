package com.training.trackplanner.analysis.tissue

import kotlin.math.abs

object TissuePchipInterpolator {
    fun value(knots: List<TissueRecoveryKnot>, x: Double): Double {
        require(knots.size >= 2)
        require(knots.zipWithNext().all { (left, right) -> left.elapsedHours < right.elapsedHours })
        if (x <= knots.first().elapsedHours) return knots.first().value
        if (x >= knots.last().elapsedHours) return knots.last().value

        val h = DoubleArray(knots.size - 1) { i ->
            knots[i + 1].elapsedHours - knots[i].elapsedHours
        }
        val delta = DoubleArray(knots.size - 1) { i ->
            (knots[i + 1].value - knots[i].value) / h[i]
        }
        val slopes = slopes(h, delta)
        val i = knots.binarySearch { it.elapsedHours.compareTo(x) }.let { index ->
            if (index >= 0) return knots[index].value else (-index - 2).coerceIn(0, knots.lastIndex - 1)
        }
        val t = (x - knots[i].elapsedHours) / h[i]
        val t2 = t * t
        val t3 = t2 * t
        val interpolated =
            (2 * t3 - 3 * t2 + 1) * knots[i].value +
                (t3 - 2 * t2 + t) * h[i] * slopes[i] +
                (-2 * t3 + 3 * t2) * knots[i + 1].value +
                (t3 - t2) * h[i] * slopes[i + 1]
        return interpolated.coerceIn(
            minOf(knots[i].value, knots[i + 1].value),
            maxOf(knots[i].value, knots[i + 1].value)
        )
    }

    private fun slopes(h: DoubleArray, delta: DoubleArray): DoubleArray {
        if (delta.size == 1) return doubleArrayOf(delta[0], delta[0])
        val result = DoubleArray(delta.size + 1)
        for (i in 1 until result.lastIndex) {
            result[i] = if (delta[i - 1] == 0.0 || delta[i] == 0.0 || delta[i - 1] * delta[i] < 0.0) {
                0.0
            } else {
                val w1 = 2 * h[i] + h[i - 1]
                val w2 = h[i] + 2 * h[i - 1]
                (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])
            }
        }
        result[0] = endpointSlope(h[0], h[1], delta[0], delta[1])
        result[result.lastIndex] = endpointSlope(
            h[h.lastIndex],
            h[h.lastIndex - 1],
            delta[delta.lastIndex],
            delta[delta.lastIndex - 1]
        )
        return result
    }

    private fun endpointSlope(h0: Double, h1: Double, delta0: Double, delta1: Double): Double {
        val slope = ((2 * h0 + h1) * delta0 - h0 * delta1) / (h0 + h1)
        if (slope * delta0 <= 0.0) return 0.0
        if (delta0 * delta1 < 0.0 && abs(slope) > abs(3 * delta0)) return 3 * delta0
        return slope
    }
}
