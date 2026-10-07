package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Deterministic before/after report for the exact C27 owner-week regression set. */
internal object C28MaterialDemandExecutionAuthorityCensus {
    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        preparedContexts: Map<String, PreparedCanonicalGenerationContext>,
        c20Census: String,
        startSha: String
    ): String {
        val prior = JSONObject(File("../docs/c27-sparse-added-identity-census.json").readText())
        val priorRows = prior.getJSONArray("ownerWeekRows")
        val byCase = records.associateBy { it.first.label }
        val identityRows = mutableListOf<JSONObject>()
        val ownerWeekRows = mutableListOf<JSONObject>()

        val identities = prior.getJSONArray("attributions").let { rows ->
            (0 until rows.length()).map(rows::getJSONObject).sortedWith(compareBy(
                { it.getString("case") }, { it.getString("stableKey") }, { it.getString("selectionRole") }
            ))
        }
        identities.forEach { before ->
            val caseName = before.getString("case")
            val identity = StimulusPrescriptionOwnerIdentity(
                before.getString("stableKey"), before.getString("selectionRole")
            )
            val result = requireNotNull(byCase[caseName]?.second) { "Missing C28 corpus result for $caseName" }
            val comparison = result.comparison
            val context = requireNotNull(preparedContexts[caseName]) { "Missing prepared inputs for $caseName" }
            // Resolver-only re-observation uses the exact immutable history/request inputs
            // captured before EXP. It does not invoke a planner build or create prescription.
            val candidateDemand = MaterialDemandResolver().resolve(
                context.snapshot, context.state, context.gaps, context.resolvedRequest.request
            )
            val provider = comparison?.prescriptionAuthorizationPlan?.provider()
            val locallyResolvedDemand = reResolveMaterialDemandCandidatesWithExistingAuthority(candidateDemand, provider)
            val generatedOrigin = comparison?.experimental?.personalizedDecision?.planningBudget?.execution
                ?.materialDemandCandidateOrigins?.singleOrNull { it.owner == identity }
            val origin = generatedOrigin ?: locallyResolvedDemand.candidateOrigins.singleOrNull { it.owner == identity }
            val recovery = origin?.authorityResolution
                ?: result.materialDemandAuthorityResolutions.singleOrNull { it.originalOwner == identity }
            val needGapCodes = origin?.gapCodes ?: before.optJSONArray("representedGapCodes")?.let { codes ->
                (0 until codes.length()).mapTo(linkedSetOf()) { codes.getString(it) }
            }.orEmpty()
            val unresolvedNeedCodes = locallyResolvedDemand.unresolvedGapCodes + result.unresolvedMaterialDemandGaps
            val needRetained = needGapCodes.any { it in unresolvedNeedCodes } ||
                (recovery?.status == ExecutionAuthorityResolutionStatus.READY && recovery.finalOwner != null)
            val recoveryOutcome = when {
                recovery?.status == ExecutionAuthorityResolutionStatus.READY && recovery.finalOwner == identity ->
                    "ORIGINAL_CANDIDATE_AUTHORIZED"
                recovery?.status == ExecutionAuthorityResolutionStatus.READY -> "RESELECTED_TO_AUTHORIZED_OWNER"
                recovery?.status in setOf(ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED,
                    ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT) -> "USER_INPUT_REQUIRED"
                else -> "UNRESOLVED_NO_AUTHORITY"
            }
            val candidateKey = "${identity.stableKey}#${identity.selectionRole}"
            val diagnosticReason = comparison?.experimental?.personalizedDecision?.planningBudget?.execution
                ?.candidateAudit?.get(candidateKey)
                ?: MaterialDemandExecutionDeferral.NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY.reasonCode
            val probe = PlannedExercise(identity.stableKey, identity.selectionRole, "C28 exact authority probe", 0)
            val currentB6 = comparison?.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { authorization ->
                authorization.owner?.let { it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole } == true
            }
            val providerResolution = comparison?.prescriptionAuthorizationPlan?.provider()
                ?.resolveOwnerPrescription(probe)?.javaClass?.simpleName
                ?: before.optString("exactProviderResolution", "NO_EXACT_AUTHORITY_DECISION")
            val currentAddedAttribution = comparison?.experimentalReadinessAudit?.changeAttributions.orEmpty().any { attribution ->
                attribution.stableKey == identity.stableKey && attribution.selectionRole == identity.selectionRole &&
                    "UNEXPLAINED_ADDED_IDENTITY" in attribution.reasonCodes
            }
            val beforeWeeks = (0 until priorRows.length()).map(priorRows::getJSONObject).filter {
                it.getString("case") == caseName && it.getString("stableKey") == identity.stableKey &&
                    it.getString("selectionRole") == identity.selectionRole
            }.sortedBy { it.getInt("week") }
            identityRows += JSONObject()
                .put("case", caseName)
                .put("stableKey", identity.stableKey)
                .put("selectionRole", identity.selectionRole)
                .put("exercise", before.optString("exercise"))
                .put("ownerRoleType", before.optString("ownerRoleType"))
                .put("beforeB6Status", before.optString("b6ExactOwnerStatus"))
                .put("beforeB6Reasons", before.optJSONArray("b6ExactOwnerReasons") ?: JSONArray())
                .put("beforePrescriptionAuthority", before.optString("prescriptionAuthority"))
                .put("beforePrescriptionSource", before.optString("prescriptionSource"))
                .put("beforePrescription", before.optJSONObject("prescription"))
                .put("beforeFrequencySource", before.optString("frequencySource"))
                .put("beforeFirstProducedAt", before.optJSONObject("firstProducedAt"))
                .put("beforeB7AttributionStatus", before.optString("b7AttributionStatus"))
                .put("candidateOrigin", origin?.toJson())
                .put("candidateOriginPreserved", origin != null)
                .put("candidateDeferredReason", diagnosticReason)
                .put("candidateDemandTargetSets", candidateDemand.candidates.firstOrNull {
                    it.stableKey == identity.stableKey && it.role == identity.selectionRole
                }?.targetSets)
                .put("candidateAlternativesHaveZeroPrescriptionUnits", candidateDemand.candidateAlternatives
                    .filter { StimulusPrescriptionOwnerIdentity(it.candidate.stableKey, it.candidate.role) == identity }
                    .all { it.candidate.targetSets == 0 })
                .put("authorityResolution", recovery?.let { resolution -> JSONObject()
                    .put("status", resolution.status.name)
                    .put("reason", resolution.reason.name)
                    .put("returnTarget", resolution.returnTarget.name)
                    .put("attemptedOwners", JSONArray(resolution.attemptedOwners.map {
                        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    }))
                    .put("finalOwner", resolution.finalOwner?.let {
                        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    })
                })
                .put("recoveryOutcome", recoveryOutcome)
                .put("missingAuthorityKind", recovery?.reason?.name ?: "NO_EXACT_OWNER_AUTHORITY")
                .put("reResolutionStage", recovery?.returnTarget?.name ?: ExecutionAuthorityReturnTarget.NONE.name)
                .put("finalExecutableOwner", recovery?.finalOwner?.let {
                    JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                })
                .put("needGapCodes", JSONArray(needGapCodes.sorted()))
                .put("unresolvedNeedGapCodes", JSONArray(unresolvedNeedCodes.sorted()))
                .put("needRetainedOrResolvedByExactAuthority", needRetained)
                .put("needRemainsUnresolved", needGapCodes.any { it in unresolvedNeedCodes })
                .put("b5Selected", comparison?.selectionPlan?.selectedCandidates.orEmpty().any {
                    it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                })
                .put("b6DecisionCount", if (comparison == null) before.optInt("b6DecisionCount", 0) else currentB6.size)
                .put("exactProviderResolution", providerResolution)
                .put("rootCauseBefore", before.getString("primaryRootCauseCategory"))
                .put("experimentalEvaluationFailedClosed", comparison == null)
                .put("actualExecutableRowsAfter", comparison?.experimental?.items.orEmpty().count {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                })
                .put("unexplainedAddedAttributionAfter", currentAddedAttribution)

            beforeWeeks.forEach { oldRow ->
                val week = oldRow.getInt("week")
                val controlExists = (comparison?.control?.items ?: result.program.items).any {
                    it.weekNumber == week && it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }
                val experimentalRows = comparison?.experimental?.items.orEmpty().filter {
                    it.weekNumber == week && it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }
                ownerWeekRows += JSONObject()
                    .put("case", caseName)
                    .put("week", week)
                    .put("stableKey", identity.stableKey)
                    .put("selectionRole", identity.selectionRole)
                    .put("beforeExecutableRow", oldRow.optBoolean("actualExecutableRow"))
                    .put("beforePrescription", oldRow.optJSONObject("prescription"))
                    .put("controlPresentAfter", controlExists)
                    .put("experimentalRowsAfter", experimentalRows.size)
                    .put("experimentalRowDetailsAfter", JSONArray(experimentalRows.map { row ->
                        JSONObject().put("day", row.dayOfWeek).put("order", row.orderIndex)
                            .put("setCount", row.setCount).put("reps", row.reps)
                            .put("seconds", row.seconds).put("loadKg", row.weightKg)
                            .put("sets", JSONArray(row.setPrescriptions.map { set ->
                                JSONObject().put("setIndex", set.setIndex).put("reps", set.reps)
                                    .put("seconds", set.seconds).put("loadKg", set.weightKg)
                                    .put("loadState", set.loadState.name).put("targetRpeMin", set.targetRpeMin)
                            }))
                            .put("restSeconds", row.restSeconds)
                            .put("prescription", row.prescription).put("weightSource", row.weightSource)
                    }))
                    .put("unauthorizedExecutableRowAfter", experimentalRows.isNotEmpty() &&
                        currentB6.none { it.authorizedPrescription != null })
                    .put("materialDeltaAfter", !controlExists && experimentalRows.isNotEmpty())
                    .put("unexplainedAddedAttributionAfter", currentAddedAttribution)
                    .put("originPresentAfter", origin != null)
                    .put("frequencyOrCompletionResurrected", experimentalRows.isNotEmpty())
            }
        }

        val hypertrophyBefore = prior.getJSONObject("unexplainedPrescriptionChange")
        val hypertrophyIdentity = StimulusPrescriptionOwnerIdentity(
            hypertrophyBefore.getString("stableKey"), hypertrophyBefore.getString("selectionRole")
        )
        val hypertrophyResult = requireNotNull(byCase[hypertrophyBefore.getString("case")]?.second)
        val hypertrophyComparison = hypertrophyResult.comparison
        val hypertrophyAfterRows = hypertrophyComparison?.experimental?.items.orEmpty().filter {
            it.exerciseStableKey == hypertrophyIdentity.stableKey && it.selectionRole == hypertrophyIdentity.selectionRole
        }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
        val hypertrophy = JSONObject()
            .put("case", hypertrophyBefore.getString("case"))
            .put("stableKey", hypertrophyIdentity.stableKey)
            .put("selectionRole", hypertrophyIdentity.selectionRole)
            .put("beforeControlRows", hypertrophyBefore.getJSONArray("controlRows"))
            .put("beforeExperimentalRows", hypertrophyBefore.getJSONArray("experimentalRows"))
            .put("afterExperimentalRows", JSONArray(hypertrophyAfterRows.map { row ->
                JSONObject().put("week", row.weekNumber).put("sets", row.setCount).put("prescription", row.prescription)
            }))
            .put("afterCandidateOriginPreserved", hypertrophyComparison?.experimental?.personalizedDecision
                ?.planningBudget?.execution?.materialDemandCandidateOrigins?.any { it.owner == hypertrophyIdentity }
                ?: MaterialDemandResolver().resolve(
                    requireNotNull(preparedContexts[hypertrophyBefore.getString("case")]).snapshot,
                    requireNotNull(preparedContexts[hypertrophyBefore.getString("case")]).state,
                    requireNotNull(preparedContexts[hypertrophyBefore.getString("case")]).gaps,
                    requireNotNull(preparedContexts[hypertrophyBefore.getString("case")]).resolvedRequest.request
                ).candidateOrigins.any { it.owner == hypertrophyIdentity })
            .put("experimentalEvaluationFailedClosed", hypertrophyComparison == null)
            .put("afterExactB6", hypertrophyComparison?.prescriptionAuthorizationPlan?.authorizations.orEmpty().any {
                it.owner?.let { owner -> owner.stableKey == hypertrophyIdentity.stableKey &&
                    owner.selectionRole == hypertrophyIdentity.selectionRole } == true && it.authorizedPrescription != null
            })

        val generatedRecords = records.mapNotNull { (spec, result) -> result?.let { spec.label to it } }
        val generated = generatedRecords.map { it.second }
        val routes = generated.groupingBy { it.routeDecision.selectedSource.name }.eachCount().toSortedMap()
        val b7Reasons = generated.flatMap { it.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
            .groupingBy { it }.eachCount().toSortedMap()
        val changeReasons = generated.flatMap { result ->
            result.comparison?.experimentalReadinessAudit?.changeAttributions.orEmpty().flatMap { it.reasonCodes }
        }.groupingBy { it }.eachCount().toSortedMap()
        val b7Cases = generatedRecords.flatMap { (case, result) ->
            result.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty().map { case to it }
        }.groupBy({ it.second }, { it.first }).mapValues { it.value.distinct().size }.toSortedMap()
        val buildCounts = JSONObject()
            .put("control", generated.sumOf { it.buildCounts.controlBuilds })
            .put("experimental", generated.sumOf { it.buildCounts.experimentalBuilds })
            .put("total", generated.sumOf { it.buildCounts.totalBuildInvocations })
            .put("third", generated.sumOf { it.buildCounts.thirdBuilds })
            .put("generatedCases", generated.size)
            .put("perGeneratedCase", JSONObject()
                .put("control", generated.sumOf { it.buildCounts.controlBuilds } / generated.size.coerceAtLeast(1))
                .put("experimental", generated.sumOf { it.buildCounts.experimentalBuilds } / generated.size.coerceAtLeast(1))
                .put("total", generated.sumOf { it.buildCounts.totalBuildInvocations } / generated.size.coerceAtLeast(1))
                .put("third", generated.sumOf { it.buildCounts.thirdBuilds } / generated.size.coerceAtLeast(1)))
        val experimentalItems = generated.flatMap { it.comparison?.experimental?.items.orEmpty() }
        val powerRows = experimentalItems.count { it.selectionRole == "CANONICAL_STIMULUS_QUALITY_POWER" }
        val jumpLandingRows = experimentalItems.count { it.selectionRole.contains("JUMP_LANDING") }
        val c20 = JSONObject(c20Census).getJSONObject("summary")
        val c26Baseline = prior.getJSONObject("c26Baseline")

        val json = JSONObject()
            .put("phase", "C28")
            .put("title", "Material-demand execution authority boundary hardening")
            .put("startSha", startSha)
            .put("before", JSONObject()
                .put("c27AddedIdentities", prior.getJSONObject("summary").getInt("unexplainedAddedIdentityAttributions"))
                .put("c27ExecutableOwnerWeeks", prior.getJSONObject("summary").getInt("actualExecutableOwnerWeekRowsWithoutExactB6"))
                .put("c26Baseline", c26Baseline)
                .put("b6RejectedQualitySparseIntersection", c26Baseline.getJSONObject("b6RejectedQuality")
                    .getInt("exactIntersectionWithSparseAttributions")))
            .put("after", JSONObject()
                .put("routes", JSONObject(routes))
                .put("generatedCases", generated.size)
                .put("preflightRejectedCases", records.count { it.second == null })
                .put("b7CaseCounts", JSONObject(b7Cases))
                .put("b7ReasonOccurrences", JSONObject(b7Reasons))
                .put("provenanceAttributionCounts", JSONObject(changeReasons))
                .put("c20", c20)
                .put("buildAccounting", buildCounts)
                .put("powerMaterialRows", powerRows)
                .put("jumpLandingMaterialRows", jumpLandingRows)
                .put("taskMaterialRows", experimentalItems.count { !it.taskProtocolSemanticsJson.isNullOrBlank() }))
            .put("summary", JSONObject()
                .put("sparseIdentities", identityRows.size)
                .put("sparseOwnerWeeksBefore", priorRows.length())
                .put("candidateOriginsPreserved", identityRows.count { it.optBoolean("candidateOriginPreserved") })
                .put("noExactAuthority", identityRows.count { it.optString("exactProviderResolution") == "NoExactAuthority" })
                .put("b5Selected", identityRows.count { it.optBoolean("b5Selected") })
                .put("originalCandidateAuthorized", identityRows.count {
                    it.optJSONObject("authorityResolution")?.optString("status") == ExecutionAuthorityResolutionStatus.READY.name &&
                        it.optJSONObject("authorityResolution")?.optJSONObject("finalOwner")?.optString("stableKey") == it.optString("stableKey") &&
                        it.optJSONObject("authorityResolution")?.optJSONObject("finalOwner")?.optString("selectionRole") == it.optString("selectionRole")
                })
                .put("reselectedAuthorizedOwner", identityRows.count {
                    val resolution = it.optJSONObject("authorityResolution")
                    resolution?.optString("status") == ExecutionAuthorityResolutionStatus.READY.name &&
                        (resolution.optJSONObject("finalOwner")?.optString("stableKey") != it.optString("stableKey") ||
                            resolution.optJSONObject("finalOwner")?.optString("selectionRole") != it.optString("selectionRole"))
                })
                .put("userInputRequired", identityRows.count {
                    it.optJSONObject("authorityResolution")?.optString("status") in setOf(
                        ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED.name,
                        ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT.name
                    )
                })
                .put("unresolvedNoAuthority", identityRows.count {
                    it.optJSONObject("authorityResolution")?.optString("status") == ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY.name
                })
                .put("ownerWeekOriginalCandidateAuthorized", ownerWeekRows.count { row ->
                    identityRows.any { it.getString("case") == row.getString("case") &&
                        it.getString("stableKey") == row.getString("stableKey") &&
                        it.getString("selectionRole") == row.getString("selectionRole") &&
                        it.optString("recoveryOutcome") == "ORIGINAL_CANDIDATE_AUTHORIZED" }
                })
                .put("ownerWeekReselectedAuthorized", ownerWeekRows.count { row ->
                    identityRows.any { it.getString("case") == row.getString("case") &&
                        it.getString("stableKey") == row.getString("stableKey") &&
                        it.getString("selectionRole") == row.getString("selectionRole") &&
                        it.optString("recoveryOutcome") == "RESELECTED_TO_AUTHORIZED_OWNER" }
                })
                .put("ownerWeekUserInputRequired", ownerWeekRows.count { row ->
                    identityRows.any { it.getString("case") == row.getString("case") &&
                        it.getString("stableKey") == row.getString("stableKey") &&
                        it.getString("selectionRole") == row.getString("selectionRole") &&
                        it.optString("recoveryOutcome") == "USER_INPUT_REQUIRED" }
                })
                .put("ownerWeekUnresolvedNoAuthority", ownerWeekRows.count { row ->
                    identityRows.any { it.getString("case") == row.getString("case") &&
                        it.getString("stableKey") == row.getString("stableKey") &&
                        it.getString("selectionRole") == row.getString("selectionRole") &&
                        it.optString("recoveryOutcome") == "UNRESOLVED_NO_AUTHORITY" }
                })
                .put("ownerWeekStillUnauthorizedMaterialized", ownerWeekRows.count {
                    it.optBoolean("unauthorizedExecutableRowAfter")
                })
                .put("c26DeniedQualitySparseIntersection", c26Baseline.getJSONObject("b6RejectedQuality")
                    .getInt("exactIntersectionWithSparseAttributions"))
                .put("candidateAlternativesWithZeroPrescriptionUnits", identityRows.count {
                    it.optBoolean("candidateAlternativesHaveZeroPrescriptionUnits")
                })
                .put("executableRowsAfter", ownerWeekRows.sumOf { it.getInt("experimentalRowsAfter") })
                .put("unauthorizedExecutableOwnerWeeksAfter", ownerWeekRows.count { it.optBoolean("unauthorizedExecutableRowAfter") })
                .put("materialDeltaOwnerWeeksAfter", ownerWeekRows.count { it.optBoolean("materialDeltaAfter") })
                .put("unexplainedAddedAttributionsAfter", identityRows.count { it.optBoolean("unexplainedAddedAttributionAfter") })
                .put("frequencyCompletionResurrections", ownerWeekRows.count { it.optBoolean("frequencyOrCompletionResurrected") })
                .put("reviewedHypertrophyExperimentalRowsAfter", hypertrophyAfterRows.size)
                .put("reviewedHypertrophyEvaluationFailedClosed", hypertrophyComparison == null)
                .put("reviewedHypertrophyPrescriptionChangeBefore", 1)
                .put("reviewedHypertrophyPrescriptionChangeAfter", hypertrophyAfterRows.any { it.setCount != 3 }))
            .put("sparseIdentities", JSONArray(identityRows))
            .put("sparseOwnerWeeks", JSONArray(ownerWeekRows))
            .put("reviewedHypertrophyPrescriptionChange", hypertrophy)
            .put("qualityReplacementUnclosedRows", prior.getJSONObject("qualityReplacementRegression").getInt("observedUnclosedRows"))
            .put("productionBehaviorChanged", true)
            .put("productionBehaviorChangedByCensus", false)

        return json.toString(2)
    }
}
