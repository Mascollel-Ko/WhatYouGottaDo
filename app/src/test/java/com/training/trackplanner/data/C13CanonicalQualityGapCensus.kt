package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

internal enum class C13TargetGapRootCause {
    TARGET_HAS_NO_EXECUTABLE_B6,
    AUTHORIZED_BUT_NOT_FULLY_MATERIALIZED,
    B5_SELECTED_INSUFFICIENT_IDENTITY,
    BUILDER_CAPACITY_CONSTRAINED,
    PRESCRIPTION_CLASS_MISMATCH,
    HISTORY_BASELINE_EDGE_CASE,
    TARGET_ACCOUNTING_MISMATCH,
    TARGET_REGRESSED,
    MULTI_TARGET_COMPETITION,
    OTHER_PROVEN,
    UNRESOLVED
}

internal enum class C13FirstShortfallStage {
    B4_TARGET_CREATION,
    B5_SELECTION,
    B6_AUTHORITY,
    B6_REALIZATION,
    MATERIAL_DEMAND,
    FINITE_ALLOCATION,
    TIMED_ALLOCATION,
    COMPLETION,
    PLACEMENT,
    REBALANCING,
    FREQUENCY_EXPANSION,
    POST_SPLIT_REFLOW,
    EXACT_PRESCRIPTION_MATERIALIZATION,
    FINAL_TARGET_AUDIT,
    NONE
}

internal enum class C13B6FailureCause {
    NO_PERSONAL_REFERENCE,
    LOAD_SEMANTICS_UNRESOLVED,
    REP_RANGE_INCOMPATIBLE,
    EFFORT_TARGET_INCOMPLETE,
    TARGET_DOSE_WITHOUT_PRESCRIPTION,
    SAFE_REPAIR_NOT_AVAILABLE,
    MULTI_QUALITY_AUTHORITY_CONFLICT,
    MATERIALIZATION_FAILURE_AFTER_AUTHORIZATION,
    MODEL_UNAVAILABLE_TRUE_GAP,
    OTHER_PROVEN,
    UNRESOLVED
}

/** Small, typed input to the diagnostic classifier. It is test-only and grants no authority. */
internal data class C13TargetGapFacts(
    val selectedOwnerExists: Boolean,
    val realizationStatus: StimulusPrescriptionResolutionStatus?,
    val reasonCodes: Set<String>,
    val authorizationStatus: StimulusPrescriptionAuthorizationStatus?,
    val executionAuthority: StimulusPrescriptionExecutionAuthority?,
    val authorizedPrescriptionExists: Boolean,
    val materializationState: StimulusPrescriptionMaterializationState?,
    val numericAuthority: StimulusTargetNumericAuthority,
    val strategy: StimulusDoseStrategy,
    val outcomeStatus: StimulusExperimentalTargetOutcomeStatus?,
    val targetUnmet: Boolean,
    val finalUnitsStatus: StimulusTargetControlStatus?,
    val finalSessionsStatus: StimulusTargetControlStatus?,
    val finalIncompatibleDirectUnits: Int,
    val exactCapacityReduction: Boolean,
    val exactCapacityReductionStage: OwnerAllocationStage? = null,
    val numericFinalUnitsWithinTarget: Boolean? = null,
    val numericFinalSessionsWithinTarget: Boolean? = null,
    val ownerCompetesAcrossTargets: Boolean = false
)

internal data class C13TargetGapClassification(
    val rootCause: C13TargetGapRootCause,
    val firstShortfallStage: C13FirstShortfallStage,
    val b6FailureCause: C13B6FailureCause
)

internal fun classifyC13TargetGap(facts: C13TargetGapFacts): C13TargetGapClassification {
    val b6Cause = classifyC13B6Failure(
        facts.authorizationStatus,
        facts.realizationStatus,
        facts.reasonCodes,
        facts.executionAuthority,
        facts.authorizedPrescriptionExists,
        facts.materializationState,
        facts.numericAuthority,
        facts.strategy
    )
    val stage = when {
        !facts.selectedOwnerExists -> C13FirstShortfallStage.B5_SELECTION
        facts.authorizationStatus == StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE ||
            facts.realizationStatus in setOf(StimulusPrescriptionResolutionStatus.NO_SAFE_TARGET_COMPATIBLE_PRESCRIPTION,
                StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE) -> C13FirstShortfallStage.B6_REALIZATION
        facts.authorizationStatus == null || facts.authorizationStatus !in C13_EXECUTABLE_AUTHORIZATION_STATUSES ||
            facts.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
            !facts.authorizedPrescriptionExists -> C13FirstShortfallStage.B6_AUTHORITY
        facts.materializationState != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ->
            C13FirstShortfallStage.EXACT_PRESCRIPTION_MATERIALIZATION
        facts.exactCapacityReduction && facts.exactCapacityReductionStage != null ->
            facts.exactCapacityReductionStage.toC13Stage()
        facts.finalIncompatibleDirectUnits > 0 -> C13FirstShortfallStage.FINAL_TARGET_AUDIT
        facts.targetUnmet || facts.outcomeStatus == StimulusExperimentalTargetOutcomeStatus.REGRESSED ->
            C13FirstShortfallStage.FINAL_TARGET_AUDIT
        else -> C13FirstShortfallStage.NONE
    }
    val root = when {
        !facts.selectedOwnerExists -> C13TargetGapRootCause.B5_SELECTED_INSUFFICIENT_IDENTITY
        facts.authorizationStatus == StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE ->
            C13TargetGapRootCause.TARGET_HAS_NO_EXECUTABLE_B6
        facts.authorizationStatus == null || facts.authorizationStatus !in C13_EXECUTABLE_AUTHORIZATION_STATUSES ||
            facts.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
            !facts.authorizedPrescriptionExists -> C13TargetGapRootCause.TARGET_HAS_NO_EXECUTABLE_B6
        facts.materializationState != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ->
            C13TargetGapRootCause.AUTHORIZED_BUT_NOT_FULLY_MATERIALIZED
        facts.exactCapacityReduction -> C13TargetGapRootCause.BUILDER_CAPACITY_CONSTRAINED
        facts.finalIncompatibleDirectUnits > 0 -> C13TargetGapRootCause.PRESCRIPTION_CLASS_MISMATCH
        facts.ownerCompetesAcrossTargets -> C13TargetGapRootCause.MULTI_TARGET_COMPETITION
        facts.numericFinalUnitsWithinTarget == true && facts.numericFinalSessionsWithinTarget == true && facts.targetUnmet ->
            C13TargetGapRootCause.TARGET_ACCOUNTING_MISMATCH
        facts.strategy == StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE &&
            facts.numericAuthority == StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE &&
            facts.targetUnmet -> C13TargetGapRootCause.HISTORY_BASELINE_EDGE_CASE
        facts.outcomeStatus == StimulusExperimentalTargetOutcomeStatus.REGRESSED ->
            C13TargetGapRootCause.TARGET_REGRESSED
        facts.targetUnmet ->
            C13TargetGapRootCause.OTHER_PROVEN
        else -> C13TargetGapRootCause.UNRESOLVED
    }
    return C13TargetGapClassification(root, stage, b6Cause)
}

internal fun classifyC13B6Failure(
    status: StimulusPrescriptionAuthorizationStatus?,
    realizationStatus: StimulusPrescriptionResolutionStatus?,
    reasonCodes: Set<String>,
    executionAuthority: StimulusPrescriptionExecutionAuthority?,
    hasPrescription: Boolean,
    materializationState: StimulusPrescriptionMaterializationState?,
    numericAuthority: StimulusTargetNumericAuthority,
    strategy: StimulusDoseStrategy
): C13B6FailureCause = when {
    status == StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE ||
        realizationStatus == StimulusPrescriptionResolutionStatus.REALIZATION_MODEL_UNAVAILABLE ->
        C13B6FailureCause.MODEL_UNAVAILABLE_TRUE_GAP
    status == StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY -> C13B6FailureCause.MULTI_QUALITY_AUTHORITY_CONFLICT
    "CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE" in reasonCodes -> C13B6FailureCause.NO_PERSONAL_REFERENCE
    "PLANNED_RESISTANCE_LOAD_UNAVAILABLE" in reasonCodes || "PLANNED_LOAD_UNAVAILABLE" in reasonCodes ->
        C13B6FailureCause.LOAD_SEMANTICS_UNRESOLVED
    "HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE" in reasonCodes ||
        "B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE" in reasonCodes ->
        C13B6FailureCause.TARGET_DOSE_WITHOUT_PRESCRIPTION
    "PLANNED_REPS_OUTSIDE_STRENGTH_MODEL" in reasonCodes || "PLANNED_REPS_OUTSIDE_HYPERTROPHY_MODEL" in reasonCodes ->
        C13B6FailureCause.REP_RANGE_INCOMPATIBLE
    "EFFORT_TARGET_UNAVAILABLE" in reasonCodes || "MINIMUM_EFFORT_TARGET_UNAVAILABLE" in reasonCodes ->
        C13B6FailureCause.EFFORT_TARGET_INCOMPLETE
    status in C13_EXECUTABLE_AUTHORIZATION_STATUSES && executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
        hasPrescription && materializationState != StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ->
        C13B6FailureCause.MATERIALIZATION_FAILURE_AFTER_AUTHORIZATION
    status in C13_EXECUTABLE_AUTHORIZATION_STATUSES && executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
        hasPrescription && materializationState == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED ->
        C13B6FailureCause.OTHER_PROVEN
    status == StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION &&
        numericAuthority in setOf(StimulusTargetNumericAuthority.DIRECTION_ONLY, StimulusTargetNumericAuthority.NONE,
            StimulusTargetNumericAuthority.UNRESOLVED) -> C13B6FailureCause.TARGET_DOSE_WITHOUT_PRESCRIPTION
    status == StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION -> C13B6FailureCause.SAFE_REPAIR_NOT_AVAILABLE
    status == StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER -> C13B6FailureCause.OTHER_PROVEN
    status != null && (executionAuthority == StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT ||
        !hasPrescription) && strategy == StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE -> C13B6FailureCause.EFFORT_TARGET_INCOMPLETE
    else -> C13B6FailureCause.UNRESOLVED
}

private val C13_EXECUTABLE_AUTHORIZATION_STATUSES = setOf(
    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
)

private fun OwnerAllocationStage.toC13Stage(): C13FirstShortfallStage = when (this) {
    OwnerAllocationStage.MATERIAL_DEMAND -> C13FirstShortfallStage.MATERIAL_DEMAND
    OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION -> C13FirstShortfallStage.FINITE_ALLOCATION
    OwnerAllocationStage.TIMED_EXECUTION_ALLOCATION -> C13FirstShortfallStage.TIMED_ALLOCATION
    OwnerAllocationStage.RESIDUAL_COMPLETION -> C13FirstShortfallStage.COMPLETION
    OwnerAllocationStage.INITIAL_WEEKLY_PLACEMENT,
    OwnerAllocationStage.MANDATORY_CONTINUITY_PLACEMENT -> C13FirstShortfallStage.PLACEMENT
    OwnerAllocationStage.BOUNDED_DAY_REBALANCER -> C13FirstShortfallStage.REBALANCING
    OwnerAllocationStage.FREQUENCY_EXPANSION -> C13FirstShortfallStage.FREQUENCY_EXPANSION
    OwnerAllocationStage.POST_SPLIT_WEEKLY_REFLOW -> C13FirstShortfallStage.POST_SPLIT_REFLOW
    OwnerAllocationStage.EXACT_PRESCRIPTION_FUNDED_MATERIALIZATION -> C13FirstShortfallStage.EXACT_PRESCRIPTION_MATERIALIZATION
    OwnerAllocationStage.INITIAL_MAIN_PLACEMENT -> C13FirstShortfallStage.PLACEMENT
    OwnerAllocationStage.SPLIT_AWARE_CONTINUITY_ALLOCATION,
    OwnerAllocationStage.PROGRAM_REPAIR -> C13FirstShortfallStage.FINAL_TARGET_AUDIT
}

private fun jsonNumber(value: Number?): Any = value ?: JSONObject.NULL
private fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
    .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)

private fun rankingJson(tuple: StimulusCandidateRankingTuple?): Any = tuple?.let {
    JSONObject().put("targetCompatibleHistory", it.targetCompatibleHistory)
        .put("recentHistory", it.recentHistory).put("contextHistory", it.contextHistory)
        .put("anchorContinuity", it.anchorContinuity).put("repeatedRecentSessions", it.repeatedRecentSessions)
        .put("freeWeightCompatible", it.freeWeightCompatible).put("highConfidence", it.highConfidence)
        .put("redundant", it.redundant).put("stableKey", it.stableKey)
} ?: JSONObject.NULL

private fun dispositionJson(row: StimulusCandidateDisposition) = JSONObject()
    .put("targetId", row.targetId).put("stableKey", row.stableKey)
    .put("canonicalSelectionRole", row.canonicalSelectionRole)
    .put("directTargetCandidate", row.directTargetCandidate).put("selectionRequired", row.selectionRequired)
    .put("status", row.status.name).put("reasons", JSONArray(row.reasons.map { it.name }.sorted()))
    .put("candidateRanking", rankingJson(row.candidateRanking))
    .put("selectedInstead", row.selectedInstead?.let(::ownerJson) ?: JSONObject.NULL)
    .put("selectedInsteadRanking", rankingJson(row.selectedInsteadRanking))
    .put("firstDifferingField", row.firstDifferingField?.name ?: JSONObject.NULL)
    .put("targetCoveredBySelectedOwner", row.targetCoveredBySelectedOwner)

private fun nonSelectionJson(row: StimulusNonSelectionProvenance) = JSONObject()
    .put("omittedControlOwner", ownerJson(row.omittedControlOwner))
    .put("classification", row.classification.name)
    .put("targetEvidence", JSONArray(row.targetEvidence.sortedBy { it.targetId }.map { evidence ->
        JSONObject().put("targetId", evidence.targetId).put("classification", evidence.classification.name)
            .put("disposition", dispositionJson(evidence.disposition))
    }))

private fun changeAttributionJson(row: StimulusExperimentalChangeAttribution) = JSONObject()
    .put("owner", if (row.stableKey == null) JSONObject.NULL else JSONObject()
        .put("stableKey", row.stableKey).put("selectionRole", row.selectionRole ?: JSONObject.NULL))
    .put("source", row.source.name).put("targetIds", JSONArray(row.targetIds.sorted()))
    .put("reasonCodes", JSONArray(row.reasonCodes.sorted())).put("evidenceSources", JSONArray(row.evidenceSources.sorted()))

private fun targetOutcomeJson(row: StimulusExperimentalTargetOutcome) = JSONObject()
    .put("targetId", row.targetId).put("status", row.status.name)
    .put("reasonCodes", JSONArray(row.reasonCodes.sorted()))
    .put("controlWeeklyUnitsDistance", jsonNumber(row.controlWeeklyUnitsDistance))
    .put("experimentalWeeklyUnitsDistance", jsonNumber(row.experimentalWeeklyUnitsDistance))
    .put("controlWeeklySessionsDistance", jsonNumber(row.controlWeeklySessionsDistance))
    .put("experimentalWeeklySessionsDistance", jsonNumber(row.experimentalWeeklySessionsDistance))

private fun prescriptionJson(value: PlannedPrescription?): Any = value?.let { prescription ->
    JSONObject().put("text", prescription.text).put("restSeconds", prescription.restSeconds)
        .put("weightSource", prescription.weightSource)
        .put("sets", JSONArray(prescription.sets.map { set -> JSONObject()
            .put("setIndex", set.setIndex).put("reps", set.reps).put("weightKg", jsonNumber(set.weightKg))
            .put("seconds", set.seconds).put("targetRpeMin", jsonNumber(set.targetRpeMin))
        }))
} ?: JSONObject.NULL

private data class C13OwnerQualityIdentity(
    val caseId: String,
    val owner: StimulusPrescriptionOwnerIdentity,
    val quality: TrainableQuality
)

private data class C13SetClassification(
    val direct: Int = 0,
    val supportive: Int = 0,
    val incompatibleDirect: Int = 0,
    val incompatibleSupportive: Int = 0
)

private fun classifyFinalOwnerSets(
    comparison: StimulusSelectionProgramComparison,
    catalog: CanonicalExercisePhysicalQualityCatalog,
    owner: StimulusPrescriptionOwnerIdentity,
    quality: TrainableQuality
): C13SetClassification {
    var direct = 0
    var supportive = 0
    var incompatibleDirect = 0
    var incompatibleSupportive = 0
    comparison.experimental.items.filter {
        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
    }.forEach { item ->
        val relations = catalog.relations(owner.stableKey).filter { it.qualityId == quality }
        if (relations.isEmpty()) return@forEach
        val isDirect = relations.any { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
        val isSupportive = !isDirect && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
        item.setPrescriptions.forEach { set ->
            val compatible = quality !in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) ||
                prescriptionShapeCompatible(quality, owner.stableKey, set.reps)
            when {
                isDirect && compatible -> direct++
                isDirect -> incompatibleDirect++
                isSupportive && compatible -> supportive++
                isSupportive -> incompatibleSupportive++
            }
        }
    }
    return C13SetClassification(direct, supportive, incompatibleDirect, incompatibleSupportive)
}

internal fun renderC13CanonicalQualityGapCensus(
    records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
    standardCoverageReport: String,
    c12MergeHead: String,
    c13StartHead: String,
    canonicalPlanningByCase: Map<String, CanonicalStimulusPlanningResult>,
    catalog: CanonicalExercisePhysicalQualityCatalog
): String {
    val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }.sortedBy { it.first.label }
    generated.forEach { (spec, result) ->
        val comparison = requireNotNull(result.comparison)
        TrainableQuality.entries.forEach { quality ->
            val identities = comparison.experimental.items.map {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
            }.distinct().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            val classified = identities.map { classifyFinalOwnerSets(comparison, catalog, it, quality) }
            val audit = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == quality }
            val horizon = comparison.experimentalAudit?.planningHorizonWeeks ?: 0
            val auditedDirectSets = audit?.plannedWeeklyDirectUnits?.times(horizon)
            if (auditedDirectSets != null) {
                check(classified.sumOf { it.direct }.toDouble() == auditedDirectSets) {
                    "${spec.label} C13 owner compatible-direct classification differs from final EXPERIMENTAL audit for $quality: " +
                        "owner=${classified.sumOf { it.direct }} audit=$auditedDirectSets"
                }
            }
        }
    }
    val allCaseJson = generated.map { (spec, result) ->
        val comparison = requireNotNull(result.comparison)
        val decision = comparison.experimental.personalizedDecision
        val canonicalPlanning = requireNotNull(canonicalPlanningByCase[spec.label]) {
            "${spec.label} missing CONTROL-independent canonical B1-B4 recomputation"
        }
        check(canonicalPlanning.targetPlan == comparison.targetPlan) {
            "${spec.label} canonical prepared-input target plan differs from the live comparison target plan"
        }
        val needProfile = canonicalPlanning.athleteStimulusNeedProfile
        val b2 = canonicalPlanning.qualityDoseHistory
        val b3 = canonicalPlanning.decisionPortfolio
        val b7 = requireNotNull(comparison.experimentalReadinessAudit)
        val c12 = observeC12Case(result)
        val selectedRows = comparison.selectionPlan.selectedCandidates.flatMap { candidate ->
            candidate.coveredTargetIds.sorted().map { targetId ->
                JSONObject().put("targetId", targetId).put("owner", ownerJson(
                    StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)))
                    .put("primaryTargetId", candidate.primaryTargetId)
                    .put("selectionReasons", JSONArray(candidate.selectionReasons.sorted()))
                    .put("probePrescriptionCompatibility", candidate.probePrescriptionCompatibility.name)
                    .put("targetSetsFromExistingPrescription", candidate.targetSetsFromExistingPrescription)
            }
        }.sortedWith(compareBy({ it.optString("targetId") }, { it.getJSONObject("owner").optString("stableKey") },
            { it.getJSONObject("owner").optString("selectionRole") }))
        val targetRows = buildList {
            comparison.targetPlan.qualityTargets.sortedBy { it.quality.name }.forEach { target ->
                val targetId = "QUALITY:${target.quality.name}"
                val need = needProfile?.qualityNeeds?.firstOrNull { it.quality == target.quality }
                val history = b2?.bands?.get(target.quality)
                val portfolio = b3?.qualityDecisions?.firstOrNull { it.quality == target.quality }
                val audit = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == target.quality }
                val horizon = comparison.experimentalAudit?.planningHorizonWeeks ?: 0
                val finalSetClassifications = comparison.experimental.items.map {
                    StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
                }.distinct().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                    .map { it to classifyFinalOwnerSets(comparison, catalog, it, target.quality) }
                val finalDirectUnits = finalSetClassifications.sumOf { it.second.direct }
                val finalSupportiveUnits = finalSetClassifications.sumOf { it.second.supportive }
                val finalIncompatibleDirectUnits = finalSetClassifications.sumOf { it.second.incompatibleDirect }
                val finalIncompatibleSupportiveUnits = finalSetClassifications.sumOf { it.second.incompatibleSupportive }
                val finalDirectSessions = audit?.plannedWeeklyDirectSessions?.times(horizon)
                val auditDirectUnits = audit?.plannedWeeklyDirectUnits?.times(horizon)
                if (auditDirectUnits != null) check(kotlin.math.abs(finalDirectUnits - auditDirectUnits) < 1e-8) {
                    "${spec.label} C13 owner compatible-direct classification differs from final EXPERIMENTAL audit for $targetId: " +
                        "owner=$finalDirectUnits audit=$auditDirectUnits"
                }
                val outcome = b7.targetOutcomes.firstOrNull { it.targetId == targetId }
                val selected = comparison.selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }
                    .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                val selectedOwners = selected.map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                val authorizations = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { row ->
                    row.targetId == targetId && (row.owner == null || selectedOwners.any { owner ->
                        owner.stableKey == row.owner.stableKey && owner.selectionRole == row.owner.selectionRole
                    })
                }.sortedWith(compareBy({ it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() }))
                val resolutions = comparison.prescriptionRealizationPlan?.resolutions.orEmpty().filter { it.targetId == targetId }
                    .sortedWith(compareBy({ it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() }))
                val materializations = comparison.prescriptionMaterializationAudits.filter { it.targetId == targetId }
                    .sortedWith(compareBy({ it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() }))
                val events = decision?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty()
                    .filter { event -> event.owner in selectedOwners }
                    .deterministicOwnerOrder()
                val ownerRows = selected.map { candidate ->
                    val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                    val setClassification = classifyFinalOwnerSets(comparison, catalog, identity, target.quality)
                    val rows = comparison.experimental.items.filter {
                        it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val ownerAuth = authorizations.firstOrNull { row -> row.owner?.let {
                        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    } == true }
                    val resolution = resolutions.firstOrNull { row -> row.owner?.let {
                        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    } == true }
                    val material = materializations.firstOrNull { row -> row.owner?.let {
                        it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                    } == true }
                    JSONObject().put("owner", ownerJson(identity))
                        .put("selectionRole", candidate.selectionRole)
                        .put("ownerQualityAuthorized", ownerAuth?.let { it.status in C13_EXECUTABLE_AUTHORIZATION_STATUSES &&
                            it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && it.authorizedPrescription != null } ?: false)
                        .put("authorization", ownerAuth?.let { authorizationJson(it) } ?: JSONObject.NULL)
                        .put("resolution", resolution?.let { resolutionJson(it) } ?: JSONObject.NULL)
                        .put("materialization", material?.let { materializationJson(it) } ?: JSONObject.NULL)
                        .put("finalSetClassification", JSONObject().put("direct", setClassification.direct)
                            .put("supportive", setClassification.supportive)
                            .put("incompatibleDirect", setClassification.incompatibleDirect)
                            .put("incompatibleSupportive", setClassification.incompatibleSupportive))
                        .put("experimentalRows", JSONArray(rows.map { row -> JSONObject()
                            .put("week", row.weekNumber).put("day", row.dayOfWeek).put("order", row.orderIndex)
                            .put("setCount", row.setCount).put("setPrescriptions", JSONArray(row.setPrescriptions.map { set ->
                                JSONObject().put("reps", set.reps).put("weightKg", jsonNumber(set.weightKg))
                                    .put("seconds", set.seconds).put("targetRpeMin", jsonNumber(set.targetRpeMin))
                            })).put("prescription", row.prescription)
                        }))
                }
                val primarySelected = selected.firstOrNull()
                val primaryIdentity = primarySelected?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                val capacityEvents = events.filter { event ->
                    event.owner == primaryIdentity && targetId in event.targetIds &&
                        event.action == OwnerAllocationAction.SET_COUNT_REDUCED && event.cause in setOf(
                        OwnerAllocationCause.CAPACITY_LIMIT, OwnerAllocationCause.CAPACITY_SHARE_ALLOCATION,
                        OwnerAllocationCause.SESSION_TIME_LIMIT
                    ) && event.before != null && event.after != null && event.before.setCount > event.after.setCount
                }
                val primaryAuth = authorizations.firstOrNull { row -> row.owner?.let { owner ->
                    owner.stableKey == primaryIdentity?.stableKey && owner.selectionRole == primaryIdentity.selectionRole
                } == true } ?: authorizations.firstOrNull {
                    it.owner == null && it.status == StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE
                }
                val primaryResolution = resolutions.firstOrNull { row -> row.owner?.let { owner ->
                    owner.stableKey == primaryIdentity?.stableKey && owner.selectionRole == primaryIdentity.selectionRole
                } == true } ?: resolutions.firstOrNull { it.owner == null }
                val primaryMaterialization = materializations.firstOrNull { row -> row.owner?.let { owner ->
                    owner.stableKey == primaryIdentity?.stableKey && owner.selectionRole == primaryIdentity.selectionRole
                } == true }
                val numericRange = target.weeklyDirectUnitsTarget
                val numericSessions = target.weeklyDirectSessionsTarget
                val directUnits = audit?.plannedWeeklyDirectUnits
                val directSessions = audit?.plannedWeeklyDirectSessions
                val unitWithin = numericRange?.let { directUnits != null && directUnits >= it.min && directUnits <= it.max }
                val sessionWithin = numericSessions?.let { directSessions != null && directSessions >= it.min && directSessions <= it.max }
                val root = classifyC13TargetGap(C13TargetGapFacts(
                    selectedOwnerExists = selected.isNotEmpty(),
                    realizationStatus = primaryResolution?.status,
                    reasonCodes = (primaryAuth?.reasonCodes.orEmpty() + primaryResolution?.reasonCodes.orEmpty() +
                        primaryResolution?.plannedCompatibility?.reasonCodes.orEmpty()).toSet(),
                    authorizationStatus = primaryAuth?.status,
                    executionAuthority = primaryAuth?.executionAuthority,
                    authorizedPrescriptionExists = primaryAuth?.authorizedPrescription != null,
                    materializationState = primaryMaterialization?.state,
                    numericAuthority = target.numericAuthority,
                    strategy = target.strategy,
                    outcomeStatus = outcome?.status,
                    targetUnmet = outcome?.reasonCodes?.contains("TARGET_UNMET") == true,
                    finalUnitsStatus = audit?.weeklyDirectUnitsStatus,
                    finalSessionsStatus = audit?.weeklyDirectSessionsStatus,
                    finalIncompatibleDirectUnits = finalIncompatibleDirectUnits,
                    exactCapacityReduction = capacityEvents.isNotEmpty(),
                    exactCapacityReductionStage = capacityEvents.firstOrNull()?.stage,
                    numericFinalUnitsWithinTarget = unitWithin,
                    numericFinalSessionsWithinTarget = sessionWithin,
                    ownerCompetesAcrossTargets = primaryIdentity != null && comparison.selectionPlan.selectedCandidates
                        .firstOrNull { it.stableKey == primaryIdentity.stableKey && it.selectionRole == primaryIdentity.selectionRole }
                        ?.coveredTargetIds.orEmpty().size > 1
                ))
                val targetGap = outcome?.reasonCodes?.contains("TARGET_UNMET") == true ||
                    outcome?.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED
                val finalNumericDistance = when {
                    directUnits == null || numericRange == null -> null
                    directUnits < numericRange.min -> numericRange.min - directUnits
                    directUnits > numericRange.max -> directUnits - numericRange.max
                    else -> 0.0
                }
                val finalSessionDistance = when {
                    directSessions == null || numericSessions == null -> null
                    directSessions < numericSessions.min -> numericSessions.min - directSessions
                    directSessions > numericSessions.max -> directSessions - numericSessions.max
                    else -> 0.0
                }
                add(JSONObject().put("targetId", targetId).put("targetKind", "QUALITY")
                    .put("quality", target.quality.name)
                    .put("B1_need", need?.let { JSONObject().put("relevance", it.relevance.name)
                        .put("decision", it.decision.name).put("confidence", it.confidence.name)
                        .put("directUnits28d", it.exposure.current28d.directUnits)
                        .put("supportiveUnits28d", it.exposure.current28d.supportiveUnits)
                        .put("currentExposure", it.exposure.currentExposure.name)
                        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                    .put("B2_baseline", JSONObject().put("source", history?.source?.name ?: JSONObject.NULL)
                        .put("directUnitsMedian", jsonNumber(history?.directUnitsMedian))
                        .put("directSessionsMedian", jsonNumber(history?.directSessionsMedian))
                        .put("eligibleWeeks", history?.eligibleWeekCount ?: 0)
                        .put("observability", b2?.baselineObservability?.get(target.quality)?.name ?: JSONObject.NULL)
                        .put("comparisonStatus", b2?.comparisons?.get(target.quality)?.status?.name ?: JSONObject.NULL)
                        .put("directWeeklyEvidence", JSONArray(b2?.weeklyEvidence?.get(target.quality).orEmpty().map { week -> JSONObject()
                            .put("start", week.start.toString()).put("end", week.end.toString())
                            .put("eligible", week.eligibleForNumericBaseline).put("directUnits", week.directUnits)
                            .put("classificationComplete", week.classificationComplete)
                        })))
                    .put("B3_strategy", portfolio?.let { JSONObject().put("needDecision", it.needDecision.name)
                        .put("strategy", it.strategy.name).put("baselineDirectUnitsMedian", jsonNumber(it.baselineDirectUnitsMedian))
                        .put("baselineEligibleWeeks", it.baselineEligibleWeekCount)
                        .put("baselineObservability", it.baselineObservability.name)
                        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                    .put("B4_target", JSONObject().put("strategy", target.strategy.name)
                        .put("numericAuthority", target.numericAuthority.name).put("baselineSource", target.baselineSource?.name ?: JSONObject.NULL)
                        .put("baselineAvailable", target.baselineAvailable).put("hasPersonalDirectBaseline", target.hasPersonalDirectBaseline)
                        .put("numericBaselineUsable", target.numericBaselineUsable)
                        .put("minimum", jsonNumber(numericRange?.min)).put("preferred", jsonNumber(numericRange?.preferred))
                        .put("maximum", jsonNumber(numericRange?.max))
                        .put("sessionMinimum", jsonNumber(numericSessions?.min)).put("sessionPreferred", jsonNumber(numericSessions?.preferred))
                        .put("sessionMaximum", jsonNumber(numericSessions?.max))
                        .put("reasonCodes", JSONArray(target.reasonCodes.sorted())))
                    .put("B5_selectedOwners", JSONArray(ownerRows.map { it.getJSONObject("owner") }))
                    .put("B5_selectionRequired", comparison.selectionPlan.traces.firstOrNull { it.targetId == targetId }?.selectionRequired)
                    .put("B6_authorizations", JSONArray(authorizations.map(::authorizationJson)))
                    .put("B6_realizations", JSONArray(resolutions.map(::resolutionJson)))
                    .put("B6_materializations", JSONArray(materializations.map(::materializationJson)))
                    .put("B6_primaryEvidence", JSONObject()
                        .put("owner", primaryIdentity?.let(::ownerJson) ?: JSONObject.NULL)
                        .put("authorizationStatus", primaryAuth?.status?.name ?: JSONObject.NULL)
                        .put("executionAuthority", primaryAuth?.executionAuthority?.name ?: JSONObject.NULL)
                        .put("authorizedPrescription", prescriptionJson(primaryAuth?.authorizedPrescription))
                        .put("authorizationReasons", JSONArray(primaryAuth?.reasonCodes.orEmpty().sorted()))
                        .put("realizationStatus", primaryResolution?.status?.name ?: JSONObject.NULL)
                        .put("realizationReasons", JSONArray(primaryResolution?.reasonCodes.orEmpty().sorted()))
                        .put("materializationState", primaryMaterialization?.state?.name ?: JSONObject.NULL)
                        .put("authorizedUnits", primaryMaterialization?.authorizedWeeklySetUnits ?: 0)
                        .put("materializedUnits", primaryMaterialization?.materializedWeeklySetUnits ?: 0)
                        .put("materializationShortfall", primaryMaterialization?.shortfall ?: 0)
                        .put("failureCause", root.b6FailureCause.name)
                        .put("incomplete", primaryAuth?.status !in C13_EXECUTABLE_AUTHORIZATION_STATUSES ||
                            primaryAuth?.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED ||
                            primaryAuth?.authorizedPrescription == null))
                    .put("C10_ownerMutationTrace", JSONArray(events.map { it.toJson() }))
                    .put("finalCanonicalRealization", JSONObject()
                        .put("directUnits", finalDirectUnits)
                        .put("supportiveUnits", finalSupportiveUnits)
                        .put("incompatibleDirectUnits", finalIncompatibleDirectUnits)
                        .put("incompatibleSupportiveUnits", finalIncompatibleSupportiveUnits)
                        .put("directSessions", jsonNumber(finalDirectSessions))
                        .put("planningHorizonWeeks", horizon)
                        .put("directUnitsPerWeek", jsonNumber(directUnits))
                        .put("directSessionsPerWeek", jsonNumber(directSessions))
                        .put("unitStatus", audit?.weeklyDirectUnitsStatus?.name ?: JSONObject.NULL)
                        .put("sessionStatus", audit?.weeklyDirectSessionsStatus?.name ?: JSONObject.NULL)
                        .put("unitsWithinB4Band", unitWithin ?: JSONObject.NULL)
                        .put("sessionsWithinB4Band", sessionWithin ?: JSONObject.NULL)
                        .put("unitDistanceOutsideBand", jsonNumber(finalNumericDistance))
                        .put("sessionDistanceOutsideBand", jsonNumber(finalSessionDistance))
                        .put("b7Outcome", outcome?.status?.name ?: JSONObject.NULL)
                        .put("b7ReasonCodes", JSONArray(outcome?.reasonCodes.orEmpty().sorted()))
                        .put("b7ControlComparatorUnitDistance", jsonNumber(outcome?.controlWeeklyUnitsDistance))
                        .put("b7ExperimentalComparatorUnitDistance", jsonNumber(outcome?.experimentalWeeklyUnitsDistance)))
                    .put("selectedOwnerFinalSetClassification", JSONArray(ownerRows.map { row ->
                        JSONObject().put("owner", row.getJSONObject("owner"))
                            .put("classification", row.getJSONObject("finalSetClassification"))
                    }))
                    .put("exactCapacityReduction", JSONArray(capacityEvents.map { it.toJson() }))
                    .put("firstShortfallStage", if (targetGap) root.firstShortfallStage.name else C13FirstShortfallStage.NONE.name)
                    .put("targetGapRootCause", if (targetGap) root.rootCause.name else JSONObject.NULL)
                    .put("b6FailureCause", root.b6FailureCause.name)
                    .put("firstShortfallEvidenceOwner", primaryIdentity?.let(::ownerJson) ?: JSONObject.NULL))
            }
            comparison.targetPlan.taskTargets.sortedBy { it.task }.forEach { target ->
                val targetId = "TASK:${target.task}"
                val outcome = b7.targetOutcomes.firstOrNull { it.targetId == targetId }
                val selected = comparison.selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }
                val taskAudit = comparison.experimentalAudit?.taskAudits?.firstOrNull { it.task == target.task }
                val taskHorizon = comparison.experimentalAudit?.planningHorizonWeeks ?: 0
                val finalTaskDirectUnits = taskAudit?.plannedDirectUnits?.times(taskHorizon)
                add(JSONObject().put("targetId", targetId).put("targetKind", "TASK").put("quality", JSONObject.NULL)
                    .put("B1_need", JSONObject.NULL).put("B2_baseline", JSONObject.NULL).put("B3_strategy", JSONObject.NULL)
                    .put("B4_target", JSONObject().put("strategy", target.strategy.name).put("numericAuthority", target.numericAuthority.name)
                        .put("minimum", jsonNumber(target.weeklyDirectUnitsTarget?.min))
                        .put("preferred", jsonNumber(target.weeklyDirectUnitsTarget?.preferred))
                        .put("maximum", jsonNumber(target.weeklyDirectUnitsTarget?.max))
                        .put("reasonCodes", JSONArray(target.reasonCodes.sorted())))
                    .put("B5_selectedOwners", JSONArray(selected.map { candidate -> ownerJson(
                        StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)) }))
                    .put("B6_authorizations", JSONArray()).put("B6_realizations", JSONArray()).put("B6_materializations", JSONArray())
                    .put("C10_ownerMutationTrace", JSONArray())
                    .put("finalCanonicalRealization", JSONObject()
                        .put("directUnits", jsonNumber(finalTaskDirectUnits)).put("supportiveUnits", JSONObject.NULL)
                        .put("planningHorizonWeeks", taskHorizon)
                        .put("directUnitsPerWeek", jsonNumber(taskAudit?.plannedDirectUnits))
                        .put("status", taskAudit?.status?.name ?: JSONObject.NULL)
                        .put("b7Outcome", outcome?.status?.name ?: JSONObject.NULL)
                        .put("b7ReasonCodes", JSONArray(outcome?.reasonCodes.orEmpty().sorted())))
                    .put("firstShortfallStage", JSONObject.NULL).put("targetGapRootCause", JSONObject.NULL)
                    .put("b6FailureCause", JSONObject.NULL))
            }
        }
        JSONObject().put("caseId", spec.label)
            .put("route", result.routeDecision.selectedSource.name)
            .put("B1ThroughB3ProfileSource", "CONTROL_FREE_PREPARED_INPUT_RECOMPUTATION")
            .put("B4TargetSource", "comparison.targetPlan")
            .put("canonicalPlanningProfileAvailable", true)
            .put("canonicalTargetPlanRecomputedMatchesLive", true)
            .put("c12PrimaryClassification", c12.classification.primaryClassification.name)
            .put("b7Status", b7.status.name)
            .put("b7Reasons", JSONArray(b7.reasonCodes.sorted()))
            .put("b7Integrity", JSONObject().put("materializationIntegrityPassed", b7.materializationIntegrityPassed)
                .put("changeProvenanceClosed", b7.changeProvenanceClosed)
                .put("collateralRegressionFree", b7.collateralRegressionFree)
                .put("targetOutcomes", JSONArray(b7.targetOutcomes.sortedBy { it.targetId }.map(::targetOutcomeJson)))
                .put("changeAttributions", JSONArray(b7.changeAttributions.sortedWith(compareBy(
                    { it.stableKey.orEmpty() }, { it.selectionRole.orEmpty() }, { it.source.name }
                )).map(::changeAttributionJson))))
            .put("b8Status", comparison.productionCutoverAuthority?.status?.name ?: JSONObject.NULL)
            .put("b8Scope", comparison.productionCutoverAuthority?.scope?.name ?: JSONObject.NULL)
            .put("b8Reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
            .put("b8AuthorizedOwners", JSONArray(comparison.productionCutoverAuthority?.authorizedOwnerIdentities.orEmpty()
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
            .put("B5_nonSelectionProvenance", JSONArray(comparison.nonSelectionProvenance
                .sortedWith(compareBy({ it.omittedControlOwner.stableKey }, { it.omittedControlOwner.selectionRole }))
                .map(::nonSelectionJson)))
            .put("B5_selectedCandidateDispositions", JSONArray(comparison.selectionPlan.candidateDispositionIndex.entries
                .filter { disposition -> comparison.selectionPlan.selectedCandidates.any { selected ->
                    disposition.targetId in selected.coveredTargetIds && disposition.stableKey == selected.stableKey &&
                        disposition.canonicalSelectionRole == selected.selectionRole
                } }
                .sortedWith(compareBy({ it.targetId }, { it.stableKey }, { it.canonicalSelectionRole }))
                .map(::dispositionJson)))
            .put("controlOwnerIdentities", JSONArray(comparison.controlOwnerIdentities
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
            .put("experimentalOwnerIdentities", JSONArray(comparison.experimentalOwnerIdentities
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
            .put("addedOwnerIdentities", JSONArray(comparison.addedOwnerIdentities
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
            .put("removedOwnerIdentities", JSONArray(comparison.removedOwnerIdentities
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
            .put("buildAccounting", JSONObject().put("control", result.buildCounts.controlBuilds)
                .put("experimental", result.buildCounts.experimentalBuilds)
                .put("total", result.buildCounts.totalBuildInvocations).put("third", result.buildCounts.thirdBuilds))
            .put("selectedOwnerTargets", JSONArray(selectedRows))
            .put("targets", JSONArray(targetRows))
    }
        val ownerQuality = generated.flatMap { (spec, result) ->
            val comparison = requireNotNull(result.comparison)
            comparison.selectionPlan.selectedCandidates.flatMap { candidate ->
                candidate.coveredTargetIds.mapNotNull { targetId ->
                    val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == targetId }
                        ?: return@mapNotNull null
                    C13OwnerQualityIdentity(spec.label, StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole), target.quality)
                }
            }
        }
        val exactOwnerQuality = ownerQuality.distinct().size
        val qualityCounts = JSONObject()
        TrainableQuality.entries.sortedBy { it.name }.forEach { quality ->
            val requestedTargetCount = generated.sumOf { (_, result) ->
                requireNotNull(result.comparison).targetPlan.qualityTargets.count { target ->
                    target.quality == quality && target.strategy != StimulusDoseStrategy.NO_MINIMUM_TARGET &&
                        target.numericAuthority != StimulusTargetNumericAuthority.NONE
                }
            }
            var selectedCount = 0
            var authorizedCount = 0
            var materializedCount = 0
            var realizedCompatibleCount = 0
            var targetPassCount = 0
            var materializedCompatibleCount = 0
            var materializedTargetPassCount = 0
            generated.forEach { (_, result) ->
                val comparison = requireNotNull(result.comparison)
                val targetId = "QUALITY:${quality.name}"
                comparison.selectionPlan.selectedCandidates.filter { targetId in it.coveredTargetIds }.forEach { candidate ->
                    selectedCount++
                    val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                    val auth = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().firstOrNull { it.targetId == targetId &&
                        it.owner?.let { row -> row.stableKey == owner.stableKey && row.selectionRole == owner.selectionRole } == true }
                    if (auth?.status in C13_EXECUTABLE_AUTHORIZATION_STATUSES &&
                        auth?.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && auth.authorizedPrescription != null) {
                        authorizedCount++
                    }
                    val material = comparison.prescriptionMaterializationAudits.firstOrNull { it.targetId == targetId &&
                        it.owner?.let { row -> row.stableKey == owner.stableKey && row.selectionRole == owner.selectionRole } == true }
                    val fullyMaterialized = material?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED &&
                        material.shortfall == 0
                    if (fullyMaterialized) materializedCount++
                    val target = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == quality }
                    val audit = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == quality }
                    val compatible = when (target?.numericAuthority) {
                        StimulusTargetNumericAuthority.DIRECTION_ONLY -> audit?.weeklyDirectUnitsStatus == StimulusTargetControlStatus.DIRECT_PRESENT
                        StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                        StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE ->
                            audit?.weeklyDirectUnitsStatus == StimulusTargetControlStatus.WITHIN_BAND &&
                                audit.weeklyDirectSessionsStatus == StimulusTargetControlStatus.WITHIN_BAND
                        else -> false
                    }
                    if (compatible) realizedCompatibleCount++
                    val outcome = comparison.experimentalReadinessAudit?.targetOutcomes?.firstOrNull { it.targetId == targetId }
                    if (outcome?.status == StimulusExperimentalTargetOutcomeStatus.IMPROVED ||
                        (outcome?.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED &&
                            "TARGET_UNMET" !in outcome.reasonCodes)) targetPassCount++
                    if (fullyMaterialized && compatible) materializedCompatibleCount++
                    if (fullyMaterialized && (outcome?.status == StimulusExperimentalTargetOutcomeStatus.IMPROVED ||
                            (outcome?.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED &&
                                "TARGET_UNMET" !in outcome.reasonCodes))) materializedTargetPassCount++
                }
            }
            qualityCounts.put(quality.name, JSONObject().put("B4TargetRequested", requestedTargetCount)
                .put("selectedOwnerQuality", selectedCount)
                .put("B6ExecutableExact", authorizedCount).put("fullyMaterialized", materializedCount)
                .put("programTargetCompatibleIndependentOfB6", realizedCompatibleCount)
                .put("programB7TargetOutcomeAcceptedIndependentOfB6", targetPassCount)
                .put("finalTargetCompatibleAfterFullMaterialization", materializedCompatibleCount)
                .put("B7TargetOutcomeAcceptedAfterFullMaterialization", materializedTargetPassCount))
        }
        val c12PrimaryCounts = generated.groupingBy { observeC12Case(it.second).classification.primaryClassification.name }.eachCount()
        val targetUnmetCases = generated.filter { observeC12Case(it.second).classification.primaryClassification ==
            C12PrimaryClassification.TARGET_NOT_SATISFIED }
        val b6PrimaryCases = generated.filter { observeC12Case(it.second).classification.primaryClassification ==
            C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE }
        val targetFindingRows = allCaseJson.flatMap { case ->
            val label = case.getString("caseId")
            case.getJSONArray("targets").let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }
                .filter { it.optString("targetGapRootCause") != "" && !it.isNull("targetGapRootCause") }
                .map { target -> JSONObject().put("caseId", label).put("target", target) }
            }
        }
        val primaryB6OwnerTargetRows = b6PrimaryCases.flatMap { (spec, _) ->
            val case = allCaseJson.single { it.getString("caseId") == spec.label }
            val targets = case.getJSONArray("targets")
            (0 until targets.length()).map { targets.getJSONObject(it) }
                .filter { it.getString("targetKind") == "QUALITY" && it.getJSONArray("B5_selectedOwners").length() > 0 }
                .map { target -> JSONObject().put("caseId", spec.label).put("targetId", target.getString("targetId"))
                    .put("quality", target.getString("quality")).put("primaryEvidence", target.getJSONObject("B6_primaryEvidence")) }
        }
        val primaryB6FailedOwnerTargetRows = primaryB6OwnerTargetRows.filter {
            it.getJSONObject("primaryEvidence").getBoolean("incomplete")
        }
        val b6PrimaryFailureCauseCounts = JSONObject().apply {
            C13B6FailureCause.entries.sortedBy { it.name }.forEach { cause ->
                val rows = primaryB6FailedOwnerTargetRows.filter {
                    it.getJSONObject("primaryEvidence").getString("failureCause") == cause.name
                }
                put(cause.name, JSONObject().put("ownerTargetRows", rows.size)
                    .put("cases", rows.map { it.getString("caseId") }.distinct().size)
                    .put("caseIds", JSONArray(rows.map { it.getString("caseId") }.distinct().sorted())))
            }
        }
        val rootCauseCounts = JSONObject().apply {
            C13TargetGapRootCause.entries.sortedBy { it.name }.forEach { cause ->
                put(cause.name, targetFindingRows.count { it.getJSONObject("target").getString("targetGapRootCause") == cause.name })
            }
        }
        val shortfallStageCounts = JSONObject().apply {
            C13FirstShortfallStage.entries.sortedBy { it.name }.forEach { stage ->
                put(stage.name, targetFindingRows.count { it.getJSONObject("target").getString("firstShortfallStage") == stage.name })
            }
        }
        val exerciseRows = generated.flatMap { (spec, result) ->
            val comparison = requireNotNull(result.comparison)
            comparison.selectionPlan.selectedCandidates.flatMap { candidate ->
                candidate.coveredTargetIds.mapNotNull { targetId ->
                    val target = comparison.targetPlan.qualityTargets.firstOrNull { "QUALITY:${it.quality.name}" == targetId }
                        ?: return@mapNotNull null
                    val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                    val auth = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().firstOrNull { it.targetId == targetId &&
                        it.owner?.let { row -> row.stableKey == owner.stableKey && row.selectionRole == owner.selectionRole } == true }
                    val outcome = comparison.experimentalReadinessAudit?.targetOutcomes?.firstOrNull { it.targetId == targetId }
                    JSONObject().put("caseId", spec.label).put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                        .put("quality", target.quality.name).put("selected", true)
                        .put("B6Status", auth?.status?.name ?: JSONObject.NULL)
                        .put("B6Executable", auth?.let { it.status in C13_EXECUTABLE_AUTHORIZATION_STATUSES &&
                            it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && it.authorizedPrescription != null } ?: false)
                        .put("targetOutcome", outcome?.status?.name ?: JSONObject.NULL)
                        .put("targetUnmet", outcome?.reasonCodes?.contains("TARGET_UNMET") == true)
                }
            }
        }.groupBy { it.getString("stableKey") to it.getString("quality") }.toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .map { (key, rows) -> JSONObject().put("stableKey", key.first).put("quality", key.second)
                .put("selectedCases", rows.size).put("authorizedCases", rows.count { it.getBoolean("B6Executable") })
                .put("failedB6Cases", rows.count { !it.getBoolean("B6Executable") })
                .put("targetUnmetCases", rows.count { it.getBoolean("targetUnmet") })
                .put("caseIds", JSONArray(rows.map { it.getString("caseId") }.sorted()))
            }
        val zeroRestoreCases = generated.flatMap { (spec, result) ->
            requireNotNull(result.comparison).targetPlan.qualityTargets.filter { target ->
                target.strategy == StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE &&
                    target.numericAuthority == StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE &&
                    target.weeklyDirectUnitsTarget?.preferred == 0.0
            }.map { target -> JSONObject().put("caseId", spec.label).put("quality", target.quality.name)
                .put("baselineDirectUnitsMedian", jsonNumber(canonicalPlanningByCase[spec.label]
                    ?.qualityDoseHistory?.bands?.get(target.quality)?.directUnitsMedian))
                .put("baselineSource", target.baselineSource?.name ?: JSONObject.NULL)
                .put("eligibleWeeks", canonicalPlanningByCase[spec.label]
                    ?.qualityDoseHistory?.bands?.get(target.quality)?.eligibleWeekCount ?: 0)
                .put("minimum", target.weeklyDirectUnitsTarget?.min).put("preferred", target.weeklyDirectUnitsTarget?.preferred)
                .put("maximum", target.weeklyDirectUnitsTarget?.max)
            }
        }
        val unitName = C13TargetGapRootCause.entries.associateBy { it.name }
        val output = JSONObject().put("schema", "c13-canonical-quality-gap-census-v1")
            .put("c12MergeMainHead", c12MergeHead).put("c13StartHead", c13StartHead)
            .put("implementationCommit", JSONObject.NULL).put("finalHead", JSONObject.NULL)
            .put("standardCoverageSha256", java.security.MessageDigest.getInstance("SHA-256")
                .digest(standardCoverageReport.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) })
            .put("corpus", JSONObject().put("totalCases", records.size).put("generated", generated.size)
                .put("preflightRejected", records.size - generated.size)
                .put("preflightRejections", JSONArray(records.filter { it.second == null }.sortedBy { it.first.label }.map { (spec, _) ->
                    JSONObject().put("caseId", spec.label).put("history", spec.history)
                        .put("reason", "PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY")
                        .put("B4ThroughB9Executed", false)
                        .put("buildAccounting", JSONObject().put("control", 0).put("experimental", 0)
                            .put("total", 0).put("third", 0))
                }))
                .put("routes", JSONObject().put("CONTROL", generated.count { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL })
                    .put("B8_STRENGTH_V1", generated.count { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.B8_STRENGTH_V1 })
                    .put("B8_HYPERTROPHY_V1", generated.count { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.B8_HYPERTROPHY_V1 })
                    .put("B8_STRENGTH_HYPERTROPHY_V1", generated.count { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1 }))
                .put("buildAccounting", JSONArray(generated.map { it.second.buildCounts }.distinct().map { counts -> JSONObject()
                    .put("control", counts.controlBuilds).put("experimental", counts.experimentalBuilds)
                    .put("total", counts.totalBuildInvocations).put("third", counts.thirdBuilds) })))
            .put("C12PrimaryClassificationCounts", JSONObject().apply { C12PrimaryClassification.entries.sortedBy { it.name }.forEach {
                put(it.name, c12PrimaryCounts[it.name] ?: 0)
            } })
            .put("caseCohorts", JSONObject().put("targetNotSatisfiedPrimaryCases", JSONArray(targetUnmetCases.map { it.first.label }.sorted()))
                .put("B6AuthorityIncompletePrimaryCases", JSONArray(b6PrimaryCases.map { it.first.label }.sorted()))
                .put("targetNotSatisfiedPrimaryCount", targetUnmetCases.size)
                .put("B6AuthorityIncompletePrimaryCount", b6PrimaryCases.size)
                .put("overlap", JSONArray((targetUnmetCases.map { it.first.label }.toSet() intersect b6PrimaryCases.map { it.first.label }.toSet()).sorted()))
                .put("targetUnmetOnly", targetUnmetCases.map { it.first.label }.toSet().minus(b6PrimaryCases.map { it.first.label }.toSet()).size)
                .put("B6IncompleteOnly", b6PrimaryCases.map { it.first.label }.toSet().minus(targetUnmetCases.map { it.first.label }.toSet()).size))
            .put("targetGapRootCauseCounts", rootCauseCounts).put("firstShortfallStageCounts", shortfallStageCounts)
            .put("primaryB6OwnerTargetRows", primaryB6OwnerTargetRows.size)
            .put("primaryB6FailedOwnerTargetRows", primaryB6FailedOwnerTargetRows.size)
            .put("primaryB6FailureCauseCounts", b6PrimaryFailureCauseCounts)
            .put("selectedOwnerQualityFunnel", JSONObject().put("selectedCaseOwnerQualityCount", exactOwnerQuality)
                .put("rows", qualityCounts).put("finalDirectCompatibleOwnerRows", ownerQuality.count { identity ->
                    val result = generated.firstOrNull { it.first.label == identity.caseId }?.second
                    val resultRows = result?.comparison?.let { comparison ->
                        val targetId = "QUALITY:${identity.quality.name}"
                        val candidate = comparison.selectionPlan.selectedCandidates.firstOrNull { identity.owner.stableKey == it.stableKey &&
                            identity.owner.selectionRole == it.selectionRole && targetId in it.coveredTargetIds }
                        if (candidate == null) null else classifyFinalOwnerSets(comparison, catalog, identity.owner, identity.quality).direct
                    }
                    (resultRows ?: 0) > 0
                }))
            .put("exerciseQualityRepetition", JSONArray(exerciseRows))
            .put("zeroPreferredRestoreTargets", JSONArray(zeroRestoreCases.sortedBy { it.getString("caseId") }))
            .put("targetGapDossiers", JSONArray(targetFindingRows.sortedWith(compareBy({ it.getString("caseId") },
                { it.getJSONObject("target").getString("targetId") }))))
            .put("primaryB6Dossiers", JSONArray(b6PrimaryCases.map { (spec, result) ->
                val c = requireNotNull(result.comparison)
                val relatedTargets = allCaseJson.single { it.getString("caseId") == spec.label }.getJSONArray("targets")
                JSONObject().put("caseId", spec.label).put("route", result.routeDecision.selectedSource.name)
                    .put("C12Primary", C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE.name)
                    .put("selectedOwnerTargetEvidence", JSONArray((0 until relatedTargets.length()).map { relatedTargets.getJSONObject(it) }
                        .filter { it.getJSONArray("B5_selectedOwners").length() > 0 }))
                    .put("B7Status", c.experimentalReadinessAudit?.status?.name ?: JSONObject.NULL)
                    .put("B7Reasons", JSONArray(c.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                    .put("B8Reasons", JSONArray(c.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                    .put("B6ActuallyIncompleteForSelectedTarget", c.selectionPlan.selectedCandidates.any { candidate ->
                        candidate.coveredTargetIds.any { targetId ->
                            if (!targetId.startsWith("QUALITY:")) return@any false
                            val auth = c.prescriptionAuthorizationPlan?.authorizations.orEmpty().firstOrNull { row ->
                                row.targetId == targetId && row.owner?.let { it.stableKey == candidate.stableKey && it.selectionRole == candidate.selectionRole } == true
                            }
                            auth?.status !in C13_EXECUTABLE_AUTHORIZATION_STATUSES ||
                                auth?.executionAuthority != StimulusPrescriptionExecutionAuthority.FULLY_ENCODED || auth.authorizedPrescription == null
                        }
                    })
            }))
            .put("targets", JSONArray(allCaseJson.flatMap { case ->
                val rows = case.getJSONArray("targets")
                (0 until rows.length()).map { rows.getJSONObject(it) }
            }))
            .put("cases", JSONArray(allCaseJson))
            .put("phaseOrdering", JSONArray(listOf(
                "B1-B6", "EXPERIMENTAL", "CONTROL", "COMPARISON", "B7", "B8", "B9"
            )))
            .put("classificationNotes", JSONArray(listOf(
                "A target shortfall is measured against the canonical B4 band and final EXPERIMENTAL audit; CONTROL distances are comparator-only context.",
                "Direction-only targets have no numeric unit minimum. Their final outcome is direct-presence only.",
                "Per-owner set classifications use exact final EXPERIMENTAL rows and canonical catalog relations; compatible direct totals are cross-checked against the live EXPERIMENTAL target audit. Compatibility mirrors attached to the generated decision are not used as the final-program source.",
                "B1-B3 diagnostics are independently recomputed from the same prepared canonical inputs through the service's CONTROL-free B1-B4 audit seam; B1 uses the canonical need profile, B2 the canonical dose-history result, B3 the canonical decision portfolio, and each recomputed B4 target plan must equal the live comparison target plan.",
                "C12 case-level B6 primary labels are retained as a cohort; C13 owner-target evidence independently checks whether B6 is actually missing for the selected identity."
            )))
        return output.toString(2) + "\n"
    }

private fun authorizationJson(row: StimulusPrescriptionAuthorization) = JSONObject()
    .put("targetId", row.targetId).put("quality", row.quality?.name ?: JSONObject.NULL)
    .put("owner", row.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) } ?: JSONObject.NULL)
    .put("source", row.source?.name ?: JSONObject.NULL).put("status", row.status.name)
    .put("executionAuthority", row.executionAuthority.name).put("authorizedPrescription", prescriptionJson(row.authorizedPrescription))
    .put("inputPrescription", prescriptionJson(row.inputPrescription))
    .put("plannedCompatibility", row.plannedCompatibility?.let { JSONObject().put("status", it.status.name)
        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
    .put("reasonCodes", JSONArray(row.reasonCodes.sorted()))

private fun resolutionJson(row: StimulusPrescriptionResolution) = JSONObject()
    .put("targetId", row.targetId).put("quality", row.quality?.name ?: JSONObject.NULL)
    .put("owner", row.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) } ?: JSONObject.NULL)
    .put("status", row.status.name).put("numericAuthority", row.numericAuthority.name)
    .put("currentPrescription", prescriptionJson(row.currentPrescription))
    .put("probePrescription", prescriptionJson(row.probePrescription))
    .put("proposedPrescription", row.proposedPrescription?.let { proposal -> JSONObject()
        .put("numericAuthority", proposal.numericAuthority).put("source", proposal.source)
        .put("effortMinimumRpe", proposal.effortTarget.minimumRpe)
        .put("sets", JSONArray(proposal.sets.map { JSONObject().put("reps", it.reps).put("weightKg", jsonNumber(it.weightKg))
            .put("targetRpeMin", jsonNumber(it.targetRpeMin)) })) } ?: JSONObject.NULL)
    .put("plannedCompatibility", row.plannedCompatibility?.let { JSONObject().put("status", it.status.name)
        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
    .put("reasonCodes", JSONArray(row.reasonCodes.sorted()))

private fun materializationJson(row: StimulusPrescriptionMaterializationAudit) = JSONObject()
    .put("targetId", row.targetId).put("quality", row.quality?.name ?: JSONObject.NULL)
    .put("owner", row.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) } ?: JSONObject.NULL)
    .put("state", row.state.name).put("authorizedUnits", row.authorizedWeeklySetUnits)
    .put("materializedUnits", row.materializedWeeklySetUnits).put("targetCompatibleUnits", row.targetCompatibleMaterializedUnits)
    .put("shortfall", row.shortfall).put("prescriptionPreservedOrSubset", row.prescriptionPreservedOrSubset)
    .put("executionAuthority", row.executionAuthority.name).put("reasonCodes", JSONArray(row.reasonCodes.sorted()))
    .put("weeks", JSONArray(row.weeklyAudits.sortedBy { it.weekNumber }.map { week -> JSONObject()
        .put("week", week.weekNumber).put("authorizedUnits", week.authorizedSetUnits)
        .put("materializedUnits", week.materializedSetUnits).put("compatibleUnits", week.targetCompatibleMaterializedUnits)
        .put("shortfall", week.shortfall).put("reasonCodes", JSONArray(week.reasonCodes.sorted())) }))
