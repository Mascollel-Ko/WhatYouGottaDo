package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.LegacyAutoSkeleton
import com.training.trackplanner.data.program.legacy.LegacyAutoSkeletonItem

/** After frozen output finalization only. Maps rows, never invokes a planner or edits its result. */
internal fun LegacyAutoSkeleton.toTrainingProgram(existing: TrainingProgram?, now: Long): TrainingProgram =
    TrainingProgram(
        id = existing?.id ?: 0,
        stableKey = existing?.stableKey ?: ProgramStableKeyPolicy.newUserKey(),
        name = suggestedName.ifBlank { request.name.ifBlank { "새 프로그램" } },
        durationDays = durationDays,
        createdAt = existing?.createdAt ?: now,
        goal = request.goal.name,
        weeklyTrainingDays = request.weeklyTrainingDays,
        sessionMinutes = request.sessionMinutes,
        availableEquipment = request.availableEquipment.joinToString("|"),
        excludedExerciseText = request.excludedExerciseText,
        badmintonTransferRatio = request.badmintonTransferRatio,
        sportStrengthRatio = request.sportStrengthRatio,
        periodizationType = periodizationType.name,
        updatedAt = now
    )

/** Set-row shape only; no progression or planner reconciliation. */
internal object LegacyAutoSetRows {
    fun resolve(item: LegacyAutoSkeletonItem): List<ProgramSetPrescription> =
        item.setPrescriptions.sortedBy(ProgramSetPrescription::setIndex)
            .takeIf(List<ProgramSetPrescription>::isNotEmpty)
            ?.mapIndexed { index, set -> set.copy(setIndex = index + 1) }
            ?: List(item.setCount.coerceAtLeast(1)) { index ->
                ProgramSetPrescription(index + 1, item.reps, item.weightKg, item.seconds)
            }

    fun summarize(sets: List<ProgramSetPrescription>) = ProgramSetPrescriptionResolver.summarize(sets)
}

internal fun LegacyAutoSkeletonItem.toTrainingProgramItem(programId: Long): TrainingProgramItem {
    val summary = ProgramSetPrescriptionResolver.summarize(
        LegacyAutoSetRows.resolve(this)
    )
    return TrainingProgramItem(
        programId = programId,
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        orderIndex = orderIndex,
        exerciseStableKey = exerciseStableKey,
        exerciseName = exerciseName,
        category = category,
        restSeconds = restSeconds,
        prescription = prescription,
        setCount = summary.setCount,
        reps = summary.reps,
        weightKg = summary.weightKg,
        seconds = summary.seconds,
        trainingSlot = trainingSlot.ifBlank { ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name },
        dayIntensity = dayIntensity.ifBlank { ProgramDayIntensity.MODERATE.name },
        weightSource = weightSource.ifBlank { "MANUAL_OR_EXISTING" }
    )
}
