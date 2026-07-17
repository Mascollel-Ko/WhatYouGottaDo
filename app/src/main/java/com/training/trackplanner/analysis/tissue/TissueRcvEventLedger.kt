package com.training.trackplanner.analysis.tissue

import kotlin.math.ln

object TissueRcvDoseResolver {
    fun resolve(record: TissueWorkoutRecord, basis: String): TissueDoseResolution = when (basis) {
        "WEIGHTED_REPETITION" ->
            TissueDoseResolver.resolve(record, TissueDoseBasis.EXTERNAL_LOAD_REPETITIONS)
        "BODYWEIGHT_REPETITION" ->
            TissueDoseResolver.resolve(record, TissueDoseBasis.EFFECTIVE_BODYWEIGHT_REPETITIONS)
        "DURATION_HOLD", "HOLD_TIME" ->
            TissueDoseResolver.resolve(record, TissueDoseBasis.DURATION_HOLD)
        "DURATION_SESSION", "CYCLIC_WORK", "DISTANCE_TIME", "LOADED_CARRY",
        "ACTIVE_TIME", "SPORT_ACTIVE_TIME_EVENTS", "LOAD_DISTANCE_TIME", "POSITION_TIME" ->
            explicitDuration(record)
        "EVENT_COUNT", "FOOT_CONTACT_COD", "STEP_CONTACT", "GROUND_CONTACT", "STEP_COUNT",
        "BAND_REPETITION", "CONTACT_EVENT", "COD_DECEL_EVENT", "OVERHEAD_STRIKE_EVENT",
        "THROW_EVENT" -> explicitCount(record)
        else -> missing("Unsupported reviewed dose basis: $basis")
    }

    private fun explicitDuration(record: TissueWorkoutRecord): TissueDoseResolution {
        val seconds = record.sets.filter { it.confirmed }.sumOf { it.seconds }
        return if (seconds > 0) {
            TissueDoseResolution(seconds.toDouble(), TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD)
        } else {
            missing("No explicit duration is recorded.")
        }
    }

    private fun explicitCount(record: TissueWorkoutRecord): TissueDoseResolution {
        val count = record.sets.filter { it.confirmed }.sumOf { it.reps }
        return if (count > 0) {
            TissueDoseResolution(count.toDouble(), TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD)
        } else {
            missing("No explicit event count is recorded; no count was invented.")
        }
    }

    private fun missing(message: String) = TissueDoseResolution(
        null,
        TissueDoseResolutionStatus.MISSING_RECORD_INPUT,
        diagnostics = listOf(message)
    )
}

object TissueEffortResolver {
    fun resolve(record: TissueWorkoutRecord, dose: TissueDoseResolution): TissueEffortSelection {
        if (dose.rpeAlreadyApplied) {
            return TissueEffortSelection(1.0, TissueEffortSource.DOSE_ALREADY_EFFORT_ADJUSTED)
        }
        val setRpe = record.sets.filter { it.confirmed }.mapNotNull { it.rpe }
            .takeIf { it.isNotEmpty() }?.average()
        if (setRpe != null) {
            val rejected = if (record.entry.rpe != null) listOf(TissueEffortSource.ENTRY_RPE) else emptyList()
            return effort(setRpe, TissueEffortSource.SET_RPE, rejected)
        }
        record.entry.rpe?.let { return effort(it, TissueEffortSource.ENTRY_RPE) }
        return TissueEffortSelection(
            null,
            TissueEffortSource.UNRESOLVED,
            diagnostics = listOf("No reviewed effort source is recorded.")
        )
    }

    private fun effort(
        rpe: Double,
        source: TissueEffortSource,
        rejected: List<TissueEffortSource> = emptyList()
    ): TissueEffortSelection =
        if (!rpe.isFinite() || rpe !in 0.0..10.0) {
            TissueEffortSelection(null, TissueEffortSource.UNRESOLVED, diagnostics = listOf("RPE is outside 0..10."))
        } else {
            TissueEffortSelection(rpe / 10.0, source, rejected)
        }
}

class TissueRcvEventLedgerBuilder(
    private val catalog: TissueRcvCatalog,
    private val zoneId: java.time.ZoneId
) {
    private val contextModifierResolver = TissueContextModifierResolver(catalog)

    fun build(records: List<TissueWorkoutRecord>): TissueEventLedgerResult {
        val ordered = records.sortedWith(
            compareBy<TissueWorkoutRecord>({ it.date }, { it.entry.id }, { it.exercise.stableKey })
        )
        val authorityByStableKey = catalog.authorityRows.groupBy(TissueRcvAuthorityRow::exerciseStableKey)
        val rawDoseCache = mutableMapOf<Pair<Long, String>, TissueDoseResolution>()
        fun dose(record: TissueWorkoutRecord, basis: String): TissueDoseResolution =
            rawDoseCache.getOrPut(record.entry.id to basis) { TissueRcvDoseResolver.resolve(record, basis) }

        val events = mutableListOf<TissueExposureEvent>()
        val diagnostics = mutableListOf<String>()
        ordered.forEach { record ->
            val protocol = catalog.protocols[record.exercise.stableKey]
            val rows = authorityByStableKey[record.exercise.stableKey].orEmpty()
            if (protocol == null || rows.isEmpty()) {
                diagnostics += "${record.entry.id}:${record.exercise.stableKey}: missing reviewed mapping."
                return@forEach
            }
            rows.groupBy { it.loadUnitStableKey to it.loadProfileP }.toSortedMap(
                compareBy<Pair<String, String>>({ it.first }, { it.second })
            ).forEach { (pair, group) ->
                val basis = group.map(TissueRcvAuthorityRow::doseBasis).distinct().singleOrNull()
                if (basis == null) {
                    diagnostics += "${record.entry.id}:${pair.first}: conflicting dose basis."
                    return@forEach
                }
                val resolvedDose = dose(record, basis)
                val rawDose = resolvedDose.resolvedDose
                if (rawDose == null) {
                    diagnostics += "${record.entry.id}:${pair.first}: ${resolvedDose.diagnostics.joinToString()}"
                    return@forEach
                }
                val effort = TissueEffortResolver.resolve(record, resolvedDose)
                val effortValue = effort.value
                if (effortValue == null) {
                    diagnostics += "${record.entry.id}:${pair.first}: ${effort.diagnostics.joinToString()}"
                    return@forEach
                }
                val reference = robustReference(ordered, record, basis, ::dose)
                val normalizedDose = normalize(rawDose, reference)
                val loadUnit = requireNotNull(catalog.loadUnits[pair.first])
                val routing = requireNotNull(catalog.routing[loadUnit.recoveryClass])
                val curves = curves(protocol, routing, group.first().bodyRegion)
                val magnitude = group.maxOf(TissueRcvAuthorityRow::magnitudeM)
                val contextResolution = contextModifierResolver.resolve(
                    record.exercise.stableKey,
                    pair.first
                )
                val initialExposure = tissueRcvInitialExposure(
                    magnitude = magnitude,
                    normalizedDose = normalizedDose,
                    effortValue = effortValue,
                    resolvedContextModifier = contextResolution.factor
                )
                events += TissueExposureEvent(
                    eventId = "${record.entry.id}|${pair.first}|${pair.second}",
                    recordId = record.entry.id,
                    exerciseStableKey = record.exercise.stableKey,
                    exerciseName = record.exercise.name,
                    key = TissueRcvLoadKey(pair.first, pair.second),
                    jointComplexStableKey = loadUnit.jointComplexStableKey,
                    tissueClass = loadUnit.tissueClass,
                    initialExposure = initialExposure,
                    rawDose = rawDose,
                    doseReference56d = reference,
                    normalizedDose = normalizedDose,
                    selectedEffort = effort,
                    magnitudeM = magnitude,
                    rapidityS = group.maxOf(TissueRcvAuthorityRow::rapidityS),
                    contextModifier = contextResolution.factor,
                    mappingRoleWeight = 1.0,
                    curveIds = curves,
                    performedTime = TissuePerformedTimeResolver.resolve(record.entry, zoneId),
                    scoreVersion = (
                        group.map(TissueRcvAuthorityRow::scoreVersion) + TISSUE_RCV_CALCULATION_VERSION
                        ).distinct().sorted().joinToString("|"),
                    protocolVersion = "RCV-ALL-0.6",
                    curveVersion = "RCV-ALL-0.6",
                    evidenceGrade = group.map(TissueRcvAuthorityRow::mappingConfidence).filter(String::isNotBlank)
                        .distinct().sorted().joinToString("|"),
                    sourceRefs = group.flatMap(TissueRcvAuthorityRow::sourceRefs).distinct().sorted(),
                    diagnostics = listOf(
                        "GROUP/component conflicts use max M/S/C without summing duplicate authority rows.",
                        "Execution laterality is diagnostic only; the event key is unsided.",
                        contextResolution.diagnosticReason
                    ),
                    contextModifierRuleId = contextResolution.modifierRuleId,
                    contextModifierStatus = contextResolution.status,
                    contextPolicyVersion = contextResolution.policyVersion
                )
            }
        }
        return TissueEventLedgerResult(events.sortedBy(TissueExposureEvent::eventId), diagnostics.distinct().sorted())
    }

    private fun robustReference(
        records: List<TissueWorkoutRecord>,
        current: TissueWorkoutRecord,
        basis: String,
        resolver: (TissueWorkoutRecord, String) -> TissueDoseResolution
    ): Double {
        val values = records.asSequence()
            .filter { it.exercise.stableKey == current.exercise.stableKey }
            .filter { !it.date.isAfter(current.date) && !it.date.isBefore(current.date.minusDays(55)) }
            .mapNotNull { resolver(it, basis).resolvedDose }
            .filter { it > 0.0 && it.isFinite() }
            .sorted()
            .toList()
        if (values.isEmpty()) return 1.0
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2.0
    }

    private fun normalize(rawDose: Double, reference: Double): Double {
        if (rawDose <= 0.0) return 0.0
        val ratio = rawDose / reference.coerceAtLeast(1e-9)
        return (ln(1.0 + ratio) / ln(2.0)).coerceIn(0.0, 2.5)
    }

    private fun curves(
        protocol: TissueRcvExerciseProtocol,
        routing: TissueRecoveryRouting,
        bodyRegion: String
    ): Map<TissueRecoveryChannel, String> = buildMap {
        if ("FUNCTIONAL_CAPACITY" in routing.channels) {
            put(TissueRecoveryChannel.FUNCTIONAL_CAPACITY, protocol.functionalCurveId)
        }
        if ("JOINT_PROTECTION_FUNCTION" in routing.channels) {
            put(TissueRecoveryChannel.JOINT_PROTECTION_FUNCTION, protocol.jointProtectionCurveId)
        }
        if ("FAST_MECHANICAL_STATE" in routing.channels) {
            val routed = routing.fastCurveId ?: protocol.fastMechanicalCurveId
            put(
                TissueRecoveryChannel.FAST_MECHANICAL_STATE,
                if (routed == "REGION_DEPENDENT_CARTILAGE") {
                    if (bodyRegion == "LOWER") "RCV_CARTILAGE_FAST_LOWER" else "RCV_CARTILAGE_UPPER_PROXY"
                } else {
                    routed
                }
            )
        }
        routing.biologicalCurveId?.let {
            put(TissueRecoveryChannel.BIOLOGICAL_REMODELING_ACTIVITY, it)
        }
    }
}

internal fun tissueRcvInitialExposure(
    magnitude: Double,
    normalizedDose: Double,
    effortValue: Double,
    resolvedContextModifier: Double
): Double {
    val baseExposure = (magnitude / 10.0) * normalizedDose * effortValue
    return baseExposure * resolvedContextModifier
}
