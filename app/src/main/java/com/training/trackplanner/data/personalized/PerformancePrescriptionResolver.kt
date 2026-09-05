package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSeed

/** Exact, executable shape. Rest-only timing is deliberately not a prescription. */
data class PerformancePrescriptionAuthority(
    val sets: List<ProgramSetPrescription>,
    val restSeconds: Int,
    val text: String,
    val source: String
)

object PerformancePrescriptionResolver {
    fun fromCanonicalPrograms(programs: List<ProgramSeed>): Map<String, PerformancePrescriptionAuthority> =
        programs.filter { it.durationDays > 7 }.flatMap { program -> program.items.map { program.key to it } }
            .filter { (_, item) -> item.setCount >= 2 && item.weightKg == 0.0 && (item.reps > 0 || item.seconds > 0) }
            .groupBy { it.second.exerciseStableKey }
            .mapValues { (_, choices) ->
                // First block of a multi-week seed, excluding short assessment programs and loaded tests.
                val (program, item) = choices.sortedWith(
                    compareBy<Pair<String, com.training.trackplanner.data.ProgramItemSeed>> { it.second.weekNumber }
                        .thenBy { it.first }.thenBy { it.second.orderIndex }
                ).first()
                PerformancePrescriptionAuthority(
                    List(item.setCount) { ProgramSetPrescription(it + 1, item.reps, 0.0, item.seconds) },
                    item.restSeconds, item.prescription, "CANONICAL_PROGRAM_${program}_${item.weekNumber}_${item.orderIndex}"
                )
            }

    fun resolve(snapshot: PlanningHistorySnapshot, key: String): PerformancePrescriptionAuthority? {
        val recent = snapshot.allConfirmedSets.filter {
            it.stableKey == key && !it.date.isBefore(snapshot.cutoff.minusDays(55)) &&
                !it.date.isAfter(snapshot.cutoff) && (it.reps > 0 || it.seconds > 0)
        }
        val lastDate = recent.maxOfOrNull(PlanningSetRecord::date)
        if (lastDate != null) {
            val rows = recent.filter { it.date == lastDate }.sortedBy(PlanningSetRecord::setIndex)
            return PerformancePrescriptionAuthority(
                rows.mapIndexed { i, row -> ProgramSetPrescription(i + 1, row.reps, row.weightKg, row.seconds) },
                snapshot.exercises.getValue(key).defaultRestSeconds,
                "",
                "RECENT_PERSONAL_EXECUTION_HOLD"
            )
        }
        snapshot.performancePrescriptions[key]?.let { return it }
        val category = snapshot.reviewedBadmintonCategory(key) ?: return null
        val guide = com.training.trackplanner.data.ProgramIntensityResolver.badminton(category)
        return PerformancePrescriptionAuthority(
            List(guide.setCount) { ProgramSetPrescription(it + 1, guide.reps, 0.0, guide.seconds) },
            guide.restSeconds, guide.text, "REVIEWED_BADMINTON_RULE_${category.name}"
        )
    }

    fun prescribe(snapshot: PlanningHistorySnapshot, item: PlannedExercise): PlannedPrescription {
        val authority = requireNotNull(resolve(snapshot, item.stableKey)) { "NO_SAFE_PRESCRIPTION_AUTHORITY:${item.stableKey}" }
        val requested = item.targetSets.takeIf { it > 0 } ?: authority.sets.size
        val sets = if (authority.source == "RECENT_PERSONAL_EXECUTION_HOLD") {
            // Repeat only a demonstrated shape; no progression for performance work.
            List(requested) { i -> authority.sets[i % authority.sets.size].copy(setIndex = i + 1) }
        } else {
            require(requested == authority.sets.size) { "INDIVISIBLE_REVIEWED_PRESCRIPTION:${item.stableKey}" }
            authority.sets
        }
        val text = if (authority.source == "RECENT_PERSONAL_EXECUTION_HOLD")
            "최근 완료 처방 유지 · ${sets.size}세트" else authority.text
        return PlannedPrescription(text, sets, authority.restSeconds, authority.source)
    }
}
