package com.training.trackplanner.data

/** Explicit UI write intent. Snapshot fields outside [fields] never reach storage. */
data class RecordSetEdit(val values: WorkoutSet, val fields: Set<RecordSetField>) {
    fun applyTo(current: WorkoutSet): WorkoutSet {
        require(current.id == values.id)
        val weightField = RecordSetField.WEIGHT in fields
        val actualWeightEdit = weightField && (
            values.weightKg != current.weightKg ||
                values.manualWeight != current.manualWeight ||
                values.loadState != current.loadState
            )
        val userWeightEdit = actualWeightEdit && values.manualWeight
        val automaticKnownSourceEdit = actualWeightEdit && !values.manualWeight &&
            current.loadSource != ProgramLoadSource.LEGACY_UNKNOWN
        return current.copy(
            reps = if (RecordSetField.REPS in fields) values.reps else current.reps,
            weightKg = if (RecordSetField.WEIGHT in fields) values.weightKg else current.weightKg,
            manualWeight = if (RecordSetField.WEIGHT in fields) values.manualWeight else current.manualWeight,
            secondaryWeightKg = if (RecordSetField.SECONDARY_WEIGHT in fields) values.secondaryWeightKg else current.secondaryWeightKg,
            loadState = if (RecordSetField.WEIGHT in fields) values.loadState else current.loadState,
            estimatedLoadKg = when {
                userWeightEdit || automaticKnownSourceEdit -> values.weightKg.takeIf { it > 0.0 }
                else -> current.estimatedLoadKg
            },
            loadSource = if (userWeightEdit) ProgramLoadSource.USER_FIXED else current.loadSource,
            freshRir = if (actualWeightEdit) null else current.freshRir,
            expectedRir = if (actualWeightEdit) null else current.expectedRir,
            relativeIntensity = if (actualWeightEdit) null else current.relativeIntensity,
            evidenceVersion = if (userWeightEdit) "USER_WEIGHT_EDIT" else current.evidenceVersion,
            seconds = if (RecordSetField.DURATION in fields) values.seconds else current.seconds,
            rpe = if (RecordSetField.RPE in fields) values.rpe else current.rpe,
            restSecondsOverride = if (RecordSetField.REST in fields) values.restSecondsOverride else current.restSecondsOverride,
            confirmed = if (RecordSetField.CONFIRMATION in fields) values.confirmed else current.confirmed
        )
    }
}

enum class RecordSetField { REPS, WEIGHT, SECONDARY_WEIGHT, DURATION, RPE, REST, CONFIRMATION }

fun WorkoutSet.edit(vararg fields: RecordSetField) = RecordSetEdit(this, fields.toSet())
