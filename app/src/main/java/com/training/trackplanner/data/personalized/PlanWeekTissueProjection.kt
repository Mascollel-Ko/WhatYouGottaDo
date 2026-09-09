package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.tissue.*
import com.training.trackplanner.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONObject

data class PlannedTissueDay(val day: Int, val date: String, val blockedUnits: Set<String>, val unresolvedKeys: Set<String>,
    val before: TissueCurrentState?, val after: TissueCurrentState?) {
    val feasible: Boolean get() = blockedUnits.isEmpty() && unresolvedKeys.isEmpty()
    fun toJson() = JSONObject().put("day", day).put("date", date).put("feasible", feasible)
        .put("blockedUnits", JSONArray(blockedUnits.sorted())).put("unresolvedKeys", JSONArray(unresolvedKeys.sorted()))
        .put("units", JSONArray(after?.loadUnits.orEmpty().filter { it.relevantForProvenance }.map { unit ->
            JSONObject().put("unit", unit.key.loadUnitStableKey).put("tissueClass", unit.tissueClass)
                .put("beforeStatus", before?.loadUnits?.firstOrNull { it.key == unit.key }?.status?.name)
                .put("afterStatus", unit.status.name).put("residualLower", unit.rawResidual.lower).put("residualUpper", unit.rawResidual.upper)
                .put("channels", JSONObject(unit.channelResiduals.mapKeys { it.key.name }.mapValues { it.value.toString() }))
        }))
}
data class PlannedTissueWeek(val days: List<PlannedTissueDay>, val diagnostic: String = "CANONICAL_RCV_PROJECTION") {
    val feasible: Boolean get() = diagnostic == "CANONICAL_RCV_PROJECTION" && days.all { it.feasible }
    fun toJson() = JSONObject().put("diagnostic", diagnostic).put("feasible", feasible).put("days", JSONArray(days.map { it.toJson() }))
}
fun interface PlanWeekTissueProjection {
    fun evaluate(items: List<ProgramSkeletonItem>, targetRpeMax: Double): PlannedTissueWeek
}
internal data class PlannedTissueCalculation(val state: TissueCurrentState, val currentExposureUnitsByExercise: Map<String, Set<String>>)

/** A representative week starts next Monday; logical day gaps are real calendar gaps, not cutoff+1 resets. */
internal class CanonicalPlanWeekTissueProjection(private val cutoff: LocalDate, private val zone: ZoneId,
    private val calculate: (Long, List<WorkoutEntryWithSets>) -> PlannedTissueCalculation) : PlanWeekTissueProjection {
    override fun evaluate(items: List<ProgramSkeletonItem>, targetRpeMax: Double): PlannedTissueWeek {
        if (!targetRpeMax.isFinite() || targetRpeMax !in 0.0..10.0) return PlannedTissueWeek(emptyList(), "MISSING_TYPED_PLANNED_RPE")
        val start = cutoff.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val accumulated = mutableListOf<WorkoutEntryWithSets>()
        var identity = -1L
        val days = items.groupBy { it.dayOfWeek }.toSortedMap().map { (day, rows) ->
            require(day in 1..7)
            val date = start.plusDays(day - 1L)
            val time = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val before = calculate(time, accumulated.toList()).state
            rows.sortedWith(compareBy({ it.orderIndex }, { it.localId })).forEach { item ->
                val id = identity--
                accumulated += WorkoutEntryWithSets(WorkoutEntry(id = id, date = date.toString(),
                    exerciseStableKey = item.exerciseStableKey, exerciseName = item.exerciseName, category = item.category,
                    restSeconds = item.restSeconds, createdAt = time, performedAt = time), item.setPrescriptions.map {
                    WorkoutSet(entryId = id, setIndex = it.setIndex, reps = it.reps, weightKg = it.weightKg,
                        seconds = it.seconds, confirmed = true, rpe = targetRpeMax)
                })
            }
            val calculated = calculate(time, accumulated.toList())
            val after = calculated.state
            val keys = rows.mapTo(mutableSetOf()) { it.exerciseStableKey }
            // Full canonical events own exposure. Display contributors are top-1/top-2 summaries and MUST NOT own this gate.
            val exposure = calculated.currentExposureUnitsByExercise.filterKeys { it in keys }
            val exposedKeys = exposure.values.flatten().toSet()
            val exposed = after.loadUnits.filter { it.key.loadUnitStableKey in exposedKeys }
            val resolved = exposure.filterValues { it.isNotEmpty() }.keys
            val blocked = before.loadUnits.filter { prior -> exposed.any { it.key == prior.key } &&
                (prior.status in setOf(TissueCanonicalStatus.HIGH, TissueCanonicalStatus.VERY_HIGH) ||
                    prior.symptomOverride != TissueSymptomOverride.NONE) }.mapTo(mutableSetOf()) { it.key.loadUnitStableKey }
            blocked += exposed.filter { it.status == TissueCanonicalStatus.UNAVAILABLE }.map { it.key.loadUnitStableKey }
            if (before.hasUnscopedHighJointTendonDiscomfort) blocked += "UNSCOPED_HIGH_DISCOMFORT"
            PlannedTissueDay(day, date.toString(), blocked, keys - resolved, before, after)
        }
        return PlannedTissueWeek(days)
    }
}
