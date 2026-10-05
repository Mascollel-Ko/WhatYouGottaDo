package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C24 output census built from the existing CONTROL/EXPERIMENTAL pair only. */
internal object C24BadmintonTaskB6Census {
    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        c23Census: String,
        c21PowerCensus: String,
        c20Census: String,
        standardCoverageSha256: String,
        metadataRepository: CanonicalExerciseMetadataRepository
    ): String {
        val c23 = JSONObject(c23Census)
        val c21Power = JSONObject(c21PowerCensus).getJSONObject("counts")
        val c20 = JSONObject(c20Census)
        val sourceCases = c23.getJSONArray("corpusCases")
        val results = records.associate { it.first.label to it.second }
        val relationCatalog = metadataRepository.badmintonObjectiveCatalog()
        val outputCases = JSONArray()
        val totals = linkedMapOf(
            "taskTargetsTotal" to 0, "directionOnlyTaskTargets" to 0,
            "exactApprovedBindingsUsed" to 0, "authorizedTaskB6Owners" to 0,
            "materializedTaskProtocolRows" to 0, "taskTargetsReceivingCredit" to 0,
            "weeklyFrequencyShortfalls" to 0, "placementShortfalls" to 0,
            "unapprovedTaskOwnersDeferred" to 0, "untypedTaskMaterialRows" to 0, "powerMaterialRows" to 0,
            "jumpLandingMaterialRows" to 0
        )
        val b7Reasons = linkedMapOf<String, Int>()
        val routes = linkedMapOf<String, Int>()
        var c20HardValid = 0
        var c20HardInvalid = 0
        var c20Unresolved = 0
        var c20InvalidOrUnknownPreserved = 0

        for (caseIndex in 0 until sourceCases.length()) {
            val sourceCase = sourceCases.getJSONObject(caseIndex)
            val caseName = sourceCase.getString("case")
            val result = results[caseName]
            val comparison = result?.comparison
            val experimental = comparison?.experimental
            val selectedOwners = comparison?.selectionPlan?.selectedCandidates.orEmpty()
                .filter { candidate -> candidate.coveredTargetIds.any { it.startsWith("TASK:") } }
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            val taskRows = experimental?.items.orEmpty().mapNotNull { item ->
                val raw = item.taskProtocolSemanticsJson ?: return@mapNotNull null
                val metadata = runCatching { TaskProtocolExposureMetadata.fromJsonString(raw) }.getOrNull()
                    ?: return@mapNotNull null
                JSONObject().put("week", item.weekNumber).put("day", item.dayOfWeek).put("order", item.orderIndex)
                    .put("stableKey", item.exerciseStableKey).put("selectionRole", item.selectionRole)
                    .put("protocolId", metadata.authorization.definition.protocolId)
                    .put("exposureIndex", metadata.exposureIndex)
                    .put("setCount", item.setCount).put("prescription", item.prescription)
                    .put("restSeconds", item.restSeconds).put("activityKind", metadata.authorization.materializationActivityKind.name)
                    .put("loadMode", metadata.authorization.definition.shape.loadMode?.name ?: JSONObject.NULL)
                    .put("targetAttributions", JSONArray(metadata.authorization.attributedTasks.sortedBy { it.ordinal }.map { it.name }))
            }.sortedWith(compareBy<JSONObject>({ it.getInt("week") }, { it.getInt("day") }, { it.getInt("order") }, { it.getString("stableKey") }))
            totals["untypedTaskMaterialRows"] = totals.getValue("untypedTaskMaterialRows") +
                experimental?.items.orEmpty().count { it.selectionRole.startsWith("CANONICAL_STIMULUS_TASK_") && it.taskProtocolSemanticsJson.isNullOrBlank() }
            totals["materializedTaskProtocolRows"] = totals.getValue("materializedTaskProtocolRows") + taskRows.size
            totals["exactApprovedBindingsUsed"] = totals.getValue("exactApprovedBindingsUsed") +
                taskRows.map { it.getString("protocolId") }.distinct().size
            totals["authorizedTaskB6Owners"] = totals.getValue("authorizedTaskB6Owners") + selectedOwners.count { owner ->
                taskRows.any { it.getString("stableKey") == owner.stableKey && it.getString("selectionRole") == owner.selectionRole }
            }
            totals["unapprovedTaskOwnersDeferred"] = totals.getValue("unapprovedTaskOwnersDeferred") + selectedOwners.count { owner ->
                taskRows.none { it.getString("stableKey") == owner.stableKey && it.getString("selectionRole") == owner.selectionRole }
            }
            val outcomes = JSONArray(comparison?.experimentalReadinessAudit?.targetOutcomes.orEmpty().map { outcome ->
                JSONObject().put("targetId", outcome.targetId).put("status", outcome.status.name)
                    .put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
            }.sortedBy { it.getString("targetId") })
            // Count target identities explicitly attributed by a validated physical protocol
            // row. Readiness status alone is not exposure credit (NOT_APPLICABLE/INCONCLUSIVE
            // must never inflate this measure).
            totals["taskTargetsReceivingCredit"] = totals.getValue("taskTargetsReceivingCredit") +
                taskRows.flatMap { row ->
                    val attributions = row.getJSONArray("targetAttributions")
                    (0 until attributions.length()).map { attributions.getString(it) }
                }.distinct().size
            val frequency = JSONArray(experimental?.taskProtocolFrequencyOutcomes.orEmpty().map { item ->
                JSONObject().put("protocolId", item.protocolId).put("stableKey", item.stableKey)
                    .put("selectionRole", item.selectionRole).put("week", item.week)
                    .put("requestedExposures", item.requestedExposures).put("placedExposures", item.placedExposures)
                    .put("shortfall", item.shortfall).put("status", item.status.name)
                    .put("authority", item.authority.name).put("reasonCode", item.reasonCode)
            })
            totals["weeklyFrequencyShortfalls"] = totals.getValue("weeklyFrequencyShortfalls") +
                frequency.countObjects { it.getString("status") == TaskProtocolFrequencyStatus.SHORTFALL.name }
            totals["placementShortfalls"] = totals.getValue("placementShortfalls") +
                frequency.countObjects { it.getInt("shortfall") > 0 }

            val targetSources = sourceCase.optJSONArray("taskTargets") ?: JSONArray()
            val targets = JSONArray()
            for (index in 0 until targetSources.length()) {
                val sourceTarget = targetSources.getJSONObject(index)
                val task = CanonicalTaskTarget.valueOf(sourceTarget.getString("task"))
                val targetId = "TASK:${task.name}"
                val targetOwners = selectedOwners.filter { targetId in it.coveredTargetIds }
                val matchingRows = taskRows.filter { row ->
                    val attributed = row.getJSONArray("targetAttributions")
                    (0 until attributed.length()).any { attributed.getString(it) == task.name }
                }
                val relationDetails = targetOwners.map { owner ->
                    val relation = relationCatalog.relations(owner.stableKey).singleOrNull { it.objective.name == task.name }
                    JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                        .put("primaryTargetId", owner.primaryTargetId)
                        .put("transferLevel", relation?.transferLevel?.name ?: "NONE")
                        .put("relationId", relation?.relationId ?: JSONObject.NULL)
                }
                val outcome = comparison?.experimentalReadinessAudit?.targetOutcomes?.singleOrNull { it.targetId == targetId }
                val sourceB4 = sourceTarget.optJSONObject("b4Target") ?: JSONObject()
                totals["taskTargetsTotal"] = totals.getValue("taskTargetsTotal") + 1
                if (sourceB4.optString("numericAuthority") == "DIRECTION_ONLY") {
                    totals["directionOnlyTaskTargets"] = totals.getValue("directionOnlyTaskTargets") + 1
                }
                targets.put(JSONObject().put("task", task.name)
                    .put("need", sourceTarget.opt("need").takeUnless { sourceTarget.isNull("need") } ?: JSONObject.NULL)
                    .put("b3Decision", sourceTarget.opt("b3Decision").takeUnless { sourceTarget.isNull("b3Decision") } ?: JSONObject.NULL)
                    .put("b4Target", sourceTarget.opt("b4Target").takeUnless { sourceTarget.isNull("b4Target") } ?: JSONObject.NULL)
                    .put("b5Owners", JSONArray(relationDetails))
                    .put("approvedProtocolIds", JSONArray(matchingRows.map { it.getString("protocolId") }.distinct().sorted()))
                    .put("frequencyAuthority", if (matchingRows.isEmpty()) JSONObject.NULL else "USER_APPROVED_PROJECT_POLICY")
                    .put("weeklySessions", if (matchingRows.isEmpty()) JSONObject.NULL else 2)
                    .put("materializedRows", JSONArray(matchingRows))
                    .put("targetOutcome", outcome?.let { JSONObject().put("status", it.status.name)
                        .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL))
            }

            val readiness = comparison?.experimentalReadinessAudit
            readiness?.reasonCodes.orEmpty().forEach { code -> b7Reasons[code] = b7Reasons.getOrDefault(code, 0) + 1 }
            val route = result?.routeDecision?.selectedSource?.name ?: "PRELIGHT_REJECTED"
            routes[route] = routes.getOrDefault(route, 0) + 1
            val c20 = c20Case(JSONObject(c20Census), caseName)
            val c20Rows = c20.optJSONArray("incumbentRows") ?: JSONArray()
            c20HardValid += c20Rows.countObjects { it.optJSONObject("liveFeasibility")?.optString("status") == "HARD_VALID" }
            c20HardInvalid += c20Rows.countObjects { it.optJSONObject("liveFeasibility")?.optString("status") == "HARD_INVALID" }
            c20Unresolved += c20Rows.countObjects { it.optJSONObject("liveFeasibility")?.optString("status") == "UNRESOLVED" }
            c20InvalidOrUnknownPreserved += c20Rows.countObjects { row ->
                row.optJSONObject("liveFeasibility")?.optString("status") in setOf("HARD_INVALID", "UNRESOLVED") &&
                    row.optJSONObject("productionAfter")?.optInt("day") == row.optJSONObject("incumbent")?.optInt("day") &&
                    row.optJSONObject("productionAfter")?.optInt("order") == row.optJSONObject("incumbent")?.optInt("order")
            }
            val b8 = comparison?.productionCutoverAuthority
            outputCases.put(JSONObject().put("case", caseName).put("preflight", sourceCase.optString("preflight"))
                .put("taskTargets", targets).put("selectedTaskOwners", JSONArray(selectedOwners.map { owner ->
                    JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                        .put("primaryTargetId", owner.primaryTargetId).put("coveredTargetIds", JSONArray(owner.coveredTargetIds.sorted()))
                }))
                .put("protocolRows", JSONArray(taskRows)).put("frequencyOutcomes", frequency)
                .put("targetOutcomes", outcomes)
                .put("b7", readiness?.let { JSONObject().put("status", it.status.name)
                    .put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    .put("changeProvenanceClosed", it.changeProvenanceClosed)
                    .put("collateralRegressionFree", it.collateralRegressionFree) } ?: JSONObject.NULL)
                .put("b8", b8?.let { JSONObject().put("status", it.status.name).put("scope", it.scope.name)
                    .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("route", route))
        }
        val powerRows = records.mapNotNull { it.second?.comparison?.experimental }.sumOf { program ->
            program.items.count { it.selectionRole == "CANONICAL_STIMULUS_QUALITY_POWER" }
        }
        val jumpRows = records.mapNotNull { it.second?.comparison?.experimental }.sumOf { program ->
            program.items.count { row -> row.selectionRole == "CANONICAL_STIMULUS_TASK_JUMP_LANDING" ||
                row.taskProtocolSemanticsJson?.let { json ->
                    runCatching { TaskProtocolExposureMetadata.fromJsonString(json).authorization.attributedTasks
                        .contains(CanonicalTaskTarget.JUMP_LANDING) }.getOrDefault(false)
                } == true }
        }
        totals["powerMaterialRows"] = powerRows
        totals["jumpLandingMaterialRows"] = jumpRows
        val frequencyRows = outputCases.flatMapObjects { it.optJSONArray("frequencyOutcomes") ?: JSONArray() }
        val b7Summary = JSONObject().apply { b7Reasons.toSortedMap().forEach { (key, value) -> put(key, value) } }
        val collateralRegressionCases = outputCases.countObjects { row ->
            row.optJSONObject("b7")?.optBoolean("collateralRegressionFree") == false
        }
        val routeSnapshot = JSONObject().apply { routes.toSortedMap().forEach { (key, value) -> put(key, value) } }
        val root = JSONObject().put("schema", "c24-badminton-task-b6-persistence-census-v1")
            .put("c23MergeHead", "9663bc7c078229adfb04f23dc66da0eb976778fe")
            .put("c24StartHead", "9663bc7c078229adfb04f23dc66da0eb976778fe")
            .put("standardProductionCoverageSha256", standardCoverageSha256)
            .put("versions", JSONObject().put("protocol", "3.59.0").put("runtime", "RECORD_BASED_PLANNER_0.15.1_KOTLIN_1").put("app", "0.5.1.5"))
            .put("approvedProtocols", JSONArray(ApprovedBadmintonTaskProtocols.definitions.map { definition ->
                JSONObject().put("protocolId", definition.protocolId).put("stableKey", definition.stableKey)
                    .put("selectionRole", definition.selectionRole).put("primaryTask", definition.primaryTask.name)
                    .put("authorizedTasks", JSONArray(definition.authorizedTasks.sortedBy { it.ordinal }.map { it.name }))
                    .put("weeklyExposures", definition.weeklyExposures).put("frequencyProvenance", definition.provenance.name)
                    .put("shape", definition.shape.toJson()).put("activitySemantics", definition.activitySemantics.name)
                    .put("creditSemantics", definition.creditSemantics.name)
            }))
            .put("counts", JSONObject(totals as Map<*, *>).put("corpusCases", outputCases.length())
                .put("frequencyOutcomeRows", frequencyRows.size).put("authorizedProtocolDefinitions", ApprovedBadmintonTaskProtocols.definitions.size))
            .put("cases", outputCases).put("b7ReasonOccurrences", b7Summary).put("routeSnapshot", routeSnapshot)
            .put("b7Summary", JSONObject().put("provenanceUnclosed", b7Reasons["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                .put("targetUnmet", b7Reasons["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                .put("targetRegressed", b7Reasons["TARGET_REGRESSED"] ?: 0)
                .put("collateralRegressionCases", collateralRegressionCases))
            .put("c20IncumbentRegression", JSONObject().put("HARD_VALID", c20HardValid)
                .put("HARD_INVALID", c20HardInvalid).put("UNRESOLVED", c20Unresolved)
                .put("invalidOrUnresolvedForcedPreserved", c20InvalidOrUnknownPreserved))
            .put("powerInvariant", JSONObject()
                .put("numericPowerAuthority", c21Power.getInt("numericPowerAuthorityAfter"))
                .put("executableB6", c21Power.getInt("fullyMaterializedPower"))
                .put("materialRows", powerRows))
            .put("jumpLandingInvariant", JSONObject().put("approvedProtocol", false).put("materialRows", jumpRows))
            .put("buildAccounting", JSONObject().put("CONTROL", 1).put("EXPERIMENTAL", 1).put("TOTAL", 2).put("THIRD", 0))
            .put("legacyUnauthorizedRows", c23.optJSONArray("taskMaterialRowsBefore") ?: JSONArray())
            .put("persona3Recent", outputCases.findObject("persona3_recent") ?: JSONObject())
            .put("b8TaskRouteAdded", false).put("b7OrB8RulesChanged", false)
        return root.toString(2)
    }

    private fun c20Case(census: JSONObject, name: String): JSONObject = census.optJSONArray("caseRows")?.let { rows ->
        (0 until rows.length()).map { rows.getJSONObject(it) }.singleOrNull { it.optString("case") == name }
    } ?: JSONObject()

    private fun JSONArray.countObjects(predicate: (JSONObject) -> Boolean): Int =
        (0 until length()).count { predicate(getJSONObject(it)) }

    private fun JSONArray.findObject(name: String): JSONObject? =
        (0 until length()).map { getJSONObject(it) }.singleOrNull { it.optString("case") == name }

    private fun JSONArray.flatMapObjects(transform: (JSONObject) -> JSONArray): List<JSONObject> =
        (0 until length()).flatMap { index ->
            val nested = transform(getJSONObject(index))
            (0 until nested.length()).map { nested.getJSONObject(it) }
        }
}
