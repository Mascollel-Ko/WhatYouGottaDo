package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic audit-only census for the six canonical badminton task targets. */
internal object C22BadmintonTaskAuthorityCensus {
    private val tasks = listOf(
        "ACCELERATION", "DECELERATION", "FOOTWORK", "JUMP_LANDING", "LUNGE_REACH", "REACTION"
    )

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult? >>,
        planningByCase: Map<String, CanonicalStimulusPlanningResult>,
        contextByCase: Map<String, PreparedCanonicalGenerationContext>,
        metadataRepository: CanonicalExerciseMetadataRepository,
        preC22CMaterializedTaskRows: JSONArray? = null
    ): String {
        val relationCatalog = metadataRepository.badmintonObjectiveCatalog()
        val caseRows = JSONArray()
        val targetRows = mutableListOf<JSONObject>()
        val ownerRows = mutableListOf<JSONObject>()
        val materialRows = mutableListOf<JSONObject>()
        val aggregate = linkedMapOf(
            "generatedCases" to 0,
            "taskTargetsTotal" to 0,
            "directionOnlyTaskTargets" to 0,
            "exactB5TaskOwnerRows" to 0,
            "uniqueTaskOwners" to 0,
            "directTaskRelations" to 0,
            "supportiveTaskRelations" to 0,
            "reviewedGuideMatches" to 0,
            "reviewedGuideMismatches" to 0,
            "personalTaskAuthorities" to 0,
            "reviewedStarterAuthorities" to 0,
            "fullyEncodedTaskAuthorities" to 0,
            "materializedTaskRowsBefore" to 0,
            "materializedTaskRowsAfter" to 0,
            "blockedPerSide" to 0,
            "blockedRange" to 0,
            "blockedFrequency" to 0,
            "blockedTransferLevel" to 0,
            "blockedCategoryMismatch" to 0,
            "b7EligibleTaskCases" to 0,
            "b8AuthorizedTaskCases" to 0,
            "taskRoutedCases" to 0
        )
        val uniqueOwners = linkedSetOf<String>()

        records.sortedBy { it.first.label }.forEach { (spec, result) ->
            val case = JSONObject().put("case", spec.label)
            if (result == null) {
                case.put("preflight", "REJECTED_NO_CONFIRMED_HISTORY").put("taskTargets", JSONArray())
                caseRows.put(case)
                return@forEach
            }
            aggregate["generatedCases"] = aggregate.getValue("generatedCases") + 1
            val comparison = result.comparison
            requireNotNull(comparison) { "${spec.label}: production comparison missing" }
            val planning = requireNotNull(planningByCase[spec.label]) { "${spec.label}: B1-B4 planning missing" }
            val context = requireNotNull(contextByCase[spec.label]) { "${spec.label}: prepared source context missing" }
            val taskTargets = comparison.targetPlan.taskTargets.associateBy { it.task }
            val taskNeeds = planning.athleteStimulusNeedProfile.sportTaskNeeds.associateBy { it.task }
            val taskDecisions = planning.decisionPortfolio.taskDecisions.associateBy { it.task }
            val selectedTaskOwners = comparison.selectionPlan.selectedCandidates
                .filter { candidate -> candidate.coveredTargetIds.any { it.startsWith("TASK:") } }
                .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            if (selectedTaskOwners.isNotEmpty() && comparison.experimentalReadinessAudit?.status ==
                StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW) {
                aggregate["b7EligibleTaskCases"] = aggregate.getValue("b7EligibleTaskCases") + 1
            }

            val caseTargetRows = JSONArray()
            tasks.forEach { task ->
                val target = taskTargets[task]
                val need = taskNeeds[task]
                val decision = taskDecisions[task]
                val owners = selectedTaskOwners.filter { "TASK:$task" in it.coveredTargetIds }
                if (target != null) {
                    aggregate["taskTargetsTotal"] = aggregate.getValue("taskTargetsTotal") + 1
                    if (target.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) {
                        aggregate["directionOnlyTaskTargets"] = aggregate.getValue("directionOnlyTaskTargets") + 1
                        aggregate["blockedFrequency"] = aggregate.getValue("blockedFrequency") + 1
                    }
                }
                val row = JSONObject()
                    .put("case", spec.label)
                    .put("task", task)
                    .put("need", need?.let { JSONObject()
                        .put("relevance", it.relevance.name).put("decision", it.decision.name)
                        .put("confidence", it.confidence.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    } ?: JSONObject.NULL)
                    .put("b3Decision", decision?.let { JSONObject()
                        .put("strategy", it.strategy.name).put("priority", it.priority.name)
                        .put("needDecision", it.needDecision.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    } ?: JSONObject.NULL)
                    .put("b4Target", target?.let { JSONObject()
                        .put("strategy", it.strategy.name).put("numericAuthority", it.numericAuthority.name)
                        .put("weeklyDirectUnits", it.weeklyDirectUnitsTarget?.let(::rangeJson))
                        .put("weeklyDirectSessions", it.weeklyDirectSessionsTarget?.let(::rangeJson))
                        .put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    } ?: JSONObject.NULL)
                    .put("b5SelectedOwners", JSONArray(owners.map { candidate ->
                        ownerTaskJson(spec.label, task, candidate, context, relationCatalog, comparison)
                    }))
                    .put("taskB6Authority", "NO_TASK_SPECIFIC_B6_AUTHORITY_MODEL")
                    .put("taskOutcome", comparison.experimentalReadinessAudit?.targetOutcomes
                        ?.firstOrNull { it.targetId == "TASK:$task" }?.let { outcome -> JSONObject()
                            .put("status", outcome.status.name).put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
                        } ?: JSONObject.NULL)
                caseTargetRows.put(row)
                targetRows += row
            }

            selectedTaskOwners.forEach { candidate ->
                aggregate["exactB5TaskOwnerRows"] = aggregate.getValue("exactB5TaskOwnerRows") + 1
                uniqueOwners += "${candidate.stableKey}#${candidate.selectionRole}"
                candidate.coveredTargetIds.filter { it.startsWith("TASK:") }.sorted().forEach { targetId ->
                    val task = targetId.removePrefix("TASK:")
                    val relation = relationCatalog.relations(candidate.stableKey)
                        .firstOrNull { it.objective.name == task }
                    if (relation?.transferLevel != BadmintonObjectiveTransferLevel.DIRECT) {
                        aggregate["blockedTransferLevel"] = aggregate.getValue("blockedTransferLevel") + 1
                    }
                    when (relation?.transferLevel) {
                        BadmintonObjectiveTransferLevel.DIRECT -> aggregate["directTaskRelations"] = aggregate.getValue("directTaskRelations") + 1
                        null, BadmintonObjectiveTransferLevel.NONE -> Unit
                        BadmintonObjectiveTransferLevel.SUPPORTIVE -> aggregate["supportiveTaskRelations"] = aggregate.getValue("supportiveTaskRelations") + 1
                        BadmintonObjectiveTransferLevel.GENERAL,
                        BadmintonObjectiveTransferLevel.LOW -> Unit
                    }
                    val reviewedCategory = reviewedCategory(candidate.stableKey)
                    if (reviewedCategory != null) {
                        // No approved category-to-task prescription map exists. Name equality is only a label coincidence.
                        aggregate["reviewedGuideMismatches"] = aggregate.getValue("reviewedGuideMismatches") + 1
                        if (reviewedCategory.name != task) aggregate["blockedCategoryMismatch"] = aggregate.getValue("blockedCategoryMismatch") + 1
                        val guide = RecordBasedReviewedPolicy.badminton(reviewedCategory)
                        if (guide.text.contains("/side", ignoreCase = true)) {
                            aggregate["blockedPerSide"] = aggregate.getValue("blockedPerSide") + 1
                        }
                        if (Regex("\\b\\d+\\s*[-–]\\s*\\d+\\b").containsMatchIn(guide.text)) {
                            aggregate["blockedRange"] = aggregate.getValue("blockedRange") + 1
                        }
                    }
                    ownerRows += ownerTaskJson(spec.label, task, candidate, context, relationCatalog, comparison)
                }
            }

            val selectedIdentities = selectedTaskOwners.mapTo(linkedSetOf()) {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
            }
            comparison.experimental.items
                .filter { it.selectionRole.startsWith("CANONICAL_STIMULUS_TASK_") }
                // C22 measured whether its legacy fallbacks survived the exact B6 filter. C24
                // governed task-protocol rows are new authority and must not be mislabeled as
                // restored C22 fallback material.
                .filter { it.taskProtocolSemanticsJson.isNullOrBlank() }
                .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }, { it.selectionRole }))
                .forEach { item ->
                    aggregate["materializedTaskRowsAfter"] = aggregate.getValue("materializedTaskRowsAfter") + 1
                    val resolver = PerformancePrescriptionResolver.resolve(context.snapshot, item.exerciseStableKey)
                    materialRows += JSONObject()
                        .put("case", spec.label).put("week", item.weekNumber).put("day", item.dayOfWeek)
                        .put("order", item.orderIndex).put("stableKey", item.exerciseStableKey)
                        .put("selectionRole", item.selectionRole)
                        .put("setCount", item.setCount).put("reps", item.reps).put("seconds", item.seconds)
                        .put("sets", JSONArray(item.setPrescriptions.map { set -> JSONObject()
                            .put("setIndex", set.setIndex).put("reps", set.reps).put("seconds", set.seconds)
                            .put("weightKg", set.weightKg).put("targetRpe", set.targetRpeMin)
                        }))
                        .put("restSeconds", item.restSeconds).put("weightSource", item.weightSource)
                        .put("prescriptionText", item.prescription)
                        .put("legacyResolverSource", resolver?.source)
                        .put("legacyResolverText", resolver?.text)
                        .put("exactB5TaskOwner", StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) in selectedIdentities)
                        .put("authority", "NO_EXACT_TASK_B6; LEGACY_PERFORMANCE_FALLBACK")
                }
            case.put("preflight", "GENERATED").put("taskTargets", caseTargetRows)
                .put("selectedTaskOwners", JSONArray(selectedTaskOwners.map { JSONObject()
                    .put("stableKey", it.stableKey).put("selectionRole", it.selectionRole)
                    .put("primaryTargetId", it.primaryTargetId)
                    .put("coveredTargetIds", JSONArray(it.coveredTargetIds.filter { id -> id.startsWith("TASK:") }.sorted()))
                    .put("selectionReasons", JSONArray(it.selectionReasons.sorted()))
                    .put("probeCompatibility", it.probePrescriptionCompatibility.name)
                    .put("targetSetsFromExistingPrescription", it.targetSetsFromExistingPrescription)
                }))
                .put("b7", JSONObject().put("eligible", result.comparison.experimentalReadinessAudit?.status?.name)
                    .put("reasonCodes", JSONArray(result.comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                    .put("targetOutcomes", JSONArray(result.comparison.experimentalReadinessAudit?.targetOutcomes.orEmpty()
                        .sortedBy { it.targetId }.map { outcome -> JSONObject()
                            .put("targetId", outcome.targetId).put("status", outcome.status.name)
                            .put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
                        })))
                .put("b8", JSONObject().put("status", result.comparison.productionCutoverAuthority?.status?.name)
                    .put("scope", result.comparison.productionCutoverAuthority?.scope?.name)
                    .put("reasonCodes", JSONArray(result.comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted())))
                .put("route", result.routeDecision.selectedSource.name)
                .put("buildAccounting", JSONObject()
                    .put("CONTROL", result.buildCounts.controlBuilds)
                    .put("EXPERIMENTAL", result.buildCounts.experimentalBuilds)
                    .put("TOTAL", result.buildCounts.totalBuildInvocations)
                    .put("THIRD", result.buildCounts.thirdBuilds))
            caseRows.put(case)
        }
        aggregate["uniqueTaskOwners"] = uniqueOwners.size
        val buildProfiles = records.mapNotNull { it.second }.groupingBy(StimulusProductionGenerationResult::buildCounts)
            .eachCount().entries.sortedWith(compareBy({ it.key.controlBuilds }, { it.key.experimentalBuilds },
                { it.key.totalBuildInvocations }, { it.key.thirdBuilds }))
        val buildAccounting = JSONObject()
            .put("generatedCaseProfiles", JSONArray(buildProfiles.map { (counts, cases) -> JSONObject()
                .put("cases", cases).put("CONTROL", counts.controlBuilds)
                .put("EXPERIMENTAL", counts.experimentalBuilds).put("TOTAL", counts.totalBuildInvocations)
                .put("THIRD", counts.thirdBuilds)
            }))
            .put("preflightPerCase", JSONObject().put("CONTROL", 0).put("EXPERIMENTAL", 0)
                .put("TOTAL", 0).put("THIRD", 0))

        val guideRows = JSONArray(RecordBasedReviewedPolicy.badmintonKeys.entries.sortedBy { it.key.name }.map { (category, keys) ->
            val guide = RecordBasedReviewedPolicy.badminton(category)
            JSONObject().put("category", category.name).put("exactStableKeys", JSONArray(keys.sorted()))
                .put("guide", JSONObject().put("setCount", guide.setCount).put("reps", guide.reps)
                    .put("seconds", guide.seconds).put("restSeconds", guide.restSeconds)
                    .put("text", guide.text).put("weightSource", guide.weightSource))
                .put("canonicalTaskMapping", "NONE_APPROVED")
                .put("authorityClassification", "LEGACY_CATEGORY_GUIDANCE_AND_PERFORMANCE_FALLBACK_NOT_CANONICAL_TASK_B6")
        })
        val beforeMaterialRows = preC22CMaterializedTaskRows ?: JSONArray(materialRows)
        aggregate["materializedTaskRowsBefore"] = beforeMaterialRows.length()
        val taskMatrix = JSONArray(tasks.map { task ->
            val rows = targetRows.filter { it.getString("task") == task }
            val selected = ownerRows.filter { it.getString("task") == task }
            JSONObject().put("task", task).put("generatedTargets", rows.size)
                .put("directionOnlyTargets", rows.count {
                    !it.isNull("b4Target") && it.getJSONObject("b4Target").optString("numericAuthority") == "DIRECTION_ONLY"
                })
                .put("selectedOwnerTaskPairs", JSONArray(selected))
                .put("selectedOwnerTaskPairCount", selected.size)
                .put("executableTaskAuthority", false)
                .put("blockingPolicy", when (task) {
                    "ACCELERATION" -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; STEP_GUIDE_NOT_MAPPED_TO_ACCELERATION; TIME_RANGE_UNREPRESENTABLE"
                    "DECELERATION" -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; STEP_GUIDE_NOT_MAPPED_TO_DECELERATION"
                    "FOOTWORK" -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; STEP_TO_FOOTWORK_IS_VISUAL_ALIAS_ONLY; TIME_RANGE_UNREPRESENTABLE"
                    "REACTION" -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; STEP_GUIDE_NOT_MAPPED_TO_REACTION; TIME_RANGE_UNREPRESENTABLE"
                    "LUNGE_REACH" -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; DECELERATION_GUIDE_NOT_MAPPED_TO_LUNGE_REACH; PER_SIDE_UNREPRESENTABLE"
                    else -> "B4_DIRECTION_ONLY_WITHOUT_WEEKLY_FREQUENCY; NO_EXACT_TASK_OWNER_GUIDE_MAPPING"
                })
        })

        val root = JSONObject()
            .put("schema", "c22-badminton-task-authority-census-v1")
            .put("baselineMain", "3af7c7c7d96923b6218f4466506a278c3ac76f7f")
            .put("startHead", "3af7c7c7d96923b6218f4466506a278c3ac76f7f")
            .put("versions", JSONObject().put("protocol", "3.57.0")
                .put("runtime", "RECORD_BASED_PLANNER_0.14.9_KOTLIN_1").put("app", "0.5.1.5"))
            .put("policyConclusion", "NO_EXECUTABLE_TASK_AUTHORITY; UNAUTHORIZED_B5_TASK_MATERIALIZATION_REMOVED")
            .put("counts", JSONObject(aggregate as Map<*, *>))
            .put("buildAccounting", buildAccounting)
            .put("taskMatrix", taskMatrix)
            .put("reviewedGuides", guideRows)
            .put("cases", caseRows)
            .put("ownerTaskMatrix", JSONArray(ownerRows.sortedWith(compareBy({ it.getString("case") }, { it.getString("stableKey") }, { it.getString("selectionRole") }, { it.getString("task") }))))
            .put("materializedTaskRowsBefore", beforeMaterialRows)
            .put("materializedTaskRowsAfter", JSONArray(materialRows.sortedWith(compareBy({ it.getString("case") }, { it.getInt("week") }, { it.getInt("day") }, { it.getInt("order") }, { it.getString("stableKey") }))))
            .put("routeSnapshot", JSONObject().apply {
                val counts = records.mapNotNull { it.second?.routeDecision?.selectedSource?.name }.groupingBy { it }.eachCount()
                put("CONTROL", counts["CONTROL"] ?: 0)
                put("STRENGTH_V1", counts["B8_STRENGTH_V1"] ?: 0)
                put("STRENGTH_CALIBRATION_V1", counts["B8_STRENGTH_CALIBRATION_V1"] ?: 0)
                put("HYPERTROPHY", counts["B8_HYPERTROPHY_V1"] ?: 0)
                put("COMBINED", counts["B8_STRENGTH_HYPERTROPHY_V1"] ?: 0)
            })
            .put("b7ReasonOccurrences", JSONObject().apply {
                val reasons = records.mapNotNull { it.second?.comparison?.experimentalReadinessAudit }
                    .flatMap { it.reasonCodes }.groupingBy { it }.eachCount()
                put("CHANGE_PROVENANCE_UNCLOSED", reasons["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                put("AFFECTED_TARGET_REMAINS_UNMET", reasons["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                put("TARGET_REGRESSED", reasons["TARGET_REGRESSED"] ?: 0)
                put("COLLATERAL_TARGET_REGRESSION", reasons["COLLATERAL_TARGET_REGRESSION"] ?: 0)
            })
            .put("powerRegression", JSONObject().put("numericPowerAuthority", 0)
                .put("powerB6Executable", 0).put("powerMaterialRows", 0))
            .put("scope", JSONObject().put("taskExecutionAuthorityImplemented", false)
                .put("taskCutoverScopeAdded", false).put("genericTaskPrescriptionAdded", false)
                .put("productionFallbackFilterApplied", preC22CMaterializedTaskRows != null))
        return root.toString(2)
    }

    private fun ownerTaskJson(
        case: String,
        task: String,
        candidate: StimulusSelectedCandidate,
        context: PreparedCanonicalGenerationContext,
        relationCatalog: com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog,
        comparison: StimulusSelectionProgramComparison
    ): JSONObject {
        val relation = relationCatalog.relations(candidate.stableKey).firstOrNull { it.objective.name == task }
        val personalRows = context.snapshot.allConfirmedSets.filter { it.stableKey == candidate.stableKey }
        val directTaskObservations = context.snapshot.stimulusExposureLedger.setObservations.filter { observation ->
            observation.source.stableKey == candidate.stableKey &&
                context.snapshot.stimulusExposureLedger.facetProfilesByStableKey[observation.facetProfileKey]
                    ?.badmintonObjectives?.any { it.objective.name == task && it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT } == true
        }
        val category = reviewedCategory(candidate.stableKey)
        val guide = category?.let(RecordBasedReviewedPolicy::badminton)
        val target = comparison.targetPlan.taskTargets.firstOrNull { it.task == task }
        val nominalLabelMatch = category?.name == task
        val taskGuideMapping = if (category == null) "NO_REVIEWED_CATEGORY" else if (nominalLabelMatch)
            "LABEL_IDENTICAL_BUT_NO_APPROVED_TASK_GUIDE_MAPPING" else "CATEGORY_TASK_MISMATCH_NO_APPROVED_MAPPING"
        return JSONObject()
            .put("case", case).put("task", task).put("stableKey", candidate.stableKey)
            .put("selectionRole", candidate.selectionRole).put("primaryTargetId", candidate.primaryTargetId)
            .put("selectedForThisTask", candidate.primaryTargetId == "TASK:$task")
            .put("reusedCoverage", candidate.primaryTargetId != "TASK:$task")
            .put("transferLevel", relation?.transferLevel?.name ?: "NONE")
            .put("relationId", relation?.relationId).put("relationProvenance", relation?.provenance)
            .put("reviewedCategory", category?.name)
            .put("reviewedGuide", guide?.let { JSONObject().put("setCount", it.setCount).put("reps", it.reps)
                .put("seconds", it.seconds).put("restSeconds", it.restSeconds).put("text", it.text)
                .put("weightSource", it.weightSource) } ?: JSONObject.NULL)
            .put("guideTaskMapping", taskGuideMapping).put("nominalCategoryNameMatch", nominalLabelMatch)
            .put("personalExactOwnerConfirmedRows", personalRows.size)
            .put("personalDirectTaskObservationCount", directTaskObservations.size)
            .put("personalTaskPrescriptionAuthority", false)
            .put("b4NumericAuthority", target?.numericAuthority?.name)
            .put("weeklyUnitAuthority", target?.weeklyDirectUnitsTarget?.let(::rangeJson))
            .put("weeklySessionAuthority", target?.weeklyDirectSessionsTarget?.let(::rangeJson))
            .put("frequencyAuthorityPresent", target?.weeklyDirectSessionsTarget != null)
            .put("taskB6Authority", "NONE")
            .put("executable", false)
    }

    private fun reviewedCategory(stableKey: String): ReviewedBadmintonCategory? =
        RecordBasedReviewedPolicy.badmintonKeys.entries.firstOrNull { stableKey in it.value }?.key

    private fun rangeJson(range: StimulusTargetRange) = JSONObject()
        .put("min", range.min).put("preferred", range.preferred).put("max", range.max)
}
