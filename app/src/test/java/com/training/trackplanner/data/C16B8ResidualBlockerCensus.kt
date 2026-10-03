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
        .put("seconds", set.seconds).put("targetRpeMin", set.targetRpeMin ?: JSONObject.NULL)
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

    fun deltaLedger(comparison: StimulusSelectionProgramComparison): List<JSONObject> {
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
        return deltas.sortedWith(compareBy<JSONObject>({ it.getJSONObject("owner").getString("stableKey") },
            { it.getJSONObject("owner").getString("selectionRole") }, { it.getString("kind") },
            { it.optJSONObject("before")?.optInt("week") ?: it.optJSONObject("after")?.optInt("week") ?: 0 },
            { it.optJSONObject("before")?.optInt("day") ?: it.optJSONObject("after")?.optInt("day") ?: 0 },
            { it.optJSONObject("before")?.optInt("order") ?: it.optJSONObject("after")?.optInt("order") ?: 0 }))
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

    fun approvedCalibrationRoleReplacement(
        comparison: StimulusSelectionProgramComparison,
        delta: JSONObject,
        calibrationOwner: StimulusPrescriptionOwnerIdentity?
    ): Boolean {
        if (calibrationOwner == null || delta.optString("kind") != "ROW_REMOVED") return false
        val before = delta.optJSONObject("before") ?: return false
        val omittedOwner = StimulusPrescriptionOwnerIdentity(
            before.getJSONObject("owner").getString("stableKey"),
            before.getJSONObject("owner").getString("selectionRole")
        )
        if (omittedOwner.stableKey != calibrationOwner.stableKey || omittedOwner == calibrationOwner) return false
        val hasExactReplacement = comparison.nonSelectionProvenance.any { omission ->
            omission.omittedControlOwner == omittedOwner &&
                omission.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT &&
                omission.targetEvidence.any { evidence ->
                    evidence.targetId == "QUALITY:STRENGTH" &&
                        evidence.classification == StimulusNonSelectionClassification.CANONICAL_REPLACEMENT &&
                        evidence.disposition.status == StimulusCandidateDispositionStatus.SELECTED
                }
        }
        val exactB5 = comparison.selectionPlan.selectedCandidates.any {
            it.stableKey == calibrationOwner.stableKey && it.selectionRole == calibrationOwner.selectionRole &&
                "QUALITY:STRENGTH" in it.coveredTargetIds
        }
        val exactB6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().any {
            it.owner?.let { owner -> owner.stableKey == calibrationOwner.stableKey && owner.selectionRole == calibrationOwner.selectionRole } == true &&
                it.quality == TrainableQuality.STRENGTH &&
                it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                it.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT
        }
        val fullyMaterialized = comparison.prescriptionMaterializationAudits.any {
            it.owner?.let { owner -> owner.stableKey == calibrationOwner.stableKey && owner.selectionRole == calibrationOwner.selectionRole } == true &&
                it.quality == TrainableQuality.STRENGTH && it.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED
        }
        val exactReplacementAttribution = comparison.experimentalReadinessAudit?.changeAttributions.orEmpty().any {
            it.source == StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY &&
                it.stableKey == omittedOwner.stableKey && it.selectionRole == omittedOwner.selectionRole &&
                "QUALITY:STRENGTH" in it.targetIds && "B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE" in it.reasonCodes
        }
        return hasExactReplacement && exactB5 && exactB6 && fullyMaterialized && exactReplacementAttribution
    }

    fun actualBlockerClassification(
        comparison: StimulusSelectionProgramComparison,
        residual: List<JSONObject>,
        ownerEvidence: JSONArray
    ): JSONObject {
        val b8Rejected = comparison.productionCutoverAuthority?.status !=
            StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER
        fun evidence(owner: StimulusPrescriptionOwnerIdentity): JSONObject? =
            (0 until ownerEvidence.length()).asSequence().map { ownerEvidence.getJSONObject(it) }.firstOrNull {
                val found = it.getJSONObject("owner")
                found.getString("stableKey") == owner.stableKey && found.getString("selectionRole") == owner.selectionRole
            }
        val placementOwners = residual.filter { it.optString("kind") == "PLACEMENT_OR_ROW_CHANGE" }
            .mapNotNull { delta -> delta.optJSONObject("owner")?.let { StimulusPrescriptionOwnerIdentity(it.getString("stableKey"), it.getString("selectionRole")) } }
            .distinct().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
            .filter { owner -> evidence(owner)?.let { !it.optBoolean("B5Selected") && it.getJSONArray("B6").length() == 0 } == true }
        val powerAdditions = residual.filter { it.optString("kind") == "ROW_ADDED" }.mapNotNull { delta ->
            val ownerJson = delta.optJSONObject("owner") ?: return@mapNotNull null
            val owner = StimulusPrescriptionOwnerIdentity(ownerJson.getString("stableKey"), ownerJson.getString("selectionRole"))
            val ownerInfo = evidence(owner) ?: return@mapNotNull null
            val powerB4 = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.POWER }
            val selectedForPower = ownerInfo.getJSONArray("B5Targets").let { targets ->
                (0 until targets.length()).any { targets.getString(it) == "QUALITY:POWER" }
            }
            val hasPowerB6 = ownerInfo.getJSONArray("B6").let { authorities ->
                (0 until authorities.length()).any { authorities.getJSONObject(it).optString("quality") == "POWER" }
            }
            if (selectedForPower && !hasPowerB6 && powerB4 != null &&
                powerB4.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) owner else null
        }.distinct().sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
        val calibrationAuthorization = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().firstOrNull {
            it.quality == TrainableQuality.STRENGTH && it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        }
        val calibrationOwner = calibrationAuthorization?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val calibrationMaterialization = calibrationOwner?.let { owner -> comparison.prescriptionMaterializationAudits.firstOrNull {
            it.owner?.let { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole } == true &&
                it.quality == TrainableQuality.STRENGTH
        } }
        val strengthTarget = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.STRENGTH }
        val scope = StimulusProductionMaterialScopeResolver().resolveDetailed(comparison)
        val targetOutcomes = comparison.experimentalReadinessAudit?.targetOutcomes.orEmpty()
        val targetOutcomesAccepted = targetOutcomes.isNotEmpty() && targetOutcomes.all {
            it.status in setOf(StimulusExperimentalTargetOutcomeStatus.IMPROVED,
                StimulusExperimentalTargetOutcomeStatus.UNCHANGED, StimulusExperimentalTargetOutcomeStatus.NOT_APPLICABLE)
        } && targetOutcomes.any { it.targetId == "QUALITY:STRENGTH" && it.status in setOf(
            StimulusExperimentalTargetOutcomeStatus.IMPROVED, StimulusExperimentalTargetOutcomeStatus.UNCHANGED) }
        val strictFalseNegativeProof = b8Rejected && residual.isEmpty() && placementOwners.isEmpty() && powerAdditions.isEmpty() &&
            strengthTarget?.numericAuthority in setOf(StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE) &&
            calibrationOwner != null && comparison.selectionPlan.selectedCandidates.any {
                it.stableKey == calibrationOwner.stableKey && it.selectionRole == calibrationOwner.selectionRole &&
                    "QUALITY:STRENGTH" in it.coveredTargetIds
            } && calibrationAuthorization?.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT &&
            calibrationAuthorization?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
            calibrationMaterialization?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED &&
            targetOutcomesAccepted && comparison.experimentalReadinessAudit?.collateralRegressionFree == true &&
            comparison.experimentalReadinessAudit?.changeProvenanceClosed == true &&
            scope?.scope == StimulusProductionCutoverScope.STRENGTH_CALIBRATION_V1 &&
            comparison.control.weekDaySchedule == comparison.experimental.weekDaySchedule

        val root = when {
            !b8Rejected && residual.isEmpty() -> JSONObject().put("type", "EXPECTED_CALIBRATION_CUTOVER")
                .put("owners", JSONArray()).put("finding", "The exact calibration delta is the only material change and B8 authorized the calibration route.")
            powerAdditions.isNotEmpty() -> JSONObject().put("type", "UNAUTHORIZED_NON_STRENGTH_CHANGE")
                .put("owners", JSONArray(powerAdditions.map(::ownerJson)))
                .put("finding", "B5 selected a POWER owner for a DIRECTION_ONLY B4 target, but no exact executable B6 POWER authority exists; the generated POWER rows are outside Strength Calibration authority.")
            placementOwners.isNotEmpty() -> JSONObject().put("type", "UNAUTHORIZED_PLACEMENT_CHANGE")
                .put("owners", JSONArray(placementOwners.map(::ownerJson)))
                .put("finding", "The exact shared owner placement differs from CONTROL, has no exact B5 selection authority or B6 prescription authority, and has no B7 material attribution. C10 records EXP placement assignment (and accepted rebalancer moves where present), but no target-governed causal edge.")
            comparison.experimentalReadinessAudit?.changeProvenanceClosed != true -> JSONObject().put("type", "PROVENANCE_ONLY_GAP")
                .put("owners", JSONArray()).put("finding", "No independently attributable residual owner mutation was classified.")
            strictFalseNegativeProof -> JSONObject().put("type", "POTENTIAL_B8_FALSE_NEGATIVE")
                .put("owners", JSONArray()).put("finding", "All C16 material and authority predicates are closed but B8 rejected the calibration route.")
            residual.isEmpty() -> JSONObject().put("type", "B8_REJECTION_WITH_MISSING_CUTOVER_PREDICATE")
                .put("owners", JSONArray()).put("finding", "The delta ledger is empty, but at least one independent authority, outcome, provenance, scope, or schedule predicate is not proven.")
            else -> JSONObject().put("type", "OTHER_RESIDUAL_MATERIAL_CHANGE")
                .put("owners", JSONArray()).put("finding", "Residual material changes remain and require owner-level review.")
        }
        val secondary = JSONArray()
        if (placementOwners.isNotEmpty() && root.optString("type") != "UNAUTHORIZED_PLACEMENT_CHANGE") {
            secondary.put(JSONObject().put("type", "UNAUTHORIZED_PLACEMENT_CHANGE").put("owners", JSONArray(placementOwners.map(::ownerJson))))
        }
        if (scope?.reasonCodes?.contains("MATERIAL_PROVENANCE_PARTIAL") == true) {
            secondary.put(JSONObject().put("type", "MATERIAL_SCOPE_PROVENANCE_GAP")
                .put("owners", JSONArray(scope.unattributedOwnerIdentities.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map(::ownerJson)))
                .put("finding", "C10 origin events exist for the final EXP placements, but B7 has no exact material target attribution for these changed shared owners; B8 therefore cannot resolve a calibration-only scope."))
        }
        val expectedCalibrationNotes = JSONArray()
        if (comparison.removedOwnerIdentities.any { removed -> approvedCalibrationRoleReplacement(comparison,
                JSONObject().put("kind", "ROW_REMOVED").put("before", JSONObject().put("owner", ownerJson(removed))),
                comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().firstOrNull {
                    it.quality == TrainableQuality.STRENGTH && it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                }?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            ) }) {
            expectedCalibrationNotes.put(JSONObject().put("type", "CANONICAL_ROLE_REPLACEMENT")
                .put("finding", "Exact C11 canonical replacement for Strength is authorized; this is an expected C15 delta, not a residual blocker."))
        }
        if (comparison.experimentalReadinessAudit?.changeProvenanceClosed == false) {
            secondary.put(JSONObject().put("type", "PROVENANCE_PARTIAL").put("finding", "B7 does not attribute all residual changed owners; this is a downstream manifestation of the owner/placement gaps above."))
        }
        return JSONObject().put("primaryRootBlocker", root)
            .put("secondaryBlockers", secondary)
            .put("expectedCalibrationNotes", expectedCalibrationNotes)
            .put("scopeResolutionStatus", scope?.status?.name ?: JSONObject.NULL)
            .put("scopeResolutionReasons", JSONArray(scope?.reasonCodes.orEmpty().sorted()))
            .put("B8Reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
            .put("primaryDisposition", when (root.optString("type")) {
                "EXPECTED_CALIBRATION_CUTOVER" -> "EXPECTED_CALIBRATION_CUTOVER"
                "POTENTIAL_B8_FALSE_NEGATIVE" -> "POTENTIAL_B8_FALSE_NEGATIVE"
                "PROVENANCE_ONLY_GAP" -> "AUDIT_OR_PROVENANCE_GAP"
                "UNAUTHORIZED_NON_STRENGTH_CHANGE" -> "CURRENT_SCOPE_LIMITATION"
                else -> "TRUE_SAFETY_BLOCK"
            })
            .put("potentialFalseNegativeEligible", root.optString("type") == "POTENTIAL_B8_FALSE_NEGATIVE" && residual.isEmpty() &&
                comparison.experimentalReadinessAudit?.changeProvenanceClosed == true && placementOwners.isEmpty() && powerAdditions.isEmpty())
    }

    fun caseJson(spec: StimulusProductionCoverageAuditTest.CoverageSpec, result: StimulusProductionGenerationResult): JSONObject {
        val comparison = requireNotNull(result.comparison)
        val resolution = result.diagnostics.scopeResolution
        val coldStartAuthority = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().singleOrNull {
            it.quality == TrainableQuality.STRENGTH && it.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        }
        val exactOwner = coldStartAuthority?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val deltas = deltaLedger(comparison)
        val evidence = ownerEvidence(comparison)
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
                        actual.optDouble("targetRpeMin", Double.NaN) == (expected.targetRpeMin ?: Double.NaN) &&
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
        val approvedRoleReplacementDeltas = deltas.filter { delta ->
            approvedCalibrationRoleReplacement(comparison, delta, exactOwner)
        }
        approvedRoleReplacementDeltas.forEach {
            it.put("c16Classification", "EXPECTED_C15_CALIBRATION_DELTA")
                .put("calibrationDeltaBasis", "EXACT_C11_CANONICAL_ROLE_REPLACEMENT")
        }
        val materialDeltas = deltas.filter { it.optString("kind") !in setOf("OWNER_ADDED", "OWNER_REMOVED") }
        materialDeltas.filterNot { it in calibrationDelta || it in approvedRoleReplacementDeltas }.forEach { delta ->
            delta.put("c16Classification", when {
                delta.optString("kind") == "ROW_ADDED" -> "ROW_ADDED_REQUIRES_EXACT_B5_B6_TARGET_AUDIT"
                delta.optString("kind") == "ROW_REMOVED" -> "ROW_REMOVED_REQUIRES_C11_AND_B8_REMOVAL_AUDIT"
                else -> "RESIDUAL_MATERIAL_FIELD_DELTA"
            })
        }
        val allAuthorizedCalibrationDeltas = calibrationDelta + approvedRoleReplacementDeltas
        val residual = materialDeltas.filterNot { it in allAuthorizedCalibrationDeltas }
        val blockerClassification = actualBlockerClassification(comparison, residual, evidence)
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
            .put("B4Targets", JSONArray(comparison.targetPlan.qualityTargets.sortedBy { it.quality.name }.map {
                JSONObject().put("targetId", "QUALITY:${it.quality.name}").put("strategy", it.strategy.name)
                    .put("numericAuthority", it.numericAuthority.name).put("targetUnits", it.weeklyDirectUnitsTarget?.let { range ->
                        JSONObject().put("min", range.min).put("preferred", range.preferred).put("max", range.max)
                    } ?: JSONObject.NULL)
            }))
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
            .put("authorizedCalibrationDeltaCount", allAuthorizedCalibrationDeltas.size)
            .put("residualDeltaCount", residual.size)
            .put("rootClassification", blockerClassification)
            .put("deltaLedger", JSONArray(deltas))
            .put("ownerEvidence", evidence)
    }

    val cases = selected.map { (spec, result) -> caseJson(spec, requireNotNull(result)) }
    return JSONObject().put("schema", "c16-b8-residual-blocker-census-v1")
        .put("c15MergeMainHead", c15MergeMainHead).put("c16StartHead", c16StartHead)
        .put("cases", JSONArray(cases)).toString(2)
}
