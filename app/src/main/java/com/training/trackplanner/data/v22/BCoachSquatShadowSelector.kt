package com.training.trackplanner.data.v22

import kotlin.math.abs
import kotlin.math.max

/**
 * Gate C, pure SHADOW experiment. Nothing here approves a working-set load or edits a program.
 *
 * All coefficients are explicitly caller-supplied UNVALIDATED scenario policies; no default
 * physiological value or opaque "coach optimum". Compares a frozen four-action benchmark
 * universe to keep unrelated new search candidates from changing the opportunity baseline.
 *
 * The only uncertainty scenarios available in Gate B are the observed anchor session's
 * canonical RPE/RIR discrete mixture. These are NOT future day-to-day variability scenarios.
 */
internal object BCoachSquatShadowSelector {
    enum class OmegaBand { CENTRAL_80, CENTRAL_90, FULL_RIR_SUPPORT }
    enum class DecisionState {
        NO_COMPARABLE_EVIDENCE,
        INCOMPLETE_BENCHMARK,
        GRID_CONFLICT_REVIEW,
        WEIGHT_OR_OMEGA_SENSITIVE,
        STABLE_SHADOW_ONLY
    }

    /** Explicitly UNVALIDATED coefficients, only for experimental sensitivity runs. */
    data class Weights(
        val rewardPerLoadStep: Double,
        val rewardPerAddedRep: Double,
        val rewardPerAddedSet: Double,
        val effortDeviationPenalty: Double,
        val rampPenalty: Double,
        val changePenalty: Double
    ) {
        init {
            val all = listOf(
                rewardPerLoadStep, rewardPerAddedRep, rewardPerAddedSet,
                effortDeviationPenalty, rampPenalty, changePenalty
            )
            require(all.all { it.isFinite() && it >= 0.0 })
            require(all.any { it > 0.0 })
        }
    }

    data class CandidateCosts(
        val action: BCoachSquatRelativeShadow.Action,
        val worstOpportunityRegret: Double,
        val worstHoldRelativeCost: Double,
        val avgHoldRelativeCost: Double,
        val positiveProgressUnits: Double,
        val nominalRampIncrease: Double,
        val changedLeverCount: Int
    )

    data class ScenarioDecision(
        val omegaBand: OmegaBand,
        val weightsIndex: Int,
        val anchorRirScenarios: List<Int>,
        val winner: BCoachSquatRelativeShadow.Action,
        /** Frozen benchmark comparison; additions outside this scope do not change this. */
        val benchmarkActions: List<BCoachSquatRelativeShadow.Action>,
        val candidates: List<CandidateCosts>
    )

    data class Decision(
        val state: DecisionState,
        val experimentalWinner: BCoachSquatRelativeShadow.Action?,
        val sensitivityWinnerCount: Int,
        val results: List<ScenarioDecision>,
        val excludedActions: List<BCoachSquatRelativeShadow.Action>,
        val warnings: List<String>
    )

    /** Stable, published first-slice comparison universe, not every generated exploration candidate. */
    val frozenBenchmark: List<BCoachSquatRelativeShadow.Action> = listOf(
        BCoachSquatRelativeShadow.Action.HOLD,
        BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP,
        BCoachSquatRelativeShadow.Action.REPS_UP_ONE,
        BCoachSquatRelativeShadow.Action.LOAD_UP_ONE_STEP_REPS_DOWN_ONE
    )

    fun compare(
        input: BCoachSquatRelativeShadow.Result,
        policies: List<Weights>,
        omegaBands: List<OmegaBand>,
        targetRirMin: Double,
        targetRirMax: Double
    ): Decision {
        require(policies.isNotEmpty())
        require(omegaBands.isNotEmpty())
        require(omegaBands.distinct().size == omegaBands.size)
        require(targetRirMin.isFinite() && targetRirMax.isFinite() &&
            targetRirMin >= 0.0 && targetRirMax >= targetRirMin)

        val byAction = input.candidates.associateBy { it.action }
        val excluded = input.candidates.map { it.action }.filterNot(frozenBenchmark::contains)
        if (input.status != BCoachSquatRelativeShadow.ResultStatus.READY ||
            input.anchorSet == null || byAction[BCoachSquatRelativeShadow.Action.HOLD] == null
        ) return Decision(
            DecisionState.NO_COMPARABLE_EVIDENCE, null, 0, emptyList(), excluded,
            listOf("No confirmed anchor and canonical RPE/RIR mixture; cannot compare")
        )
        if (!frozenBenchmark.all(byAction::containsKey)) return Decision(
            DecisionState.INCOMPLETE_BENCHMARK, null, 0, emptyList(), excluded,
            listOf("The frozen first-slice benchmark must be present before relative regrets are comparable")
        )

        val hold = checkNotNull(byAction[BCoachSquatRelativeShadow.Action.HOLD])
        val holdSupport = hold.scenarios.sortedBy { it.anchorRir }
        if (holdSupport.isEmpty() || holdSupport.any { it.probability <= 0.0 || it.projectedRir == null })
            return Decision(
                DecisionState.NO_COMPARABLE_EVIDENCE, null, 0, emptyList(), excluded,
                listOf("Original RPE/RIR scenario support is not usable")
            )

        val all = mutableListOf<ScenarioDecision>()
        for (band in omegaBands) {
            val states = selectScenarios(holdSupport, band)
            val eligible = frozenBenchmark.mapNotNull(byAction::get)
                .filter { candidate ->
                    candidate.scenarios.all { it.probability > 0.0 } &&
                        states.all { rir ->
                            candidate.scenarios.any { it.anchorRir == rir && it.projectedRir != null }
                        }
                }
            if (eligible.none { it.action == BCoachSquatRelativeShadow.Action.HOLD }) continue

            for ((weightIndex, weights) in policies.withIndex()) {
                val costMap = eligible.associate { candidate ->
                    candidate.action to states.associateWith { rir ->
                        val projectedRir = checkNotNull(candidate.scenarios
                            .single { it.anchorRir == rir }.projectedRir)
                        cost(
                            candidate, hold, input.grid.configuredTotalStepKg,
                            projectedRir, targetRirMin, targetRirMax, weights
                        )
                    }
                }
                val bestForState = states.associateWith { rir ->
                    eligible.minOf { checkNotNull(costMap[it.action]?.get(rir)) }
                }
                val holdCost = checkNotNull(costMap[hold.action])
                val rows = eligible.map { candidate ->
                    val costs = checkNotNull(costMap[candidate.action])
                    val differences = states.map { rir ->
                        checkNotNull(costs[rir]) - checkNotNull(holdCost[rir])
                    }
                    CandidateCosts(
                        action = candidate.action,
                        worstOpportunityRegret = states.maxOf { rir ->
                            checkNotNull(costs[rir]) - checkNotNull(bestForState[rir])
                        }.coerceAtLeast(0.0),
                        worstHoldRelativeCost = differences.max(),
                        avgHoldRelativeCost = differences.average(),
                        positiveProgressUnits = positiveProgressUnits(
                            candidate, hold, input.grid.configuredTotalStepKg, weights
                        ),
                        nominalRampIncrease = max(0.0, candidate.nominalVolumeLoadDelta),
                        changedLeverCount = candidate.changedLeverCount
                    )
                }
                // Deterministic choice; near ties are NOT evidence of meaningful superiority.
                val ordered = rows.sortedWith(
                    compareBy<CandidateCosts>({ it.worstOpportunityRegret },
                        { it.worstHoldRelativeCost }, { it.changedLeverCount }, { it.action.ordinal })
                )
                all += ScenarioDecision(
                    omegaBand = band, weightsIndex = weightIndex,
                    anchorRirScenarios = states, winner = ordered.first().action,
                    benchmarkActions = eligible.map { it.action },
                    candidates = ordered
                )
            }
        }
        if (all.isEmpty()) return Decision(
            DecisionState.NO_COMPARABLE_EVIDENCE, null, 0, emptyList(), excluded,
            listOf("None of the predeclared RIR scenarios can be evaluated")
        )

        val winners = all.map(ScenarioDecision::winner).distinct()
        val gridConflict = input.grid.userConfirmationRequired
        val resultState = when {
            gridConflict -> DecisionState.GRID_CONFLICT_REVIEW
            winners.size > 1 -> DecisionState.WEIGHT_OR_OMEGA_SENSITIVE
            else -> DecisionState.STABLE_SHADOW_ONLY
        }
        return Decision(
            state = resultState,
            experimentalWinner = winners.singleOrNull(),
            sensitivityWinnerCount = winners.size,
            results = all,
            excludedActions = excluded,
            warnings = buildList {
                add("SHADOW_ONLY: lambda coefficients unvalidated; no final prescription approval")
                add("Only anchor RPE/RIR scenarios; no independently validated daily performance distribution")
                add("Later working-set completion and rest-time fatigue are NOT predicted")
                add("Progress reward is an explicit intent credit, NOT estimated muscle/strength adaptation")
                if (gridConflict) add("CONFIGURED_${input.grid.configuredTotalStepKg}KG_VS_OBSERVED_GRID_REVIEW: user confirmation required")
                if (winners.size > 1) add("POLICY_OR_OMEGA_WINNER_REVERSAL: no unique robust winner")
                if (excluded.isNotEmpty()) add("Additional generated candidates ignored outside frozen benchmark")
            }
        )
    }

    private fun cost(
        candidate: BCoachSquatRelativeShadow.Candidate,
        hold: BCoachSquatRelativeShadow.Candidate,
        step: Double,
        rir: Double,
        targetMin: Double,
        targetMax: Double,
        w: Weights
    ): Double {
        val effortGap = when {
            rir < targetMin -> targetMin - rir
            rir > targetMax -> rir - targetMax
            else -> 0.0
        }
        // One normalized "progression unit" is one configured load increment, one repetition,
        // or one set. Their trade-off is entirely supplied by unvalidated policy coefficients.
        val reward = positiveProgressUnits(candidate, hold, step, w)
        val ramp = max(0.0, candidate.nominalVolumeLoadDelta)
        return -reward + w.effortDeviationPenalty * effortGap +
            w.rampPenalty * ramp + w.changePenalty * candidate.changedLeverCount
    }

    private fun positiveProgressUnits(
        candidate: BCoachSquatRelativeShadow.Candidate,
        hold: BCoachSquatRelativeShadow.Candidate,
        step: Double,
        w: Weights
    ): Double =
        w.rewardPerLoadStep * max(0.0, (candidate.totalLoadKg - hold.totalLoadKg) / step) +
            w.rewardPerAddedRep * max(0, candidate.repetitions - hold.repetitions) +
            w.rewardPerAddedSet * max(0, candidate.plannedSetCount - hold.plannedSetCount)

    private fun selectScenarios(
        values: List<BCoachSquatRelativeShadow.Scenario>, band: OmegaBand
    ): List<Int> {
        val quantiles = when (band) {
            OmegaBand.CENTRAL_80 -> listOf(0.10, 0.50, 0.90)
            OmegaBand.CENTRAL_90 -> listOf(0.05, 0.50, 0.95)
            OmegaBand.FULL_RIR_SUPPORT -> return values.map { it.anchorRir }.distinct()
        }
        return quantiles.map { q ->
            var cumulative = 0.0
            values.firstOrNull { item ->
                cumulative += item.probability
                cumulative + 1e-9 >= q
            }?.anchorRir ?: values.last().anchorRir
        }.distinct()
    }
}
