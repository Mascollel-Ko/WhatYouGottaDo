package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C37 corpus evidence; it does not itself grant cutover authority. */
internal object C37PowerJumpIntegratedAuthorityCensus {
    private const val POWER_OWNER_KEY = "ex_314df428"

    /** Small reviewable projection for checked-in docs; the full diagnostic stays in build/reports. */
    fun renderCompactSummary(census: String): String {
        val source = JSONObject(census)
        val cases = source.getJSONArray("cases")
        val compactCases = JSONArray()
        for (index in 0 until cases.length()) {
            val row = cases.getJSONObject(index)
            val b4 = row.optJSONObject("b4PowerDoseDecision")
            val b6 = row.optJSONObject("b6PowerAuthorization")
            val materialization = row.optJSONObject("b6PowerMaterialization")
            val generated = row.optJSONArray("generatedPowerRows") ?: JSONArray()
            val generatedRows = JSONArray()
            for (rowIndex in 0 until generated.length()) {
                val material = generated.getJSONObject(rowIndex)
                generatedRows.put(JSONObject().put("week", material.optInt("week"))
                    .put("day", material.optInt("day")).put("stableKey", material.optString("stableKey"))
                    .put("selectionRole", material.optString("selectionRole"))
                    .put("sets", material.optInt("setCount")).put("reps", material.optInt("reps"))
                    .put("setPrescriptions", material.optJSONArray("sets") ?: JSONArray())
                    .put("restSeconds", material.optInt("restSeconds"))
                    .put("loadStates", JSONArray((0 until (material.optJSONArray("sets")?.length() ?: 0))
                        .map { index -> material.getJSONArray("sets").getJSONObject(index).optString("loadState") }.distinct()))
                    .put("weightKg", JSONArray((0 until (material.optJSONArray("sets")?.length() ?: 0))
                        .map { index -> material.getJSONArray("sets").getJSONObject(index).optDouble("weightKg") }))
                    .put("weightSource", material.optString("weightSource")))
            }
            val owners = JSONArray()
            val selected = row.optJSONArray("b5SelectedPowerOwners") ?: JSONArray()
            for (ownerIndex in 0 until selected.length()) {
                val owner = selected.getJSONObject(ownerIndex)
                owners.put(JSONObject().put("stableKey", owner.optString("stableKey"))
                    .put("selectionRole", owner.optString("selectionRole"))
                    .put("selectionReasons", owner.optJSONArray("selectionReasons") ?: JSONArray()))
            }
            compactCases.put(JSONObject().put("case", row.optString("case"))
                .put("preflight", row.optString("preflight"))
                .put("route", row.optString("route"))
                .put("b1Need", row.optJSONObject("b1PowerNeed")?.let { need -> JSONObject()
                    .put("decision", need.optString("decision")).put("relevance", need.optString("relevance"))
                    .put("confidence", need.optString("confidence")).put("coverage", need.optString("coverage"))
                    .put("reasonCodes", need.optJSONArray("reasonCodes") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("b3Decision", row.optJSONObject("b3PowerDecision")?.let { decision -> JSONObject()
                    .put("needDecision", decision.optString("needDecision"))
                    .put("strategy", decision.optString("strategy")) } ?: JSONObject.NULL)
                .put("b4Target", row.optJSONObject("b4PowerTarget")?.let { target -> JSONObject()
                    .put("numericAuthority", target.optString("numericAuthority"))
                    .put("priority", target.optString("priority")) } ?: JSONObject.NULL)
                .put("b4Dose", b4?.let { decision -> JSONObject()
                    .put("status", decision.optString("status"))
                    .put("weeklyCap", decision.opt("applicableWeeklyCap"))
                    .put("approvedWeeklySets", decision.optInt("approvedWeeklySetUnits"))
                    .put("existingSets", decision.optInt("existingAuthorizedSetUnits"))
                    .put("lowerResistanceBand", decision.opt("lowerResistanceBand"))
                    .put("upperResistanceBand", decision.opt("upperResistanceBand"))
                    .put("badmintonBand", decision.optString("badmintonBand"))
                    .put("reasonCodes", decision.optJSONArray("reasonCodes") ?: JSONArray())
                    .put("evidence", decision.optJSONArray("evidence") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("b5Owners", owners)
                .put("b6", b6?.let { authorization -> JSONObject()
                    .put("status", authorization.optString("status"))
                    .put("authorized", authorization.optBoolean("authorizedPrescriptionPresent"))
                    .put("reasonCodes", authorization.optJSONArray("reasonCodes") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("materialization", materialization?.let { audit -> JSONObject()
                    .put("state", audit.optString("state"))
                    .put("authorizedWeeklySets", audit.optInt("authorizedWeeklySetUnits"))
                    .put("materializedWeeklySets", audit.optInt("materializedWeeklySetUnits")) } ?: JSONObject.NULL)
                .put("generatedRows", generatedRows)
                .put("targetOutcome", row.optJSONObject("powerTargetOutcome")?.let { outcome -> JSONObject()
                    .put("status", outcome.optString("status"))
                    .put("directlyAffected", outcome.optBoolean("directlyAffected"))
                    .put("reasonCodes", outcome.optJSONArray("reasonCodes") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("b7", row.optJSONObject("b7")?.let { b7 -> JSONObject()
                    .put("status", b7.optString("status"))
                    .put("provenanceClosed", b7.optBoolean("changeProvenanceClosed"))
                    .put("reasons", b7.optJSONArray("reasonCodes") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("b8", row.optJSONObject("b8")?.let { b8 -> JSONObject()
                    .put("status", b8.optString("status")).put("scope", b8.optString("scope"))
                    .put("reasons", b8.optJSONArray("reasonCodes") ?: JSONArray())
                    .put("authorizedMovementTargetOwnerIdentities",
                        b8.optJSONArray("authorizedMovementTargetOwnerIdentities") ?: JSONArray()) } ?: JSONObject.NULL)
                .put("jumpLanding", row.optJSONObject("jumpLanding") ?: JSONObject.NULL))
        }
        return JSONObject().put("schema", "c37-power-jump-integrated-authority-summary-v1")
            .put("startSha", source.optString("c37StartingHead"))
            .put("versions", source.optJSONObject("versions") ?: JSONObject())
            .put("policyConclusion", source.optString("policyConclusion"))
            .put("counts", source.optJSONObject("counts") ?: JSONObject())
            .put("cases", compactCases)
            .put("scope", "exact B8/B9 bounded Power/Jump route only; every other case remains governed by its existing B7/B8 result")
            .toString(2)
    }

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult? >>,
        planningByCase: Map<String, CanonicalStimulusPlanningResult>,
        contextByCase: Map<String, PreparedCanonicalGenerationContext>,
        metadataRepository: CanonicalExerciseMetadataRepository
    ): String {
        val physicalRelations = metadataRepository.physicalQualityRelations()
        val badmintonObjectiveCatalog = metadataRepository.badmintonObjectiveCatalog()
        val reviewedMembership = RecordBasedReviewedPolicy.badmintonKeys.entries
            .firstOrNull { (_, keys) -> POWER_OWNER_KEY in keys }
        val reviewedGuide = reviewedMembership?.let { RecordBasedReviewedPolicy.badminton(it.key) }
        val reviewedCategory = reviewedMembership?.key
        val reviewedGuideJson = reviewedGuide?.let { JSONObject()
            .put("setCount", it.setCount).put("reps", it.reps).put("seconds", it.seconds)
            .put("restSeconds", it.restSeconds).put("text", it.text).put("weightSource", it.weightSource)
        }
        val caseRows = JSONArray()
        var generatedCount = 0
        var directionOnlyBefore = 0
        var numericPowerAfter = 0
        var exactB6Authorities = 0
        var userApprovedPolicyAuthorities = 0
        var personalNumericAuthorities = 0
        var reviewedStarterAuthorities = 0
        var stillDirectionOnly = 0
        var fullyMaterializedPower = 0
        var selectedPowerOwnerRows = 0
        var generatedPowerRows = 0
        val exactOwnerPowerObservationIds = linkedSetOf<String>()

        records.sortedBy { it.first.label }.forEach { (spec, result) ->
            val row = JSONObject().put("case", spec.label)
            if (result == null) {
                row.put("preflight", "REJECTED_NO_CONFIRMED_HISTORY")
                row.put("powerTarget", JSONObject.NULL)
                row.put("jumpLanding", JSONObject.NULL)
                row.put("selectedPowerOwners", JSONArray())
                row.put("generatedPowerRows", JSONArray())
                caseRows.put(row)
                return@forEach
            }
            generatedCount++
            val planning = requireNotNull(planningByCase[spec.label]) { "${spec.label}: missing B1-B4 evidence" }
            val context = requireNotNull(contextByCase[spec.label]) { "${spec.label}: missing production source snapshot" }
            if (planning.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.POWER }?.numericAuthority ==
                StimulusTargetNumericAuthority.DIRECTION_ONLY) directionOnlyBefore++
            val powerNeed = planning.athleteStimulusNeedProfile.qualityNeeds.firstOrNull { it.quality == TrainableQuality.POWER }
            val powerDecision = planning.decisionPortfolio.qualityDecisions.firstOrNull { it.quality == TrainableQuality.POWER }
            val comparison = result.comparison
            val jumpLanding = jumpLandingEvidence(planning, comparison)
            if (comparison == null) {
                // C28 may correctly leave a case with no executable canonical demand and
                // fall back intact to CONTROL. Preserve its B1-B4 Power evidence in the
                // audit without pretending B5/B6 or EXP comparison ran.
                val powerTarget = planning.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.POWER }
                if (powerTarget?.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) stillDirectionOnly++
                row.put("evaluation", "CONTROL_FALLBACK_NO_EXECUTABLE_EXP_MATERIAL")
                    .put("powerNeed", powerNeed?.let { JSONObject().put("relevance", it.relevance.name)
                        .put("decision", it.decision.name).put("confidence", it.confidence.name)
                        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                    .put("powerTarget", powerTarget?.let { JSONObject().put("strategy", it.strategy.name)
                        .put("numericAuthority", it.numericAuthority.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) }
                        ?: JSONObject.NULL)
                    .put("jumpLanding", jumpLanding)
                    .put("selectedPowerOwners", JSONArray())
                    .put("generatedPowerRows", JSONArray())
                    .put("b4PowerDoseDecision", JSONObject.NULL)
                    .put("b6PowerResolution", JSONObject.NULL)
                    .put("b6PowerAuthorization", JSONObject.NULL)
                    .put("b6PowerMaterialization", JSONObject.NULL)
                    .put("route", result.routeDecision.selectedSource.name)
                    .put("upstreamFailureReason", result.upstreamFailureReason)
                    .put("upstreamFailureDetails", JSONArray(result.upstreamFailureDetails))
                caseRows.put(row)
                return@forEach
            }
            val powerTarget = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.POWER }
            val powerB4Decision = comparison.targetPlan.powerJumpDoseDecisions.singleOrNull { it.targetId == "QUALITY:POWER" }
            if (powerTarget?.numericAuthority == StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY &&
                powerB4Decision?.status == PowerJumpDoseStatus.AUTHORIZED && powerB4Decision.approvedWeeklySetUnits > 0) {
                numericPowerAfter++
                userApprovedPolicyAuthorities++
            }
            if (powerTarget?.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                )) {
                numericPowerAfter++
                personalNumericAuthorities++
            }
            if (powerTarget?.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) stillDirectionOnly++

            val selectedPowerOwners = comparison.selectionPlan.selectedCandidates.filter { "QUALITY:POWER" in it.coveredTargetIds }
            selectedPowerOwnerRows += selectedPowerOwners.size
            val exactPowerOwnerHistory = context.snapshot.allConfirmedSets.filter { it.stableKey in selectedPowerOwners.map { owner -> owner.stableKey } }
            val allDirectPowerObservations = context.snapshot.stimulusExposureLedger.setObservations.filter { observation ->
                context.snapshot.stimulusExposureLedger.facetProfilesByStableKey[observation.facetProfileKey]
                    ?.physicalQualities?.any { it.qualityId == TrainableQuality.POWER && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } == true
            }
            val exactDirectPowerObservations = allDirectPowerObservations.filter { observation ->
                observation.source.stableKey in selectedPowerOwners.map { it.stableKey }
            }
            exactDirectPowerObservations.mapTo(exactOwnerPowerObservationIds) { it.source.portableObservationId }

            val powerResolution = comparison.prescriptionRealizationPlan?.resolutions?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val powerAuthorization = comparison.prescriptionAuthorizationPlan?.authorizations?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val powerMaterialization = comparison.prescriptionMaterializationAudits.firstOrNull { it.targetId == "QUALITY:POWER" }
            if (powerAuthorization?.authorizedPrescription != null) exactB6Authorities++
            if (powerAuthorization?.reasonCodes?.contains("POWER_REVIEWED_TASK_STARTER_AUTHORIZED") == true) reviewedStarterAuthorities++
            if (powerMaterialization?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED) fullyMaterializedPower++

            val generatedRows = comparison.experimental.items.filter { item ->
                item.exerciseStableKey in selectedPowerOwners.map { it.stableKey } &&
                    selectedPowerOwners.any { it.selectionRole == item.selectionRole && it.stableKey == item.exerciseStableKey }
            }.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey))
            generatedPowerRows += generatedRows.size

            val powerBand = planning.qualityDoseHistory.bands[TrainableQuality.POWER]
            val powerB2 = JSONObject()
                .put("baselineObservability", planning.qualityDoseHistory.baselineObservability[TrainableQuality.POWER]?.name)
                .put("evidenceBasis", planning.qualityDoseHistory.evidenceBasis[TrainableQuality.POWER]?.name)
                .put("bandSource", powerBand?.source?.name)
                .put("eligibleWeeks", powerBand?.eligibleWeekCount ?: 0)
                .put("hasPersonalDirectBaseline", powerBand?.hasPersonalDirectBaseline ?: false)
                .put("weeklyDirectUnitsMedian", powerBand?.weeklyDirectUnitsMedian)
                .put("weeklyDirectSessionsMedian", powerBand?.weeklyDirectSessionsMedian)
                .put("directExposureWeekFrequency", powerBand?.directExposureWeekFrequency)
                .put("exactOwnerConfirmedSets", JSONArray(exactPowerOwnerHistory.sortedWith(compareBy({ it.date }, { it.setIndex }))
                    .map { set -> JSONObject().put("date", set.date.toString()).put("stableKey", set.stableKey)
                        .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds)
                        .put("rpe", set.rpe).put("setIndex", set.setIndex) }))
                .put("directPowerObservations", JSONArray(allDirectPowerObservations.sortedWith(compareBy({ it.source.stableKey }, { it.source.date }, { it.source.portableObservationId }))
                    .map { observation -> JSONObject().put("stableKey", observation.source.stableKey)
                        .put("observationId", observation.source.portableObservationId).put("date", observation.source.date.toString())
                        .put("activityKind", observation.activityKind.name).put("reps", observation.reps)
                        .put("weightKg", observation.weightKg).put("seconds", observation.seconds).put("rpe", observation.rpe)
                        .put("classification", observation.realizedStimulusClassification.kind.name)
                        .put("classificationAuthority", observation.classificationAuthority.name) }))
                .put("exactOwnerDirectPowerObservationCount", exactDirectPowerObservations.size)
                .put("sameQualityOtherOwnerObservationCount", allDirectPowerObservations.size - exactDirectPowerObservations.size)

            val ownerRows = JSONArray(selectedPowerOwners.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map { owner ->
                JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                    .put("coveredTargets", JSONArray(owner.coveredTargetIds.sorted()))
                    .put("selectionReasons", JSONArray(owner.selectionReasons.sorted()))
                    .put("probeCompatibility", owner.probePrescriptionCompatibility.name)
                    .put("targetSetsFromExistingPrescription", owner.targetSetsFromExistingPrescription)
            })
            val powerRelations = physicalRelations.filter { it.exerciseStableKey == POWER_OWNER_KEY && it.qualityId == TrainableQuality.POWER }
            val objectiveRelations = badmintonObjectiveCatalog.relations(POWER_OWNER_KEY)
            val targetOutcome = comparison.experimentalReadinessAudit?.targetOutcomes?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val b1Need = powerNeed?.let { need -> JSONObject().put("relevance", need.relevance.name)
                .put("decision", need.decision.name).put("confidence", need.confidence.name)
                .put("reasonCodes", JSONArray(need.reasonCodes.sorted())).put("evidence", JSONArray(need.evidence.sorted()))
                .put("directUnits28d", need.exposure.current28d.directUnits)
                .put("directSessions28d", need.exposure.current28d.directSessions)
                .put("supportiveUnits28d", need.exposure.current28d.supportiveUnits)
                .put("coverage", need.exposure.coverage.name)
            } ?: JSONObject.NULL
            val b1Tasks = JSONArray(planning.athleteStimulusNeedProfile.sportTaskNeeds.sortedBy { it.task }.map { need ->
                JSONObject().put("task", need.task).put("relevance", need.relevance.name)
                    .put("decision", need.decision.name).put("confidence", need.confidence.name)
                    .put("reasonCodes", JSONArray(need.reasonCodes.sorted())).put("evidence", JSONArray(need.evidence.sorted()))
            })
            val b3TaskDecisions = JSONArray(planning.decisionPortfolio.taskDecisions.sortedBy { it.task }.map { decision ->
                JSONObject().put("task", decision.task).put("strategy", decision.strategy.name)
                    .put("needDecision", decision.needDecision.name).put("priority", decision.priority.name)
                    .put("reasonCodes", JSONArray(decision.reasonCodes.sorted())).put("evidence", JSONArray(decision.evidence.sorted()))
            })
            val b4TaskTargets = JSONArray(comparison.targetPlan.taskTargets.sortedBy { it.task }.map { target ->
                JSONObject().put("task", target.task).put("strategy", target.strategy.name)
                    .put("numericAuthority", target.numericAuthority.name)
                    .put("weeklyDirectUnitsTarget", rangeJson(target.weeklyDirectUnitsTarget))
                    .put("weeklyDirectSessionsTarget", rangeJson(target.weeklyDirectSessionsTarget))
                    .put("reasonCodes", JSONArray(target.reasonCodes.sorted()))
            })
            val currentPrescriptionSource = selectedPowerOwners.firstOrNull()?.let { owner ->
                PerformancePrescriptionResolver.resolve(context.snapshot, owner.stableKey)
            }
            val selectedPowerKey = selectedPowerOwners.firstOrNull()?.stableKey ?: POWER_OWNER_KEY
            val canonicalExercise = context.snapshot.exercises[selectedPowerKey]
            val runtimeMetadata = context.snapshot.metadata[selectedPowerKey]
            val generatedPowerRowsJson = JSONArray(generatedRows.map { item ->
                JSONObject().put("week", item.weekNumber).put("day", item.dayOfWeek).put("order", item.orderIndex)
                    .put("stableKey", item.exerciseStableKey).put("selectionRole", item.selectionRole)
                    .put("quality", "POWER").put("setCount", item.setCount).put("reps", item.reps)
                    .put("sets", JSONArray(item.setPrescriptions.map { set -> JSONObject().put("setIndex", set.setIndex)
                        .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds)
                        .put("loadState", set.loadState.name).put("targetRpe", set.targetRpeMin) }))
                    .put("restSeconds", item.restSeconds).put("weightSource", item.weightSource)
                    .put("prescription", item.prescription)
            })
            val b6Auth = powerAuthorization?.let { auth -> JSONObject()
                .put("quality", auth.quality?.name).put("owner", auth.owner?.let { owner -> JSONObject()
                    .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole) })
                .put("status", auth.status.name).put("reasonCodes", JSONArray(auth.reasonCodes.sorted()))
                .put("authorizedPrescriptionPresent", auth.authorizedPrescription != null)
                .put("inputPrescription", prescriptionJson(auth.inputPrescription))
                .put("authorizedPrescription", prescriptionJson(auth.authorizedPrescription))
            } ?: JSONObject.NULL
            val resolution = powerResolution?.let { value -> JSONObject()
                .put("targetId", value.targetId).put("quality", value.quality?.name)
                .put("evidenceBasis", value.evidenceBasis.name).put("strategy", value.strategy?.name)
                .put("numericAuthority", value.numericAuthority.name).put("status", value.status.name)
                .put("owner", value.owner?.let { JSONObject().put("stableKey", it.stableKey)
                    .put("selectionRole", it.selectionRole) } ?: JSONObject.NULL)
                .put("reasonCodes", JSONArray(value.reasonCodes.sorted()))
            } ?: JSONObject.NULL
            val materialization = powerMaterialization?.let { JSONObject()
                .put("state", it.state.name).put("authorizedWeeklySetUnits", it.authorizedWeeklySetUnits)
                .put("materializedWeeklySetUnits", it.materializedWeeklySetUnits)
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL
            val powerTargetJson = powerTarget?.let { target -> JSONObject()
                .put("strategy", target.strategy.name).put("priority", target.priority.name)
                .put("numericAuthority", target.numericAuthority.name).put("baselineSource", target.baselineSource?.name)
                .put("baselineConfidence", target.baselineConfidence?.name)
                .put("weeklyDirectUnitsTarget", rangeJson(target.weeklyDirectUnitsTarget))
                .put("weeklyDirectSessionsTarget", rangeJson(target.weeklyDirectSessionsTarget))
                .put("exposureWeekFrequencyReference", target.exposureWeekFrequencyReference)
                .put("reasonCodes", JSONArray(target.reasonCodes.sorted())).put("evidence", JSONArray(target.evidence.sorted()))
            } ?: JSONObject.NULL

            row.put("preflight", "GENERATED")
                .put("b1PowerNeed", b1Need)
                .put("b1TaskNeeds", b1Tasks)
                .put("b2PowerHistory", powerB2)
                .put("b3PowerDecision", powerDecision?.let { JSONObject()
                    .put("strategy", it.strategy.name).put("needDecision", it.needDecision.name)
                    .put("baselineAvailable", it.baselineAvailable).put("hasPersonalDirectBaseline", it.hasPersonalDirectBaseline)
                    .put("numericBaselineUsable", it.numericBaselineUsable).put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    .put("evidence", JSONArray(it.evidence.sorted())) } ?: JSONObject.NULL)
                .put("b3TaskDecisions", b3TaskDecisions)
                .put("b4PowerTarget", powerTargetJson)
                .put("b4PowerDoseDecision", powerB4Decision?.let { decision -> JSONObject()
                    .put("status", decision.status.name).put("numericAuthority", decision.numericAuthority.name)
                    .put("approvedWeeklySetUnits", decision.approvedWeeklySetUnits)
                    .put("applicableWeeklyCap", decision.applicableWeeklyCap ?: JSONObject.NULL)
                    .put("existingAuthorizedSetUnits", decision.existingAuthorizedSetUnits)
                    .put("lowerResistanceBand", decision.lowerResistanceBand?.name ?: JSONObject.NULL)
                    .put("upperResistanceBand", decision.upperResistanceBand?.name ?: JSONObject.NULL)
                    .put("badmintonBand", decision.badmintonBand.name).put("provenance", decision.provenance)
                    .put("reasonCodes", JSONArray(decision.reasonCodes.sorted()))
                    .put("evidence", JSONArray(decision.evidence.sorted())) } ?: JSONObject.NULL)
                .put("b4TaskTargets", b4TaskTargets)
                .put("b5SelectedPowerOwners", ownerRows)
                .put("canonicalPowerRelations", JSONArray(powerRelations.map { relation -> JSONObject()
                    .put("relationId", relation.relationId).put("quality", relation.qualityId.name)
                    .put("relationLevel", relation.relationLevel.name).put("region", relation.regionQualifier.name)
                    .put("mode", relation.modeQualifier.name).put("prescriptionDependent", relation.prescriptionDependent)
                    .put("provenance", relation.provenance).put("reviewStatus", relation.reviewStatus)
                    .put("notes", relation.notes) }))
                .put("badmintonObjectiveRelations", JSONArray(objectiveRelations.map(::objectiveJson)))
                .put("reviewedBadmintonCategory", reviewedCategory?.name)
                .put("reviewedCategoryMembers", JSONArray(reviewedCategory?.let { RecordBasedReviewedPolicy.badmintonKeys.getValue(it).sorted() }.orEmpty()))
                .put("reviewedPrescriptionGuide", reviewedGuideJson ?: JSONObject.NULL)
                .put("exerciseSemantics", JSONObject()
                    .put("stableKey", selectedPowerKey)
                    .put("name", canonicalExercise?.name)
                    .put("equipment", canonicalExercise?.equipment)
                    .put("runtimeActivityKind", context.snapshot.activityKind(selectedPowerKey).name)
                    .put("runtimePlanningEligibility", runtimeMetadata?.planningEligibility)
                    .put("canonicalFamilyRole", canonicalExercise?.familyRole)
                    .put("laterality", canonicalExercise?.laterality)
                    .put("weightSourceFromReviewedPrescription", currentPrescriptionSource?.source)
                    .put("bodyweightSemanticsExplicitInCanonicalExercise", canonicalExercise?.equipment?.contains("BODYWEIGHT") == true)
                )
                .put("currentPrescriptionSource", currentPrescriptionSource?.let { prescriptionAuthorityJson(it) } ?: JSONObject.NULL)
                .put("b6PowerResolution", resolution)
                .put("b6PowerAuthorization", b6Auth)
                .put("b6PowerMaterialization", materialization)
                .put("generatedPowerRows", generatedPowerRowsJson)
                .put("powerTargetOutcome", targetOutcome?.let { JSONObject().put("status", it.status.name)
                    .put("directlyAffected", it.directlyAffected).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("jumpLanding", jumpLanding)
                .put("b7", comparison.experimentalReadinessAudit?.let { JSONObject().put("status", it.status.name)
                    .put("changeProvenanceClosed", it.changeProvenanceClosed)
                    .put("collateralRegressionFree", it.collateralRegressionFree)
                    .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("b7ChangeAttributions", JSONArray(comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().map { attr ->
                    JSONObject().put("stableKey", attr.stableKey).put("selectionRole", attr.selectionRole)
                        .put("source", attr.source.name).put("targetIds", JSONArray(attr.targetIds.sorted()))
                        .put("reasonCodes", JSONArray(attr.reasonCodes.sorted()))
                }))
                .put("b8MaterialScopeResolution", StimulusProductionMaterialScopeResolver().resolveDetailed(comparison).let { scope -> JSONObject()
                    .put("scope", scope.scope?.name ?: JSONObject.NULL).put("status", scope.status.name)
                    .put("materialOwners", JSONArray(scope.materialOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                        .map { "${it.stableKey}#${it.selectionRole}" }))
                    .put("removedOwners", JSONArray(scope.removedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                        .map { "${it.stableKey}#${it.selectionRole}" }))
                    .put("unattributedOwners", JSONArray(scope.unattributedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                        .map { "${it.stableKey}#${it.selectionRole}" }))
                    .put("unknownTargetIds", JSONArray(scope.unknownTargetIds.sorted()))
                    .put("reasonCodes", JSONArray(scope.reasonCodes.sorted()))
                })
                .put("b8", comparison.productionCutoverAuthority?.let { authority -> JSONObject().put("status", authority.status.name)
                    .put("scope", authority.scope.name).put("reasonCodes", JSONArray(authority.reasonCodes.sorted()))
                    .put("authorizedMovementTargetOwnerIdentities", JSONArray(authority.authorizedMovementTargetOwnerIdentities
                        .sortedWith(compareBy({ identity -> identity.targetId }, { identity -> identity.stableKey },
                            { identity -> identity.selectionRole }))
                        .map { identity -> JSONObject().put("targetId", identity.targetId)
                            .put("stableKey", identity.stableKey).put("selectionRole", identity.selectionRole) }))
                } ?: JSONObject.NULL)
                .put("route", result.routeDecision.selectedSource.name)
                .put("c20PowerInteraction", result.incumbentPlacementShadow?.let { shadow -> JSONObject()
                    .put("indexStatus", shadow.indexStatus.name)
                    .put("hardValid", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.HARD_VALID })
                    .put("hardInvalid", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.HARD_INVALID })
                    .put("unresolved", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.UNRESOLVED })
                    .put("preservationEvents", result.incumbentPlacementPreservations.size) } ?: JSONObject.NULL)
            caseRows.put(row)
        }

        val persona3 = records.firstOrNull { it.first.label == "persona3_reviewed" }?.second
        val persona3Comparison = persona3?.comparison
        val persona3PowerTarget = persona3Comparison?.targetPlan?.qualityTargets?.firstOrNull { it.quality == TrainableQuality.POWER }
        val powerRouteRows = records.mapNotNull { (spec, result) -> result?.comparison?.let { spec.label to it } }
            .filter { (_, comparison) -> comparison.selectionPlan.selectedCandidates.any { "QUALITY:POWER" in it.coveredTargetIds } }
        fun hasPowerB6Authority(comparison: StimulusSelectionProgramComparison) =
            comparison.prescriptionAuthorizationPlan?.authorizations?.any {
                it.quality == TrainableQuality.POWER && it.authorizedPrescription != null
            } == true
        fun powerTargetWasActuallyAdjudicated(comparison: StimulusSelectionProgramComparison) =
            comparison.experimentalReadinessAudit?.targetOutcomes?.singleOrNull {
                it.targetId == "QUALITY:POWER"
            }?.let { outcome ->
                outcome.directlyAffected && outcome.status in setOf(
                    StimulusExperimentalTargetOutcomeStatus.IMPROVED,
                    StimulusExperimentalTargetOutcomeStatus.UNCHANGED
                )
            } == true
        val b7EligiblePowerCases = powerRouteRows.count { (_, comparison) ->
            comparison.experimentalReadinessAudit?.status == StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
                hasPowerB6Authority(comparison) && powerTargetWasActuallyAdjudicated(comparison)
        }
        val b8AuthorizedPowerCases = powerRouteRows.count { (_, comparison) ->
            hasPowerB6Authority(comparison) && comparison.productionCutoverAuthority?.status ==
                StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER
        }
        val powerRoutedCases = records.count { (_, result) ->
            val comparison = result?.comparison
            result?.routeDecision?.selectedSource != null &&
                result.routeDecision.selectedSource != StimulusProductionProgramSource.CONTROL &&
                comparison != null && hasPowerB6Authority(comparison)
        }
        val powerTargetsTotal = records.count { (spec, result) ->
            (result?.comparison?.targetPlan ?: planningByCase[spec.label]?.targetPlan)
                ?.qualityTargets?.any { it.quality == TrainableQuality.POWER } == true
        }
        val routedResults = records.mapNotNull { it.second }
        val routeCounts = routedResults.groupingBy { it.routeDecision.selectedSource.name }.eachCount()
        val b7ReasonCounts = routedResults.flatMap { it.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
            .groupingBy { it }.eachCount()
        val incumbentRows = routedResults.flatMap { it.incumbentPlacementShadow?.rows.orEmpty() }
        val incumbentFeasibilityCounts = incumbentRows.groupingBy { it.feasibility.name }.eachCount()

        val root = JSONObject()
            .put("schema", "c37-power-jump-integrated-authority-census-v1")
            .put("c37StartingHead", "e4d07e82e595ab70772e40f1b1cc45b039c98701")
            .put("versions", JSONObject().put("protocol", "3.72.0")
                .put("runtime", "RECORD_BASED_PLANNER_0.15.14_KOTLIN_1").put("app", "0.5.1.5").put("room", 38))
            .put("policyConclusion", "EXACT_B8_B9_POWER_JUMP_ROUTE_WITH_OTHER_CASES_FAIL_CLOSED")
            .put("routeSnapshot", JSONObject()
                .put("CONTROL", routeCounts[StimulusProductionProgramSource.CONTROL.name] ?: 0)
                .put("POWER_JUMP", routeCounts[StimulusProductionProgramSource.B8_POWER_JUMP_V1.name] ?: 0)
                .put("STRENGTH_V1", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_V1.name] ?: 0)
                .put("STRENGTH_CALIBRATION_V1", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1.name] ?: 0)
                .put("HYPERTROPHY", routeCounts[StimulusProductionProgramSource.B8_HYPERTROPHY_V1.name] ?: 0)
                .put("COMBINED", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1.name] ?: 0))
            .put("b7ReasonOccurrences", JSONObject()
                .put("CHANGE_PROVENANCE_UNCLOSED", b7ReasonCounts["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                .put("AFFECTED_TARGET_REMAINS_UNMET", b7ReasonCounts["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                .put("TARGET_REGRESSED", b7ReasonCounts["TARGET_REGRESSED"] ?: 0))
            .put("c20LiveIncumbentFeasibility", JSONObject()
                .put("HARD_VALID", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.HARD_VALID.name] ?: 0)
                .put("HARD_INVALID", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.HARD_INVALID.name] ?: 0)
                .put("UNRESOLVED", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.UNRESOLVED.name] ?: 0)
                .put("preservedHardValidRows", routedResults.sumOf { it.incumbentPlacementPreservations.size })
                .put("hardInvalidOrUnresolvedForcedPreserved", routedResults.sumOf { result ->
                    result.incumbentPlacementPreservations.count { it.feasibility.status != CanonicalIncumbentFeasibility.HARD_VALID }
                }))
            .put("counts", JSONObject()
                .put("corpusCases", records.size).put("preflightRejected", records.count { it.second == null })
                .put("generatedCases", generatedCount).put("powerTargets", powerTargetsTotal)
                .put("powerTargetsTotal", powerTargetsTotal)
                .put("directionOnlyBeforeC37B4", directionOnlyBefore).put("numericPowerAuthorityAfter", numericPowerAfter)
                .put("userApprovedPolicyAuthorityCases", userApprovedPolicyAuthorities)
                .put("personalNumericAuthorityCases", personalNumericAuthorities)
                .put("exactB6AuthorizedPowerCases", exactB6Authorities)
                .put("reviewedStarterAuthority", reviewedStarterAuthorities)
                .put("stillDirectionOnly", stillDirectionOnly)
                .put("fullyMaterializedPower", fullyMaterializedPower)
                .put("b7EligiblePowerCases", b7EligiblePowerCases)
                .put("b8AuthorizedPowerCases", b8AuthorizedPowerCases)
                .put("powerRoutedCases", powerRoutedCases)
                .put("exactB5PowerOwnerRows", selectedPowerOwnerRows)
                .put("uniqueExactB5PowerOwners", powerRouteRows.flatMap { (_, comparison) -> comparison.selectionPlan.selectedCandidates
                    .filter { "QUALITY:POWER" in it.coveredTargetIds }.map { "${it.stableKey}#${it.selectionRole}" } }.distinct().size)
                .put("generatedPowerRowsAfterAuthorityFilter", generatedPowerRows)
                .put("preC21BaselineGeneratedPowerRows", 8)
                .put("exactOwnerDirectPowerObservationIds", JSONArray(exactOwnerPowerObservationIds.sorted()))
                .put("jumpLandingTargets", records.count { (spec, result) ->
                    val targetPlan = result?.comparison?.targetPlan ?: planningByCase[spec.label]?.targetPlan
                    targetPlan?.qualityTargets?.any { it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC } == true
                })
                .put("jumpLandingNeedCases", records.count { (spec, _) ->
                    planningByCase[spec.label]?.athleteStimulusNeedProfile?.qualityNeeds?.any {
                        it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC && it.decision != TrainingNeedDecision.NO_EXTRA_NEED
                    } == true
                })
                .put("jumpLandingNumericB4Cases", records.count { (spec, result) ->
                    val targetPlan = result?.comparison?.targetPlan ?: planningByCase[spec.label]?.targetPlan
                    targetPlan?.qualityTargets?.any { it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC &&
                        it.numericAuthority == StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY } == true
                })
                .put("jumpLandingB5OwnerRows", records.sumOf { (_, result) ->
                    result?.comparison?.selectionPlan?.selectedCandidates?.count {
                        "QUALITY:${TrainableQuality.REACTIVE_STRENGTH_SSC.name}" in it.coveredTargetIds
                    } ?: 0
                })
                .put("jumpLandingB6AuthorizedCases", records.count { (_, result) ->
                    result?.comparison?.prescriptionAuthorizationPlan?.authorizations?.any {
                        it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC && it.authorizedPrescription != null
                    } == true
                })
                .put("jumpLandingMaterializedCases", records.count { (_, result) ->
                    result?.comparison?.prescriptionMaterializationAudits?.any {
                        it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC &&
                            it.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED
                    } == true
                })
                .put("jumpLandingMaterializedSetUnits", records.sumOf { (_, result) ->
                    result?.comparison?.prescriptionMaterializationAudits.orEmpty().filter {
                        it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC
                    }.sumOf { it.materializedWeeklySetUnits }
                })
                .put("jumpLandingB7EligibleCases", records.count { (_, result) ->
                    result?.comparison?.experimentalReadinessAudit?.status ==
                        StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
                        result.comparison?.prescriptionAuthorizationPlan?.authorizations?.any {
                            it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC && it.authorizedPrescription != null
                        } == true
                })
                .put("jumpLandingB8AuthorizedCases", records.count { (_, result) ->
                    result?.comparison?.productionCutoverAuthority?.status ==
                        StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER &&
                        result.comparison?.prescriptionAuthorizationPlan?.authorizations?.any {
                            it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC && it.authorizedPrescription != null
                        } == true
                })
                .put("jumpLandingRoutedCases", records.count { (_, result) ->
                    result?.routeDecision?.selectedSource != null &&
                        result.routeDecision.selectedSource != StimulusProductionProgramSource.CONTROL &&
                        result.comparison?.prescriptionAuthorizationPlan?.authorizations?.any {
                            it.quality == TrainableQuality.REACTIVE_STRENGTH_SSC && it.authorizedPrescription != null
                        } == true
                })
            )
            .put("reviewedRuleSource", JSONObject()
                .put("category", reviewedCategory?.name).put("members", JSONArray(reviewedCategory?.let { RecordBasedReviewedPolicy.badmintonKeys.getValue(it).sorted() }.orEmpty()))
                .put("guide", reviewedGuideJson ?: JSONObject.NULL)
                .put("bridgeAccepted", false)
                .put("rejectionReasons", JSONArray(listOf(
                    "B4_POWER_HAS_NO_NUMERIC_DOSE_OR_WEEKLY_FREQUENCY_AUTHORITY",
                    "BADMINTON_DECELERATION_RELATION_IS_SUPPORTIVE_NOT_DIRECT",
                    "PROGRAM_SET_MODEL_DOES_NOT_REPRESENT_REPS_PER_SIDE",
                    "GUIDE_IS_CATEGORY_LEVEL_AND_NOT_PROVEN_AS_EXACT_OWNER_POWER_AUTHORITY"
                )))
            )
            .put("persona3Reviewed", JSONObject()
                .put("powerTarget", persona3PowerTarget?.let { JSONObject().put("strategy", it.strategy.name)
                    .put("numericAuthority", it.numericAuthority.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("selectedOwner", persona3Comparison?.selectionPlan?.selectedCandidates?.firstOrNull { "QUALITY:POWER" in it.coveredTargetIds }
                    ?.let { JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) } ?: JSONObject.NULL)
                .put("exactOwnerPersonalHistoryCount", persona3Comparison?.let { comparison ->
                    val key = comparison.selectionPlan.selectedCandidates.firstOrNull { "QUALITY:POWER" in it.coveredTargetIds }?.stableKey
                    key?.let { stable -> contextByCase["persona3_reviewed"]?.snapshot?.allConfirmedSets?.count { it.stableKey == stable } } ?: 0
                } ?: 0)
                .put("currentPowerRowsAfterAuthorityFilter", persona3Comparison?.let { comparison ->
                    val selectedOwner = comparison.selectionPlan.selectedCandidates
                        .firstOrNull { "QUALITY:POWER" in it.coveredTargetIds }
                    comparison.experimental?.items?.count { item ->
                        selectedOwner != null && item.exerciseStableKey == selectedOwner.stableKey &&
                            item.selectionRole == selectedOwner.selectionRole
                    } ?: 0
                } ?: 0)
                .put("currentB6Status", persona3Comparison?.prescriptionAuthorizationPlan?.authorizations?.firstOrNull { it.targetId == "QUALITY:POWER" }?.status?.name)
                .put("currentB6ReasonCodes", JSONArray(persona3Comparison?.prescriptionAuthorizationPlan?.authorizations
                    ?.firstOrNull { it.targetId == "QUALITY:POWER" }?.reasonCodes.orEmpty().sorted()))
                .put("routeBefore", persona3?.routeDecision?.selectedSource?.name)
            )
            .put("cases", caseRows)
        return root.toString(2)
    }

    private fun jumpLandingEvidence(
        planning: CanonicalStimulusPlanningResult,
        comparison: StimulusSelectionProgramComparison?
    ): JSONObject {
        val quality = TrainableQuality.REACTIVE_STRENGTH_SSC
        val targetPlan = comparison?.targetPlan ?: planning.targetPlan
        val need = planning.athleteStimulusNeedProfile.qualityNeeds.firstOrNull { it.quality == quality }
        val b3 = planning.decisionPortfolio.qualityDecisions.firstOrNull { it.quality == quality }
        val target = targetPlan.qualityTargets.firstOrNull { it.quality == quality }
        val dose = targetPlan.powerJumpDoseDecisions.singleOrNull { it.targetId == "QUALITY:${quality.name}" }
        val owners = comparison?.selectionPlan?.selectedCandidates.orEmpty().filter {
            "QUALITY:${quality.name}" in it.coveredTargetIds
        }
        val authorization = comparison?.prescriptionAuthorizationPlan?.authorizations?.firstOrNull {
            it.quality == quality
        }
        val materialization = comparison?.prescriptionMaterializationAudits?.firstOrNull {
            it.quality == quality
        }
        val ownerIds = owners.mapTo(hashSetOf()) { it.stableKey to it.selectionRole }
        val generatedRows = JSONArray(comparison?.experimental?.items.orEmpty().filter {
            (it.exerciseStableKey to it.selectionRole) in ownerIds
        }.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
            ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey)).map { item ->
            JSONObject().put("week", item.weekNumber).put("day", item.dayOfWeek)
                .put("stableKey", item.exerciseStableKey).put("selectionRole", item.selectionRole)
                .put("sets", item.setCount).put("reps", item.reps).put("restSeconds", item.restSeconds)
                .put("loadStates", JSONArray(item.setPrescriptions.map { it.loadState.name }.distinct()))
                .put("weightSource", item.weightSource)
        })
        return JSONObject()
            .put("b1Need", need?.let { JSONObject().put("decision", it.decision.name)
                .put("relevance", it.relevance.name).put("confidence", it.confidence.name)
                .put("coverage", it.exposure.coverage.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) }
                ?: JSONObject.NULL)
            .put("b3Decision", b3?.let { JSONObject().put("needDecision", it.needDecision.name)
                .put("strategy", it.strategy.name).put("priority", it.priority.name)
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
            .put("b4Target", target?.let { JSONObject().put("numericAuthority", it.numericAuthority.name)
                .put("priority", it.priority.name).put("weeklyDirectUnitsTarget", rangeJson(it.weeklyDirectUnitsTarget))
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
            .put("b4Dose", dose?.let { JSONObject().put("status", it.status.name)
                .put("approvedWeeklySets", it.approvedWeeklySetUnits).put("weeklyCap", it.applicableWeeklyCap ?: JSONObject.NULL)
                .put("existingSets", it.existingAuthorizedSetUnits).put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                .put("evidence", JSONArray(it.evidence.sorted())) } ?: JSONObject.NULL)
            .put("b5Owners", JSONArray(owners.map { JSONObject().put("stableKey", it.stableKey)
                .put("selectionRole", it.selectionRole).put("selectionReasons", JSONArray(it.selectionReasons.sorted())) }))
            .put("b6", authorization?.let { JSONObject().put("status", it.status.name)
                .put("authorized", it.authorizedPrescription != null).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) }
                ?: JSONObject.NULL)
            .put("materialization", materialization?.let { JSONObject().put("state", it.state.name)
                .put("authorizedWeeklySets", it.authorizedWeeklySetUnits)
                .put("materializedWeeklySets", it.materializedWeeklySetUnits)
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
            .put("generatedRows", generatedRows)
            .put("targetOutcome", comparison?.experimentalReadinessAudit?.targetOutcomes?.firstOrNull {
                it.targetId == "QUALITY:${quality.name}"
            }?.let { JSONObject().put("status", it.status.name).put("directlyAffected", it.directlyAffected)
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
            .put("b7", comparison?.experimentalReadinessAudit?.let { JSONObject().put("status", it.status.name)
                .put("provenanceClosed", it.changeProvenanceClosed).put("reasons", JSONArray(it.reasonCodes.sorted())) }
                ?: JSONObject.NULL)
            .put("b8", comparison?.productionCutoverAuthority?.let { JSONObject().put("status", it.status.name)
                .put("scope", it.scope?.name).put("reasons", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
    }

    private fun rangeJson(value: StimulusTargetRange?): Any = value?.let { JSONObject()
        .put("min", it.min).put("preferred", it.preferred).put("max", it.max) } ?: JSONObject.NULL

    private fun prescriptionJson(value: PlannedPrescription?): Any = value?.let { prescription -> JSONObject()
        .put("text", prescription.text).put("weightSource", prescription.weightSource).put("restSeconds", prescription.restSeconds)
        .put("sets", JSONArray(prescription.sets.map { set -> JSONObject().put("setIndex", set.setIndex)
            .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds)
            .put("loadState", set.loadState.name).put("targetRpe", set.targetRpeMin) }))
    } ?: JSONObject.NULL

    private fun prescriptionAuthorityJson(value: PerformancePrescriptionAuthority): JSONObject = JSONObject()
        .put("source", value.source).put("text", value.text).put("restSeconds", value.restSeconds)
        .put("sets", JSONArray(value.sets.map { set -> JSONObject().put("setIndex", set.setIndex)
            .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds) }))

    private fun objectiveJson(value: CanonicalBadmintonObjectiveRelation): JSONObject = JSONObject()
        .put("relationId", value.relationId).put("objective", value.objective.name)
        .put("transferLevel", value.transferLevel.name).put("provenance", value.provenance)
        .put("evidenceRelationKeys", JSONArray(value.evidenceRelationKeys.sorted())).put("reviewReason", value.reviewReason)
}
