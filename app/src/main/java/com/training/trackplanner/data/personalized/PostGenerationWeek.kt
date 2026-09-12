package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*

internal const val PLANNING_EPSILON = 1e-9
internal fun plannedSeconds(item: ProgramSkeletonItem): Int = item.setPrescriptions.sumOf { if (it.seconds > 0) it.seconds else 45 } +
    item.restSeconds * (item.setPrescriptions.size - 1).coerceAtLeast(0)
internal fun planningMedian(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2.0
}

/** Explicit logical identities are supplied at materialization; never parsed from localIds or names. */
internal data class RepresentativeWeek(
    val days: List<Int>,
    val items: List<ProgramSkeletonItem>,
    val atomByLocalId: Map<String, String>,
    val originals: Map<Pair<Int, String>, ProgramSkeletonItem>
) {
    fun mirror(initial: GeneratedProgramSkeleton, result: List<ProgramSkeletonItem>, schedule: Map<Int, Set<Int>>): GeneratedProgramSkeleton {
        val firstDays = schedule.getValue(1).sorted()
        val materialized = (1..initial.request.durationWeeks).flatMap { week ->
            val targetDays = schedule.getValue(week).sorted()
            result.map { row ->
                val atom = atomByLocalId[row.localId] ?: row.localId
                val original = originals[week to atom]
                // Mirror reviewed content (including exact restorations), retaining existing local identities.
                row.copy(localId = original?.localId ?: "residual_${week}_${row.localId}").copy(
                    weekNumber = week, dayOfWeek = targetDays[firstDays.indexOf(row.dayOfWeek)], orderIndex = row.orderIndex)
            }
        }
        return initial.copy(items = materialized, weekDaySchedule = schedule)
    }

    companion object {
        /** Editor-only structural correspondence: prescriptions and week-local bindings may differ.
         * Exact authorized parent/chunk identity wins; unowned rows need an unambiguous typed key.
         * Neither display labels nor list positions are identities. The planner's exact derive is unchanged.
         */
        fun deriveStructure(initial: GeneratedProgramSkeleton): RepresentativeWeek? {
            data class Atom(val origin: AuthorizedAtomOrigin?,val key: String,val variant: String,val slot: String)
            val origins=initial.personalizedDecision?.authorizedScheduling?.localOrigins.orEmpty()
            val identity=initial.items.associate { it.localId to Atom(origins[it.localId],it.exerciseStableKey,it.progressionVariant,it.requestedTemplateSlot) }
            val ids=identity.values.distinct().mapIndexed { index,atom -> atom to "editor_atom_$index" }.toMap()
            val atoms=identity.mapValues { ids.getValue(it.value) }
            val days=initial.weekDaySchedule[1]?.sorted() ?: return null
            val first=initial.items.filter { it.weekNumber==1 }
            fun structure(week: Int): Map<String,Pair<Int,Int>>? {
                if(initial.weekDaySchedule[week]?.sorted()!=days) return null
                val rows=initial.items.filter { it.weekNumber==week }
                if(rows.any { it.dayOfWeek !in days } || rows.map { atoms.getValue(it.localId) }.distinct().size!=rows.size) return null
                return rows.associate { atoms.getValue(it.localId) to (it.dayOfWeek to it.orderIndex) }
            }
            val baseline=structure(1) ?: return null
            if((1..initial.request.durationWeeks).any { structure(it)!=baseline }) return null
            return RepresentativeWeek(days,first,atoms,initial.items.associateBy { it.weekNumber to atoms.getValue(it.localId) })
        }

        fun derive(initial: GeneratedProgramSkeleton, atoms: Map<String, String>): RepresentativeWeek? {
            val days = initial.weekDaySchedule[1]?.sorted() ?: return null
            val representative = initial.items.filter { it.weekNumber == 1 }
            if (representative.isEmpty() || representative.any { it.setPrescriptions.isEmpty() }) return null
            fun structure(week: Int): Map<String, ProgramSkeletonItem>? {
                val weekDays = initial.weekDaySchedule[week]?.sorted() ?: return null
                if (weekDays.size != days.size) return null
                val rows = initial.items.filter { it.weekNumber == week }
                if (rows.any { it.localId !in atoms || it.dayOfWeek !in weekDays }) return null
                if (rows.map { atoms.getValue(it.localId) }.distinct().size != rows.size) return null
                return rows.associate { atoms.getValue(it.localId) to it.copy(localId = "", weekNumber = 1,
                    dayOfWeek = weekDays.indexOf(it.dayOfWeek) + 1) }
            }
            val baseline = structure(1) ?: return null
            if ((1..initial.request.durationWeeks).any { structure(it) != baseline }) return null
            return RepresentativeWeek(days, representative, atoms,
                initial.items.associateBy { it.weekNumber to atoms.getValue(it.localId) })
        }
    }
}

internal fun postProcessTissueAllowed(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, key: String): Boolean =
    key !in snapshot.recoverySignals.tissueRestrictedStableKeys && state.trainingStateAssessment?.globalHardRestriction != true &&
        !(snapshot.recoverySignals.tissueStatus in setOf("VERY_HIGH", "BLOCKED") && snapshot.recoverySignals.tissueRestrictedStableKeys.isEmpty())
