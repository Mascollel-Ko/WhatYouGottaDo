package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Exact, test-only row ledger for the four C15 calibration cases and the positive reference. */
internal fun renderC16B8ResidualBlockerCensus(
    records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
    c15MergeMainHead: String,
    c16StartHead: String
): String {
    val selectedIds = listOf("persona0_mixed", "persona0_reviewed", "persona2_reviewed", "persona3_reviewed", "persona4_mixed")
    val selected = records.filter { it.first.label in selectedIds }.sortedBy { it.first.label }
    require(selected.map { it.first.label }.toSet() == selectedIds.toSet()) { "C16 target corpus is incomplete" }

    fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
        .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)

    fun setJson(set: ProgramSetPrescription) = JSONObject()
        .put("setIndex", set.setIndex).put("reps", set.reps).put("weightKg", set.weightKg)
        .put("seconds", set.seconds).put("targetRpe", set.targetRpeMin ?: JSONObject.NULL)
        .put("loadState", set.loadState.name)

    fun rowJson(row: ProgramSkeletonItem) = JSONObject()
        .put("owner", ownerJson(StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)))
        .put("week", row.weekNumber).put("day", row.dayOfWeek).put("order", row.orderIndex)
        .put("localId", row.localId).put("exerciseName", row.exerciseName).put("category", row.category)
        .put("setCount", row.setCount).put("reps", row.reps).put("weightKg", row.weightKg)
        .put("seconds", row.seconds).put("restSeconds", row.restSeconds)
        .put("prescription", row.prescription).put("weightSource", row.weightSource)
        .put("selectionReason", row.selectionReason).put("stableKeyAlias", row.stableKey)
        .put("trainingSlot", row.trainingSlot).put("dayIntensity", row.dayIntensity)
        .put("metadataProgramSlot", row.metadataProgramSlot).put("redundancyGroup", row.redundancyGroup)
        .put("strengthProgressionGroup", row.strengthProgressionGroup)
        .put("setPrescriptions", JSONArray(row.setPrescriptions.sortedBy { it.setIndex }.map(::setJson)))

    fun rowKey(row: ProgramSkeletonItem) = listOf(row.weekNumber, row.dayOfWeek, row.orderIndex)

    fun fieldChanges(before: ProgramSkeletonItem, after: ProgramSkeletonItem): List<String> = buildList {
        if (before.dayOfWeek != after.dayOfWeek) add("day")
        if (before.orderIndex != after.orderIndex) add("order")
        if (before.setCount != after.setCount) add("setCount")
        if (before.reps != after.reps) add("reps")
        if (before.weightKg != after.weightKg || before.weightSource != after.weightSource ||
            before.setPrescriptions.map { it.loadState } != after.setPrescriptions.map { it.loadState }) add("loadStateOrLoad")
        if (before.setPrescriptions.map { it.targetRpeMin } != after.setPrescriptions.map { it.targetRpeMin }) add("targetRpe")
        if (before.setPrescriptions != after.setPrescriptions) add("setPrescriptions")
        if (before.seconds != after.seconds) add("seconds")
        if (before.restSeconds != after.restSeconds) add("restSeconds")
        if (before.prescription != after.prescription) add("prescription")
        if (before.trainingSlot != after.trainingSlot) add("trainingSlot")
        if (before.dayIntensity != after.dayIntensity) add("dayIntensity")
        if (before.exerciseName != after.exerciseName || before.category != after.category) add("exerciseMetadata")
        if (before.selectionReason != after.selectionReason || before.stableKey != after.stableKey ||
            before.metadataProgramSlot != after.metadataProgramSlot || before.redundancyGroup != after.redundancyGroup ||
            before.strengthProgressionGroup != after.strengthProgressionGroup) add("selectionOrOwnerMetadata")
        if (before != after && isEmpty()) add("otherSkeletonMetadata")
    }

    fun deltaLedger(comparison: StimulusSelectionProgramComparison): JSONArray {
        val control = comparison.control.items
        val experimental = comparison.experimental.items
        val owners = (comparison.controlOwnerIdentities + comparison.experimentalOwnerIdentities)
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val deltas = mutableListOf<JSONObject>()
        owners.forEach { owner ->
            val beforeRows = control.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
                .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId }))
            val afterRows = experimental.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
                .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId }))
            val matchedBefore = mutableSetOf<ProgramSkeletonItem>()
            val matchedAfter = mutableSetOf<ProgramSkeletonItem>()
            fun record(before: ProgramSkeletonItem, after: ProgramSkeletonItem, match: String) {
                matchedBefore += before
                matchedAfter += after
                val fields = fieldChanges(before, after)
                if (fields.isNotEmpty()) deltas += JSONObject()
                    .put("owner", ownerJson(owner)).put("kind", if ("day" in fields || "order" in fields) "PLACEMENT_OR_ROW_CHANGE" else "ROW_MATERIAL_CHANGE")
                    .put("match", match).put("changedFields", JSONArray(fields.sorted()))
                    .put("before", rowJson(before)).put("after", rowJson(after))
            }
            // Match exact weekly placements first, then pair remaining rows within the same week
            // to expose accepted day/order moves without conflating roles or weeks.
            beforeRows.forEach { before ->
                val after = afterRows.firstOrNull { candidate -> candidate !in matchedAfter && rowKey(candidate) == rowKey(before) }
                if (after != null) record(before, after, "EXACT_OWNER_WEEK_DAY_ORDER")
            }
            beforeRows.filterNot { it in matchedBefore }.groupBy { it.weekNumber }.toSortedMap().forEach { (week, remainingBefore) ->
                val remainingAfter = afterRows.filter { it !in matchedAfter && it.weekNumber == week }
                remainingBefore.zip(remainingAfter).forEach { (before, after) -> record(before, after, "EXACT_OWNER_WEEK_FALLBACK") }
            }
            beforeRows.filterNot { it in matchedBefore }.forEach { before ->
                deltas += JSONObject().put("owner", ownerJson(owner)).put("kind", "ROW_REMOVED")
                    .put("match", "EXACT_OWNER_NO_EXPERIMENTAL_ROW").put("before", rowJson(before)).put("after", JSONObject.NULL)
            }
            afterRows.filterNot { it in matchedAfter }.forEach { after ->
                deltas += JSONObject().put("owner", ownerJson(owner)).put("kind", "ROW_ADDED")
                    .put("match", "EXACT_OWNER_NO_CONTROL_ROW").put("before", JSONObject.NULL).put("after", rowJson(after))
            }
            if (beforeRows.isEmpty() || afterRows.isEmpty()) {
                // The row records above are sufficient; this marker captures exact owner-level disposition.
                deltas += JSONObject().put("owner", ownerJson(owner))
                    .put("kind", if (beforeRows.isEmpty()) "OWNER_ADDED" else "OWNER_REMOVED")
                    .put("match", "EXACT_STABLE_KEY_AND_SELECTION_ROLE")
                    .put("beforeOwnerRowCount", beforeRows.size).put("afterOwnerRowCount", afterRows.size)
            }
        }
        return JSONArray(deltas.sortedWith(compareBy<JSONObject>({ it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") }, { it.getString("kind") },
            { it.optJSONObject("before")?.optInt("week") ?: it.optJSONObject("after")?.optInt("week") ?: 0 },
            { it.optJSONObject("before")?.optInt("day") ?: it.optJSONObject("after")?.optInt("day") ?: 0 },
            { it.optJSONObject("before")?.optInt("order") ?: it.optJSONObject("after")?.optInt("order") ?: 0 })))
    }

    fun ownerEvidence(comparison: StimulusSelectionProgramComparison): JSONArray {
        val owners = (comparison.controlOwnerIdentities + comparison.experimentalOwnerIdentities)
            .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val audit = comparison.experimentalReadinessAudit
        val selectedCandidates = comparison.selectionPlan.selectedCandidates
        val authorizations = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
        val materializations = comparison.prescriptionMaterializationAudits
        val nonSelection = comparison.nonSelectionProvenance
        val execution = comparison.experimental.personalizedDecision?.planningBudget?.execution
        return JSONArray(owners.map { owner ->
            val candidate = selectedCandidates.filter { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            val auth = authorizations.filter { it.owner?.let { value ->
                value.stableKey == owner.stableKey && value.selectionRole == owner.selectionRole
            } == true }
            val ownerAttributions = audit?.changeAttributions.orEmpty().filter {
                it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
            }
            val ownerOmission = nonSelection.filter { it.omittedControlOwner == owner }
            JSONObject().put("owner", ownerJson(owner))
                .put("controlRowCount", comparison.control.items.count { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole })
                .put("experimentalRowCount", comparison.experimental.items.count { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole })
                .put("B5Selected", candidate.isNotEmpty())
                .put("B5Targets", JSONArray(candidate.flatMap { it.coveredTargetIds }.distinct().sorted()))
                .put("B5NonSelection", JSONArray(ownerOmission.map { omission ->
                    JSONObject().put("classification", omission.classification.name).put("targetEvidence", JSONArray(
                        omission.targetEvidence.sortedBy { it.targetId }.map { evidence -> JSONObject()
                            .put("targetId", evidence.targetId).put("classification", evidence.classification.name)
                            .put("status", evidence.disposition.status.name)
                            .put("reasons", JSONArray(evidence.disposition.reasons.map { it.name }.sorted()))
                            .put("selectedInstead", evidence.disposition.selectedInstead?.let(::ownerJson) ?: JSONObject.NULL)
                        }
                    ))
                }))
                .put("B6", JSONArray(auth.map { row -> JSONObject()
                    .put("quality", row.quality?.name ?: JSONObject.NULL).put("targetId", row.targetId)
                    .put("status", row.status.name).put("executionAuthority", row.executionAuthority.name)
                    .put("reasons", JSONArray(row.reasonCodes.sorted()))
                    .put("authorizedPrescription", row.authorizedPrescription?.let { prescription -> JSONObject()
                        .put("weightSource", prescription.weightSource).put("restSeconds", prescription.restSeconds)
                        .put("sets", JSONArray(prescription.sets.sortedBy { it.setIndex }.map(::setJson)))
                    } ?: JSONObject.NULL)
                }))
                .put("materialization", JSONArray(materializations.filter { row -> row.owner?.let {
                    it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                } == true }.map { row -> JSONObject().put("quality", row.quality?.name ?: JSONObject.NULL)
                    .put("state", row.state.name).put("executionAuthority", row.executionAuthority.name)
                    .put("shortfall", row.shortfall).put("overrun", row.overrun)
                    .put("reasonCodes", JSONArray(row.reasonCodes.sorted())) }))
                .put("B7Attributions", JSONArray(ownerAttributions.map { attribution -> JSONObject()
                    .put("source", attribution.source.name).put("targetIds", JSONArray(attribution.targetIds.sorted()))
                    .put("reasonCodes", JSONArray(attribution.reasonCodes.sorted()))
                }))
                .put("C10OwnerMutations", JSONArray(execution?.ownerAllocationProvenance.orEmpty().filter { it.owner == owner }
                    .deterministicOwnerOrder().map { it.toJson() }))
                .put("C10DisplacementEdges", JSONArray(execution?.ownerDisplacementEdges.orEmpty().filter {
                    it.causeOwner == owner || it.displacedOwner == owner
                }.map { it.toJson() }))
        })
    }

    fun caseJson(spec: StimulusProductionCoverageAuditTest.CoverageSpec, result: StimulusProductionGenerationResult): JSONObject {
        val comparison = requireNotNull(result.comparison)
        val resolution = result.diagnostics.scopeResolution
        val coldStartAuthority = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().singleOrNull {
            it.quality == TrainableQuality.STRENGTH && it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        }
        val exactOwner = coldStartAuthority?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val deltas = deltaLedger(comparison)
        val calibrationDelta = deltas.filter { delta ->
            val owner = delta.optJSONObject("owner") ?: return@filter false
            if (owner.optString("stableKey") != exactOwner?.stableKey ||
                owner.optString("selectionRole") != exactOwner?.selectionRole || coldStartAuthority == null ||
                coldStartAuthority.targetId != "QUALITY:STRENGTH" ||
                coldStartAuthority.executionAuthority != StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT
            ) return@filter false
            val after = delta.optJSONObject("after") ?: return@filter false
            val prescription = coldStartAuthority.authorizedPrescription ?: return@filter false
            val authorizedSets = prescription.sets.sortedBy { it.setIndex }
            val afterSets = after.optJSONArray("setPrescriptions") ?: return@filter false
            val exactMaterializedShape = after.optInt("setCount") == authorizedSets.size &&
                after.optInt("restSeconds") == prescription.restSeconds &&
                after.optString("weightSource") == prescription.weightSource && afterSets.length() == authorizedSets.size &&
                (0 until afterSets.length()).all { index ->
                    val actual = afterSets.getJSONObject(index)
                    val expected = authorizedSets[index]
                    actual.optInt("setIndex") == expected.setIndex && actual.optInt("reps") == expected.reps &&
                        actual.optDouble("weightKg", Double.NaN) == expected.weightKg &&
                        actual.optInt("seconds") == expected.seconds &&
                        actual.optDouble("targetRpe", Double.NaN) == (expected.targetRpeMin ?: Double.NaN) &&
                        actual.optString("loadState") == expected.loadState.name
                }
            if (!exactMaterializedShape) return@filter false
            when (delta.optString("kind")) {
                "ROW_ADDED" -> true
                "ROW_MATERIAL_CHANGE" -> {
                    val changed = delta.optJSONArray("changedFields")
                    val calibrationFields = setOf("reps", "loadStateOrLoad", "targetRpe", "setPrescriptions", "prescription", "restSeconds")
                    changed != null && changed.length() > 0 && (0 until changed.length()).all { changed.getString(it) in calibrationFields }
                }
                else -> false
            }
        }
        calibrationDelta.forEach { it.put("c16Classification", "EXPECTED_C15_CALIBRATION_DELTA") }
        val materialDeltas = deltas.filter { it.optString("kind") !in setOf("OWNER_ADDED", "OWNER_REMOVED") }
        materialDeltas.filterNot { it in calibrationDelta }.forEach { delta ->
            delta.put("c16Classification", when {
                delta.optString("kind") == "ROW_ADDED" -> "ROW_ADDED_REQUIRES_EXACT_B5_B6_TARGET_AUDIT"
                delta.optString("kind") == "ROW_REMOVED" -> "ROW_REMOVED_REQUIRES_C11_AND_B8_REMOVAL_AUDIT"
                else -> "RESIDUAL_MATERIAL_FIELD_DELTA"
            })
        }
        val residual = materialDeltas.filterNot { it in calibrationDelta }
        return JSONObject().put("case", spec.label).put("route", result.routeDecision.selectedSource.name)
            .put("calibrationOwner", exactOwner?.let(::ownerJson) ?: JSONObject.NULL)
            .put("CONTROL_rows", JSONArray(comparison.control.items.sortedWith(compareBy(
                { it.exerciseStableKey }, { it.selectionRole }, { it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }
            )).map(::rowJson)))
            .put("EXPERIMENTAL_rows", JSONArray(comparison.experimental.items.sortedWith(compareBy(
                { it.exerciseStableKey }, { it.selectionRole }, { it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }
            )).map(::rowJson)))
            .put("B4Strength", comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.STRENGTH }?.let {
                JSONObject().put("strategy", it.strategy.name).put("numericAuthority", it.numericAuthority.name)
                    .put("targetId", "QUALITY:STRENGTH")
            } ?: JSONObject.NULL)
            .put("selectedB5StrengthOwners", JSONArray(comparison.selectionPlan.selectedCandidates.filter {
                "QUALITY:STRENGTH" in it.coveredTargetIds
            }.map { ownerJson(StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)) }))
            .put("B7", JSONObject().put("status", comparison.experimentalReadinessAudit?.status?.name ?: JSONObject.NULL)
                .put("reasons", JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("provenanceClosed", comparison.experimentalReadinessAudit?.changeProvenanceClosed ?: false))
            .put("scopeResolution", JSONObject().put("scope", resolution?.scope?.name ?: JSONObject.NULL)
                .put("status", resolution?.status?.name ?: JSONObject.NULL)
                .put("reasonCodes", JSONArray(resolution?.reasonCodes.orEmpty().sorted()))
                .put("materialOwners", JSONArray(resolution?.materialOwnerIdentities.orEmpty().sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("unattributedOwners", JSONArray(resolution?.unattributedOwnerIdentities.orEmpty().sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("removedOwners", JSONArray(resolution?.removedOwnerIdentities.orEmpty().sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("materialAuthorityIdentities", JSONArray(resolution?.materialAuthorityIdentities.orEmpty().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }, { it.quality.name })).map {
                    JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole).put("quality", it.quality.name)
                })))
            .put("B8", JSONObject().put("scope", comparison.productionCutoverAuthority?.scope?.name ?: JSONObject.NULL)
                .put("status", comparison.productionCutoverAuthority?.status?.name ?: JSONObject.NULL)
                .put("reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                .put("reasonCount", comparison.productionCutoverAuthority?.reasonCodes.orEmpty().size))
            .put("authorizedCalibrationDeltaCount", calibrationDelta.size)
            .put("residualDeltaCount", residual.size)
            .put("deltaLedger", deltas)
            .put("ownerEvidence", ownerEvidence(comparison))
    }

    val cases = selected.map { (spec, result) -> caseJson(spec, requireNotNull(result)) }
    return JSONObject().put("schema", "c16-b8-residual-blocker-census-v1")
        .put("c15MergeMainHead", c15MergeMainHead).put("c16StartHead", c16StartHead)
        .put("cases", JSONArray(cases)).toString(2)
}
