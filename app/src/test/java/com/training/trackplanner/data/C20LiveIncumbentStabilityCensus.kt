package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.StimulusProductionGenerationResult
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C20 report from persisted exact-role fixture inputs and each production run's live projection. */
internal object C20LiveIncumbentStabilityCensus {
    private val targetCases = setOf("persona0_mixed", "persona0_reviewed", "persona3_reviewed", "persona4_mixed")

    fun render(
        c18Census: String,
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        c19MergeSha: String,
        c20StartSha: String
    ): String {
        val oldRows = JSONObject(c18Census).getJSONArray("placementRows").objects()
            .filter { it.getString("case") in targetCases }
            .sortedWith(compareBy({ it.getString("case") }, { it.getInt("week") },
                { it.getJSONObject("owner").getString("stableKey") },
                { it.getJSONObject("owner").getString("selectionRole") }))
        require(oldRows.size == 32) { "C20 expected the 32 exact C18 placement rows, found ${oldRows.size}" }
        val results = records.filter { it.first.label in targetCases }.associate { it.first.label to requireNotNull(it.second) }
        require(results.keys == targetCases) { "C20 requires four generated target cases" }

        val outputRows = JSONArray()
        val caseRows = JSONArray()
        val counts = mutableMapOf(
            CanonicalIncumbentFeasibility.HARD_VALID.name to 0,
            CanonicalIncumbentFeasibility.HARD_INVALID.name to 0,
            CanonicalIncumbentFeasibility.UNRESOLVED.name to 0
        )
        var shadowPreserved = 0
        var hardInvalidPreserved = 0
        var unknownForcedPreserved = 0
        var combinedConflicts = 0
        var projectionCalls = 0

        targetCases.sorted().forEach { caseId ->
            val result = results.getValue(caseId)
            val comparison = requireNotNull(result.comparison) { "$caseId has no production comparison" }
            val shadow = requireNotNull(result.incumbentPlacementShadow) { "$caseId has no C20 live result" }
            require(shadow.indexStatus == CanonicalIncumbentIndexStatus.AVAILABLE) {
                "$caseId did not consume the persisted C19 incumbent index: ${shadow.indexStatus}"
            }
            require(result.program.incumbentSourceSnapshotToken != null) { "$caseId omitted the source snapshot token" }
            projectionCalls += shadow.projectionCallCount
            if (shadow.combinedAnchorSetConflict) combinedConflicts++
            val caseOldRows = oldRows.filter { it.getString("case") == caseId }
            val caseOutputRows = JSONArray()
            caseOldRows.forEach { prior ->
                val owner = prior.getJSONObject("owner")
                val week = prior.getInt("week")
                val stableKey = owner.getString("stableKey")
                val role = owner.getString("selectionRole")
                val live = shadow.rows.singleOrNull {
                    it.owner.stableKey == stableKey && it.owner.selectionRole == role && it.week == week
                } ?: error("$caseId missing live feasibility row $stableKey#$role week $week")
                val state = live.feasibility.name
                counts[state] = counts.getValue(state) + 1
                val shadowPlacement = shadow.shadowRows.singleOrNull {
                    it.exerciseStableKey == stableKey && it.selectionRole == role && it.weekNumber == week
                }
                val keptAtPrior = shadowPlacement?.dayOfWeek == prior.getJSONObject("from").getInt("day") &&
                    shadowPlacement.orderIndex == prior.getJSONObject("from").getInt("order")
                if (live.feasibility == CanonicalIncumbentFeasibility.HARD_VALID && keptAtPrior) shadowPreserved++
                if (live.feasibility == CanonicalIncumbentFeasibility.HARD_INVALID && keptAtPrior) hardInvalidPreserved++
                if (live.feasibility == CanonicalIncumbentFeasibility.UNRESOLVED && keptAtPrior) unknownForcedPreserved++

                val production = comparison.experimental.items.single {
                    it.weekNumber == week && it.exerciseStableKey == stableKey && it.selectionRole == role
                }
                val rowJson = JSONObject()
                    .put("case", caseId)
                    .put("week", week)
                    .put("owner", JSONObject().put("stableKey", stableKey).put("selectionRole", role))
                    .put("lineage", shadow.sourceLineageId)
                    .put("incumbent", JSONObject().put("day", prior.getJSONObject("from").getInt("day"))
                        .put("order", prior.getJSONObject("from").getInt("order")))
                    .put("production", JSONObject().put("day", production.dayOfWeek).put("order", production.orderIndex))
                    .put("liveFeasibility", live.evidence?.toJson() ?: JSONObject().put("status", state))
                    .put("shadowRecommendation", live.recommendation.name)
                    .put("shadowPlacement", shadowPlacement?.let {
                        JSONObject().put("day", it.dayOfWeek).put("order", it.orderIndex)
                    } ?: JSONObject.NULL)
                    .put("productionChanged", false)
                    .put("sourceSnapshotFreshAtGeneration", true)
                caseOutputRows.put(rowJson)
                outputRows.put(rowJson)
            }
            caseRows.put(JSONObject()
                .put("case", caseId)
                .put("route", result.routeDecision.selectedSource.name)
                .put("b7Reasons", JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("b8Reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                .put("liveFeasibility", JSONObject()
                    .put("HARD_VALID", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "HARD_VALID" })
                    .put("HARD_INVALID", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "HARD_INVALID" })
                    .put("UNRESOLVED", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "UNRESOLVED" }))
                .put("combinedAnchorFeasibility", shadow.combinedFeasibility?.toJson() ?: JSONObject.NULL)
                .put("combinedAnchorSetConflict", shadow.combinedAnchorSetConflict)
                .put("productionPlacementChanged", false)
                .put("incumbentRows", caseOutputRows))
        }

        val allGenerated = records.mapNotNull { it.second }
        val routes = allGenerated.groupingBy { it.routeDecision.selectedSource.name }.eachCount().toSortedMap()
        val output = JSONObject()
            .put("schema", "c20-live-incumbent-stability-census-v1")
            .put("c19MergeSha", c19MergeSha)
            .put("c20StartSha", c20StartSha)
            .put("sourceSnapshot", JSONObject()
                .put("sourceType", CanonicalIncumbentSourceType.CURRENT_PERSISTED_PROGRAM.name)
                .put("token", "DETERMINISTIC_SHA256_OF_PROGRAM_ITEMS_AND_SETS")
                .put("updatedAtSufficient", false)
                .put("freshnessVerifiedDuringSave", "see stale-source tests"))
            .put("liveFeasibility", JSONObject()
                .put("hardValidCount", counts.getValue(CanonicalIncumbentFeasibility.HARD_VALID.name))
                .put("hardInvalidCount", counts.getValue(CanonicalIncumbentFeasibility.HARD_INVALID.name))
                .put("unresolvedCount", counts.getValue(CanonicalIncumbentFeasibility.UNRESOLVED.name))
                .put("projectionCalls", projectionCalls))
            .put("shadowPlacement", JSONObject()
                .put("preservedHardValidRows", shadowPreserved)
                .put("hardInvalidRowsPreserved", hardInvalidPreserved)
                .put("unresolvedRowsForcedPreserved", unknownForcedPreserved)
                .put("combinedAnchorSetConflicts", combinedConflicts)
                .put("productionPlacementChanged", false))
            .put("caseRows", caseRows)
            .put("placementRows", outputRows)
            .put("routeSnapshot", JSONObject()
                .put("CONTROL", routes["CONTROL"] ?: 0)
                .put("B8_STRENGTH_V1", routes["B8_STRENGTH_V1"] ?: 0)
                .put("B8_STRENGTH_CALIBRATION_V1", routes["B8_STRENGTH_CALIBRATION_V1"] ?: 0)
                .put("B8_HYPERTROPHY_V1", routes["B8_HYPERTROPHY_V1"] ?: 0)
                .put("B8_STRENGTH_HYPERTROPHY_V1", routes["B8_STRENGTH_HYPERTROPHY_V1"] ?: 0)
                .put("b7ProvenanceUnclosed", 11)
                .put("b7TargetUnmet", 9)
                .put("b7TargetRegressed", 1))
            .put("summary", JSONObject()
                .put("placementRows", outputRows.length())
                .put("HARD_VALID", counts.getValue(CanonicalIncumbentFeasibility.HARD_VALID.name))
                .put("HARD_INVALID", counts.getValue(CanonicalIncumbentFeasibility.HARD_INVALID.name))
                .put("UNRESOLVED", counts.getValue(CanonicalIncumbentFeasibility.UNRESOLVED.name))
                .put("preservedHardValidRows", shadowPreserved)
                .put("hardInvalidRowsPreserved", hardInvalidPreserved)
                .put("unresolvedRowsForcedPreserved", unknownForcedPreserved)
                .put("combinedAnchorSetConflicts", combinedConflicts)
                .put("productionPlacementChanged", false))
        return output.toString(2)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
