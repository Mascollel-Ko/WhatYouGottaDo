package com.training.trackplanner.analysis.core

object AnalysisStimulusRpePolicy {
    fun modifier(rpe: Double?): Double = when {
        rpe == null || !rpe.isFinite() -> 1.00
        rpe < 7.0 -> 0.90
        rpe < 8.0 -> 1.00
        rpe < 9.0 -> 1.05
        rpe < 10.0 -> 1.10
        else -> 1.15
    }
}
