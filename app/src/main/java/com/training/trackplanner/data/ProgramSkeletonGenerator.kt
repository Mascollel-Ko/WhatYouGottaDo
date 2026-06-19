package com.training.trackplanner.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class ProgramGoal {
    BADMINTON_SUPPORT,
    STRENGTH,
    BODYBUILDING,
    FUNCTIONAL_CONDITIONING
}

enum class ProgramPeriodizationType {
    AUTO,
    STEP_DELOAD,
    BADMINTON_WAVE,
    DAILY_UNDULATING,
    LINEAR_STRENGTH
}

data class ProgramSkeletonRequest(
    val name: String,
    val goal: ProgramGoal,
    val weeklyTrainingDays: Int,
    val sessionMinutes: Int,
    val availableEquipment: Set<String>,
    val excludedExerciseText: String,
    val badmintonTransferRatio: Double,
    val sportStrengthRatio: String,
    val periodizationType: ProgramPeriodizationType
)

data class ProgramWeekPlan(
    val weekIndex: Int,
    val weekType: String,
    val volumeMultiplier: Double,
    val intensityMultiplier: Double,
    val heavyExposureLimit: Int,
    val lowerBodyFatigueLimit: Double,
    val axialLoadLimit: Int,
    val plyometricLimit: Int,
    val deloadFlag: Boolean
)

data class ProgramSkeletonItem(
    val localId: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseId: Long,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val prescription: String,
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val selectionReason: String,
    val weightSource: String
)

data class GeneratedProgramSkeleton(
    val suggestedName: String,
    val durationDays: Int,
    val request: ProgramSkeletonRequest,
    val periodizationType: ProgramPeriodizationType,
    val weekPlans: List<ProgramWeekPlan>,
    val items: List<ProgramSkeletonItem>,
    val warnings: List<String> = emptyList()
)

class ProgramSkeletonGenerator {
    fun generate(
        request: ProgramSkeletonRequest,
        exercises: List<Exercise>,
        history: List<WorkoutEntryWithSets>,
        today: LocalDate = LocalDate.now(),
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
    ): GeneratedProgramSkeleton {
        val periodizationType = choosePeriodization(request)
        val weekPlans = weekPlans(periodizationType)
        val selectedEquipment = request.availableEquipment.ifEmpty { defaultEquipment }
        val excludedTerms = request.excludedExerciseText
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val selectableExercises = exercises.filter { exercise ->
            exercise.isRuntimeProgramSelectable(runtimeMetadataCatalog.resolve(exercise))
        }
        val baseCandidates = selectableExercises
            .filter { exercise -> exercise.category.isNotBlank() }
            .filter { exercise -> exercise.matchesEquipment(selectedEquipment) }
            .filter { exercise ->
                excludedTerms.none { term ->
                    exercise.name.contains(term, ignoreCase = true)
                }
            }
            .filterNot { exercise ->
                exercise.metadataConfidence == MetadataConfidence.NEEDS_REVIEW.name &&
                    exercise.analysisEligibility.isBlank()
            }
            .ifEmpty { selectableExercises.filter { it.category.isNotBlank() } }

        val trainingDays = request.weeklyTrainingDays.coerceIn(2, 5)
        val dayOfWeeks = dayOfWeeks(trainingDays)
        val targetItemsPerDay = when {
            request.sessionMinutes <= 30 -> 3
            request.sessionMinutes <= 45 -> 4
            request.sessionMinutes <= 60 -> 5
            else -> 6
        }
        val historyIndex = HistoryWeightIndex(history, exercises)
        val generated = mutableListOf<ProgramSkeletonItem>()
        weekPlans.forEach { weekPlan ->
            val weeklyHighFatigue = mutableMapOf<String, Int>()
            dayOfWeeks.forEachIndexed { dayIndex, dayOfWeek ->
                val daySelected = mutableListOf<Exercise>()
                val dayHighFatigueLimit = if (request.goal == ProgramGoal.BADMINTON_SUPPORT) 1 else 2
                repeat(targetItemsPerDay) { slotIndex ->
                    val candidate = baseCandidates
                        .asSequence()
                        .filter { candidate ->
                            !candidate.highFatigueBlocked(
                                daySelected = daySelected,
                                weeklyHighFatigue = weeklyHighFatigue,
                                weekPlan = weekPlan,
                                request = request,
                                dayHighFatigueLimit = dayHighFatigueLimit,
                                runtimeMetadataCatalog = runtimeMetadataCatalog
                            )
                        }
                        .maxByOrNull { candidate ->
                            candidate.selectionScore(
                                request = request,
                                dayIndex = dayIndex,
                                slotIndex = slotIndex,
                                daySelected = daySelected,
                                selectedEquipment = selectedEquipment,
                                runtimeMetadataCatalog = runtimeMetadataCatalog
                            )
                        }
                        ?: baseCandidates.maxByOrNull { candidate ->
                            candidate.selectionScore(
                                request = request,
                                dayIndex = dayIndex,
                                slotIndex = slotIndex,
                                daySelected = daySelected,
                                selectedEquipment = selectedEquipment,
                                runtimeMetadataCatalog = runtimeMetadataCatalog
                            )
                        }
                    if (candidate != null) {
                        daySelected += candidate
                        val candidateMetadata = runtimeMetadataCatalog.resolve(candidate)
                        if (candidate.fatigueCost(candidateMetadata) >= 4.0) {
                            val bucket = candidate.fatigueBucket(candidateMetadata)
                            weeklyHighFatigue[bucket] = (weeklyHighFatigue[bucket] ?: 0) + 1
                        }
                        val prescription = candidate.prescription(request, weekPlan)
                        val weight = historyIndex.suggestWeight(
                            exercise = candidate,
                            targetReps = prescription.reps,
                            weekPlan = weekPlan,
                            today = today
                        )
                        generated += ProgramSkeletonItem(
                            localId = "${weekPlan.weekIndex}-$dayOfWeek-${slotIndex + 1}-${candidate.id}",
                            weekNumber = weekPlan.weekIndex,
                            dayOfWeek = dayOfWeek,
                            orderIndex = slotIndex + 1,
                            exerciseId = candidate.id,
                            exerciseName = candidate.name,
                            category = candidate.category,
                            restSeconds = candidate.defaultRestSeconds,
                            prescription = listOf(
                                weekPlan.weekType,
                                prescription.label,
                                "RPE ${prescription.targetRpe}",
                                weight.source
                            ).joinToString(" · "),
                            setCount = prescription.setCount,
                            reps = prescription.reps,
                            weightKg = weight.weightKg,
                            seconds = prescription.seconds,
                            selectionReason = candidate.reasonLabel(candidateMetadata),
                            weightSource = weight.source
                        )
                    }
                }
            }
        }
        val suggestedName = request.name.ifBlank { request.defaultProgramName() }
        val warnings = buildList {
            if (baseCandidates.size < 8) add("조건에 맞는 운동 후보가 적어 일부 패턴이 반복될 수 있습니다.")
            if (request.availableEquipment.isEmpty()) add("운동기구를 선택하지 않아 기본 기구 전체를 허용했습니다.")
            if (selectableExercises.size < exercises.size) {
                add("스포츠 경기/세션 기록은 프로그램 생성 후보에서 제외했습니다.")
            }
        }
        return GeneratedProgramSkeleton(
            suggestedName = suggestedName,
            durationDays = 28,
            request = request,
            periodizationType = periodizationType,
            weekPlans = weekPlans,
            items = generated,
            warnings = warnings
        )
    }

    private fun choosePeriodization(request: ProgramSkeletonRequest): ProgramPeriodizationType {
        if (request.periodizationType != ProgramPeriodizationType.AUTO) return request.periodizationType
        return when {
            request.badmintonTransferRatio >= 0.55 -> ProgramPeriodizationType.BADMINTON_WAVE
            request.goal == ProgramGoal.STRENGTH -> ProgramPeriodizationType.LINEAR_STRENGTH
            request.weeklyTrainingDays >= 4 -> ProgramPeriodizationType.DAILY_UNDULATING
            else -> ProgramPeriodizationType.STEP_DELOAD
        }
    }

    private fun weekPlans(type: ProgramPeriodizationType): List<ProgramWeekPlan> =
        when (type) {
            ProgramPeriodizationType.BADMINTON_WAVE -> listOf(
                ProgramWeekPlan(1, "중간 부하", 0.90, 0.85, 1, 7.0, 1, 1, false),
                ProgramWeekPlan(2, "높은 부하", 1.05, 0.92, 2, 8.0, 2, 1, false),
                ProgramWeekPlan(3, "낮은 부하", 0.70, 0.78, 1, 5.0, 1, 0, true),
                ProgramWeekPlan(4, "중상 전이", 0.95, 0.88, 1, 7.0, 1, 1, false)
            )
            ProgramPeriodizationType.DAILY_UNDULATING -> listOf(
                ProgramWeekPlan(1, "기술/볼륨", 0.90, 0.82, 1, 7.0, 1, 1, false),
                ProgramWeekPlan(2, "고중량", 1.00, 0.92, 2, 8.0, 2, 1, false),
                ProgramWeekPlan(3, "파워/볼륨", 1.10, 0.88, 2, 8.0, 2, 1, false),
                ProgramWeekPlan(4, "디로드", 0.65, 0.72, 1, 5.0, 1, 0, true)
            )
            ProgramPeriodizationType.LINEAR_STRENGTH -> listOf(
                ProgramWeekPlan(1, "기초 강도", 0.95, 0.82, 1, 7.0, 1, 0, false),
                ProgramWeekPlan(2, "강도 상승", 0.90, 0.90, 2, 8.0, 2, 0, false),
                ProgramWeekPlan(3, "고강도", 0.80, 0.97, 2, 8.0, 2, 0, false),
                ProgramWeekPlan(4, "디로드", 0.60, 0.75, 1, 5.0, 1, 0, true)
            )
            else -> listOf(
                ProgramWeekPlan(1, "누적 1", 0.85, 0.85, 1, 7.0, 1, 1, false),
                ProgramWeekPlan(2, "누적 2", 1.00, 0.90, 2, 8.0, 2, 1, false),
                ProgramWeekPlan(3, "누적 3", 1.15, 0.95, 2, 8.0, 2, 1, false),
                ProgramWeekPlan(4, "디로드", 0.65, 0.75, 1, 5.0, 1, 0, true)
            )
        }

    private fun dayOfWeeks(trainingDays: Int): List<Int> =
        when (trainingDays.coerceIn(2, 5)) {
            2 -> listOf(1, 4)
            3 -> listOf(1, 3, 5)
            4 -> listOf(1, 2, 4, 6)
            else -> listOf(1, 2, 3, 5, 6)
        }

    private data class Prescription(
        val setCount: Int,
        val reps: Int,
        val seconds: Int,
        val targetRpe: Int,
        val label: String
    )

    private fun Exercise.prescription(
        request: ProgramSkeletonRequest,
        weekPlan: ProgramWeekPlan
    ): Prescription {
        val role = trainingRole.uppercase(Locale.US)
        val categoryToken = movementCategory.uppercase(Locale.US)
        val isTimed = category == "스포츠" || category == "유산소운동" || mode.contains("시간")
        val baseSets = when {
            weekPlan.deloadFlag -> 2
            role == FatigueTrainingRole.MAIN_STRENGTH.name -> 4
            request.goal == ProgramGoal.BODYBUILDING && volumeLoadEligible -> 4
            role == FatigueTrainingRole.ACCESSORY.name || categoryToken == MovementCategory.PREHAB.name -> 2
            else -> 3
        }
        val setCount = max(1, (baseSets * weekPlan.volumeMultiplier).roundToInt())
        val reps = when {
            isTimed -> 0
            request.goal == ProgramGoal.STRENGTH && !weekPlan.deloadFlag -> if (weekPlan.intensityMultiplier > 0.92) 4 else 5
            request.goal == ProgramGoal.BODYBUILDING -> 10
            role == FatigueTrainingRole.PREHAB.name || categoryToken == MovementCategory.PREHAB.name -> 15
            badmintonTransferStrength == BadmintonTransferStrength.DIRECT.name -> 6
            else -> 8
        }
        val seconds = if (isTimed) {
            when {
                category == "스포츠" -> 15 * 60
                category == "유산소운동" -> 20 * 60
                else -> 30
            }
        } else {
            0
        }
        val rpe = when {
            weekPlan.deloadFlag -> 6
            weekPlan.intensityMultiplier >= 0.95 -> 8
            else -> 7
        }
        val label = if (isTimed) "${setCount}세트 시간형" else "${setCount}x$reps"
        return Prescription(setCount, reps, seconds, rpe, label)
    }

    private data class WeightSuggestion(
        val weightKg: Double,
        val source: String
    )

    private data class HistoricalSet(
        val date: String,
        val reps: Int,
        val weightKg: Double
    )

    private inner class HistoryWeightIndex(
        private val history: List<WorkoutEntryWithSets>,
        private val exercises: List<Exercise>
    ) {
        private val exerciseById = exercises.associateBy { it.id }

        fun suggestWeight(
            exercise: Exercise,
            targetReps: Int,
            weekPlan: ProgramWeekPlan,
            today: LocalDate
        ): WeightSuggestion {
            if (targetReps <= 0 || exercise.category != "근력운동") {
                return WeightSuggestion(0.0, "EMPTY_NEEDS_MANUAL_INPUT")
            }
            val direct = confirmedSets(exercise.id)
            val directRecent = direct
                .filter { it.date >= today.minusDays(90).format(DateTimeFormatter.ISO_LOCAL_DATE) }
                .maxByOrNull { it.date }
            if (directRecent != null) {
                return WeightSuggestion(
                    weightKg = roundWeight(exercise, estimateTargetWeight(directRecent, targetReps, weekPlan, 0.92)),
                    source = "DIRECT_HISTORY_HIGH"
                )
            }
            val directBest = direct.maxByOrNull { it.weightKg * it.reps }
            if (directBest != null) {
                return WeightSuggestion(
                    weightKg = roundWeight(exercise, estimateTargetWeight(directBest, targetReps, weekPlan, 0.86)),
                    source = "DIRECT_HISTORY_MEDIUM"
                )
            }
            val similar = history.flatMap { entryWithSets ->
                val similarExercise = exerciseById[entryWithSets.entry.exerciseId]
                if (similarExercise != null && similarExercise.isSimilarTo(exercise)) {
                    entryWithSets.sets.mapNotNull { set ->
                        if (set.confirmed && set.weightKg > 0.0 && set.reps > 0) {
                            HistoricalSet(entryWithSets.entry.date, set.reps, set.weightKg)
                        } else {
                            null
                        }
                    }
                } else {
                    emptyList()
                }
            }.maxByOrNull { it.date }
            if (similar != null) {
                return WeightSuggestion(
                    weightKg = roundWeight(exercise, estimateTargetWeight(similar, targetReps, weekPlan, 0.75)),
                    source = "SIMILAR_EXERCISE_LOW"
                )
            }
            return WeightSuggestion(0.0, "EMPTY_NEEDS_MANUAL_INPUT")
        }

        private fun confirmedSets(exerciseId: Long): List<HistoricalSet> =
            history
                .filter { entryWithSets -> entryWithSets.entry.exerciseId == exerciseId }
                .flatMap { entryWithSets ->
                    entryWithSets.sets.mapNotNull { set ->
                        if (set.confirmed && set.weightKg > 0.0 && set.reps > 0) {
                            HistoricalSet(entryWithSets.entry.date, set.reps, set.weightKg)
                        } else {
                            null
                        }
                    }
                }

        private fun estimateTargetWeight(
            set: HistoricalSet,
            targetReps: Int,
            weekPlan: ProgramWeekPlan,
            confidenceFactor: Double
        ): Double {
            val e1rm = set.weightKg * (1.0 + set.reps / 30.0)
            val target = e1rm / (1.0 + targetReps / 30.0)
            return target * weekPlan.intensityMultiplier * confidenceFactor
        }

        private fun Exercise.isSimilarTo(other: Exercise): Boolean {
            if (id == other.id) return false
            val samePattern = movementPattern.isNotBlank() && movementPattern == other.movementPattern
            val sameEquipment = equipmentTokens().intersect(other.equipmentTokens()).isNotEmpty()
            val sameMuscle = primaryMuscles.splitTokens().intersect(other.primaryMuscles.splitTokens()).isNotEmpty()
            return samePattern && (sameEquipment || sameMuscle)
        }

        private fun roundWeight(exercise: Exercise, raw: Double): Double {
            if (raw <= 0.0 || raw.isNaN()) return 0.0
            val step = when {
                "DUMBBELL" in exercise.equipmentTokens() -> 2.0
                "CABLE" in exercise.equipmentTokens() || "MACHINE" in exercise.equipmentTokens() -> 5.0
                else -> 2.5
            }
            return (raw / step).toInt().coerceAtLeast(0) * step
        }
    }

    private fun Exercise.highFatigueBlocked(
        daySelected: List<Exercise>,
        weeklyHighFatigue: Map<String, Int>,
        weekPlan: ProgramWeekPlan,
        request: ProgramSkeletonRequest,
        dayHighFatigueLimit: Int,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog
    ): Boolean {
        val runtimeMetadata = runtimeMetadataCatalog.resolve(this)
        val cost = fatigueCost(runtimeMetadata)
        val currentDayHigh = daySelected.count { selected ->
            selected.fatigueCost(runtimeMetadataCatalog.resolve(selected)) >= 4.0
        }
        if (cost >= 4.0 && currentDayHigh >= dayHighFatigueLimit) return true
        if (axialLoadLevel == AxialLoadLevel.HIGH.name &&
            (weeklyHighFatigue["AXIAL"] ?: 0) >= weekPlan.axialLoadLimit
        ) {
            return true
        }
        if (fatigueCategories.splitTokens().contains(FatigueCategory.ELASTIC_SSC.name) &&
            (weeklyHighFatigue["PLYOMETRIC"] ?: 0) >= weekPlan.plyometricLimit
        ) {
            return true
        }
        if (request.goal == ProgramGoal.BADMINTON_SUPPORT &&
            movementPattern == MovementPattern.HINGE.name &&
            cost >= 4.0 &&
            (weeklyHighFatigue["HINGE"] ?: 0) >= 1
        ) {
            return true
        }
        return false
    }

    private fun Exercise.selectionScore(
        request: ProgramSkeletonRequest,
        dayIndex: Int,
        slotIndex: Int,
        daySelected: List<Exercise>,
        selectedEquipment: Set<String>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog
    ): Double {
        val runtimeMetadata = runtimeMetadataCatalog.resolve(this)
        val transfer = badmintonTransferScore(runtimeMetadata)
        val progressBehavior = runtimeMetadata?.progressBehavior
        val baseGoalScore = when (request.goal) {
            ProgramGoal.BADMINTON_SUPPORT -> transfer * 1.2 + stabilityScore(runtimeMetadata)
            ProgramGoal.STRENGTH -> when {
                progressBehavior == ProgressMetricRuntimeBehavior.ESTIMATED_1RM -> 5.0
                progressBehavior in setOf(
                    ProgressMetricRuntimeBehavior.LOAD_REPS,
                    ProgressMetricRuntimeBehavior.VOLUME_LOAD
                ) -> 2.0
                trainingRole in strengthRoles -> 5.0
                volumeLoadEligible -> 2.0
                else -> 0.0
            }
            ProgramGoal.BODYBUILDING -> when {
                progressBehavior in setOf(
                    ProgressMetricRuntimeBehavior.LOAD_REPS,
                    ProgressMetricRuntimeBehavior.VOLUME_LOAD
                ) -> 4.0
                volumeLoadEligible -> 4.0
                trainingRole == FatigueTrainingRole.ACCESSORY.name -> 3.0
                else -> 0.0
            }
            ProgramGoal.FUNCTIONAL_CONDITIONING -> if (movementCategory in functionalCategories) {
                4.0
            } else {
                stabilityScore(runtimeMetadata)
            }
        }
        val badmintonScore = request.badmintonTransferRatio * transfer * 3.0
        val equipmentMatch = if (equipmentTokens().intersect(selectedEquipment).isNotEmpty()) 1.2 else 0.0
        val patternNeed = patternNeedScore(request.goal, dayIndex, slotIndex)
        val redundancyKey = runtimeMetadata?.redundancyGroup?.takeIf { it.isNotBlank() } ?: movementPattern
        val redundancyPenalty = if (daySelected.any { selected ->
                val selectedKey = runtimeMetadataCatalog.resolve(selected)
                    ?.redundancyGroup
                    ?.takeIf { it.isNotBlank() }
                    ?: selected.movementPattern
                selectedKey == redundancyKey
            }
        ) 3.0 else 0.0
        val sameMusclePenalty = if (daySelected.any { it.primaryMuscles == primaryMuscles && primaryMuscles.isNotBlank() }) 1.5 else 0.0
        val fatiguePenalty = fatigueCost(runtimeMetadata) * if (request.badmintonTransferRatio >= 0.55) 0.65 else 0.45
        val confidence = runtimeMetadata?.sourceConfidenceLevel?.ifBlank { metadataConfidence } ?: metadataConfidence
        val confidencePenalty = if (confidence in setOf(MetadataConfidence.LOW.name, "SOURCE_WEAK_BUT_ACCEPTABLE")) 0.5 else 0.0
        return baseGoalScore +
            badmintonScore +
            equipmentMatch +
            patternNeed -
            redundancyPenalty -
            sameMusclePenalty -
            fatiguePenalty -
            confidencePenalty
    }

    private fun Exercise.patternNeedScore(goal: ProgramGoal, dayIndex: Int, slotIndex: Int): Double {
        val desired = when (goal) {
            ProgramGoal.BADMINTON_SUPPORT -> when (dayIndex % 4) {
                0 -> setOf(MovementPattern.LUNGE.name, MovementPattern.HINGE.name, MovementPattern.BOUND.name, MovementPattern.HOP.name)
                1 -> setOf(MovementPattern.PULL_HORIZONTAL.name, MovementPattern.PUSH_HORIZONTAL.name, MovementPattern.PUSH_VERTICAL.name, MovementPattern.PREHAB.name)
                2 -> setOf(MovementPattern.ROTATION.name, MovementPattern.ANTI_ROTATION.name, MovementPattern.CARRY.name, MovementPattern.ISOLATION.name)
                else -> setOf(MovementPattern.FOOTWORK.name, MovementPattern.LOCOMOTION.name, MovementPattern.MOBILITY.name)
            }
            ProgramGoal.STRENGTH -> when (slotIndex) {
                0 -> setOf(MovementPattern.SQUAT.name, MovementPattern.HINGE.name)
                1 -> setOf(MovementPattern.PUSH_HORIZONTAL.name, MovementPattern.PULL_HORIZONTAL.name)
                else -> setOf(MovementPattern.LUNGE.name, MovementPattern.PULL_VERTICAL.name, MovementPattern.ANTI_ROTATION.name)
            }
            ProgramGoal.BODYBUILDING -> when (dayIndex % 5) {
                0 -> setOf(MovementPattern.PUSH_HORIZONTAL.name, MovementPattern.PUSH_VERTICAL.name)
                1 -> setOf(MovementPattern.PULL_HORIZONTAL.name, MovementPattern.PULL_VERTICAL.name)
                2 -> setOf(MovementPattern.SQUAT.name, MovementPattern.LUNGE.name)
                3 -> setOf(MovementPattern.HINGE.name, MovementPattern.ISOLATION.name)
                else -> setOf(MovementPattern.ISOLATION.name, MovementPattern.PREHAB.name)
            }
            ProgramGoal.FUNCTIONAL_CONDITIONING -> setOf(
                MovementPattern.CARRY.name,
                MovementPattern.ROTATION.name,
                MovementPattern.ANTI_ROTATION.name,
                MovementPattern.LOCOMOTION.name,
                MovementPattern.MOBILITY.name
            )
        }
        return if (movementPattern in desired) 3.0 else 0.0
    }

    private fun Exercise.matchesEquipment(selectedEquipment: Set<String>): Boolean {
        val tokens = equipmentTokens()
        if (tokens.isEmpty()) return "BODYWEIGHT" in selectedEquipment
        return tokens.intersect(selectedEquipment).isNotEmpty()
    }

    private fun Exercise.equipmentTokens(): Set<String> =
        listOf(equipment, equipmentTags, mode)
            .joinToString("|")
            .uppercase(Locale.US)
            .replace("바벨", "BARBELL")
            .replace("덤벨", "DUMBBELL")
            .replace("케이블", "CABLE")
            .replace("머신", "MACHINE")
            .replace("케틀벨", "KETTLEBELL")
            .replace("랜드마인", "LANDMINE")
            .replace("밴드", "BAND")
            .replace("맨몸", "BODYWEIGHT")
            .split('|', ',', '/', ' ', '·')
            .map { it.trim() }
            .filter { it in defaultEquipment }
            .toSet()

    private fun Exercise.badmintonTransferScore(runtimeMetadata: RuntimeExerciseMetadata?): Double =
        when (runtimeMetadata?.transferLevel) {
            RuntimeBadmintonTransferLevel.DIRECT -> 5.0
            RuntimeBadmintonTransferLevel.SUPPORTIVE -> 3.5
            RuntimeBadmintonTransferLevel.GENERAL -> 1.5
            RuntimeBadmintonTransferLevel.NONE -> 0.0
            RuntimeBadmintonTransferLevel.UNKNOWN, null -> when (badmintonTransferStrength) {
            BadmintonTransferStrength.DIRECT.name -> 5.0
            BadmintonTransferStrength.SUPPORTIVE.name -> 3.5
            BadmintonTransferStrength.GENERAL.name -> 1.5
            else -> if (badmintonTransferRoles.isNotBlank() || badmintonSkillTargets.isNotBlank()) 1.0 else 0.0
            }
        }

    private fun Exercise.stabilityScore(runtimeMetadata: RuntimeExerciseMetadata?): Double =
        when {
            runtimeMetadata?.badmintonPhysicalQualities?.values?.any { token ->
                token.contains("STABILITY") || token.contains("BALANCE") || token.contains("CONTROL")
            } == true -> 2.0
            trainingRole == FatigueTrainingRole.PREHAB.name -> 2.0
            stabilityDemandLevel == StabilityDemandLevel.HIGH.name -> 2.0
            balanceContributionTags.isNotBlank() -> 1.0
            else -> 0.0
        }

    private fun Exercise.fatigueCost(runtimeMetadata: RuntimeExerciseMetadata? = null): Double {
        val weighted = systemicLoadWeight * 2.0 +
            neuralHeavyWeight * 1.5 +
            neuralSpeedWeight * 1.2 +
            decelerationWeight * 1.4 +
            elasticSscWeight * 1.4
        val axial = when (axialLoadLevel) {
            AxialLoadLevel.HIGH.name -> 1.4
            AxialLoadLevel.MODERATE.name -> 0.7
            else -> 0.0
        }
        val categoryCost = when (movementCategory) {
            MovementCategory.PLYOMETRIC.name, MovementCategory.POWER.name, MovementCategory.REACTIVE.name -> 1.0
            MovementCategory.PREHAB.name, MovementCategory.MOBILITY.name, MovementCategory.RECOVERY.name -> -1.0
            else -> 0.0
        }
        val canonicalCost = when (runtimeMetadata?.stressMagnitudeHint?.uppercase(Locale.ROOT)) {
            "VERY_HIGH" -> 5.0
            "HIGH" -> 4.2
            "MODERATE" -> 3.0
            "LOW" -> 1.5
            else -> null
        }
        return canonicalCost ?: (1.0 + weighted + axial + categoryCost).coerceIn(1.0, 5.0)
    }

    private fun Exercise.fatigueBucket(runtimeMetadata: RuntimeExerciseMetadata? = null): String =
        when {
            runtimeMetadata?.primaryStressProfile?.isNotBlank() == true -> runtimeMetadata.primaryStressProfile
            axialLoadLevel == AxialLoadLevel.HIGH.name -> "AXIAL"
            movementPattern == MovementPattern.HINGE.name -> "HINGE"
            FatigueCategory.ELASTIC_SSC.name in fatigueCategories.splitTokens() -> "PLYOMETRIC"
            else -> movementPattern.ifBlank { "GENERAL" }
        }

    private fun Exercise.reasonLabel(runtimeMetadata: RuntimeExerciseMetadata? = null): String =
        when {
            runtimeMetadata?.transferLevel == RuntimeBadmintonTransferLevel.DIRECT -> "배드민턴 직접 전이"
            runtimeMetadata?.transferLevel == RuntimeBadmintonTransferLevel.SUPPORTIVE -> "배드민턴 보조 전이"
            badmintonTransferStrength == BadmintonTransferStrength.DIRECT.name -> "배드민턴 직접 전이"
            badmintonTransferStrength == BadmintonTransferStrength.SUPPORTIVE.name -> "배드민턴 보조 전이"
            FatigueCategory.DECELERATION.name in fatigueCategories.splitTokens() -> "감속 제어"
            FatigueCategory.ROTATION_POWER.name in fatigueCategories.splitTokens() -> "회전 파워"
            FatigueCategory.ANTI_ROTATION.name in fatigueCategories.splitTokens() -> "항회전 안정성"
            FatigueCategory.GRIP_FOREARM.name in fatigueCategories.splitTokens() -> "그립/전완"
            trainingRole == FatigueTrainingRole.PREHAB.name -> "저피로 보강"
            else -> movementPattern.ifBlank { category }
        }

    private fun Exercise.isRuntimeProgramSelectable(runtimeMetadata: RuntimeExerciseMetadata?): Boolean =
        when (runtimeMetadata?.planningEligibility?.uppercase(Locale.ROOT)) {
            "PROGRAM_SELECTABLE" -> isActive
            "FATIGUE_ONLY", "ANALYSIS_ONLY", "HIDDEN", "NOT_APPLICABLE" -> false
            else -> isProgramSelectableExercise()
        }

    private fun ProgramSkeletonRequest.defaultProgramName(): String {
        val goalName = when (goal) {
            ProgramGoal.BADMINTON_SUPPORT -> "배드민턴 지원 웨이트"
            ProgramGoal.STRENGTH -> "스트렝스"
            ProgramGoal.BODYBUILDING -> "보디빌딩"
            ProgramGoal.FUNCTIONAL_CONDITIONING -> "기능성 컨디셔닝"
        }
        return "$goalName ${weeklyTrainingDays.coerceIn(2, 5)}일 루틴"
    }

    private fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ' ', ';')
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() }
            .toSet()

    private val defaultEquipment = setOf(
        "BARBELL",
        "DUMBBELL",
        "CABLE",
        "MACHINE",
        "KETTLEBELL",
        "LANDMINE",
        "BAND",
        "BODYWEIGHT"
    )

    private val strengthRoles = setOf(
        FatigueTrainingRole.MAIN_STRENGTH.name,
        FatigueTrainingRole.SECONDARY_STRENGTH.name,
        FatigueTrainingRole.POWER.name
    )

    private val functionalCategories = setOf(
        MovementCategory.STABILITY.name,
        MovementCategory.CONDITIONING.name,
        MovementCategory.MOBILITY.name,
        MovementCategory.SKILL_DRILL.name,
        MovementCategory.PREHAB.name
    )
}
