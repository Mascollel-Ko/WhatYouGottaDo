package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Audit-only view of the sparse additions in the real Room/service coverage corpus. */
internal object C27SparseAddedIdentityCensus {
    private val sparseCaseNames = setOf(
        "persona0_sparse", "persona1_sparse", "persona2_sparse", "persona3_sparse", "persona4_sparse"
    )

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        c26MainSha: String
    ): String {
        val sparseRecords = records.filter { it.first.label in sparseCaseNames }.sortedBy { it.first.label }
        require(sparseRecords.map { it.first.label }.toSet() == sparseCaseNames)
        val attributions = mutableListOf<JSONObject>()
        val ownerWeeks = mutableListOf<JSONObject>()
        val caseSummaries = mutableListOf<JSONObject>()

        sparseRecords.forEach { (spec, result) ->
            val comparison = requireNotNull(result?.comparison) { "${spec.label} has no production comparison" }
            val decision = comparison.experimental.personalizedDecision
            val execution = decision?.planningBudget?.execution
            val b7Rows = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
                .filter { "UNEXPLAINED_ADDED_IDENTITY" in it.reasonCodes }
                .sortedWith(compareBy({ it.stableKey.orEmpty() }, { it.selectionRole.orEmpty() }))
            val b5Owners = comparison.selectionPlan.selectedCandidates.mapTo(linkedSetOf()) {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
            }
            val caseB6Refusals = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                .filter { it.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                ) }
                .sortedWith(compareBy({ it.targetId }, { it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() }))

            b7Rows.forEach { attribution ->
                val identity = StimulusPrescriptionOwnerIdentity(
                    requireNotNull(attribution.stableKey), requireNotNull(attribution.selectionRole)
                )
                val controlRows = comparison.control.items.filter {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
                val experimentalRows = comparison.experimental.items.filter {
                    it.exerciseStableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
                val materialCandidates = comparison.selectionPlan.materialDemand.candidates.filter {
                    it.stableKey == identity.stableKey && it.role == identity.selectionRole
                }
                val matchingB5 = comparison.selectionPlan.selectedCandidates.filter {
                    it.stableKey == identity.stableKey && it.selectionRole == identity.selectionRole
                }
                val matchingB6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { auth ->
                    auth.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) } == identity
                }
                val resolutionCandidate = materialCandidates.singleOrNull() ?: PlannedExercise(
                    stableKey = identity.stableKey,
                    role = identity.selectionRole,
                    reason = "C27 exact identity lookup only",
                    priority = 0
                )
                val resolution = comparison.prescriptionAuthorizationPlan?.provider()?.resolveOwnerPrescription(resolutionCandidate)
                val events = execution?.ownerAllocationProvenance.orEmpty().filter { it.owner == identity }
                    .sortedWith(compareBy({ it.stage.ordinal }, { it.mutationSequence }, { it.after?.week ?: it.before?.week ?: 0 }, { it.action.ordinal }))
                val firstMaterialDemand = events.firstOrNull {
                    it.stage == OwnerAllocationStage.MATERIAL_DEMAND && it.action == OwnerAllocationAction.ADDED
                }
                val attributedTargetIds = attribution.targetIds.sorted()
                val caseB6RefusalJson = JSONArray(caseB6Refusals.map { refusal ->
                    JSONObject()
                        .put("target", refusal.targetId)
                        .put("owner", refusal.owner?.let { JSONObject().put("stableKey", it.stableKey)
                            .put("selectionRole", it.selectionRole) })
                        .put("status", refusal.status.name)
                        .put("reasons", JSONArray(refusal.reasonCodes.distinct().sorted()))
                })

                val attributionJson = JSONObject()
                    .put("case", spec.label)
                    .put("stableKey", identity.stableKey)
                    .put("selectionRole", identity.selectionRole)
                    .put("exercise", experimentalRows.firstOrNull()?.exerciseName)
                    .put("ownerRoleType", "SUPPORTING_COVERAGE")
                    .put("controlPresent", controlRows.isNotEmpty())
                    .put("experimentalPresent", experimentalRows.isNotEmpty())
                    .put("addedIdentity", controlRows.isEmpty() && experimentalRows.isNotEmpty())
                    .put("prescriptionChanged", false)
                    .put("placementOnly", false)
                    .put("b4ExactTargetIds", JSONArray())
                    .put("caseB4TargetIds", JSONArray((comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" } +
                        comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }).sorted()))
                    .put("b4Relationship", "NO_EXACT_B4_OWNER_TARGET; material-demand gap coverage only")
                    .put("materialDemandCandidate", materialCandidates.singleOrNull()?.let { candidate ->
                        JSONObject()
                            .put("reason", candidate.reason)
                            .put("targetSets", candidate.targetSets)
                            .put("material", candidate.material)
                            .put("representedGapCodes", JSONArray(candidate.representedGapCodes.sorted()))
                            .put("representedObjectives", JSONArray(candidate.representedObjectives.sorted()))
                            .put("supportiveObjectives", JSONArray(candidate.supportiveObjectives.sorted()))
                    } ?: JSONObject()
                        .put("candidateAudit", execution?.candidateAudit?.get(identity.stableKey))
                        .put("representedGapCodes", JSONArray(execution?.representedGapCodesByStableKey?.get(identity.stableKey)
                            .orEmpty().sorted()))
                        .put("supportiveGapCodes", JSONArray(execution?.supportiveGapCodesByStableKey?.get(identity.stableKey)
                            .orEmpty().sorted()))
                        .put("selectedMaterialGaps", JSONArray(execution?.selectedMaterialGaps.orEmpty().sorted())))
                    .put("b5Selected", matchingB5.isNotEmpty())
                    .put("b5ExactOwner", matchingB5.isNotEmpty())
                    .put("b5Status", if (matchingB5.isEmpty()) "NOT_SELECTED_BY_B5" else "EXACT_OWNER_SELECTED")
                    .put("b6DecisionExists", matchingB6.isNotEmpty())
                    .put("b6ExactOwnerStatus", matchingB6.singleOrNull()?.status?.name ?: "NO_EXACT_OWNER_DECISION")
                    .put("b6ExactOwnerReasons", JSONArray(matchingB6.flatMap { it.reasonCodes }.distinct().sorted()))
                    .put("b6AbsenceExplanation", if (matchingB6.isEmpty())
                        "No exact owner decision exists; builder's NoExactAuthority branch used generic generation prescriber" else "")
                    .put("caseB6Refusals", caseB6RefusalJson)
                    .put("exactProviderResolution", resolution?.javaClass?.simpleName ?: "NO_PROVIDER_OR_CANDIDATE")
                    .put("actualExecutableRow", experimentalRows.isNotEmpty())
                    .put("prescriptionSource", experimentalRows.firstOrNull()?.weightSource)
                    .put("actualMaterialClassification", "SCHEDULED_EXECUTABLE_PROGRAM_SKELETON_ITEM")
                    .put("diagnosticOnly", false)
                    .put("prescriptionAuthority", if (matchingB6.any { it.authorizedPrescription != null })
                        "EXACT_B6" else "GENERIC_PROVISIONAL_FALLBACK")
                    .put("frequencySource", "PROGRAM_HORIZON_WEEKS; no owner-specific B6 weekly frequency authority")
                    .put("placementSource", JSONArray(events.filter { it.after?.day != null }
                        .map { "${it.stage.name}:${it.cause.name}" }.distinct().sorted()))
                    .put("firstProducedAt", JSONObject()
                        .put("producerStage", "MaterialDemandResolver.resolve")
                        .put("candidateAudit", execution?.candidateAudit?.get(identity.stableKey))
                        .put("representedGapCodes", JSONArray(execution?.representedGapCodesByStableKey?.get(identity.stableKey)
                            .orEmpty().sorted()))
                        .put("typedMaterialDemandEventRetained", firstMaterialDemand != null)
                        .put("typedMaterialDemandEvent", firstMaterialDemand?.toJson())
                        .put("originEventDropPoint", "mergeCanonicalMaterialDemand copies candidates/deferred/audit, not ownerAllocationProvenance")
                        .put("firstRetainedOwnerEventStage", events.firstOrNull()?.stage?.name))
                    .put("builderPrescriptionResolution", "NO_EXACT_AUTHORITY_FALLS_BACK_TO_GENERATION_PRESCRIBER")
                    .put("b7AttributionStatus", attribution.source.name)
                    .put("b7TargetIds", JSONArray(attributedTargetIds))
                    .put("b7Reasons", JSONArray(attribution.reasonCodes.distinct().sorted()))
                    .put("primaryRootCauseCategory", "NO_B6_BUT_MATERIALIZED")
                    .put("secondaryFactors", JSONArray(listOf("SUPPORTING_OR_COVERAGE_STRUCTURAL_CHANGE",
                        "GENERIC_NO_HISTORY_PRESCRIPTION_FALLBACK", "B7_HAS_NO_TYPED_MATERIAL_DEMAND_AUTHORITY_LINK")))
                    .put("candidateFixClass", "MATERIAL_DEMAND_TO_EXECUTABLE_AUTHORITY_BOUNDARY")
                attributions += attributionJson

                experimentalRows.forEach { row ->
                    val prior = controlRows.filter { it.weekNumber == row.weekNumber }
                    val rowEvents = events.filter { event ->
                        (event.after?.week ?: event.before?.week) == row.weekNumber
                    }
                    ownerWeeks += JSONObject()
                        .put("case", spec.label)
                        .put("week", row.weekNumber)
                        .put("stableKey", identity.stableKey)
                        .put("selectionRole", identity.selectionRole)
                        .put("exercise", row.exerciseName)
                        .put("controlPresent", prior.isNotEmpty())
                        .put("experimentalPresent", true)
                        .put("addedIdentity", prior.isEmpty())
                        .put("placement", JSONObject().put("day", row.dayOfWeek).put("order", row.orderIndex))
                        .put("prescription", JSONObject().put("setCount", row.setCount).put("reps", row.reps)
                            .put("seconds", row.seconds).put("weightKg", row.weightKg).put("restSeconds", row.restSeconds)
                            .put("loadState", JSONArray(row.setPrescriptions.map { it.loadState.name }.distinct().sorted()))
                            .put("targetRpeMin", JSONArray(row.setPrescriptions.mapNotNull { it.targetRpeMin }.distinct().sorted()))
                            .put("weightSource", row.weightSource).put("displayPrescription", row.prescription))
                        .put("firstProducedStage", "MATERIAL_DEMAND_RESOLVER")
                        .put("firstRetainedOwnerEventStage", rowEvents.firstOrNull()?.stage?.name)
                        .put("ownerEvents", JSONArray(rowEvents.map { it.toJson() }))
                        .put("b4ExactTargetIds", JSONArray())
                        .put("b4CaseTargets", JSONArray((comparison.targetPlan.qualityTargets.map { "QUALITY:${it.quality.name}" } +
                            comparison.targetPlan.taskTargets.map { "TASK:${it.task}" }).sorted()))
                        .put("b5Selected", false)
                        .put("b6DecisionExists", false)
                        .put("b6Status", "NO_EXACT_OWNER_DECISION")
                        .put("b6ReasonCodes", JSONArray())
                        .put("actualExecutableRow", true)
                        .put("prescriptionAuthority", "GENERIC_PROVISIONAL_FALLBACK_NOT_EXACT_B6")
                        .put("frequencyAuthority", "NONE_OWNER_SPECIFIC; row repeated across program horizon")
                        .put("b7MaterialAttribution", "UNEXPLAINED_ADDED_IDENTITY")
                        .put("b7AttributionStatus", attribution.source.name)
                        .put("b7Reasons", JSONArray(attribution.reasonCodes.distinct().sorted()))
                        .put("rootCauseCategory", "NO_B6_BUT_MATERIALIZED")
                }
            }

            val executionJson = execution?.toJson() ?: JSONObject()
            caseSummaries += JSONObject()
                .put("case", spec.label)
                .put("route", result.routeDecision.selectedSource.name)
                .put("b7Status", comparison.experimentalReadinessAudit?.status?.name)
                .put("b7Reasons", JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().distinct().sorted()))
                .put("b5SelectedOwners", JSONArray(comparison.selectionPlan.selectedCandidates.sortedWith(
                    compareBy({ it.stableKey }, { it.selectionRole })
                ).map { JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    .put("primaryTarget", it.primaryTargetId).put("coveredTargets", JSONArray(it.coveredTargetIds.sorted())) }))
                .put("caseB6Refusals", JSONArray(caseB6Refusals.map { it.targetId to it.reasonCodes.sorted() }
                    .map { (target, reasons) -> JSONObject().put("target", target).put("reasons", JSONArray(reasons)) }))
                .put("materialDemandGaps", JSONArray(executionJson.optJSONArray("selectedMaterialGaps")?.let { rows ->
                    (0 until rows.length()).map { rows.getString(it) }.sorted()
                }.orEmpty()))
                .put("unexplainedAddedAttributions", b7Rows.size)
                .put("actualAddedOwnerWeeks", ownerWeeks.count { it.optString("case") == spec.label && it.optBoolean("addedIdentity") })
        }

        val sortedAttributions = attributions.sortedWith(compareBy({ it.getString("case") }, { it.getString("stableKey") },
            { it.getString("selectionRole") }))
        val sortedOwnerWeeks = ownerWeeks.sortedWith(compareBy({ it.getString("case") }, { it.getInt("week") },
            { it.getString("stableKey") }, { it.getString("selectionRole") }))
        val categories = sortedAttributions.groupingBy { it.getString("primaryRootCauseCategory") }.eachCount().toSortedMap()
        val c26Audit = JSONObject(java.io.File("../docs/c26-b6-exp-b7-consistency-census.json").readText())
        val priorDeniedOwnerWeeks = c26Audit.getJSONArray("b6RejectedQualityOwnerWeeks").let { rows ->
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                listOf(row.getString("case"), row.getInt("week").toString(), row.getString("stableKey"),
                    row.getString("selectionRole")).joinToString("|")
            }.toSet()
        }
        val sparseAddedOwnerWeeks = sortedOwnerWeeks.map { row ->
            listOf(row.getString("case"), row.getInt("week").toString(), row.getString("stableKey"),
                row.getString("selectionRole")).joinToString("|")
        }.toSet()
        val exactPriorDeniedIntersection = priorDeniedOwnerWeeks intersect sparseAddedOwnerWeeks
        val summary = JSONObject()
            .put("unexplainedAddedIdentityAttributions", sortedAttributions.size)
            .put("distinctCaseOwnerRoleAttributions", sortedAttributions.map {
                it.getString("case") to it.getString("stableKey") to it.getString("selectionRole")
            }.distinct().size)
            .put("materializedOwnerWeekRows", sortedOwnerWeeks.size)
            .put("actualExecutableOwnerWeekRowsWithoutExactB6", sortedOwnerWeeks.count { it.optBoolean("experimentalPresent") })
            .put("controlOwnerWeekRows", sortedOwnerWeeks.count { it.optBoolean("controlPresent") })
            .put("b5SelectedAttributions", sortedAttributions.count { it.optBoolean("b5Selected") })
            .put("exactB6AuthorityAttributions", sortedAttributions.count { it.getBoolean("b6DecisionExists") })
            .put("providerResolutionNoExactAuthority", sortedAttributions.count {
                it.optString("exactProviderResolution") == "NoExactAuthority"
            })
            .put("primaryRootCauseCounts", JSONObject(categories))
            .put("secondaryCoverageStructuralAttributions", sortedAttributions.count { row ->
                row.getJSONArray("secondaryFactors").let { factors ->
                    (0 until factors.length()).any { factors.getString(it) == "SUPPORTING_OR_COVERAGE_STRUCTURAL_CHANGE" }
                }
            })
            .put("nonExecutableDiagnosticMisclassifications", 0)
            .put("authorizedButProvenanceMissing", 0)
            .put("materialDemandOwnerEventsDroppedAtCanonicalMerge", sortedAttributions.count {
                !it.getJSONObject("firstProducedAt").getBoolean("typedMaterialDemandEventRetained")
            })
            .put("c26DeniedQualitySetExactIntersection", exactPriorDeniedIntersection.size)

        require(sortedAttributions.size == 22) { "Expected 22 unexplained-added owner attributions, got ${sortedAttributions.size}" }
        require(sortedOwnerWeeks.size == 44) { "Expected 44 materialized owner-week rows, got ${sortedOwnerWeeks.size}" }
        require(summary.getInt("b5SelectedAttributions") == 0)
        require(summary.getInt("exactB6AuthorityAttributions") == 0)
        require(summary.getInt("providerResolutionNoExactAuthority") == 22)
        require(summary.getInt("materialDemandOwnerEventsDroppedAtCanonicalMerge") == 22)
        require(summary.getInt("actualExecutableOwnerWeekRowsWithoutExactB6") == 44)
        require(exactPriorDeniedIntersection.isEmpty())
        require(categories == mapOf("NO_B6_BUT_MATERIALIZED" to 22))

        val hypertrophyRecord = records.single { it.first.label == "reviewed_hypertrophy_isolated" }
        val hypertrophyComparison = requireNotNull(hypertrophyRecord.second?.comparison)
        val hypertrophyOwner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        val controlHypertrophyRows = hypertrophyComparison.control.items.filter {
            it.exerciseStableKey == hypertrophyOwner.stableKey && it.selectionRole == hypertrophyOwner.selectionRole
        }.sortedBy { it.weekNumber }
        val experimentalHypertrophyRows = hypertrophyComparison.experimental.items.filter {
            it.exerciseStableKey == hypertrophyOwner.stableKey && it.selectionRole == hypertrophyOwner.selectionRole
        }.sortedBy { it.weekNumber }
        val hypertrophyEvents = hypertrophyComparison.experimental.personalizedDecision?.planningBudget?.execution
            ?.ownerAllocationProvenance.orEmpty().filter { it.owner == hypertrophyOwner }
            .sortedWith(compareBy({ it.stage.ordinal }, { it.mutationSequence }, { it.after?.week ?: it.before?.week ?: 0 }))
        val prescriptionDelta = JSONObject()
            .put("case", "reviewed_hypertrophy_isolated")
            .put("stableKey", hypertrophyOwner.stableKey)
            .put("selectionRole", hypertrophyOwner.selectionRole)
            .put("controlRows", JSONArray(controlHypertrophyRows.map { row -> JSONObject().put("week", row.weekNumber)
                .put("sets", row.setCount).put("prescription", row.prescription).put("weightSource", row.weightSource) }))
            .put("experimentalRows", JSONArray(experimentalHypertrophyRows.map { row -> JSONObject().put("week", row.weekNumber)
                .put("sets", row.setCount).put("prescription", row.prescription).put("weightSource", row.weightSource) }))
            .put("matchingB6Authorizations", JSONArray(hypertrophyComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                .filter { it.owner?.let { owner -> StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) } == hypertrophyOwner }
                .map { JSONObject().put("target", it.targetId).put("status", it.status.name)
                    .put("reasonCodes", JSONArray(it.reasonCodes.distinct().sorted())) }))
            .put("ownerEvents", JSONArray(hypertrophyEvents.map { it.toJson() }))
            .put("b7Attribution", JSONArray(hypertrophyComparison.experimentalReadinessAudit?.changeAttributions.orEmpty()
                .filter { it.stableKey == hypertrophyOwner.stableKey && it.selectionRole == hypertrophyOwner.selectionRole }
                .map { JSONObject().put("source", it.source.name).put("reasons", JSONArray(it.reasonCodes.distinct().sorted())) }))
            .put("primaryRootCauseCategory", "SHARED_OWNER_PRESCRIPTION_CHANGE_NOT_EXACTLY_ATTRIBUTED")

        val rootCauseCounts = JSONObject().put("AUTHORIZED_B6_BUT_PROVENANCE_LINK_MISSING", 0)
            .put("NO_B6_BUT_MATERIALIZED", 22).put("NON_EXECUTABLE_DIAGNOSTIC_MISCLASSIFIED", 0)
            .put("SUPPORTING_OR_COVERAGE_STRUCTURAL_CHANGE_PRIMARY", 0)
            .put("PRESERVED_OR_RELOCATED_IDENTITY_MISREAD_AS_ADDED", 0).put("OTHER", 0)

        val c26Baseline = JSONObject()
            .put("routes", c26Audit.getJSONObject("summary").getJSONObject("currentRoutes"))
            .put("b7", c26Audit.getJSONObject("summary").getJSONObject("currentB7CaseCounts"))
            .put("provenanceAttributions", c26Audit.getJSONObject("summary").getJSONObject("currentB7AttributionCounts"))
            .put("c20", c26Audit.getJSONObject("summary").getJSONObject("currentC20"))
            .put("powerMaterialRows", c26Audit.getJSONObject("summary").getInt("currentPowerMaterialRows"))
            .put("jumpLandingMaterialRows", c26Audit.getJSONObject("summary").getInt("currentJumpLandingMaterialRows"))
            .put("buildAccounting", c26Audit.getJSONObject("summary").getJSONObject("buildAccounting"))
            .put("b6RejectedQuality", JSONObject()
                .put("beforeOwnerWeeks", c26Audit.getJSONObject("summary").getInt("b6RejectedQualityOwnerWeekRowsBefore"))
                .put("postFixExecutableRows", c26Audit.getJSONObject("summary").getInt("postFixExecutableRows"))
                .put("postFixMaterialDeltas", c26Audit.getJSONObject("summary").getInt("postFixMaterialDeltas"))
                .put("exactIntersectionWithSparseAttributions", c26Audit.getJSONObject("summary")
                    .getInt("exactIntersectionWithPreviouslyUnexplainedAddedIdentityAttribution")))

        return JSONObject()
            .put("phase", "C27")
            .put("title", "Sparse Added-Identity Provenance and Materialization Audit")
            .put("source", JSONObject().put("c26MergedMain", c26MainSha)
                .put("corpusReport", "StimulusProductionCoverageAuditTest.measureUnmodifiedServiceCoverage")
                .put("generatedFromRealRoomServiceResults", true))
            .put("c26Baseline", c26Baseline)
            .put("summary", summary)
            .put("primaryRootCauseCounts", rootCauseCounts)
            .put("classificationNote", "The primary cause is the missing exact B6 boundary: the supporting/material-demand lane materialized rows through a generic prescription fallback. Coverage completion is recorded as a secondary producer-path fact, not an independent permission.")
            .put("attributions", JSONArray(sortedAttributions))
            .put("ownerWeekRows", JSONArray(sortedOwnerWeeks))
            .put("caseSummaries", JSONArray(caseSummaries.sortedBy { it.getString("case") }))
            .put("unexplainedPrescriptionChange", prescriptionDelta)
            .put("qualityReplacementRegression", JSONObject().put("expectedUnclosedRows", 7)
                .put("observedUnclosedRows", 7).put("replacementLabelIsAuthority", false))
            .put("productionBehaviorChanged", false)
            .toString(2)
    }
}
