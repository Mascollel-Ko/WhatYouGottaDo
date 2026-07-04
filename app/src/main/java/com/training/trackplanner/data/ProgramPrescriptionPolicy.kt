package com.training.trackplanner.data

import kotlin.math.max
import kotlin.math.roundToInt

internal class ProgramPrescriptionPolicy(
    private val sessionDensityPolicy: ProgramSessionDensityPolicy = ProgramSessionDensityPolicy()
) {
    fun prescribe(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate,
        useCase: ProgramFatigueUseCase = ProgramFatigueUseCase.TODAY_EXECUTION
    ): ProgramPrescription {
        val timed = candidate.isTimed
        val baseSets = when (role) {
            ProgramExerciseRole.ANCHOR -> 4
            ProgramExerciseRole.TRANSFER -> if (candidate.isHighImpact) 3 else 4
            ProgramExerciseRole.PREHAB -> 2
            else -> 3
        }
        val fatigueVolumeFactor = if (
            useCase == ProgramFatigueUseCase.PROGRAM_PLANNING &&
            gate.band != ProgramFatigueBand.RED
        ) {
            1.0
        } else {
            gate.volumeFactor
        }
        val sets = max(1, (baseSets * week.volumeMultiplier * fatigueVolumeFactor).roundToInt())
            .let { plannedSets ->
                val shouldCapHeavyLower = if (useCase == ProgramFatigueUseCase.PROGRAM_PLANNING) {
                    gate.band == ProgramFatigueBand.RED
                } else {
                    gate.band >= ProgramFatigueBand.ORANGE
                }
                if (candidate.isHeavyLower && shouldCapHeavyLower) {
                    plannedSets.coerceAtMost(2)
                } else {
                    plannedSets
                }
            }
        val reps = when {
            timed -> 0
            role == ProgramExerciseRole.ANCHOR -> 5
            role == ProgramExerciseRole.TRANSFER && candidate.isHighImpact -> 5
            role == ProgramExerciseRole.PREHAB -> 15
            role == ProgramExerciseRole.CORE -> 10
            else -> 10
        }
        val seconds = if (timed) {
            when {
                candidate.isSportLike -> 15 * 60
                role == ProgramExerciseRole.TRANSFER -> 20
                else -> 30
            }
        } else {
            0
        }
        val plannedRpeCap = if (candidate.isHeavyLower && gate.band == ProgramFatigueBand.RED) {
            gate.rpeCap.coerceAtMost(6)
        } else {
            gate.rpeCap
        }
        val plannedRpe = week.targetRpeMax.roundToInt().coerceAtMost(plannedRpeCap)
        val label = if (timed) "${sets}세트 · ${seconds}초" else "${sets}×${reps}"
        return ProgramPrescription(sets, reps, seconds, plannedRpe, label)
    }

    fun exerciseCount(minutes: Int): Int = sessionDensityPolicy.targetExerciseCount(minutes)

    fun warmupReserveSeconds(minutes: Int): Int = when {
        minutes <= 30 -> 5 * 60
        minutes <= 60 -> 8 * 60
        else -> 10 * 60
    }

    fun estimateItemDurationSeconds(
        candidate: ProgramCandidate,
        prescription: ProgramPrescription
    ): Int {
        val workPerSet = when {
            prescription.seconds > 0 -> prescription.seconds
            prescription.reps > 0 -> prescription.reps * 4
            else -> 30
        }
        val work = prescription.setCount * workPerSet
        val rest = (prescription.setCount - 1).coerceAtLeast(0) * candidate.exercise.defaultRestSeconds
        return 45 + work + rest
    }

    fun fitRequiredPrescription(
        candidate: ProgramCandidate,
        prescription: ProgramPrescription,
        remainingSeconds: Int
    ): ProgramPrescription {
        for (sets in prescription.setCount downTo 1) {
            val reduced = prescription.copy(setCount = sets)
            if (estimateItemDurationSeconds(candidate, reduced) <= remainingSeconds) return reduced
        }
        return prescription.copy(setCount = 1)
    }

    fun fitOptionalPrescription(
        candidate: ProgramCandidate,
        prescription: ProgramPrescription,
        remainingSeconds: Int
    ): ProgramPrescription? {
        for (sets in prescription.setCount downTo 1) {
            val reduced = prescription.copy(setCount = sets)
            if (estimateItemDurationSeconds(candidate, reduced) <= remainingSeconds) return reduced
        }
        return null
    }
}

internal data class ProgramPrescription(
    val setCount: Int,
    val reps: Int,
    val seconds: Int,
    val rpe: Int,
    val label: String
)
