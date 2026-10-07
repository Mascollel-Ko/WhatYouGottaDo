package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C30 projection from canonical B1-B6 plans and the unchanged Room/service corpus. */
internal object C30MovementDispositionCensus {
    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        canonicalPlanningByCase: Map<String, CanonicalStimulusPlanningResult>,
        c29Census: String,
        c20Census: String,
        generationMillisByCase: Map<String, Long>,
        plannerMetricsByCase: Map<String, Map<String, Int>>,
        selectionPlanByCase: Map<String, StimulusCandidateSelectionPlan>,
        authorizationPlanByCase: Map<String, StimulusPrescriptionAuthorizationPlan>,
        startSha: String
    ): String {
        val priorNeeds = JSONObject(c29Census).getJSONArray("needs").objects().sortedWith(compareBy(
            { it.getString("case") }, { it.getString("stableKey") }, { it.getString("selectionRole") }
        ))
        val resultByCase = records.associate { it.first.label to it.second }
        val identityRows = mutableListOf<JSONObject>()
        val ownerWeekRows = mutableListOf<JSONObject>()

        priorNeeds.forEach { prior ->
            val caseName = prior.getString("case")
            val planning = requireNotNull(canonicalPlanningByCase[caseName]) { "Missing canonical C30 planning for $caseName" }
            val result = resultByCase[caseName]
            val codes = prior.getJSONArray("representedGapCodes").strings().distinct().sorted()
            codes.forEach { sourceCode ->
                val decision = planning.decisionPortfolio.movementDecisions.singleOrNull { it.sourceCoverageCode == sourceCode }
                val coverage = decision?.movementCoverage
                val target = coverage?.let { value -> planning.targetPlan.movementTargets.singleOrNull { it.movementCoverage == value } }
                val targetId = target?.targetId
                val comparison = result?.comparison
                val selectionPlan = comparison?.selectionPlan ?: selectionPlanByCase[caseName]
                val authorizationPlan = comparison?.prescriptionAuthorizationPlan ?: authorizationPlanByCase[caseName]
                val trace = targetId?.let { id -> selectionPlan?.traces?.singleOrNull { it.targetId == id } }
                val selected = targetId?.let { id -> selectionPlan?.selectedCandidates?.singleOrNull { id in it.coveredTargetIds } }
                val b6 = targetId?.let { id -> authorizationPlan?.movementAuthorizations?.singleOrNull { it.targetId == id } }
                val exactOwnerRows = selected?.let { candidate ->
                    comparison?.experimental?.items.orEmpty().filter { row ->
                        row.exerciseStableKey == candidate.stableKey && row.selectionRole == candidate.selectionRole
                    }
                }.orEmpty()
                val authorized = b6?.status in setOf(
                    StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6,
                    StimulusMovementB6Status.COVERED_BY_APPROVED_TASK_B6
                )
                val identity = JSONObject()
                    .put("case", caseName)
                    .put("priorSparseStableKey", prior.getString("stableKey"))
                    .put("priorSparseSelectionRole", prior.getString("selectionRole"))
                    .put("priorSparseWeeks", JSONArray(prior.optJSONArray("weeks")?.strings().orEmpty().sorted()))
                    .put("needSource", prior.optString("needSource", "MOVEMENT_REPRESENTATION"))
                    .put("representedGapCodes", JSONArray(codes))
                    .put("movementCoverage", coverage?.name ?: JSONObject.NULL)
                    .put("gapCode", sourceCode)
                    .put("historyRepresentation", decision?.representationState?.name ?: JSONObject.NULL)
                    .put("current28Exposure", decision?.currentExposure28d ?: JSONObject.NULL)
                    .put("prior28Exposure", decision?.priorExposure28d ?: JSONObject.NULL)
                    .put("b3Disposition", decision?.disposition?.name ?: "NO_CANONICAL_MOVEMENT_DECISION")
                    .put("b3Relevance", decision?.relevance?.name ?: JSONObject.NULL)
                    .put("b3Priority", decision?.priority?.name ?: JSONObject.NULL)
                    .put("b3ReasonCodes", JSONArray(decision?.reasonCodes.orEmpty().sorted()))
                    .put("b3Evidence", JSONArray(decision?.evidence.orEmpty().sorted()))
                    .put("b4TargetPresent", target != null)
                    .put("b4TargetId", targetId ?: JSONObject.NULL)
                    .put("b4NumericAuthority", target?.numericAuthority?.name ?: "NONE")
                    .put("b5Owner", selected?.let { JSONObject()
                        .put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    } ?: JSONObject.NULL)
                    .put("b5Status", when {
                        selected != null -> if (trace?.coveredByPreviouslySelectedStableKey != null) "REUSED_BY_CANONICAL_B5_OWNER" else "SELECTED_BY_CANONICAL_B5"
                        trace == null -> "NOT_REACHED_OR_NO_COMPARISON"
                        trace.candidatePool.isEmpty() -> "NO_ELIGIBLE_DIRECT_MOVEMENT_OWNER"
                        else -> "NO_SELECTED_B5_OWNER"
                    })
                    .put("b5CandidatePoolSize", trace?.candidatePool?.size ?: 0)
                    .put("b5ReasonCodes", JSONArray(trace?.reasonCodes.orEmpty().sorted()))
                    .put("b6Status", b6?.status?.name ?: "NOT_EVALUATED")
                    .put("b6ReasonCodes", JSONArray(b6?.reasonCodes.orEmpty().sorted()))
                    .put("b6ExistingAuthorityTargetId", b6?.existingAuthorityTargetId ?: JSONObject.NULL)
                    .put("exactB6AuthorityAvailable", authorized)
                    .put("materializedExactOwnerRows", exactOwnerRows.size)
                    .put("materialized", exactOwnerRows.isNotEmpty())
                    .put("comparisonAvailable", comparison != null)
                    .put("upstreamFailureReason", result?.upstreamFailureReason ?: JSONObject.NULL)
                    .put("upstreamFailureDetails", JSONArray(result?.upstreamFailureDetails.orEmpty()))
                    .put("b5ObservedTargetIds", JSONArray(selectionPlan?.traces.orEmpty().map { it.targetId }.filter { it.startsWith("MOVEMENT:") }.distinct().sorted()))
                    .put("b6ObservedTargetIds", JSONArray(authorizationPlan?.movementAuthorizations.orEmpty().map { it.targetId }.distinct().sorted()))
                    .put("executionDisposition", when {
                        decision?.disposition != MovementNeedDisposition.ADDRESS -> decision?.disposition?.name ?: "UNRESOLVED"
                        selected == null || b6?.status == StimulusMovementB6Status.NO_B5_MOVEMENT_OWNER -> "OWNER_SELECTION_GAP"
                        authorized && exactOwnerRows.isNotEmpty() -> "RESOLVED_EXECUTABLE"
                        authorized -> "AUTHORIZED_BUT_NOT_MATERIALIZED"
                        b6?.status == StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY -> "POLICY_UNSUPPORTED"
                        else -> "PRESCRIPTION_AUTHORITY_GAP"
                    })
                    .put("executionBlocker", when {
                        decision?.disposition != MovementNeedDisposition.ADDRESS -> JSONObject.NULL
                        selected == null || b6?.status == StimulusMovementB6Status.NO_B5_MOVEMENT_OWNER -> "EXACT_B5_OWNER_UNAVAILABLE"
                        b6?.status == StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY -> "NO_EXACT_MOVEMENT_PRESCRIPTION_AUTHORITY"
                        authorized && exactOwnerRows.isEmpty() -> "AUTHORIZED_ROW_NOT_PRESENT_IN_EXPERIMENTAL"
                        else -> JSONObject.NULL
                    })
                    .put("generationTimeMs", generationMillisByCase[caseName] ?: JSONObject.NULL)
                    .put("noDosePolicyAdded", true)
                identityRows += identity

                val weeks = prior.optJSONArray("weeks")?.strings()?.mapNotNull(String::toIntOrNull).orEmpty().ifEmpty { listOf(1, 2) }
                weeks.forEach { week ->
                    val identityKey = StimulusPrescriptionOwnerIdentity(prior.getString("stableKey"), prior.getString("selectionRole"))
                    val rowsInExperimental = result?.comparison?.experimental?.items.orEmpty().count { row ->
                        row.weekNumber == week && row.exerciseStableKey == identityKey.stableKey && row.selectionRole == identityKey.selectionRole
                    }
                    ownerWeekRows += JSONObject()
                        .put("case", caseName)
                        .put("week", week)
                        .put("originalSparseStableKey", identityKey.stableKey)
                        .put("originalSparseSelectionRole", identityKey.selectionRole)
                        .put("movementCoverage", coverage?.name ?: JSONObject.NULL)
                        .put("b3Disposition", decision?.disposition?.name ?: "NO_CANONICAL_MOVEMENT_DECISION")
                        .put("b4TargetPresent", target != null)
                        .put("b5OwnerStableKey", selected?.stableKey ?: JSONObject.NULL)
                        .put("b5OwnerSelectionRole", selected?.selectionRole ?: JSONObject.NULL)
                        .put("b6Status", b6?.status?.name ?: "NOT_EVALUATED")
                        .put("originalUnauthorizedOwnerWeekRowsAfter", rowsInExperimental)
                }
            }
        }

        val generated = records.mapNotNull { (spec, result) -> result?.let { spec.label to it } }
        val routeCounts = generated.groupingBy { it.second.routeDecision.selectedSource.name }.eachCount().toSortedMap()
        val b7ReasonCases = linkedMapOf<String, Int>()
        val b7ReasonOccurrences = linkedMapOf<String, Int>()
        val b8Counts = linkedMapOf<String, Int>()
        val provenanceCounts = linkedMapOf<String, Int>()
        generated.forEach { (_, result) ->
            val audit = result.comparison?.experimentalReadinessAudit
            audit?.reasonCodes.orEmpty().distinct().forEach { reason -> b7ReasonCases[reason] = (b7ReasonCases[reason] ?: 0) + 1 }
            audit?.reasonCodes.orEmpty().forEach { reason -> b7ReasonOccurrences[reason] = (b7ReasonOccurrences[reason] ?: 0) + 1 }
            audit?.changeAttributions.orEmpty().flatMap { it.reasonCodes }.forEach { reason ->
                if (reason in setOf("UNEXPLAINED_ADDED_IDENTITY", "UNEXPLAINED_REMOVED_IDENTITY", "UNEXPLAINED_PRESCRIPTION_CHANGE")) {
                    provenanceCounts[reason] = (provenanceCounts[reason] ?: 0) + 1
                }
            }
            result.comparison?.productionCutoverAuthority?.status?.name?.let { status -> b8Counts[status] = (b8Counts[status] ?: 0) + 1 }
        }
        val targetDispositions = identityRows.groupingBy { it.getString("b3Disposition") }.eachCount().toSortedMap()
        val executionDispositions = identityRows.groupingBy { it.getString("executionDisposition") }.eachCount().toSortedMap()
        val perCaseDispositions = identityRows.groupBy { it.getString("case") }.toSortedMap().mapValues { (_, rows) ->
            rows.groupingBy { it.getString("b3Disposition") }.eachCount().toSortedMap()
        }
        val addressRows = identityRows.filter { it.getString("b3Disposition") == MovementNeedDisposition.ADDRESS.name }
        val selectedB5 = identityRows.filter { it.optJSONObject("b5Owner") != null }.size
        val authorizedB6 = identityRows.count { it.getBoolean("exactB6AuthorityAvailable") }
        val ownerWeekUnauthorizedRows = ownerWeekRows.sumOf { it.getInt("originalUnauthorizedOwnerWeekRowsAfter") }
        val buildAccounting = JSONObject()
            .put("CONTROL", generated.sumOf { it.second.buildCounts.controlBuilds })
            .put("EXPERIMENTAL", generated.sumOf { it.second.buildCounts.experimentalBuilds })
            .put("TOTAL", generated.sumOf { it.second.buildCounts.totalBuildInvocations })
            .put("THIRD", generated.sumOf { it.second.buildCounts.thirdBuilds })
            .put("generatedCases", generated.size)
        val movementMaterialRows = generated.sumOf { (_, result) ->
            result.comparison?.experimental?.items.orEmpty().count { item ->
                item.selectionRole.startsWith("CANONICAL_STIMULUS_MOVEMENT_")
            }
        }
        val approvedTaskRows = generated.sumOf { (_, result) -> result.comparison?.experimental?.items.orEmpty().count { item ->
            item.exerciseStableKey in setOf("ex_33841b88", "ex_421ba24b", "ex_8e69fc74")
        } }
        val powerRows = generated.sumOf { (_, result) -> result.comparison?.experimental?.items.orEmpty().count { item ->
            item.selectionRole == "CANONICAL_STIMULUS_QUALITY_POWER"
        } }
        val canonicalJumpLandingTargets = canonicalPlanningByCase.values.sumOf { planning ->
            planning.targetPlan.taskTargets.count { it.task == "JUMP_LANDING" }
        }
        val generationTimes = generated.mapNotNull { (caseName, _) -> generationMillisByCase[caseName] }.sorted()
        val metricNames = plannerMetricsByCase.values.flatMap { it.keys }.toSortedSet()
        val metricTotals = JSONObject().apply { metricNames.forEach { name -> put(name, plannerMetricsByCase.values.sumOf { it[name] ?: 0 }) } }
        val movementScanCount = generated.sumOf { (caseName, result) ->
            (result.comparison?.selectionPlan ?: selectionPlanByCase[caseName])?.traces.orEmpty()
                .filter { it.targetId.startsWith("MOVEMENT:") }.sumOf { it.candidatePool.size }
        }
        val b6MovementLookups = generated.sumOf { (caseName, result) ->
            (result.comparison?.prescriptionAuthorizationPlan ?: authorizationPlanByCase[caseName])?.movementAuthorizations?.size ?: 0
        }
        val repeatedB6MovementLookups = generated.sumOf { (caseName, result) ->
            val rows = result.comparison?.prescriptionAuthorizationPlan?.movementAuthorizations
                ?: authorizationPlanByCase[caseName]?.movementAuthorizations.orEmpty()
            rows.size - rows.map { it.targetId }.distinct().size
        }
        val c20Summary = JSONObject(c20Census).getJSONObject("summary")
        val summary = JSONObject()
            .put("c29MovementIdentities", priorNeeds.size)
            .put("movementDispositionRows", identityRows.size)
            .put("ownerWeekRows", ownerWeekRows.size)
            .put("dispositions", JSONObject().apply { targetDispositions.forEach { (key, value) -> put(key, value) } })
            .put("dispositionsByCase", JSONObject().apply {
                perCaseDispositions.forEach { (case, values) -> put(case, JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }) }
            })
            .put("executionDispositions", JSONObject().apply { executionDispositions.forEach { (key, value) -> put(key, value) } })
            .put("b4MovementTargets", identityRows.count { it.getBoolean("b4TargetPresent") })
            .put("b5MovementOwnersSelected", selectedB5)
            .put("b5AddressTargetsWithoutOwner", addressRows.count { it.optJSONObject("b5Owner") == null })
            .put("b6MovementTargetsCoveredByExactExistingAuthority", authorizedB6)
            .put("movementTargetsMaterializedByExistingAuthorizedRow", identityRows.count { it.getBoolean("materialized") })
            .put("addressExecutionBlockedNoMovementDoseAuthority", identityRows.count {
                it.getString("executionDisposition") == "POLICY_UNSUPPORTED"
            })
            .put("unauthorizedSparseOwnerWeekRowsAfter", ownerWeekUnauthorizedRows)
            .put("movementRoleMaterialRows", movementMaterialRows)
            .put("approvedTaskOwnerRows", approvedTaskRows)
            .put("powerMaterialRows", powerRows)
            .put("canonicalJumpLandingTaskTargets", canonicalJumpLandingTargets)
            .put("approvedJumpLandingProtocols", 0)
            .put("jumpLandingMaterialRows", generated.sumOf { (_, result) -> result.comparison?.experimental?.items.orEmpty().count { it.selectionRole.contains("JUMP_LANDING") } })
            .put("routes", JSONObject().apply { routeCounts.forEach { (key, value) -> put(key, value) } })
            .put("b7ReasonCases", JSONObject().apply { b7ReasonCases.forEach { (key, value) -> put(key, value) } })
            .put("b7ReasonOccurrences", JSONObject().apply { b7ReasonOccurrences.forEach { (key, value) -> put(key, value) } })
            .put("unexplainedProvenanceAttributions", JSONObject().apply { provenanceCounts.forEach { (key, value) -> put(key, value) } })
            .put("b8Statuses", JSONObject().apply { b8Counts.forEach { (key, value) -> put(key, value) } })
            .put("buildAccounting", buildAccounting)
            .put("c20", JSONObject()
                .put("HARD_VALID", c20Summary.optInt("HARD_VALID"))
                .put("HARD_INVALID", c20Summary.optInt("HARD_INVALID"))
                .put("UNRESOLVED", c20Summary.optInt("UNRESOLVED"))
                .put("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER", c20Summary.optInt("NOT_EVALUATED_NO_CURRENT_AUTHORIZED_OWNER"))
                .put("hardInvalidForcedPreserved", c20Summary.optInt("hardInvalidRowsForcedPreserved"))
                .put("unresolvedForcedPreserved", c20Summary.optInt("unresolvedRowsForcedPreserved")))
            .put("generationTimeTotalMs", generationTimes.sum())
            .put("generationTimeMeanMs", generationTimes.average().takeIf { generationTimes.isNotEmpty() } ?: 0.0)
            .put("generationTimeMedianMs", if (generationTimes.isEmpty()) 0.0 else if (generationTimes.size % 2 == 1) generationTimes[generationTimes.size / 2].toDouble() else
                (generationTimes[generationTimes.size / 2 - 1] + generationTimes[generationTimes.size / 2]) / 2.0)
            .put("generationTimeMaxMs", generationTimes.maxOrNull() ?: 0L)
            .put("canonicalB3MovementEvaluations", identityRows.size)
            .put("canonicalB4MovementTargetBuilds", identityRows.count { it.getBoolean("b4TargetPresent") })
            .put("b5MovementCandidatePoolRows", movementScanCount)
            .put("b6MovementAuthorityLookups", b6MovementLookups)
            .put("repeatedIdenticalB6MovementLookups", repeatedB6MovementLookups)
            .put("plannerPlacementMetrics", metricTotals)
            .put("thirdPlannerBuilds", buildAccounting.getInt("THIRD"))
        return JSONObject()
            .put("phase", "C30")
            .put("title", "Movement need disposition and canonical target admission")
            .put("startSha", startSha)
            .put("productionBehaviorChangedOutsideB1ToB6MovementAdmission", false)
            .put("summary", summary)
            .put("movementNeedIdentities", JSONArray(identityRows.sortedWith(compareBy({ it.getString("case") }, { it.optString("movementCoverage") }))))
            .put("ownerWeekRows", JSONArray(ownerWeekRows.sortedWith(compareBy({ it.getString("case") }, { it.getInt("week") }, { it.optString("originalSparseStableKey") }))))
            .put("performance", JSONObject()
                .put("generationCallIsObservationOnly", true)
                .put("generationCalls", generationTimes.size)
                .put("generationTimeBefore", JSONObject()
                    .put("source", "C29 audited 22-case Room/service observation")
                    .put("totalMs", 3584)
                    .put("meanMs", 162.9)
                    .put("medianMs", 144.5)
                    .put("maxMs", 369))
                .put("generationTimeAfter", JSONObject()
                    .put("totalMs", summary.getLong("generationTimeTotalMs"))
                    .put("meanMs", summary.getDouble("generationTimeMeanMs"))
                    .put("medianMs", summary.getDouble("generationTimeMedianMs"))
                    .put("maxMs", summary.getLong("generationTimeMaxMs")))
                .put("candidateScanPlacement", "B5 only, downstream from the O(Q+T+M) B3 pass")
                .put("identicalB6MovementLookupsRepeated", repeatedB6MovementLookups))
            .toString(2)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
}
