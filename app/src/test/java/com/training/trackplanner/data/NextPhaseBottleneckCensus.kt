package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic test-side rendering of the already-built real CONTROL/EXPERIMENTAL corpus. */
internal object NextPhaseBottleneckCensus {
    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>
    ): String {
        val cases = records.mapNotNull { (spec, result) ->
            val comparison = result?.comparison ?: return@mapNotNull null
            val b7 = comparison.experimentalReadinessAudit
            fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
                .put("stableKey", owner.stableKey)
                .put("selectionRole", owner.selectionRole)
            fun rangeJson(range: StimulusTargetRange?): Any = range?.let {
                JSONObject().put("min", it.min).put("preferred", it.preferred).put("max", it.max)
            } ?: JSONObject.NULL
            fun itemJson(row: ProgramSkeletonItem): JSONObject {
                val value = JSONObject()
                    .put("stableKey", row.exerciseStableKey)
                    .put("selectionRole", row.selectionRole)
                    .put("week", row.weekNumber)
                    .put("day", row.dayOfWeek)
                    .put("order", row.orderIndex)
                    .put("sets", row.setCount)
                    .put("reps", row.reps)
                    .put("seconds", row.seconds)
                    .put("weightKg", row.weightKg)
                    .put("restSeconds", row.restSeconds)
                    .put("weightSource", row.weightSource)
                    .put("prescription", row.prescription)
                    .put("setPrescriptions", JSONArray(row.setPrescriptions.map { set ->
                        JSONObject().put("set", set.setIndex).put("reps", set.reps)
                            .put("seconds", set.seconds).put("weightKg", set.weightKg)
                            .put("rpe", set.targetRpeMin ?: JSONObject.NULL)
                            .put("loadState", set.loadState.name)
                    }))
                row.taskProtocolSemanticsJson?.let { raw ->
                    runCatching { JSONObject(raw) }.getOrNull()?.let { protocol ->
                        value.put("taskProtocol", protocol)
                    }
                }
                return value
            }
            fun rowSignature(row: ProgramSkeletonItem) = listOf(
                row.prescription, row.setCount, row.reps, row.seconds, row.weightKg,
                row.restSeconds, row.weightSource, row.setPrescriptions, row.taskProtocolSemanticsJson
            )
            fun roleFamily(row: ProgramSkeletonItem): String = when {
                !row.taskProtocolSemanticsJson.isNullOrBlank() -> "APPROVED_TASK_PROTOCOL"
                row.selectionRole.startsWith("CANONICAL_STIMULUS_QUALITY_STRENGTH") -> "STRENGTH"
                row.selectionRole.startsWith("CANONICAL_STIMULUS_QUALITY_HYPERTROPHY") -> "HYPERTROPHY"
                row.selectionRole.startsWith("CANONICAL_STIMULUS_QUALITY_POWER") -> "POWER"
                row.selectionRole.startsWith("CANONICAL_STIMULUS_TASK_") -> "TASK_WITHOUT_TASK_PROTOCOL"
                else -> "SUPPORTING_OR_UNCLASSIFIED"
            }
            val ownerOrder = compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole })
            val ownersBefore = comparison.control.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
            val ownersAfter = comparison.experimental.items.groupBy { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }
            val byOwnerWeekBefore = comparison.control.items.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
            }
            val byOwnerWeekAfter = comparison.experimental.items.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
            }
            val ownerWeekChanges = (byOwnerWeekBefore.keys + byOwnerWeekAfter.keys).distinct()
                .sortedWith(compareBy({ it.first.stableKey }, { it.first.selectionRole }, { it.second }))
                .mapNotNull { (owner, week) ->
                    val before = byOwnerWeekBefore[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }))
                    val after = byOwnerWeekAfter[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }))
                    val kind = when {
                        before.isEmpty() -> "ADDED_OWNER"
                        after.isEmpty() -> "REMOVED_OWNER"
                        before.map(::rowSignature) != after.map(::rowSignature) -> "PRESCRIPTION_CHANGE"
                        before.map { it.dayOfWeek to it.orderIndex } != after.map { it.dayOfWeek to it.orderIndex } &&
                            before.map { it.dayOfWeek } != after.map { it.dayOfWeek } -> "PLACEMENT_CHANGE"
                        before.map { it.orderIndex } != after.map { it.orderIndex } -> "ORDER_CHANGE"
                        else -> return@mapNotNull null
                    }
                    JSONObject()
                        .put("owner", ownerJson(owner))
                        .put("week", week)
                        .put("kind", kind)
                        .put("materialFamily", (after.firstOrNull() ?: before.first()).let(::roleFamily))
                        .put("before", JSONArray(before.map(::itemJson)))
                        .put("after", JSONArray(after.map(::itemJson)))
                }
            val addedOwners = (ownersAfter.keys - ownersBefore.keys).sortedWith(ownerOrder)
            val removedOwners = (ownersBefore.keys - ownersAfter.keys).sortedWith(ownerOrder)
            val changedOwners = comparison.sharedOwnerIdentities.filter { owner ->
                ownersBefore.getValue(owner).map(::rowSignature) != ownersAfter.getValue(owner).map(::rowSignature) ||
                    ownersBefore.getValue(owner).map { it.dayOfWeek to it.orderIndex } !=
                    ownersAfter.getValue(owner).map { it.dayOfWeek to it.orderIndex }
            }.sortedWith(ownerOrder)
            val b4Quality = comparison.targetPlan.qualityTargets.sortedBy { it.quality.name }.map { target ->
                JSONObject().put("target", "QUALITY:${target.quality.name}")
                    .put("strategy", target.strategy.name).put("numericAuthority", target.numericAuthority.name)
                    .put("weeklyUnits", rangeJson(target.weeklyDirectUnitsTarget))
                    .put("weeklySessions", rangeJson(target.weeklyDirectSessionsTarget))
                    .put("reasons", JSONArray(target.reasonCodes.sorted()))
            }
            val b4Task = comparison.targetPlan.taskTargets.sortedBy { it.task }.map { target ->
                JSONObject().put("target", "TASK:${target.task}")
                    .put("strategy", target.strategy.name).put("numericAuthority", target.numericAuthority.name)
                    .put("weeklyUnits", rangeJson(target.weeklyDirectUnitsTarget))
                    .put("weeklySessions", rangeJson(target.weeklyDirectSessionsTarget))
                    .put("reasons", JSONArray(target.reasonCodes.sorted()))
            }
            val b5 = comparison.selectionPlan.selectedCandidates.sortedWith(
                compareBy({ it.stableKey }, { it.selectionRole }, { it.primaryTargetId })
            ).map { candidate ->
                JSONObject().put("owner", JSONObject().put("stableKey", candidate.stableKey)
                    .put("selectionRole", candidate.selectionRole))
                    .put("primaryTarget", candidate.primaryTargetId)
                    .put("coveredTargets", JSONArray(candidate.coveredTargetIds.sorted()))
                    .put("selectionReasons", JSONArray(candidate.selectionReasons.sorted()))
                    .put("prescriptionCompatibility", candidate.currentPrescriptionCompatibility)
            }
            val b6 = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty()
                .sortedWith(compareBy({ it.targetId }, { it.owner?.stableKey.orEmpty() }, { it.owner?.selectionRole.orEmpty() }))
                .map { auth ->
                    JSONObject().put("target", auth.targetId).put("quality", auth.quality?.name ?: JSONObject.NULL)
                        .put("owner", auth.owner?.let { JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) } ?: JSONObject.NULL)
                        .put("status", auth.status.name).put("executionAuthority", auth.executionAuthority.name)
                        .put("authorized", auth.authorizedPrescription != null)
                        .put("reasonCodes", JSONArray(auth.reasonCodes.sorted()))
                }
            val taskRows = comparison.experimental.items.filter { !it.taskProtocolSemanticsJson.isNullOrBlank() }
                .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }))
            val taskFrequency = comparison.experimental.taskProtocolFrequencyOutcomes.sortedWith(
                compareBy({ it.protocolId }, { it.week }, { it.stableKey }, { it.selectionRole })
            ).map { row ->
                JSONObject().put("protocolId", row.protocolId).put("stableKey", row.stableKey)
                    .put("selectionRole", row.selectionRole).put("week", row.week)
                    .put("requested", row.requestedExposures).put("placed", row.placedExposures)
                    .put("shortfall", row.shortfall).put("status", row.status.name)
                    .put("authority", row.authority.name).put("reason", row.reasonCode)
            }
            val outcomes = b7?.targetOutcomes.orEmpty().sortedBy { it.targetId }.map { outcome ->
                JSONObject().put("target", outcome.targetId).put("status", outcome.status.name)
                    .put("directlyAffected", outcome.directlyAffected)
                    .put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
                    .put("controlWeeklyUnitsDistance", outcome.controlWeeklyUnitsDistance ?: JSONObject.NULL)
                    .put("experimentalWeeklyUnitsDistance", outcome.experimentalWeeklyUnitsDistance ?: JSONObject.NULL)
                    .put("controlWeeklySessionsDistance", outcome.controlWeeklySessionsDistance ?: JSONObject.NULL)
                    .put("experimentalWeeklySessionsDistance", outcome.experimentalWeeklySessionsDistance ?: JSONObject.NULL)
            }
            fun qualityProgramAudits(audit: StimulusTargetControlProgramAudit?) = JSONArray(
                audit?.qualityAudits.orEmpty().sortedBy { it.quality.name }.map { row ->
                    JSONObject().put("target", "QUALITY:${row.quality.name}")
                        .put("plannedWeeklyDirectUnits", row.plannedWeeklyDirectUnits ?: JSONObject.NULL)
                        .put("unitsStatus", row.weeklyDirectUnitsStatus.name)
                        .put("plannedWeeklyDirectSessions", row.plannedWeeklyDirectSessions ?: JSONObject.NULL)
                        .put("sessionsStatus", row.weeklyDirectSessionsStatus.name)
                        .put("plannedExposureWeekFrequency", row.plannedExposureWeekFrequency ?: JSONObject.NULL)
                        .put("reasons", JSONArray(row.reasonCodes.sorted()))
                }
            )
            fun taskProgramAudits(audit: StimulusTargetControlProgramAudit?) = JSONArray(
                audit?.taskAudits.orEmpty().sortedBy { it.task }.map { row ->
                    JSONObject().put("target", "TASK:${row.task}")
                        .put("plannedDirectUnits", row.plannedDirectUnits ?: JSONObject.NULL)
                        .put("status", row.status.name).put("reasons", JSONArray(row.reasonCodes.sorted()))
                }
            )
            val attributions = b7?.changeAttributions.orEmpty().map { attribution ->
                JSONObject().put("stableKey", attribution.stableKey ?: JSONObject.NULL)
                    .put("selectionRole", attribution.selectionRole ?: JSONObject.NULL)
                    .put("source", attribution.source.name)
                    .put("targetIds", JSONArray(attribution.targetIds.sorted()))
                    .put("reasons", JSONArray(attribution.reasonCodes.sorted()))
                    .put("evidence", JSONArray(attribution.evidenceSources.sorted()))
            }.sortedWith(compareBy({ it.optString("stableKey") }, { it.optString("selectionRole") }, { it.optString("source") }))
            val b7Unmet = b7?.targetOutcomes.orEmpty().filter {
                "TARGET_UNMET" in it.reasonCodes
            }.sortedBy { it.targetId }.map { it.targetId }
            val directlyAffectedUnmet = b7?.targetOutcomes.orEmpty().filter {
                it.directlyAffected && "TARGET_UNMET" in it.reasonCodes
            }.sortedBy { it.targetId }.map { it.targetId }
            val regressions = b7?.targetOutcomes.orEmpty().filter {
                it.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED
            }.sortedBy { it.targetId }.map { it.targetId }
            val scope = result.diagnostics.scopeResolution
            val scopeJson = scope?.let {
                JSONObject().put("resolvedScope", it.scope?.name ?: JSONObject.NULL)
                    .put("status", it.status.name).put("reasons", JSONArray(it.reasonCodes.sorted()))
                    .put("materialQualities", JSONArray(it.materialQualities.map { quality -> quality.name }.sorted()))
                    .put("materialOwners", JSONArray(it.materialOwnerIdentities.sortedWith(ownerOrder).map(::ownerJson)))
                    .put("unknownTargets", JSONArray(it.unknownTargetIds.sorted()))
                    .put("unattributedOwners", JSONArray(it.unattributedOwnerIdentities.sortedWith(ownerOrder).map(::ownerJson)))
                    .put("removedOwners", JSONArray(it.removedOwnerIdentities.sortedWith(ownerOrder).map(::ownerJson)))
                    .put("qualityAuthorityIdentities", JSONArray(it.materialAuthorityIdentities.sortedWith(
                        compareBy({ authority -> authority.stableKey }, { authority -> authority.selectionRole }, { authority -> authority.quality.name })
                    ).map { authority -> JSONObject().put("stableKey", authority.stableKey)
                        .put("selectionRole", authority.selectionRole).put("quality", authority.quality.name) }))
                    .put("taskProtocolIdentities", JSONArray(it.materialTaskProtocolIdentities.sortedWith(
                        compareBy({ identity -> identity.protocolId }, { identity -> identity.stableKey }, { identity -> identity.selectionRole })
                    ).map { identity -> JSONObject().put("protocolId", identity.protocolId)
                        .put("stableKey", identity.stableKey).put("selectionRole", identity.selectionRole)
                        .put("authorizedTasks", JSONArray(identity.authorizedTasks.map { task -> task.name }.sorted())) }))
            } ?: JSONObject.NULL
            val removalEvidence = removedOwners.map { owner ->
                val attribution = b7?.changeAttributions.orEmpty().filter {
                    it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }
                val nonSelection = comparison.nonSelectionProvenance.filter { it.omittedControlOwner == owner }
                val nonSelectionRow = nonSelection.singleOrNull()
                JSONObject().put("owner", ownerJson(owner))
                    .put("b7Attributions", JSONArray(attribution.map { row -> JSONObject()
                        .put("source", row.source.name).put("reasons", JSONArray(row.reasonCodes.sorted()))
                        .put("targets", JSONArray(row.targetIds.sorted())) }))
                    .put("b11Classification", nonSelectionRow?.classification?.name ?: JSONObject.NULL)
                    .put("b11TargetEvidence", JSONArray(nonSelectionRow?.targetEvidence.orEmpty().map { evidence ->
                        val disposition = evidence.disposition
                        JSONObject().put("target", evidence.targetId)
                            .put("classification", evidence.classification.name)
                            .put("dispositionStatus", disposition.status.name)
                            .put("candidateStableKey", disposition.stableKey)
                            .put("candidateRole", disposition.canonicalSelectionRole)
                            .put("directTargetCandidate", disposition.directTargetCandidate)
                            .put("selectionRequired", disposition.selectionRequired)
                            .put("reasons", JSONArray(disposition.reasons.map { it.name }.sorted()))
                            .put("selectedInstead", disposition.selectedInstead?.let(::ownerJson) ?: JSONObject.NULL)
                            .put("targetCoveredBySelectedOwner", disposition.targetCoveredBySelectedOwner)
                    }))
                    .put("removalProvenanceClosed", attribution.isNotEmpty() && attribution.none {
                        it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                            it.source == StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
                    })
            }
            val qualityMaterial = comparison.experimental.items.filter { roleFamily(it) in setOf("STRENGTH", "HYPERTROPHY", "POWER") }
            val taskOwners = taskRows.map { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }.toSet()
            val qualityOwners = qualityMaterial.map { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) }.toSet()
            val successfulQualityB6Owners = comparison.prescriptionAuthorizationPlan?.authorizations.orEmpty().filter { auth ->
                val owner = auth.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                owner != null && auth.authorizedPrescription != null &&
                    auth.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED &&
                    owner in qualityOwners
            }.map { StimulusPrescriptionOwnerIdentity(requireNotNull(it.owner).stableKey, requireNotNull(it.owner).selectionRole) }.toSet()
            val relevantAttributions = b7?.changeAttributions.orEmpty().filter {
                it.stableKey != null && it.selectionRole != null &&
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) in (taskOwners + qualityOwners)
            }
            val taskProtocolByOwner = taskRows.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
            }
            val taskRowsHaveExactProtocolAuthority = taskOwners.isNotEmpty() && taskOwners.all { owner ->
                val rows = taskProtocolByOwner[owner].orEmpty()
                rows.isNotEmpty() && rows.all { row ->
                    val protocol = row.taskProtocolSemanticsJson?.let { raw ->
                        runCatching { JSONObject(raw) }.getOrNull()
                    } ?: return@all false
                    val transferEvidence = protocol.optJSONObject("transferEvidence")
                    val authorizedTasks = protocol.optJSONArray("authorizedTasks")?.strings().orEmpty()
                    protocol.optString("status") == "AUTHORIZED_APPROVED_TASK_PROTOCOL" &&
                        protocol.optString("policyProvenance") == "USER_APPROVED_PROJECT_POLICY" &&
                        protocol.optString("stableKey") == owner.stableKey &&
                        protocol.optString("selectionRole") == owner.selectionRole &&
                        protocol.optString("protocolId").isNotBlank() &&
                        protocol.optInt("weeklyExposures", 0) > 0 && authorizedTasks.isNotEmpty() &&
                        authorizedTasks.all { task -> transferEvidence?.optString(task) == "DIRECT" }
                }
            }
            val taskOwnersHaveB5Selection = taskOwners.all { owner ->
                comparison.selectionPlan.selectedCandidates.any {
                    it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }
            }
            val allMaterialOwnersHaveB6 = taskRowsHaveExactProtocolAuthority && taskOwnersHaveB5Selection &&
                qualityOwners.isNotEmpty() && qualityOwners.all { it in successfulQualityB6Owners }
            val closedAttributionSources = setOf(
                StimulusExperimentalChangeAttributionSource.B6_APPROVED_TASK_PROTOCOL,
                StimulusExperimentalChangeAttributionSource.B6_EXISTING_OWNER_PRESCRIPTION,
                StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
                StimulusExperimentalChangeAttributionSource.B6_COLD_START_USER_CALIBRATION,
                StimulusExperimentalChangeAttributionSource.B5_SELECTED_IDENTITY,
            )
            val ownerAttributions = (taskOwners + qualityOwners).map { owner ->
                relevantAttributions.filter { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            }
            val allMaterialAttributionsClosed = ownerAttributions.isNotEmpty() && ownerAttributions.all { rows ->
                rows.isNotEmpty() && rows.all { it.source in closedAttributionSources }
            }
            val mixedOnlyCandidate = taskOwners.isNotEmpty() && qualityOwners.isNotEmpty() &&
                taskRowsHaveExactProtocolAuthority && taskOwnersHaveB5Selection &&
                allMaterialOwnersHaveB6 && allMaterialAttributionsClosed &&
                removedOwners.isEmpty() && b7?.status == StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
                directlyAffectedUnmet.isEmpty() && regressions.isEmpty() && b7.collateralRegressionFree

            JSONObject()
                .put("case", spec.label)
                .put("route", result.routeDecision.selectedSource.name)
                .put("routeReasons", JSONArray(result.routeDecision.reasonCodes.sorted()))
                .put("productionProgramIsControl", result.program === comparison.control)
                .put("productionProgramIsExperimental", result.program === comparison.experimental)
                .put("builds", JSONObject().put("control", result.buildCounts.controlBuilds)
                    .put("experimental", result.buildCounts.experimentalBuilds)
                    .put("total", result.buildCounts.totalBuildInvocations).put("third", result.buildCounts.thirdBuilds))
                .put("b4", JSONObject().put("qualityTargets", JSONArray(b4Quality)).put("taskTargets", JSONArray(b4Task))
                    .put("unresolved", JSONArray(comparison.targetPlan.unresolved.sorted())))
                .put("b5SelectedOwners", JSONArray(b5))
                .put("b6QualityAuthorities", JSONArray(b6))
                .put("taskMaterialRows", JSONArray(taskRows.map(::itemJson)))
                .put("taskProtocolFrequency", JSONArray(taskFrequency))
                .put("materialDeltas", JSONArray(ownerWeekChanges))
                .put("addedOwners", JSONArray(addedOwners.map(::ownerJson)))
                .put("removedOwners", JSONArray(removedOwners.map(::ownerJson)))
                .put("changedSharedOwners", JSONArray(changedOwners.map(::ownerJson)))
                .put("materialOwnerFamilies", JSONObject().put("taskProtocolOwners", taskOwners.size)
                    .put("strengthOwners", qualityOwners.count { owner -> qualityMaterial.any {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole && roleFamily(it) == "STRENGTH"
                    } })
                    .put("hypertrophyOwners", qualityOwners.count { owner -> qualityMaterial.any {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole && roleFamily(it) == "HYPERTROPHY"
                    } })
                    .put("powerOwners", qualityOwners.count { owner -> qualityMaterial.any {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole && roleFamily(it) == "POWER"
                    } })
                    .put("otherChangedOwners", (addedOwners + changedOwners).count { owner ->
                        (ownersAfter[owner] ?: emptyList()).firstOrNull()?.let(::roleFamily) == "SUPPORTING_OR_UNCLASSIFIED"
                    }))
                .put("b7", JSONObject().put("status", b7?.status?.name ?: "MISSING")
                    .put("reasons", JSONArray(b7?.reasonCodes.orEmpty().sorted()))
                    .put("changeProvenanceClosed", b7?.changeProvenanceClosed ?: false)
                    .put("materializationIntegrityPassed", b7?.materializationIntegrityPassed ?: false)
                    .put("collateralRegressionFree", b7?.collateralRegressionFree ?: false)
                    .put("targetOutcomes", JSONArray(outcomes))
                    .put("affectedUnmetTargets", JSONArray(directlyAffectedUnmet))
                    .put("allUnmetTargetOutcomes", JSONArray(b7Unmet))
                    .put("regressedTargets", JSONArray(regressions))
                    .put("changeAttributions", JSONArray(attributions))
                    .put("unclosedAttributions", JSONArray(attributions.filter {
                        it.optString("source") in setOf("UNEXPLAINED", "INCONCLUSIVE_DISPLACEMENT")
                    }))
                    .put("removedOwnerEvidence", JSONArray(removalEvidence)))
                .put("programTargetAudits", JSONObject()
                    .put("controlQuality", qualityProgramAudits(comparison.controlAudit))
                    .put("experimentalQuality", qualityProgramAudits(comparison.experimentalAudit))
                    .put("controlTasks", taskProgramAudits(comparison.controlAudit))
                    .put("experimentalTasks", taskProgramAudits(comparison.experimentalAudit)))
                .put("materialScope", scopeJson)
                .put("b8", JSONObject().put("scope", comparison.productionCutoverAuthority?.scope?.name ?: JSONObject.NULL)
                    .put("status", comparison.productionCutoverAuthority?.status?.name ?: "MISSING")
                    .put("reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted())))
                .put("diagnostics", JSONObject().put("primaryFallbackStage", result.diagnostics.primaryFallbackStage?.name ?: JSONObject.NULL)
                    .put("secondaryReasons", JSONArray(result.diagnostics.secondaryReasonCodes.sorted())))
                .put("mixedStrengthTaskOnlyFailureCandidate", mixedOnlyCandidate)
        }.sortedBy { it.getString("case") }

        val controlCases = cases.filter { it.getString("route") == StimulusProductionProgramSource.CONTROL.name }
        fun reasonCaseMap(reasonSelector: (JSONObject) -> List<String>) = sortedMapOf<String, List<String>>().apply {
            cases.forEach { row -> reasonSelector(row).distinct().forEach { reason ->
                put(reason, (get(reason).orEmpty() + row.getString("case")).sorted())
            } }
        }
        fun objectCounts(values: Map<String, Int>) = JSONObject().also { out -> values.toSortedMap().forEach(out::put) }
        val b7All = cases.flatMap { row -> row.getJSONObject("b7").getJSONArray("reasons").strings() }
            .groupingBy { it }.eachCount()
        val b7Control = controlCases.flatMap { row -> row.getJSONObject("b7").getJSONArray("reasons").strings() }
            .groupingBy { it }.eachCount()
        val affectedUnmet = cases.flatMap { row -> row.getJSONObject("b7").getJSONArray("affectedUnmetTargets").strings() }
            .groupingBy { it }.eachCount()
        val unclosedReasons = cases.flatMap { row -> row.getJSONObject("b7").getJSONArray("unclosedAttributions").let { attrs ->
            (0 until attrs.length()).flatMap { attrs.getJSONObject(it).getJSONArray("reasons").strings() }
        } }.groupingBy { it }.eachCount()
        val unclosedReasonCases = reasonCaseMap { row ->
            val attrs = row.getJSONObject("b7").getJSONArray("unclosedAttributions")
            (0 until attrs.length()).flatMap { attrs.getJSONObject(it).getJSONArray("reasons").strings() }
        }
        val deltaKindCounts = cases.flatMap { row -> row.getJSONArray("materialDeltas").let { deltas ->
            (0 until deltas.length()).map { deltas.getJSONObject(it).getString("kind") }
        } }.groupingBy { it }.eachCount()
        val routes = cases.groupingBy { it.getString("route") }.eachCount()
        val removedControlOwnerCount = controlCases.sumOf { row -> row.getJSONArray("removedOwners").length() }
        val unexecutableQualityAddedRows = controlCases.flatMap { case ->
            val authorities = case.getJSONArray("b6QualityAuthorities")
            case.getJSONArray("materialDeltas").let { deltas -> (0 until deltas.length()).mapNotNull { index ->
                val delta = deltas.getJSONObject(index)
                if (delta.getString("kind") != "ADDED_OWNER" ||
                    delta.optString("materialFamily") !in setOf("STRENGTH", "HYPERTROPHY", "POWER")) return@mapNotNull null
                val owner = delta.getJSONObject("owner")
                val matching = (0 until authorities.length()).map { authorities.getJSONObject(it) }.firstOrNull { auth ->
                    val authOwner = auth.optJSONObject("owner") ?: return@firstOrNull false
                    authOwner.optString("stableKey") == owner.optString("stableKey") &&
                        authOwner.optString("selectionRole") == owner.optString("selectionRole")
                }
                if (matching?.optBoolean("authorized") == true) return@mapNotNull null
                JSONObject().put("case", case.getString("case")).put("week", delta.getInt("week"))
                    .put("owner", owner)
                    .put("b6Status", matching?.optString("status") ?: "MISSING")
                    .put("b6Reasons", matching?.optJSONArray("reasonCodes") ?: JSONArray())
            } }
        }
        val canonicalReplacementUnclosedRows = controlCases.flatMap { case ->
            case.getJSONObject("b7").getJSONArray("removedOwnerEvidence").let { rows -> (0 until rows.length()).mapNotNull { index ->
                val row = rows.getJSONObject(index)
                if (row.optString("b11Classification") == "CANONICAL_REPLACEMENT" &&
                    !row.optBoolean("removalProvenanceClosed")) {
                    JSONObject().put("case", case.getString("case")).put("owner", row.getJSONObject("owner"))
                        .put("b11TargetEvidence", row.getJSONArray("b11TargetEvidence"))
                } else null
            } }
        }
        val taskReplacementsWithExactProtocol = canonicalReplacementUnclosedRows.flatMap { removal ->
            val case = controlCases.single { it.getString("case") == removal.getString("case") }
            val owner = removal.getJSONObject("owner")
            val evidence = removal.getJSONArray("b11TargetEvidence")
            (0 until evidence.length()).mapNotNull { index ->
                val target = evidence.getJSONObject(index)
                if (target.optString("classification") != "CANONICAL_REPLACEMENT" ||
                    !target.optString("target").startsWith("TASK:")) return@mapNotNull null
                val replacementRole = target.optString("candidateRole")
                val matches = case.getJSONArray("taskMaterialRows").let { rows -> (0 until rows.length()).any { rowIndex ->
                    val row = rows.getJSONObject(rowIndex)
                    val protocol = row.optJSONObject("taskProtocol") ?: return@any false
                    row.optString("stableKey") == owner.optString("stableKey") &&
                        row.optString("selectionRole") == replacementRole &&
                        protocol.optString("status") == "AUTHORIZED_APPROVED_TASK_PROTOCOL" &&
                        protocol.optString("policyProvenance") == "USER_APPROVED_PROJECT_POLICY" &&
                        protocol.optString("stableKey") == owner.optString("stableKey") &&
                        protocol.optString("selectionRole") == replacementRole &&
                        protocol.optString("primaryTask") == target.optString("target").removePrefix("TASK:")
                } }
                if (matches) JSONObject().put("case", removal.getString("case")).put("removedOwner", owner)
                    .put("target", target.optString("target")).put("replacementRole", replacementRole)
                else null
            }
        }
        val reasonCases = JSONObject()
            .put("b7", JSONObject().put("byReason", objectCounts(reasonCaseMap {
                it.getJSONObject("b7").getJSONArray("reasons").strings()
            }.mapValues { it.value.size }))
                .put("caseNamesByReason", JSONObject().also { obj ->
                    reasonCaseMap { it.getJSONObject("b7").getJSONArray("reasons").strings() }.forEach { (key, value) ->
                        obj.put(key, JSONArray(value))
                    }
                }))
            .put("scope", JSONObject().also { obj ->
                val mapping = reasonCaseMap { it.optJSONObject("materialScope")?.getJSONArray("reasons")?.strings().orEmpty() }
                mapping.forEach { (key, value) -> obj.put(key, JSONArray(value)) }
            })
            .put("b8", JSONObject().also { obj ->
                val mapping = reasonCaseMap { it.getJSONObject("b8").getJSONArray("reasons").strings() }
                mapping.forEach { (key, value) -> obj.put(key, JSONArray(value)) }
            })
        val summary = JSONObject()
            .put("generatedCases", cases.size)
            .put("controlCases", controlCases.size)
            .put("routes", objectCounts(routes))
            .put("b7ReasonOccurrencesAllGenerated", objectCounts(b7All))
            .put("b7ReasonOccurrencesControlOnly", objectCounts(b7Control))
            .put("b7CaseCountsAllGenerated", JSONObject().put("changeProvenanceUnclosed", b7All["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                .put("affectedTargetRemainsUnmet", b7All["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                .put("targetRegressed", b7All["TARGET_REGRESSED"] ?: 0))
            .put("unclosedAttributionReasonOccurrences", objectCounts(unclosedReasons))
            .put("unclosedAttributionReasonCaseCounts", objectCounts(unclosedReasonCases.mapValues { it.value.size }))
            .put("unclosedAttributionCaseNames", JSONObject().also { obj ->
                unclosedReasonCases.forEach { (key, value) -> obj.put(key, JSONArray(value)) }
            })
            .put("affectedUnmetTargetCounts", objectCounts(affectedUnmet))
            .put("materialDeltaKinds", objectCounts(deltaKindCounts))
            .put("controlRemovedOwnerIdentityCount", removedControlOwnerCount)
            .put("casesWithMixedStrengthAndTaskMaterialOnlyBlockers", controlCases.count {
                it.getBoolean("mixedStrengthTaskOnlyFailureCandidate")
            })
            .put("mixedStrengthTaskOnlyCaseNames", JSONArray(controlCases.filter {
                it.getBoolean("mixedStrengthTaskOnlyFailureCandidate")
            }.map { it.getString("case") }))
            .put("powerMaterialRows", cases.sumOf { row ->
                row.getJSONArray("materialDeltas").let { deltas -> (0 until deltas.length()).count {
                    deltas.getJSONObject(it).optString("materialFamily") == "POWER"
                } }
            })
            .put("jumpLandingMaterialRows", cases.sumOf { it.getJSONArray("taskMaterialRows").let { rows ->
                (0 until rows.length()).count { idx ->
                    val protocol = rows.getJSONObject(idx).optJSONObject("taskProtocol") ?: return@count false
                    protocol.getJSONArray("authorizedTasks").strings().contains("JUMP_LANDING")
                }
            } })
            .put("qualityAddedOwnerWeeksWithoutAuthorizedB6", unexecutableQualityAddedRows.size)
            .put("qualityAddedOwnerWeeksWithoutAuthorizedB6Cases", JSONArray(unexecutableQualityAddedRows.map {
                it.getString("case")
            }.distinct().sorted()))
            .put("qualityAddedOwnerWeeksWithoutAuthorizedB6Evidence", JSONArray(unexecutableQualityAddedRows))
            .put("b11CanonicalReplacementButB7UnclosedOwnerRows", canonicalReplacementUnclosedRows.size)
            .put("taskRoleReplacementRowsWithExactApprovedTaskB6", JSONArray(taskReplacementsWithExactProtocol))
            .put("buildAccounting", JSONObject().put("controlPerGeneratedCase", 1)
                .put("experimentalPerGeneratedCase", 1).put("totalPerGeneratedCase", 2).put("thirdPerGeneratedCase", 0))
        return JSONObject()
            .put("phase", "NEXT_PHASE_BOTTLENECK_AUDIT")
            .put("source", "REAL_ROOM_SERVICE_CORPUS_FROM_STIMULUS_PRODUCTION_COVERAGE_AUDIT_TEST")
            .put("cases", JSONArray(cases))
            .put("controlCaseNames", JSONArray(controlCases.map { it.getString("case") }))
            .put("summary", summary)
            .put("reasonCaseNames", reasonCases)
            .put("unclosedAttributionReasonOccurrences", objectCounts(unclosedReasons))
            .put("affectedUnmetTargetCounts", objectCounts(affectedUnmet))
            .toString() + "\n"
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
