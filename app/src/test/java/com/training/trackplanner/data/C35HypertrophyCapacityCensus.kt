package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C35 census over the same production corpus used by C33/C34. */
internal object C35HypertrophyCapacityCensus {
    private const val H_TARGET = "QUALITY:HYPERTROPHY"
    private const val HIGH_PRIORITY = 100

    fun render(
        generatedByCase: Map<String, StimulusProductionGenerationResult>,
        generationMillisByCase: Map<String, Long>,
        startSha: String
    ): String {
        val cases = JSONArray()
        val regionalRows = mutableListOf<JSONObject>()
        val routes = sortedMapOf<String, Int>()
        val b7ReasonCounts = sortedMapOf<String, Int>()
        val b8StatusCounts = sortedMapOf<String, Int>()
        var aggregateFalseRegressionCandidates = 0
        var trueRegionalRegressions = 0
        var duplicatePhysicalSetRows = 0
        var regionalRawResidual = 0.0
        var regionalAuthorized = 0
        var regionalFunded = 0
        var regionalMaterialized = 0
        var regionalUnfunded = 0
        var regionalUnmaterialized = 0
        var unauthorizedMaterialRows = 0
        var regionalOverrunTargets = 0
        var highPriorityFunded = 0
        var highPriorityCapacityRejected = 0
        var lowerFundedWhileHigherRejected = 0
        var beforeInversions = 0
        var afterInversions = 0
        var totalCompetingOwners = 0
        var collateralCases = 0
        var targetRegressionCases = 0
        var hypertrophyAggregateRegressionCases = 0
        var provenanceUnclosedCases = 0
        var unexplainedRemovedOccurrences = 0
        var unexplainedPrescriptionCases = 0

        generatedByCase.toSortedMap().forEach { (caseName, generated) ->
            val comparison = generated.comparison ?: run {
                cases.put(JSONObject().put("case", caseName).put("generated", false)
                    .put("route", generated.routeDecision.selectedSource.name))
                return@forEach
            }
            val b7 = comparison.experimentalReadinessAudit
            val allocation = comparison.experimental.personalizedDecision?.planningBudget?.execution
            val priorityRows = allocation?.finiteAllocationPriorityOrder.orEmpty()
            duplicatePhysicalSetRows += comparison.experimental.items
                .groupingBy { listOf(it.exerciseStableKey, it.selectionRole, it.weekNumber, it.dayOfWeek, it.orderIndex) }
                .eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
            totalCompetingOwners += priorityRows.size
            beforeInversions += priorityInversions(priorityRows.sortedWith(oldPreC35Order()))
            afterInversions += priorityInversions(priorityRows)
            highPriorityFunded += priorityRows.count { it.priority >= HIGH_PRIORITY && it.fundedUnits > 0 }
            highPriorityCapacityRejected += priorityRows.count {
                it.priority >= HIGH_PRIORITY && it.requestedUnits > 0 && it.fundedUnits == 0
            }
            priorityRows.forEach { rejectedHigh ->
                if (rejectedHigh.requestedUnits <= 0 || rejectedHigh.fundedUnits >= rejectedHigh.requestedUnits) return@forEach
                if (priorityRows.any { fundedLow -> fundedLow.priority < rejectedHigh.priority && fundedLow.fundedUnits > 0 }) {
                    lowerFundedWhileHigherRejected++
                }
            }

            val aggregateTarget = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.HYPERTROPHY }
            val controlAggregate = comparison.controlAudit?.qualityAudits?.firstOrNull { it.quality == TrainableQuality.HYPERTROPHY }
            val experimentalAggregate = comparison.experimentalAudit?.qualityAudits?.firstOrNull { it.quality == TrainableQuality.HYPERTROPHY }
            val aggregateOutcome = b7?.targetOutcomes?.firstOrNull { it.targetId == H_TARGET }
            val excludedRegionalUnits = regionalHypertrophyAddedWeeklyUnits(comparison)
            val aggregateRange = aggregateTarget?.weeklyDirectUnitsTarget
            val rawAggregateWouldRegress = aggregateRange != null &&
                controlAggregate?.plannedWeeklyDirectUnits != null && experimentalAggregate?.plannedWeeklyDirectUnits != null &&
                controlAggregate.plannedWeeklyDirectUnits.let { distance(aggregateRange, it) } <
                distance(aggregateRange, experimentalAggregate.plannedWeeklyDirectUnits)
            val rawAdjustedComparisonStatus = aggregateRange?.let { range ->
                val controlDistance = controlAggregate?.plannedWeeklyDirectUnits?.let { distance(range, it) }
                val adjustedDistance = experimentalAggregate?.plannedWeeklyDirectUnits?.let {
                    distance(range, (it - excludedRegionalUnits).coerceAtLeast(0.0))
                }
                if (controlDistance != null && adjustedDistance != null) compareDistance(controlDistance, adjustedDistance) else null
            }
            val regressionOnlyFromRawRegionalAggregate = rawAggregateWouldRegress && excludedRegionalUnits > 0.0 &&
                rawAdjustedComparisonStatus != StimulusExperimentalTargetOutcomeStatus.REGRESSED
            if (regressionOnlyFromRawRegionalAggregate) aggregateFalseRegressionCandidates++
            if (aggregateOutcome?.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED) {
                targetRegressionCases++
                if (aggregateTarget?.quality == TrainableQuality.HYPERTROPHY) hypertrophyAggregateRegressionCases++
            }
            if (b7?.reasonCodes?.contains("COLLATERAL_TARGET_REGRESSION") == true) collateralCases++
            if (b7?.reasonCodes?.contains("CHANGE_PROVENANCE_UNCLOSED") == true) provenanceUnclosedCases++
            unexplainedRemovedOccurrences += b7?.changeAttributions.orEmpty().count {
                it.reasonCodes.contains("UNEXPLAINED_REMOVED_IDENTITY")
            }
            if (b7?.changeAttributions.orEmpty().any { "UNEXPLAINED_PRESCRIPTION_CHANGE" in it.reasonCodes }) unexplainedPrescriptionCases++
            b7?.reasonCodes.orEmpty().forEach { b7ReasonCounts[it] = (b7ReasonCounts[it] ?: 0) + 1 }
            val b8Status = comparison.productionCutoverAuthority?.status?.name ?: "NOT_EVALUATED"
            b8StatusCounts[b8Status] = (b8StatusCounts[b8Status] ?: 0) + 1
            routes[generated.routeDecision.selectedSource.name] = (routes[generated.routeDecision.selectedSource.name] ?: 0) + 1

            val authorizationPlan = comparison.prescriptionAuthorizationPlan
            val movementJson = JSONArray()
            comparison.targetPlan.movementTargets.sortedBy { it.targetId }.forEach { movement ->
                movement.regionalDoseTargets.forEach { dose ->
                    if (dose.kind != StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET) return@forEach
                    val selectedOwners = comparison.selectionPlan.selectedCandidates.filter {
                        movement.targetId in it.coveredTargetIds && it.primaryTargetId == movement.targetId
                    }
                    val targetRawResidual = dose.residualEquivalentExposure
                    val authorizedUnits = dose.authorizedWholeSetUnits ?: 0
                    regionalRawResidual += targetRawResidual ?: authorizedUnits.toDouble()
                    regionalAuthorized += authorizedUnits
                    val ownerRows = selectedOwners.map { selected ->
                        val auth = authorizationPlan?.authorizations.orEmpty().singleOrNull {
                            it.targetId == movement.targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                                it.owner?.stableKey == selected.stableKey && it.owner.selectionRole == selected.selectionRole
                        }
                        val owner = auth?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                        val priority = owner?.let { exact -> priorityRows.singleOrNull { it.owner == exact } }
                        val audit = comparison.prescriptionMaterializationAudits.singleOrNull {
                            it.targetId == movement.targetId && it.quality == TrainableQuality.HYPERTROPHY &&
                                it.owner?.stableKey == selected.stableKey && it.owner.selectionRole == selected.selectionRole
                        }
                        val fundedUnits = priority?.fundedUnits ?: 0
                        val materializedUnits = audit?.minimumWeeklyCompatibleUnits ?: 0
                        regionalFunded += fundedUnits
                        regionalMaterialized += materializedUnits
                        if (auth?.authorizedPrescription == null && materializedUnits > 0) unauthorizedMaterialRows += materializedUnits
                        JSONObject()
                            .put("stableKey", owner?.stableKey ?: selected.stableKey)
                            .put("selectionRole", owner?.selectionRole ?: selected.selectionRole)
                            .put("b6Status", auth?.status?.name ?: authorizationPlan?.movementAuthorizations.orEmpty()
                                .firstOrNull { it.targetId == movement.targetId }?.status?.name ?: "NO_AUTHORITY")
                            .put("authorized", auth?.authorizedPrescription != null)
                            .put("fundedUnits", fundedUnits)
                            .put("materializedCompatibleUnits", materializedUnits)
                            .put("priority", priority?.priority ?: JSONObject.NULL)
                            .put("b6ReasonCodes", JSONArray(auth?.reasonCodes.orEmpty().sorted()))
                            .put("capacityReasons", JSONArray(allocation?.ownerAllocationProvenance.orEmpty().filter {
                                owner != null && it.owner == owner && it.stage == OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION
                            }.flatMap { it.evidenceCodes }.distinct().sorted()))
                    }
                    val fundedUnits = ownerRows.sumOf { it.optInt("fundedUnits") }
                    val materializedUnits = ownerRows.sumOf { it.optInt("materializedCompatibleUnits") }
                    regionalUnfunded += (authorizedUnits - fundedUnits).coerceAtLeast(0)
                    regionalUnmaterialized += (authorizedUnits - materializedUnits).coerceAtLeast(0)
                    val movementOutcome = b7?.targetOutcomes?.firstOrNull { it.targetId == movement.targetId }
                    if (movementOutcome?.status == StimulusExperimentalTargetOutcomeStatus.REGRESSED) regionalOverrunTargets++
                    val row = JSONObject()
                            .put("case", caseName)
                            .put("targetId", movement.targetId)
                            .put("movementCoverage", movement.movementCoverage.name)
                            .put("targetPriority", movement.priority.name)
                            .put("doseNumericAuthority", dose.numericAuthority.name)
                            .put("targetEquivalentExposure", dose.weeklyTarget ?: JSONObject.NULL)
                            .put("existingEquivalentExposure", dose.existingEquivalentExposure ?: JSONObject.NULL)
                            .put("rawResidualEquivalentExposure", targetRawResidual ?: JSONObject.NULL)
                            .put("authorizedWholeSetUnits", authorizedUnits)
                            .put("selectedOwners", JSONArray(ownerRows))
                            .put("candidateCount", comparison.selectionPlan.traces.firstOrNull { it.targetId == movement.targetId }
                                ?.candidatePool?.distinct()?.size ?: 0)
                            .put("fundedUnits", fundedUnits)
                            .put("materializedCompatibleUnits", materializedUnits)
                            .put("unfundedUnits", (authorizedUnits - fundedUnits).coerceAtLeast(0))
                            .put("unmaterializedShortfallUnits", (authorizedUnits - materializedUnits).coerceAtLeast(0))
                            .put("b7Outcome", movementOutcome?.status?.name ?: "NO_OUTCOME")
                            .put("b7ReasonCodes", JSONArray(movementOutcome?.reasonCodes.orEmpty().sorted()))
                    regionalRows += row
                    movementJson.put(row)
                }
            }
            val outcomeJson = JSONArray(b7?.targetOutcomes.orEmpty().sortedBy { it.targetId }.map { outcome ->
                JSONObject().put("targetId", outcome.targetId).put("status", outcome.status.name)
                    .put("directlyAffected", outcome.directlyAffected)
                    .put("controlUnitsDistance", outcome.controlWeeklyUnitsDistance ?: JSONObject.NULL)
                    .put("experimentalUnitsDistance", outcome.experimentalWeeklyUnitsDistance ?: JSONObject.NULL)
                    .put("regionalHypertrophyUnitsExcludedFromAggregateComparison",
                        outcome.regionalHypertrophyUnitsExcludedFromAggregateComparison ?: JSONObject.NULL)
                    .put("reasonCodes", JSONArray(outcome.reasonCodes.sorted()))
            })
            val casePriority = JSONArray(priorityRows.mapIndexed { index, item -> item.toJson().put("actualOrder", index + 1) })
            cases.put(JSONObject()
                .put("case", caseName)
                .put("generated", true)
                .put("generationTimeMs", generationMillisByCase[caseName] ?: JSONObject.NULL)
                .put("route", generated.routeDecision.selectedSource.name)
                .put("aggregateHypertrophy", JSONObject()
                    .put("numericAuthority", aggregateTarget?.numericAuthority?.name ?: "NO_TARGET")
                    .put("b4Range", aggregateTarget?.weeklyDirectUnitsTarget?.let {
                        JSONObject().put("min", it.min).put("preferred", it.preferred).put("max", it.max)
                    } ?: JSONObject.NULL)
                    .put("controlRawAggregateUnits", controlAggregate?.plannedWeeklyDirectUnits ?: JSONObject.NULL)
                    .put("experimentalRawAggregateUnits", experimentalAggregate?.plannedWeeklyDirectUnits ?: JSONObject.NULL)
                    .put("regionalAuthorizedAddedUnitsExcluded", excludedRegionalUnits)
                    .put("comparisonUnits", experimentalAggregate?.plannedWeeklyDirectUnits?.let {
                        (it - excludedRegionalUnits).coerceAtLeast(0.0)
                    } ?: JSONObject.NULL)
                    .put("b7Status", aggregateOutcome?.status?.name ?: "NO_OUTCOME")
                    .put("reasonCodes", JSONArray(aggregateOutcome?.reasonCodes.orEmpty().sorted())))
                .put("movementTargets", movementJson)
                .put("finiteAllocation", JSONObject()
                    .put("capacity", allocation?.capacity?.finalControllableUnits ?: JSONObject.NULL)
                    .put("continuityRequested", allocation?.continuityRequestedUnits ?: JSONObject.NULL)
                    .put("continuityAllocated", allocation?.continuityAllocatedUnits ?: JSONObject.NULL)
                    .put("materialRequested", allocation?.materialGapRequestedUnits ?: JSONObject.NULL)
                    .put("materialAllocated", allocation?.materialGapAllocatedUnits ?: JSONObject.NULL)
                    .put("priorityOrder", casePriority)
                    .put("priorityInversionsBefore", priorityInversions(priorityRows.sortedWith(oldPreC35Order())))
                    .put("priorityInversionsAfter", priorityInversions(priorityRows)))
                .put("b7", JSONObject()
                    .put("status", b7?.status?.name ?: "NOT_EVALUATED")
                    .put("reasons", JSONArray(b7?.reasonCodes.orEmpty().sorted()))
                    .put("changeProvenanceClosed", b7?.changeProvenanceClosed ?: JSONObject.NULL)
                    .put("collateralRegressionFree", b7?.collateralRegressionFree ?: JSONObject.NULL)
                    .put("outcomes", outcomeJson))
                .put("b8", JSONObject()
                    .put("scope", comparison.productionCutoverAuthority?.scope?.name ?: JSONObject.NULL)
                    .put("status", b8Status)
                    .put("reasonCodes", JSONArray(comparison.productionCutoverAuthority?.reasonCodes.orEmpty().sorted())))
                .put("buildAccounting", JSONObject()
                    .put("control", generated.buildCounts.controlBuilds)
                    .put("experimental", generated.buildCounts.experimentalBuilds)
                    .put("total", generated.buildCounts.totalBuildInvocations)
                    .put("third", generated.buildCounts.thirdBuilds)))
        }

        val oldOutcomeCounts = JSONObject().put("targetRegressedCases", 2).put("collateralRegressionCases", 1)
        val generationTimings = generationMillisByCase.values.sorted()
        val medianGenerationMs = if (generationTimings.isEmpty()) 0.0 else if (generationTimings.size % 2 == 1) {
            generationTimings[generationTimings.size / 2].toDouble()
        } else {
            (generationTimings[generationTimings.size / 2 - 1] + generationTimings[generationTimings.size / 2]) / 2.0
        }
        val priorityDeterministic = generatedByCase.values.all { generated ->
            generated.comparison?.experimental?.personalizedDecision?.planningBudget?.execution
                ?.finiteAllocationPriorityOrder.orEmpty().let { rows -> rows == rows.sortedWith(canonicalOrder()) }
        }
        val syntheticPriorityRows = listOf(
            FiniteAllocationPriorityRow(StimulusPrescriptionOwnerIdentity("regional-primary", "MOVEMENT"),
                priority = 100, resistance = false, styleVariant = "", requestedUnits = 3, fundedUnits = 0),
            FiniteAllocationPriorityRow(StimulusPrescriptionOwnerIdentity("base-secondary", "BASE"),
                priority = 50, resistance = true, styleVariant = "", requestedUnits = 3, fundedUnits = 0)
        )
        val syntheticBefore = syntheticPriorityRows.sortedWith(oldPreC35Order())
        val syntheticAfter = syntheticPriorityRows.sortedWith(canonicalOrder())
        return JSONObject()
            .put("phase", "C35")
            .put("title", "Regional Hypertrophy semantics and finite-capacity priority")
            .put("startSha", startSha)
            .put("c34Before", JSONObject()
                .put("sourceCensus", "docs/c34-removed-identity-provenance-census.json")
                .put("qualityHypertrophyRegressionCases", oldOutcomeCounts.getInt("targetRegressedCases"))
                .put("collateralRegressionCases", oldOutcomeCounts.getInt("collateralRegressionCases"))
                .put("route", "CONTROL=22"))
            .put("cases", cases)
            .put("regionalTargets", JSONArray(regionalRows))
            .put("summary", JSONObject()
                .put("generatedCases", cases.length())
                .put("regionalHypertrophyTargetRows", regionalRows.size)
                .put("regionalRawResidualWeeklyUnits", regionalRawResidual)
                .put("regionalAuthorizedWholeSetUnits", regionalAuthorized)
                .put("regionalFundedUnits", regionalFunded)
                .put("regionalMaterializedCompatibleUnits", regionalMaterialized)
                .put("regionalUnfundedUnits", regionalUnfunded)
                .put("regionalUnmaterializedShortfallUnits", regionalUnmaterialized)
                .put("aggregateOnlyFalseRegressionCandidates", aggregateFalseRegressionCandidates)
                .put("aggregateHypertrophyRegressionCasesAfter", hypertrophyAggregateRegressionCases)
                .put("regionalHypertrophyOverrunTargetsAfter", regionalOverrunTargets)
                .put("collateralRegressionCasesAfter", collateralCases)
                .put("duplicatePhysicalSetRows", duplicatePhysicalSetRows)
                .put("unauthorizedMaterialRows", unauthorizedMaterialRows)
                .put("totalCompetingMaterialOwners", totalCompetingOwners)
                .put("priorityInversionsBefore", beforeInversions)
                .put("priorityInversionsAfter", afterInversions)
                .put("syntheticPriorityProbe", JSONObject()
                    .put("beforeOrder", JSONArray(syntheticBefore.map { it.owner.stableKey }))
                    .put("beforeInversions", priorityInversions(syntheticBefore))
                    .put("afterOrder", JSONArray(syntheticAfter.map { it.owner.stableKey }))
                    .put("afterInversions", priorityInversions(syntheticAfter)))
                .put("highPriorityFunded", highPriorityFunded)
                .put("highPriorityCapacityRejected", highPriorityCapacityRejected)
                .put("lowerPriorityFundedWhileHigherPriorityCapacityRejected", lowerFundedWhileHigherRejected)
                .put("equalPriorityOrderMatchesDeterministicCanonicalTieBreak", priorityDeterministic)
                .put("targetRegressedCasesAfter", targetRegressionCases)
                .put("collateralRegressionCasesAfter", collateralCases)
                .put("changeProvenanceUnclosedCasesAfter", provenanceUnclosedCases)
                .put("unexplainedRemovedOccurrencesAfter", unexplainedRemovedOccurrences)
                .put("unexplainedPrescriptionChangeCasesAfter", unexplainedPrescriptionCases)
                .put("routes", JSONObject(routes.toMap()))
                .put("b7ReasonCaseCounts", JSONObject(b7ReasonCounts.toMap()))
                .put("b8StatusCaseCounts", JSONObject(b8StatusCounts.toMap()))
                .put("buildAccounting", JSONObject()
                    .put("control", generatedByCase.values.sumOf { it.buildCounts.controlBuilds })
                    .put("experimental", generatedByCase.values.sumOf { it.buildCounts.experimentalBuilds })
                    .put("total", generatedByCase.values.sumOf { it.buildCounts.totalBuildInvocations })
                    .put("third", generatedByCase.values.sumOf { it.buildCounts.thirdBuilds })))
            .put("invariants", JSONObject()
                .put("noPriorityInversionAfterOrdering", afterInversions == 0)
                .put("noDuplicatePhysicalSetRows", duplicatePhysicalSetRows == 0)
                .put("noUnauthorizedMaterialRows", unauthorizedMaterialRows == 0)
                .put("noThirdBuild", generatedByCase.values.all { it.buildCounts.thirdBuilds == 0 }))
            .put("performance", JSONObject()
                .put("measuredGeneratedCases", generationTimings.size)
                .put("totalGenerationMs", generationTimings.sum())
                .put("meanPerCaseMs", generationTimings.average().takeIf { generationTimings.isNotEmpty() } ?: 0.0)
                .put("medianPerCaseMs", medianGenerationMs)
                .put("maxPerCaseMs", generationTimings.maxOrNull() ?: 0L))
            .toString(2)
    }

    private fun oldPreC35Order(): Comparator<FiniteAllocationPriorityRow> =
        compareByDescending<FiniteAllocationPriorityRow> { it.resistance }
            .thenByDescending { it.priority }
            .thenBy { it.owner.stableKey }
            .thenBy { it.owner.selectionRole }
            .thenBy { it.styleVariant }

    private fun canonicalOrder(): Comparator<FiniteAllocationPriorityRow> =
        compareByDescending<FiniteAllocationPriorityRow> { it.priority }
            .thenByDescending { it.resistance }
            .thenBy { it.owner.stableKey }
            .thenBy { it.owner.selectionRole }
            .thenBy { it.styleVariant }

    private fun priorityInversions(rows: List<FiniteAllocationPriorityRow>): Int {
        var result = 0
        for (left in rows.indices) for (right in left + 1 until rows.size) {
            if (rows[left].priority < rows[right].priority) result++
        }
        return result
    }

    private fun distance(range: StimulusTargetRange, value: Double): Double = when {
        value < range.min -> range.min - value
        value > range.max -> value - range.max
        else -> 0.0
    }

    private fun compareDistance(control: Double, experimental: Double): StimulusExperimentalTargetOutcomeStatus = when {
        experimental < control -> StimulusExperimentalTargetOutcomeStatus.IMPROVED
        experimental > control -> StimulusExperimentalTargetOutcomeStatus.REGRESSED
        else -> StimulusExperimentalTargetOutcomeStatus.UNCHANGED
    }
}
