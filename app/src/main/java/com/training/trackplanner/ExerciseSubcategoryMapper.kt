package com.training.trackplanner

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import java.util.Locale

internal enum class ExerciseSubcategory(
    val broadCategory: String,
    val label: String,
    private vararg val structuredKeywords: String
) {
    STRENGTH_CHEST_PUSH("근력운동", "가슴 / 푸시", "BENCH", "CHEST", "HORIZONTAL_PUSH", "PUSH_UP", "DIP_COMPOUND"),
    STRENGTH_BACK_PULL("근력운동", "등 / 풀", "ROW", "PULL_UP", "VERTICAL_PULL", "HORIZONTAL_PULL", "LAT_", "PULLOVER"),
    STRENGTH_SHOULDERS("근력운동", "어깨", "OVERHEAD_PRESS", "SHOULDER_PRESS", "DELTOID", "REAR_DELT"),
    STRENGTH_ARMS("근력운동", "팔", "BICEPS", "TRICEPS", "ELBOW_FLEXION", "ELBOW_EXTENSION", "BRACHIALIS", "FOREARM_WRIST"),
    STRENGTH_SQUAT_QUAD("근력운동", "스쿼트 / 대퇴사두", "SQUAT", "LEG_PRESS", "KNEE_EXTENSION", "QUAD_", "KNEE_DOMINANT"),
    STRENGTH_HINGE_POSTERIOR("근력운동", "힌지 / 후면사슬", "HINGE", "DEADLIFT", "HIP_THRUST", "HAMSTRING", "POSTERIOR_CHAIN", "GLUTE_"),
    STRENGTH_UNILATERAL_LOWER("근력운동", "편측 하체", "UNILATERAL_LOWER", "SINGLE_LEG", "LUNGE", "STEP_UP"),
    STRENGTH_CORE("근력운동", "코어", "CORE", "ANTI_ROTATION", "TRUNK_ANTI", "LOADED_CARRY", "TOTAL_BODY_STABILITY"),
    STRENGTH_CALF_ANKLE("근력운동", "종아리 / 발목", "CALF", "ANKLE_STIFFNESS"),
    STRENGTH_OTHER("근력운동", "기타"),

    FUNCTIONAL_MOBILITY("기능성운동", "가동성", "MOBILITY", "WARMUP"),
    FUNCTIONAL_STABILITY("기능성운동", "안정성", "STABILITY", "ANTI_ROTATION", "ANTI_EXTENSION"),
    FUNCTIONAL_BALANCE("기능성운동", "균형", "BALANCE", "SINGLE_LEG_STABILITY", "HIP_CONTROL"),
    FUNCTIONAL_CORE_CONTROL("기능성운동", "코어 조절", "CORE", "TRUNK_ANTI", "BRACING", "ANTI_ROTATION"),
    FUNCTIONAL_DECELERATION("기능성운동", "감속", "DECELERATION", "LANDING"),
    FUNCTIONAL_LATERAL("기능성운동", "측면 움직임", "LATERAL", "FRONTAL_PLANE"),
    FUNCTIONAL_ROTATION("기능성운동", "회전", "ROTATION"),
    FUNCTIONAL_FOOTWORK("기능성운동", "풋워크 / 민첩성", "FOOTWORK", "AGILITY", "REACTION", "RUNNING_MECHANICS", "SPRINT_ACCELERATION"),
    FUNCTIONAL_PLYOMETRIC("기능성운동", "플라이오메트릭 / 반응", "PLYOMETRIC", "ELASTIC_SSC", "ANKLE_SSC", "JUMP", "HOP", "BOUND"),
    FUNCTIONAL_SHOULDER_CARE("기능성운동", "어깨 / 견갑 관리", "SCAPULAR", "ROTATOR_CUFF", "SERRATUS", "SHOULDER_STABILITY", "EXTERNAL_ROTATION"),
    FUNCTIONAL_GRIP_FOREARM("기능성운동", "그립 / 전완", "GRIP", "FOREARM", "WRIST"),
    FUNCTIONAL_RECOVERY("기능성운동", "회복 / 저부하 보강", "RECOVERY", "PREHAB", "LOW_LOAD", "CONTROL_STRESS"),
    FUNCTIONAL_OTHER("기능성운동", "기타");

    fun matches(tokens: Set<String>): Boolean = structuredKeywords.any { keyword ->
        tokens.any { token -> token.contains(keyword) }
    }
}

internal object ExerciseSubcategoryMapper {
    fun availableFor(broadCategory: String): List<ExerciseSubcategory> =
        ExerciseSubcategory.entries.filter { it.broadCategory == broadCategory }

    fun categoriesFor(
        exercise: Exercise,
        metadata: RuntimeExerciseMetadata?
    ): Set<ExerciseSubcategory> {
        val candidates = availableFor(exercise.category)
        if (candidates.isEmpty()) return emptySet()
        val other = candidates.last()
        val tokens = structuredTokens(exercise, metadata)
        val matched = candidates.dropLast(1).filterTo(linkedSetOf()) { it.matches(tokens) }
        return matched.ifEmpty { setOf(other) }
    }

    fun matchesSearch(exercise: Exercise, query: String): Boolean =
        query.isBlank() ||
            exercise.name.contains(query, ignoreCase = true) ||
            exercise.detail1.contains(query, ignoreCase = true) ||
            exercise.detail2.contains(query, ignoreCase = true)

    fun countsFor(
        exercises: List<Exercise>,
        broadCategory: String,
        query: String,
        metadataByExerciseId: Map<Long, RuntimeExerciseMetadata>
    ): Map<ExerciseSubcategory, Int> {
        val scoped = exercises.filter { exercise ->
            exercise.category == broadCategory && matchesSearch(exercise, query)
        }
        return availableFor(broadCategory).associateWith { subcategory ->
            scoped.count { exercise ->
                subcategory in categoriesFor(exercise, metadataByExerciseId[exercise.id])
            }
        }
    }

    private fun structuredTokens(
        exercise: Exercise,
        metadata: RuntimeExerciseMetadata?
    ): Set<String> = buildSet {
        fun addStructured(value: String) {
            value.split('|', ',', ';')
                .map { it.trim().uppercase(Locale.ROOT) }
                .filter(String::isNotBlank)
                .forEach(::add)
        }

        metadata?.let { runtime ->
            listOf(
                runtime.activityKind,
                runtime.planningEligibility,
                runtime.movementFamily,
                runtime.movementSubtype,
                runtime.programSlot,
                runtime.redundancyGroup,
                runtime.progressMetricType,
                runtime.strengthProgressionGroup,
                runtime.primaryStressProfile,
                runtime.recoveryDecayProfile,
                runtime.stressMagnitudeHint,
                runtime.badmintonTransferLevel
            ).forEach(::addStructured)
            listOf(
                runtime.analysisEligibility,
                runtime.secondaryStressTags,
                runtime.tendonStressTags,
                runtime.ligamentJointStabilityStressTags,
                runtime.jointImpactStressTags,
                runtime.cognitiveStressTags,
                runtime.sportContextTags,
                runtime.badmintonTransferType,
                runtime.badmintonSkillTargets,
                runtime.badmintonPhysicalQualities
            ).flatMap { it.values }.forEach(::addStructured)
        }
        listOf(
            exercise.movementPattern,
            exercise.movementCategory,
            exercise.primaryMuscles,
            exercise.secondaryMuscles,
            exercise.equipment,
            exercise.trainingRole,
            exercise.fatigueCategories,
            exercise.badmintonTransferRoles,
            exercise.stabilityRoles,
            exercise.accessoryRoles,
            exercise.analysisEligibility
        ).forEach(::addStructured)
    }
}
