package com.training.trackplanner.analysis.readiness

import java.time.LocalDate
import java.time.LocalDateTime

enum class ReadinessStatus {
    READY,
    CAUTION,
    FATIGUED,
    LIMITED
}

enum class AnalysisConfidence {
    LOW,
    MEDIUM_LOW,
    MEDIUM,
    HIGH
}

enum class FatigueDetailType {
    SYSTEMIC,
    NEURAL_HEAVY,
    NEURAL_SPEED,
    LOCAL_BODY_PART,
    BADMINTON_COURT,
    RECOVERY,
    PERFORMANCE,
    PAIN,
    ADAPTIVE_BASELINE
}

enum class FatigueLevel {
    LOW,
    NORMAL,
    ELEVATED,
    HIGH,
    VERY_HIGH,
    LIMITED
}

enum class FatigueCategoryKey {
    SYSTEMIC,
    NEURAL_HEAVY,
    NEURAL_SPEED,
    LOCAL_MUSCLE,
    DECELERATION,
    ELASTIC_SSC,
    ROTATION_POWER,
    ANTI_ROTATION,
    OVERHEAD_REPETITION,
    GRIP_FOREARM,
    BADMINTON_COURT
}

enum class BaselineTrend {
    RISING,
    STABLE,
    FALLING,
    INSUFFICIENT_DATA
}

data class MetricDisplayItem(
    val label: String,
    val value: String,
    val description: String? = null
)

data class FatigueDetailSection(
    val type: FatigueDetailType,
    val title: String,
    val level: FatigueLevel,
    val summary: String,
    val metrics: List<MetricDisplayItem>,
    val relatedCategories: List<String>,
    val restrictedTargets: List<String>
)

data class TodayReadinessSummary(
    val status: ReadinessStatus,
    val headline: String,
    val shortReason: String,
    val primaryReasons: List<String>,
    val recommendedModes: List<String>,
    val restrictedModes: List<String>,
    val confidence: AnalysisConfidence,
    val detailSections: List<FatigueDetailSection>,
    val adaptiveBaselineNotes: List<String>,
    val generatedAt: LocalDateTime,
    val fatiguePresentation: FatiguePresentationSnapshot? = null
)

data class DailyLoadContribution(
    val date: LocalDate,
    val exerciseId: Long,
    val entryId: Long,
    val exerciseName: String,
    val recoveryDecayProfile: String,
    val categoryLoads: Map<FatigueCategoryKey, Double>,
    val bodyPartLoads: Map<String, Double>,
    val baselineGroupLoads: Map<String, Double>,
    val completedSets: Int,
    val totalReps: Int,
    val durationMinutes: Double?,
    val averageRpe: Double? = null
)

data class DailyAnalysisLoad(
    val date: LocalDate,
    val categoryLoads: Map<FatigueCategoryKey, Double>,
    val bodyPartLoads: Map<String, Double>,
    val baselineGroupLoads: Map<String, Double>,
    val completedEntryCount: Int,
    val completedSetCount: Int,
    val contributions: List<DailyLoadContribution>
)

data class ResidualFatigueSnapshot(
    val residualByCategory: Map<FatigueCategoryKey, Double>,
    val residualByBodyPart: Map<String, Double>,
    val residualByAdaptiveBaselineGroup: Map<String, Double>,
    val highestResidualCategories: List<FatigueCategoryKey>,
    val highestResidualBodyParts: List<String>
)

data class BaselineStat(
    val rollingMean: Double,
    val rollingStd: Double,
    val zScore: Double?,
    val percentile: Double?,
    val ewmaBaseline: Double,
    val pressure: Double?,
    val trend: BaselineTrend,
    val confidence: AnalysisConfidence,
    val sampleDays: Int
)

data class StatisticalBaselineSnapshot(
    val categoryStats: Map<FatigueCategoryKey, BaselineStat>,
    val baselineGroupStats: Map<String, BaselineStat>,
    val bodyPartStats: Map<String, BaselineStat>,
    val overallConfidence: AnalysisConfidence
)

data class AdaptiveBaselineSnapshot(
    val toleranceByCategory: Map<FatigueCategoryKey, Double>,
    val toleranceByBaselineGroup: Map<String, Double>,
    val toleranceByBodyPart: Map<String, Double>,
    val confidenceByCategory: Map<FatigueCategoryKey, AnalysisConfidence>,
    val trendByCategory: Map<FatigueCategoryKey, BaselineTrend>,
    val successfulExposureCountByCategory: Map<FatigueCategoryKey, Int>,
    val failedExposureCountByCategory: Map<FatigueCategoryKey, Int>,
    val dataSufficiency: AnalysisConfidence,
    val baselineAdjustmentNotes: List<String>
)

data class FatiguePressure(
    val key: String,
    val currentResidualLoad: Double,
    val adaptiveTolerance: Double?,
    val rollingMean: Double,
    val rollingStd: Double,
    val zScore: Double?,
    val percentile: Double?,
    val pressure: Double?,
    val level: FatigueLevel,
    val confidence: AnalysisConfidence,
    val baselineTrend: BaselineTrend
)

data class FatiguePressureSnapshot(
    val categoryPressures: Map<FatigueCategoryKey, FatiguePressure>,
    val baselineGroupPressures: Map<String, FatiguePressure>,
    val bodyPartPressures: Map<String, FatiguePressure>
)

data class RecoverySignalSnapshot(
    val sleepSignal: FatigueLevel,
    val fatigueSignal: FatigueLevel,
    val sorenessSignal: FatigueLevel,
    val stressSignal: FatigueLevel,
    val moodSignal: FatigueLevel,
    val overallRecoveryLevel: FatigueLevel,
    val recoveryPenalty: Int,
    val affectedBodyParts: List<String>,
    val confidence: AnalysisConfidence,
    val reasons: List<String>
)

data class PerformanceSignalSnapshot(
    val sameLoadRpeIncrease: Boolean,
    val sameLoadRepsDrop: Boolean,
    val estimated1RmDrop: Boolean,
    val plannedSetFailure: Boolean,
    val testPerformanceDrop: Boolean,
    val footworkTestDrop: Boolean,
    val level: FatigueLevel,
    val confidence: AnalysisConfidence,
    val reasons: List<String>
) {
    val hasDrop: Boolean
        get() = reasons.isNotEmpty()
}

data class PainInput(
    val date: LocalDate,
    val score: Int?,
    val bodyPart: String? = null,
    val acute: Boolean = false,
    val worsening: Boolean = false
)

data class PainGateSnapshot(
    val isLimited: Boolean,
    val level: FatigueLevel,
    val restrictedTargets: List<String>,
    val reasons: List<String>,
    val confidence: AnalysisConfidence
)

data class AdaptiveOutcomeSignal(
    val date: LocalDate,
    val category: FatigueCategoryKey,
    val recoveryStable: Boolean = true,
    val painPresent: Boolean = false,
    val performanceDrop: Boolean = false,
    val fatigueIncrease: Boolean = false
)

data class TodayReadinessEngineInput(
    val today: LocalDate,
    val exercises: List<com.training.trackplanner.data.Exercise>,
    val entriesWithSets: List<com.training.trackplanner.data.WorkoutEntryWithSets>,
    val dailyMetrics: List<com.training.trackplanner.data.DailyMetric>,
    val dailyCheckIns: List<com.training.trackplanner.data.DailyCheckIn> = emptyList(),
    val initialProfile: com.training.trackplanner.data.InitialUserProfile? = null,
    val runtimeMetadataCatalog: com.training.trackplanner.data.RuntimeExerciseMetadataCatalog =
        com.training.trackplanner.data.RuntimeExerciseMetadataCatalog.EMPTY,
    val painInputs: List<PainInput> = emptyList(),
    val adaptiveOutcomeSignals: List<AdaptiveOutcomeSignal> = emptyList()
)
