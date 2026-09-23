package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** B7 is an observation-only cutover gate; it never grants production authority. */
enum class StimulusExperimentalReadinessStatus {
    ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
    NOT_ELIGIBLE,
    INCONCLUSIVE,
    NO_MATERIAL_CHANGE
}

enum class StimulusExperimentalTargetOutcomeStatus {
    IMPROVED,
    UNCHANGED,
    REGRESSED,
    INCONCLUSIVE,
    NOT_APPLICABLE,
    NO_AUTHORITY
}

enum class StimulusExperimentalChangeAttributionSource {
    B5_SELECTED_IDENTITY,
    B5_REUSED_IDENTITY,
    B6_EXISTING_OWNER_PRESCRIPTION,
    B6_SAFE_REPAIRED_PRESCRIPTION,
    DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
    UNEXPLAINED
}

data class StimulusExperimentalTargetOutcome(
    val targetId: String,
    val status: StimulusExperimentalTargetOutcomeStatus,
    val directlyAffected: Boolean,
    val controlWeeklyUnitsDistance: Double? = null,
    val experimentalWeeklyUnitsDistance: Double? = null,
    val controlWeeklySessionsDistance: Double? = null,
    val experimentalWeeklySessionsDistance: Double? = null,
    val reasonCodes: List<String> = emptyList()
)

data class StimulusExperimentalChangeAttribution(
    val stableKey: String?,
    val selectionRole: String?,
    val source: StimulusExperimentalChangeAttributionSource,
    val targetIds: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList()
)

data class StimulusExperimentalReadinessAudit(
    val status: StimulusExperimentalReadinessStatus,
    val targetOutcomes: List<StimulusExperimentalTargetOutcome> = emptyList(),
    val changeAttributions: List<StimulusExperimentalChangeAttribution> = emptyList(),
    val materializationIntegrityPassed: Boolean = true,
    val changeProvenanceClosed: Boolean = true,
    val collateralRegressionFree: Boolean = true,
    val reasonCodes: List<String> = emptyList(),
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
) {
    val integrityPassed: Boolean get() = materializationIntegrityPassed
    val winner: String? get() = null
}

/**
 * Adjudicates the already-built B6.2 comparison. This class deliberately accepts no builder,
 * DAO, snapshot or request and therefore cannot trigger a third build or rerun an earlier phase.
 */
class StimulusExperimentalReadinessAuditEngine {
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusExperimentalReadinessAudit {
        if (comparison.winner != null) {
            return failed(comparison, "B5_WINNER_MUST_REMAIN_NULL")
        }
        if (noMaterialChange(comparison)) {
            return StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE,
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true,
                reasonCodes = listOf("NO_MATERIAL_CHANGE"),
                shadowOnly = true,
                productionAuthority = false
            )
        }

        val integrityReasons = linkedSetOf<String>()
        comparison.prescriptionMaterializationAudits.forEach { audit ->
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE) {
                integrityReasons += "B6_MATERIALIZATION_INVARIANT_FAILURE"
            }
            if (audit.overrun > 0 || audit.maximumWeeklyOverrun > 0) integrityReasons += "B6_AUTHORIZATION_OVERRUN"
            if (!audit.prescriptionPreservedOrSubset) integrityReasons += "B6_PRESCRIPTION_MUTATION"
            if (audit.weeklyAudits.any { !it.prescriptionPreservedOrSubset }) integrityReasons += "B6_PRESCRIPTION_MUTATION"
        }
        val attributions = attributeChanges(comparison)
        val provenanceClosed = attributions.none { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED }
        val affectedTargets = affectedTargetIds(comparison, attributions)
        val outcomes = targetOutcomes(comparison, affectedTargets)
        val collateralRegressionFree = outcomes.none { !it.directlyAffected && it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
        val reasons = linkedSetOf<String>().apply {
            addAll(integrityReasons)
            if (!provenanceClosed) add("CHANGE_PROVENANCE_UNCLOSED")
            if (!collateralRegressionFree) add("COLLATERAL_TARGET_REGRESSION")
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY && it.directlyAffected }) {
                add("AFFECTED_TARGET_HAS_NO_AUTHORITY")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE && it.directlyAffected }) {
                add("AFFECTED_TARGET_EVIDENCE_INCONCLUSIVE")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
                add("TARGET_REGRESSED")
            }
            if (outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && it.directlyAffected && it.reasonCodes.contains("TARGET_UNMET") }) {
                add("AFFECTED_TARGET_REMAINS_UNMET")
            }
        }
        val status = when {
            integrityReasons.isNotEmpty() -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            !provenanceClosed -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            !collateralRegressionFree -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY && it.directlyAffected } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && it.directlyAffected && it.reasonCodes.contains("TARGET_UNMET") } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE && it.directlyAffected } -> StimulusExperimentalReadinessStatus.INCONCLUSIVE
            else -> StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
        }
        return StimulusExperimentalReadinessAudit(
            status = status,
            targetOutcomes = outcomes,
            changeAttributions = attributions,
            materializationIntegrityPassed = integrityReasons.isEmpty(),
            changeProvenanceClosed = provenanceClosed,
            collateralRegressionFree = collateralRegressionFree,
            reasonCodes = reasons.toList(),
            shadowOnly = true,
            productionAuthority = false
        )
    }

    private fun noMaterialChange(comparison: StimulusSelectionProgramComparison): Boolean =
        comparison.control.items == comparison.experimental.items &&
            comparison.control.weekPlans == comparison.experimental.weekPlans &&
            comparison.control.weekDaySchedule == comparison.experimental.weekDaySchedule &&
            comparison.differences.isEmpty() && comparison.addedStableKeys.isEmpty() && comparison.removedStableKeys.isEmpty()

    private fun failed(comparison: StimulusSelectionProgramComparison, reason: String) =
        StimulusExperimentalReadinessAudit(
            status = StimulusExperimentalReadinessStatus.NOT_ELIGIBLE,
            targetOutcomes = targetOutcomes(comparison, emptySet()),
            changeAttributions = listOf(StimulusExperimentalChangeAttribution(null, null, StimulusExperimentalChangeAttributionSource.UNEXPLAINED, reasonCodes = listOf(reason))),
            materializationIntegrityPassed = false,
            changeProvenanceClosed = false,
            collateralRegressionFree = false,
            reasonCodes = listOf(reason),
            shadowOnly = true,
            productionAuthority = false
        )

    private fun affectedTargetIds(
        comparison: StimulusSelectionProgramComparison,
        attributions: List<StimulusExperimentalChangeAttribution>
    ): Set<String> {
        val selectedTargets = comparison.selectionPlan.selectedCandidates
            .filter { it.stableKey in comparison.addedStableKeys || it.stableKey in comparison.sharedStableKeys }
            .flatMap { it.coveredTargetIds }
        val attributed = attributions.flatMap { it.targetIds }
        val fromTraces = comparison.materializationTraces
            .filter {
                it.selectedStableKey?.let { key -> key in comparison.addedStableKeys || key in comparison.sharedStableKeys } == true
            }
            .map { it.targetId }
        val result = (selectedTargets + attributed + fromTraces).toSet()
        if (result.isNotEmpty()) return result
        val all = comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" } +
            comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }
        return if (all.size == 1) all.toSet() else emptySet()
    }

    private fun attributeChanges(comparison: StimulusSelectionProgramComparison): List<StimulusExperimentalChangeAttribution> {
        val result = mutableListOf<StimulusExperimentalChangeAttribution>()
        val selected = comparison.selectionPlan.selectedCandidates.associateBy { it.stableKey }
        comparison.addedStableKeys.sorted().forEach { key ->
            val candidate = selected[key]
            result += if (candidate != null) {
                StimulusExperimentalChangeAttribution(
                    stableKey = key,
                    selectionRole = candidate.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_SELECTED_IDENTITY_ADDED")
                )
            } else {
                StimulusExperimentalChangeAttribution(key, null, StimulusExperimentalChangeAttributionSource.UNEXPLAINED, reasonCodes = listOf("UNEXPLAINED_ADDED_IDENTITY"))
            }
        }
        comparison.selectionPlan.selectedCandidates
            .filter { it.stableKey in comparison.sharedStableKeys }
            .sortedBy { it.stableKey }
            .forEach { candidate ->
                result += StimulusExperimentalChangeAttribution(
                    stableKey = candidate.stableKey,
                    selectionRole = candidate.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_REUSED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_REUSED_IDENTITY")
                )
            }
        comparison.removedStableKeys.sorted().forEach { key ->
            val traces = comparison.materializationTraces.filter { it.selectedStableKey == key }
            result += if (comparison.differences.any { it.controlStableKey == key }) {
                StimulusExperimentalChangeAttribution(key, null, StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
                    traces.map { it.targetId }.distinct().sorted(), listOf("REMOVED_IDENTITY_ATTRIBUTED_TO_DOWNSTREAM_CONSTRAINT"))
            } else {
                StimulusExperimentalChangeAttribution(key, null, StimulusExperimentalChangeAttributionSource.UNEXPLAINED, reasonCodes = listOf("UNEXPLAINED_REMOVED_IDENTITY"))
            }
        }
        val authByOwner = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .filter { it.owner != null }.associateBy { StimulusPrescriptionOwnerIdentity(it.owner!!.stableKey, it.owner!!.selectionRole) }
        val controlByIdentity = comparison.control.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        val experimentalByIdentity = comparison.experimental.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        (controlByIdentity.keys intersect experimentalByIdentity.keys).sortedWith(compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val before = controlByIdentity.getValue(identity).map(::prescription)
            val after = experimentalByIdentity.getValue(identity).map(::prescription)
            if (before == after) return@forEach
            val auth = authByOwner[identity]
            val authorized = auth?.authorizedPrescription
            val source = when {
                auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR && authorized != null && after.all { it.isPrefixOf(authorized) } -> StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
                auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE && authorized != null && after.all { it.isPrefixOf(authorized) } -> StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION
                else -> StimulusExperimentalChangeAttributionSource.UNEXPLAINED
            }
            result += StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole, source,
                auth?.targetId?.let(::listOf).orEmpty(), if (source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED) listOf("UNEXPLAINED_PRESCRIPTION_CHANGE") else listOf("B6_AUTHORIZED_PRESCRIPTION_CHANGE"))
        }
        return result
    }

    private fun targetOutcomes(comparison: StimulusSelectionProgramComparison, affected: Set<String>): List<StimulusExperimentalTargetOutcome> =
        comparison.targetPlan.qualityTargets.map { target ->
            val id = "QUALITY:${target.quality.name}"
            val control = comparison.controlAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            val experimental = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            numericOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control?.plannedWeeklyDirectUnits, experimental?.plannedWeeklyDirectUnits, control?.plannedWeeklyDirectSessions, experimental?.plannedWeeklyDirectSessions,
                control?.weeklyDirectUnitsStatus, experimental?.weeklyDirectUnitsStatus, control?.weeklyDirectSessionsStatus, experimental?.weeklyDirectSessionsStatus)
        } + comparison.targetPlan.taskTargets.map { target ->
            val id = "TASK:${target.task}"
            val control = comparison.controlAudit?.taskAudits?.firstOrNull { it.task == target.task }
            val experimental = comparison.experimentalAudit?.taskAudits?.firstOrNull { it.task == target.task }
            taskOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control, experimental)
        }

    private fun numericOutcome(
        id: String,
        affected: Boolean,
        authority: StimulusTargetNumericAuthority,
        unitsRange: StimulusTargetRange?,
        sessionsRange: StimulusTargetRange?,
        controlUnits: Double?,
        experimentalUnits: Double?,
        controlSessions: Double?,
        experimentalSessions: Double?,
        controlUnitsStatus: StimulusTargetControlStatus?,
        experimentalUnitsStatus: StimulusTargetControlStatus?,
        controlSessionsStatus: StimulusTargetControlStatus?,
        experimentalSessionsStatus: StimulusTargetControlStatus?
    ): StimulusExperimentalTargetOutcome {
        if (authority == StimulusTargetNumericAuthority.NONE) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = if (affected) listOf("NO_AUTHORITY_CHANGE") else emptyList())
        if (authority == StimulusTargetNumericAuthority.DIRECTION_ONLY) {
            return directionOutcome(id, affected, controlUnitsStatus, experimentalUnitsStatus, controlSessionsStatus, experimentalSessionsStatus)
        }
        if (authority == StimulusTargetNumericAuthority.UNRESOLVED || unitsRange == null || sessionsRange == null || controlUnits == null || experimentalUnits == null || controlSessions == null || experimentalSessions == null) {
            return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_NUMERIC_EVIDENCE_UNRESOLVED"))
        }
        val cu = distance(unitsRange, controlUnits)
        val eu = distance(unitsRange, experimentalUnits)
        val cs = distance(sessionsRange, controlSessions)
        val es = distance(sessionsRange, experimentalSessions)
        val relation = compareDistances(cu, eu, cs, es)
        val reasons = mutableListOf<String>()
        if (relation == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && (eu > 0.0 || es > 0.0)) reasons += "TARGET_UNMET"
        return StimulusExperimentalTargetOutcome(id, relation, affected, cu, eu, cs, es, reasons)
    }

    private fun taskOutcome(
        id: String,
        affected: Boolean,
        authority: StimulusTargetNumericAuthority,
        unitsRange: StimulusTargetRange?,
        sessionsRange: StimulusTargetRange?,
        control: StimulusTaskControlProgramAudit?,
        experimental: StimulusTaskControlProgramAudit?
    ): StimulusExperimentalTargetOutcome {
        if (authority == StimulusTargetNumericAuthority.NONE) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = if (affected) listOf("NO_AUTHORITY_CHANGE") else emptyList())
        if (authority == StimulusTargetNumericAuthority.UNRESOLVED || control == null || experimental == null) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_EVIDENCE_UNRESOLVED"))
        if (authority == StimulusTargetNumericAuthority.DIRECTION_ONLY || unitsRange == null) {
            val status = directionStatus(control.status, experimental.status)
            return StimulusExperimentalTargetOutcome(id, status, affected, reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && experimental.status != StimulusTargetControlStatus.DIRECT_PRESENT) listOf("TARGET_UNMET") else emptyList())
        }
        val cu = control.plannedDirectUnits?.let { distance(unitsRange, it) }
        val eu = experimental.plannedDirectUnits?.let { distance(unitsRange, it) }
        if (cu == null || eu == null) return StimulusExperimentalTargetOutcome(id, if (affected) StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, affected, reasonCodes = listOf("TARGET_NUMERIC_EVIDENCE_UNRESOLVED"))
        val status = compareDistances(cu, eu, null, null)
        return StimulusExperimentalTargetOutcome(id, status, affected, cu, eu, reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && eu > 0.0) listOf("TARGET_UNMET") else emptyList())
    }

    private fun directionOutcome(id: String, affected: Boolean, cu: StimulusTargetControlStatus?, eu: StimulusTargetControlStatus?, cs: StimulusTargetControlStatus?, es: StimulusTargetControlStatus?) =
        StimulusExperimentalTargetOutcome(id, directionStatus(if (cu == StimulusTargetControlStatus.DIRECT_PRESENT || cs == StimulusTargetControlStatus.DIRECT_PRESENT) StimulusTargetControlStatus.DIRECT_PRESENT else cu, if (eu == StimulusTargetControlStatus.DIRECT_PRESENT || es == StimulusTargetControlStatus.DIRECT_PRESENT) StimulusTargetControlStatus.DIRECT_PRESENT else eu), affected,
            reasonCodes = if (eu == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED || es == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED) listOf("DISTRIBUTION_COMPARISON_DEFERRED") else emptyList())

    private fun directionStatus(control: StimulusTargetControlStatus?, experimental: StimulusTargetControlStatus?): StimulusExperimentalTargetOutcomeStatus = when {
        control == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED || experimental == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED -> StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE
        control == StimulusTargetControlStatus.DIRECT_ABSENT && experimental == StimulusTargetControlStatus.DIRECT_PRESENT -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
        control == StimulusTargetControlStatus.DIRECT_PRESENT && experimental == StimulusTargetControlStatus.DIRECT_ABSENT -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
        else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
    }

    private fun compareDistances(cu: Double?, eu: Double?, cs: Double?, es: Double?): StimulusExperimentalTargetOutcomeStatus {
        if (cu == null || eu == null || ((cs == null) != (es == null))) return StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE
        val dimensions = buildList {
            add(compareDistance(cu, eu))
            if (cs != null && es != null) add(compareDistance(cs, es))
        }
        val improved = dimensions.count { it == StimulusExperimentalTargetOutcomeStatus.IMPROVED }
        val regressed = dimensions.count { it == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
        return when {
            improved > 0 && regressed > 0 -> StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE
            regressed > 0 -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
            improved > 0 -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
            else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
        }
    }

    private fun compareDistance(control: Double, experimental: Double): StimulusExperimentalTargetOutcomeStatus = when {
        experimental < control -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
        experimental > control -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
        else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
    }

    private fun distance(range: StimulusTargetRange, value: Double): Double = when {
        value < range.min -> range.min - value
        value > range.max -> value - range.max
        else -> 0.0
    }

    private fun prescription(item: ProgramSkeletonItem) = PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)

    private fun PlannedPrescription.isPrefixOf(authorized: PlannedPrescription): Boolean =
        text == authorized.text && restSeconds == authorized.restSeconds && weightSource == authorized.weightSource &&
            sets.size <= authorized.sets.size && sets == authorized.sets.take(sets.size).mapIndexed { index, set -> set.copy(setIndex = index + 1) }
}

internal fun StimulusExperimentalReadinessAudit.toJson(): JSONObject = JSONObject()
    .put("status", status.name)
    .put("materializationIntegrityPassed", materializationIntegrityPassed)
    .put("changeProvenanceClosed", changeProvenanceClosed)
    .put("collateralRegressionFree", collateralRegressionFree)
    .put("shadowOnly", shadowOnly)
    .put("productionAuthority", productionAuthority)
    .put("reasonCodes", JSONArray(reasonCodes))
    .put("targetOutcomes", JSONArray(targetOutcomes.map { outcome -> JSONObject()
        .put("targetId", outcome.targetId).put("status", outcome.status.name).put("directlyAffected", outcome.directlyAffected)
        .put("controlWeeklyUnitsDistance", outcome.controlWeeklyUnitsDistance).put("experimentalWeeklyUnitsDistance", outcome.experimentalWeeklyUnitsDistance)
        .put("controlWeeklySessionsDistance", outcome.controlWeeklySessionsDistance).put("experimentalWeeklySessionsDistance", outcome.experimentalWeeklySessionsDistance)
        .put("reasonCodes", JSONArray(outcome.reasonCodes))
    }))
    .put("changeAttributions", JSONArray(changeAttributions.map { attribution -> JSONObject()
        .put("stableKey", attribution.stableKey).put("selectionRole", attribution.selectionRole).put("source", attribution.source.name)
        .put("targetIds", JSONArray(attribution.targetIds)).put("reasonCodes", JSONArray(attribution.reasonCodes))
    }))
