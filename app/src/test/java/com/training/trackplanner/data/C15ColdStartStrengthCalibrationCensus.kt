package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal fun renderC15ColdStartStrengthCalibrationCensus(
    records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
    coverageReport: String,
    c14MergeMainHead: String,
    c15StartHead: String
): String {
    val ordered = records.sortedBy { it.first.label }
    val generated = ordered.mapNotNull { (spec, result) -> result?.let { spec to it } }
    val repRangeCaseIds = listOf(
        "persona0_mixed", "persona0_reviewed", "persona2_reviewed", "persona3_reviewed", "persona4_mixed"
    )

    fun ownerJson(owner: StimulusPrescriptionOwnerIdentity?) = owner?.let {
        JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
    } ?: JSONObject.NULL

    fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    fun caseJson(spec: StimulusProductionCoverageAuditTest.CoverageSpec, result: StimulusProductionGenerationResult): JSONObject {
        val comparison = result.comparison
        val target = comparison?.targetPlan?.qualityTargets?.firstOrNull { it.quality == TrainableQuality.STRENGTH }
        val strengthResolution = comparison?.prescriptionRealizationPlan?.resolutions
            ?.firstOrNull { it.quality == TrainableQuality.STRENGTH }
        val coldStart = strengthResolution?.coldStartStrengthCalibration
        val proposal = coldStart?.proposal
        val exactOwner = proposal?.owner ?: strengthResolution?.owner?.let {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        val selectedOwners = comparison?.selectionPlan?.selectedCandidates.orEmpty()
            .filter { "QUALITY:STRENGTH" in it.coveredTargetIds }
            .map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            .distinct()
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val materialDemand = exactOwner?.let { owner -> comparison?.selectionPlan?.materialDemand?.candidates
            ?.firstOrNull { it.stableKey == owner.stableKey && it.role == owner.selectionRole } }
        val authorization = exactOwner?.let { owner -> comparison?.prescriptionAuthorizationPlan?.authorizations
            ?.singleOrNull { row ->
                row.quality == TrainableQuality.STRENGTH &&
                    row.owner?.let { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole } == true
            } }
        val materialization = exactOwner?.let { owner -> comparison?.prescriptionMaterializationAudits
            ?.singleOrNull { row ->
                row.quality == TrainableQuality.STRENGTH &&
                    row.owner?.let { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole } == true
            } }
        val b7 = comparison?.experimentalReadinessAudit
        val b8 = comparison?.productionCutoverAuthority
        val strengthOutcome = b7?.targetOutcomes?.firstOrNull { it.targetId == "QUALITY:STRENGTH" }
        val exactOwnerAttribution = exactOwner?.let { owner -> b7?.changeAttributions?.filter {
            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
        } }.orEmpty()
        val route = result.routeDecision.selectedSource
        return JSONObject()
            .put("caseId", spec.label)
            .put("route", route.name)
            .put("B4_strengthTarget", target?.let {
                JSONObject().put("strategy", it.strategy.name).put("numericAuthority", it.numericAuthority.name)
                    .put("targetId", "QUALITY:STRENGTH")
            } ?: JSONObject.NULL)
            .put("B4_authorizedSetCount", materialDemand?.targetSets ?: JSONObject.NULL)
            .put("B5_selectedStrengthOwners", JSONArray(selectedOwners.map(::ownerJson)))
            .put("exactOwner", ownerJson(exactOwner))
            .put("exactOwnerSameKeyAndRole", exactOwner != null && selectedOwners.contains(exactOwner))
            .put("C14_shadow", strengthResolution?.strengthTrainingLoadShadow?.let { shadow ->
                JSONObject().put("available", shadow.available)
                    .put("evidenceTier", shadow.evidenceTier.name)
                    .put("unavailableReasons", JSONArray(shadow.unavailableReasons.map { it.name }))
                    .put("ownerLocalObservationCount", shadow.ownerLocalObservations.size)
                    .put("hasCapacityReference", shadow.capacityReference != null)
            } ?: JSONObject.NULL)
            .put("C15_coldStart", coldStart?.let { resolved ->
                JSONObject().put("status", resolved.status.name)
                    .put("unavailableReasons", JSONArray(resolved.unavailableReasons.map { it.name }))
                    .put("loadState", resolved.proposal?.loadState?.name ?: JSONObject.NULL)
                    .put("sets", resolved.proposal?.setCount ?: JSONObject.NULL)
                    .put("reps", resolved.proposal?.repetitions ?: JSONObject.NULL)
                    .put("targetRpe", resolved.proposal?.targetRpe ?: JSONObject.NULL)
                    .put("loadSemantics", resolved.proposal?.loadSemantics?.name ?: JSONObject.NULL)
                    .put("ownerHistoryStatus", resolved.proposal?.ownerHistoryStatus?.name ?: JSONObject.NULL)
                    .put("reasonCodes", JSONArray(resolved.proposal?.reasonCodes.orEmpty()))
            } ?: JSONObject.NULL)
            .put("B6", JSONObject()
                .put("status", authorization?.status?.name ?: JSONObject.NULL)
                .put("executionAuthority", authorization?.executionAuthority?.name ?: JSONObject.NULL)
                .put("exactOwner", ownerJson(authorization?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }))
                .put("authorizedSetCount", authorization?.authorizedPrescription?.sets?.size ?: JSONObject.NULL)
                .put("reasons", JSONArray(authorization?.reasonCodes.orEmpty()))
            )
            .put("materialization", JSONObject()
                .put("state", materialization?.state?.name ?: JSONObject.NULL)
                .put("executionAuthority", materialization?.executionAuthority?.name ?: JSONObject.NULL)
                .put("authorizedUnits", materialization?.authorizedWeeklySetUnits ?: JSONObject.NULL)
                .put("materializedUnits", materialization?.materializedWeeklySetUnits ?: JSONObject.NULL)
                .put("shortfall", materialization?.shortfall ?: JSONObject.NULL)
                .put("prescriptionPreserved", materialization?.prescriptionPreservedOrSubset ?: JSONObject.NULL)
            )
            .put("targetOutcome", JSONObject()
                .put("status", strengthOutcome?.status?.name ?: JSONObject.NULL)
                .put("reasons", JSONArray(strengthOutcome?.reasonCodes.orEmpty()))
            )
            .put("B7", JSONObject()
                .put("status", b7?.status?.name ?: JSONObject.NULL)
                .put("changeProvenanceClosed", b7?.changeProvenanceClosed ?: JSONObject.NULL)
                .put("collateralRegressionFree", b7?.collateralRegressionFree ?: JSONObject.NULL)
                .put("reasons", JSONArray(b7?.reasonCodes.orEmpty()))
                .put("exactOwnerAttributions", JSONArray(exactOwnerAttribution.map { attribution ->
                    JSONObject().put("source", attribution.source.name)
                        .put("targetIds", JSONArray(attribution.targetIds.sorted()))
                        .put("reasonCodes", JSONArray(attribution.reasonCodes.sorted()))
                }))
            )
            .put("B8", JSONObject()
                .put("scope", b8?.scope?.name ?: JSONObject.NULL)
                .put("status", b8?.status?.name ?: JSONObject.NULL)
                .put("reasons", JSONArray(b8?.reasonCodes.orEmpty()))
            )
            .put("buildAccounting", JSONObject()
                .put("control", result.buildCounts.controlBuilds)
                .put("experimental", result.buildCounts.experimentalBuilds)
                .put("total", result.buildCounts.totalBuildInvocations)
                .put("third", result.buildCounts.thirdBuilds)
            )
    }

    val allCases = generated.map { (spec, result) -> caseJson(spec, result) }
    val byId = generated.associate { it.first.label to it }
    val fiveDossiers = repRangeCaseIds.map { caseId ->
        val (spec, result) = requireNotNull(byId[caseId]) { "Missing C13 REP_RANGE case $caseId" }
        caseJson(spec, result)
    }
    val directionOnlyCases = generated.mapNotNull { (spec, result) ->
        val target = result.comparison?.targetPlan?.qualityTargets?.firstOrNull {
            it.quality == TrainableQuality.STRENGTH && it.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY
        } ?: return@mapNotNull null
        JSONObject().put("caseId", spec.label)
            .put("strategy", target.strategy.name)
            .put("numericAuthority", target.numericAuthority.name)
            .put("coldStartAuthorityAvailable", result.comparison.prescriptionRealizationPlan?.resolutions
                ?.firstOrNull { it.quality == TrainableQuality.STRENGTH }
                ?.coldStartStrengthCalibration?.available ?: false)
            .put("authorizedNumericSets", JSONObject.NULL)
            .put("reason", "DIRECTION_ONLY_DOES_NOT_AUTHORIZE_C15_SET_COUNT")
    }
    val routes = StimulusProductionProgramSource.entries.associate { source ->
        source.name to generated.count { it.second.routeDecision.selectedSource == source }
    }
    val b7Reasons = generated.flatMap { (_, result) -> result.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
        .groupingBy { it }.eachCount().toSortedMap()
    val b8Reasons = generated.flatMap { (_, result) -> result.comparison?.productionCutoverAuthority?.reasonCodes.orEmpty() }
        .groupingBy { it }.eachCount().toSortedMap()
    val sha = MessageDigest.getInstance("SHA-256")
        .digest(coverageReport.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    return JSONObject()
        .put("schema", "c15-cold-start-strength-calibration-census-v1")
        .put("c14MergeMainHead", c14MergeMainHead)
        .put("c15StartHead", c15StartHead)
        .put("programBuilderProtocol", "3.52.0")
        .put("plannerRuntime", "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1")
        .put("appVersion", "0.5.1.5")
        .put("standardCoverageBaselineSha256", "818E8FA6F67164EEAAE0C938273A777D645874CF0EECD17F1E1795DC811434D9")
        .put("standardCoverageSha256", sha)
        .put("corpus", JSONObject()
            .put("totalCases", ordered.size)
            .put("generated", generated.size)
            .put("preflightRejected", ordered.size - generated.size)
            .put("routes", JSONObject(routes))
            .put("B7ReasonOccurrences", JSONObject(b7Reasons))
            .put("B8ReasonOccurrences", JSONObject(b8Reasons))
        )
        .put("phaseOrdering", JSONArray(listOf("B1-B6", "EXPERIMENTAL", "CONTROL", "COMPARISON", "B7", "B8", "B9")))
        .put("fiveC13RepRangeCases", JSONArray(fiveDossiers))
        .put("directionOnlyStrengthCases", JSONArray(directionOnlyCases))
        .put("cases", JSONArray(allCases))
        .put("buildAccounting", JSONObject()
            .put("normal", JSONObject().put("control", 1).put("experimental", 1).put("total", 2).put("third", 0))
            .put("preflightRejected", JSONObject().put("control", 0).put("experimental", 0).put("total", 0).put("third", 0))
        )
        .put("controlIndependent", true)
        .put("newRouteAllowedOnlyAfterExactB4B5B6MaterializationB7B8", true)
        .toString(2)
}
