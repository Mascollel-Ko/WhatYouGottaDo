package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** The deliberately narrow first production-cutover boundary. */
enum class StimulusProductionCutoverScope {
    STRENGTH_V1
}

enum class StimulusProductionCutoverAuthorityStatus {
    AUTHORIZED_FOR_BOUNDED_CUTOVER,
    CONTROL_REQUIRED,
    INCONCLUSIVE,
    NO_MATERIAL_CHANGE
}

data class StimulusProductionCutoverAuthorityDecision(
    val status: StimulusProductionCutoverAuthorityStatus,
    val scope: StimulusProductionCutoverScope,
    val authorizedOwnerIdentities: List<StimulusPrescriptionOwnerIdentity>,
    val reasonCodes: List<String>,
    val b7Status: StimulusExperimentalReadinessStatus,
    val routingActive: Boolean = false,
    val productionMutationAuthority: Boolean = false
) {
    init {
        require(!routingActive) { "B8.0 must never activate routing" }
        require(!productionMutationAuthority) { "B8.0 must never mutate production" }
    }
}

/** Result of the internal B8 evaluation. The experimental object is already built by B6.2. */
data class StimulusProductionCutoverEvaluation(
    val comparison: StimulusSelectionProgramComparison,
    val cutoverAuthority: StimulusProductionCutoverAuthorityDecision
) {
    val decision: StimulusProductionCutoverAuthorityDecision get() = cutoverAuthority
}

/**
 * B8.0 is a pure, fail-closed consumer of the existing B6.2/B7 comparison. It does not have
 * access to a builder, DAO, snapshot or request, so it cannot rerun any upstream phase.
 */
class StimulusProductionCutoverAuthorityAuditEngine {
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusProductionCutoverAuthorityDecision {
        val b7 = comparison.experimentalReadinessAudit
            ?: return control(comparison, "B8_B7_AUDIT_MISSING")

        when (b7.status) {
            StimulusExperimentalReadinessStatus.NOT_ELIGIBLE ->
                return control(comparison, "B8_B7_NOT_ELIGIBLE")
            StimulusExperimentalReadinessStatus.INCONCLUSIVE ->
                return inconclusive(comparison, "B8_B7_INCONCLUSIVE")
            StimulusExperimentalReadinessStatus.NO_MATERIAL_CHANGE ->
                return noMaterialChange(b7.status)
            StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW -> Unit
        }

        val reasons = linkedSetOf<String>()
        if (!b7.changeProvenanceClosed) reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        if (!b7.collateralRegressionFree) reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        if (b7.changeAttributions.any {
                it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                    it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
            }) {
            reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        if (b7.targetOutcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }) {
            reasons += "B8_CUTOVER_V1_COLLATERAL_REGRESSION"
        }
        if (comparison.removedOwnerIdentities.isNotEmpty()) {
            reasons += "B8_CUTOVER_V1_CONTROL_OWNER_REMOVAL_NOT_ALLOWED"
        }
        if (comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule) {
            reasons += "B8_CUTOVER_V1_WEEKDAY_SCHEDULE_CHANGED"
        }
        reasons += b6IntegrityReasons(comparison)

        val materialOwners = materialOwnerIdentities(comparison)
        if (materialOwners.isEmpty()) {
            reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        if (materialOwners.isNotEmpty()) {
            materialOwners.forEach { identity ->
                val authorization = exactAuthorization(comparison, identity)
                val targetId = authorization?.targetId
                    ?: if (identity in comparison.addedOwnerIdentities) "QUALITY:${TrainableQuality.STRENGTH.name}" else null
                strengthTargetAuthorityReason(comparison, targetId)?.let(reasons::add)
            }
            val nonStrength = materialOwners.flatMap { identity ->
                materialAttributionsFor(comparison, identity).filter(::isMaterialAttribution).flatMap { attribution ->
                    attribution.targetIds.filterNot(::isStrengthTarget)
                }
            }.distinct().sorted()
            if (nonStrength.isNotEmpty()) {
                reasons += "B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE"
            }
        }

        val authorized = linkedSetOf<StimulusPrescriptionOwnerIdentity>()
        materialOwners.sortedWith(OWNER_ORDER).forEach { identity ->
            when {
                identity in comparison.addedOwnerIdentities -> {
                    val validation = validateAddedOwner(comparison, identity)
                    reasons += validation
                    if (validation.isEmpty()) authorized += identity
                }
                identity in comparison.sharedOwnerIdentities -> {
                    val validation = validateSharedOwner(comparison, identity)
                    reasons += validation
                    if (validation.isEmpty()) authorized += identity
                }
            }
        }

        if (authorized.isEmpty()) {
            reasons += "B8_CUTOVER_V1_EMPTY_MATERIAL_AUTHORITY"
        }

        val parityFailures = unrelatedControlParityFailures(comparison, authorized)
        if (parityFailures) reasons += "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"

        // A material owner is authorized only after every independent gate passes. Do not
        // expose a partial list when the decision is CONTROL_REQUIRED or INCONCLUSIVE.
        val normalizedReasons = reasons.toList().sorted()
        return if (normalizedReasons.isEmpty()) {
            StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = StimulusProductionCutoverScope.STRENGTH_V1,
                authorizedOwnerIdentities = authorized.toList().sortedWith(OWNER_ORDER),
                reasonCodes = listOf("B8_STRENGTH_V1_AUTHORIZED"),
                b7Status = b7.status
            )
        } else {
            control(comparison, normalizedReasons)
        }
    }

    private fun validateAddedOwner(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<String> {
        val reasons = linkedSetOf<String>()
        val candidate = comparison.selectionPlan.selectedCandidates.firstOrNull {
            it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }
        if (candidate == null) reasons += "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"

        val strengthTargetId = "QUALITY:${TrainableQuality.STRENGTH.name}"
        val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == strengthTargetId }
        val candidateTargets = candidate?.coveredTargetIds.orEmpty()
        val selectedTraceTargets = comparison.selectionPlan.traces
            .filter { trace -> trace.selectedStableKey == identity.stableKey && trace.selectedSelectionRole == identity.selectionRole }
            .map { it.targetId }
        if (target == null || strengthTargetId !in candidateTargets || strengthTargetId !in selectedTraceTargets && selectedTraceTargets.isNotEmpty()) {
            reasons += "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"
        }
        strengthTargetAuthorityReason(comparison, strengthTargetId)?.let(reasons::add)

        val attribution = materialAttributionsFor(comparison, identity)
            .firstOrNull { it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY }
        if (attribution == null || attribution.targetIds.any { !isStrengthTarget(it) }) {
            reasons += "B8_CUTOVER_V1_ADDED_OWNER_WITHOUT_EXACT_B5_AUTHORITY"
        }

        val auth = exactAuthorization(comparison, identity)
        if (!isExecutableStrengthAuthorization(auth)) {
            reasons += "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY"
        }
        strengthTargetAuthorityReason(comparison, auth?.targetId)?.let(reasons::add)
        if (!fullMaterialization(comparison, identity)) {
            reasons += "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION"
        }
        return reasons.toList()
    }

    private fun validateSharedOwner(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<String> {
        val before = ownerRows(comparison.control, identity)
        val after = ownerRows(comparison.experimental, identity)
        if (before == after) return emptyList()
        val reasons = linkedSetOf<String>()
        if (placementSignature(before) != placementSignature(after)) {
            reasons += "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION"
        }
        val auth = exactAuthorization(comparison, identity)
        if (!isExecutableStrengthAuthorization(auth)) {
            reasons += "B8_CUTOVER_V1_PRESCRIPTION_CHANGE_WITHOUT_EXACT_B6_AUTHORITY"
        }
        if (auth?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE) {
            reasons += "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        val attributions = materialAttributionsFor(comparison, identity)
        if (attributions.none {
                it.source == StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION ||
                    it.source == StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
            }) {
            reasons += "B8_CUTOVER_V1_PROVENANCE_NOT_CLOSED"
        }
        if (attributions.filter(::isMaterialAttribution).any { attribution ->
            attribution.targetIds.any { !isStrengthTarget(it) }
        }) {
            reasons += "B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE"
        }
        if (!fullMaterialization(comparison, identity)) {
            reasons += "B8_CUTOVER_V1_REQUIRES_FULL_B6_MATERIALIZATION"
        }
        return reasons.toList()
    }

    private fun exactAuthorization(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): StimulusPrescriptionAuthorization? {
        val matches = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter {
            val owner = it.owner ?: return@filter false
            owner.stableKey == identity.stableKey && owner.selectionRole == identity.selectionRole
        }
        return matches.singleOrNull()
    }

    private fun isExecutableStrengthAuthorization(authorization: StimulusPrescriptionAuthorization?): Boolean =
        authorization != null && authorization.quality == TrainableQuality.STRENGTH &&
            authorization.authorizedPrescription != null && authorization.status in setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
        )

    private fun strengthTargetAuthorityReason(
        comparison: StimulusSelectionProgramComparison,
        targetId: String?
    ): String? {
        if (targetId == null) return "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        val target = comparison.targetPlan.qualityTargets.firstOrNull {
            "QUALITY:${it.quality.name}" == targetId
        }
        if (target == null || target.strategy == StimulusDoseStrategy.UNRESOLVED || targetId in comparison.targetPlan.unresolved) {
            return "B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY"
        }
        if (target.quality != TrainableQuality.STRENGTH ||
            target.numericAuthority in setOf(
                StimulusTargetNumericAuthority.NONE,
                StimulusTargetNumericAuthority.UNRESOLVED
            )
        ) {
            return "B8_CUTOVER_V1_STRENGTH_TARGET_HAS_NO_NUMERIC_AUTHORITY"
        }
        return null
    }

    private fun fullMaterialization(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): Boolean {
        val audits = comparison.prescriptionMaterializationAudits.filter {
            val owner = it.owner ?: return@filter false
            owner.stableKey == identity.stableKey && owner.selectionRole == identity.selectionRole
        }
        if (audits.size != 1) return false
        val audit = audits.single()
        val expectedWeeks = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
        if (audit.state != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ||
            audit.weeklyAudits.size != expectedWeeks ||
            audit.weeklyAudits.map { it.weekNumber }.toSet() != (1..expectedWeeks).toSet()) return false
        return audit.weeklyAudits.all { week ->
            week.shortfall == 0 && week.overrun == 0 &&
                week.prescriptionPreservedOrSubset &&
                week.targetCompatibleMaterializedUnits == week.materializedSetUnits
        } && audit.shortfall == 0 && audit.overrun == 0 && audit.prescriptionPreservedOrSubset
    }

    private fun b6IntegrityReasons(comparison: StimulusSelectionProgramComparison): Set<String> = buildSet {
        val known = setOf(
            "B6_AUTHORIZED_SET_REUSED",
            "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED",
            "B6_UNAUTHORIZED_SET_CONTENT",
            "B6_UNAUTHORIZED_SET_IDENTITY",
            "B6_AUTHORIZATION_OVERRUN",
            "B6_AUTHORIZATION_SHORTFALL",
            "B6_AUTHORIZATION_MISSING_WEEK",
            "B6_PRESCRIPTION_NOT_PRESERVED",
            "B6_PRESCRIPTION_MUTATION",
            "B6_PRESCRIPTION_AUTHORITY_MISMATCH",
            "B6_MATERIALIZATION_INVARIANT_FAILURE"
        )
        comparison.prescriptionMaterializationAudits.forEach { audit ->
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE) add("B6_MATERIALIZATION_INVARIANT_FAILURE")
            addAll(audit.reasonCodes.filter { it in known })
            addAll(audit.weeklyAudits.flatMap { it.reasonCodes }.filter { it in known })
            if (audit.overrun > 0 || audit.maximumWeeklyOverrun > 0) add("B6_AUTHORIZATION_OVERRUN")
            if (audit.shortfall > 0 || audit.maximumWeeklyShortfall > 0) add("B6_AUTHORIZATION_SHORTFALL")
            if (!audit.prescriptionPreservedOrSubset || audit.weeklyAudits.any { !it.prescriptionPreservedOrSubset }) {
                add("B6_PRESCRIPTION_NOT_PRESERVED")
            }
        }
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            addAll(authorization.reasonCodes.filter { it in known })
        }
    }

    private fun materialOwnerIdentities(comparison: StimulusSelectionProgramComparison): Set<StimulusPrescriptionOwnerIdentity> = buildSet {
        addAll(comparison.addedOwnerIdentities)
        comparison.sharedOwnerIdentities.forEach { identity ->
            if (ownerRows(comparison.control, identity) != ownerRows(comparison.experimental, identity)) add(identity)
        }
    }

    private fun materialAttributionsFor(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<StimulusExperimentalChangeAttribution> = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().filter {
        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
    }

    private fun isMaterialAttribution(attribution: StimulusExperimentalChangeAttribution): Boolean =
        attribution.source in setOf(
            StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
            StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
        )

    private fun unrelatedControlParityFailures(
        comparison: StimulusSelectionProgramComparison,
        authorized: Set<StimulusPrescriptionOwnerIdentity>
    ): Boolean = comparison.controlOwnerIdentities.any { identity ->
        identity !in authorized && ownerRows(comparison.control, identity) != ownerRows(comparison.experimental, identity)
    }

    private data class OwnerRow(
        val weekNumber: Int,
        val dayOfWeek: Int,
        val orderIndex: Int,
        val stableKey: String,
        val selectionRole: String,
        val setPrescriptions: List<com.training.trackplanner.data.ProgramSetPrescription>,
        val restSeconds: Int,
        val weightSource: String,
        val prescription: String,
        val setCount: Int,
        val reps: Int,
        val weightKg: Double,
        val seconds: Int
    )

    private fun ownerRows(
        program: com.training.trackplanner.data.GeneratedProgramSkeleton,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<OwnerRow> = program.items.filter {
        it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
    }.map(::ownerRow).sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.stableKey }, { it.selectionRole }))

    private fun ownerRow(item: ProgramSkeletonItem) = OwnerRow(
        weekNumber = item.weekNumber,
        dayOfWeek = item.dayOfWeek,
        orderIndex = item.orderIndex,
        stableKey = item.exerciseStableKey,
        selectionRole = item.selectionRole,
        setPrescriptions = item.setPrescriptions,
        restSeconds = item.restSeconds,
        weightSource = item.weightSource,
        prescription = item.prescription,
        setCount = item.setCount,
        reps = item.reps,
        weightKg = item.weightKg,
        seconds = item.seconds
    )

    private fun placementSignature(rows: List<OwnerRow>) = rows.map { listOf(it.weekNumber, it.dayOfWeek, it.orderIndex, it.stableKey, it.selectionRole) }

    private fun isStrengthTarget(targetId: String): Boolean = targetId == "QUALITY:${TrainableQuality.STRENGTH.name}"

    private fun control(comparison: StimulusSelectionProgramComparison, reason: String) =
        control(comparison, listOf(reason))

    private fun control(comparison: StimulusSelectionProgramComparison, reasons: List<String>) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
            scope = StimulusProductionCutoverScope.STRENGTH_V1,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = reasons.distinct().sorted(),
            b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
        )

    private fun inconclusive(comparison: StimulusSelectionProgramComparison, reason: String) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.INCONCLUSIVE,
            scope = StimulusProductionCutoverScope.STRENGTH_V1,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf(reason),
            b7Status = comparison.experimentalReadinessAudit?.status ?: StimulusExperimentalReadinessStatus.INCONCLUSIVE
        )

    private fun noMaterialChange(b7Status: StimulusExperimentalReadinessStatus) =
        StimulusProductionCutoverAuthorityDecision(
            status = StimulusProductionCutoverAuthorityStatus.NO_MATERIAL_CHANGE,
            scope = StimulusProductionCutoverScope.STRENGTH_V1,
            authorizedOwnerIdentities = emptyList(),
            reasonCodes = listOf("B8_NO_MATERIAL_CHANGE"),
            b7Status = b7Status
        )

    private companion object {
        val OWNER_ORDER = compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })
    }
}

/** Compatibility spelling for callers that prefer the shorter engine name. */
typealias StimulusProductionCutoverAuthorityEngine = StimulusProductionCutoverAuthorityAuditEngine

internal fun StimulusProductionCutoverAuthorityDecision.toJson(): JSONObject = JSONObject()
    .put("status", status.name)
    .put("scope", scope.name)
    .put("authorizedOwnerIdentities", JSONArray(authorizedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    }))
    .put("reasonCodes", JSONArray(reasonCodes.distinct().sorted()))
    .put("b7Status", b7Status.name)
    .put("routingActive", routingActive)
    .put("productionMutationAuthority", productionMutationAuthority)

internal fun StimulusProductionCutoverEvaluation.toCompactJson(): JSONObject = JSONObject()
    .put("comparison", comparison.toCompactJson())
    .put("cutoverAuthority", cutoverAuthority.toJson())
