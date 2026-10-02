package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal data class C12ObservedCase(
    val evidence: C12GateEvidence,
    val classification: C12RootCauseClassification,
    val explicitUnsupportedScope: Boolean,
    val programGap: Boolean,
    val authorityAuditGap: Boolean,
    val placementOnlyOwnerWeeks: List<String>,
    val unmetTargets: List<String>,
    val regressedTargets: List<String>,
    val unexplainedOwners: List<String>
)

internal fun observeC12Case(result: StimulusProductionGenerationResult): C12ObservedCase {
    val comparison = requireNotNull(result.comparison)
    val b7 = requireNotNull(comparison.experimentalReadinessAudit)
    val b8 = requireNotNull(comparison.productionCutoverAuthority)
    val scope = StimulusProductionMaterialScopeResolver().resolveDetailed(comparison)
    val selectedOwners = comparison.selectionPlan.selectedCandidates.mapTo(linkedSetOf()) {
        StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
    }
    val validRemovedOwnerClassifications = setOf(
        StimulusNonSelectionClassification.CANONICAL_REPLACEMENT,
        StimulusNonSelectionClassification.OUTRANKED_FOR_RELEVANT_TARGET,
        StimulusNonSelectionClassification.TARGET_ALREADY_COVERED
    )
    val omissionsByOwner = comparison.nonSelectionProvenance.associateBy { it.omittedControlOwner }
    val b5Exact = comparison.addedOwnerIdentities.all { it in selectedOwners } &&
        comparison.removedOwnerIdentities.all { owner ->
            val omission = omissionsByOwner[owner] ?: return@all false
            omission.classification in validRemovedOwnerClassifications && omission.targetEvidence.any { target ->
                target.disposition.directTargetCandidate && target.disposition.selectionRequired &&
                    target.classification in validRemovedOwnerClassifications
            }
        }

    val b4TargetById = comparison.targetPlan.qualityTargets.associateBy { "QUALITY:${it.quality.name}" }
    val relevantSelectedTargets = comparison.selectionPlan.selectedCandidates.flatMap { candidate ->
        candidate.coveredTargetIds.mapNotNull { targetId ->
            val target = b4TargetById[targetId] ?: return@mapNotNull null
            targetId to StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
        }
    }.distinct().sortedWith(compareBy({ it.first }, { it.second.stableKey }, { it.second.selectionRole }))
    val authRows = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
    fun exactExecutableAuthorization(targetId: String, owner: StimulusPrescriptionOwnerIdentity): StimulusPrescriptionAuthorization? =
        authRows.firstOrNull { authorization ->
            authorization.targetId == targetId && authorization.owner?.let {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == owner
            } == true && authorization.quality?.let { targetId == "QUALITY:${it.name}" } == true &&
                authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
                ) && authorization.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
                authorization.authorizedPrescription != null
        }
    val b6Exact = relevantSelectedTargets.all { (targetId, owner) -> exactExecutableAuthorization(targetId, owner) != null }
    val requiredMaterializations = relevantSelectedTargets.mapNotNull { (targetId, owner) ->
        if (exactExecutableAuthorization(targetId, owner) == null) return@mapNotNull null
        comparison.prescriptionMaterializationAudits.firstOrNull { audit ->
            audit.targetId == targetId && audit.owner?.let {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == owner
            } == true
        }
    }
    val materializationComplete = relevantSelectedTargets.isEmpty() || (
        requiredMaterializations.size == relevantSelectedTargets.size && requiredMaterializations.all { audit ->
            audit.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED &&
                audit.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
                audit.shortfall == 0 && audit.prescriptionPreservedOrSubset
        }
    )

    val targetOutcomes = b7.targetOutcomes
    val unmetTargets = targetOutcomes.filter { outcome ->
        outcome.directlyAffected && outcome.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED &&
            "TARGET_UNMET" in outcome.reasonCodes
    }.map { it.targetId }.distinct().sorted()
    val regressedTargets = targetOutcomes.filter {
        it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED
    }.map { it.targetId }.distinct().sorted()
    val targetOutcome = when {
        regressedTargets.isNotEmpty() -> C12TargetOutcome.REGRESSED
        unmetTargets.isNotEmpty() -> C12TargetOutcome.UNMET
        targetOutcomes.any { it.directlyAffected && it.status == StimulusExperimentalTargetOutcomeStatus.NO_AUTHORITY } ->
            C12TargetOutcome.NO_AUTHORITY
        targetOutcomes.any { it.directlyAffected && it.status == StimulusExperimentalTargetOutcomeStatus.INCONCLUSIVE } ->
            C12TargetOutcome.INCONCLUSIVE
        else -> C12TargetOutcome.PASS
    }
    val scopeUnsupported = scope.status == StimulusProductionScopeResolutionStatus.UNSUPPORTED_QUALITY ||
        b8.reasonCodes.contains("B8_CUTOVER_V1_NON_STRENGTH_CHANGE_OUT_OF_SCOPE")
    val upstreamConsistent = scope.unknownTargetIds.isEmpty() &&
        comparison.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty().isEmpty() &&
        comparison.targetPlan.qualityTargets.none {
            it.strategy == StimulusDoseStrategy.UNRESOLVED || it.numericAuthority == StimulusTargetNumericAuthority.UNRESOLVED
        } && comparison.targetPlan.taskTargets.none {
            it.strategy == StimulusDoseStrategy.UNRESOLVED || it.numericAuthority == StimulusTargetNumericAuthority.UNRESOLVED
        }
    val b4Consistent = comparison.targetPlan.unresolved.isEmpty()

    val execution = comparison.experimental.personalizedDecision?.planningBudget?.execution
    val displacementOwners = execution?.ownerDisplacementEdges.orEmpty().mapTo(linkedSetOf()) { it.displacedOwner }
    val explicitlyReplacedControlOwners = b7.changeAttributions.filter {
        it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY &&
            it.reasonCodes.contains("B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE")
    }.mapNotNullTo(linkedSetOf()) { attribution ->
        if (attribution.stableKey == null || attribution.selectionRole == null) null
        else StimulusPrescriptionOwnerIdentity(attribution.stableKey, attribution.selectionRole)
    }
    val allowedChangedOwners = b8.authorizedOwnerIdentities.toSet() + displacementOwners + explicitlyReplacedControlOwners
    val changedControlOwners = comparison.controlOwnerIdentities.filter { owner ->
        rowsFor(comparison.control, owner) != rowsFor(comparison.experimental, owner)
    }.toSet()
    val b8UnrelatedMutation = b8.reasonCodes.contains("B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION")
    val placementOnlyOwnerWeeks = placementOnlyDeltaOwnerWeeks(comparison)
    val disallowedPlacement = placementOnlyOwnerWeeks.filter { label ->
        val owner = label.substringBefore("@w").let { value ->
            val split = value.indexOf('#')
            StimulusPrescriptionOwnerIdentity(value.substring(0, split), value.substring(split + 1))
        }
        owner in changedControlOwners && owner !in allowedChangedOwners
    }
    val scheduleChanged = comparison.control.weekDaySchedule != comparison.experimental.weekDaySchedule
    val placementScheduleAllowed = !scheduleChanged && !(b8UnrelatedMutation && disallowedPlacement.isNotEmpty())
    val b8Blockers = c12B8Blockers(b8.reasonCodes).flatMap { blocker ->
        if (blocker.reasonCode != "B8_CUTOVER_V1_UNRELATED_CONTROL_MUTATION") return@flatMap listOf(blocker)
        val placementCause = scheduleChanged || disallowedPlacement.isNotEmpty()
        val semanticUnprovenCause = changedControlOwners.any { owner ->
            owner !in allowedChangedOwners && placementOnlyOwnerWeeks.none { it.startsWith("${owner.stableKey}#${owner.selectionRole}@") }
        }
        buildList {
            if (placementCause) add(blocker.copy(classification = C12PrimaryClassification.PLACEMENT_OR_SCHEDULE_POLICY))
            if (semanticUnprovenCause) add(blocker.copy(classification = C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE))
            if (!placementCause && !semanticUnprovenCause) add(blocker.copy(classification = C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE))
        }
    }
    val explicitUpstreamInconsistency = b8.reasonCodes.contains("B8_CUTOVER_V1_UPSTREAM_INCONSISTENCY") &&
        (scope.status == StimulusProductionScopeResolutionStatus.UNKNOWN_TARGET ||
            comparison.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty().isNotEmpty() ||
            comparison.targetPlan.unresolved.isNotEmpty())
    val evidence = C12GateEvidence(
        b4Consistent = b4Consistent,
        upstreamConsistent = upstreamConsistent && !explicitUpstreamInconsistency,
        b5Exact = b5Exact,
        b6Exact = b6Exact,
        materializationComplete = materializationComplete,
        targetOutcome = targetOutcome,
        collateralSafe = b7.collateralRegressionFree,
        provenanceClosed = b7.changeProvenanceClosed,
        b7Eligible = b7.status == StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
        scopeSupported = !scopeUnsupported,
        placementScheduleAllowed = placementScheduleAllowed,
        b8Authorized = b8.status == StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
        b8Blockers = b8Blockers,
        b9RoutedCanonical = result.routeDecision.selectedSource != StimulusProductionProgramSource.CONTROL
    )
    val unexplainedOwners = b7.changeAttributions.filter {
        it.source in setOf(
            StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
        )
    }.map { "${it.stableKey ?: "?"}#${it.selectionRole ?: "?"}:${it.source}" }.distinct().sorted()
    val classification = C12ControlFallbackClassifier.classify(evidence)
    val programGap = targetOutcome != C12TargetOutcome.PASS || !b6Exact || !materializationComplete ||
        b8Blockers.any { it.classification in setOf(
            C12PrimaryClassification.B6_PRESCRIPTION_AUTHORITY_INCOMPLETE,
            C12PrimaryClassification.MATERIALIZATION_INCOMPLETE
        ) } ||
        comparison.addedOwnerIdentities.any { it !in selectedOwners }
    val authorityAuditGap = !b5Exact || !b7.changeProvenanceClosed || b8Blockers.any { it.classification in setOf(
        C12PrimaryClassification.B5_IDENTITY_AUTHORITY_INCOMPLETE,
        C12PrimaryClassification.CHANGE_PROVENANCE_INCOMPLETE
    ) }
    return C12ObservedCase(
        evidence, classification, scopeUnsupported, programGap, authorityAuditGap,
        placementOnlyOwnerWeeks, unmetTargets, regressedTargets, unexplainedOwners
    )
}

private data class C12SemanticRow(
    val setCount: Int,
    val setPrescriptions: List<ProgramSetPrescription>,
    val prescription: String?,
    val restSeconds: Int,
    val weightSource: String,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int
)

private data class C12PlacementRow(val day: Int, val order: Int)

private fun rowsFor(program: GeneratedProgramSkeleton, owner: StimulusPrescriptionOwnerIdentity): List<ProgramSkeletonItem> =
    program.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
        .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId }))

private fun placementOnlyDeltaOwnerWeeks(comparison: StimulusSelectionProgramComparison): List<String> {
    fun rows(program: GeneratedProgramSkeleton) = program.items.groupBy {
        StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
    }
    val before = rows(comparison.control)
    val after = rows(comparison.experimental)
    return (before.keys + after.keys).distinct().sortedWith(compareBy({ it.first.stableKey }, { it.first.selectionRole }, { it.second }))
        .mapNotNull { (owner, week) ->
            val old = before[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
            val new = after[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
            if (old.isEmpty() || new.isEmpty()) return@mapNotNull null
            val oldSemantics = old.map { it.semanticRow() }.sortedBy { it.toString() }
            val newSemantics = new.map { it.semanticRow() }.sortedBy { it.toString() }
            val oldPlacement = old.map { C12PlacementRow(it.dayOfWeek, it.orderIndex) }.sortedWith(compareBy({ it.day }, { it.order }))
            val newPlacement = new.map { C12PlacementRow(it.dayOfWeek, it.orderIndex) }.sortedWith(compareBy({ it.day }, { it.order }))
            if (oldSemantics == newSemantics && oldPlacement != newPlacement) {
                "${owner.stableKey}#${owner.selectionRole}@w$week"
            } else null
        }
}

private fun ProgramSkeletonItem.semanticRow() = C12SemanticRow(
    setCount, setPrescriptions, prescription, restSeconds, weightSource, reps, weightKg, seconds
)

internal fun renderC12ControlFallbackCensus(
    records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
    standardCoverageReport: String,
    c12StartHead: String
): String {
    val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }.sortedBy { it.first.label }
    val control = generated.filter { it.second.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL }
    val observed = control.associate { (spec, result) -> spec.label to observeC12Case(result) }
    val outcomes = JSONObject()
        .put("schema", "c12-control-fallback-census-v1")
        .put("c12StartHead", c12StartHead)
        .put("standardCoverageSha256", MessageDigest.getInstance("SHA-256")
            .digest(standardCoverageReport.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) })
        .put("corpus", JSONObject()
            .put("totalCases", records.size)
            .put("generatedCases", generated.size)
            .put("preflightRejectedCases", records.size - generated.size)
            .put("controlCases", control.size)
            .put("routeCounts", JSONObject().apply {
                generated.groupingBy { it.second.routeDecision.selectedSource.name }.eachCount().toSortedMap()
                    .forEach { (key, value) -> put(key, value) }
            })
            .put("buildAccountingDistribution", JSONObject().apply {
                generated.groupingBy { (_, result) ->
                    val counts = result.buildCounts
                    "${counts.controlBuilds}/${counts.experimentalBuilds}/${counts.totalBuildInvocations}/${counts.thirdBuilds}"
                }.eachCount().toSortedMap().forEach { (key, value) -> put(key, value) }
            }))
        .put("b7ReasonCounts", JSONObject().apply {
            control.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
                .groupingBy { it }.eachCount().toSortedMap().forEach { (key, value) -> put(key, value) }
        })
        .put("b7Intersection", JSONObject().apply {
            val intersectionKeys = listOf(
                "NONE", "PROVENANCE_ONLY", "UNMET_ONLY", "TARGET_REGRESSION_ONLY",
                "PROVENANCE_AND_UNMET", "PROVENANCE_AND_TARGET_REGRESSION", "UNMET_AND_TARGET_REGRESSION",
                "PROVENANCE_AND_UNMET_AND_TARGET_REGRESSION"
            )
            val counts = control.groupingBy { (_, result) ->
                val row = requireNotNull(result.comparison?.experimentalReadinessAudit)
                val hasUnmet = row.targetOutcomes.any { outcome ->
                    outcome.directlyAffected && outcome.status == StimulusExperimentalTargetOutcomeStatus.UNCHANGED &&
                        "TARGET_UNMET" in outcome.reasonCodes
                }
                val hasRegression = row.targetOutcomes.any { it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED }
                val hasProvenanceGap = !row.changeProvenanceClosed
                when {
                    hasProvenanceGap && hasUnmet && hasRegression -> "PROVENANCE_AND_UNMET_AND_TARGET_REGRESSION"
                    hasProvenanceGap && hasUnmet -> "PROVENANCE_AND_UNMET"
                    hasProvenanceGap && hasRegression -> "PROVENANCE_AND_TARGET_REGRESSION"
                    hasUnmet && hasRegression -> "UNMET_AND_TARGET_REGRESSION"
                    hasProvenanceGap -> "PROVENANCE_ONLY"
                    hasUnmet -> "UNMET_ONLY"
                    hasRegression -> "TARGET_REGRESSION_ONLY"
                    else -> "NONE"
                }
            }.eachCount()
            intersectionKeys.sorted().forEach { key -> put(key, counts[key] ?: 0) }
        })
        .put("b8ReasonCounts", JSONObject().apply {
            control.flatMap { it.second.comparison?.productionCutoverAuthority?.reasonCodes.orEmpty() }
                .groupingBy { it }.eachCount().toSortedMap().forEach { (key, value) -> put(key, value) }
        })
        .put("firstFailingGateCounts", JSONObject().apply {
            val counts = observed.values.groupingBy { it.classification.firstFailingGate }.eachCount()
            C12FirstFailingGate.entries.map { it.name }.sorted().forEach { key ->
                put(key, counts.entries.firstOrNull { it.key.name == key }?.value ?: 0)
            }
        })
        .put("primaryClassificationCounts", JSONObject().apply {
            val counts = observed.values.groupingBy { it.classification.primaryClassification }.eachCount()
            C12PrimaryClassification.entries.map { it.name }.sorted().forEach { key ->
                put(key, counts.entries.firstOrNull { it.key.name == key }?.value ?: 0)
            }
        })
        .put("secondaryClassificationOccurrences", JSONObject().apply {
            val counts = observed.values.flatMap { it.classification.secondaryClassifications }
                .groupingBy { it }.eachCount()
            C12PrimaryClassification.entries.map { it.name }.sorted().forEach { key ->
                put(key, counts.entries.firstOrNull { it.key.name == key }?.value ?: 0)
            }
        })
        .put("dispositions", JSONObject().apply {
            observed.values.groupingBy { it.classification.disposition.name }.eachCount().toSortedMap()
                .forEach { (key, value) -> put(key, value) }
        })
        .put("programGapCaseCount", observed.values.count { it.programGap })
        .put("authorityAuditGapCaseCount", observed.values.count { it.authorityAuditGap })
        .put("unsupportedScopeCaseCount", observed.values.count { it.explicitUnsupportedScope })
        .put("programGapCases", JSONArray(observed.filterValues { it.programGap }.keys.sorted()))
        .put("authorityAuditGapCases", JSONArray(observed.filterValues { it.authorityAuditGap }.keys.sorted()))
        .put("gateDispositionCounts", JSONObject()
            .put("b7NotEligible", control.count { it.second.comparison?.experimentalReadinessAudit?.status !=
                StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW })
            .put("b7EligibleButB8Rejected", control.count { (_, result) ->
                result.comparison?.experimentalReadinessAudit?.status ==
                    StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
                    result.comparison?.productionCutoverAuthority?.status !=
                    StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER
            })
            .put("authorizedAtB8", control.count { it.second.comparison?.productionCutoverAuthority?.status ==
                StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER }))
        .put("potentialFalseNegativeCases", JSONArray(observed.filterValues {
            it.classification.disposition == C12FallbackDisposition.POTENTIAL_FALSE_NEGATIVE_GATE
        }.keys.sorted()))
        .put("trueSafetyBlockCases", JSONArray(observed.filterValues {
            it.classification.disposition == C12FallbackDisposition.TRUE_CONTROL_FALLBACK &&
                it.classification.primaryClassification != C12PrimaryClassification.SCOPE_UNSUPPORTED
        }.keys.sorted()))
        .put("unsupportedScopeCases", JSONArray(observed.filterValues { it.explicitUnsupportedScope }.keys.sorted()))
        .put("programImprovementCases", JSONArray(observed.filterValues { it.programGap }.keys.sorted()))
        .put("cases", JSONArray(control.map { (spec, result) ->
            val comparison = requireNotNull(result.comparison)
            val classification = observed.getValue(spec.label)
            c12CaseJson(spec, result, classification)
        }))
        .put("preflightRejections", JSONArray(records.filter { it.second == null }.sortedBy { it.first.label }.map { (spec, _) ->
            JSONObject()
                .put("caseId", spec.label)
                .put("history", spec.history)
                .put("reason", "PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY")
                .put("targetsMaterializationAndGatesReached", false)
                .put("buildCounts", JSONObject().put("control", 0).put("experimental", 0).put("total", 0).put("third", 0))
        }))
        .put("positiveReference", c12CaseJson(
            generated.single { it.first.label == "reviewed_strength_isolated" }.first,
            generated.single { it.first.label == "reviewed_strength_isolated" }.second,
            null
        ))
        .put("knownSafetyBlockReference", c12CaseJson(
            generated.single { it.first.label == "reviewed_hypertrophy_isolated" }.first,
            generated.single { it.first.label == "reviewed_hypertrophy_isolated" }.second,
            observed["reviewed_hypertrophy_isolated"]
        ))
    return outcomes.toString(2) + "\n"
}

private fun c12CaseJson(
    spec: StimulusProductionCoverageAuditTest.CoverageSpec,
    result: StimulusProductionGenerationResult,
    observed: C12ObservedCase?
): JSONObject {
    val comparison = requireNotNull(result.comparison)
    val decision = comparison.experimental.personalizedDecision
    // This compatibility mirror stores the canonical B1/B2 result; it is computed before
    // CONTROL materialization and contains no CONTROL program/owner evidence.
    val needProfile = comparison.control.personalizedDecision?.athleteStimulusNeedProfile
    val execution = decision?.planningBudget?.execution
    val b7 = comparison.experimentalReadinessAudit
    val b8 = comparison.productionCutoverAuthority
    val scope = StimulusProductionMaterialScopeResolver().resolveDetailed(comparison)
    fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
        .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
    fun prescriptionJson(prescription: PlannedPrescription?) = prescription?.let { value ->
        JSONObject().put("text", value.text).put("sets", JSONArray(value.sets.map { set ->
            JSONObject().put("setIndex", set.setIndex).put("reps", set.reps).put("weightKg", set.weightKg)
                .put("seconds", set.seconds).put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
        })).put("restSeconds", value.restSeconds).put("weightSource", value.weightSource)
    } ?: JSONObject.NULL
    fun rowJson(row: ProgramSkeletonItem) = JSONObject()
        .put("week", row.weekNumber).put("day", row.dayOfWeek).put("order", row.orderIndex)
        .put("owner", JSONObject().put("stableKey", row.exerciseStableKey).put("selectionRole", row.selectionRole))
        .put("setCount", row.setCount).put("setPrescriptions", JSONArray(row.setPrescriptions.map { set ->
            JSONObject().put("setIndex", set.setIndex).put("reps", set.reps).put("weightKg", set.weightKg)
                .put("seconds", set.seconds).put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
        })).put("prescription", row.prescription).put("restSeconds", row.restSeconds)
        .put("weightSource", row.weightSource).put("reps", row.reps).put("weightKg", row.weightKg).put("seconds", row.seconds)
    val changedOwners = (comparison.controlOwnerIdentities + comparison.experimentalOwnerIdentities)
        .distinct().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        .mapNotNull { owner ->
            val before = rowsFor(comparison.control, owner)
            val after = rowsFor(comparison.experimental, owner)
            if (before.map { rowJson(it).toString() } == after.map { rowJson(it).toString() }) null
            else JSONObject().put("owner", ownerJson(owner))
                .put("controlRows", JSONArray(before.map(::rowJson)))
                .put("experimentalRows", JSONArray(after.map(::rowJson)))
        }
    val targetPlanShadow = needProfile?.stimulusTargetPlanShadow
    val selectedOwnerRows = comparison.selectionPlan.selectedCandidates.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        .map { candidate -> JSONObject()
            .put("owner", JSONObject().put("stableKey", candidate.stableKey).put("selectionRole", candidate.selectionRole))
            .put("coveredTargetIds", JSONArray(candidate.coveredTargetIds.sorted()))
            .put("primaryTargetId", candidate.primaryTargetId)
            .put("selectionReasons", JSONArray(candidate.selectionReasons.sorted()))
            .put("probePrescriptionCompatibility", candidate.probePrescriptionCompatibility.name)
            .put("targetSetsFromExistingPrescription", candidate.targetSetsFromExistingPrescription)
        }
    val dispositionRows = comparison.nonSelectionProvenance.sortedWith(compareBy({ it.omittedControlOwner.stableKey }, { it.omittedControlOwner.selectionRole }))
        .map { omission -> JSONObject()
            .put("omittedControlOwner", ownerJson(omission.omittedControlOwner))
            .put("classification", omission.classification.name)
            .put("targetEvidence", JSONArray(omission.targetEvidence.sortedBy { it.targetId }.map { target ->
                val disposition = target.disposition
                JSONObject().put("targetId", target.targetId).put("classification", target.classification.name)
                    .put("status", disposition.status.name).put("reasons", JSONArray(disposition.reasons.map { it.name }.sorted()))
                    .put("canonicalSelectionRole", disposition.canonicalSelectionRole)
                    .put("directTargetCandidate", disposition.directTargetCandidate)
                    .put("selectionRequired", disposition.selectionRequired)
                    .put("candidateRanking", rankingJson(disposition.candidateRanking))
                    .put("selectedInstead", disposition.selectedInstead?.let(::ownerJson) ?: JSONObject.NULL)
                    .put("selectedInsteadRanking", rankingJson(disposition.selectedInsteadRanking))
                    .put("firstDifferingField", disposition.firstDifferingField?.name ?: JSONObject.NULL)
                    .put("targetCoveredBySelectedOwner", disposition.targetCoveredBySelectedOwner)
            }))
        }
    val authRows = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().sortedWith(
        compareBy({ it.targetId }, { it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() })
    ).map { authorization -> JSONObject()
        .put("targetId", authorization.targetId).put("quality", authorization.quality?.name ?: JSONObject.NULL)
        .put("owner", authorization.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) } ?: JSONObject.NULL)
        .put("source", authorization.source?.name ?: JSONObject.NULL).put("status", authorization.status.name)
        .put("executionAuthority", authorization.executionAuthority.name)
        .put("authorizedPrescription", prescriptionJson(authorization.authorizedPrescription))
        .put("reasonCodes", JSONArray(authorization.reasonCodes.sorted()))
    }
    val materializationRows = comparison.prescriptionMaterializationAudits.sortedWith(
        compareBy({ it.targetId }, { it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() })
    ).map { audit -> JSONObject()
        .put("targetId", audit.targetId).put("quality", audit.quality?.name ?: JSONObject.NULL)
        .put("owner", audit.owner?.let { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) } ?: JSONObject.NULL)
        .put("authorizedWeeklySetUnits", audit.authorizedWeeklySetUnits).put("materializedWeeklySetUnits", audit.materializedWeeklySetUnits)
        .put("targetCompatibleMaterializedUnits", audit.targetCompatibleMaterializedUnits)
        .put("shortfall", audit.shortfall).put("overrun", audit.overrun)
        .put("prescriptionPreservedOrSubset", audit.prescriptionPreservedOrSubset)
        .put("state", audit.state.name).put("executionAuthority", audit.executionAuthority.name)
        .put("reasonCodes", JSONArray(audit.reasonCodes.sorted()))
        .put("weeks", JSONArray(audit.weeklyAudits.sortedBy { it.weekNumber }.map { week -> JSONObject()
            .put("week", week.weekNumber).put("authorizedSetUnits", week.authorizedSetUnits)
            .put("materializedSetUnits", week.materializedSetUnits).put("compatibleUnits", week.targetCompatibleMaterializedUnits)
            .put("shortfall", week.shortfall).put("overrun", week.overrun)
            .put("prescriptionPreservedOrSubset", week.prescriptionPreservedOrSubset)
            .put("reasonCodes", JSONArray(week.reasonCodes.sorted()))
        }))
    }
    val executionJson = JSONObject()
        .put("constrainedOwnerStableKeys", JSONArray(execution?.constrainedOwnerStableKeys.orEmpty().sorted()))
        .put("ownerAllocationProvenance", JSONArray(execution?.ownerAllocationProvenance.orEmpty()
            .deterministicOwnerOrder().map { it.toJson() }))
        .put("ownerDisplacementEdges", JSONArray(execution?.ownerDisplacementEdges.orEmpty().sortedWith(compareBy(
            { it.causeOwner.stableKey }, { it.causeOwner.selectionRole }, { it.displacedOwner.stableKey },
            { it.displacedOwner.selectionRole }, { it.week ?: 0 }, { it.stage.ordinal }, { it.mutationSequence }
        )).map { it.toJson() }))
    val resultJson = JSONObject()
        .put("caseId", spec.label)
        .put("finalRoute", result.routeDecision.selectedSource.name)
        .put("routeReasonCodes", JSONArray(result.routeDecision.reasonCodes.sorted()))
        .put("buildCounts", JSONObject()
            .put("control", result.buildCounts.controlBuilds).put("experimental", result.buildCounts.experimentalBuilds)
            .put("total", result.buildCounts.totalBuildInvocations).put("third", result.buildCounts.thirdBuilds))
        .put("programRequest", JSONObject().put("goal", comparison.experimental.request.goal.name)
            .put("days", comparison.experimental.request.weeklyTrainingDays).put("minutes", comparison.experimental.request.sessionMinutes)
            .put("durationWeeks", comparison.experimental.request.durationWeeks)
            .put("equipment", JSONArray(comparison.experimental.request.availableEquipment.map { it.toString() }.sorted())))
        .put("B1_B4_needDoseStrategyAndTargets", needProfile?.toCompactJson() ?: JSONObject.NULL)
        .put("B4_canonicalTargetPlan", targetPlanShadow?.toCompactJson() ?: comparison.targetPlan.toCompactJson())
        .put("B5_selection", JSONObject()
            .put("selectedOwners", JSONArray(selectedOwnerRows))
            .put("selectionTraces", JSONArray(comparison.selectionPlan.traces.sortedBy { it.targetId }.map { trace -> JSONObject()
                .put("targetId", trace.targetId).put("strategy", trace.strategy.name).put("priority", trace.priority.name)
                .put("selectionRequired", trace.selectionRequired).put("candidatePool", JSONArray(trace.candidatePool.sorted()))
                .put("selectedStableKey", trace.selectedStableKey ?: JSONObject.NULL)
                .put("selectedSelectionRole", trace.selectedSelectionRole ?: JSONObject.NULL)
                .put("coveredByPreviouslySelectedStableKey", trace.coveredByPreviouslySelectedStableKey ?: JSONObject.NULL)
                .put("coveredByPreviouslySelectedSelectionRole", trace.coveredByPreviouslySelectedSelectionRole ?: JSONObject.NULL)
                .put("reasonCodes", JSONArray(trace.reasonCodes.sorted()))
            }))
            .put("removedControlOwnerDispositions", JSONArray(dispositionRows)))
        .put("B6_prescriptionAuthorities", JSONArray(authRows))
        .put("B6_realization", comparison.prescriptionRealizationPlan?.toCompactJson() ?: JSONObject.NULL)
        .put("B6_materialization", JSONArray(materializationRows))
        .put("C10_originProvenance", executionJson)
        .put("B7_targetOutcomes", JSONArray(b7?.targetOutcomes.orEmpty().sortedBy { it.targetId }.map { outcome -> JSONObject()
            .put("targetId", outcome.targetId).put("status", outcome.status.name).put("directlyAffected", outcome.directlyAffected)
            .put("controlWeeklyUnitsDistance", outcome.controlWeeklyUnitsDistance ?: JSONObject.NULL)
            .put("experimentalWeeklyUnitsDistance", outcome.experimentalWeeklyUnitsDistance ?: JSONObject.NULL)
            .put("controlWeeklySessionsDistance", outcome.controlWeeklySessionsDistance ?: JSONObject.NULL)
            .put("experimentalWeeklySessionsDistance", outcome.experimentalWeeklySessionsDistance ?: JSONObject.NULL)
            .put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
        }))
        .put("B7_attributions", JSONArray(b7?.changeAttributions.orEmpty().sortedWith(compareBy(
            { it.stableKey.orEmpty() }, { it.selectionRole.orEmpty() }, { it.source.name }
        )).map { attribution -> JSONObject()
            .put("owner", if (attribution.stableKey == null || attribution.selectionRole == null) JSONObject.NULL else
                ownerJson(StimulusPrescriptionOwnerIdentity(attribution.stableKey, attribution.selectionRole)))
            .put("source", attribution.source.name).put("targetIds", JSONArray(attribution.targetIds.sorted()))
            .put("reasonCodes", JSONArray(attribution.reasonCodes.sorted()))
            .put("evidenceSources", JSONArray(attribution.evidenceSources.sorted()))
        }))
        .put("B7", JSONObject().put("status", b7?.status?.name ?: JSONObject.NULL)
            .put("materializationIntegrityPassed", b7?.materializationIntegrityPassed)
            .put("changeProvenanceClosed", b7?.changeProvenanceClosed)
            .put("collateralRegressionFree", b7?.collateralRegressionFree)
            .put("reasonCodes", JSONArray(b7?.reasonCodes.orEmpty().sorted())))
        .put("B8", JSONObject().put("scope", b8?.scope?.name ?: JSONObject.NULL)
            .put("status", b8?.status?.name ?: JSONObject.NULL)
            .put("authorizedOwnerIdentities", JSONArray(b8?.authorizedOwnerIdentities.orEmpty().sortedWith(compareBy(
                { it.stableKey }, { it.selectionRole }
            )).map(::ownerJson)))
            .put("authorizedAuthorityIdentities", JSONArray(b8?.authorizedAuthorityIdentities.orEmpty().sortedWith(compareBy(
                { it.stableKey }, { it.selectionRole }, { it.quality.name }
            )).map { identity -> JSONObject().put("stableKey", identity.stableKey)
                .put("selectionRole", identity.selectionRole).put("quality", identity.quality.name) }))
            .put("routingActive", b8?.routingActive).put("productionMutationAuthority", b8?.productionMutationAuthority)
            .put("reasonCodes", JSONArray(b8?.reasonCodes.orEmpty().sorted()))
            .put("typedBlockers", JSONArray((observed?.evidence?.b8Blockers ?: c12B8Blockers(b8?.reasonCodes.orEmpty())).map { blocker -> JSONObject()
                .put("reasonCode", blocker.reasonCode)
                .put("classification", blocker.classification?.name ?: JSONObject.NULL)
                .put("relatedGate", blocker.relatedGate?.name ?: JSONObject.NULL)
            }))
            .put("scopeResolution", JSONObject().put("status", scope.status.name).put("scope", scope.scope?.name ?: JSONObject.NULL)
                .put("materialQualities", JSONArray(scope.materialQualities.map { it.name }.sorted()))
                .put("materialOwners", JSONArray(scope.materialOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("unknownTargetIds", JSONArray(scope.unknownTargetIds.sorted()))
                .put("unattributedOwnerIdentities", JSONArray(scope.unattributedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("reasonCodes", JSONArray(scope.reasonCodes.sorted())))
        )
        .put("B9", JSONObject().put("selectedSource", result.routeDecision.selectedSource.name)
            .put("reasonCodes", JSONArray(result.routeDecision.reasonCodes.sorted())))
        .put("exactChangedOwnerRows", JSONArray(changedOwners))
    if (observed != null) {
        resultJson.put("classification", JSONObject()
            .put("firstFailingGate", observed.classification.firstFailingGate.name)
            .put("primary", observed.classification.primaryClassification.name)
            .put("secondary", JSONArray(observed.classification.secondaryClassifications.map { it.name }.sorted()))
            .put("disposition", observed.classification.disposition.name)
            .put("facts", JSONObject()
                .put("b4Consistent", observed.evidence.b4Consistent).put("upstreamConsistent", observed.evidence.upstreamConsistent)
                .put("b5ExactIdentity", observed.evidence.b5Exact).put("b6ExactPrescriptionAuthority", observed.evidence.b6Exact)
                .put("fullMaterialization", observed.evidence.materializationComplete)
                .put("targetOutcome", observed.evidence.targetOutcome.name).put("collateralSafe", observed.evidence.collateralSafe)
                .put("provenanceClosed", observed.evidence.provenanceClosed).put("b7Eligible", observed.evidence.b7Eligible)
                .put("scopeSupported", observed.evidence.scopeSupported).put("placementScheduleAllowed", observed.evidence.placementScheduleAllowed)
                .put("b8Authorized", observed.evidence.b8Authorized).put("b9RoutedCanonical", observed.evidence.b9RoutedCanonical)
                .put("b8TypedBlockers", JSONArray(observed.evidence.b8Blockers.map { blocker -> JSONObject()
                    .put("reasonCode", blocker.reasonCode)
                    .put("classification", blocker.classification?.name ?: JSONObject.NULL)
                    .put("relatedGate", blocker.relatedGate?.name ?: JSONObject.NULL)
                }))
                .put("explicitUnsupportedScope", observed.explicitUnsupportedScope)
                .put("programGap", observed.programGap).put("authorityAuditGap", observed.authorityAuditGap))
            .put("placementOnlyOwnerWeeks", JSONArray(observed.placementOnlyOwnerWeeks.sorted()))
            .put("unmetTargets", JSONArray(observed.unmetTargets))
            .put("regressedTargets", JSONArray(observed.regressedTargets))
            .put("unexplainedOwners", JSONArray(observed.unexplainedOwners)))
    }
    return resultJson
}

private fun rankingJson(tuple: StimulusCandidateRankingTuple?): Any = tuple?.let {
    JSONObject().put("targetCompatibleHistory", it.targetCompatibleHistory).put("recentHistory", it.recentHistory)
        .put("contextHistory", it.contextHistory).put("anchorContinuity", it.anchorContinuity)
        .put("repeatedRecentSessions", it.repeatedRecentSessions).put("freeWeightCompatible", it.freeWeightCompatible)
        .put("highConfidence", it.highConfidence).put("redundant", it.redundant).put("stableKey", it.stableKey)
} ?: JSONObject.NULL
