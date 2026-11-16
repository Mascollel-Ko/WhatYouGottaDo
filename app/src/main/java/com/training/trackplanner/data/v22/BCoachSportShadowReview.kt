package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import kotlin.math.max

/**
 * Read-only experiment across production-generated feasible candidates.
 * No new plan is accepted, persisted or used to change the production ranking.
 */
internal data class BCoachSportShadowWeek(
    val objective: String,
    val week: Int,
    val dose: Double,
    val lower: Double,
    val upper: Double
)

internal data class BCoachSportShadowObjective(
    val objective: String,
    val weeks: Int,
    val meanDose: Double,
    val meanLower: Double,
    val meanUpper: Double,
    val shortfallFraction: Double
)

internal data class BCoachSportShadowExercise(
    val name: String,
    val stableKey: String,
    val weeklySets: Double,
    val objectives: List<String>
)

internal data class BCoachSportShadowCandidate(
    val candidateId: String,
    val productionRecommended: Boolean,
    val productionBaseLoss: Double,
    val shadowSportLoss: Double?,
    val objectiveCoverage: Int,
    val objectiveCount: Int,
    val estimatedMaxDayMinutes: Double,
    val meanWeeklySets: Double,
    val objectives: List<BCoachSportShadowObjective>,
    val sportExercises: List<BCoachSportShadowExercise>
)

internal data class BCoachSportShadowReport(
    val productionCandidateId: String,
    val shadowLeaderId: String?,
    val candidates: List<BCoachSportShadowCandidate>,
    val notInEvaluatedPool: List<String>,
    val evaluatedCandidateCount: Int,
    val error: String? = null
) {
    val available: Boolean get() = candidates.isNotEmpty() || error != null
}

internal object BCoachSportShadowReview {
    internal data class Input(
        val candidateId: String,
        val skeleton: GeneratedProgramSkeleton,
        val evaluation: BCoachV2CandidateEvaluation
    )

    fun evaluate(
        input: List<Input>,
        productionCandidateId: String,
        catalog: CanonicalBadmintonObjectiveCatalog,
        exercises: Map<String, Exercise>
    ): BCoachSportShadowReport {
        val qualifiedPool = input.sortedBy(Input::candidateId)
        val allCandidateKeys = qualifiedPool.flatMap { candidate ->
            candidate.skeleton.items.map { it.exerciseStableKey }
        }.toSet()
        val unrepresented = catalog.allRelations().asSequence()
            .filter { it.transferLevel in setOf(
                BadmintonObjectiveTransferLevel.DIRECT,
                BadmintonObjectiveTransferLevel.SUPPORTIVE
            ) }
            .map { it.exerciseStableKey }.distinct()
            .filter { key ->
                key !in allCandidateKeys && exercises[key]?.let {
                    it.isActive && !it.needsReview && !it.isCustom &&
                        it.planningEligibility != "HISTORY_ONLY"
                } == true
            }
            .map { key -> exercises.getValue(key).name }
            .distinct().sorted().toList()

        val rows = qualifiedPool.map { candidate ->
            val eligible = candidate.evaluation.sportObjectiveWeeks.filter { row ->
                row.lower > 0.0 && row.upper >= row.lower &&
                    listOf(row.dose, row.lower, row.upper).all(Double::isFinite)
            }
            val objectives = eligible.groupBy(BCoachSportShadowWeek::objective)
                .toSortedMap().map { (objective, values) ->
                    BCoachSportShadowObjective(
                        objective = objective,
                        weeks = values.size,
                        meanDose = values.map(BCoachSportShadowWeek::dose).average(),
                        meanLower = values.map(BCoachSportShadowWeek::lower).average(),
                        meanUpper = values.map(BCoachSportShadowWeek::upper).average(),
                        shortfallFraction = values.map { row ->
                            (max(0.0, row.lower - row.dose) / max(1.0, row.lower)).coerceIn(0.0, 1.0)
                        }.average()
                    )
                }
            val deficit = eligible.map { row ->
                (max(0.0, row.lower - row.dose) / max(1.0, row.lower)).coerceIn(0.0, 1.0)
            }.average().takeIf { eligible.isNotEmpty() }
            val excess = eligible.map { row ->
                (max(0.0, row.dose - row.upper) / max(1.0, row.upper)).coerceIn(0.0, 1.0)
            }.average().takeIf { eligible.isNotEmpty() }
            val shadowSportLoss = if (deficit != null && excess != null) {
                0.85 * deficit + 0.15 * excess
            } else null

            val sportItems = candidate.skeleton.items.filter { item ->
                catalog.relations(item.exerciseStableKey).any { relation ->
                    relation.transferLevel in setOf(
                        BadmintonObjectiveTransferLevel.DIRECT,
                        BadmintonObjectiveTransferLevel.SUPPORTIVE
                    )
                }
            }
            val sportExercises = sportItems.groupBy { it.exerciseStableKey }
                .toSortedMap().map { (key, items) ->
                    BCoachSportShadowExercise(
                        name = exercises[key]?.name ?: items.first().exerciseName,
                        stableKey = key,
                        weeklySets = items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
                            .toDouble() / candidate.skeleton.request.durationWeeks.coerceAtLeast(1),
                        objectives = catalog.relations(key).filter { relation ->
                            relation.transferLevel in setOf(
                                BadmintonObjectiveTransferLevel.DIRECT,
                                BadmintonObjectiveTransferLevel.SUPPORTIVE
                            )
                        }.map { it.objective.name }.distinct().sorted()
                    )
                }
            BCoachSportShadowCandidate(
                candidateId = candidate.candidateId,
                productionRecommended = candidate.candidateId == productionCandidateId,
                productionBaseLoss = candidate.evaluation.baseLoss,
                shadowSportLoss = shadowSportLoss,
                objectiveCoverage = objectives.count { it.shortfallFraction <= 0.05 },
                objectiveCount = objectives.size,
                estimatedMaxDayMinutes = candidate.evaluation.maximumDayMinutes,
                meanWeeklySets = candidate.skeleton.items.sumOf {
                    ProgramSetPrescriptionResolver.resolve(it).size
                }.toDouble() / candidate.skeleton.request.durationWeeks.coerceAtLeast(1),
                objectives = objectives,
                sportExercises = sportExercises
            )
        }
        val leader = rows.filter { it.shadowSportLoss != null }
            .minWithOrNull(compareBy<BCoachSportShadowCandidate> {
                checkNotNull(it.shadowSportLoss)
            }.thenBy { it.productionBaseLoss }.thenBy { it.candidateId })
        return BCoachSportShadowReport(
            productionCandidateId = productionCandidateId,
            shadowLeaderId = leader?.candidateId,
            candidates = rows.sortedWith(
                compareBy<BCoachSportShadowCandidate> { it.shadowSportLoss == null }
                    .thenBy { it.shadowSportLoss ?: Double.POSITIVE_INFINITY }
                    .thenBy { it.candidateId }
            ),
            notInEvaluatedPool = unrepresented,
            evaluatedCandidateCount = qualifiedPool.size
        )
    }
}
