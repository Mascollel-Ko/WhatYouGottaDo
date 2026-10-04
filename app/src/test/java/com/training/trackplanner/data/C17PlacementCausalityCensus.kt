package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Test-only placement audit. CONTROL is read solely as the counterfactual's prior placement. */
internal object C17PlacementCausalityCensus {
    private val targetCases = setOf("persona0_mixed", "persona0_reviewed", "persona3_reviewed", "persona4_mixed")

    private data class RowKey(val week: Int, val stableKey: String, val role: String)
    private data class Move(
        val caseId: String,
        val key: RowKey,
        val control: ProgramSkeletonItem,
        val experimental: ProgramSkeletonItem
    )
    private data class Counterfactual(
        val program: GeneratedProgramSkeleton,
        val violations: List<String>,
        val details: JSONObject
    ) {
        val valid: Boolean get() = violations.isEmpty()
    }

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult?>>,
        contexts: Map<String, PreparedCanonicalGenerationContext>,
        c16Census: String,
        c16MergeSha: String,
        c17StartSha: String
    ): String {
        val priorCensus = JSONObject(c16Census)
        val priorCases = priorCensus.getJSONArray("cases")
        val selected = records.filter { it.first.label in targetCases }.associateBy { it.first.label }
        require(selected.keys == targetCases) { "C17 requires exactly the four C16 blocked cases" }
        fun comparisonFor(caseId: String) = c20PreActivationComparison(
            requireNotNull(selected[caseId]?.second) { "$caseId missing production result" }
        )
        val outputCases = JSONArray()
        val allMoves = mutableListOf<Move>()

        targetCases.sorted().forEach { caseId ->
            val record = selected.getValue(caseId)
            val result = requireNotNull(record.second) { "$caseId was unexpectedly preflight-rejected" }
            // C20 may update final placements after the canonical EXP build. C17 is a historical
            // audit of that pre-activation builder output, so keep its regression census pinned
            // to the exact rows captured before C20 activation.
            val comparison = c20PreActivationComparison(result)
            val context = requireNotNull(contexts[caseId]) { "$caseId missing canonical placement context" }
            val priorCase = (0 until priorCases.length()).map(priorCases::getJSONObject).single { it.getString("case") == caseId }
            val rawPlacementDeltas = priorCase.getJSONArray("deltaLedger").toList<JSONObject>().filter { delta ->
                delta.optString("kind") == "PLACEMENT_OR_ROW_CHANGE" &&
                    delta.optString("c16Classification") == "RESIDUAL_MATERIAL_FIELD_DELTA" &&
                    delta.optJSONArray("changedFields")?.let { fields ->
                        (0 until fields.length()).any { fields.getString(it) in setOf("day", "order") }
                    } == true
            }
            val moves = rawPlacementDeltas.map { delta ->
                val owner = delta.getJSONObject("owner")
                val before = delta.getJSONObject("before")
                val after = delta.getJSONObject("after")
                val key = RowKey(before.getInt("week"), owner.getString("stableKey"), owner.getString("selectionRole"))
                val control = comparison.control.items.single {
                    it.weekNumber == key.week && it.exerciseStableKey == key.stableKey && it.selectionRole == key.role
                }
                val experimental = comparison.experimental.items.single {
                    it.weekNumber == key.week && it.exerciseStableKey == key.stableKey && it.selectionRole == key.role
                }
                require(control.dayOfWeek == before.getInt("day") && control.orderIndex == before.getInt("order"))
                require(experimental.dayOfWeek == after.getInt("day") && experimental.orderIndex == after.getInt("order"))
                require(materialSignature(control) == materialSignature(experimental)) {
                    "$caseId ${key.stableKey}#${key.role} week ${key.week} is not a pure placement delta"
                }
                Move(caseId, key, control, experimental)
            }.sortedWith(compareBy({ it.key.week }, { it.key.stableKey }, { it.key.role }))
            require(moves.size == when (caseId) {
                "persona0_mixed" -> 6
                "persona0_reviewed" -> 4
                "persona3_reviewed" -> 8
                else -> 14
            }) { "$caseId C16 placement delta count changed: ${moves.size}" }
            allMoves += moves

            val allShared = comparison.control.items.mapNotNull { control ->
                comparison.experimental.items.singleOrNull {
                    it.weekNumber == control.weekNumber && it.exerciseStableKey == control.exerciseStableKey &&
                        it.selectionRole == control.selectionRole
                }?.let { control to it }
            }.associate { (control, experimental) ->
                RowKey(control.weekNumber, control.exerciseStableKey, control.selectionRole) to control
            }
            val addedOwners = comparison.experimental.items
                .filter { row -> comparison.control.items.none { it.weekNumber == row.weekNumber &&
                    it.exerciseStableKey == row.exerciseStableKey && it.selectionRole == row.selectionRole } }
                .map { RowKey(0, it.exerciseStableKey, it.selectionRole) }
                .distinct().sortedWith(compareBy({ it.stableKey }, { it.role }))
            val restoreKeys = moves.mapTo(linkedSetOf()) { it.key }
            val fullCounterfactual = counterfactual(
                comparison.experimental, comparison.control, allShared, restoreKeys, context
            )
            val individual = moves.map { move ->
                val assessment = counterfactual(comparison.experimental, comparison.control, allShared,
                    setOf(move.key), context)
                move to assessment
            }

            val rebalancerRows = moves.map { move ->
                val events = comparison.experimental.personalizedDecision?.planningBudget?.execution
                    ?.ownerAllocationProvenance.orEmpty().filter { event ->
                        event.owner.stableKey == move.key.stableKey && event.owner.selectionRole == move.key.role &&
                            event.stage == OwnerAllocationStage.BOUNDED_DAY_REBALANCER &&
                            (event.before?.week == move.key.week || event.after?.week == move.key.week)
                    }.sortedWith(compareBy({ it.mutationSequence }, { it.action.ordinal }))
                JSONObject().put("case", caseId).put("week", move.key.week)
                    .put("stableKey", move.key.stableKey).put("selectionRole", move.key.role)
                    .put("events", JSONArray(events.map { it.toJson() }))
            }

            val deltas = JSONArray(moves.map { move ->
                val owner = StimulusPrescriptionOwnerIdentity(move.key.stableKey, move.key.role)
                val ownerEvidence = priorCase.getJSONArray("ownerEvidence").toList<JSONObject>().singleOrNull { evidence ->
                    evidence.getJSONObject("owner").getString("stableKey") == owner.stableKey &&
                        evidence.getJSONObject("owner").getString("selectionRole") == owner.selectionRole
                }
                val allEvents = comparison.experimental.personalizedDecision?.planningBudget?.execution
                    ?.ownerAllocationProvenance.orEmpty().filter { event ->
                        event.owner == owner && (event.before?.week == move.key.week || event.after?.week == move.key.week) &&
                            event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                                OwnerAllocationAction.ORDER_CHANGED)
                    }.sortedWith(compareBy({ it.stage.ordinal }, { it.mutationSequence }, { it.action.ordinal }))
                val firstDivergence = allEvents.firstOrNull { event ->
                    event.after?.week == move.key.week && event.after.day != move.control.dayOfWeek ||
                        event.after?.week == move.key.week && event.after.order != move.control.orderIndex
                }
                val finalStateEvent = allEvents.lastOrNull { it.after?.day == move.experimental.dayOfWeek &&
                    it.after?.order == move.experimental.orderIndex }
                val retained = individual.single { it.first.key == move.key }.second
                val triggerSensitivity = JSONArray(addedOwners.map { addedOwner ->
                    val omitted = comparison.experimental.items.filter { it.exerciseStableKey == addedOwner.stableKey &&
                        it.selectionRole == addedOwner.role }
                        .mapTo(linkedSetOf()) { RowKey(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
                    val withoutTrigger = counterfactual(comparison.experimental, comparison.control, allShared,
                        setOf(move.key), context, omitKeys = omitted)
                    val evidence = priorCase.getJSONArray("ownerEvidence").toList<JSONObject>().singleOrNull {
                        it.getJSONObject("owner").getString("stableKey") == addedOwner.stableKey &&
                            it.getJSONObject("owner").getString("selectionRole") == addedOwner.role
                    }
                    val targetIds = evidence?.getJSONArray("B5Targets")?.toStringList().orEmpty()
                    val b4Targets = priorCase.getJSONArray("B4Targets").toList<JSONObject>()
                        .filter { it.getString("targetId") in targetIds }
                    JSONObject().put("owner", ownerJson(StimulusPrescriptionOwnerIdentity(addedOwner.stableKey, addedOwner.role)))
                        .put("b4Targets", JSONArray(b4Targets))
                        .put("b5Selected", evidence?.optBoolean("B5Selected") ?: false)
                        .put("b5TargetIds", JSONArray(targetIds.sorted()))
                        .put("b6Authorities", evidence?.optJSONArray("B6") ?: JSONArray())
                        .put("materialization", evidence?.optJSONArray("materialization") ?: JSONArray())
                        .put("retainOldPlacementWithoutTriggerValid", withoutTrigger.valid)
                        .put("violationsWithoutTrigger", JSONArray(withoutTrigger.violations))
                        .put("interpretation", "SENSITIVITY_ONLY_NO_ORIGIN_EMITTED_CAUSAL_EDGE")
                })
                val rebalancerActions = comparison.experimental.personalizedDecision?.dayRebalancing?.actions.orEmpty()
                    .filter { it.stableKeys.contains(move.key.stableKey) }.map { it.toJson() }
                val classification = C17PlacementCausalityRules.classify(
                    previousPlacementCounterfactualValid = fullCounterfactual.valid || retained.valid,
                    proof = null
                ).name
                JSONObject()
                    .put("case", caseId)
                    .put("week", move.key.week)
                    .put("owner", ownerJson(owner))
                    .put("fromDay", move.control.dayOfWeek)
                    .put("fromOrder", move.control.orderIndex)
                    .put("toDay", move.experimental.dayOfWeek)
                    .put("toOrder", move.experimental.orderIndex)
                    .put("beforeRow", rowJson(move.control))
                    .put("afterRow", rowJson(move.experimental))
                    .put("b5Selected", ownerEvidence?.optBoolean("B5Selected") ?: false)
                    .put("b5TargetRelations", ownerEvidence?.optJSONArray("B5Targets") ?: JSONArray())
                    .put("b5NonSelectionEvidence", ownerEvidence?.optJSONArray("B5NonSelection") ?: JSONArray())
                    .put("b6Authorities", ownerEvidence?.optJSONArray("B6") ?: JSONArray())
                    .put("materialization", ownerEvidence?.optJSONArray("materialization") ?: JSONArray())
                    .put("b7Attributions", ownerEvidence?.optJSONArray("B7Attributions") ?: JSONArray())
                    .put("materialParity", materialSignature(move.control) == materialSignature(move.experimental))
                    .put("firstDivergenceStage", firstDivergence?.stage?.name ?: "NO_EXACT_DIVERGENCE_EVENT")
                    .put("firstDivergenceAction", firstDivergence?.action?.name ?: "NO_EXACT_DIVERGENCE_EVENT")
                    .put("firstDivergenceCause", firstDivergence?.cause?.name ?: "NO_EXACT_DIVERGENCE_EVENT")
                    .put("firstDivergenceEvent", firstDivergence?.toJson() ?: JSONObject.NULL)
                    .put("originStage", firstDivergence?.stage?.name ?: "UNPROVEN")
                    .put("originAction", firstDivergence?.action?.name ?: "UNPROVEN")
                    .put("originCause", firstDivergence?.cause?.name ?: "UNPROVEN")
                    .put("originAuthorizedDemandIds", JSONArray(firstDivergence?.authorizedDemandIds?.sorted().orEmpty()))
                    .put("originTargetIds", JSONArray(firstDivergence?.targetIds?.sorted().orEmpty()))
                    .put("originQualities", JSONArray(firstDivergence?.qualities?.sorted().orEmpty()))
                    .put("originEvidenceCodes", JSONArray(firstDivergence?.evidenceCodes?.distinct()?.sorted().orEmpty()))
                    .put("allOriginEvents", JSONArray(allEvents.map { it.toJson() }))
                    .put("candidateCanonicalTriggerSensitivity", triggerSensitivity)
                    .put("finalPlacementStage", finalStateEvent?.stage?.name ?: "NO_EXACT_FINAL_STAGE_EVENT")
                    .put("allAcceptedPlacementEvents", JSONArray(allEvents.map { it.toJson() }))
                    .put("directCausalDisplacementEdges", JSONArray(comparison.experimental.personalizedDecision
                        ?.planningBudget?.execution?.ownerDisplacementEdges.orEmpty().filter { it.displacedOwner == owner }
                        .filter { it.week == null || it.week == move.key.week }.map { it.toJson() }))
                    .put("individualCounterfactualValid", retained.valid)
                    .put("individualCounterfactualViolations", JSONArray(retained.violations))
                    .put("fullSharedOwnerCounterfactualValid", fullCounterfactual.valid)
                    .put("fullSharedOwnerCounterfactualViolations", JSONArray(fullCounterfactual.violations))
                    .put("classification", classification)
                    .put("rebalancerActions", JSONArray(rebalancerActions))
                    .put("causalDepth", JSONObject.NULL)
                    .put("derivedAuthorityEligible", false)
                    .put("counterfactualConstraintState", retained.details)
            })

            outputCases.put(JSONObject()
                .put("case", caseId)
                .put("route", result.routeDecision.selectedSource.name)
                .put("b7Status", comparison.experimentalReadinessAudit?.status?.name)
                .put("b7Reasons", JSONArray(comparison.experimentalReadinessAudit?.reasonCodes.orEmpty().sorted()))
                .put("b8Status", comparison.productionCutoverAuthority?.status?.name)
                .put("b8Reasons", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted()))
                .put("authorizedCalibrationOwner", ownerJson(StimulusPrescriptionOwnerIdentity(
                    priorCase.getJSONObject("calibrationOwner").getString("stableKey"),
                    priorCase.getJSONObject("calibrationOwner").getString("selectionRole"))))
                .put("b4Targets", priorCase.getJSONArray("B4Targets"))
                .put("placementDeltaCount", moves.size)
                .put("uniqueOwnerRoleCount", moves.map { it.key.stableKey to it.key.role }.distinct().size)
                .put("sharedOwnerPlacementMetrics", sharedPlacementMetrics(comparison.control.items, comparison.experimental.items))
                .put("fullSharedOwnerCounterfactualValid", fullCounterfactual.valid)
                .put("fullSharedOwnerCounterfactual", fullCounterfactual.details)
                .put("individualCounterfactualsValid", individual.count { it.second.valid })
                .put("individualCounterfactualsInvalid", individual.count { !it.second.valid })
                .put("rebalancerEvents", JSONArray(rebalancerRows))
                .put("deltas", deltas))
        }

        val classifications = allMoves.groupBy { move ->
            val assessment = counterfactual(
                comparisonFor(move.caseId).experimental,
                comparisonFor(move.caseId).control,
                comparisonFor(move.caseId).control.items.mapNotNull { control ->
                    comparisonFor(move.caseId).experimental.items.singleOrNull {
                        it.weekNumber == control.weekNumber && it.exerciseStableKey == control.exerciseStableKey &&
                            it.selectionRole == control.selectionRole
                    }?.let { RowKey(control.weekNumber, control.exerciseStableKey, control.selectionRole) to control }
                }.toMap(), setOf(move.key), requireNotNull(contexts[move.caseId])
            )
            C17PlacementCausalityRules.classify(assessment.valid, proof = null).name
        }
        val positiveResult = requireNotNull(records.singleOrNull { it.first.label == "persona2_reviewed" }?.second) {
            "C17 requires the positive persona2_reviewed reference"
        }
        val positiveComparison = c20PreActivationComparison(positiveResult)
        val positiveMetrics = sharedPlacementMetrics(positiveComparison.control.items, positiveComparison.experimental.items)
        val positiveOwner = priorCases.let { cases ->
            (0 until cases.length()).map(cases::getJSONObject).single { it.getString("case") == "persona2_reviewed" }
                .getJSONObject("calibrationOwner")
        }
        val output = JSONObject()
            .put("schema", "c17-placement-causality-census-v1")
            .put("c16MergeSha", c16MergeSha)
            .put("c17StartSha", c17StartSha)
            .put("cases", outputCases)
            .put("summary", JSONObject()
                .put("placementDeltas", allMoves.size)
                .put("caseOwnerRolePairs", allMoves.map { Triple(it.caseId, it.key.stableKey, it.key.role) }.distinct().size)
                .put("necessaryAuthorizedDisplacementCandidates", classifications["NECESSARY_AUTHORIZED_DISPLACEMENT"]?.size ?: 0)
                .put("unnecessaryPlacementDrift", classifications["UNNECESSARY_PLACEMENT_DRIFT"]?.size ?: 0)
                .put("displacementCausedByUnauthorizedTrigger", classifications["DISPLACEMENT_CAUSED_BY_UNAUTHORIZED_TRIGGER"]?.size ?: 0)
                .put("orderOnlyRows", allMoves.count { it.control.dayOfWeek == it.experimental.dayOfWeek && it.control.orderIndex != it.experimental.orderIndex })
                .put("unresolved", classifications["UNRESOLVED_NO_PROVEN_AUTHORIZED_DISPLACEMENT"]?.size ?: 0)
                .put("placementEventStageCounts", JSONObject(allMoves.flatMap { move ->
                    val result = requireNotNull(selected[move.caseId]?.second)
                    c20PreActivationComparison(result).experimental.personalizedDecision?.planningBudget?.execution
                        ?.ownerAllocationProvenance.orEmpty().filter { it.owner.stableKey == move.key.stableKey &&
                            it.owner.selectionRole == move.key.role && it.after?.week == move.key.week &&
                        it.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                                OwnerAllocationAction.ORDER_CHANGED) }.map { it.stage.name }
                }.groupingBy { it }.eachCount().toSortedMap().mapValues { it.value }))
                .put("boundedDayRebalancerAcceptedEvents", allMoves.sumOf { move ->
                    comparisonFor(move.caseId).experimental.personalizedDecision
                        ?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty().count {
                            it.owner.stableKey == move.key.stableKey && it.owner.selectionRole == move.key.role &&
                                it.after?.week == move.key.week && it.stage == OwnerAllocationStage.BOUNDED_DAY_REBALANCER &&
                                it.action in setOf(OwnerAllocationAction.PLACEMENT_MOVED, OwnerAllocationAction.ORDER_CHANGED)
                        }
                }))
                .put("sharedOwnerPlacementMetrics", sharedPlacementMetrics(
                    targetCases.sorted().flatMap { caseId ->
                        val comparison = comparisonFor(caseId)
                        comparison.control.items.mapNotNull { before ->
                            comparison.experimental.items.singleOrNull { after -> after.weekNumber == before.weekNumber &&
                                after.exerciseStableKey == before.exerciseStableKey && after.selectionRole == before.selectionRole
                            }?.let { before to it }
                        }
                    }
                ))
            .put("positiveReference", JSONObject()
                .put("case", "persona2_reviewed")
                .put("route", positiveResult.routeDecision.selectedSource.name)
                .put("b8Status", positiveComparison.productionCutoverAuthority?.status?.name)
                .put("calibrationOwner", positiveOwner)
                .put("authorizedCalibrationRows", positiveComparison.experimental.items.count { row ->
                    row.exerciseStableKey == positiveOwner.getString("stableKey") &&
                        row.selectionRole == positiveOwner.getString("selectionRole")
                })
                .put("sharedOwnerPlacementMetrics", positiveMetrics))
        require(allMoves.size == 32) { "C17 must account for all 32 C16 placement deltas, found ${allMoves.size}" }
        return output.toString(2)
    }

    private fun counterfactual(
        experimental: GeneratedProgramSkeleton,
        control: GeneratedProgramSkeleton,
        allSharedControlRows: Map<RowKey, ProgramSkeletonItem>,
        restoreKeys: Set<RowKey>,
        context: PreparedCanonicalGenerationContext,
        omitKeys: Set<RowKey> = emptySet()
    ): Counterfactual {
        val controlKeys = control.items.mapTo(hashSetOf()) { RowKey(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
        val retainedExperimentalRows = experimental.items.filter { row ->
            RowKey(row.weekNumber, row.exerciseStableKey, row.selectionRole) !in omitKeys
        }
        val relocated = retainedExperimentalRows.map { row ->
            val key = RowKey(row.weekNumber, row.exerciseStableKey, row.selectionRole)
            val prior = allSharedControlRows[key]
            if (key in restoreKeys && prior != null) row.copy(dayOfWeek = prior.dayOfWeek, orderIndex = prior.orderIndex) else row
        }
        // When an authorized new row wants an occupied order, keep the shared rows' relative placement
        // and put the addition after them. Order is a presentation slot, not a constraint on the old row.
        val ordered = relocated.groupBy { it.weekNumber to it.dayOfWeek }.values.flatMap { dayRows ->
            dayRows.sortedWith(compareBy<ProgramSkeletonItem> { if (RowKey(it.weekNumber, it.exerciseStableKey, it.selectionRole) in controlKeys) 0 else 1 }
                .thenBy { row ->
                    allSharedControlRows[RowKey(row.weekNumber, row.exerciseStableKey, row.selectionRole)]?.orderIndex
                        ?: row.orderIndex
                }.thenBy { it.exerciseStableKey }.thenBy { it.selectionRole }.thenBy { it.localId })
                .mapIndexed { index, row -> row.copy(orderIndex = index + 1) }
        }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }, { it.selectionRole }))
        val plan = experimental.copy(items = ordered)
        val violations = sortedSetOf<String>()
        val baselineErrors = ProgramProjectionValidator().errors(experimental)
        val counterErrors = ProgramProjectionValidator().errors(plan)
        if (omitKeys.isEmpty()) (counterErrors - baselineErrors).forEach { violations += "PROGRAM_PROJECTION:$it" }

        plan.items.groupBy { it.weekNumber }.forEach { (week, rows) ->
            val allowedDays = plan.weekDaySchedule[week].orEmpty()
            rows.filter { it.dayOfWeek !in allowedDays }.forEach { violations += "DAY_NOT_IN_USER_SCHEDULE:week=$week:day=${it.dayOfWeek}" }
            rows.groupBy { it.dayOfWeek }.forEach { (day, dayRows) ->
                dayRows.groupBy { it.exerciseStableKey }.filterValues { it.size > 1 }.keys.sorted()
                    .forEach { violations += "SAME_OWNER_TWICE_IN_DAY:week=$week:day=$day:key=$it" }
                if (dayRows.sumOf { it.estimatedDurationSeconds } > plan.request.sessionMinutes * 60) {
                    violations += "SESSION_TIME_CAP:week=$week:day=$day:seconds=${dayRows.sumOf { it.estimatedDurationSeconds }}:cap=${plan.request.sessionMinutes * 60}"
                }
            }
        }

        val weekProjectionDetails = JSONArray()
        plan.items.map { it.weekNumber }.distinct().sorted().forEach { week ->
            val baseWeek = retainedExperimentalRows.filter { it.weekNumber == week }
            val candidateWeek = plan.items.filter { it.weekNumber == week }
            val baseDayLoads = context.snapshot.planDayProjection?.let { projection ->
                baseWeek.groupBy { it.dayOfWeek }.toSortedMap().mapValues { (_, rows) -> projection.evaluate(rows.sortedBy { it.orderIndex }) }
            }.orEmpty()
            val candidateDayLoads = context.snapshot.planDayProjection?.let { projection ->
                candidateWeek.groupBy { it.dayOfWeek }.toSortedMap().mapValues { (_, rows) -> projection.evaluate(rows.sortedBy { it.orderIndex }) }
            }.orEmpty()
            if (context.snapshot.planDayProjection == null) violations += "DAY_OFI_PROJECTION_UNAVAILABLE:week=$week"
            candidateDayLoads.forEach { (day, candidate) ->
                val baseline = baseDayLoads[day]
                if (!candidate.feasible && baseline?.feasible != false) {
                    violations += "NEW_DAY_OFI_OR_AXIS_GATE:week=$week:day=$day:ofi=${candidate.ofi}:axes=${candidate.axisScores}:cautions=${candidate.cautionReasons.sorted()}"
                }
            }

            val baseTissue = context.snapshot.planWeekTissueProjection?.evaluate(baseWeek, 8.5)
            val candidateTissue = context.snapshot.planWeekTissueProjection?.evaluate(candidateWeek, 8.5)
            if (context.snapshot.planWeekTissueProjection == null) violations += "WEEK_TISSUE_PROJECTION_UNAVAILABLE:week=$week"
            if (baseTissue != null && candidateTissue != null) {
                val beforeByDay = baseTissue.days.associateBy { it.day }
                candidateTissue.days.forEach { day ->
                    val before = beforeByDay[day.day]
                    val newBlocked = day.blockedUnits - before?.blockedUnits.orEmpty()
                    val newUnresolved = day.unresolvedKeys - before?.unresolvedKeys.orEmpty()
                    if (newBlocked.isNotEmpty() || newUnresolved.isNotEmpty()) {
                        violations += "NEW_WEEK_TISSUE_GATE:week=$week:day=${day.day}:blocked=${newBlocked.sorted()}:unresolved=${newUnresolved.sorted()}"
                    }
                }
            }
            weekProjectionDetails.put(JSONObject().put("week", week)
                .put("dayOfi", JSONArray(candidateDayLoads.toSortedMap().map { (day, load) ->
                    JSONObject().put("day", day).put("ofi", load.ofi).put("axisScores", JSONArray(load.axisScores))
                        .put("cautionReasons", JSONArray(load.cautionReasons.sorted())).put("feasible", load.feasible)
                }))
                .put("baselineDayOfi", JSONArray(baseDayLoads.toSortedMap().map { (day, load) ->
                    JSONObject().put("day", day).put("ofi", load.ofi).put("axisScores", JSONArray(load.axisScores))
                        .put("cautionReasons", JSONArray(load.cautionReasons.sorted())).put("feasible", load.feasible)
                }))
                .put("weekTissue", candidateTissue?.toJson() ?: JSONObject.NULL)
                .put("baselineWeekTissue", baseTissue?.toJson() ?: JSONObject.NULL))
        }

        val continuityKeys = experimental.personalizedDecision?.authorizedScheduling?.authorized.orEmpty()
            .filter { it.continuity }.mapTo(sortedSetOf()) { it.item.stableKey }
        val primaryKeys = PrimaryStrengthAnchorSpacingPolicy.keys(context.snapshot, context.state, continuityKeys)
        if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(plan.items, primaryKeys)) violations += "PRIMARY_ANCHOR_CALENDAR_SPACING"

        val originalFrequency = retainedExperimentalRows.groupingBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }.eachCount()
        val counterFrequency = plan.items.groupingBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }.eachCount()
        if (originalFrequency != counterFrequency) violations += "OWNER_FREQUENCY_CHANGED"
        val originalMaterial = retainedExperimentalRows.groupBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
            .mapValues { (_, rows) -> rows.sortedBy { it.orderIndex }.map(::materialSignature) }
        val counterMaterial = plan.items.groupBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
            .mapValues { (_, rows) -> rows.sortedBy { it.orderIndex }.map(::materialSignature) }
        if (originalMaterial != counterMaterial) violations += "PRESCRIPTION_OR_OWNER_MUTATION"

        val detail = JSONObject()
            .put("restoredOwnerRows", JSONArray(restoreKeys.sortedWith(compareBy({ it.week }, { it.stableKey }, { it.role })).map {
                JSONObject().put("week", it.week).put("stableKey", it.stableKey).put("selectionRole", it.role)
            }))
            .put("omittedOwnerRowsForSensitivity", JSONArray(omitKeys.sortedWith(compareBy({ it.week }, { it.stableKey }, { it.role })).map {
                JSONObject().put("week", it.week).put("stableKey", it.stableKey).put("selectionRole", it.role)
            }))
            .put("sessionSecondsByWeek", JSONObject(plan.items.groupBy { it.weekNumber }.toSortedMap().entries.associate { (week, rows) ->
                week.toString() to JSONObject(rows.groupBy { it.dayOfWeek }.toSortedMap().entries.associate { (day, dayRows) ->
                    day.toString() to dayRows.sumOf { it.estimatedDurationSeconds }
                })
            }))
            .put("sessionCapacitySeconds", plan.request.sessionMinutes * 60)
            .put("weekConstraintStates", weekProjectionDetails)
            .put("primarySpacingKeys", JSONArray(primaryKeys.sorted()))
            .put("programProjectionErrors", JSONArray(counterErrors.sorted()))
            .put("violations", JSONArray(violations))
        return Counterfactual(plan, violations.toList(), detail)
    }

    private fun ownerJson(owner: StimulusPrescriptionOwnerIdentity) = JSONObject()
        .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)

    private fun sharedPlacementMetrics(
        control: List<ProgramSkeletonItem>,
        experimental: List<ProgramSkeletonItem>
    ): JSONObject = sharedPlacementMetrics(control.mapNotNull { before ->
            experimental.singleOrNull { after -> after.weekNumber == before.weekNumber &&
                after.exerciseStableKey == before.exerciseStableKey && after.selectionRole == before.selectionRole
            }?.let { before to it }
        })

    private fun sharedPlacementMetrics(shared: List<Pair<ProgramSkeletonItem, ProgramSkeletonItem>>): JSONObject {
        return JSONObject().put("sharedOwnerRows", shared.size)
            .put("dayPreserved", shared.count { (before, after) -> before.dayOfWeek == after.dayOfWeek })
            .put("dayAndOrderPreserved", shared.count { (before, after) ->
                before.dayOfWeek == after.dayOfWeek && before.orderIndex == after.orderIndex
            })
            .put("movedOwnerRows", shared.count { (before, after) ->
                before.dayOfWeek != after.dayOfWeek || before.orderIndex != after.orderIndex
            })
            .put("totalAbsoluteDayDistance", shared.sumOf { (before, after) ->
                kotlin.math.abs(before.dayOfWeek - after.dayOfWeek)
            })
            .put("orderOnlyChanges", shared.count { (before, after) ->
                before.dayOfWeek == after.dayOfWeek && before.orderIndex != after.orderIndex
            })
    }

    private fun rowJson(row: ProgramSkeletonItem) = JSONObject()
        .put("owner", ownerJson(StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)))
        .put("week", row.weekNumber).put("day", row.dayOfWeek).put("order", row.orderIndex)
        .put("quality", JSONObject.NULL)
        .put("qualityEvidence", "NOT_ENCODED_ON_THIS_LEGACY_OWNER_ROW")
        .put("setCount", row.setCount).put("reps", row.reps).put("weightKg", row.weightKg)
        .put("loadSource", row.weightSource).put("targetRpeMin", row.setPrescriptions.mapNotNull { it.targetRpeMin }.minOrNull())
        .put("targetRpeMax", Regex("RPE\\s+([0-9]+(?:\\.[0-9]+)?)[–-]([0-9]+(?:\\.[0-9]+)?)")
            .find(row.prescription)?.groupValues?.getOrNull(2)?.toDoubleOrNull())
        .put("restSeconds", row.restSeconds).put("prescription", row.prescription)
        .put("setPrescriptions", JSONArray(row.setPrescriptions.sortedBy { it.setIndex }.map { set ->
            JSONObject().put("setIndex", set.setIndex).put("reps", set.reps).put("weightKg", set.weightKg)
                .put("seconds", set.seconds).put("targetRpeMin", set.targetRpeMin).put("loadState", set.loadState.name)
        }))

    private fun materialSignature(row: ProgramSkeletonItem) = listOf(
        row.setCount, row.reps, row.weightKg, row.seconds, row.restSeconds, row.prescription, row.weightSource,
        row.setPrescriptions.sortedBy { it.setIndex }.joinToString(";") {
            listOf(it.setIndex, it.reps, it.weightKg, it.seconds, it.targetRpeMin, it.loadState.name).joinToString(":")
        }, row.trainingSlot, row.dayIntensity, row.stableKey, row.selectionRole
    )

    private inline fun <reified T> JSONArray.toList(): List<T> = (0 until length()).map { get(it) as T }
    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
