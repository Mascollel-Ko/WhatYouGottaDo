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
    B6_COLD_START_USER_CALIBRATION,
    B6_APPROVED_TASK_PROTOCOL,
    DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
    INCONCLUSIVE_DISPLACEMENT,
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
    val reasonCodes: List<String> = emptyList(),
    /** Authorized movement-scoped H material is kept visible, but is not compared as an aggregate Quality H cap. */
    val regionalHypertrophyUnitsExcludedFromAggregateComparison: Double? = null
)

data class StimulusExperimentalChangeAttribution(
    val stableKey: String?,
    val selectionRole: String?,
    val source: StimulusExperimentalChangeAttributionSource,
    val targetIds: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
    val evidenceSources: List<String> = emptyList()
)

/**
 * Owner-local causal evidence for one removed CONTROL identity. Global B5/B6 change evidence
 * is intentionally separate from the capacity, placement and disappearance fields below.
 */
data class StimulusRemovalCausalEvidence(
    val stableKey: String,
    val selectionRole: String?,
    val governedExperimentalChangeExists: Boolean,
    val removedOwnerHasCapacityOrPlacementEvidence: Boolean,
    val removedOwnerHasDisappearanceEvidence: Boolean,
    val removedOwnerWasDirectB5Action: Boolean,
    val contradictoryProvenance: Boolean,
    val evidenceSources: List<String> = emptyList(),
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

private val B6_INTEGRITY_REASON_CODES = setOf(
    "B6_AUTHORIZED_SET_REUSED",
    "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED",
    "B6_UNAUTHORIZED_SET_CONTENT",
    "B6_UNAUTHORIZED_SET_IDENTITY",
    "B6_PRESCRIPTION_AUTHORITY_MISMATCH",
    "B6_PRESCRIPTION_NOT_PRESERVED",
    "B6_PRESCRIPTION_MUTATION",
    "B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED"
)

private data class QualityPrescriptionSignature(
    val prescription: String,
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val restSeconds: Int,
    val weightSource: String,
    val setPrescriptions: List<com.training.trackplanner.data.ProgramSetPrescription>
)

/**
 * Adjudicates the already-built B6.2 comparison. This class deliberately accepts no builder,
 * DAO, snapshot or request and therefore cannot trigger a third build or rerun an earlier phase.
 */
class StimulusExperimentalReadinessAuditEngine {
    fun audit(comparison: StimulusSelectionProgramComparison): StimulusExperimentalReadinessAudit {
        if (comparison.winner != null) {
            return failed(comparison, "B5_WINNER_MUST_REMAIN_NULL")
        }
        // B6 integrity is adjudicated before the semantic no-change shortcut. A malformed
        // experimental object can be byte-for-byte equal to CONTROL and must still fail closed.
        val integrityReasons = materializationIntegrityReasons(comparison)
        val experimentalRowsByIdentity = comparison.experimental.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            // A competing-quality owner is intentionally non-executable. Its retained authority
            // rows remain in diagnostics, but validating CONTROL-preserved rows against either
            // conflicting proposal would turn an owner-local disposition into global B6 failure.
            if (authorization.status == StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY) return@forEach
            val owner = authorization.owner ?: return@forEach
            val authorized = authorization.authorizedPrescription ?: return@forEach
            val validation = validateAuthorizedWeeklySubset(
                experimentalRowsByIdentity[StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)].orEmpty(),
                authorized, owner.stableKey, owner.selectionRole
            )
            if (!validation.valid) integrityReasons += validation.reasonCodes
        }
        if (integrityReasons.isEmpty() && noMaterialChange(comparison)) {
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

        val attributions = attributeChanges(comparison)
        val hasUnexplainedProvenance = attributions.any { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED }
        val hasInconclusiveProvenance = attributions.any { it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT }
        val provenanceClosed = !hasUnexplainedProvenance && !hasInconclusiveProvenance
        val affectedTargets = affectedTargetIds(comparison, attributions)
        val outcomes = targetOutcomes(comparison, affectedTargets)
        val collateralRegressionFree = outcomes.none { !it.directlyAffected && it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
        val reasons = linkedSetOf<String>().apply {
            addAll(integrityReasons)
            if (!provenanceClosed) add("CHANGE_PROVENANCE_UNCLOSED")
            if (hasInconclusiveProvenance) add("REMOVAL_CAUSALITY_UNPROVEN")
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
            hasUnexplainedProvenance -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            !collateralRegressionFree -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY && it.directlyAffected } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && it.directlyAffected && it.reasonCodes.contains("TARGET_UNMET") } -> StimulusExperimentalReadinessStatus.NOT_ELIGIBLE
            hasInconclusiveProvenance || outcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE && it.directlyAffected } -> StimulusExperimentalReadinessStatus.INCONCLUSIVE
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

    private fun materializationIntegrityReasons(
        comparison: StimulusSelectionProgramComparison
    ): LinkedHashSet<String> {
        val integrityReasons = linkedSetOf<String>()
        integrityReasons += rejectedQualityMaterializationReasons(comparison)
        comparison.prescriptionMaterializationAudits.forEach { audit ->
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE) {
                integrityReasons += "B6_MATERIALIZATION_INVARIANT_FAILURE"
            }
            integrityReasons += audit.reasonCodes.filter { it in B6_INTEGRITY_REASON_CODES }
            integrityReasons += audit.weeklyAudits.flatMap { it.reasonCodes }.filter { it in B6_INTEGRITY_REASON_CODES }
            if (audit.overrun > 0 || audit.maximumWeeklyOverrun > 0) integrityReasons += "B6_AUTHORIZATION_OVERRUN"
            if (!audit.prescriptionPreservedOrSubset) integrityReasons += "B6_PRESCRIPTION_MUTATION"
            if (audit.weeklyAudits.any { !it.prescriptionPreservedOrSubset }) integrityReasons += "B6_PRESCRIPTION_MUTATION"
        }
        val selectedTaskOwners = comparison.selectionPlan.selectedCandidates
            .filter { candidate -> candidate.coveredTargetIds.any { it.startsWith("TASK:") } }
            .associateBy { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val rowsByOwner = comparison.experimental.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }
        selectedTaskOwners.forEach { (owner, candidate) ->
            val ownerRows = rowsByOwner[owner].orEmpty()
            if (ownerRows.isNotEmpty() && ownerRows.any { it.taskProtocolSemanticsJson.isNullOrBlank() }) {
                integrityReasons += "B6_TASK_PROTOCOL_AUTHORITY_REQUIRED"
            }
        }
        comparison.experimental.items.filter { !it.taskProtocolSemanticsJson.isNullOrBlank() }.forEach { item ->
            val owner = StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole)
            val candidate = selectedTaskOwners[owner]
            val metadata = runCatching {
                TaskProtocolExposureMetadata.fromJsonString(requireNotNull(item.taskProtocolSemanticsJson))
            }.getOrNull()
            val b4Tasks = comparison.targetPlan.taskTargets.mapNotNullTo(linkedSetOf()) { target ->
                runCatching { CanonicalTaskTarget.valueOf(target.task) }.getOrNull()
            }
            val valid = candidate != null && metadata != null && metadata.matchesMaterializedItem(
                item = item,
                actualActivityKind = metadata.authorization.materializationActivityKind,
                currentB4Tasks = b4Tasks,
                selectedPrimaryTargetId = candidate.primaryTargetId
            )
            if (!valid) integrityReasons += "B6_TASK_PROTOCOL_MATERIALIZATION_FAILURE"
        }
        selectedTaskOwners.keys.forEach { owner ->
            val tagged = rowsByOwner[owner].orEmpty().filter { !it.taskProtocolSemanticsJson.isNullOrBlank() }
            tagged.groupBy { it.weekNumber }.forEach { (week, rows) ->
                val metadata = rows.mapNotNull { row -> runCatching {
                    TaskProtocolExposureMetadata.fromJsonString(requireNotNull(row.taskProtocolSemanticsJson))
                }.getOrNull() }
                if (metadata.size != rows.size || metadata.map { it.exposureIndex }.distinct().size != metadata.size ||
                    metadata.map { it.exposureIndex }.size != metadata.map { it.exposureIndex }.toSet().size ||
                    metadata.mapIndexed { index, value -> value.exposureIndex to rows[index].dayOfWeek }
                        .map { it.second }.distinct().size != rows.size ||
                    metadata.any { it.exposureIndex !in 1..it.authorization.definition.weeklyExposures }) {
                    integrityReasons += "B6_TASK_PROTOCOL_EXPOSURE_IDENTITY_FAILURE"
                }
            }
        }
        return integrityReasons
    }

    /**
     * A B5-selected Quality owner with a rejected B6 may remain unchanged in EXP, including a
     * placement/order-only move. It may not add an owner-week or change its executable prescription.
     * This is a B7 integrity alarm; the builder boundary must already have prevented the material.
     */
    private fun rejectedQualityMaterializationReasons(
        comparison: StimulusSelectionProgramComparison
    ): Set<String> {
        val executableStatuses = setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        )
        val before = comparison.control.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
        }
        val after = comparison.experimental.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
        }
        val reasons = linkedSetOf<String>()
        comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().forEach { authorization ->
            val owner = authorization.owner ?: return@forEach
            if (authorization.quality == null || authorization.authorizedPrescription != null && authorization.status in executableStatuses) {
                return@forEach
            }
            val identity = StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
            val weeks = (before.keys + after.keys).asSequence()
                .filter { it.first == identity }
                .map { it.second }
                .distinct()
            weeks.forEach weekLoop@ { week ->
                val beforeRows = before[identity to week].orEmpty()
                val afterRows = after[identity to week].orEmpty()
                if (afterRows.isEmpty()) return@weekLoop
                fun signatures(rows: List<ProgramSkeletonItem>) = rows.map { row ->
                    QualityPrescriptionSignature(
                        prescription = row.prescription,
                        setCount = row.setCount,
                        reps = row.reps,
                        weightKg = row.weightKg,
                        seconds = row.seconds,
                        restSeconds = row.restSeconds,
                        weightSource = row.weightSource,
                        setPrescriptions = row.setPrescriptions
                    )
                }.sortedBy { it.toString() }
                if (beforeRows.isEmpty() || signatures(beforeRows) != signatures(afterRows)) {
                    reasons += "B6_REJECTED_QUALITY_OWNER_WEEK_MATERIALIZED"
                }
            }
        }
        return reasons
    }

    private fun noMaterialChange(comparison: StimulusSelectionProgramComparison): Boolean =
        personalizedProgramFingerprint(comparison.control.request, comparison.control.items) ==
            personalizedProgramFingerprint(comparison.experimental.request, comparison.experimental.items) &&
            comparison.control.weekDaySchedule == comparison.experimental.weekDaySchedule &&
            comparison.controlOwnerIdentities == comparison.experimentalOwnerIdentities

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
            .filter {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.addedOwnerIdentities ||
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.sharedOwnerIdentities
            }
            .flatMap { it.coveredTargetIds }
        val attributed = attributions.flatMap { it.targetIds }
        val fromTraces = comparison.materializationTraces
            .filter {
                val key = it.selectedStableKey ?: return@filter false
                val identity = it.selectionRole?.let { role -> StimulusPrescriptionOwnerIdentity(key, role) }
                identity != null && (identity in comparison.addedOwnerIdentities || identity in comparison.sharedOwnerIdentities)
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
        val selected = comparison.selectionPlan.selectedCandidates.associateBy {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        comparison.addedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val candidate = selected[identity]
            result += if (candidate != null) {
                val taskProtocol = exactTaskProtocolAttributions(comparison, identity, candidate)
                if (taskProtocol.isNotEmpty()) StimulusExperimentalChangeAttribution(
                    stableKey = identity.stableKey,
                    selectionRole = identity.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL,
                    targetIds = taskProtocol.flatMap { it.authorization.attributedTasks }.distinct().sortedBy { it.ordinal }
                        .map { "TASK:${it.name}" },
                    reasonCodes = listOf("B6_USER_APPROVED_EXACT_TASK_PROTOCOL", "TASK_CREDIT_NON_ADDITIVE"),
                    evidenceSources = listOf("EXACT_B4_TASK_TARGET", "EXACT_B5_OWNER_ROLE", "USER_APPROVED_PROJECT_POLICY",
                        "LOSSLESS_TASK_PROTOCOL_SHAPE", "EXPERIMENTAL_MATERIALIZATION_MATCH")
                ) else StimulusExperimentalChangeAttribution(
                    stableKey = identity.stableKey,
                    selectionRole = identity.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_SELECTED_IDENTITY_ADDED")
                )
            } else {
                StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole, StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                    reasonCodes = listOf("UNEXPLAINED_ADDED_IDENTITY"))
            }
        }
        comparison.selectionPlan.selectedCandidates
            .filter {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in comparison.sharedOwnerIdentities
            }
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            .forEach { candidate ->
                result += StimulusExperimentalChangeAttribution(
                    stableKey = candidate.stableKey,
                    selectionRole = candidate.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B5_REUSED_IDENTITY,
                    targetIds = candidate.coveredTargetIds.toList().sorted(),
                    reasonCodes = listOf("B5_REUSED_IDENTITY")
                )
            }
        comparison.removedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val evidence = removalCausalEvidence(comparison, identity, selected)
            val targetIds = evidenceOwnerTargetIds(comparison, identity)
            val canonicalReplacementTargets = if (evidence.contradictoryProvenance) emptyList()
                else canonicalReplacementTargetIds(comparison, identity, selected)
            result += when {
                canonicalReplacementTargets.isNotEmpty() ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
                        canonicalReplacementTargets,
                        listOf("B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE") +
                            (if (canonicalReplacementTargets.any { it.startsWith("MOVEMENT:") }) {
                                listOf("B6_AUTHORIZED_REGIONAL_MOVEMENT_REPLACEMENT")
                            } else emptyList()) +
                            (if (canonicalReplacementTargets.any { it.startsWith("TASK:") }) {
                                listOf("B6_APPROVED_TASK_PROTOCOL_REPLACEMENT", "TASK_CREDIT_NON_ADDITIVE")
                            } else emptyList()),
                        listOf("EXACT_B5_CANONICAL_REPLACEMENT_OWNER") +
                            (if (canonicalReplacementTargets.any { it.startsWith("MOVEMENT:") }) {
                                listOf("EXACT_B4_MOVEMENT_TARGET", "EXACT_B5_PRIMARY_MOVEMENT_OWNER",
                                    "EXACT_B6_REGIONAL_HYPERTROPHY_AUTHORITY", "EXACT_WEEKLY_MATERIALIZATION_MATCH")
                            } else emptyList()) +
                            (if (canonicalReplacementTargets.any { it.startsWith("TASK:") }) {
                                listOf("EXACT_TASK_B6_AUTHORIZATION", "DIRECT_CANONICAL_TASK_RELATION",
                                    "USER_APPROVED_PROJECT_POLICY", "LOSSLESS_TASK_MATERIALIZATION", "TASK_PROTOCOL_FREQUENCY_SATISFIED")
                            } else {
                                listOf("EXACT_B6_AUTHORIZATION_FOR_REPLACEMENT", "EXACT_OWNER_QUALITY_TARGET_AUTHORITY",
                                    "EXPERIMENTAL_AUTHORIZED_WEEKLY_SUBSET")
                            }))
                evidence.governedExperimentalChangeExists &&
                    evidence.removedOwnerHasCapacityOrPlacementEvidence &&
                    evidence.removedOwnerHasDisappearanceEvidence &&
                    !evidence.removedOwnerWasDirectB5Action &&
                    !evidence.contradictoryProvenance ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT,
                        targetIds, listOf("REMOVED_IDENTITY_ATTRIBUTED_TO_DOWNSTREAM_CONSTRAINT"), evidence.evidenceSources)
                evidence.governedExperimentalChangeExists &&
                    evidence.removedOwnerHasCapacityOrPlacementEvidence &&
                    !evidence.removedOwnerWasDirectB5Action &&
                    !evidence.contradictoryProvenance ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT,
                        targetIds, listOf("REMOVAL_CAUSALITY_UNPROVEN"), evidence.evidenceSources)
                else ->
                    StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole,
                        StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
                        targetIds, listOf("UNEXPLAINED_REMOVED_IDENTITY"), evidence.evidenceSources)
            }
        }
        val controlByIdentity = comparison.control.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        val experimentalByIdentity = comparison.experimental.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
        (controlByIdentity.keys intersect experimentalByIdentity.keys).sortedWith(compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })).forEach { identity ->
            val before = controlByIdentity.getValue(identity).map(::prescription)
            val after = experimentalByIdentity.getValue(identity).map(::prescription)
            if (before == after) return@forEach
            val downstreamTargets = constrainedDownstreamTargetIds(comparison, identity)
            val taskProtocol = exactTaskProtocolAttributions(comparison, identity, selected[identity])
            val executableAuthorizations = exactExecutableChangeAuthorizations(comparison, identity, selected[identity])
            val source = when {
                downstreamTargets.isNotEmpty() -> StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT
                taskProtocol.isNotEmpty() -> StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL
                executableAuthorizations.any { it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION } ->
                    StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION
                executableAuthorizations.any { it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR } -> StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION
                executableAuthorizations.any { it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE } -> StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION
                else -> StimulusExperimentalChangeAttributionSource.UNEXPLAINED
            }
            val reasonCodes = when {
                source == StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT ->
                    listOf("OWNER_LOCAL_CONSTRAINED_SET_SUBSET", "B5_TARGET_REMAINS_B6_AUTHORIZED")
                source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ->
                    listOf("UNEXPLAINED_PRESCRIPTION_CHANGE")
                source == StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION ->
                    listOf("B6_COLD_START_SHAPE_AUTHORITY_LOAD_REQUIRES_USER_INPUT")
                source == StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL ->
                    listOf("B6_USER_APPROVED_EXACT_TASK_PROTOCOL", "TASK_CREDIT_NON_ADDITIVE")
                else -> listOf("B6_AUTHORIZED_PRESCRIPTION_CHANGE")
            }
            result += StimulusExperimentalChangeAttribution(identity.stableKey, identity.selectionRole, source,
                when {
                    downstreamTargets.isNotEmpty() -> downstreamTargets
                    taskProtocol.isNotEmpty() -> taskProtocol.flatMap { it.authorization.attributedTasks }.distinct().sortedBy { it.ordinal }.map { "TASK:${it.name}" }
                    else -> executableAuthorizations.map { it.targetId }.distinct().sorted()
                },
                reasonCodes,
                if (downstreamTargets.isNotEmpty()) listOf("EXPERIMENTAL_OWNER_CONSTRAINT_TRACE", "CONTROL_PRESCRIPTION_IS_EXACT_SET_SUPERSET")
                else if (taskProtocol.isNotEmpty()) listOf("EXACT_B4_TASK_TARGET", "EXACT_B5_OWNER_ROLE", "USER_APPROVED_PROJECT_POLICY", "EXPERIMENTAL_MATERIALIZATION_MATCH")
                else if (executableAuthorizations.isNotEmpty()) listOf("EXACT_B5_OWNER_TARGET_COVERAGE",
                    "EXACT_OWNER_QUALITY_TARGET_AUTHORITY", "EXPERIMENTAL_AUTHORIZED_WEEKLY_SUBSET") else emptyList())
        }
        return result
    }

    /** Owner-local bounded displacement requires an explicit allocator trace and an exact set-prefix reduction. */
    private fun constrainedDownstreamTargetIds(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<String> {
        val execution = comparison.experimental.personalizedDecision?.planningBudget?.execution ?: return emptyList()
        val events = execution.ownerAllocationProvenance
        val edges = execution.ownerDisplacementEdges
        if (events.isEmpty() || edges.isEmpty() || !isExactSetPrefixReduction(comparison, identity)) return emptyList()

        val controlBySlot = comparison.control.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val experimentalBySlot = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        if (controlBySlot.isEmpty() || controlBySlot.keys != experimentalBySlot.keys) return emptyList()
        val changedSlots = controlBySlot.keys.filter { slot ->
            val before = controlBySlot.getValue(slot)
            val after = experimentalBySlot.getValue(slot)
            before.setCount != after.setCount || before.setPrescriptions != after.setPrescriptions
        }
        if (changedSlots.isEmpty()) return emptyList()

        val selectedCauseOwners = comparison.selectionPlan.selectedCandidates.asSequence()
            .mapNotNull candidateLoop@ { candidate ->
                val causeOwner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                if (causeOwner !in comparison.addedOwnerIdentities) return@candidateLoop null
                val demandIds = comparison.experimental.personalizedDecision?.authorizedScheduling?.authorized.orEmpty()
                    .filter { StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == causeOwner }
                    .mapTo(sortedSetOf()) { it.id }
                if (demandIds.isEmpty()) return@candidateLoop null
                candidate.coveredTargetIds.asSequence().mapNotNull targetLoop@ { targetId ->
                    val authorizations = exactExecutableChangeAuthorizations(comparison, causeOwner, candidate)
                        .filter { it.targetId == targetId }
                    if (authorizations.isEmpty()) return@targetLoop null
                    Triple(causeOwner, targetId, demandIds)
                }.toList()
            }.flatten().toList()

        return selectedCauseOwners.mapNotNull { (causeOwner, targetId, causeDemandIds) ->
            val quality = targetId.takeIf { it.startsWith("QUALITY:") }?.removePrefix("QUALITY:")
                ?: return@mapNotNull null
            val everyChangedSlotProven = changedSlots.all { slot ->
                val before = controlBySlot.getValue(slot)
                val after = experimentalBySlot.getValue(slot)
                val beforeState = ownerAllocationState(before)
                val afterState = ownerAllocationState(after)
                val matchingEdges = edges.filter { edge -> edge.causeOwner == causeOwner && edge.displacedOwner == identity &&
                    edge.targetIds.contains(targetId) && edge.qualities.contains(quality) &&
                    edge.authorizedDemandIds.any { it in causeDemandIds } }
                hasExactOwnerDisplacementProof(events, matchingEdges, causeOwner, identity, beforeState, afterState)
            }
            targetId.takeIf { everyChangedSlotProven }
        }.distinct().sorted()
    }

    private fun exactTaskProtocolAttributions(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        candidate: StimulusSelectedCandidate?
    ): List<TaskProtocolExposureMetadata> {
        if (candidate == null || StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole) != identity ||
            !candidate.primaryTargetId.startsWith("TASK:")) return emptyList()
        val rows = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }
        if (rows.isEmpty() || rows.any { it.taskProtocolSemanticsJson.isNullOrBlank() }) return emptyList()
        val b4Tasks = comparison.targetPlan.taskTargets.mapNotNullTo(linkedSetOf()) { target ->
            runCatching { CanonicalTaskTarget.valueOf(target.task) }.getOrNull()
        }
        val metadata = rows.mapNotNull { row -> runCatching {
            TaskProtocolExposureMetadata.fromJsonString(requireNotNull(row.taskProtocolSemanticsJson))
        }.getOrNull() }
        if (metadata.size != rows.size || rows.indices.any { index ->
                !metadata[index].matchesMaterializedItem(
                    item = rows[index],
                    actualActivityKind = metadata[index].authorization.materializationActivityKind,
                    currentB4Tasks = b4Tasks,
                    selectedPrimaryTargetId = candidate.primaryTargetId
                )
            }) return emptyList()
        val definition = metadata.first().authorization.definition
        if (metadata.any { it.authorization.definition.protocolId != definition.protocolId ||
                it.authorization.attributedTasks != metadata.first().authorization.attributedTasks }) return emptyList()
        val perWeek = rows.indices.groupBy { rows[it].weekNumber }
        if (perWeek.values.any { indices ->
                val weekMetadata = indices.map(metadata::get)
                weekMetadata.map { it.exposureIndex }.distinct().size != weekMetadata.size ||
                    indices.map { rows[it].dayOfWeek }.distinct().size != indices.size
            }) return emptyList()
        return metadata
    }

    private fun isExactSetPrefixReduction(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): Boolean {
        val control = comparison.control.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val experimental = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }.associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        if (control.isEmpty() || control.keys != experimental.keys) return false
        var reduced = false
        for ((slot, before) in control) {
            val after = experimental.getValue(slot)
            if (before.setPrescriptions == after.setPrescriptions && before.setCount == after.setCount) continue
            val exactPrefix = after.setPrescriptions.size < before.setPrescriptions.size &&
                before.setPrescriptions.take(after.setPrescriptions.size) == after.setPrescriptions &&
                after.setCount == after.setPrescriptions.size &&
                before.prescription == after.prescription && before.restSeconds == after.restSeconds &&
                before.weightSource == after.weightSource && before.reps == after.reps &&
                before.weightKg == after.weightKg && before.seconds == after.seconds
            if (!exactPrefix) return false
            reduced = true
        }
        return reduced
    }

    /**
     * A canonical B5 owner may take over the exact exercise key previously emitted under a
     * legacy role. Treat that owner-role replacement as target-governed only when the new exact
     * owner has executable B6 authorization; same-key coincidence alone remains unexplained.
     */
    private fun canonicalReplacementTargetIds(
        comparison: StimulusSelectionProgramComparison,
        removed: StimulusPrescriptionOwnerIdentity,
        selected: Map<StimulusPrescriptionOwnerIdentity, StimulusSelectedCandidate>
    ): List<String> {
        if (removed !in comparison.removedOwnerIdentities ||
            removed.selectionRole.startsWith("CANONICAL_STIMULUS_")) return emptyList()
        val replacements = selected.values.filter { candidate ->
            val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
            candidate.stableKey == removed.stableKey && owner != removed &&
                owner in comparison.addedOwnerIdentities && candidate.coveredTargetIds.any {
                    candidate.selectionRole == "CANONICAL_STIMULUS_${it.replace(':', '_')}"
                }
        }
        // A same-key set of alternative roles cannot establish which one replaced this owner.
        val replacement = replacements.singleOrNull() ?: return emptyList()
        val owner = StimulusPrescriptionOwnerIdentity(replacement.stableKey, replacement.selectionRole)
        val omission = comparison.nonSelectionProvenance.singleOrNull { it.omittedControlOwner == removed }
            ?: return emptyList()
        val exactB5Targets = omission.targetEvidence.asSequence()
            .filter { evidence ->
                val disposition = evidence.disposition
                evidence.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT &&
                    disposition.status == StimulusCandidateDispositionStatus.SELECTED &&
                    disposition.directTargetCandidate && disposition.stableKey == removed.stableKey &&
                    disposition.canonicalSelectionRole == replacement.selectionRole &&
                    disposition in comparison.selectionPlan.candidateDispositionIndex.entries
            }
            .map { it.targetId }
            .toSet()
        val qualityTargets = exactExecutableChangeAuthorizations(comparison, owner, replacement)
            .filter {
                it.targetId.startsWith("QUALITY:") && it.targetId in exactB5Targets &&
                    replacement.selectionRole == "CANONICAL_STIMULUS_${it.targetId.replace(':', '_')}"
            }
            .map { it.targetId }
        val movementTargets = exactExecutableChangeAuthorizations(comparison, owner, replacement)
            .mapNotNull { authorization ->
                val targetId = authorization.targetId
                if (targetId !in exactB5Targets || replacement.primaryTargetId != targetId) return@mapNotNull null
                val target = comparison.targetPlan.movementTargets.singleOrNull { it.targetId == targetId }
                    ?: return@mapNotNull null
                val expectedRole = "CANONICAL_STIMULUS_MOVEMENT_${target.movementCoverage.name}"
                if (replacement.selectionRole != expectedRole ||
                    authorization.quality != TrainableQuality.HYPERTROPHY) return@mapNotNull null
                val dose = target.regionalDoseTargets.singleOrNull {
                    it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                        it.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION
                } ?: return@mapNotNull null
                val authorizedWeeklyUnits = dose.authorizedWholeSetUnits?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                if (dose.weeklyTarget == null || dose.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) {
                    return@mapNotNull null
                }

                // A target label and an authorized candidate are not enough: every week removed
                // from the exact old CONTROL identity must have the selected new-role material,
                // and its B4 residual must be fully represented by those B6-authorized rows.
                val removedWeeks = comparison.control.items.asSequence()
                    .filter { it.exerciseStableKey == removed.stableKey && it.selectionRole == removed.selectionRole }
                    .map { it.weekNumber }.toSortedSet()
                val replacementRows = comparison.experimental.items.filter {
                    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }
                val rowsByWeek = replacementRows.groupBy { it.weekNumber }
                if (removedWeeks.isEmpty() || rowsByWeek.keys != removedWeeks ||
                    removedWeeks.any { week -> rowsByWeek[week].orEmpty().sumOf { it.setCount } != authorizedWeeklyUnits }) {
                    return@mapNotNull null
                }
                val trace = comparison.materializationTraces.singleOrNull {
                    it.targetId == targetId && it.selectedStableKey == owner.stableKey &&
                        it.selectionRole == owner.selectionRole
                } ?: return@mapNotNull null
                if (!trace.selectedAtB5 || trace.directIdentityVerifiedAtSelection != true ||
                    !trace.presentInFinalExperimentalSkeleton || !trace.directIdentityStillValid ||
                    trace.finalWeeklyOccurrences != replacementRows.size ||
                    trace.finalTotalSetUnits != replacementRows.sumOf { it.setCount }) return@mapNotNull null
                targetId
            }
        val taskTargets = exactTaskReplacementTargetIds(comparison, removed, replacement, exactB5Targets)
        return (qualityTargets + movementTargets + taskTargets).distinct().sorted()
    }

    /** Task role replacement has its own exact C24 B6 proof; Quality B6 rows cannot stand in for it. */
    private fun exactTaskReplacementTargetIds(
        comparison: StimulusSelectionProgramComparison,
        removed: StimulusPrescriptionOwnerIdentity,
        replacement: StimulusSelectedCandidate,
        exactB5Targets: Set<String>
    ): List<String> {
        val primaryTargetId = replacement.primaryTargetId
        if (!primaryTargetId.startsWith("TASK:") || primaryTargetId !in exactB5Targets ||
            replacement.selectionRole != "CANONICAL_STIMULUS_${primaryTargetId.replace(':', '_')}" ||
            primaryTargetId !in replacement.coveredTargetIds) return emptyList()
        val task = runCatching { CanonicalTaskTarget.valueOf(primaryTargetId.removePrefix("TASK:")) }.getOrNull()
            ?: return emptyList()
        if (comparison.targetPlan.taskTargets.none { it.task == task.name }) return emptyList()
        val identity = StimulusPrescriptionOwnerIdentity(replacement.stableKey, replacement.selectionRole)
        val metadata = exactTaskProtocolAttributions(comparison, identity, replacement)
        if (metadata.isEmpty()) return emptyList()
        val authorization = metadata.first().authorization
        val definition = authorization.definition
        if (removed.stableKey != identity.stableKey ||
            ApprovedBadmintonTaskProtocols.exact(identity.stableKey, identity.selectionRole, task) != definition ||
            definition.primaryTask != task || authorization.status != TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL ||
            authorization.definition.provenance != TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY ||
            authorization.attributedTasks != definition.authorizedTasks ||
            authorization.transferEvidence.keys != definition.authorizedTasks ||
            task !in authorization.attributedTasks ||
            authorization.transferEvidence[task] != com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT
        ) return emptyList()

        val weeks = (1..comparison.experimental.request.durationWeeks.coerceAtLeast(1)).toSet()
        val ownerRows = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }
        if (ownerRows.map { it.weekNumber }.toSet() != weeks) return emptyList()
        val byWeek = ownerRows.groupBy { it.weekNumber }
        if (weeks.any { week ->
                val weekRows = byWeek[week].orEmpty()
                val indices = metadata.indices.filter { ownerRows[it].weekNumber == week }.map { metadata[it].exposureIndex }
                weekRows.size != definition.weeklyExposures || indices.sorted() != (1..definition.weeklyExposures).toList() ||
                    weekRows.map { it.dayOfWeek }.distinct().size != definition.weeklyExposures
            }) return emptyList()
        val frequency = comparison.experimental.taskProtocolFrequencyOutcomes.filter {
            it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole && it.protocolId == definition.protocolId
        }
        if (frequency.size != weeks.size || frequency.map { it.week }.toSet() != weeks || frequency.any {
                it.status != TaskProtocolFrequencyStatus.SATISFIED ||
                    it.authority != TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY ||
                    it.requestedExposures != definition.weeklyExposures ||
                    it.placedExposures != definition.weeklyExposures || it.shortfall != 0
            }) return emptyList()
        return listOf(primaryTargetId)
    }

    /** Keep owner, quality and target joined until the exact experimental rows prove the prescription. */
    private fun exactExecutableChangeAuthorizations(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        candidate: StimulusSelectedCandidate?
    ): List<StimulusPrescriptionAuthorization> {
        if (candidate == null || StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole) != identity) return emptyList()
        val plan = comparison.prescriptionAuthorizationPlan ?: return emptyList()
        if (identity in plan.conflictingOwners) return emptyList()
        val authorizations = plan.authorizations.filter { authorization ->
            authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == identity
        }
        if (authorizations.any { it.status in setOf(
                StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY,
                StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER
            ) }) return emptyList()
        val rows = comparison.experimental.items.filter {
            it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
        }
        // Empty materialization is a valid mathematical subset but proves no actual change.
        if (rows.isEmpty() || rows.any { it.setPrescriptions.isEmpty() || it.setCount != it.setPrescriptions.size }) return emptyList()
        return authorizations.filter { authorization ->
            val quality = authorization.quality ?: return@filter false
            val authorized = authorization.authorizedPrescription ?: return@filter false
            val targetIsCanonicalQuality = authorization.targetId == "QUALITY:${quality.name}" &&
                comparison.targetPlan.qualityTargets.any { it.quality == quality }
            val targetIsRegionalMovement = quality == TrainableQuality.HYPERTROPHY &&
                comparison.targetPlan.movementTargets.any { movement ->
                    movement.targetId == authorization.targetId && movement.regionalDoseTargets.any { dose ->
                        dose.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                            dose.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION &&
                            (dose.authorizedWholeSetUnits ?: 0) > 0 && dose.weeklyTarget != null
                    }
                }
            (targetIsCanonicalQuality || targetIsRegionalMovement) &&
                authorization.targetId in candidate.coveredTargetIds &&
                authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                ) && authorization.executionAuthority == canonicalExecutionAuthority(quality, authorized) &&
                (authorization.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
                    authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                        authorization.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT &&
                        authorization.coldStartCalibration?.owner == identity) &&
                validateAuthorizedWeeklySubset(rows, authorized, identity.stableKey, identity.selectionRole).valid
        }
    }

    private fun removalCausalEvidence(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity,
        selected: Map<StimulusPrescriptionOwnerIdentity, StimulusSelectedCandidate>
    ): StimulusRemovalCausalEvidence {
        val selectionTraces = comparison.selectionPlan.traces.filter { trace ->
            traceOwnerMatches(trace, identity, comparison)
        }
        val materializationTraces = comparison.materializationTraces.filter { trace ->
            traceOwnerMatches(trace, identity, comparison)
        }
        val localReasonCodes = buildList {
            selectionTraces.forEach { trace ->
                val selectedOrReused =
                    (trace.selectedStableKey == identity.stableKey &&
                        roleMatches(trace.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)) ||
                        (trace.coveredByPreviouslySelectedStableKey == identity.stableKey &&
                            roleMatches(trace.coveredByPreviouslySelectedSelectionRole, identity.selectionRole, comparison, identity.stableKey))
                if (selectedOrReused) addAll(trace.reasonCodes)
                trace.candidateRejectionReasons[identity.stableKey]?.let { reason ->
                    if (roleMatches(trace.candidateSelectionRoles[identity.stableKey], identity.selectionRole, comparison, identity.stableKey)) add(reason)
                }
            }
            materializationTraces.forEach { addAll(it.reasonCodes) }
        }.distinct()
        val governedByB5 = comparison.addedOwnerIdentities.any { selected[it] != null }
        val governedByB6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { authorization ->
            authorization.owner != null && authorization.authorizedPrescription != null &&
                authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                )
        }
        val capacityOrPlacement = localReasonCodes.any(::isCapacityOrPlacementEvidence)
        val disappearance = localReasonCodes.any(::isDisappearanceEvidence)
        val directB5Action = selected.containsKey(identity) || selectionTraces.any {
            (it.selectedStableKey == identity.stableKey && roleMatches(it.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey))
        }
        val contradictory = localReasonCodes.any(::isContradictoryProvenance)
        val sources = buildList {
            if (selectionTraces.isNotEmpty()) add("B5_SELECTION_TRACE_OWNER_LOCAL")
            if (materializationTraces.isNotEmpty()) add("FINAL_MATERIALIZATION_TRACE_OWNER_LOCAL")
            if (capacityOrPlacement) add("OWNER_LOCAL_CAPACITY_OR_PLACEMENT")
            if (disappearance) add("OWNER_LOCAL_DISAPPEARANCE")
        }.distinct()
        return StimulusRemovalCausalEvidence(
            stableKey = identity.stableKey,
            selectionRole = identity.selectionRole,
            governedExperimentalChangeExists = governedByB5 || governedByB6,
            removedOwnerHasCapacityOrPlacementEvidence = capacityOrPlacement,
            removedOwnerHasDisappearanceEvidence = disappearance,
            removedOwnerWasDirectB5Action = directB5Action,
            contradictoryProvenance = contradictory,
            evidenceSources = sources,
            reasonCodes = localReasonCodes
        )
    }

    private fun evidenceOwnerTargetIds(
        comparison: StimulusSelectionProgramComparison,
        identity: StimulusPrescriptionOwnerIdentity
    ): List<String> = (comparison.selectionPlan.traces.filter { traceOwnerMatches(it, identity, comparison) }.map { it.targetId } +
        comparison.materializationTraces.filter { traceOwnerMatches(it, identity, comparison) }.map { it.targetId }).distinct().sorted()

    private fun traceOwnerMatches(
        trace: StimulusCandidateSelectionTrace,
        identity: StimulusPrescriptionOwnerIdentity,
        comparison: StimulusSelectionProgramComparison
    ): Boolean {
        val selectedMatch = trace.selectedStableKey == identity.stableKey &&
            roleMatches(trace.selectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)
        val reusedMatch = trace.coveredByPreviouslySelectedStableKey == identity.stableKey &&
            roleMatches(trace.coveredByPreviouslySelectedSelectionRole, identity.selectionRole, comparison, identity.stableKey)
        val rejectedMatch = trace.candidateRejectionReasons.containsKey(identity.stableKey) &&
            roleMatches(trace.candidateSelectionRoles[identity.stableKey], identity.selectionRole, comparison, identity.stableKey)
        return selectedMatch || reusedMatch || rejectedMatch
    }

    private fun traceOwnerMatches(
        trace: StimulusCandidateMaterializationTrace,
        identity: StimulusPrescriptionOwnerIdentity,
        comparison: StimulusSelectionProgramComparison
    ): Boolean = trace.selectedStableKey == identity.stableKey &&
        roleMatches(trace.selectionRole, identity.selectionRole, comparison, identity.stableKey)

    private fun roleMatches(
        traceRole: String?,
        expectedRole: String,
        comparison: StimulusSelectionProgramComparison,
        stableKey: String
    ): Boolean = traceRole == expectedRole ||
        (traceRole == null &&
            comparison.controlOwnerIdentities.count { it.stableKey == stableKey } <= 1 &&
            comparison.experimentalOwnerIdentities.count { it.stableKey == stableKey } <= 1)

    private fun isCapacityOrPlacementEvidence(code: String): Boolean =
        code.contains("CAPACITY", ignoreCase = true) || code.contains("PLACEMENT", ignoreCase = true) ||
            code.contains("REFLOW", ignoreCase = true) || code.contains("DISPLAC", ignoreCase = true) ||
            code.contains("CONSTRAINED_MACHINERY", ignoreCase = true)

    private fun isDisappearanceEvidence(code: String): Boolean =
        code.contains("NOT_MATERIALIZED", ignoreCase = true) || code.contains("REMOVED", ignoreCase = true) ||
            code.contains("DISPLAC", ignoreCase = true) || code.contains("UNFUNDED", ignoreCase = true) ||
            code.contains("DEFER", ignoreCase = true) || code.contains("DISAPPEAR", ignoreCase = true)

    private fun isContradictoryProvenance(code: String): Boolean =
        code.contains("DIRECT_B5_ACTION", ignoreCase = true) || code == "SELECTION_TARGET_IDENTITY_MATERIALIZED"

    private fun targetOutcomes(comparison: StimulusSelectionProgramComparison, affected: Set<String>): List<StimulusExperimentalTargetOutcome> =
        comparison.targetPlan.qualityTargets.map { target ->
            val id = "QUALITY:${target.quality.name}"
            val control = comparison.controlAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            val experimental = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
            val regionalHypertrophyUnits = if (target.quality == TrainableQuality.HYPERTROPHY) {
                regionalHypertrophyAddedWeeklyUnits(comparison)
            } else 0.0
            val comparableExperimentalUnits = experimental?.plannedWeeklyDirectUnits?.let {
                (it - regionalHypertrophyUnits).coerceAtLeast(0.0)
            }
            numericOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control?.plannedWeeklyDirectUnits, comparableExperimentalUnits, control?.plannedWeeklyDirectSessions, experimental?.plannedWeeklyDirectSessions,
                control?.weeklyDirectUnitsStatus, experimental?.weeklyDirectUnitsStatus, control?.weeklyDirectSessionsStatus, experimental?.weeklyDirectSessionsStatus)
                .let { outcome ->
                    if (regionalHypertrophyUnits <= 0.0) outcome else outcome.copy(
                        reasonCodes = (outcome.reasonCodes + "AUTHORIZED_REGIONAL_HYPERTROPHY_EXCLUDED_FROM_AGGREGATE_QUALITY_COMPARISON").distinct(),
                        regionalHypertrophyUnitsExcludedFromAggregateComparison = regionalHypertrophyUnits
                    )
                }
        } + comparison.targetPlan.taskTargets.map { target ->
            val id = "TASK:${target.task}"
            val control = comparison.controlAudit?.taskAudits?.firstOrNull { it.task == target.task }
            val experimental = comparison.experimentalAudit?.taskAudits?.firstOrNull { it.task == target.task }
            taskOutcome(id, affected.contains(id), target.numericAuthority, target.weeklyDirectUnitsTarget, target.weeklyDirectSessionsTarget,
                control, experimental)
        } + comparison.targetPlan.movementTargets.mapNotNull { target ->
            val id = target.targetId
            val dose = target.regionalDoseTargets.firstOrNull {
                it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                    it.shapeAuthority == StimulusMovementDoseShapeAuthority.HYPERTROPHY_BAND_8_12_PERSONAL_7_15_RPE_7_USER_LOAD_CALIBRATION
            } ?: return@mapNotNull null
            val required = dose.authorizedWholeSetUnits ?: return@mapNotNull null
            val isAffected = id in affected
            if (required <= 0) {
                val coveredByQualityB6 = comparison.prescriptionAuthorizationPlan?.movementAuthorizations.orEmpty()
                    .any { it.targetId == id && it.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6 }
                val satisfied = isAffected && coveredByQualityB6 &&
                    dose.weeklyTarget != null && (dose.existingEquivalentExposure ?: 0.0) + 1e-9 >= dose.weeklyTarget
                return@mapNotNull StimulusExperimentalTargetOutcome(
                    targetId = id,
                    status = if (satisfied) StimulusExperimentalTargetOutcomeStatus.IMPROVED
                        else StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE,
                    directlyAffected = satisfied,
                    controlWeeklyUnitsDistance = dose.existingEquivalentExposure,
                    experimentalWeeklyUnitsDistance = dose.existingEquivalentExposure,
                    reasonCodes = if (satisfied) listOf(
                        "B4_TARGET_COVERED_BY_EXISTING_AUTHORIZED_QUALITY_EXPOSURE",
                        "B6_EXISTING_QUALITY_AUTHORITY_REUSED_NON_ADDITIVELY"
                    ) else dose.residualReasonCodes
                )
            }
            if (!isAffected) return@mapNotNull StimulusExperimentalTargetOutcome(
                id, StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE, false,
                controlWeeklyUnitsDistance = dose.existingEquivalentExposure,
                experimentalWeeklyUnitsDistance = dose.existingEquivalentExposure,
                reasonCodes = dose.residualReasonCodes
            )
            val authorization = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().singleOrNull {
                it.targetId == id && it.quality == TrainableQuality.HYPERTROPHY
            }
            val audit = comparison.prescriptionMaterializationAudits.singleOrNull {
                it.targetId == id && it.quality == TrainableQuality.HYPERTROPHY
            }
            val materialized = audit?.materializedWeeklySetUnits ?: 0
            val status = when {
                authorization?.authorizedPrescription == null -> StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY
                audit?.overrun?.let { it > 0 } == true -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
                audit?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED && materialized == required ->
                    StimulusExperimentalTargetOutcomeStatus.IMPROVED
                else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
            }
            StimulusExperimentalTargetOutcome(
                targetId = id,
                status = status,
                directlyAffected = true,
                controlWeeklyUnitsDistance = dose.existingEquivalentExposure,
                experimentalWeeklyUnitsDistance = (dose.existingEquivalentExposure ?: 0.0) + materialized,
                reasonCodes = when (status) {
                    StimulusExperimentalTargetOutcomeStatus.IMPROVED -> listOf("B4_RESIDUAL_FULLY_MATERIALIZED")
                    StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY -> listOf("B6_REGIONAL_RESIDUAL_AUTHORITY_MISSING")
                    StimulusExperimentalTargetOutcomeStatus.REGRESSED -> listOf("B6_REGIONAL_RESIDUAL_OVERRUN")
                    else -> listOf("TARGET_UNMET", "B4_RESIDUAL_SHORTFALL=${(required - materialized).coerceAtLeast(0)}")
                }
            )
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

    private fun directionOutcome(id: String, affected: Boolean, cu: StimulusTargetControlStatus?, eu: StimulusTargetControlStatus?, cs: StimulusTargetControlStatus?, es: StimulusTargetControlStatus?): StimulusExperimentalTargetOutcome {
        if (listOf(cu, eu, cs, es).any { it == StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED }) {
            return StimulusExperimentalTargetOutcome(id, StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE, affected,
                reasonCodes = listOf("DISTRIBUTION_COMPARISON_DEFERRED"))
        }
        val control = if (cu == StimulusTargetControlStatus.DIRECT_PRESENT || cs == StimulusTargetControlStatus.DIRECT_PRESENT) {
            StimulusTargetControlStatus.DIRECT_PRESENT
        } else cu ?: cs
        val experimental = if (eu == StimulusTargetControlStatus.DIRECT_PRESENT || es == StimulusTargetControlStatus.DIRECT_PRESENT) {
            StimulusTargetControlStatus.DIRECT_PRESENT
        } else eu ?: es
        val status = directionStatus(control, experimental)
        return StimulusExperimentalTargetOutcome(id, status, affected,
            reasonCodes = if (status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED && experimental != StimulusTargetControlStatus.DIRECT_PRESENT) listOf("TARGET_UNMET") else emptyList())
    }

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

}

/**
 * Projects only the newly materialized, exact-B6 movement-scoped Hypertrophy units out of the
 * aggregate Quality H comparison. The raw aggregate remains in the B4/B7 audit; regional units
 * have their own B4 residual and B7 overrun check, so comparing their sum to the historical
 * aggregate envelope would mix regional dose authority with an observed whole-quality reference.
 */
internal fun regionalHypertrophyAddedWeeklyUnits(comparison: StimulusSelectionProgramComparison): Double {
    val authorizationPlan = comparison.prescriptionAuthorizationPlan ?: return 0.0
    val horizon = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
    val qualityTargetId = "QUALITY:${TrainableQuality.HYPERTROPHY.name}"
    val addedByOwner = linkedMapOf<StimulusPrescriptionOwnerIdentity, Int>()

    comparison.targetPlan.movementTargets.forEach { movement ->
        val targetId = movement.targetId
        val dose = movement.regionalDoseTargets.firstOrNull {
            it.kind == StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET &&
                (it.authorizedWholeSetUnits ?: 0) > 0
        } ?: return@forEach
        comparison.selectionPlan.selectedCandidates
            .filter { it.primaryTargetId == targetId && targetId in it.coveredTargetIds && qualityTargetId !in it.coveredTargetIds }
            .forEach candidateLoop@{ candidate ->
                val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                val authorization = authorizationPlan.authorizations.singleOrNull {
                    it.targetId == targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                        it.owner?.let { exact -> exact.stableKey == owner.stableKey && exact.selectionRole == owner.selectionRole } == true
                } ?: return@candidateLoop
                if (authorization.status !in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                    ) || authorization.authorizedPrescription?.sets?.size != dose.authorizedWholeSetUnits
                ) return@candidateLoop
                if (authorizationPlan.movementAuthorizations.none {
                    it.targetId == targetId && it.owner == owner &&
                        it.status == StimulusMovementB6Status.AUTHORIZED_REGIONAL_HYPERTROPHY_B6
                }) return@candidateLoop
                val materialization = comparison.prescriptionMaterializationAudits.singleOrNull {
                    it.targetId == targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                        it.owner?.let { exact -> exact.stableKey == owner.stableKey && exact.selectionRole == owner.selectionRole } == true
                } ?: return@candidateLoop
                if (materialization.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE ||
                    materialization.totalCompatibleUnits <= 0
                ) return@candidateLoop
                val experimentalUnits = comparison.experimental.items.filter {
                    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }.sumOf { it.setPrescriptions.size }
                val controlUnits = comparison.control.items.filter {
                    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }.sumOf { it.setPrescriptions.size }
                val incrementalMaterializedUnits = minOf(
                    materialization.totalCompatibleUnits,
                    (experimentalUnits - controlUnits).coerceAtLeast(0)
                )
                if (incrementalMaterializedUnits > 0) {
                    // An exact physical owner can satisfy more than one regional observation;
                    // count its physical increment once in the aggregate projection.
                    addedByOwner[owner] = maxOf(addedByOwner[owner] ?: 0, incrementalMaterializedUnits)
                }
            }
    }
    return addedByOwner.values.sum().toDouble() / horizon
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
        .put("regionalHypertrophyUnitsExcludedFromAggregateComparison", outcome.regionalHypertrophyUnitsExcludedFromAggregateComparison)
        .put("reasonCodes", JSONArray(outcome.reasonCodes))
    }))
    .put("changeAttributions", JSONArray(changeAttributions.map { attribution -> JSONObject()
        .put("stableKey", attribution.stableKey).put("selectionRole", attribution.selectionRole).put("source", attribution.source.name)
        .put("targetIds", JSONArray(attribution.targetIds)).put("reasonCodes", JSONArray(attribution.reasonCodes))
        .put("evidenceSources", JSONArray(attribution.evidenceSources))
    }))
