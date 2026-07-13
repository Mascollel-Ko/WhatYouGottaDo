package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator

object TissueDoseResolver {
    fun resolve(record: TissueWorkoutRecord, basis: TissueDoseBasis): TissueDoseResolution {
        val sets = record.sets.filter { it.confirmed }
        if (sets.isEmpty()) return missing("No confirmed sets are available.")
        if (sets.any { it.reps < 0 || it.weightKg < 0.0 || it.seconds < 0 }) {
            return missing("Negative record inputs are invalid.")
        }
        return when (basis) {
            TissueDoseBasis.EXTERNAL_LOAD_REPETITIONS -> TissueDoseResolution(
                resolvedDose = sets.sumOf { it.reps * it.weightKg },
                status = TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD
            )
            TissueDoseBasis.EFFECTIVE_BODYWEIGHT_REPETITIONS -> {
                if (record.bodyWeightKg == null) return missing("Effective bodyweight dose requires body weight.")
                TissueDoseResolution(
                    resolvedDose = sets.sumOf {
                        BodyweightEffectiveLoadCalculator.volumeLoad(record.exercise, it, record.bodyWeightKg)
                    },
                    status = TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD
                )
            }
            TissueDoseBasis.DURATION_HOLD -> {
                val loads = sets.mapNotNull { set ->
                    DurationHoldLoadCalculator.holdLoad(record.exercise, set, record.entry.rpe)
                }
                if (loads.isEmpty()) missing("No supported duration-hold input is available.")
                else TissueDoseResolution(
                    resolvedDose = loads.sum(),
                    status = TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD,
                    rpeAlreadyApplied = true
                )
            }
            TissueDoseBasis.LOCOMOTION_DURATION,
            TissueDoseBasis.MIXED_EVENT_DURATION -> duration(sets.map { it.seconds })
            TissueDoseBasis.SESSION_DURATION_RPE -> {
                val seconds = sets.sumOf { it.seconds }
                val rpe = sets.mapNotNull { it.rpe }.average().takeIf { !it.isNaN() } ?: record.entry.rpe
                if (seconds <= 0 || rpe == null) missing("Session duration and RPE are both required.")
                else TissueDoseResolution(
                    resolvedDose = seconds * rpe,
                    status = TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD,
                    rpeAlreadyApplied = true
                )
            }
            TissueDoseBasis.DISTANCE,
            TissueDoseBasis.LANDING_CONTACT_COUNT,
            TissueDoseBasis.DIRECTION_CHANGE_COUNT,
            TissueDoseBasis.JUMP_COUNT,
            TissueDoseBasis.THROW_COUNT,
            TissueDoseBasis.STROKE_COUNT -> missing("${basis.name} is not recorded; no event count was invented.")
        }
    }

    private fun duration(seconds: List<Int>): TissueDoseResolution {
        val total = seconds.sum()
        return if (total <= 0) missing("No explicit duration is available.")
        else TissueDoseResolution(total.toDouble(), TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD)
    }

    private fun missing(message: String) = TissueDoseResolution(
        resolvedDose = null,
        status = TissueDoseResolutionStatus.MISSING_RECORD_INPUT,
        diagnostics = listOf(message)
    )
}
