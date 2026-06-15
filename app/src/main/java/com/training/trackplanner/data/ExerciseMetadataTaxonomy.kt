package com.training.trackplanner.data

enum class MovementPattern {
    SQUAT,
    HINGE,
    LUNGE,
    PUSH_HORIZONTAL,
    PUSH_VERTICAL,
    PULL_HORIZONTAL,
    PULL_VERTICAL,
    CARRY,
    ROTATION,
    ANTI_ROTATION,
    LOCOMOTION,
    JUMP,
    HOP,
    BOUND,
    FOOTWORK,
    MOBILITY,
    ISOLATION,
    PREHAB
}

enum class MovementCategory {
    STRENGTH,
    HYPERTROPHY,
    POWER,
    PLYOMETRIC,
    SPEED,
    REACTIVE,
    STABILITY,
    MOBILITY,
    PREHAB,
    CONDITIONING,
    SKILL_DRILL,
    TEST,
    RECOVERY
}

enum class CompoundType {
    COMPOUND,
    ISOLATION,
    HYBRID,
    DRILL
}

enum class FatigueForceType {
    PUSH,
    PULL,
    HINGE,
    SQUAT,
    ROTATE,
    BRACE,
    CARRY,
    LAND,
    DECELERATE,
    ACCELERATE
}

enum class Plane {
    SAGITTAL,
    FRONTAL,
    TRANSVERSE,
    MULTI_PLANAR
}

enum class FatigueLaterality {
    BILATERAL,
    UNILATERAL,
    ALTERNATING,
    ASYMMETRIC
}

enum class AxialLoadLevel {
    NONE,
    LOW,
    MODERATE,
    HIGH
}

enum class FatigueTrainingRole {
    MAIN_STRENGTH,
    SECONDARY_STRENGTH,
    ACCESSORY,
    POWER,
    PLYOMETRIC,
    SPEED_REACTIVE,
    STABILITY,
    PREHAB,
    MOBILITY,
    CONDITIONING,
    SKILL,
    TEST,
    RECOVERY
}

enum class BadmintonTransferRole {
    NONE,
    FOOTWORK,
    REACTION,
    ACCELERATION,
    DECELERATION,
    LUNGE_REACH,
    JUMP_LANDING,
    OVERHEAD_POWER,
    ROTATION_POWER,
    ANTI_ROTATION_STABILITY,
    GRIP_FOREARM,
    SHOULDER_CARE,
    CONDITIONING
}

enum class FatigueCategory {
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
    LOW_FATIGUE_REHAB
}

enum class AdaptiveBaselineGroup {
    SYSTEMIC,
    HEAVY_LOWER,
    HINGE,
    SQUAT_PATTERN,
    UPPER_PUSH,
    UPPER_PULL,
    SHOULDER_OVERHEAD,
    GRIP_FOREARM,
    BADMINTON_COURT,
    DECELERATION,
    ELASTIC_SSC,
    ROTATION_POWER,
    ANTI_ROTATION,
    RECOVERY_LOW_LOAD
}

enum class RecoveryDecayProfile {
    SHORT,
    MEDIUM,
    LONG,
    VERY_LONG,
    MINIMAL
}

enum class MetadataConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NEEDS_REVIEW
}

enum class ProgressMetricType {
    ESTIMATED_1RM,
    VOLUME_LOAD,
    REPS_AT_LOAD,
    TIME_OR_DISTANCE,
    MAX_REPS_TEST,
    QUALITY_BASED,
    NOT_PROGRESS_TARGET
}

enum class StrengthProgressionGroup {
    NONE,
    SQUAT,
    HINGE,
    LUNGE,
    UPPER_PUSH_HORIZONTAL,
    UPPER_PUSH_VERTICAL,
    UPPER_PULL_HORIZONTAL,
    UPPER_PULL_VERTICAL,
    CARRY,
    CORE,
    BADMINTON_TEST
}

enum class HypertrophyVolumeGroup {
    NONE,
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    QUADS,
    POSTERIOR_CHAIN,
    GLUTES,
    CALVES,
    CORE,
    FOREARM_GRIP
}

enum class MainLiftGroup {
    NONE,
    SQUAT,
    DEADLIFT,
    BENCH_PRESS,
    OVERHEAD_PRESS,
    PULL_UP,
    ROW,
    LUNGE
}

enum class AccessoryContributionGroup {
    NONE,
    UPPER_PUSH_ACCESSORY,
    UPPER_PULL_ACCESSORY,
    LOWER_ACCESSORY,
    POSTERIOR_CHAIN_ACCESSORY,
    SHOULDER_CARE,
    ARM_ACCESSORY,
    CORE_STABILITY,
    GRIP_FOREARM,
    BADMINTON_SUPPORT
}

enum class BadmintonTransferStrength {
    NONE,
    GENERAL,
    SUPPORTIVE,
    DIRECT
}

enum class CourtMovementType {
    NONE,
    SPLIT_STEP,
    FIRST_STEP,
    LATERAL_MOVE,
    CROSSOVER,
    FRONT_LUNGE,
    REAR_COURT,
    MULTI_DIRECTION,
    REACTION_RANDOM,
    JUMP_LANDING,
    DECELERATION,
    RECOVERY_STEP
}

enum class BadmintonSkillTarget {
    NONE,
    FOOTWORK_SPEED,
    FIRST_STEP_REACTION,
    DECELERATION_CONTROL,
    LUNGE_REACH,
    REAR_COURT_RECOVERY,
    JUMP_LANDING_CONTROL,
    OVERHEAD_POWER,
    ROTATION_SEQUENCING,
    ANTI_ROTATION_STABILITY,
    GRIP_ENDURANCE,
    SHOULDER_DURABILITY,
    CONDITIONING
}

enum class JointStressTag {
    SHOULDER,
    ELBOW,
    WRIST,
    LOW_BACK,
    HIP,
    KNEE,
    ANKLE_ACHILLES
}

enum class StabilityDemandLevel {
    NONE,
    LOW,
    MODERATE,
    HIGH
}

enum class MobilityDemandLevel {
    NONE,
    LOW,
    MODERATE,
    HIGH
}

enum class BalanceContributionTag {
    UPPER_PUSH,
    UPPER_PULL,
    LOWER_PUSH,
    LOWER_PULL,
    HINGE,
    SQUAT_PATTERN,
    UNILATERAL_LOWER,
    UNILATERAL_UPPER,
    ROTATION,
    ANTI_ROTATION,
    SCAPULAR_STABILITY,
    SHOULDER_CARE,
    POSTERIOR_CHAIN,
    ANTERIOR_CHAIN,
    GRIP_FOREARM,
    CALF_ACHILLES,
    HIP_STABILITY,
    KNEE_CONTROL,
    ANKLE_STIFFNESS
}

enum class AnalysisEligibility {
    FATIGUE,
    STRENGTH_PROGRESS,
    HYPERTROPHY_VOLUME,
    BADMINTON_TRANSFER,
    BALANCE,
    RECOVERY_ONLY,
    TEST_ONLY,
    EXCLUDED_FROM_ANALYSIS
}
