package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** C29 audit-only projection over the real C28 service corpus and its retained gap evidence. */
internal object C29UnresolvedDispositionCensus {
    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        preparedContexts: Map<String, PreparedCanonicalGenerationContext>,
        c28Census: String,
        plannerMetricsByCase: Map<String, Map<String, Int>>,
        startSha: String
    ): String {
        val c28 = JSONObject(c28Census)
        val byCase = records.associateBy { it.first.label }
        val ownerWeeks = c28.getJSONArray("sparseOwnerWeeks").let { array ->
            (0 until array.length()).map(array::getJSONObject)
        }
        val ownerWeeksByIdentity = ownerWeeks.groupBy {
            Triple(it.getString("case"), it.getString("stableKey"), it.getString("selectionRole"))
        }
        val identities = c28.getJSONArray("sparseIdentities").let { array ->
            (0 until array.length()).map(array::getJSONObject).sortedWith(compareBy(
                { it.getString("case") }, { it.getString("stableKey") }, { it.getString("selectionRole") }
            ))
        }
        val needRows = mutableListOf<JSONObject>()
        val ownerWeekRows = mutableListOf<JSONObject>()

        identities.forEach { sparse ->
            val caseName = sparse.getString("case")
            val stableKey = sparse.getString("stableKey")
            val selectionRole = sparse.getString("selectionRole")
            val identity = StimulusPrescriptionOwnerIdentity(stableKey, selectionRole)
            val context = requireNotNull(preparedContexts[caseName]) { "Missing C29 inputs for $caseName" }
            val planning = ((context.canonicalPlanningOutcome as? CanonicalPlanningOutcome.Success)?.result)
            val result = requireNotNull(byCase[caseName]?.second) { "Missing C29 generation result for $caseName" }
            val gapCodes = sparse.getJSONArray("needGapCodes").let { values ->
                (0 until values.length()).map { values.getString(it) }.sorted()
            }
            val representationRows = gapCodes.mapNotNull { code ->
                context.state.movementRepresentations.firstOrNull { it.movementCoverage == code }
            }
            val gapRows = gapCodes.mapNotNull { code -> context.gaps.firstOrNull { it.code == code } }
            val movementData = JSONArray(representationRows.map { row -> JSONObject()
                .put("coverage", row.movementCoverage)
                .put("basePriority", row.basePriority.name)
                .put("representationState", row.representationState.name)
                .put("currentExposure28d", row.currentExposure28d)
                .put("priorExposure28d", row.priorExposure28d)
                .put("currentActiveBins", row.currentActiveBins)
                .put("currentShare", row.currentShare ?: JSONObject.NULL)
                .put("priorShare", row.priorShare ?: JSONObject.NULL)
                .put("peerRepresentationRatio", row.peerRepresentationRatio ?: JSONObject.NULL)
                .put("personalRetentionRatio", row.personalRetentionRatio ?: JSONObject.NULL)
                .put("evidenceConfidence", row.evidenceConfidence.name)
                .put("reasonCodes", JSONArray(row.reasonCodes.sorted()))
            })
            val movementGapData = JSONArray(gapRows.map { gap -> JSONObject()
                .put("code", gap.code)
                .put("priority", gap.priority)
                .put("sourceType", gap.sourceType)
                .put("reasonCodes", JSONArray(gap.reasonCodes.sorted()))
                .put("contributesTransitionPressure", gap.contributesTransitionPressure)
                .put("currentExposure", gap.currentExposure ?: JSONObject.NULL)
                .put("priorExposure", gap.priorExposure ?: JSONObject.NULL)
            })
            val canonicalQualityTargets = planning?.targetPlan?.qualityTargets.orEmpty().map {
                "QUALITY:${it.quality.name}"
            }.sorted()
            val canonicalTaskTargets = planning?.targetPlan?.taskTargets.orEmpty().map {
                "TASK:${it.task}"
            }.sorted()
            val b1QualityNeeds = planning?.athleteStimulusNeedProfile?.qualityNeeds.orEmpty()
            val b1TaskNeeds = planning?.athleteStimulusNeedProfile?.sportTaskNeeds.orEmpty()
            val b3QualityDecisions = planning?.decisionPortfolio?.qualityDecisions.orEmpty()
            val b3TaskDecisions = planning?.decisionPortfolio?.taskDecisions.orEmpty()
            val attemptedOwners = sparse.getJSONObject("authorityResolution").getJSONArray("attemptedOwners")
            val alternatives = JSONArray((0 until attemptedOwners.length()).map { index ->
                val attempted = attemptedOwners.getJSONObject(index)
                JSONObject()
                    .put("stableKey", attempted.getString("stableKey"))
                    .put("selectionRole", attempted.getString("selectionRole"))
                    .put("exactAuthorityResolution", "NO_EXACT_EXECUTABLE_AUTHORITY")
            })
            val weekRows = ownerWeeksByIdentity[Triple(caseName, stableKey, selectionRole)].orEmpty()
                .sortedBy { it.getInt("week") }
            val target = JSONArray(canonicalQualityTargets + canonicalTaskTargets)
            val b1 = JSONObject()
                .put("profileAvailable", planning != null)
                .put("qualityNeedCount", b1QualityNeeds.size)
                .put("taskNeedCount", b1TaskNeeds.size)
                .put("qualityNeeds", JSONArray(b1QualityNeeds.map { need -> JSONObject()
                    .put("quality", need.quality.name)
                    .put("relevance", need.relevance.name)
                    .put("decision", need.decision.name)
                    .put("reasonCodes", JSONArray(need.reasonCodes.sorted()))
                }))
                .put("taskNeeds", JSONArray(b1TaskNeeds.map { need -> JSONObject()
                    .put("task", need.task)
                    .put("relevance", need.relevance.name)
                    .put("decision", need.decision.name)
                    .put("reasonCodes", JSONArray(need.reasonCodes.sorted()))
                }))
                .put("hasMovementCoverageNeed", false)
                .put("reason", "B1 canonical profile models trainable quality and badminton task needs; movement representation remains a separate gap signal")
            val b2 = JSONObject()
                .put("canonicalQualityDoseHistoryAvailable", planning?.qualityDoseHistory?.available ?: false)
                .put("canonicalQualityDoseHistoryAuthority", planning?.qualityDoseHistory?.prescriptionAuthority ?: false)
                .put("canonicalQualityEvidenceCount", planning?.qualityDoseHistory?.bands?.size ?: 0)
                .put("movementHistoryEvidence", movementData)
                .put("movementDoseHistoryInB2", false)
                .put("reason", "Movement exposure counts are available from the state representation; B2 dose history is quality-keyed and does not create a movement target")
            val b3 = JSONObject()
                .put("qualityDecisionCount", b3QualityDecisions.size)
                .put("taskDecisionCount", b3TaskDecisions.size)
                .put("hasMovementCoverageDecision", false)
                .put("unresolvedCanonicalDecisions", JSONArray(planning?.decisionPortfolio?.unresolved.orEmpty().sorted()))
            val b4 = JSONObject()
                .put("targetExists", false)
                .put("targetId", JSONObject.NULL)
                .put("numericAuthority", "NONE_NO_CANONICAL_MOVEMENT_TARGET")
                .put("qualityTargetIds", JSONArray(canonicalQualityTargets))
                .put("taskTargetIds", JSONArray(canonicalTaskTargets))
                .put("reason", "TargetStimulusPlan has qualityTargets and taskTargets only; it has no MovementCoverage target form")
            val b5 = JSONObject()
                .put("canonicalB5Entered", false)
                .put("selectedOwner", JSONObject.NULL)
                .put("candidatePoolSource", "C28 MaterialDemandResolver side path")
                .put("alternativeOwnerCount", attemptedOwners.length())
                .put("alternatives", alternatives)
                .put("reason", "No B4 target means the canonical B5 selector has no target for this coverage code")
            val b6 = JSONObject()
                .put("decisionExists", false)
                .put("status", "NOT_EVALUATED_NO_CANONICAL_B5_OWNER")
                .put("exactPrescriptionAuthority", "NONE")
                .put("materialDemandRecoveryStatus", sparse.getJSONObject("authorityResolution").getString("status"))
                .put("materialDemandRecoveryReason", sparse.getJSONObject("authorityResolution").getString("reason"))
                .put("materialDemandRecoveryReturnTarget", sparse.getJSONObject("authorityResolution").getString("returnTarget"))
                .put("prescriptionSource", "NONE")
                .put("frequencyAuthority", "NONE_NOT_APPLICABLE_WITHOUT_B4_TARGET")
            val resultData = JSONObject()
                .put("route", result.routeDecision.selectedSource.name)
                .put("comparisonAvailable", result.comparison != null)
                .put("b7Status", result.comparison?.experimentalReadinessAudit?.status?.name ?: "NOT_EVALUATED_NO_COMPARISON")
                .put("b7Reasons", JSONArray(result.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("b8Status", result.comparison?.productionCutoverAuthority?.status?.name ?: "NOT_EVALUATED_NO_COMPARISON")
                .put("b9Reason", result.routeDecision.reasonCodes.sorted().joinToString(","))
            val need = JSONObject()
                .put("case", caseName)
                .put("stableKey", stableKey)
                .put("selectionRole", selectionRole)
                .put("exercise", sparse.optString("exercise"))
                .put("ownerRoleType", sparse.optString("ownerRoleType"))
                .put("weeks", JSONArray(weekRows.map { it.getInt("week") }))
                .put("needSource", "MOVEMENT_REPRESENTATION_GAP")
                .put("needCodes", JSONArray(gapCodes))
                .put("needEvidence", movementData)
                .put("candidateOrigin", sparse.optJSONObject("candidateOrigin") ?: JSONObject.NULL)
                .put("materialDemandOrigin", JSONArray(gapRows.map { JSONObject()
                    .put("sourceType", it.sourceType)
                    .put("gapCode", it.code)
                    .put("priority", it.priority)
                    .put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    .put("contributesTransitionPressure", it.contributesTransitionPressure)
                }))
                .put("representedGapCodes", JSONArray(gapCodes))
                .put("b1", b1)
                .put("b2", b2)
                .put("b3", b3)
                .put("b4", b4)
                .put("b5", b5)
                .put("b6", b6)
                .put("b1Status", "QUALITY_AND_TASK_NEEDS_ONLY")
                .put("b2Evidence", b2)
                .put("b3Decision", "NO_MOVEMENT_COVERAGE_DECISION")
                .put("b4TargetStatus", "NO_CANONICAL_MOVEMENT_TARGET")
                .put("b4TargetId", JSONObject.NULL)
                .put("b4Reason", "MovementCoverage is not a target type in StimulusTargetPlan")
                .put("b5OwnerStatus", "NOT_ENTERED_NO_B4_TARGET")
                .put("b5Candidates", alternatives)
                .put("b5Reason", "Side-path candidates were scanned for exact authority but are not canonical B5 selections")
                .put("b6Status", "NOT_EVALUATED_NO_CANONICAL_B5_OWNER")
                .put("b6Reason", sparse.getJSONObject("authorityResolution").getString("reason"))
                .put("candidateDisposition", "RESOLVED_DEFERRED_NO_EXACT_AUTHORITY")
                .put("candidateDispositionReason", sparse.optString("candidateDeferredReason"))
                .put("classifiedNeedDisposition", "TRUE_UNRESOLVED")
                .put("classifiedDisposition", "TRUE_UNRESOLVED")
                .put("currentNeedDisposition", "UNRESOLVED_NO_AUTHORITY")
                .put("currentDisposition", "UNRESOLVED_NO_AUTHORITY")
                .put("primaryRootCauseCategory", "TARGET_GENERATION_GAP")
                .put("rootCauseCategory", "TARGET_GENERATION_GAP")
                .put("secondaryGateEffects", JSONArray(listOf("CANONICAL_B5_NOT_ENTERED", "B6_NOT_EVALUATED", "NO_EXECUTABLE_PRESCRIPTION_AUTHORITY")))
                .put("possibleReturnStage", "MOVEMENT_NEED_DISPOSITION_AND_B4_TARGET_ADMISSION")
                .put("returnStage", "MOVEMENT_NEED_DISPOSITION_AND_B4_TARGET_ADMISSION")
                .put("reResolutionScope", "LOCAL_TARGET_REEVALUATION_THEN_B5_B6")
                .put("localOrGlobal", "LOCAL")
                .put("globalReplanNeeded", false)
                .put("userInputNeeded", false)
                .put("policyUnsupportedForExecution", true)
                .put("legitimatePlannerUnresolved", true)
                .put("estimatedRecomputeScope", "Use retained movement representation and gap; resolve its block disposition/target locally; if a canonical target is admitted, rerun B4 then B5 and B6 only")
                .put("b1ToB6", JSONObject().put("b1", b1).put("b2", b2).put("b3", b3).put("b4", b4).put("b5", b5).put("b6", b6))
                .put("generationResult", resultData)
                .put("candidateAlternativeAuthorityResolution", alternatives)
            needRows += need

            weekRows.forEach { oldWeek ->
                ownerWeekRows += JSONObject()
                    .put("case", caseName)
                    .put("week", oldWeek.getInt("week"))
                    .put("stableKey", stableKey)
                    .put("selectionRole", selectionRole)
                    .put("needCodes", JSONArray(gapCodes))
                    .put("beforeExecutableRow", oldWeek.optBoolean("beforeExecutableRow"))
                    .put("beforePrescription", oldWeek.optJSONObject("beforePrescription") ?: JSONObject.NULL)
                    .put("experimentalRowsAfter", oldWeek.optInt("experimentalRowsAfter"))
                    .put("materialDeltaAfter", oldWeek.optBoolean("materialDeltaAfter"))
                    .put("currentDisposition", "UNRESOLVED_NO_EXECUTABLE_AUTHORITY")
                    .put("classifiedDisposition", "TRUE_UNRESOLVED")
            }
        }

        val dispositionCounts = JSONObject()
            .put("TRUE_UNRESOLVED", needRows.count { it.getString("classifiedNeedDisposition") == "TRUE_UNRESOLVED" })
            .put("USER_INPUT_REQUIRED", needRows.count { it.getBoolean("userInputNeeded") })
            .put("RESOLVED_DEFERRED", 0)
            .put("RESOLVED_EXCLUDED_OR_INFEASIBLE", 0)
            .put("POLICY_UNSUPPORTED_AS_TERMINAL_NEED_DISPOSITION", 0)
            .put("POLICY_UNSUPPORTED_FOR_EXECUTION_CANDIDATES", needRows.count { it.getBoolean("policyUnsupportedForExecution") })
        val rootCounts = JSONObject().put("TARGET_GENERATION_GAP", needRows.size)
        val coverageCodeCounts = JSONObject()
        identities.flatMap { row ->
            val codeArray = row.getJSONArray("needGapCodes")
            (0 until codeArray.length()).map { codeArray.getString(it) }
        }.groupingBy { it }.eachCount().toSortedMap().forEach { (code, count) ->
            coverageCodeCounts.put(code, count)
        }
        val representationStateCounts = JSONObject()
        needRows.flatMap { row ->
            val evidence = row.getJSONArray("needEvidence")
            (0 until evidence.length()).map { evidence.getJSONObject(it).getString("representationState") }
        }.groupingBy { it }.eachCount().toSortedMap().forEach { (state, count) ->
            representationStateCounts.put(state, count)
        }
        val authorityProbeKeys = identities.flatMap { row ->
            val codes = row.getJSONArray("needGapCodes").let { values ->
                (0 until values.length()).map { values.getString(it) }.sorted().joinToString("+")
            }
            val attempted = row.getJSONObject("authorityResolution").getJSONArray("attemptedOwners")
            (0 until attempted.length()).map { index ->
                val owner = attempted.getJSONObject(index)
                listOf(row.getString("case"), codes, owner.getString("stableKey"),
                    owner.getString("selectionRole"), "NO_EXACT_AUTHORITY").joinToString("|")
            }
        }
        val repeatedAuthorityProbeKeys = authorityProbeKeys.size - authorityProbeKeys.distinct().size
        val dispositionSummary = JSONObject()
            .put("unresolvedNeedIdentities", needRows.size)
            .put("ownerWeekRows", ownerWeekRows.size)
            .put("rootCauseCounts", rootCounts)
            .put("coverageCodeCounts", coverageCodeCounts)
            .put("movementRepresentationStateCounts", representationStateCounts)
            .put("classifiedNeedDispositionCounts", dispositionCounts)
            .put("canonicalB4MovementTargets", 0)
            .put("canonicalB5SelectedMovementOwners", 0)
            .put("canonicalB6MovementOwnerDecisions", 0)
            .put("exactAuthorityCandidateProbes", identities.sumOf { it.getJSONObject("authorityResolution").getJSONArray("attemptedOwners").length() })
            .put("userInputRequired", 0)
            .put("currentMovementCandidateExecutionPolicyUnsupported", needRows.size)
            .put("globalProgramRepairRequired", 0)

        val stageCounts = JSONObject()
            .put("productionGeneratedCases", records.count { it.second != null })
            .put("canonicalB1Evaluations", records.count { it.second != null })
            .put("canonicalB2LedgerDoseHistoryComputations", records.count { it.second != null })
            .put("legacyQualityDoseHistoryComputationsForCanonicalInput", records.count { it.second != null })
            .put("canonicalB3DecisionPortfolioComputations", records.count { it.second != null })
            .put("canonicalB4TargetPlanComputations", records.count { it.second != null })
            .put("canonicalB5SelectionPasses", records.count { it.second != null })
            .put("canonicalB6AuthorizationPlanBuilds", records.count { it.second != null })
            .put("exactB6OwnerLookupsForMovementCoverageTargets", 0)
            .put("materialDemandExactOwnerAuthorityProbes", dispositionSummary.getInt("exactAuthorityCandidateProbes"))
            .put("uniqueExactOwnerAuthorityProbeKeys", authorityProbeKeys.distinct().size)
            .put("repeatedExactOwnerAuthorityProbeKeys", repeatedAuthorityProbeKeys)
            .put("prescriptionResolverCallsForNoExactAuthorityCandidates", 0)
            .put("materialDemandResolverPassesPerGeneratedRequest", 1)
            .put("globalRepairLoopPassCountInstrumented", false)
        val plannerMetricCases = records.mapNotNull { (spec, result) ->
            result?.let { spec.label }
        }.sorted().map { caseName -> JSONObject()
            .put("case", caseName)
            .put("plannerPlacementMetrics", plannerMetricsByCase[caseName]?.let(::JSONObject) ?: JSONObject.NULL)
            .put("metricsAvailable", !plannerMetricsByCase[caseName].isNullOrEmpty())
        }
        val metricMaps = plannerMetricsByCase.values
        val metricNames = metricMaps.flatMap { it.keys }.toSortedSet()
        val metricTotals = JSONObject().apply {
            metricNames.forEach { metric -> put(metric, metricMaps.sumOf { it[metric] ?: 0 }) }
        }
        val invalidation = JSONArray(listOf(
            JSONObject().put("changedInput", "B2 history").put("recompute", JSONArray(listOf("B3", "B4", "B5", "B6"))).put("retain", JSONArray(listOf("B1 if source need evidence unchanged", "snapshot-derived representations if unchanged"))),
            JSONObject().put("changedInput", "B3 decision").put("recompute", JSONArray(listOf("B4", "B5", "B6"))).put("retain", JSONArray(listOf("B1", "B2"))),
            JSONObject().put("changedInput", "B4 target").put("recompute", JSONArray(listOf("B5", "B6"))).put("retain", JSONArray(listOf("B1", "B2", "B3"))),
            JSONObject().put("changedInput", "B5 owner identity").put("recompute", JSONArray(listOf("exact owner B6 resolution"))).put("retain", JSONArray(listOf("B1", "B2", "B3", "B4", "unrelated owner B6 results"))),
            JSONObject().put("changedInput", "B6 prescription/frequency").put("recompute", JSONArray(listOf("allocation", "placement", "capacity", "tissue/OFI validation", "materialization", "B7", "B8"))).put("retain", JSONArray(listOf("B1", "B2", "B3", "B4", "B5"))),
            JSONObject().put("changedInput", "placement/order").put("recompute", JSONArray(listOf("B7", "B8", "B9"))).put("retain", JSONArray(listOf("B1", "B2", "B3", "B4", "B5", "B6")))
        ))
        val json = JSONObject()
            .put("phase", "C29")
            .put("title", "Unresolved planning disposition and efficient re-resolution audit")
            .put("startSha", startSha)
            .put("sourceC28StartSha", c28.optString("startSha"))
            .put("productionBehaviorChanged", false)
            .put("summary", dispositionSummary)
            .put("stageEvaluationCounts", stageCounts)
            .put("dependencyInvalidationMap", invalidation)
            .put("performance", JSONObject()
                .put("timingIsObservationalOnly", true)
                .put("timingObservationStoredSeparately", "build/reports/c29-generation-time-observation.json")
                .put("placementMetricsAvailableCases", plannerMetricCases.count { it.getBoolean("metricsAvailable") })
                .put("placementMetricsUnavailableCases", plannerMetricCases.count { !it.getBoolean("metricsAvailable") })
                .put("aggregatePlacementMetrics", metricTotals)
                .put("placementMetricsByCase", JSONArray(plannerMetricCases))
                .put("placementMetricNames", JSONArray(PlannerPerformanceMetrics().asMap().keys.sorted()))
                .put("repeatedExactAuthorityLookupKeys", repeatedAuthorityProbeKeys)
                .put("repeatedOwnerWeekExpansionLookups", 0)
                .put("memoization", JSONObject()
                    .put("scope", "generation-scoped")
                    .put("cachedComputations", JSONArray(listOf("prescription planning", "day projection", "week tissue projection")))
                    .put("notCached", JSONArray(listOf("B1-B6 analysis objects", "MaterialDemandResolver scans", "material-demand authority probes")))
                    .put("metrics", "placement day/tissue cache hits and misses are instrumented; B1-B6 and resolver call counts are source-derived stage invocations"))
                .put("auditObserverExtraWork", "The coverage audit separately calls buildCanonicalStimulusPlanningForPrepared once per generated case to retain canonical B1-B4 evidence, then the C28 audit renderer runs resolver-only candidate checks for each sparse identity plus the isolated posterior case. These observer-only calls are outside production generation and the recorded generation wall clock."))
            .put("needs", JSONArray(needRows))
            .put("ownerWeekRows", JSONArray(ownerWeekRows))
        return json.toString(2)
    }
}
