package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.toPosterior
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.StrengthPosteriorDao
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sqrt

internal data class BCoachV2LoadModelSnapshot(
    val stableKey: String,
    val tier: String,
    val capacityLow: Double,
    val capacityBase: Double,
    val capacityHigh: Double,
    val curve: ResolvedRepetitionCurve,
    val sourceVersion: String,
    val evidenceIds: List<String>,
    val stale: Boolean = false
)

internal data class BCoachV2LoadResolution(
    val skeleton: GeneratedProgramSkeleton,
    val instructionsByLocalId: Map<String, BCoachV2LoadInstruction>,
    val baseRirByLocalId: Map<String, Double>,
    val baseIntensityByLocalId: Map<String, Double>,
    val rirByScenario: Map<BCoachV2Scenario, Map<String, Double>>,
    val intensityByScenario: Map<BCoachV2Scenario, Map<String, Double>>,
    val reasonCodes: List<String>
)

/** Exact-stableKey load resolution only; it never borrows another exercise's capacity. */
internal object BCoachV2LoadCandidateResolver {
    val canonicalTierAKeys = setOf(
        "barbell_back_squat",
        "barbell_deadlift",
        "barbell_bench_press",
        "ex_e41f4c2b"
    )

    fun resolve(
        skeleton: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        modelSnapshots: Map<String, BCoachV2LoadModelSnapshot> = emptyMap(),
        mainExerciseKeys: Set<String> = emptySet()
    ): BCoachV2LoadResolution {
        val instructions = linkedMapOf<String, BCoachV2LoadInstruction>()
        val baseRir = linkedMapOf<String, Double>()
        val baseIntensity = linkedMapOf<String, Double>()
        val scenarioRir = BCoachV2Scenario.entries.associateWith { linkedMapOf<String, Double>() }
        val scenarioIntensity = BCoachV2Scenario.entries.associateWith { linkedMapOf<String, Double>() }
        val reasons = linkedSetOf<String>()
        val recentStart = request.asOf.minusDays(55)

        val resolvedItems = skeleton.items.map { item ->
            val exercise = exercises[item.exerciseStableKey]
            val sets = ProgramSetPrescriptionResolver.resolve(item)
            val representativeReps = sets.map { it.reps }.filter { it > 0 }.let { values ->
                values.sorted().getOrNull(values.size / 2) ?: item.reps.coerceAtLeast(1)
            }
            val existingExplicit = sets.firstOrNull {
                it.loadState == ProgramLoadState.EXPLICIT_LOAD && it.weightKg.isFinite() && it.weightKg > 0.0
            }
            val nonLoadMetric = nonLoadMetric(item.seconds, representativeReps, exercise)
            val targetRir = targetRir(item.selectionRole, item.exerciseStableKey in mainExerciseKeys)
            val targetRpe = 10.0 - targetRir
            val instruction = when {
                existingExplicit != null -> BCoachV2LoadInstruction.ExactLoad(
                    kg = existingExplicit.weightKg,
                    tier = "USER_FIXED",
                    targetRir = targetRir,
                    evidenceStatus = BCoachEvidenceStatus.OBSERVED,
                    sourceVersion = "USER_FIXED_LOAD",
                    evidenceIds = listOf(item.localId)
                )
                nonLoadMetric != null -> BCoachV2LoadInstruction.NonLoadMetric(
                    metric = nonLoadMetric.first,
                    value = nonLoadMetric.second
                )
                else -> {
                    val model = modelSnapshots[item.exerciseStableKey]
                    val step = equipmentStep(request, item.exerciseStableKey, exercise)
                    val requiresCurrentBodyweight = item.exerciseStableKey == "ex_e41f4c2b"
                    val modeled = if (model != null && !model.stale && step != null &&
                        !requiresCurrentBodyweight
                    ) {
                        modelCandidate(model, representativeReps, targetRir, step)
                    } else null
                    if (modeled != null) {
                        val availableModel = checkNotNull(model)
                        val rir = predictedRir(
                            availableModel.curve, availableModel.capacityBase, modeled, representativeReps
                        )
                        baseRir[item.localId] = rir
                        baseIntensity[item.localId] =
                            (modeled / availableModel.capacityBase).coerceIn(0.0, 1.5)
                        BCoachV2Scenario.entries.forEach { scenario ->
                            val capacity = when (scenario) {
                                BCoachV2Scenario.LOW_STRESS -> availableModel.capacityHigh
                                BCoachV2Scenario.BASE -> availableModel.capacityBase
                                BCoachV2Scenario.HIGH_STRESS -> availableModel.capacityLow
                            }
                            scenarioRir.getValue(scenario)[item.localId] = predictedRir(
                                availableModel.curve, capacity, modeled, representativeReps
                            )
                            scenarioIntensity.getValue(scenario)[item.localId] =
                                (modeled / capacity).coerceIn(0.0, 1.5)
                        }
                        BCoachV2LoadInstruction.ExactLoad(
                            kg = modeled,
                            tier = availableModel.tier,
                            targetRir = targetRir,
                            evidenceStatus = BCoachEvidenceStatus.MODEL_ESTIMATED,
                            sourceVersion = availableModel.sourceVersion,
                            evidenceIds = availableModel.evidenceIds
                        )
                    } else {
                        if (model?.stale == true) reasons += "STALE_MODEL:${item.exerciseStableKey}"
                        if (model != null && step == null) reasons += "EQUIPMENT_GRID_REQUIRED:${item.exerciseStableKey}"
                        if (model != null && requiresCurrentBodyweight) reasons +=
                            "CURRENT_BODYWEIGHT_REQUIRED:${item.exerciseStableKey}"
                        tierCHold(
                            item.exerciseStableKey,
                            representativeReps,
                            history.filter { row ->
                                row.stableKey == item.exerciseStableKey &&
                                    row.date in recentStart..request.asOf &&
                                    row.weightKg?.let { it.isFinite() && it > 0.0 } == true &&
                                    row.reps?.let { it.isFinite() && it > 0.0 } == true
                            }
                        ) ?: BCoachV2LoadInstruction.Calibrate(
                            when {
                                model?.stale == true -> "STALE_MODEL_NO_RECENT_SAME_EXERCISE_LOAD"
                                requiresCurrentBodyweight -> "CURRENT_BODYWEIGHT_REQUIRED_FOR_TOTAL_SYSTEM_LOAD"
                                step == null -> "EQUIPMENT_GRID_OR_SAME_EXERCISE_HISTORY_REQUIRED"
                                else -> "SAME_EXERCISE_LOAD_EVIDENCE_REQUIRED"
                            }
                        )
                    }
                }
            }
            instructions[item.localId] = instruction
            val updatedSets = sets.map { set ->
                when (instruction) {
                    is BCoachV2LoadInstruction.ExactLoad -> set.copy(
                        weightKg = instruction.kg,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.EXPLICIT_LOAD
                    )
                    is BCoachV2LoadInstruction.HoldLastConfirmed -> set.copy(
                        weightKg = instruction.kg,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.EXPLICIT_LOAD
                    )
                    is BCoachV2LoadInstruction.Calibrate -> set.copy(
                        weightKg = 0.0,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                    )
                    is BCoachV2LoadInstruction.NonLoadMetric -> set.copy(
                        weightKg = 0.0,
                        targetRpeMin = set.targetRpeMin,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                    )
                }
            }
            val commonWeight = updatedSets.map { it.weightKg }.distinct().singleOrNull() ?: 0.0
            item.copy(
                weightKg = commonWeight,
                weightSource = when (instruction) {
                    is BCoachV2LoadInstruction.ExactLoad -> "B_COACH_V2_${instruction.tier}_MODEL"
                    is BCoachV2LoadInstruction.HoldLastConfirmed -> "B_COACH_V2_TIER_C_HOLD"
                    is BCoachV2LoadInstruction.Calibrate -> "USER_CALIBRATION_REQUIRED"
                    is BCoachV2LoadInstruction.NonLoadMetric -> "B_COACH_V2_NON_LOAD_METRIC"
                },
                setPrescriptions = updatedSets
            )
        }
        return BCoachV2LoadResolution(
            skeleton = skeleton.copy(items = resolvedItems),
            instructionsByLocalId = instructions,
            baseRirByLocalId = baseRir,
            baseIntensityByLocalId = baseIntensity,
            rirByScenario = scenarioRir,
            intensityByScenario = scenarioIntensity,
            reasonCodes = reasons.toList().sorted()
        )
    }

    private fun equipmentStep(
        request: V22GenerationRequest,
        key: String,
        exercise: Exercise?
    ): Double? = when (BBlockEquipmentIncrements.kind(key, exercise)) {
        BEquipmentIncrementKind.BARBELL -> request.bShadowPlateOneSideKg
            ?.takeIf { it.isFinite() && it > 0.0 }?.times(2.0)
        BEquipmentIncrementKind.DUMBBELL -> request.bShadowDumbbellIncrementKg
            ?.takeIf { it.isFinite() && it > 0.0 }
        BEquipmentIncrementKind.MACHINE -> request.bShadowMachineIncrementKg
            ?.takeIf { it.isFinite() && it > 0.0 }
        BEquipmentIncrementKind.UNKNOWN -> null
    }

    private fun modelCandidate(
        model: BCoachV2LoadModelSnapshot,
        reps: Int,
        targetRir: Double,
        step: Double
    ): Double? {
        if (reps !in 1..20 || step <= 0.0) return null
        val relative = model.curve.evaluate(reps + targetRir).relativeLoad ?: return null
        val target = model.capacityBase * relative
        if (!target.isFinite() || target <= 0.0) return null
        val centerStep = round(target / step).toInt()
        val candidates = (centerStep - 8..centerStep + 4).map { it * step }
            .filter { it > 0.0 }.distinct().sorted()
        return candidates.filter { load ->
            listOf(model.capacityLow, model.capacityBase, model.capacityHigh).all { capacity ->
                predictedRir(model.curve, capacity, load, reps).let { it.isFinite() && it >= 0.0 }
            }
        }.minWithOrNull(compareBy<Double> {
            abs(predictedRir(model.curve, model.capacityBase, it, reps) - targetRir)
        }.thenBy { abs(it - target) }.thenBy { it })
    }

    internal fun predictedRir(
        curve: ResolvedRepetitionCurve,
        capacity: Double,
        load: Double,
        plannedReps: Int
    ): Double {
        if (!capacity.isFinite() || capacity <= 0.0 || !load.isFinite() || load <= 0.0) return Double.NaN
        val relative = load / capacity
        if (relative !in 0.0..1.0) return -1.0
        var low = 1.0
        var high = 20.0
        val lowRelative = curve.evaluate(low).relativeLoad ?: return Double.NaN
        val highRelative = curve.evaluate(high).relativeLoad ?: return Double.NaN
        if (relative > lowRelative) return -1.0
        if (relative < highRelative) return 20.0 - plannedReps
        repeat(64) {
            val middle = (low + high) / 2.0
            val value = curve.evaluate(middle).relativeLoad ?: return Double.NaN
            if (value > relative) low = middle else high = middle
        }
        return (low + high) / 2.0 - plannedReps
    }

    private fun tierCHold(
        stableKey: String,
        targetReps: Int,
        rows: List<V22HistoryRow>
    ): BCoachV2LoadInstruction.HoldLastConfirmed? {
        if (rows.isEmpty()) return null
        val sameRange = rows.filter { abs((it.reps ?: 0.0) - targetReps) <= 2.0 }
        val selected = if (sameRange.isNotEmpty()) {
            val weights = sameRange.mapNotNull(V22HistoryRow::weightKg).sorted()
            val median = if (weights.size % 2 == 1) weights[weights.size / 2]
                else (weights[weights.size / 2 - 1] + weights[weights.size / 2]) / 2.0
            sameRange.minWithOrNull(compareBy<V22HistoryRow> { abs(checkNotNull(it.weightKg) - median) }
                .thenByDescending { it.date }.thenByDescending { it.setIndex })
        } else rows.minWithOrNull(compareBy<V22HistoryRow> { abs((it.reps ?: 0.0) - targetReps) }
            .thenByDescending { it.date }.thenByDescending { it.setIndex })
        return selected?.weightKg?.let { kg ->
            BCoachV2LoadInstruction.HoldLastConfirmed(
                kg = kg,
                sourceDate = selected.date.toString(),
                evidenceIds = listOf("$stableKey:${selected.date}:${selected.setIndex}")
            )
        }
    }

    private fun targetRir(selectionRole: String, main: Boolean): Double = when {
        "SPORT" in selectionRole -> 3.0
        "HYPERTROPHY" in selectionRole -> 2.5
        main || "STRENGTH" in selectionRole || "PEAK" in selectionRole -> 2.0
        else -> 2.5
    }

    private fun nonLoadMetric(
        seconds: Int,
        reps: Int,
        exercise: Exercise?
    ): Pair<String, Double>? {
        val metric = exercise?.progressMetricType.orEmpty().uppercase()
        return when {
            seconds > 0 || listOf("DURATION", "TIME", "HOLD").any(metric::contains) ->
                "SECONDS" to seconds.takeIf { it > 0 }?.toDouble().orDefault(20.0)
            listOf("DISTANCE", "JUMP", "SPEED", "QUALITY", "ACCURACY").any(metric::contains) ->
                (metric.ifBlank { "QUALITY_REPETITIONS" }) to reps.coerceAtLeast(1).toDouble()
            else -> null
        }
    }

    private fun Double?.orDefault(default: Double): Double = this ?: default

    internal fun localCapacity(logMean: Double, logVariance: Double): Triple<Double, Double, Double> {
        val sd = sqrt(logVariance.coerceAtLeast(0.0))
        return Triple(exp(logMean - 1.2815515655446004 * sd), exp(logMean),
            exp(logMean + 1.2815515655446004 * sd))
    }
}

/** Read-only adapter over the already persisted posterior revision. */
internal class BCoachV2LoadEvidenceReader(
    private val posteriorDao: StrengthPosteriorDao,
    private val registry: StrengthPerformanceRegistry,
    private val curves: RepetitionCurveRegistry
) {
    suspend fun read(
        asOf: java.time.LocalDate,
        history: List<V22HistoryRow>,
        stableKeys: Set<String>
    ): Map<String, BCoachV2LoadModelSnapshot> {
        val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        val revision = posteriorDao.activeRevision(revisionKey)
            ?.takeIf(StrengthModelRevisionPolicy::isCompatible) ?: return emptyMap()
        val hasPending = posteriorDao.eventsForRevision(revisionKey).any { event ->
            event.status in setOf("PENDING", "FAILED") &&
                runCatching { java.time.LocalDate.parse(event.sessionDate) <= asOf }.getOrDefault(true)
        }
        if (hasPending) return emptyMap()
        val modelEntity = posteriorDao.modelState(StrengthModelRevisionPolicy.modelInstanceKey(revisionKey))
        val state = modelEntity?.let { runCatching { StrengthPosteriorModel.fromEntity(it) }.getOrNull() }
        val modelDate = modelEntity?.lastProcessedDate?.let {
            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
        }
        val localStates = posteriorDao.localStates(revisionKey).associateBy { it.exerciseStableKey }
        return stableKeys.sorted().mapNotNull { key ->
            val direct = registry.directTarget(key)
            val tier = if (key in BCoachV2LoadCandidateResolver.canonicalTierAKeys && direct != null) "TIER_A"
                else if (localStates[key]?.baselineEstablished == true) "TIER_B" else return@mapNotNull null
            val capacities = if (tier == "TIER_A") {
                val availableState = state ?: return@mapNotNull null
                val distribution = runCatching {
                    StrengthPosteriorModel.distribution(availableState, checkNotNull(direct))
                }.getOrNull() ?: return@mapNotNull null
                Triple(distribution.low80, distribution.median, distribution.high80)
            } else {
                val local = localStates.getValue(key)
                BCoachV2LoadCandidateResolver.localCapacity(local.stateLogMean, local.stateLogVariance)
            }
            if (listOf(capacities.first, capacities.second, capacities.third).any {
                    !it.isFinite() || it <= 0.0
                }) return@mapNotNull null
            val curveEntity = posteriorDao.curvePosterior(
                StrengthModelRevisionPolicy.curveSubjectKey(revisionKey, "exercise:$key")
            ) ?: posteriorDao.curvePosterior(
                StrengthModelRevisionPolicy.curveSubjectKey(revisionKey, "global:user-strength-endurance")
            )
            val personal = curveEntity?.let { runCatching { it.toPosterior() }.getOrNull() }
            val curve = runCatching { curves.resolve(key, personalTheta = personal?.meanTheta ?: 0.0) }
                .getOrNull() ?: return@mapNotNull null
            if (personal != null && personal.canonicalProfileId != curve.profile.id.value) return@mapNotNull null
            val lastProcessed = if (tier == "TIER_A") modelDate
                else runCatching { java.time.LocalDate.parse(localStates.getValue(key).lastProcessedDate) }.getOrNull()
            val stale = lastProcessed == null || lastProcessed > asOf || history.any { row ->
                row.stableKey == key && row.date <= asOf && row.date > lastProcessed
            }
            key to BCoachV2LoadModelSnapshot(
                stableKey = key,
                tier = tier,
                capacityLow = capacities.first,
                capacityBase = capacities.second,
                capacityHigh = capacities.third,
                curve = curve,
                sourceVersion = listOf(
                    revision.revisionKey,
                    modelEntity?.modelVersion ?: "LOCAL_POSTERIOR",
                    curve.profile.provenance.curveVersion
                ).joinToString("|"),
                evidenceIds = listOfNotNull(
                    modelEntity?.stateFingerprint,
                    localStates[key]?.stateFingerprint,
                    curveEntity?.posteriorFingerprint
                ),
                stale = stale
            )
        }.toMap()
    }
}
