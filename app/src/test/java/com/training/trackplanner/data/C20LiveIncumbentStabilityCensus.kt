package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.StimulusProductionGenerationResult
import com.training.trackplanner.data.personalized.StimulusPrescriptionAuthorizationStatus
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
        var hardValidPreserved = 0
        var hardInvalidForcedPreserved = 0
        var unresolvedForcedPreserved = 0
        var sharedPlacementDeltaBefore = 0
        var sharedPlacementDeltaAfter = 0
        var totalDayDistanceBefore = 0
        var totalDayDistanceAfter = 0
        var orderOnlyChangesBefore = 0
        var orderOnlyChangesAfter = 0
        var combinedConflicts = 0
        var projectionCalls = 0
        var dayOfiProjectionCalls = 0
        var tissueProjectionCalls = 0

        targetCases.sorted().forEach { caseId ->
            val result = results.getValue(caseId)
            val comparison = requireNotNull(result.comparison) { "$caseId has no production comparison" }
            val shadow = requireNotNull(result.incumbentPlacementShadow) { "$caseId has no C20 live result" }
            require(shadow.indexStatus == CanonicalIncumbentIndexStatus.AVAILABLE) {
                "$caseId did not consume the persisted C19 incumbent index: ${shadow.indexStatus}"
            }
            require(result.program.incumbentSourceSnapshotToken != null) { "$caseId omitted the source snapshot token" }
            projectionCalls += shadow.projectionCallCount
            dayOfiProjectionCalls += shadow.dayOfiProjectionCallCount
            tissueProjectionCalls += shadow.tissueProjectionCallCount
            if (shadow.combinedAnchorSetConflict) combinedConflicts++
            val caseOldRows = oldRows.filter { it.getString("case") == caseId }
            val caseOutputRows = JSONArray()
            var caseProductionPlacementChanged = false
            val shadowById = shadow.shadowRows.associateBy { it.localId }
            val allPlacementChanges = JSONArray()
            shadow.productionRows.sortedBy { it.localId }.forEach { before ->
                val after = shadowById[before.localId] ?: return@forEach
                if (before.dayOfWeek != after.dayOfWeek || before.orderIndex != after.orderIndex) {
                    allPlacementChanges.put(JSONObject()
                        .put("localId", before.localId)
                        .put("owner", JSONObject().put("stableKey", before.exerciseStableKey)
                            .put("selectionRole", before.selectionRole))
                        .put("week", before.weekNumber)
                        .put("from", JSONObject().put("day", before.dayOfWeek).put("order", before.orderIndex))
                        .put("to", JSONObject().put("day", after.dayOfWeek).put("order", after.orderIndex))
                        .put("isExactHardValidAnchor", shadow.rows.any { row ->
                            row.owner.stableKey == before.exerciseStableKey &&
                                row.owner.selectionRole == before.selectionRole && row.week == before.weekNumber &&
                                row.feasibility == CanonicalIncumbentFeasibility.HARD_VALID
                        }))
                }
            }
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
                val production = comparison.experimental.items.single {
                    it.weekNumber == week && it.exerciseStableKey == stableKey && it.selectionRole == role
                }
                val incumbentDay = prior.getJSONObject("from").getInt("day")
                val incumbentOrder = prior.getJSONObject("from").getInt("order")
                val beforeDay = live.productionDay
                val beforeOrder = live.productionOrder
                val afterDay = production.dayOfWeek
                val afterOrder = production.orderIndex
                if (beforeDay != afterDay || beforeOrder != afterOrder) caseProductionPlacementChanged = true
                val wasChangedBefore = beforeDay != incumbentDay || beforeOrder != incumbentOrder
                val isChangedAfter = afterDay != incumbentDay || afterOrder != incumbentOrder
                if (wasChangedBefore) sharedPlacementDeltaBefore++
                if (isChangedAfter) sharedPlacementDeltaAfter++
                totalDayDistanceBefore += kotlin.math.abs(beforeDay - incumbentDay)
                totalDayDistanceAfter += kotlin.math.abs(afterDay - incumbentDay)
                if (wasChangedBefore && beforeDay == incumbentDay) orderOnlyChangesBefore++
                if (isChangedAfter && afterDay == incumbentDay) orderOnlyChangesAfter++
                val keptAtPrior = afterDay == incumbentDay && afterOrder == incumbentOrder
                if (live.feasibility == CanonicalIncumbentFeasibility.HARD_VALID && keptAtPrior) hardValidPreserved++
                if (live.feasibility == CanonicalIncumbentFeasibility.HARD_INVALID && wasChangedBefore && keptAtPrior) hardInvalidForcedPreserved++
                if (live.feasibility == CanonicalIncumbentFeasibility.UNRESOLVED && wasChangedBefore && keptAtPrior) unresolvedForcedPreserved++
                val rowJson = JSONObject()
                    .put("case", caseId)
                    .put("week", week)
                    .put("owner", JSONObject().put("stableKey", stableKey).put("selectionRole", role))
                    .put("lineage", shadow.sourceLineageId)
                    .put("incumbent", JSONObject().put("day", incumbentDay).put("order", incumbentOrder))
                    .put("productionBefore", JSONObject().put("day", beforeDay).put("order", beforeOrder))
                    .put("productionAfter", JSONObject().put("day", afterDay).put("order", afterOrder))
                    .put("liveFeasibility", live.evidence?.toJson() ?: JSONObject().put("status", state))
                    .put("shadowRecommendation", live.recommendation.name)
                    .put("shadowPlacement", shadowPlacement?.let {
                        JSONObject().put("day", it.dayOfWeek).put("order", it.orderIndex)
                    } ?: JSONObject.NULL)
                    .put("productionChanged", beforeDay != afterDay || beforeOrder != afterOrder)
                    .put("sourceSnapshotFreshAtGeneration", true)
                caseOutputRows.put(rowJson)
                outputRows.put(rowJson)
            }
            caseRows.put(JSONObject()
                .put("case", caseId)
                .put("route", result.routeDecision.selectedSource.name)
                .put("b7Reasons", JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("b8Reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                .put("activationStatus", result.incumbentPlacementActivationStatus?.name)
                .put("activationDetails", JSONArray(result.incumbentPlacementActivationDetails.sorted()))
                .put("preservationEvents", JSONArray(result.incumbentPlacementPreservations.map { event ->
                    JSONObject().put("owner", JSONObject().put("stableKey", event.owner.stableKey)
                        .put("selectionRole", event.owner.selectionRole))
                        .put("week", event.week)
                        .put("from", JSONObject().put("day", event.producedDay).put("order", event.producedOrder))
                        .put("to", JSONObject().put("day", event.preservedDay).put("order", event.preservedOrder))
                        .put("lineage", event.sourceLineageId.value)
                        .put("snapshotToken", event.sourceSnapshotToken.value)
                        .put("feasibility", event.feasibility.toJson())
                }))
                .put("liveFeasibility", JSONObject()
                    .put("HARD_VALID", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "HARD_VALID" })
                    .put("HARD_INVALID", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "HARD_INVALID" })
                    .put("UNRESOLVED", caseOutputRows.objects().count { it.getJSONObject("liveFeasibility").getString("status") == "UNRESOLVED" }))
                .put("combinedAnchorFeasibility", shadow.combinedFeasibility?.toJson() ?: JSONObject.NULL)
                .put("combinedAnchorSetConflict", shadow.combinedAnchorSetConflict)
                .put("allShadowPlacementChanges", allPlacementChanges)
                .put("projectionCalls", shadow.projectionCallCount)
                .put("dayOfiProjectionCalls", shadow.dayOfiProjectionCallCount)
                .put("tissueProjectionCalls", shadow.tissueProjectionCallCount)
                .put("productionPlacementChanged", caseProductionPlacementChanged)
                .put("incumbentRows", caseOutputRows))
        }

        val allGenerated = records.mapNotNull { it.second }
        val routes = allGenerated.groupingBy { it.routeDecision.selectedSource.name }.eachCount().toSortedMap()
        val positiveReference = requireNotNull(records.singleOrNull { it.first.label == "persona2_reviewed" }?.second) {
            "C20 requires the persona2_reviewed stable-insertion reference"
        }
        val positiveComparison = requireNotNull(positiveReference.comparison)
        val positiveSharedRows = positiveComparison.control.items.mapNotNull { before ->
            positiveComparison.experimental.items.singleOrNull { after ->
                before.weekNumber == after.weekNumber && before.exerciseStableKey == after.exerciseStableKey &&
                    before.selectionRole == after.selectionRole
            }?.let { before to it }
        }
        fun b7Cases(reason: String) = allGenerated.count { result ->
            reason in result.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty()
        }
        val activations = targetCases.sorted().associateWith { results.getValue(it).incumbentPlacementActivationStatus?.name }
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
                .put("projectionCalls", projectionCalls)
                .put("dayOfiProjectionCalls", dayOfiProjectionCalls)
                .put("tissueProjectionCalls", tissueProjectionCalls)
                .put("cacheHits", 0)
                .put("generationScopedProjectionCache", false))
            .put("shadowPlacement", JSONObject()
                .put("activatedCases", JSONObject(activations))
                .put("preservedHardValidRows", hardValidPreserved)
                .put("hardInvalidRowsForcedPreserved", hardInvalidForcedPreserved)
                .put("unresolvedRowsForcedPreserved", unresolvedForcedPreserved)
                .put("combinedAnchorSetConflicts", combinedConflicts)
                .put("productionPlacementChanged", sharedPlacementDeltaBefore != sharedPlacementDeltaAfter)
                .put("sharedPlacementDeltaBefore", sharedPlacementDeltaBefore)
                .put("sharedPlacementDeltaAfter", sharedPlacementDeltaAfter)
                .put("totalDayDistanceBefore", totalDayDistanceBefore)
                .put("totalDayDistanceAfter", totalDayDistanceAfter)
                .put("orderOnlyChangesBefore", orderOnlyChangesBefore)
                .put("orderOnlyChangesAfter", orderOnlyChangesAfter))
            .put("caseRows", caseRows)
            .put("placementRows", outputRows)
            .put("positiveReference", JSONObject()
                .put("case", "persona2_reviewed")
                .put("route", positiveReference.routeDecision.selectedSource.name)
                .put("activationStatus", positiveReference.incumbentPlacementActivationStatus?.name)
                .put("sharedOwnerRows", positiveSharedRows.size)
                .put("sharedOwnerRowsPlacementUnchanged", positiveSharedRows.count { (before, after) ->
                    before.dayOfWeek == after.dayOfWeek && before.orderIndex == after.orderIndex
                })
                .put("preservationEvents", positiveReference.incumbentPlacementPreservations.size)
                .put("calibrationRows", positiveComparison.experimental.items.count { row ->
                    positiveComparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any { auth ->
                        auth.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                            auth.owner?.stableKey == row.exerciseStableKey && auth.owner.selectionRole == row.selectionRole
                    }
                }))
            .put("routeSnapshot", JSONObject()
                .put("CONTROL", routes["CONTROL"] ?: 0)
                .put("B8_STRENGTH_V1", routes["B8_STRENGTH_V1"] ?: 0)
                .put("B8_STRENGTH_CALIBRATION_V1", routes["B8_STRENGTH_CALIBRATION_V1"] ?: 0)
                .put("B8_HYPERTROPHY_V1", routes["B8_HYPERTROPHY_V1"] ?: 0)
                .put("B8_STRENGTH_HYPERTROPHY_V1", routes["B8_STRENGTH_HYPERTROPHY_V1"] ?: 0)
                .put("b7ProvenanceUnclosed", b7Cases("CHANGE_PROVENANCE_UNCLOSED"))
                .put("b7TargetUnmet", b7Cases("AFFECTED_TARGET_REMAINS_UNMET"))
                .put("b7TargetRegressed", b7Cases("TARGET_REGRESSED")))
            .put("summary", JSONObject()
                .put("placementRows", outputRows.length())
                .put("HARD_VALID", counts.getValue(CanonicalIncumbentFeasibility.HARD_VALID.name))
                .put("HARD_INVALID", counts.getValue(CanonicalIncumbentFeasibility.HARD_INVALID.name))
                .put("UNRESOLVED", counts.getValue(CanonicalIncumbentFeasibility.UNRESOLVED.name))
                .put("preservedHardValidRows", hardValidPreserved)
                .put("hardInvalidRowsForcedPreserved", hardInvalidForcedPreserved)
                .put("unresolvedRowsForcedPreserved", unresolvedForcedPreserved)
                .put("combinedAnchorSetConflicts", combinedConflicts)
                .put("productionPlacementChanged", sharedPlacementDeltaBefore != sharedPlacementDeltaAfter)
                .put("sharedPlacementDeltaBefore", sharedPlacementDeltaBefore)
                .put("sharedPlacementDeltaAfter", sharedPlacementDeltaAfter))
        return output.toString(2)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
