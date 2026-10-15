package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
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

internal class V22ProgramPlanningService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao
) {
    suspend fun generate(request: V22GenerationRequest): V22GeneratedProgram {
        val history = V22HistoryInput.fromWorkoutHistory(workoutDao.entriesWithSetsUntil(request.asOf.toString()))
        val plan = V22PythonParityEngine.build(history, request)
        val exercises = exerciseDao.allExercises().associateBy(Exercise::stableKey)
        return V22GeneratedProgram(plan, plan.toSkeleton(request, exercises))
    }
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
