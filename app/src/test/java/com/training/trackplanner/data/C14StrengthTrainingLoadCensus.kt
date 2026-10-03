package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal fun renderC14StrengthTrainingLoadCensus(
    records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
    standardCoverageReport: String,
    c13MergeHead: String,
    c14StartHead: String
): String {
    val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }.sortedBy { it.first.label }
    val routes = generated.groupingBy { it.second.routeDecision.selectedSource.name }.eachCount()
    val cases = JSONArray(records.sortedBy { it.first.label }.map { (spec, result) ->
        if (result == null) return@map JSONObject()
            .put("caseId", spec.label)
            .put("status", "PREFLIGHT_REJECTED")
            .put("reason", "PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY")
            .put("B4ThroughB9Executed", false)
            .put("buildAccounting", JSONObject().put("control", 0).put("experimental", 0).put("total", 0).put("third", 0))
        val comparison = result.comparison
        val targets = comparison?.targetPlan?.qualityTargets.orEmpty().associateBy { it.quality }
        val strengthOwners = comparison?.selectionPlan?.selectedCandidates.orEmpty()
            .filter { "QUALITY:STRENGTH" in it.coveredTargetIds }
            .map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            .distinct()
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val rows = strengthOwners.map { owner ->
            val realization = comparison?.prescriptionRealizationPlan?.resolutions
                ?.firstOrNull { it.quality == TrainableQuality.STRENGTH && it.owner?.let { o ->
                    StimulusPrescriptionOwnerIdentity(o.stableKey, o.selectionRole) == owner
                } == true }
            val authorization = comparison?.prescriptionAuthorizationPlan?.authorizations
                ?.firstOrNull { it.quality == TrainableQuality.STRENGTH && it.owner?.let { o ->
                    StimulusPrescriptionOwnerIdentity(o.stableKey, o.selectionRole) == owner
                } == true }
            val materialization = comparison?.prescriptionMaterializationAudits
                ?.firstOrNull { it.quality == TrainableQuality.STRENGTH && it.owner?.let { o ->
                    StimulusPrescriptionOwnerIdentity(o.stableKey, o.selectionRole) == owner
                } == true }
            JSONObject()
                .put("owner", JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole))
                .put("B4NumericAuthority", targets[TrainableQuality.STRENGTH]?.numericAuthority?.name)
                .put("B6OldStatus", authorization?.status?.name)
                .put("B6OldReasons", JSONArray(authorization?.reasonCodes.orEmpty()))
                .put("B6AuthorizedBeforeC14B", authorization?.authorizedPrescription != null)
                .put("B6MaterializationStateBeforeC14B", materialization?.state?.name)
                .put("B6MaterializedUnitsBeforeC14B", materialization?.totalMaterializedUnits ?: 0)
                .put("shadowDisposition", realization?.strengthTrainingLoadShadow?.toCompactJson())
                .put("shadowNotEvaluatedReason", if (realization?.strengthTrainingLoadShadow == null) {
                    when {
                        authorization?.authorizedPrescription != null -> "EXISTING_B6_AUTHORITY_HAS_PRECEDENCE"
                        realization?.reasonCodes?.contains("B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE") == true -> "B4_DOSE_AUTHORITY_DOES_NOT_AUTHORIZE_CHANGE"
                        else -> "NO_C14_SHADOW_RESULT_AT_THIS_B6_STAGE"
                    }
                } else JSONObject.NULL)
        }
        val b7 = comparison?.experimentalReadinessAudit
        val b8 = comparison?.productionCutoverAuthority
        val b7StrengthOutcome = b7?.targetOutcomes?.firstOrNull { it.targetId == "QUALITY:STRENGTH" }
        JSONObject()
            .put("caseId", spec.label)
            .put("status", "GENERATED")
            .put("fixtureHistoryInput", JSONObject()
                .put("stableKey", spec.stableKey)
                .put("historyProfile", spec.history)
                .put("sessions", when (spec.history) {
                    "sparse" -> 1
                    "recent" -> 5
                    "reviewed", "mixed" -> 14
                    else -> 0
                })
                .put("setsPerSession", if (spec.history == "none") 0 else 3)
                .put("setLoadKg", if (spec.history == "none") JSONObject.NULL else 40.0)
                .put("setRpe", if (spec.history == "none") JSONObject.NULL else 8.0)
                .put("repetitionCounts", JSONObject().apply {
                    if (spec.quality == TrainableQuality.STRENGTH && spec.history in setOf("reviewed", "mixed")) {
                        put("5", 12)
                        put("8", 30)
                    }
                }))
            .put("route", result.routeDecision.selectedSource.name)
            .put("B4StrengthTargetPresent", TrainableQuality.STRENGTH in targets)
            .put("B5StrengthOwners", JSONArray(strengthOwners.map { owner ->
                JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
            }))
            .put("strengthOwnerRows", JSONArray(rows))
            .put("strengthTargetOutcome", JSONObject()
                .put("status", b7StrengthOutcome?.status?.name)
                .put("reasonCodes", JSONArray(b7StrengthOutcome?.reasonCodes.orEmpty())))
            .put("B7", JSONObject().put("status", b7?.status?.name).put("reasons", JSONArray(b7?.reasonCodes.orEmpty())))
            .put("B8", JSONObject().put("status", b8?.status?.name).put("scope", b8?.scope?.name)
                .put("reasons", JSONArray(b8?.reasonCodes.orEmpty())))
            .put("buildAccounting", JSONObject()
                .put("control", result.buildCounts.controlBuilds)
                .put("experimental", result.buildCounts.experimentalBuilds)
                .put("total", result.buildCounts.totalBuildInvocations)
                .put("third", result.buildCounts.thirdBuilds))
    })
    val allShadowResults = generated.flatMap { (spec, result) ->
        result.comparison?.prescriptionRealizationPlan?.resolutions.orEmpty().mapNotNull { resolution ->
            resolution.strengthTrainingLoadShadow?.let { spec.label to it }
        }
    }
    val directionOnlyRows = generated.flatMap { (spec, result) ->
        val comparison = result.comparison ?: return@flatMap emptyList()
        val target = comparison.targetPlan.qualityTargets.singleOrNull { it.quality == TrainableQuality.STRENGTH }
            ?.takeIf { it.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY }
            ?: return@flatMap emptyList()
        comparison.selectionPlan.selectedCandidates
            .filter { "QUALITY:STRENGTH" in it.coveredTargetIds }
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            .map { candidate ->
                val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole)
                val resolution = comparison.prescriptionRealizationPlan?.resolutions.orEmpty()
                    .firstOrNull { it.quality == TrainableQuality.STRENGTH && it.owner?.let { value ->
                        StimulusPrescriptionOwnerIdentity(value.stableKey, value.selectionRole) == owner
                    } == true }
                JSONObject()
                    .put("caseId", spec.label)
                    .put("targetId", "QUALITY:STRENGTH")
                    .put("B4NumericAuthority", target.numericAuthority.name)
                    .put("owner", JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole))
                    .put("B5ExistingSetCount", candidate.targetSetsFromExistingPrescription)
                    .put("B6Status", resolution?.status?.name)
                    .put("B6Reasons", JSONArray(resolution?.reasonCodes.orEmpty()))
                    .put("C14ShadowAvailable", resolution?.strengthTrainingLoadShadow?.available ?: false)
                    .put("C14Disposition", "NO_LOAD_AUTHORITY_FROM_DIRECTION_ONLY_B4")
            }
    }
    val fiveRangeCaseIds = setOf("persona0_mixed", "persona0_reviewed", "persona2_reviewed", "persona3_reviewed", "persona4_mixed")
    val fiveRows = generated.filter { it.first.label in fiveRangeCaseIds }.mapNotNull { (spec, result) ->
        val comparison = result.comparison ?: return@mapNotNull null
        val authorization = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            .firstOrNull { it.quality == TrainableQuality.STRENGTH }
        val shadow = comparison.prescriptionRealizationPlan?.resolutions.orEmpty()
            .firstOrNull { it.quality == TrainableQuality.STRENGTH }?.strengthTrainingLoadShadow
        JSONObject()
            .put("caseId", spec.label)
            .put("owner", authorization?.owner?.let { JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) })
            .put("fixtureHistoryInput", JSONObject()
                .put("stableKey", spec.stableKey)
                .put("historyProfile", spec.history)
                .put("sessions", if (spec.history in setOf("reviewed", "mixed")) 14 else 0)
                .put("setsPerSession", if (spec.history in setOf("reviewed", "mixed")) 3 else 0)
                .put("setLoadKg", if (spec.history in setOf("reviewed", "mixed")) 40.0 else JSONObject.NULL)
                .put("setRpe", if (spec.history in setOf("reviewed", "mixed")) 8.0 else JSONObject.NULL)
                .put("repetitionCounts", JSONObject().apply {
                    if (spec.quality == TrainableQuality.STRENGTH && spec.history in setOf("reviewed", "mixed")) {
                        put("5", 12)
                        put("8", 30)
                    }
                }))
            .put("oldB6Input", authorization?.inputPrescription?.let { input -> JSONObject()
                .put("weightSource", input.weightSource)
                .put("sets", JSONArray(input.sets.map { set -> JSONObject().put("reps", set.reps)
                    .put("weightKg", set.weightKg).put("targetRpeMin", set.targetRpeMin) }))
            })
            .put("shadow", shadow?.toCompactJson())
            .put("oldB6Status", authorization?.status?.name)
            .put("route", result.routeDecision.selectedSource.name)
    }
    val obj = JSONObject()
        .put("schema", "c14-strength-capacity-training-load-census-v1")
        .put("c13MergeMainHead", c13MergeHead)
        .put("c14StartHead", c14StartHead)
        .put("protocol", PERSONALIZED_PLANNER_PROTOCOL)
        .put("standardCoverageSha256", MessageDigest.getInstance("SHA-256")
            .digest(standardCoverageReport.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) })
        .put("corpus", JSONObject()
            .put("totalCases", records.size)
            .put("generated", generated.size)
            .put("preflightRejected", records.size - generated.size)
            .put("routes", JSONObject()
                .put("CONTROL", routes[StimulusProductionProgramSource.CONTROL.name] ?: 0)
                .put("Strength", routes[StimulusProductionProgramSource.B8_STRENGTH_V1.name] ?: 0)
                .put("Hypertrophy", routes[StimulusProductionProgramSource.B8_HYPERTROPHY_V1.name] ?: 0)
                .put("Combined", routes[StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1.name] ?: 0))
            .put("buildAccountingPreserved", generated.all { it.second.buildCounts.controlBuilds == 1 &&
                it.second.buildCounts.experimentalBuilds == 1 && it.second.buildCounts.totalBuildInvocations == 2 &&
                it.second.buildCounts.thirdBuilds == 0 }))
        .put("C14A", JSONObject()
            .put("productionB6Consumption", false)
            .put("shadowAvailable", allShadowResults.count { it.second.available })
            .put("shadowUnavailable", allShadowResults.count { !it.second.available })
            .put("fiveRepRangeCases", JSONArray(fiveRows))
            .put("allEvaluatedStrengthRows", JSONArray(allShadowResults.map { (caseId, resolution) ->
                JSONObject().put("caseId", caseId).put("result", resolution.toCompactJson())
            }))
            .put("routesChanged", routes[StimulusProductionProgramSource.CONTROL.name] != 21 ||
                routes[StimulusProductionProgramSource.B8_STRENGTH_V1.name] != 1 ||
                (routes[StimulusProductionProgramSource.B8_HYPERTROPHY_V1.name] ?: 0) != 0 ||
                (routes[StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1.name] ?: 0) != 0))
        .put("C14B", JSONObject()
            .put("productionB6AuthorityActivated", false)
            .put("directionOnlyCases", JSONArray(directionOnlyRows))
            .put("directionOnlyCaseCount", directionOnlyRows.size)
            .put("directionOnlyCalibrationAuthorized", false)
            .put("reason", "DIRECTION_ONLY_DOES_NOT_AUTHORIZE_NUMERIC_B6_WORK"))
        .put("cases", cases)
        .put("phaseOrdering", JSONArray(listOf("B1-B6", "EXPERIMENTAL", "CONTROL", "COMPARISON", "B7", "B8", "B9")))
    return obj.toString(2)
}
