package com.training.trackplanner.data

/** Explicit UI write intent. Snapshot fields outside [fields] never reach storage. */
data class RecordSetEdit(val values: WorkoutSet, val fields: Set<RecordSetField>) {
    fun applyTo(current: WorkoutSet): WorkoutSet {
        require(current.id == values.id)
        return current.copy(
            reps = if (RecordSetField.REPS in fields) values.reps else current.reps,
            weightKg = if (RecordSetField.WEIGHT in fields) values.weightKg else current.weightKg,
            manualWeight = if (RecordSetField.WEIGHT in fields) values.manualWeight else current.manualWeight,
            seconds = if (RecordSetField.DURATION in fields) values.seconds else current.seconds,
            rpe = if (RecordSetField.RPE in fields) values.rpe else current.rpe,
            restSecondsOverride = if (RecordSetField.REST in fields) values.restSecondsOverride else current.restSecondsOverride,
            confirmed = if (RecordSetField.CONFIRMATION in fields) values.confirmed else current.confirmed
        )
    }
}

enum class RecordSetField { REPS, WEIGHT, DURATION, RPE, REST, CONFIRMATION }

fun WorkoutSet.edit(vararg fields: RecordSetField) = RecordSetEdit(this, fields.toSet())
