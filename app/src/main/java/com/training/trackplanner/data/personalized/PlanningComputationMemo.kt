package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/**
 * Request-scoped cache for pure planning computations. A new instance is created for each
 * top-level generation, so cached values never cross users, snapshots, or generations.
 */
internal class PlanningComputationMemo {
    var prescriptionHits: Int = 0
    var prescriptionMisses: Int = 0
    var dayProjectionHits: Int = 0
    var dayProjectionMisses: Int = 0
    var tissueProjectionHits: Int = 0
    var tissueProjectionMisses: Int = 0

    private class PrescriptionKey(
        private val snapshot: PlanningHistorySnapshot,
        private val strengthIntent: StrengthIntent,
        private val item: PlannedExercise,
        private val style: StrengthProgrammingStyle
    ) {
        override fun equals(other: Any?): Boolean = other is PrescriptionKey &&
            snapshot === other.snapshot && strengthIntent == other.strengthIntent && item == other.item && style == other.style
        override fun hashCode(): Int = (((System.identityHashCode(snapshot) * 31 + strengthIntent.hashCode()) * 31 + item.hashCode()) * 31 + style.hashCode())
    }

    private val prescriptions = mutableMapOf<PrescriptionKey, PlannedPrescription>()
    private val dayLoads = mutableMapOf<List<ProgramSkeletonItem>, StandaloneDayLoad>()
    private val tissueWeeks = mutableMapOf<Pair<List<ProgramSkeletonItem>, Double>, PlannedTissueWeek>()

    fun prescription(
        snapshot: PlanningHistorySnapshot,
        strengthIntent: StrengthIntent,
        item: PlannedExercise,
        style: StrengthProgrammingStyle,
        compute: () -> PlannedPrescription
    ): PlannedPrescription {
        val key = PrescriptionKey(snapshot, strengthIntent, item, style)
        prescriptions[key]?.let { prescriptionHits++; return it }
        prescriptionMisses++
        return compute().also { prescriptions[key] = it }
    }

    fun wrap(snapshot: PlanningHistorySnapshot): PlanningHistorySnapshot = snapshot.copy(
        planDayProjection = snapshot.planDayProjection?.let { delegate ->
            PlanDayProjection { items ->
                val key = items.toList()
                dayLoads[key]?.also { dayProjectionHits++ } ?: delegate.evaluate(items).also {
                    dayProjectionMisses++
                    dayLoads[key] = it
                }
            }
        },
        planWeekTissueProjection = snapshot.planWeekTissueProjection?.let { delegate ->
            PlanWeekTissueProjection { items, targetRpeMax ->
                val key = items.toList() to targetRpeMax
                tissueWeeks[key]?.also { tissueProjectionHits++ } ?: delegate.evaluate(items, targetRpeMax).also {
                    tissueProjectionMisses++
                    tissueWeeks[key] = it
                }
            }
        }
    )
}
