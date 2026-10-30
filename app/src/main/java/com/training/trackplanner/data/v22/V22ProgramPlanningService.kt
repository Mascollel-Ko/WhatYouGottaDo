package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.ExerciseMetadataUserOverrideDao
import com.training.trackplanner.data.UserMuscleRoleCodec
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramDayIntensity
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramTrainingSlot
import com.training.trackplanner.data.WorkoutDao
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog

internal class V22ProgramPlanningService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val canonicalMetadataRepository: CanonicalExerciseMetadataRepository,
    private val bBalancedTaskCatalog: BBalancedTaskCatalog,
    private val userOverrideDao: ExerciseMetadataUserOverrideDao? = null
) {
    suspend fun generate(request: V22GenerationRequest): V22GeneratedProgram {
        val effectiveRequest = if (request.mode == V22Mode.B) request.copy(weeks = 12) else request
        val history = V22HistoryInput.fromWorkoutHistory(workoutDao.entriesWithSetsUntil(effectiveRequest.asOf.toString()))
        val exercises = exerciseDao.allExercises().associateBy(Exercise::stableKey)
        if (effectiveRequest.mode != V22Mode.B) {
            val plan = V22PythonParityEngine.build(history, effectiveRequest)
            return V22GeneratedProgram(plan, plan.toSkeleton(effectiveRequest, exercises))
        }
        val balanced = BBalanced12WeekEngine.build(
            rows = history,
            request = effectiveRequest,
            exercises = exercises,
            muscleRelations = UserMuscleRoleCodec.effectiveRelations(
                canonicalMetadataRepository.muscleRelations(), userOverrideDao?.all().orEmpty(), exercises.values.toList()
            ),
            coreCatalog = canonicalMetadataRepository.coreCatalog(),
            badmintonCatalog = canonicalMetadataRepository.badmintonObjectiveCatalog(),
            taskCatalog = bBalancedTaskCatalog
        )
        val comparator = runCatching { V22PythonParityEngine.build(history, effectiveRequest) }.getOrNull()
            ?.takeIf { plan -> plan.weeks.firstOrNull()?.sessions?.any { it.items.isNotEmpty() } == true }
        return V22GeneratedProgram(comparator, balanced.toSkeleton(effectiveRequest, exercises), balanced)
    }
}

internal fun BBalanced12WeekPlan.toSkeleton(
    request: V22GenerationRequest,
    exercises: Map<String, Exercise>
): GeneratedProgramSkeleton {
    val skeletonRequest = ProgramSkeletonRequest(
        name = request.name,
        goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
        weeklyTrainingDays = input.days,
        sessionMinutes = request.sessionMinutes ?: 0,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.0,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = 12
    )
    val focusByBlock = blocks.associate { it.block to it.focus }
    val items = weeks.flatMap { week ->
        week.sessions.flatMap { session ->
            session.items.mapIndexed { index, item ->
                val sets = item.sets.mapIndexed { setIndex, set ->
                    ProgramSetPrescription(
                        setIndex = setIndex + 1,
                        reps = set.reps ?: 0,
                        weightKg = 0.0,
                        seconds = set.seconds ?: 0,
                        targetRpeMin = null,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                    )
                }
                val reps = sets.map(ProgramSetPrescription::reps).distinct().singleOrNull() ?: 0
                val seconds = sets.map(ProgramSetPrescription::seconds).distinct().singleOrNull() ?: 0
                val focus = focusByBlock.getValue(week.block)
                ProgramSkeletonItem(
                    localId = "b12-w${week.week}-d${session.session}-o${index + 1}-${item.stableKey}",
                    weekNumber = week.week,
                    dayOfWeek = session.session,
                    orderIndex = index + 1,
                    exerciseStableKey = item.stableKey,
                    exerciseName = item.exercise,
                    category = item.category,
                    restSeconds = exercises[item.stableKey]?.defaultRestSeconds ?: 60,
                    prescription = "HISTORICAL_REFERENCE_ONLY · USER_CALIBRATION_REQUIRED",
                    setCount = item.setCount,
                    reps = reps,
                    weightKg = 0.0,
                    seconds = seconds,
                    selectionReason = "B12_BLOCK_${week.block}_FOCUS_${focus.name}",
                    weightSource = "B12_HISTORY_REFERENCE_NOT_APPROVED_LOAD",
                    trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                    dayIntensity = ProgramDayIntensity.MODERATE.name,
                    stableKey = item.stableKey,
                    selectionRole = "B12_FOCUS_${focus.name}",
                    setPrescriptions = sets
                )
            }
        }
    }
    return GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = 84,
        request = skeletonRequest,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = weeks.map { week ->
            val focus = focusByBlock.getValue(week.block)
            com.training.trackplanner.data.ProgramWeekPlan(
                weekIndex = week.week,
                weekType = "B_BALANCED_BLOCK_${week.block}_${focus.name}",
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false,
                targetRpeMin = 6.0,
                targetRpeMax = 8.5
            )
        },
        items = items,
        weekDaySchedule = weeks.associate { week -> week.week to week.sessions.mapTo(linkedSetOf(), V22Session::session) },
        warnings = listOf(
            "B_BALANCED_12_WEEK_V1",
            "Historical kilograms are references only; every future load remains USER_CALIBRATION_REQUIRED.",
            "No automatic four-week deload; each four-week focus block keeps a stable structure.",
            "Progression is evaluated only after a completed comparable session."
        ) + limitations,
        templateId = BBalanced12WeekEngine.TEMPLATE_ID,
        representativeTemplate = false,
        planningMetadataJson = metadataJson
    )
}

internal fun V22Plan.toSkeleton(
    request: V22GenerationRequest,
    exercises: Map<String, Exercise>
): GeneratedProgramSkeleton {
    val skeletonRequest = ProgramSkeletonRequest(
        name = request.name,
        goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
        weeklyTrainingDays = request.days,
        sessionMinutes = request.sessionMinutes ?: 0,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.0,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = request.weeks
    )
    val items = weeks.flatMap { week ->
        week.sessions.flatMap { session ->
            session.items.mapIndexed { index, item ->
                val sets = item.sets.mapIndexed { setIndex, set ->
                    ProgramSetPrescription(
                        setIndex = setIndex + 1,
                        reps = set.reps ?: 0,
                        weightKg = set.weightKg ?: 0.0,
                        seconds = set.seconds ?: 0,
                        targetRpeMin = null,
                        loadState = if (set.weightKg != null) {
                            ProgramLoadState.EXPLICIT_LOAD
                        } else {
                            ProgramLoadState.USER_CALIBRATION_REQUIRED
                        }
                    )
                }
                val reps = sets.map(ProgramSetPrescription::reps).distinct().singleOrNull() ?: 0
                val weight = sets.map(ProgramSetPrescription::weightKg).distinct().singleOrNull() ?: 0.0
                val seconds = sets.map(ProgramSetPrescription::seconds).distinct().singleOrNull() ?: 0
                ProgramSkeletonItem(
                    localId = "v22-w${week.week}-d${session.session}-o${index + 1}-${item.stableKey}",
                    weekNumber = week.week,
                    dayOfWeek = session.session,
                    orderIndex = index + 1,
                    exerciseStableKey = item.stableKey,
                    exerciseName = item.exercise,
                    category = item.category,
                    restSeconds = exercises[item.stableKey]?.defaultRestSeconds ?: 60,
                    prescription = "HISTORICAL_REFERENCE_ONLY · REVIEW_REQUIRED_NOT_AUTO_PRESCRIBED",
                    setCount = item.setCount,
                    reps = reps,
                    weightKg = weight,
                    seconds = seconds,
                    selectionReason = "${item.role} · NAME_BASED_ROLE_PROXY_NOT_CANONICAL",
                    weightSource = "V2_2_HISTORICAL_REFERENCE_ONLY",
                    trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                    dayIntensity = ProgramDayIntensity.MODERATE.name,
                    stableKey = item.stableKey,
                    setPrescriptions = sets
                )
            }
        }
    }
    return GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = request.weeks * 7,
        request = skeletonRequest,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = emptyList(),
        items = items,
        weekDaySchedule = weeks.associate { week -> week.week to week.sessions.mapTo(linkedSetOf(), V22Session::session) },
        warnings = listOf(
            "V2_2_PYTHON_V1_0_PARITY_PORT",
            "Historical RPE remains observation only and is not mapped to target RPE.",
            "Missing historical load is stored as USER_CALIBRATION_REQUIRED."
        ) + limitations,
        templateId = "V2_2_PYTHON_V1_0_${request.mode.name}",
        representativeTemplate = false
    )
}
