package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Test-side deterministic shadow census. It deliberately has no CONTROL program input. */
internal object C23TaskProtocolFrequencyCensus {
    private val tasks = CanonicalTaskTarget.entries.toList()

    fun render(
        c22Census: String,
        metadataRepository: CanonicalExerciseMetadataRepository,
        c20Census: String
    ): String {
        val baseline = JSONObject(c22Census)
        val c20 = JSONObject(c20Census)
        val relationCatalog = metadataRepository.badmintonObjectiveCatalog()
        val cases = baseline.getJSONArray("cases")
        val corpusCases = JSONArray()
        val completenessRows = mutableListOf<JSONObject>()
        val seenOwnerTaskPairs = linkedSetOf<Triple<String, String, CanonicalTaskTarget>>()
        val aggregate = linkedMapOf(
            "corpusCases" to cases.length(),
            "taskTargetsTotal" to 0,
            "directionOnlyTargets" to 0,
            "exactB5TaskOwnerRows" to (baseline.optJSONObject("counts")?.optInt("exactB5TaskOwnerRows", 0) ?: 0),
            "exactB5OwnerTaskPairs" to 0,
            "uniqueTaskOwners" to 0,
            "directTaskRelations" to 0,
            "supportiveTaskRelations" to 0,
            "exactProtocolBindings" to 0,
            "missingProtocolBindings" to 0,
            "conflictingProtocolBindings" to 0,
            "personalFrequencyAuthorities" to 0,
            "reviewedFrequencyAuthorities" to 0,
            "missingFrequencyAuthorities" to 0,
            "conflictingFrequencyAuthorities" to 0,
            "perSideRepresentable" to 0,
            "durationRangeRepresentable" to 0,
            "repRangeRepresentable" to 0,
            "scalarRepetitionsRepresentable" to 0,
            "scalarDurationRepresentable" to 0,
            "existingB4NumericFrequencyTargets" to 0,
            "completeTaskAuthorities" to 0,
            "incompleteTaskAuthorities" to 0,
            "conflictingTaskAuthorities" to 0,
            "materializedTaskRows" to 0,
            "powerNumericAuthority" to 0,
            "powerB6Executable" to 0,
            "powerMaterialRows" to 0
        )
        val uniqueOwners = linkedSetOf<String>()
        val taskMapRows = JSONArray(tasks.map { task ->
            val approved = ReviewedTaskProtocolBindings.approved.filter { it.task == task }
                .sortedWith(compareBy({ it.stableKey }, { it.reviewedCategory.name }, { it.source.name }))
            val hasConflictingOwnerBinding = approved.groupBy { it.stableKey to it.selectionRole }
                .values.any { rows -> rows.distinct().size > 1 }
            val state = when {
                hasConflictingOwnerBinding -> ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS
                approved.isNotEmpty() -> ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND
                else -> ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING
            }
            JSONObject().put("task", task.name)
                .put("reviewedProtocolOutcome", state.name)
                .put("reasonCode", when (state) {
                    ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND -> "EXACT_OWNER_TASK_BINDINGS_EXIST"
                    ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING -> "NO_APPROVED_TASK_PROTOCOL_BINDING"
                    ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS -> "CONFLICTING_BINDINGS_FOR_EXACT_OWNER_TASK"
                })
                .put("approvedExactBindings", JSONArray(approved.map(::bindingJson)))
        })

        for (caseIndex in 0 until cases.length()) {
            val sourceCase = cases.getJSONObject(caseIndex)
            val caseName = sourceCase.getString("case")
            val targetRows = sourceCase.optJSONArray("taskTargets") ?: JSONArray()
            val outputTargets = JSONArray()
            for (targetIndex in 0 until targetRows.length()) {
                val sourceTarget = targetRows.getJSONObject(targetIndex)
                val task = CanonicalTaskTarget.valueOf(sourceTarget.getString("task"))
                val b4 = sourceTarget.optJSONObject("b4Target")
                val numericAuthority = b4?.optString("numericAuthority")
                val owners = sourceTarget.optJSONArray("b5SelectedOwners") ?: JSONArray()
                val ownerAssessments = JSONArray()
                aggregate["taskTargetsTotal"] = aggregate.getValue("taskTargetsTotal") + 1
                if (numericAuthority == "DIRECTION_ONLY") {
                    aggregate["directionOnlyTargets"] = aggregate.getValue("directionOnlyTargets") + 1
                }
                if (b4?.optJSONObject("weeklyDirectSessions") != null) {
                    aggregate["existingB4NumericFrequencyTargets"] = aggregate.getValue("existingB4NumericFrequencyTargets") + 1
                }

                if (owners.length() == 0) {
                    val missing = TaskAuthorityCompletenessEvaluator.evaluate(
                        TaskAuthorityCompletenessInput(
                            task = task,
                            exactTargetPresent = b4 != null,
                            owner = null,
                            transferLevel = null,
                            binding = ReviewedTaskProtocolBindings.resolve("<no-owner>", "<no-role>", task, null),
                            frequency = TaskFrequencyAuthority.none(task),
                            shape = null
                        )
                    )
                    val row = JSONObject()
                        .put("case", caseName).put("task", task.name)
                        .put("need", sourceTarget.opt("need").takeUnless { sourceTarget.isNull("need") } ?: JSONObject.NULL)
                        .put("b3Decision", sourceTarget.opt("b3Decision").takeUnless { sourceTarget.isNull("b3Decision") } ?: JSONObject.NULL)
                        .put("b4Target", b4 ?: JSONObject.NULL)
                        .put("owners", ownerAssessments)
                        .put("state", missing.state.name)
                        .put("reasonCodes", JSONArray(missing.reasonCodes))
                    outputTargets.put(row)
                    completenessRows += row
                    aggregate["incompleteTaskAuthorities"] = aggregate.getValue("incompleteTaskAuthorities") + 1
                    aggregate["missingProtocolBindings"] = aggregate.getValue("missingProtocolBindings") + 1
                    aggregate["missingFrequencyAuthorities"] = aggregate.getValue("missingFrequencyAuthorities") + 1
                    continue
                }

                val rowResults = mutableListOf<TaskAuthorityCompletenessResult>()
                for (ownerIndex in 0 until owners.length()) {
                    val ownerRow = owners.getJSONObject(ownerIndex)
                    val stableKey = ownerRow.getString("stableKey")
                    val selectionRole = ownerRow.getString("selectionRole")
                    uniqueOwners += "$stableKey#$selectionRole"
                    val relations = relationCatalog.relations(stableKey)
                    val relation = relations.singleOrNull { it.objective.name == task.name }
                    val transfer = relation?.transferLevel
                    when (transfer) {
                        BadmintonObjectiveTransferLevel.DIRECT -> aggregate["directTaskRelations"] = aggregate.getValue("directTaskRelations") + 1
                        BadmintonObjectiveTransferLevel.SUPPORTIVE -> aggregate["supportiveTaskRelations"] = aggregate.getValue("supportiveTaskRelations") + 1
                        else -> Unit
                    }
                    val category = RecordBasedReviewedPolicy.reviewedCategory(stableKey)
                    val binding = ReviewedTaskProtocolBindings.resolve(stableKey, selectionRole, task, category)
                    when (binding.state) {
                        ReviewedTaskProtocolBindingState.EXACT_REVIEWED_PROTOCOL_BOUND ->
                            aggregate["exactProtocolBindings"] = aggregate.getValue("exactProtocolBindings") + 1
                        ReviewedTaskProtocolBindingState.NO_APPROVED_PROTOCOL_BINDING ->
                            aggregate["missingProtocolBindings"] = aggregate.getValue("missingProtocolBindings") + 1
                        ReviewedTaskProtocolBindingState.MULTIPLE_CONFLICTING_BINDINGS ->
                            aggregate["conflictingProtocolBindings"] = aggregate.getValue("conflictingProtocolBindings") + 1
                    }
                    val guide = category?.let(RecordBasedReviewedPolicy::badminton)
                    val shape = guide?.taskShape
                    // Historical set observations persist exercise stableKey but not the B5 selectionRole.
                    // C22 reports no exact-owner direct task observations for these selected keys; if that
                    // changes, the source still cannot be re-labeled as this canonical owner without a role edge.
                    val historicalTaskRows = ownerRow.optInt("personalDirectTaskObservationCount")
                    val frequency = TaskFrequencyAuthority.none(
                        task,
                        if (historicalTaskRows > 0) "TASK_HISTORY_SELECTION_ROLE_NOT_PERSISTED"
                        else "TASK_EXACT_OWNER_DIRECT_HISTORY_ABSENT"
                    )
                    when (frequency.state) {
                        TaskFrequencyAuthorityState.PERSONAL_BASELINE -> aggregate["personalFrequencyAuthorities"] = aggregate.getValue("personalFrequencyAuthorities") + 1
                        TaskFrequencyAuthorityState.REVIEWED_PROTOCOL -> aggregate["reviewedFrequencyAuthorities"] = aggregate.getValue("reviewedFrequencyAuthorities") + 1
                        TaskFrequencyAuthorityState.NONE -> if (owners.length() > 0) {
                            aggregate["missingFrequencyAuthorities"] = aggregate.getValue("missingFrequencyAuthorities") + 1
                        }
                        TaskFrequencyAuthorityState.CONFLICT -> aggregate["conflictingFrequencyAuthorities"] = aggregate.getValue("conflictingFrequencyAuthorities") + 1
                    }
                    val result = TaskAuthorityCompletenessEvaluator.evaluate(
                        TaskAuthorityCompletenessInput(
                            task = task,
                            exactTargetPresent = b4 != null,
                            owner = ExactTaskOwnerRef(stableKey, selectionRole),
                            transferLevel = transfer,
                            binding = binding,
                            frequency = frequency,
                            shape = shape
                        )
                    )
                    rowResults += result
                    val pairKey = Triple(stableKey, selectionRole, task)
                    seenOwnerTaskPairs += pairKey
                    ownerAssessments.put(
                        JSONObject().put("stableKey", stableKey).put("selectionRole", selectionRole)
                            .put("primaryTargetId", ownerRow.opt("primaryTargetId").takeUnless { ownerRow.isNull("primaryTargetId") } ?: JSONObject.NULL)
                            .put("selectedForTask", ownerRow.optBoolean("selectedForThisTask"))
                            .put("reusedCoverage", ownerRow.optBoolean("reusedCoverage"))
                            .put("transferLevel", transfer?.name ?: "NONE")
                            .put("relationId", relation?.relationId ?: JSONObject.NULL)
                            .put("reviewedCategory", category?.name ?: JSONObject.NULL)
                            .put("reviewedGuide", guide?.let(::guideJson) ?: JSONObject.NULL)
                            .put("protocolBinding", bindingJson(binding))
                            .put("personalDirectTaskObservationCount", ownerRow.optInt("personalDirectTaskObservationCount"))
                            .put("historicalSelectionRoleAvailable", false)
                            .put("frequencyEvidenceStatus", frequency.reasonCode)
                            .put("frequencyAuthority", frequencyJson(frequency))
                            .put("existingB4WeeklySessions", b4?.optJSONObject("weeklyDirectSessions") ?: JSONObject.NULL)
                            .put("prescriptionShape", shape?.toJson() ?: JSONObject.NULL)
                            .put("shapeLossless", shape != null)
                            .put("shapeExecutionContextComplete", shape?.executionContextComplete ?: false)
                            .put("authority", result.state.name)
                            .put("reasonCodes", JSONArray(result.reasonCodes))
                    )
                }
                val combined = when {
                    rowResults.any { it.state == TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT } ->
                        TaskAuthorityCompletenessResult(TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT, rowResults.flatMap { it.reasonCodes }.distinct().sorted())
                    rowResults.isNotEmpty() && rowResults.all { it.state == TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE } ->
                        TaskAuthorityCompletenessResult(TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE, listOf("ALL_EXACT_OWNER_TASK_AUTHORITIES_COMPLETE"))
                    else -> TaskAuthorityCompletenessResult(TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE, rowResults.flatMap { it.reasonCodes }.distinct().sorted())
                }
                val output = JSONObject()
                    .put("case", caseName).put("task", task.name)
                    .put("need", sourceTarget.opt("need").takeUnless { sourceTarget.isNull("need") } ?: JSONObject.NULL)
                    .put("b3Decision", sourceTarget.opt("b3Decision").takeUnless { sourceTarget.isNull("b3Decision") } ?: JSONObject.NULL)
                    .put("b4Target", b4 ?: JSONObject.NULL)
                    .put("owners", ownerAssessments)
                    .put("state", combined.state.name)
                    .put("reasonCodes", JSONArray(combined.reasonCodes))
                outputTargets.put(output)
                completenessRows += output
                when (combined.state) {
                    TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE -> aggregate["completeTaskAuthorities"] = aggregate.getValue("completeTaskAuthorities") + 1
                    TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE -> aggregate["incompleteTaskAuthorities"] = aggregate.getValue("incompleteTaskAuthorities") + 1
                    TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT -> aggregate["conflictingTaskAuthorities"] = aggregate.getValue("conflictingTaskAuthorities") + 1
                }
            }
            corpusCases.put(JSONObject().put("case", caseName).put("preflight", sourceCase.optString("preflight"))
                .put("taskTargets", outputTargets)
                .put("taskMaterialRowsBefore", sourceCase.optJSONArray("taskMaterialRowsBefore") ?: JSONArray())
                .put("taskMaterialRowsAfter", sourceCase.optJSONArray("taskMaterialRowsAfter") ?: JSONArray())
                .put("route", sourceCase.optString("route"))
                .put("b7", sourceCase.optJSONObject("b7") ?: JSONObject())
                .put("b8", sourceCase.optJSONObject("b8") ?: JSONObject()))
        }
        aggregate["exactB5OwnerTaskPairs"] = seenOwnerTaskPairs.size
        aggregate["uniqueTaskOwners"] = uniqueOwners.size
        aggregate["materializedTaskRows"] = baseline.getJSONArray("materializedTaskRowsAfter").length()

        // These are unique reviewed guide forms, not owner authority counts.
        val reviewedGuideShapes = RecordBasedReviewedPolicy.badmintonKeys.keys
            .distinct().sortedBy { it.name }.map(RecordBasedReviewedPolicy::badminton).map { it.taskShape }
        reviewedGuideShapes.groupingBy { it.mode }.eachCount().forEach { (mode, count) ->
            when (mode) {
                TaskPrescriptionMode.REPETITIONS_PER_SIDE -> aggregate["perSideRepresentable"] = count
                TaskPrescriptionMode.DURATION_RANGE_SECONDS -> aggregate["durationRangeRepresentable"] = count
                TaskPrescriptionMode.REPETITION_RANGE -> aggregate["repRangeRepresentable"] = count
                TaskPrescriptionMode.REPETITIONS -> aggregate["scalarRepetitionsRepresentable"] = count
                TaskPrescriptionMode.DURATION_SECONDS -> aggregate["scalarDurationRepresentable"] = count
            }
        }

        val reviewedMembers = JSONArray(RecordBasedReviewedPolicy.badmintonKeys.entries
            .sortedBy { it.key.name }
            .flatMap { (category, stableKeys) -> stableKeys.sorted().map { stableKey ->
                val relations = relationCatalog.relations(stableKey)
                    .filter { it.objective in tasks.map { task -> task.objective() }.toSet() }
                    .sortedBy { it.objective.ordinal }
                JSONObject().put("stableKey", stableKey).put("reviewedCategory", category.name)
                    .put("guide", guideJson(RecordBasedReviewedPolicy.badminton(category)))
                    .put("canonicalTaskRelations", JSONArray(relations.map { relation -> JSONObject()
                        .put("task", relation.objective.name).put("transferLevel", relation.transferLevel.name)
                        .put("relationId", relation.relationId).put("provenance", relation.provenance)
                    }))
                    .put("approvedBindings", JSONArray(ReviewedTaskProtocolBindings.approved
                        .filter { it.stableKey == stableKey }
                        .sortedWith(compareBy({ it.task.name }, { it.reviewedCategory.name }, { it.source.name }))
                        .map(::bindingJson)))
            } })

        val taskMatrix = JSONArray(tasks.map { task ->
            val targets = completenessRows.filter { it.getString("task") == task.name }
            JSONObject().put("task", task.name).put("targets", targets.size)
                .put("directionOnlyTargets", targets.count { row ->
                    !row.isNull("b4Target") && row.getJSONObject("b4Target").optString("numericAuthority") == "DIRECTION_ONLY"
                })
                .put("mappingOutcome", when {
                    ReviewedTaskProtocolBindings.approved.any { it.task == task } -> "EXACT_REVIEWED_PROTOCOL_BOUND"
                    else -> "NO_APPROVED_PROTOCOL_BINDING"
                })
                .put("reviewedGuideNamesAreAuthority", false)
                .put("complete", targets.count { it.getString("state") == TaskAuthorityCompletenessState.TASK_AUTHORITY_COMPLETE.name })
                .put("incomplete", targets.count { it.getString("state") == TaskAuthorityCompletenessState.TASK_AUTHORITY_INCOMPLETE.name })
                .put("conflict", targets.count { it.getString("state") == TaskAuthorityCompletenessState.TASK_AUTHORITY_CONFLICT.name })
        })
        val c20Feasibility = c20.optJSONObject("summary") ?: JSONObject()
        val root = JSONObject()
            .put("schema", "c23-task-protocol-frequency-representation-census-v1")
            .put("c22MergeSha", "392b6e6346ea08b9fc92f61ae99fd99b401793b2")
            .put("c23StartSha", "392b6e6346ea08b9fc92f61ae99fd99b401793b2")
            .put("versions", JSONObject().put("protocol", CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION)
                .put("runtime", PERSONALIZED_PLANNER_PROTOCOL).put("app", "0.5.1.5"))
            .put("counts", JSONObject(aggregate as Map<*, *>))
            .put("taskProtocolBindings", JSONObject().put("approved", JSONArray())
                .put("taskOutcomes", taskMapRows))
            .put("reviewedCategoryMembers", reviewedMembers)
            .put("taskFrequencyAuthorities", JSONObject()
                .put("personalAuthorityRule", JSONObject().put("minimumCompletedWeeks", PersonalTaskFrequencyAuthorityResolver.MIN_ELIGIBLE_COMPLETED_WEEKS)
                    .put("minimumDirectExposureWeeks", PersonalTaskFrequencyAuthorityResolver.MIN_DIRECT_EXPOSURE_WEEKS)
                    .put("weeklySessionCountMustBeStable", true)
                    .put("source", "EXACT_STABLE_KEY_SELECTION_ROLE_AND_TASK_COMPLETED_DIRECT_OBSERVATIONS_ONLY"))
                .put("reviewedFrequencySource", "NONE_OF_THE_REVIEWED_GUIDES_DECLARES_SESSIONS_PER_WEEK")
                .put("existingB4NumericFrequencyTargets", aggregate.getValue("existingB4NumericFrequencyTargets"))
                .put("defaultScheduleIsAuthority", false)
                .put("authorities", JSONArray(completenessRows.flatMap { row ->
                    val owners = row.getJSONArray("owners")
                    (0 until owners.length()).map { owners.getJSONObject(it) }
                }.mapNotNull { it.optJSONObject("frequencyAuthority") }.distinctBy { it.toString() }
                    .sortedBy { it.toString() })))
            .put("taskPrescriptionRepresentations", JSONObject()
                .put("modes", JSONArray(TaskPrescriptionMode.entries.map { mode -> JSONObject().put("mode", mode.name)
                    .put("representable", true).put("examples", JSONArray(completenessRows.flatMap { row ->
                        val owners = row.getJSONArray("owners")
                        (0 until owners.length()).mapNotNull { index -> owners.getJSONObject(index).optJSONObject("prescriptionShape") }
                    }.filter { it.optString("mode") == mode.name }.distinctBy { it.toString() }.sortedBy { it.toString() })) }))
                .put("rpeRequiredByRepresentation", false)
                .put("loadModeRequiredForExecution", true)
                .put("activityKindRequiredForExecution", true)
                .put("persistenceRequired", false)
                .put("persistenceReason", "SHADOW_ONLY_SHAPE; NO_TASK_B6_OR_PERSISTED_TASK_ROWS_IN_C23"))
            .put("taskAuthorityCompleteness", JSONArray(completenessRows))
            .put("taskMatrix", taskMatrix)
            .put("corpusCases", corpusCases)
            .put("realCaseDossiers", JSONArray(listOf("persona3_recent", "persona3_reviewed", "persona3_mixed").map { name ->
                val row = corpusCases.firstObject { it.getString("case") == name }
                JSONObject().put("case", name).put("taskTargets", row.optJSONArray("taskTargets") ?: JSONArray())
                    .put("route", row.optString("route"))
            }))
            .put("c20IncumbentRegression", c20Feasibility)
            .put("routeSnapshot", baseline.optJSONObject("routeSnapshot") ?: JSONObject())
            .put("b7ReasonOccurrences", baseline.optJSONObject("b7ReasonOccurrences") ?: JSONObject())
            .put("powerInvariant", JSONObject().put("numericAuthority", 0).put("executableB6", 0).put("materialRows", 0))
            .put("taskMaterialRowsBefore", baseline.optJSONArray("materializedTaskRowsBefore") ?: JSONArray())
            .put("taskMaterialRowsAfter", JSONArray())
            .put("buildAccounting", baseline.optJSONObject("buildAccounting") ?: JSONObject())
            .put("productionChanges", JSONObject().put("taskB6Added", false).put("taskMaterializationAdded", false)
                .put("badmintonRouteAdded", false).put("b7Changed", false).put("b8Changed", false)
                .put("powerReopened", false).put("thirdBuild", false))
        return root.toString(2)
    }

    private fun guideJson(guide: ReviewedPerformanceGuide) = JSONObject()
        .put("setCount", guide.setCount).put("legacyReps", guide.reps).put("legacySeconds", guide.seconds)
        .put("restSeconds", guide.restSeconds).put("text", guide.text).put("weightSource", guide.weightSource)
        .put("typedShape", guide.taskShape.toJson())
        .put("typedShapeExecutionContextComplete", guide.taskShape.executionContextComplete)
        .put("classification", "CATEGORY_LEVEL_LEGACY_GUIDANCE_NOT_CANONICAL_TASK_AUTHORITY")

    private fun bindingJson(binding: ReviewedTaskProtocolBinding) = JSONObject()
        .put("stableKey", binding.stableKey).put("selectionRole", binding.selectionRole).put("task", binding.task.name)
        .put("reviewedCategory", binding.reviewedCategory.name).put("source", binding.source.name)
        .put("weeklySessions", binding.weeklySessions ?: JSONObject.NULL)

    private fun bindingJson(resolution: ReviewedTaskProtocolBindingResolution) = JSONObject()
        .put("state", resolution.state.name).put("reasonCode", resolution.reasonCode)
        .put("binding", resolution.binding?.let(::bindingJson) ?: JSONObject.NULL)

    private fun frequencyJson(authority: TaskFrequencyAuthority) = JSONObject()
        .put("state", authority.state.name).put("weeklySessions", authority.weeklySessions ?: JSONObject.NULL)
        .put("sourceStableKey", authority.sourceStableKey ?: JSONObject.NULL)
        .put("sourceSelectionRole", authority.sourceSelectionRole ?: JSONObject.NULL)
        .put("task", authority.task?.name ?: JSONObject.NULL)
        .put("reviewedProtocolBinding", authority.reviewedProtocolBinding?.let(::bindingJson) ?: JSONObject.NULL)
        .put("eligibleCompletedWeeks", authority.eligibleCompletedWeeks)
        .put("directExposureWeeks", authority.directExposureWeeks).put("reasonCode", authority.reasonCode)

    private fun JSONArray.firstObject(predicate: (JSONObject) -> Boolean): JSONObject =
        (0 until length()).map { getJSONObject(it) }.first(predicate)
}
