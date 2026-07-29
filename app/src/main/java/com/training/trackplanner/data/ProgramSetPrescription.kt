package com.training.trackplanner.data

data class ProgramSetPrescription(
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int
)

internal object ProgramSetPrescriptionResolver {
    fun resolve(
        item: TrainingProgramItem,
        storedSets: List<TrainingProgramItemSet>
    ): List<ProgramSetPrescription> =
        storedSets
            .sortedWith(compareBy<TrainingProgramItemSet> { it.setIndex }.thenBy { it.id })
            .takeIf(List<TrainingProgramItemSet>::isNotEmpty)
            ?.mapIndexed { index, set ->
                ProgramSetPrescription(index + 1, set.reps, set.weightKg, set.seconds)
            }
            ?: List(item.setCount.coerceAtLeast(1)) { index ->
                ProgramSetPrescription(index + 1, item.reps, item.weightKg, item.seconds)
            }

    fun resolve(item: ProgramSkeletonItem): List<ProgramSetPrescription> =
        item.setPrescriptions
            .sortedBy(ProgramSetPrescription::setIndex)
            .takeIf(List<ProgramSetPrescription>::isNotEmpty)
            ?.mapIndexed { index, set -> set.copy(setIndex = index + 1) }
            ?: List(item.setCount.coerceAtLeast(1)) { index ->
                ProgramSetPrescription(index + 1, item.reps, item.weightKg, item.seconds)
            }

    fun summarize(sets: List<ProgramSetPrescription>): ProgramSetPrescriptionSummary {
        val normalized = sets.sortedBy(ProgramSetPrescription::setIndex)
        return ProgramSetPrescriptionSummary(
            setCount = normalized.size.coerceAtLeast(1),
            reps = normalized.map(ProgramSetPrescription::reps).distinct().singleOrNull() ?: 0,
            weightKg = normalized.map(ProgramSetPrescription::weightKg).distinct().singleOrNull() ?: 0.0,
            seconds = normalized.map(ProgramSetPrescription::seconds).distinct().singleOrNull() ?: 0
        )
    }
}

internal data class ProgramSetPrescriptionSummary(
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int
)
