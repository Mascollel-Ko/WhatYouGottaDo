package com.training.trackplanner.analysis.readiness

class TodayReadinessDecisionEngine(
    private val sentenceBuilder: TodayReadinessSentenceBuilder = TodayReadinessSentenceBuilder()
) {
    fun decide(
        pressure: FatiguePressureSnapshot,
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot,
        adaptiveBaseline: AdaptiveBaselineSnapshot
    ): TodayReadinessDecision {
        val pressureStatus = statusFromPressure(pressure)
        val recoveryAdjusted = applyRecovery(pressureStatus, recovery)
        val performanceAdjusted = applyPerformance(recoveryAdjusted, performance)
        val status = if (pain.isLimited) {
            ReadinessStatus.LIMITED
        } else {
            performanceAdjusted
        }
        val confidence = combinedConfidence(
            adaptiveBaseline.dataSufficiency,
            recovery.confidence,
            performance.confidence,
            pain.confidence
        )
        val sentence = sentenceBuilder.build(
            status = status,
            pressure = pressure,
            recovery = recovery,
            performance = performance,
            pain = pain,
            adaptiveBaseline = adaptiveBaseline
        )
        return TodayReadinessDecision(
            status = status,
            confidence = confidence,
            sentence = sentence
        )
    }

    private fun statusFromPressure(pressure: FatiguePressureSnapshot): ReadinessStatus {
        val categoryPressures = pressure.categoryPressures
        val majorLevels = TodayReadinessConstants.majorCategories
            .mapNotNull { category -> categoryPressures[category]?.level }
        val bodyPartHigh = pressure.bodyPartPressures.values.any { item -> item.level >= FatigueLevel.HIGH }
        val hasVeryHighMajor = majorLevels.any { level -> level == FatigueLevel.VERY_HIGH }
        val hasHighMajor = majorLevels.any { level -> level >= FatigueLevel.HIGH }
        val hasElevated = categoryPressures.values.any { item -> item.level >= FatigueLevel.ELEVATED } ||
            pressure.bodyPartPressures.values.any { item -> item.level >= FatigueLevel.ELEVATED }

        return when {
            hasVeryHighMajor -> ReadinessStatus.FATIGUED
            hasHighMajor -> ReadinessStatus.FATIGUED
            bodyPartHigh -> ReadinessStatus.CAUTION
            hasElevated -> ReadinessStatus.CAUTION
            else -> ReadinessStatus.READY
        }
    }

    private fun applyRecovery(
        current: ReadinessStatus,
        recovery: RecoverySignalSnapshot
    ): ReadinessStatus {
        if (recovery.recoveryPenalty <= 0) return current
        return when (current) {
            ReadinessStatus.READY -> ReadinessStatus.CAUTION
            ReadinessStatus.CAUTION -> if (recovery.recoveryPenalty >= 2) ReadinessStatus.FATIGUED else current
            ReadinessStatus.FATIGUED -> current
            ReadinessStatus.LIMITED -> current
        }
    }

    private fun applyPerformance(
        current: ReadinessStatus,
        performance: PerformanceSignalSnapshot
    ): ReadinessStatus {
        if (!performance.hasDrop) return current
        return when (current) {
            ReadinessStatus.READY -> ReadinessStatus.CAUTION
            ReadinessStatus.CAUTION -> if (performance.level >= FatigueLevel.HIGH) ReadinessStatus.FATIGUED else current
            ReadinessStatus.FATIGUED -> current
            ReadinessStatus.LIMITED -> current
        }
    }

    private fun combinedConfidence(vararg values: AnalysisConfidence): AnalysisConfidence =
        values.minByOrNull { confidence -> confidence.ordinal } ?: AnalysisConfidence.LOW
}

data class TodayReadinessDecision(
    val status: ReadinessStatus,
    val confidence: AnalysisConfidence,
    val sentence: TodayReadinessSentence
)
