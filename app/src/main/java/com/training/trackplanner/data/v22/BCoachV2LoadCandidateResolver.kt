package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.toPosterior
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.StrengthPosteriorDao
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
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
    val instructionsBySetId: Map<String, BCoachV2LoadInstruction>,
    val baseRirByLocalId: Map<String, Double>,
    val baseIntensityByLocalId: Map<String, Double>,
    val rirByScenario: Map<BCoachV2Scenario, Map<String, Double>>,
    val intensityByScenario: Map<BCoachV2Scenario, Map<String, Double>>,
    val baseRirBySetId: Map<String, Double>,
    val baseIntensityBySetId: Map<String, Double>,
    val rirByScenarioAndSet: Map<BCoachV2Scenario, Map<String, Double>>,
    val intensityByScenarioAndSet: Map<BCoachV2Scenario, Map<String, Double>>,
    val reasonCodes: List<String>
)

/** Exact-stableKey load resolution only; it never borrows another exercise's capacity. */
internal object BCoachV2LoadCandidateResolver {
    private data class DirectHistoryEstimate(
        val capacityKg: Double,
        val sourceDate: String,
        val evidenceId: String,
        val hasRpeEvidence: Boolean,
        val sourceVersion: String,
        val stale: Boolean
    )

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
        mainExerciseKeys: Set<String> = emptySet(),
        rirPolicy: RpeRirPolicy? = null
    ): BCoachV2LoadResolution {
        val instructionsByItem = linkedMapOf<String, BCoachV2LoadInstruction>()
        val instructionsBySet = linkedMapOf<String, BCoachV2LoadInstruction>()
        val baseRirBySet = linkedMapOf<String, Double>()
        val baseIntensityBySet = linkedMapOf<String, Double>()
        val scenarioRirBySet = BCoachV2Scenario.entries.associateWith { linkedMapOf<String, Double>() }
        val scenarioIntensityBySet = BCoachV2Scenario.entries.associateWith { linkedMapOf<String, Double>() }
        val reasons = linkedSetOf<String>()
        val recentStart = request.asOf.minusDays(55)

        val resolvedItems = skeleton.items.map { item ->
            val exercise = exercises[item.exerciseStableKey]
            val sets = ProgramSetPrescriptionResolver.resolve(item)
            val storedModel = modelSnapshots[item.exerciseStableKey]
            val step = equipmentStep(request, item.exerciseStableKey, exercise)
            val allRows = history.filter { row ->
                row.stableKey == item.exerciseStableKey && !row.date.isAfter(request.asOf) &&
                    row.weightKg?.let { it.isFinite() && it > 0.0 } == true &&
                    row.reps?.let { it.isFinite() && it > 0.0 } == true
            }
            val recentRows = allRows.filter { it.date >= recentStart }
            val directEstimate = directHistoryEstimate(item.exerciseStableKey, allRows, request, rirPolicy)
            val model = referenceModel(storedModel, directEstimate).also { effective ->
                if (storedModel != null && effective != null && effective.capacityBase > storedModel.capacityBase + 1e-9) {
                    reasons += "DIRECT_RECORD_EXCEEDS_MODEL:${item.exerciseStableKey}"
                }
            }
            if (directEstimate != null && !directEstimate.hasRpeEvidence) reasons +=
                "DIRECT_HISTORY_COMPLETED_REPS_LOWER_BOUND:${item.exerciseStableKey}"
            if (directEstimate?.stale == true) reasons +=
                "STALE_DIRECT_HISTORY_UNCERTAINTY:${item.exerciseStableKey}"
            val requiresCurrentBodyweight = item.exerciseStableKey == "ex_e41f4c2b"
            if (model?.stale == true) reasons += "STALE_MODEL:${item.exerciseStableKey}"
            if ((model != null || directEstimate != null) && step == null) reasons +=
                "EQUIPMENT_GRID_REQUIRED_ESTIMATE_AVAILABLE:${item.exerciseStableKey}"
            if (model != null && requiresCurrentBodyweight) reasons +=
                "CURRENT_BODYWEIGHT_REQUIRED:${item.exerciseStableKey}"

            val updatedSets = sets.map { set ->
                val setId = setId(item.localId, set.setIndex)
                val fixedBySet = isSetUserFixed(set, item.weightSource)
                val validFixedLoad = fixedBySet && set.loadState == ProgramLoadState.EXPLICIT_LOAD &&
                    set.weightKg.isFinite() && set.weightKg > 0.0
                if (fixedBySet && !validFixedLoad) reasons += "INVALID_USER_FIXED_LOAD:$setId"
                val observedTargetRir = set.targetRpeMin?.takeIf { it.isFinite() && it in 1.0..10.0 }
                    ?.let { 10.0 - it }
                val hasStrongEvidence = model?.stale == false ||
                    (directEstimate?.hasRpeEvidence == true && !directEstimate.stale)
                val targetRir = observedTargetRir ?: targetRir(
                    item.selectionRole, item.exerciseStableKey in mainExerciseKeys, hasStrongEvidence
                )
                val observedSetFactor = observedFollowUpFactor(set.setIndex, set.reps, recentRows)
                val setFactor = observedSetFactor ?: followUpSetFactor(set.setIndex)
                if (observedSetFactor != null) reasons +=
                    "OBSERVED_FOLLOW_UP_FACTOR:${item.exerciseStableKey}:set=${set.setIndex}"
                val targetRpe = 10.0 - targetRir
                val nonLoadMetric = nonLoadMetric(set.seconds, set.reps, exercise)
                val instruction = when {
                    validFixedLoad -> BCoachV2LoadInstruction.ExactLoad(
                        kg = set.weightKg,
                        tier = "USER_FIXED",
                        targetRir = targetRir,
                        loadSource = BCoachLoadSource.USER_FIXED,
                        capacityKg = model?.takeIf { !it.stale }?.capacityBase ?: directEstimate?.capacityKg,
                        freshRir = model?.takeIf { !it.stale }?.let {
                            predictedRir(it.curve, it.capacityBase, set.weightKg, set.reps)
                        } ?: directEstimate?.let { predictedRirEpley(it.capacityKg, set.weightKg, set.reps) },
                        expectedRir = (model?.takeIf { !it.stale }?.let {
                            predictedRir(it.curve, it.capacityBase, set.weightKg, set.reps)
                        } ?: directEstimate?.let { predictedRirEpley(it.capacityKg, set.weightKg, set.reps) })
                            ?.let { fresh -> workingRir(fresh, set.setIndex, set.reps,
                                set.weightKg / (model?.takeIf { !it.stale }?.capacityBase
                                    ?: directEstimate?.capacityKg ?: set.weightKg),
                                item.restSeconds) },
                        evidenceStatus = BCoachEvidenceStatus.OBSERVED,
                        sourceVersion = "USER_FIXED_LOAD",
                        evidenceIds = listOf(setId)
                    )
                    fixedBySet -> BCoachV2LoadInstruction.Calibrate(
                        reason = "INVALID_USER_FIXED_LOAD",
                        evidenceIds = listOf(setId)
                    )
                    nonLoadMetric != null -> BCoachV2LoadInstruction.NonLoadMetric(
                        metric = nonLoadMetric.first,
                        value = nonLoadMetric.second
                    )
                    model != null && !model.stale && !requiresCurrentBodyweight -> {
                        val estimated = modelEstimatedCandidate(model, set.reps, targetRir, setFactor)
                        val kg = step?.let { modelCandidate(model, set.reps, targetRir, it, setFactor) }
                        when {
                            kg != null -> modelInstruction(model, setId, set.reps, targetRir, kg,
                                setFactor, set.setIndex, item.restSeconds)
                            estimated != null && step == null -> modelEstimatedInstruction(model, setId,
                                set.reps, targetRir, estimated, setFactor, set.setIndex, item.restSeconds)
                            else -> fallbackInstruction(
                                item.exerciseStableKey, set.reps, directEstimate, targetRir,
                                step, set.setIndex, setFactor, model, requiresCurrentBodyweight,
                                item.restSeconds
                            )
                    }
                    }
                    else -> fallbackInstruction(
                        item.exerciseStableKey, set.reps, directEstimate, targetRir,
                        step, set.setIndex, setFactor, model, requiresCurrentBodyweight,
                        item.restSeconds
                    )
                }
                instructionsBySet[setId] = instruction
                instructionMetrics(
                    instruction, model, set.reps, set.setIndex, item.restSeconds, setId,
                    baseRirBySet, baseIntensityBySet,
                    scenarioRirBySet, scenarioIntensityBySet
                )
                when (instruction) {
                    is BCoachV2LoadInstruction.ExactLoad -> set.copy(
                        weightKg = instruction.kg,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.EXPLICIT_LOAD,
                        estimatedLoadKg = instruction.kg,
                        loadSource = instruction.loadSource,
                        freshRir = instruction.freshRir,
                        expectedRir = instruction.expectedRir,
                        relativeIntensity = instruction.capacityKg?.let { instruction.kg / it },
                        evidenceVersion = instruction.sourceVersion
                    )
                    is BCoachV2LoadInstruction.EstimatedLoad -> set.copy(
                        weightKg = 0.0,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                        estimatedLoadKg = instruction.estimatedKg,
                        loadSource = instruction.loadSource,
                        freshRir = instruction.freshRir,
                        expectedRir = instruction.expectedRir,
                        relativeIntensity = instruction.relativeIntensity,
                        evidenceVersion = instruction.sourceVersion
                    )
                    is BCoachV2LoadInstruction.HoldLastConfirmed -> set.copy(
                        weightKg = instruction.kg,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.EXPLICIT_LOAD,
                        estimatedLoadKg = instruction.kg,
                        loadSource = BCoachLoadSource.HISTORY_HOLD,
                        evidenceVersion = instruction.sourceVersion
                    )
                    is BCoachV2LoadInstruction.Calibrate -> set.copy(
                        weightKg = 0.0,
                        targetRpeMin = set.targetRpeMin ?: targetRpe,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                        estimatedLoadKg = null,
                        loadSource = BCoachLoadSource.CALIBRATION_REQUIRED,
                        freshRir = null,
                        expectedRir = null,
                        relativeIntensity = null,
                        evidenceVersion = instruction.sourceVersion
                    )
                    is BCoachV2LoadInstruction.NonLoadMetric -> set.copy(
                        weightKg = 0.0,
                        targetRpeMin = set.targetRpeMin,
                        loadState = ProgramLoadState.NOT_APPLICABLE,
                        estimatedLoadKg = null,
                        loadSource = BCoachLoadSource.NON_LOAD_METRIC,
                        freshRir = null,
                        expectedRir = null,
                        relativeIntensity = null,
                        evidenceVersion = instruction.sourceVersion
                    )
                }
            }
            instructionsByItem[item.localId] = instructionsBySet.getValue(setId(item.localId, updatedSets.first().setIndex))
            val commonWeight = updatedSets.map { it.weightKg }.distinct().singleOrNull() ?: 0.0
            val itemSources = updatedSets.map { set ->
                when (val instruction = instructionsBySet.getValue(setId(item.localId, set.setIndex))) {
                    is BCoachV2LoadInstruction.ExactLoad -> instruction.loadSource.name
                    is BCoachV2LoadInstruction.EstimatedLoad -> instruction.loadSource.name
                    is BCoachV2LoadInstruction.HoldLastConfirmed -> BCoachLoadSource.HISTORY_HOLD.name
                    is BCoachV2LoadInstruction.Calibrate -> BCoachLoadSource.CALIBRATION_REQUIRED.name
                    is BCoachV2LoadInstruction.NonLoadMetric -> BCoachLoadSource.NON_LOAD_METRIC.name
                }
            }.distinct()
            item.copy(
                weightKg = commonWeight,
                weightSource = if (itemSources.size == 1) itemSources.single() else "B_COACH_V2_SET_SPECIFIC",
                setPrescriptions = updatedSets
            )
        }
        val baseRirByItem = aggregateByItem(baseRirBySet)
        val baseIntensityByItem = aggregateByItem(baseIntensityBySet)
        return BCoachV2LoadResolution(
            skeleton = skeleton.copy(items = resolvedItems),
            instructionsByLocalId = instructionsByItem,
            instructionsBySetId = instructionsBySet,
            baseRirByLocalId = baseRirByItem,
            baseIntensityByLocalId = baseIntensityByItem,
            rirByScenario = scenarioRirBySet.mapValues { aggregateByItem(it.value) },
            intensityByScenario = scenarioIntensityBySet.mapValues { aggregateByItem(it.value) },
            baseRirBySetId = baseRirBySet,
            baseIntensityBySetId = baseIntensityBySet,
            rirByScenarioAndSet = scenarioRirBySet,
            intensityByScenarioAndSet = scenarioIntensityBySet,
            reasonCodes = reasons.toList().sorted()
        )
    }

    internal fun equipmentStep(
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
        step: Double,
        followUpFactor: Double
    ): Double? {
        if (reps !in 1..20 || step <= 0.0) return null
        val relative = model.curve.evaluate(reps + targetRir).relativeLoad ?: return null
        val firstSet = floorToStep(
            model.capacityBase * relative * BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR,
            step
        )
        val target = floorToStep(firstSet * followUpFactor, step)
        if (!target.isFinite() || target <= 0.0) return null
        var candidate = target
        while (candidate > 0.0 && listOf(model.capacityLow, model.capacityBase, model.capacityHigh).any { capacity ->
                predictedRir(model.curve, capacity, candidate, reps).let { !it.isFinite() || it < 0.0 }
            }) candidate -= step
        return candidate.takeIf { it > 0.0 }
    }

    private fun modelEstimatedCandidate(
        model: BCoachV2LoadModelSnapshot,
        reps: Int,
        targetRir: Double,
        followUpFactor: Double
    ): Double? {
        if (reps !in 1..20) return null
        val relative = model.curve.evaluate(reps + targetRir).relativeLoad ?: return null
        val baseCandidate = model.capacityBase * relative *
            BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR * followUpFactor
        val lowCapacityCeiling = model.capacityLow * relative * followUpFactor
        return minOf(baseCandidate, lowCapacityCeiling).takeIf { it.isFinite() && it > 0.0 }
    }

    internal fun epleyTrainingWeight(
        capacityKg: Double,
        reps: Int,
        targetRir: Double,
        step: Double,
        setIndex: Int = 1,
        followUpFactor: Double = followUpSetFactor(setIndex)
    ): Double? {
        if (!capacityKg.isFinite() || capacityKg <= 0.0 || reps !in 1..20 ||
            !targetRir.isFinite() || targetRir < 0.0 || !step.isFinite() || step <= 0.0
        ) return null
        val base = capacityKg / (1.0 + (reps + targetRir) / 30.0)
        val firstSet = floorToStep(base * BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR, step)
        return floorToStep(firstSet * followUpFactor, step).takeIf { it > 0.0 }
    }

    internal fun epleyEstimatedTrainingWeight(
        capacityKg: Double,
        reps: Int,
        targetRir: Double,
        followUpFactor: Double = 1.0
    ): Double? {
        if (!capacityKg.isFinite() || capacityKg <= 0.0 || reps !in 1..20 ||
            !targetRir.isFinite() || targetRir < 0.0 || !followUpFactor.isFinite() || followUpFactor <= 0.0
        ) return null
        return (capacityKg / (1.0 + (reps + targetRir) / 30.0) *
            BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR * followUpFactor)
            .takeIf { it.isFinite() && it > 0.0 }
    }

    internal fun epleyEstimatedOneRepMax(weightKg: Double, reps: Double, rir: Double): Double? =
        if (weightKg.isFinite() && weightKg > 0.0 && reps.isFinite() && reps in 1.0..20.0 &&
            rir.isFinite() && rir >= 0.0
        ) weightKg * (1.0 + (reps + rir) / 30.0) else null

    private fun predictedRirEpley(capacityKg: Double, loadKg: Double, reps: Int): Double =
        if (capacityKg > 0.0 && loadKg > 0.0 && reps > 0) 30.0 * (capacityKg / loadKg - 1.0) - reps
        else Double.NaN

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

    private fun modelInstruction(
        model: BCoachV2LoadModelSnapshot,
        setId: String,
        reps: Int,
        targetRir: Double,
        load: Double,
        followUpFactor: Double,
        setIndex: Int,
        restSeconds: Int
    ): BCoachV2LoadInstruction.ExactLoad {
        val fresh = predictedRir(model.curve, model.capacityBase, load, reps)
        return BCoachV2LoadInstruction.ExactLoad(
            kg = load,
            tier = model.tier,
            targetRir = targetRir,
            loadSource = BCoachLoadSource.PERSONAL_MODEL,
            capacityKg = model.capacityBase,
            freshRir = fresh,
            expectedRir = workingRir(fresh, setIndex, reps, load / model.capacityBase, restSeconds),
            conservativeFactor = BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR,
            followUpSetFactor = followUpFactor,
            evidenceStatus = BCoachEvidenceStatus.MODEL_ESTIMATED,
            sourceVersion = model.sourceVersion,
            evidenceIds = model.evidenceIds + setId
        )
    }

    private fun modelEstimatedInstruction(
        model: BCoachV2LoadModelSnapshot,
        setId: String,
        reps: Int,
        targetRir: Double,
        load: Double,
        followUpFactor: Double,
        setIndex: Int,
        restSeconds: Int
    ): BCoachV2LoadInstruction.EstimatedLoad {
        val fresh = predictedRir(model.curve, model.capacityBase, load, reps)
        val intensity = load / model.capacityBase
        return BCoachV2LoadInstruction.EstimatedLoad(
            estimatedKg = load,
            tier = model.tier,
            targetRir = targetRir,
            loadSource = BCoachLoadSource.PERSONAL_MODEL,
            capacityKg = model.capacityBase,
            freshRir = fresh,
            expectedRir = workingRir(fresh, setIndex, reps, intensity, restSeconds),
            relativeIntensity = intensity,
            conservativeFactor = BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR,
            followUpSetFactor = followUpFactor,
            evidenceStatus = BCoachEvidenceStatus.MODEL_ESTIMATED,
            sourceVersion = model.sourceVersion,
            evidenceIds = model.evidenceIds + setId
        )
    }

    private fun fallbackInstruction(
        stableKey: String,
        reps: Int,
        directEstimate: DirectHistoryEstimate?,
        targetRir: Double,
        step: Double?,
        setIndex: Int,
        followUpFactor: Double,
        model: BCoachV2LoadModelSnapshot?,
        requiresCurrentBodyweight: Boolean,
        restSeconds: Int
    ): BCoachV2LoadInstruction {
        if (!requiresCurrentBodyweight && directEstimate != null) {
            val estimated = epleyEstimatedTrainingWeight(
                directEstimate.capacityKg, reps, targetRir, followUpFactor
            )
            val prescribed = step?.let {
                epleyTrainingWeight(directEstimate.capacityKg, reps, targetRir, it, setIndex, followUpFactor)
            }
            (prescribed ?: estimated)?.let { kg ->
                val fresh = predictedRirEpley(directEstimate.capacityKg, kg, reps)
                val intensity = kg / directEstimate.capacityKg
                if (prescribed == null) return BCoachV2LoadInstruction.EstimatedLoad(
                    estimatedKg = kg,
                    tier = "TIER_C_DIRECT_HISTORY",
                    targetRir = targetRir,
                    loadSource = BCoachLoadSource.DIRECT_HISTORY_ESTIMATE,
                    capacityKg = directEstimate.capacityKg,
                    freshRir = fresh,
                    expectedRir = workingRir(fresh, setIndex, reps, intensity, restSeconds),
                    relativeIntensity = intensity,
                    conservativeFactor = BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR,
                    followUpSetFactor = followUpFactor,
                    evidenceStatus = directEvidenceStatus(directEstimate),
                    sourceVersion = directEstimate.sourceVersion,
                    evidenceIds = listOf(directEstimate.evidenceId)
                )
                return BCoachV2LoadInstruction.ExactLoad(
                    kg = kg,
                    tier = "TIER_C_DIRECT_HISTORY",
                    targetRir = targetRir,
                    loadSource = BCoachLoadSource.DIRECT_HISTORY_ESTIMATE,
                    capacityKg = directEstimate.capacityKg,
                    freshRir = fresh,
                    expectedRir = workingRir(fresh, setIndex, reps, intensity, restSeconds),
                    conservativeFactor = BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR,
                    followUpSetFactor = followUpFactor,
                    evidenceStatus = directEvidenceStatus(directEstimate),
                    sourceVersion = directEstimate.sourceVersion,
                    evidenceIds = listOf(directEstimate.evidenceId)
                )
            }
        }
        return BCoachV2LoadInstruction.Calibrate(
            when {
                requiresCurrentBodyweight -> "CURRENT_BODYWEIGHT_REQUIRED_FOR_TOTAL_SYSTEM_LOAD"
                model?.stale == true -> "STALE_MODEL_NO_USABLE_DIRECT_HISTORY_ESTIMATE"
                step == null -> "EQUIPMENT_GRID_OR_SAME_EXERCISE_HISTORY_REQUIRED"
                else -> "SAME_EXERCISE_LOAD_EVIDENCE_REQUIRED"
            }
        )
    }

    private fun instructionMetrics(
        instruction: BCoachV2LoadInstruction,
        model: BCoachV2LoadModelSnapshot?,
        reps: Int,
        setIndex: Int,
        restSeconds: Int,
        setId: String,
        baseRir: MutableMap<String, Double>,
        baseIntensity: MutableMap<String, Double>,
        scenarioRir: Map<BCoachV2Scenario, MutableMap<String, Double>>,
        scenarioIntensity: Map<BCoachV2Scenario, MutableMap<String, Double>>
    ) {
        val load = when (instruction) {
            is BCoachV2LoadInstruction.ExactLoad -> instruction.kg
            is BCoachV2LoadInstruction.EstimatedLoad -> instruction.estimatedKg
            else -> return
        }
        val capacity = when (instruction) {
            is BCoachV2LoadInstruction.ExactLoad -> instruction.capacityKg
            is BCoachV2LoadInstruction.EstimatedLoad -> instruction.capacityKg
            else -> null
        } ?: model?.takeIf { !it.stale }?.capacityBase ?: return
        val source = when (instruction) {
            is BCoachV2LoadInstruction.ExactLoad -> instruction.loadSource
            is BCoachV2LoadInstruction.EstimatedLoad -> instruction.loadSource
            else -> return
        }
        val useCurve = model != null && !model.stale &&
            source != BCoachLoadSource.DIRECT_HISTORY_ESTIMATE
        fun rir(value: Double): Double = if (useCurve) {
            predictedRir(checkNotNull(model).curve, value, load, reps)
        } else predictedRirEpley(value, load, reps)
        val baseIntensityValue = (load / capacity).coerceIn(0.0, 1.5)
        baseRir[setId] = workingRir(rir(capacity), setIndex, reps, baseIntensityValue, restSeconds)
        baseIntensity[setId] = baseIntensityValue
        BCoachV2Scenario.entries.forEach { scenario ->
            val scenarioCapacity = if (useCurve) when (scenario) {
                BCoachV2Scenario.LOW_STRESS -> checkNotNull(model).capacityHigh
                BCoachV2Scenario.BASE -> model.capacityBase
                BCoachV2Scenario.HIGH_STRESS -> model.capacityLow
            } else capacity
            val intensity = (load / scenarioCapacity).coerceIn(0.0, 1.5)
            scenarioRir.getValue(scenario)[setId] =
                workingRir(rir(scenarioCapacity), setIndex, reps, intensity, restSeconds)
            scenarioIntensity.getValue(scenario)[setId] = intensity
        }
    }

    private fun directHistoryEstimate(
        stableKey: String,
        rows: List<V22HistoryRow>,
        request: V22GenerationRequest,
        rirPolicy: RpeRirPolicy?
    ): DirectHistoryEstimate? = rows.distinctBy { row ->
        listOf(row.date, row.setIndex, row.weightKg, row.reps, row.rpe)
    }.mapNotNull { row ->
        val reps = row.reps ?: return@mapNotNull null
        val weight = row.weightKg ?: return@mapNotNull null
        if (weight !in 0.1..1_000.0 || reps !in 1.0..20.0) return@mapNotNull null
        val resolvedRir = row.rpe?.let { rirPolicy?.resolve(it)?.expectedRir }
        val capacity = if (resolvedRir == null && reps == 1.0) weight else
            epleyEstimatedOneRepMax(weight, reps, resolvedRir ?: 0.0) ?: return@mapNotNull null
        val stale = row.date < request.asOf.minusDays(55)
        DirectHistoryEstimate(
            capacityKg = capacity,
            sourceDate = row.date.toString(),
            evidenceId = "$stableKey:${row.date}:${row.setIndex}",
            hasRpeEvidence = resolvedRir != null,
            sourceVersion = if (resolvedRir != null) "DIRECT_HISTORY_EPLEY:${rirPolicy?.version}"
                else if (reps == 1.0) "DIRECT_HISTORY_SINGLE_REP_COMPLETED_LOWER_BOUND"
                else "DIRECT_HISTORY_EPLEY_COMPLETED_REPS_LOWER_BOUND",
            stale = stale
        )
    }.maxWithOrNull(compareBy<DirectHistoryEstimate> { it.capacityKg }.thenBy { it.sourceDate })

    private fun directEvidenceStatus(estimate: DirectHistoryEstimate): BCoachEvidenceStatus = when {
        estimate.stale -> BCoachEvidenceStatus.STALE
        estimate.hasRpeEvidence -> BCoachEvidenceStatus.OBSERVED
        else -> BCoachEvidenceStatus.POLICY_ASSUMED
    }

    private fun referenceModel(
        model: BCoachV2LoadModelSnapshot?,
        direct: DirectHistoryEstimate?
    ): BCoachV2LoadModelSnapshot? {
        if (model == null || direct == null || direct.capacityKg <= model.capacityBase) return model
        val scale = direct.capacityKg / model.capacityBase
        return model.copy(
            capacityLow = model.capacityLow * scale,
            capacityBase = direct.capacityKg,
            capacityHigh = model.capacityHigh * scale,
            sourceVersion = "${model.sourceVersion}|HIGHEST_VALID_DIRECT_HISTORY:${direct.sourceVersion}",
            evidenceIds = (model.evidenceIds + direct.evidenceId).distinct(),
            stale = model.stale && direct.stale
        )
    }

    internal fun referenceCapacityByStableKey(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        modelSnapshots: Map<String, BCoachV2LoadModelSnapshot> = emptyMap(),
        rirPolicy: RpeRirPolicy? = null
    ): Map<String, Double> {
        val historyAtCutoff = history.filter { !it.date.isAfter(request.asOf) }
        return (historyAtCutoff.map(V22HistoryRow::stableKey) + modelSnapshots.keys)
            .distinct().sorted().mapNotNull { stableKey ->
                val direct = directHistoryEstimate(
                    stableKey,
                    historyAtCutoff.filter { it.stableKey == stableKey },
                    request,
                    rirPolicy
                )
                val effectiveModel = referenceModel(modelSnapshots[stableKey], direct)
                val capacity = effectiveModel?.takeIf { !it.stale }?.capacityBase
                    ?: direct?.capacityKg
                stableKey to (capacity?.takeIf { it.isFinite() && it > 0.0 }
                    ?: return@mapNotNull null)
            }.toMap()
    }

    internal fun isSetUserFixed(set: ProgramSetPrescription, itemWeightSource: String): Boolean =
        when (set.loadSource) {
            ProgramLoadSource.USER_FIXED -> true
            ProgramLoadSource.LEGACY_UNKNOWN -> isLegacyUserFixed(itemWeightSource)
            else -> false
        }

    private fun isLegacyUserFixed(weightSource: String): Boolean {
        val source = weightSource.trim().uppercase()
        return source == BCoachLoadSource.USER_FIXED.name || "USER_OVERRIDE" in source ||
            "MANUAL" in source || source == "B_BLOCK_USER_START_LOAD_DERIVED"
    }

    internal fun setId(localId: String, setIndex: Int): String = "$localId#set=$setIndex"

    private fun aggregateByItem(values: Map<String, Double>): Map<String, Double> = values.entries
        .groupBy { it.key.substringBefore("#set=") }
        .mapValues { (_, rows) -> rows.map { it.value }.average() }

    private fun followUpSetFactor(setIndex: Int): Double = when (setIndex) {
        in 2..3 -> BCoachV2Policy.FOLLOW_UP_SET_2_TO_3_FACTOR
        in 4..Int.MAX_VALUE -> BCoachV2Policy.FOLLOW_UP_SET_4_TO_5_FACTOR
        else -> 1.0
    }

    private fun observedFollowUpFactor(
        setIndex: Int,
        targetReps: Int,
        rows: List<V22HistoryRow>
    ): Double? {
        if (setIndex <= 1) return null
        val ratios = rows.groupBy(V22HistoryRow::date).values.mapNotNull { session ->
            fun closest(index: Int) = session.filter { row ->
                row.setIndex == index && abs((row.reps ?: 0.0) - targetReps) <= 2.0
            }.minByOrNull { row -> abs((row.reps ?: 0.0) - targetReps) }
            val first = closest(1)?.weightKg ?: return@mapNotNull null
            val later = closest(setIndex)?.weightKg ?: return@mapNotNull null
            (later / first).takeIf { it.isFinite() && it in 0.70..1.05 }
        }
        if (ratios.size < 2) return null
        val sorted = ratios.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return median.coerceAtMost(1.0)
    }

    private fun floorToStep(value: Double, step: Double): Double =
        if (value.isFinite() && value > 0.0 && step.isFinite() && step > 0.0) {
            floor((value + 1e-9) / step) * step
        } else 0.0

    private fun targetRir(selectionRole: String, main: Boolean, strongEvidence: Boolean): Double = when {
        !strongEvidence -> 4.0
        "SPORT" in selectionRole -> 3.0
        "HYPERTROPHY" in selectionRole -> 2.5
        "PEAK" in selectionRole -> 2.5
        main || "STRENGTH" in selectionRole -> 3.0
        else -> 3.0
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

    internal fun workingRir(
        freshRir: Double,
        setIndex: Int,
        reps: Int,
        relativeIntensity: Double,
        restSeconds: Int
    ): Double {
        if (!freshRir.isFinite()) return freshRir
        if (setIndex <= 1) return freshRir
        val restFactor = (90.0 / restSeconds.coerceAtLeast(30)).coerceIn(0.6, 1.4)
        val perPriorSet = (0.20 + 0.015 * reps.coerceIn(1, 30) +
            0.30 * relativeIntensity.coerceIn(0.0, 1.2)) * restFactor
        val fatigue = ((setIndex - 1) * perPriorSet)
            .coerceIn(0.0, BCoachV2Policy.WORKING_RIR_MAX_FATIGUE)
        return (freshRir - fatigue).coerceIn(-1.0, 20.0)
    }

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
