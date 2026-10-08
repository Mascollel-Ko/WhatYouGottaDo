package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic post-C32 view of the same 22 sparse movement identities audited by C31. */
internal object C32StrengthExposureCorpusCensus {
    fun render(
        c29Census: String,
        planningByCase: Map<String, CanonicalStimulusPlanningResult>,
        selectionByCase: Map<String, StimulusCandidateSelectionPlan>,
        authorizationByCase: Map<String, StimulusPrescriptionAuthorizationPlan>,
        generationByCase: Map<String, StimulusProductionGenerationResult>,
        generationTimeMillisByCase: Map<String, Long>,
        startSha: String
    ): String {
        val needs = JSONObject(c29Census).getJSONArray("needs").objects().sortedWith(compareBy(
            { it.getString("case") }, { it.getString("stableKey") }
        ))
        val rows = needs.map { need ->
            val caseName = need.getString("case")
            val planning = requireNotNull(planningByCase[caseName])
            val selection = requireNotNull(selectionByCase[caseName])
            val authorization = requireNotNull(authorizationByCase[caseName])
            val generation = requireNotNull(generationByCase[caseName])
            val coverage = MovementCoverage.valueOf(need.getJSONArray("representedGapCodes").getString(0))
            val movementDecision = planning.decisionPortfolio.movementDecisions.singleOrNull {
                it.movementCoverage == coverage
            }
            val movementTarget = planning.targetPlan.movementTargets.singleOrNull {
                it.movementCoverage == coverage
            }
            val strengthNeed = planning.athleteStimulusNeedProfile.qualityNeeds.single {
                it.quality == TrainableQuality.STRENGTH
            }
            val strengthDecision = planning.decisionPortfolio.qualityDecisions.singleOrNull {
                it.quality == TrainableQuality.STRENGTH
            }
            val strengthTarget = planning.targetPlan.qualityTargets.singleOrNull {
                it.quality == TrainableQuality.STRENGTH
            }
            val strengthTrace = selection.traces.singleOrNull { it.targetId == "QUALITY:STRENGTH" }
            val movementTrace = movementTarget?.let { target ->
                selection.traces.singleOrNull { it.targetId == target.targetId }
            }
            val strengthAuthorizations = authorization.authorizations.filter {
                it.quality == TrainableQuality.STRENGTH
            }
            val experimental = generation.comparison?.experimental
            val selectedMovementOwner = movementTrace?.selectedStableKey
            val selectedMovementRole = movementTrace?.selectedSelectionRole
            val materializedMovementWeeks = if (selectedMovementOwner == null || selectedMovementRole == null) {
                emptyList()
            } else {
                experimental?.items.orEmpty().filter { item ->
                    item.exerciseStableKey == selectedMovementOwner && item.selectionRole == selectedMovementRole
                }.map { it.weekNumber }.distinct().sorted()
            }
            val exactMovementOwnerHasB6 = selectedMovementOwner != null && selectedMovementRole != null &&
                authorization.authorizations.any { auth ->
                    auth.owner?.stableKey == selectedMovementOwner && auth.owner.selectionRole == selectedMovementRole &&
                        auth.status in setOf(
                            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                        )
                }
            val readiness = generation.comparison?.experimentalReadinessAudit
            val cutover = generation.comparison?.productionCutoverAuthority
            val strengthSets = experimental?.items.orEmpty().flatMap { item ->
                item.setPrescriptions.filter { set ->
                    CanonicalStrengthExposureCapability.strengthExposureEligible(item.stableKey, set.reps)
                }.map { set -> JSONObject()
                    .put("stableKey", item.stableKey)
                    .put("selectionRole", item.selectionRole)
                    .put("reps", set.reps)
                }
            }
            JSONObject()
                .put("case", caseName)
                .put("movementCoverage", coverage.name)
                .put("priorC31", JSONObject()
                    .put("movementB3", "ADDRESS")
                    .put("movementB4Target", true)
                    .put("movementB5SelectedOwner", need.optString("stableKey", JSONObject.NULL.toString()))
                    .put("movementExecution", "GENUINE_NO_B6_POLICY"))
                .put("b1Strength", JSONObject()
                    .put("current28dDirectUnits", strengthNeed.exposure.current28d.directUnits)
                    .put("prior28dDirectUnits", strengthNeed.exposure.prior28d.directUnits)
                    .put("needDecision", strengthNeed.decision.name)
                    .put("relevance", strengthNeed.relevance.name)
                    .put("evidenceBasis", strengthNeed.exposure.evidenceBasis.name)
                    .put("reasonCodes", JSONArray(strengthNeed.reasonCodes)))
                .put("b3Strength", strengthDecision?.let { JSONObject()
                    .put("decision", it.needDecision.name)
                    .put("strategy", it.strategy.name)
                    .put("reasonCodes", JSONArray(it.reasonCodes))
                } ?: JSONObject.NULL)
                .put("b4Strength", strengthTarget?.let { JSONObject()
                    .put("present", true)
                    .put("strategy", it.strategy.name)
                    .put("numericAuthority", it.numericAuthority.name)
                    .put("priority", it.priority.name)
                    .put("reasonCodes", JSONArray(it.reasonCodes))
                } ?: JSONObject().put("present", false))
                .put("movementDisposition", movementDecision?.disposition?.name ?: JSONObject.NULL)
                .put("movementB4TargetPresent", movementTarget != null)
                .put("movementB4NumericAuthority", movementTarget?.numericAuthority?.name ?: JSONObject.NULL)
                .put("b5StrengthSelectedOwner", strengthTrace?.selectedStableKey ?: JSONObject.NULL)
                .put("b5StrengthSelectionRole", strengthTrace?.selectedSelectionRole ?: JSONObject.NULL)
                .put("b5MovementSelectedOwner", movementTrace?.selectedStableKey ?: JSONObject.NULL)
                .put("b5MovementSelectionRole", movementTrace?.selectedSelectionRole ?: JSONObject.NULL)
                .put("movementMaterialization", JSONObject()
                    .put("materializedOwnerWeeks", JSONArray(materializedMovementWeeks))
                    .put("exactOwnerHasAuthorizedB6", exactMovementOwnerHasB6)
                    .put("unauthorizedExecutableOwnerWeeks", if (exactMovementOwnerHasB6) 0 else materializedMovementWeeks.size))
                .put("b6StrengthAuthorizations", JSONArray(strengthAuthorizations.map { row -> JSONObject()
                    .put("targetId", row.targetId)
                    .put("stableKey", row.owner?.stableKey ?: JSONObject.NULL)
                    .put("selectionRole", row.owner?.selectionRole ?: JSONObject.NULL)
                    .put("status", row.status.name)
                    .put("reasonCodes", JSONArray(row.reasonCodes))
                }))
                .put("strengthShortfalls", JSONArray(selection.strengthShortfalls.map { shortfall -> JSONObject()
                    .put("targetId", shortfall.targetId)
                    .put("reason", shortfall.reason.name)
                }))
                .put("finalStrengthShortfalls", JSONArray(generation.strengthShortfalls.map { shortfall -> JSONObject()
                    .put("targetId", shortfall.targetId)
                    .put("reason", shortfall.reason.name)
                }))
                .put("programNoticeCodes", JSONArray(generation.program.optimizationSummary.notices.map { it.code.name }.sorted()))
                .put("experimentalStrengthEligibleSetRows", JSONArray(strengthSets))
                .put("strengthShortfallNotice", generation.program.optimizationSummary.notices.any {
                    it.code == com.training.trackplanner.data.ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                        it.level == com.training.trackplanner.data.ProgramUserNoticeLevel.WARNING
                })
                .put("upstreamFailureReason", generation.upstreamFailureReason ?: JSONObject.NULL)
                .put("upstreamFailureDetails", JSONArray(generation.upstreamFailureDetails))
                .put("b7", readiness?.let { audit -> JSONObject()
                    .put("status", audit.status.name)
                    .put("reasons", JSONArray(audit.reasonCodes))
                    .put("changeProvenanceClosed", audit.changeProvenanceClosed)
                    .put("collateralRegressionFree", audit.collateralRegressionFree)
                    .put("targetOutcomes", JSONArray(audit.targetOutcomes.map { outcome -> JSONObject()
                        .put("targetId", outcome.targetId)
                        .put("status", outcome.status.name)
                        .put("reasons", JSONArray(outcome.reasonCodes))
                    }))
                } ?: JSONObject.NULL)
                .put("b8", cutover?.let { decision -> JSONObject()
                    .put("scope", decision.scope.name)
                    .put("status", decision.status.name)
                    .put("reasons", JSONArray(decision.reasonCodes))
                } ?: JSONObject.NULL)
                .put("route", generation.routeDecision.selectedSource.name)
                .put("buildCounts", JSONObject()
                    .put("control", generation.buildCounts.controlBuilds)
                    .put("experimental", generation.buildCounts.experimentalBuilds)
                    .put("total", generation.buildCounts.totalBuildInvocations)
                    .put("third", generation.buildCounts.thirdBuilds))
        }
        val caseRows = rows.distinctBy { it.getString("case") }
        val durations = caseRows.mapNotNull { generationTimeMillisByCase[it.getString("case")] }.sorted()
        val fullCorpusDurations = generationTimeMillisByCase.values.sorted()
        val fullCorpusMedian = when {
            fullCorpusDurations.isEmpty() -> 0.0
            fullCorpusDurations.size % 2 == 1 -> fullCorpusDurations[fullCorpusDurations.size / 2].toDouble()
            else -> (fullCorpusDurations[fullCorpusDurations.size / 2 - 1] +
                fullCorpusDurations[fullCorpusDurations.size / 2]) / 2.0
        }
        val routeCounts = caseRows.groupingBy { it.getString("route") }.eachCount().toSortedMap()
        val strengthStrategyCounts = caseRows.mapNotNull { row ->
            row.optJSONObject("b4Strength")?.takeIf { it.optBoolean("present") }?.optString("strategy")
        }.groupingBy { it }.eachCount().toSortedMap()
        val dispositions = rows.groupingBy { it.optString("movementDisposition", "NONE") }.eachCount().toSortedMap()
        val authorizedStrength = rows.sumOf { row ->
            val auths = row.getJSONArray("b6StrengthAuthorizations")
            (0 until auths.length()).count { auths.getJSONObject(it).getString("status") in setOf(
                "AUTHORIZED_EXISTING_COMPATIBLE", "AUTHORIZED_SAFE_REPAIR", "AUTHORIZED_COLD_START_USER_CALIBRATION"
            ) }
        }
        return JSONObject()
            .put("phase", "C32")
            .put("title", "Strength exposure capability and C31 movement corpus re-evaluation")
            .put("startSha", startSha)
            .put("strengthExposurePolicy", JSONObject()
                .put("provenance", CanonicalStrengthExposureCapability.policyProvenance)
                .put("version", CanonicalStrengthExposureCapability.policyVersion)
                .put("approvedStableKeys", JSONArray(CanonicalStrengthExposureCapability.approvedStableKeys.sorted()))
                .put("eligibility", "strengthPossible(stableKey) && reps in 1..6"))
            .put("before", JSONObject()
                .put("movementB3Address", 22)
                .put("movementB4Targets", 22)
                .put("movementB5Owners", 22)
                .put("movementB6PolicyUnsupported", 22)
                .put("movementUnauthorizedRows", 0))
            .put("summary", JSONObject()
                .put("movementIdentities", rows.size)
                .put("uniqueSparseGenerationCases", caseRows.size)
                .put("movementB3Dispositions", JSONObject().apply { dispositions.forEach { (key, count) -> put(key, count) } })
                .put("movementB4Targets", rows.count { it.getBoolean("movementB4TargetPresent") })
                .put("strengthB4TargetContexts", rows.count { it.getJSONObject("b4Strength").optBoolean("present") })
                .put("strengthB4TargetCases", caseRows.count { it.getJSONObject("b4Strength").optBoolean("present") })
                .put("strengthB4StrategiesByCase", JSONObject().apply {
                    strengthStrategyCounts.forEach { (strategy, count) -> put(strategy, count) }
                })
                .put("strengthB5SelectedOwnerContexts", rows.count { !it.isNull("b5StrengthSelectedOwner") })
                .put("strengthB5SelectedOwnerCases", caseRows.count { !it.isNull("b5StrengthSelectedOwner") })
                .put("strengthB6AuthorizedTargets", authorizedStrength)
                .put("strengthShortfallTargetCount", caseRows.sumOf { it.getJSONArray("finalStrengthShortfalls").length() })
                .put("strengthShortfallCases", caseRows.count { it.getJSONArray("finalStrengthShortfalls").length() > 0 })
                .put("strengthShortfallNotices", caseRows.count { it.getBoolean("strengthShortfallNotice") })
                .put("noMinimumStrengthCasesWithFalseShortfallNotice", caseRows.count { row ->
                    row.optJSONObject("b4Strength")?.optString("strategy") == "NO_MINIMUM_TARGET" &&
                        row.getBoolean("strengthShortfallNotice")
                })
                .put("experimentalStrengthEligibleSetRows", caseRows.sumOf { it.getJSONArray("experimentalStrengthEligibleSetRows").length() })
                .put("movementExecutableOwnerWeeks", rows.sumOf { it.getJSONObject("movementMaterialization").getJSONArray("materializedOwnerWeeks").length() })
                .put("movementUnauthorizedExecutableRows", rows.sumOf { it.getJSONObject("movementMaterialization").getInt("unauthorizedExecutableOwnerWeeks") })
                .put("routes", JSONObject().apply { routeCounts.forEach { (key, count) -> put(key, count) } })
                .put("builds", JSONObject()
                    .put("control", caseRows.sumOf { it.getJSONObject("buildCounts").getInt("control") })
                    .put("experimental", caseRows.sumOf { it.getJSONObject("buildCounts").getInt("experimental") })
                    .put("total", caseRows.sumOf { it.getJSONObject("buildCounts").getInt("total") })
                    .put("third", caseRows.sumOf { it.getJSONObject("buildCounts").getInt("third") }))
                .put("generationTimeMs", JSONObject()
                    .put("total", durations.sum())
                    .put("median", durations.getOrNull(durations.size / 2) ?: 0L)
                    .put("max", durations.maxOrNull() ?: 0L))
                .put("allGeneratedCorpusGenerationTimeMs", JSONObject()
                    .put("generatedCases", fullCorpusDurations.size)
                    .put("total", fullCorpusDurations.sum())
                    .put("meanPerCase", if (fullCorpusDurations.isEmpty()) 0.0 else fullCorpusDurations.average())
                    .put("medianPerCase", fullCorpusMedian)
                    .put("minPerCase", fullCorpusDurations.minOrNull() ?: 0L)
                    .put("maxPerCase", fullCorpusDurations.maxOrNull() ?: 0L)
                    .put("rangePerCase", if (fullCorpusDurations.isEmpty()) 0L else fullCorpusDurations.max() - fullCorpusDurations.min())))
            .put("fullGeneratedCorpus", JSONObject()
                .put("generatedCases", generationByCase.size)
                .put("routes", JSONObject().apply {
                    generationByCase.values.groupingBy { it.routeDecision.selectedSource.name }.eachCount().toSortedMap()
                        .forEach { (route, count) -> put(route, count) }
                })
                .put("builds", JSONObject()
                    .put("control", generationByCase.values.sumOf { it.buildCounts.controlBuilds })
                    .put("experimental", generationByCase.values.sumOf { it.buildCounts.experimentalBuilds })
                    .put("total", generationByCase.values.sumOf { it.buildCounts.totalBuildInvocations })
                    .put("third", generationByCase.values.sumOf { it.buildCounts.thirdBuilds }))
                .put("strengthShortfallNotices", generationByCase.values.count { result ->
                    result.program.optimizationSummary.notices.any {
                        it.code == com.training.trackplanner.data.ProgramUserNoticeCode.STRENGTH_EXPOSURE_SHORTFALL &&
                            it.level == com.training.trackplanner.data.ProgramUserNoticeLevel.WARNING
                    }
                })
                .put("b7EvaluatedCases", generationByCase.values.count { it.comparison?.experimentalReadinessAudit != null })
                .put("b8EvaluatedCases", generationByCase.values.count { it.comparison?.productionCutoverAuthority != null })
                .put("experimentalStrengthEligibleSetRows", generationByCase.values.sumOf { result ->
                    result.comparison?.experimental?.items.orEmpty().sumOf { item ->
                        item.setPrescriptions.count { set ->
                            CanonicalStrengthExposureCapability.strengthExposureEligible(item.stableKey, set.reps)
                        }
                    }
                }))
            .put("cases", JSONArray(rows))
            .toString(2)
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
