package com.training.trackplanner.analysis.readiness

data class FatiguePresentationSnapshot(
    val overallScore: Int,
    val highForceNeuralScore: Int,
    val systemicMuscularScore: Int,
    val localMuscularScore: Int,
    val highSpeedScore: Int,
    val reactiveScore: Int,
    val highCategories: List<FatigueCategoryPressure>,
    val highBodyParts: List<BodyPartPressure>,
    val gate: TrainingGateSnapshot,
    val reduceToday: List<FatigueRestriction>,
    val availableToday: List<FatigueAvailability>,
    val reasons: List<String>
)

data class FatigueCategoryPressure(
    val category: FatigueCategoryKey,
    val score: Int,
    val level: FatigueLevel,
    val pressure: Double?
)

data class BodyPartPressure(
    val key: String,
    val score: Int,
    val level: FatigueLevel,
    val pressure: Double?
)

data class TrainingGateSnapshot(
    val overallScore: Int,
    val heavyLowerRestricted: Boolean,
    val highImpactRestricted: Boolean,
    val codReactiveRestricted: Boolean,
    val upperPushRestricted: Boolean,
    val overheadRestricted: Boolean,
    val gripForearmRestricted: Boolean,
    val volumeFactor: Double,
    val rpeCap: Int?,
    val reasons: List<String>
)

data class FatigueRestriction(
    val key: String,
    val label: String,
    val score: Int,
    val reasons: List<String>
)

data class FatigueAvailability(
    val key: String,
    val label: String,
    val reason: String
)
