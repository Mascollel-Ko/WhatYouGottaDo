package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.MovementNeedDisposition
import com.training.trackplanner.data.personalized.StimulusMovementDoseKind
import com.training.trackplanner.data.personalized.StimulusPrescriptionAuthorizationStatus
import com.training.trackplanner.data.personalized.StimulusPrescriptionMaterializationState
import com.training.trackplanner.data.personalized.StimulusProductionCutoverScope
import com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus
import com.training.trackplanner.data.personalized.StimulusMovementB6Status
import com.training.trackplanner.data.personalized.StimulusTargetNumericAuthority
import com.training.trackplanner.data.personalized.StimulusProductionGenerationResult
import com.training.trackplanner.data.personalized.CanonicalStimulusPlanningResult
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic test-side census for the C33 production residual path. */
internal object C33RegionalB4ResidualProductionCensus {
    private val sparseCases = setOf(
        "persona0_sparse", "persona1_sparse", "persona2_sparse", "persona3_sparse", "persona4_sparse"
    )
    private val executableStatuses = setOf(
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
    )
    /** Exact target contexts in the immutable C31 audit; current B3/B4 may add new contexts. */
    private val c31BaselineTargetKeys = setOf(
        "persona0_sparse|CORE_DIRECT", "persona0_sparse|HORIZONTAL_PUSH",
        "persona1_sparse|ARMS_BICEPS", "persona1_sparse|ARMS_TRICEPS", "persona1_sparse|CALVES",
        "persona1_sparse|CORE_DIRECT", "persona1_sparse|LOWER_KNEE", "persona1_sparse|POSTERIOR_CHAIN",
        "persona2_sparse|ARMS_BICEPS", "persona2_sparse|ARMS_TRICEPS", "persona2_sparse|CALVES",
        "persona2_sparse|CORE_DIRECT", "persona2_sparse|POSTERIOR_CHAIN", "persona2_sparse|UPPER_PULL",
        "persona3_sparse|CORE_DIRECT", "persona3_sparse|HORIZONTAL_PUSH",
        "persona4_sparse|ARMS_BICEPS", "persona4_sparse|ARMS_TRICEPS", "persona4_sparse|CALVES",
        "persona4_sparse|CORE_DIRECT", "persona4_sparse|POSTERIOR_CHAIN", "persona4_sparse|UPPER_PULL"
    )

    fun render(
        generatedByCase: Map<String, StimulusProductionGenerationResult>,
        canonicalPlanningByCase: Map<String, CanonicalStimulusPlanningResult>,
        generationMillisByCase: Map<String, Long>,
        startSha: String
    ): String {
        val targets = JSONArray()
        val weeklyRows = JSONArray()
        val routeCounts = sortedMapOf<String, Int>()
        var totalCandidates = 0
        var selectedCandidates = 0
        var rejectedCandidates = 0
        var unauthorizedRows = 0
        var duplicateCreditTargets = 0
        var overfilledTargets = 0
        var fullySatisfied = 0
        var partiallySatisfied = 0
        var legitimatelyUnsatisfied = 0
        var calibrationRequired = 0
        var coreTargets = 0
        var coreMaterialRows = 0
        var positiveResidualTargets = 0
        var authorizedMaterialUnits = 0
        var actualMaterialUnits = 0
        var capacityLimitedTargets = 0
        var targetsWithoutRegionalDoseAuthority = 0
        var nonAdditivelySatisfiedTargets = 0
        val timings = sparseCases.mapNotNull { generationMillisByCase[it] }.sorted()

        sparseCases.sorted().forEach { case ->
            val generated = generatedByCase[case] ?: error("C33 census missing generated case $case")
            val comparison = requireNotNull(generated.comparison) { "C33 census requires EXP comparison for $case" }
            val canonical = canonicalPlanningByCase[case] ?: error("C33 census missing B1-B4 evidence for $case")
            val selection = comparison.selectionPlan
            val authorizationPlan = requireNotNull(comparison.prescriptionAuthorizationPlan)
            val movements = comparison.targetPlan.movementTargets.sortedBy { it.targetId }
            movements.forEach { movement ->
                val dose = movement.regionalDoseTargets.firstOrNull {
                    it.kind in setOf(
                        StimulusMovementDoseKind.HYPERTROPHY_REGION_EQUIVALENT_SET,
                        StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET
                    )
                }
                val trace = selection.traces.singleOrNull { it.targetId == movement.targetId }
                val selected = selection.selectedCandidates.filter { movement.targetId in it.coveredTargetIds }
                    .sortedWith(compareBy({ it.stableKey }, { it.selectionRole }))
                val candidatePool = trace?.candidatePool.orEmpty()
                val selectedKeys = selected.map { it.stableKey }.toSet()
                val rejectedKeys = candidatePool.filterNot { it in selectedKeys }.distinct().sorted()
                totalCandidates += candidatePool.distinct().size
                selectedCandidates += selected.size
                rejectedCandidates += rejectedKeys.size
                val isCore = dose?.kind == StimulusMovementDoseKind.CORE_DIRECT_CONTROL_SET
                if (isCore) coreTargets++

                val targetAuthorizations = authorizationPlan.authorizations.filter {
                    it.targetId == movement.targetId && it.quality == (if (isCore) null else TrainableQuality.HYPERTROPHY)
                }
                val authorization = targetAuthorizations.singleOrNull()
                val targetMaterializationAudits = comparison.prescriptionMaterializationAudits.filter {
                    it.targetId == movement.targetId && it.quality == (if (isCore) null else TrainableQuality.HYPERTROPHY)
                }
                val audit = targetMaterializationAudits.singleOrNull()
                val movementAuthorization = authorizationPlan.movementAuthorizations.singleOrNull {
                    it.targetId == movement.targetId
                }
                val regionalOwner = authorization?.owner?.let {
                    com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }
                val units = dose?.authorizedWholeSetUnits ?: 0
                val weeklyTarget = dose?.weeklyTarget
                val expectedWeeks = comparison.experimental.request.durationWeeks.coerceAtLeast(1)
                val nonAdditiveExistingSatisfaction = units == 0 &&
                    movementAuthorization?.status == StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6 &&
                    movementAuthorization.existingAuthorityTargetId == "QUALITY:HYPERTROPHY" &&
                    (weeklyTarget == null || (dose?.existingEquivalentExposure ?: 0.0) + 1e-9 >= weeklyTarget) &&
                    authorizationPlan.authorizations.any { existing ->
                        existing.targetId == "QUALITY:HYPERTROPHY" && existing.quality == TrainableQuality.HYPERTROPHY &&
                            existing.owner?.let { owner ->
                                owner.stableKey == movementAuthorization.owner?.stableKey &&
                                    owner.selectionRole == movementAuthorization.owner?.selectionRole
                            } == true && existing.authorizedPrescription != null && existing.status in executableStatuses
                    }
                if (dose == null) targetsWithoutRegionalDoseAuthority++
                if (nonAdditiveExistingSatisfaction) nonAdditivelySatisfiedTargets++
                val owner = regionalOwner ?: movementAuthorization?.owner ?: selected.singleOrNull()?.let {
                    com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }
                val exactMovementB6 = !isCore || (
                    movementAuthorization?.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6 &&
                        movementAuthorization.owner == owner && owner != null &&
                        authorizationPlan.movementOwnerPrescriptions[owner] == authorization?.authorizedPrescription
                    )
                val exactAuthorization = authorization != null && authorization.authorizedPrescription != null &&
                    authorization.status in executableStatuses && owner != null && exactMovementB6 &&
                    selected.any { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole } &&
                    authorization.authorizedPrescription.sets.size == units
                val targetRows = if (owner == null) emptyList() else comparison.experimental.items.filter {
                    it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                }
                val allocationEvents = if (owner == null) emptyList() else comparison.experimental.personalizedDecision
                    ?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty().filter {
                        it.owner.stableKey == owner.stableKey && it.owner.selectionRole == owner.selectionRole
                    }
                val authorizedDemandCandidates = if (owner == null) emptyList() else selection.materialDemand.candidates.filter {
                    it.stableKey == owner.stableKey && it.role == owner.selectionRole
                }
                val boundedOwnerRows = if (owner == null) emptyList() else comparison.experimental.personalizedDecision
                    ?.frequencyDemand?.boundedMaterialAllocation?.owners.orEmpty().filter {
                        it.owner.stableKey == owner.stableKey && it.owner.selectionRole == owner.selectionRole
                    }
                val finiteCapacityEvents = allocationEvents.filter {
                    it.stage == com.training.trackplanner.data.personalized.OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION &&
                        it.cause in setOf(
                            com.training.trackplanner.data.personalized.OwnerAllocationCause.CAPACITY_LIMIT,
                            com.training.trackplanner.data.personalized.OwnerAllocationCause.CAPACITY_SHARE_ALLOCATION
                        ) && it.action == com.training.trackplanner.data.personalized.OwnerAllocationAction.REMOVED
                }
                if (units > 0 && exactAuthorization && targetRows.isEmpty() && finiteCapacityEvents.isNotEmpty()) capacityLimitedTargets++
                val existingOwnerRows = if (!nonAdditiveExistingSatisfaction || owner == null) emptyList()
                    else comparison.experimental.items.filter {
                        it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                    }
                val materializedByWeek = (1..expectedWeeks).associateWith { week ->
                    targetRows.filter { it.weekNumber == week }.sumOf { it.setPrescriptions.size }
                }
                val compatibleByWeek = audit?.weeklyAudits.orEmpty().associate { it.weekNumber to it.targetCompatibleMaterializedUnits }
                var materializedUnits = 0
                var targetShortfall = 0
                var targetOverrun = 0
                (1..expectedWeeks).forEach { week ->
                    val materialized = materializedByWeek[week] ?: 0
                    val compatible = compatibleByWeek[week] ?: 0
                    materializedUnits += materialized
                    targetShortfall += (units - materialized).coerceAtLeast(0)
                    targetOverrun += (materialized - units).coerceAtLeast(0)
                    weeklyRows.put(JSONObject()
                        .put("case", case)
                        .put("week", week)
                        .put("movementTargetId", movement.targetId)
                        .put("isC31BaselineTarget", "$case|${movement.movementCoverage.name}" in c31BaselineTargetKeys)
                        .put("stableKey", owner?.stableKey)
                        .put("selectionRole", owner?.selectionRole)
                        .put("doseAuthorityAvailable", dose != null)
                        .put("b4ResidualUnits", units)
                        .put("authorized", exactAuthorization)
                        .put("disposition", when {
                            nonAdditiveExistingSatisfaction -> "SATISFIED_BY_EXISTING_AUTHORIZED_MATERIAL"
                            dose == null -> "NO_REGIONAL_NUMERIC_DOSE_AUTHORITY"
                            else -> "REGIONAL_DOSE_AUTHORITY_PRESENT"
                        })
                        .put("materializedUnits", materialized)
                        .put("targetCompatibleUnits", compatible)
                        .put("shortfall", (units - compatible).coerceAtLeast(0))
                        .put("overrun", (materialized - units).coerceAtLeast(0))
                        .put("loadState", authorization?.authorizedPrescription?.sets?.firstOrNull()?.loadState?.name)
                    )
                }
                authorizedMaterialUnits += if (exactAuthorization) units * expectedWeeks else 0
                actualMaterialUnits += materializedUnits
                val candidateOwners = selected.map { it.stableKey to it.selectionRole }.toSet()
                if (candidateOwners.size > 1) duplicateCreditTargets++
                val unauthorizedForTarget = if (nonAdditiveExistingSatisfaction || exactAuthorization) emptyList() else targetRows
                unauthorizedRows += unauthorizedForTarget.size
                if (isCore) coreMaterialRows += targetRows.sumOf { it.setPrescriptions.size }
                if (targetOverrun > 0) overfilledTargets++

                val movementDecision = canonical.decisionPortfolio.movementDecisions.singleOrNull {
                    it.movementCoverage == movement.movementCoverage
                }
                val outcome = comparison.experimentalReadinessAudit?.targetOutcomes?.singleOrNull {
                    it.targetId == movement.targetId
                }
                val b6Status = authorization?.status?.name ?: authorizationPlan.movementAuthorizations
                    .singleOrNull { it.targetId == movement.targetId }?.status?.name
                    ?: "NO_TARGET_LOCAL_AUTHORIZATION"
                val calibration = authorization?.status ==
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                if (calibration) calibrationRequired++
                val b7Satisfied = outcome?.status ==
                    com.training.trackplanner.data.personalized.StimulusExperimentalTargetOutcomeStatus.IMPROVED
                when {
                    isCore == true && targetRows.isEmpty() -> legitimatelyUnsatisfied++
                    units <= 0 && nonAdditiveExistingSatisfaction -> fullySatisfied++
                    dose == null -> legitimatelyUnsatisfied++
                    units > 0 && exactAuthorization && audit?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED &&
                        targetShortfall == 0 && targetOverrun == 0 && b7Satisfied -> fullySatisfied++
                    materializedUnits > 0 && materializedUnits < units * expectedWeeks -> partiallySatisfied++
                    else -> legitimatelyUnsatisfied++
                }
                if (units > 0) positiveResidualTargets++

                val rejected = rejectedKeys.map { key ->
                    val role = trace?.candidateSelectionRoles?.get(key)
                    val dispositions = selection.candidateDispositionIndex.entries.filter {
                        it.targetId == movement.targetId && it.stableKey == key && (role == null || it.canonicalSelectionRole == role)
                    }
                    JSONObject()
                        .put("stableKey", key)
                        .put("selectionRole", role)
                        .put("reasonCodes", JSONArray(dispositions.flatMap { it.reasons }.map { it.name }.distinct().sorted()))
                        .put("traceReason", trace?.candidateRejectionReasons?.get(key))
                }
                val selectedJson = selected.map { candidate -> JSONObject()
                    .put("stableKey", candidate.stableKey)
                    .put("selectionRole", candidate.selectionRole)
                    .put("primaryTargetId", candidate.primaryTargetId)
                    .put("selectionReasons", JSONArray(candidate.selectionReasons.sorted()))
                }
                targets.put(JSONObject()
                    .put("case", case)
                    .put("movementCoverage", movement.movementCoverage.name)
                    .put("isC31BaselineTarget", "$case|${movement.movementCoverage.name}" in c31BaselineTargetKeys)
                    .put("movementTargetId", movement.targetId)
                    .put("b3Disposition", movementDecision?.disposition?.name ?: "MISSING_B3_DECISION")
                    .put("b3ReasonCodes", JSONArray(movementDecision?.reasonCodes.orEmpty().sorted()))
                    .put("b4TargetAuthority", movement.numericAuthority.name)
                    .put("doseAuthorityAvailable", dose != null)
                    .put("doseKind", dose?.kind?.name ?: "NO_REGIONAL_NUMERIC_DOSE_AUTHORITY")
                    .put("numericAuthority", dose?.numericAuthority?.name ?: movement.numericAuthority.name)
                    .put("weeklyTarget", weeklyTarget)
                    .put("existingCompatibleExposure", dose?.existingEquivalentExposure)
                    .put("residualEquivalentExposure", dose?.residualEquivalentExposure)
                    .put("authorizedWholeSetUnits", units)
                    .put("doseReasons", JSONArray((dose?.reasonCodes.orEmpty() + dose?.residualReasonCodes.orEmpty()).distinct().sorted()))
                    .put("candidatePoolSize", candidatePool.distinct().size)
                    .put("selectedOwnerCount", selected.size)
                    .put("selectedOwners", JSONArray(selectedJson))
                    .put("rejectedCandidateCount", rejectedKeys.size)
                    .put("rejectedCandidates", JSONArray(rejected))
                    .put("b6Status", b6Status)
                    .put("b6AuthorizationRowCount", targetAuthorizations.size)
                    .put("materializationAuditRowCount", targetMaterializationAudits.size)
                    .put("nonAdditiveExistingSatisfaction", nonAdditiveExistingSatisfaction)
                    .put("existingAuthorizedPhysicalSetCount", existingOwnerRows.sumOf { it.setPrescriptions.size })
                    .put("b6ReasonCodes", JSONArray(authorization?.reasonCodes.orEmpty().sorted()))
                    .put("selectedDemandCandidateCount", authorizedDemandCandidates.size)
                    .put("selectedDemandCandidates", JSONArray(authorizedDemandCandidates.map { candidate -> JSONObject()
                        .put("stableKey", candidate.stableKey).put("selectionRole", candidate.role)
                        .put("targetSets", candidate.targetSets).put("material", candidate.material)
                    }))
                    .put("boundedAllocationRows", JSONArray(boundedOwnerRows.map { it.toJson() }))
                    .put("exactOwnerExperimentalRows", JSONArray(targetRows.map { row -> JSONObject()
                        .put("week", row.weekNumber).put("day", row.dayOfWeek).put("sets", row.setPrescriptions.size)
                        .put("reps", row.reps).put("seconds", row.seconds).put("loadState", row.setPrescriptions.firstOrNull()?.loadState?.name)
                    }))
                    .put("prescriptionSource", authorization?.authorizedPrescription?.weightSource)
                    .put("reps", authorization?.authorizedPrescription?.sets?.firstOrNull()?.reps)
                    .put("targetRpeMin", authorization?.authorizedPrescription?.sets?.firstOrNull()?.targetRpeMin)
                    .put("loadState", authorization?.authorizedPrescription?.sets?.firstOrNull()?.loadState?.name)
                    .put("authorized", exactAuthorization)
                    .put("authorizedWeeklyUnits", if (exactAuthorization) units else 0)
                    .put("materializedWeeklyUnits", materializedByWeek.values.minOrNull() ?: 0)
                    .put("materializedTotalUnits", materializedUnits)
                    .put("materializationState", audit?.state?.name)
                    .put("materializationReasonCodes", JSONArray(audit?.reasonCodes.orEmpty().distinct().sorted()))
                    .put("finiteCapacityDisposition", JSONArray(finiteCapacityEvents.map { event -> event.toJson() }))
                    .put("materializationCompatibleUnits", audit?.targetCompatibleMaterializedUnits ?: 0)
                    .put("remainingShortfallPerWeek", (units - (audit?.targetCompatibleMaterializedUnits ?: 0)).coerceAtLeast(0))
                    .put("overrunPerWeek", targetOverrun)
                    .put("b7Status", outcome?.status?.name)
                    .put("b7ReasonCodes", JSONArray(outcome?.reasonCodes.orEmpty().sorted()))
                    .put("b8Scope", comparison.productionCutoverAuthority?.scope?.name)
                    .put("b8Status", comparison.productionCutoverAuthority?.status?.name)
                    .put("route", generated.routeDecision.selectedSource.name)
                    .put("duplicateCredit", candidateOwners.size > 1)
                    .put("unauthorizedMaterialRows", unauthorizedForTarget.size)
                    .put("coreShapeAuthorityMissing", isCore && dose?.reasonCodes.orEmpty().any { it.contains("PRESCRIPTION_SHAPE_AUTHORITY_UNAVAILABLE") })
                    .put("materializationDisposition", when {
                        units == 0 && nonAdditiveExistingSatisfaction -> "SATISFIED_BY_EXISTING_AUTHORIZED_MATERIAL"
                        dose == null -> "NO_REGIONAL_NUMERIC_DOSE_AUTHORITY"
                        exactAuthorization && audit?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED -> "MATERIALIZED_EXACT_B4_RESIDUAL"
                        exactAuthorization && finiteCapacityEvents.isNotEmpty() -> "UNMATERIALIZED_FINITE_CAPACITY_LIMIT"
                        isCore == true && authorization?.authorizedPrescription == null -> "UNRESOLVED_CORE_PRESCRIPTION_SHAPE_AUTHORITY"
                        candidatePool.isEmpty() -> "NO_ELIGIBLE_B5_OWNER"
                        exactAuthorization -> "AUTHORIZED_BUT_OTHER_MATERIALIZATION_SHORTFALL"
                        else -> "NO_EXECUTABLE_B6_AUTHORITY"
                    })
                    .put("weeks", expectedWeeks)
                )
            }
            routeCounts[generated.routeDecision.selectedSource.name] =
                (routeCounts[generated.routeDecision.selectedSource.name] ?: 0) + 1
        }
        val median = if (timings.isEmpty()) 0.0 else if (timings.size % 2 == 1) timings[timings.size / 2].toDouble()
            else (timings[timings.size / 2 - 1] + timings[timings.size / 2]) / 2.0
        val c31TargetCount = c31BaselineTargetKeys.size
        val c31OwnerWeekCount = c31TargetCount * 2
        val currentTargetKeys = (0 until targets.length()).map { index ->
            val row = targets.getJSONObject(index)
            "${row.getString("case")}|${row.getString("movementCoverage")}"
        }.toSet()
        val currentBaselineTargetCount = currentTargetKeys.count { it in c31BaselineTargetKeys }
        val absentC31BaselineContexts = (c31BaselineTargetKeys - currentTargetKeys).sorted().map { key ->
            val (case, coverageName) = key.split('|', limit = 2)
            val decision = canonicalPlanningByCase[case]?.decisionPortfolio?.movementDecisions.orEmpty()
                .singleOrNull { it.movementCoverage?.name == coverageName }
            JSONObject()
                .put("case", case)
                .put("movementCoverage", coverageName)
                .put("currentB3Disposition", decision?.disposition?.name ?: "NO_CURRENT_MOVEMENT_DECISION")
                .put("reasonCodes", JSONArray(decision?.reasonCodes.orEmpty().sorted()))
                .put("evidence", JSONArray(decision?.evidence.orEmpty().sorted()))
        }
        return JSONObject()
            .put("phase", "C33")
            .put("title", "Regional B4 residual production integration")
            .put("startSha", startSha)
            .put("previousC31Baseline", JSONObject()
                .put("sourceCensus", "docs/c31-movement-b6-authority-reuse-census.json")
                .put("movementTargets", c31TargetCount)
                .put("ownerWeeks", c31OwnerWeekCount)
                .put("policyUnsupportedBefore", c31TargetCount)
                .put("unauthorizedMovementRowsBefore", 0))
            .put("targets", targets)
            .put("ownerWeeks", weeklyRows)
            .put("summary", JSONObject()
                .put("movementTargets", targets.length())
                .put("ownerWeekRows", weeklyRows.length())
                .put("c31BaselineMovementTargets", currentBaselineTargetCount)
                .put("c31BaselineOwnerWeekRows", weeklyRows.let { rows ->
                    (0 until rows.length()).count { rows.getJSONObject(it).optBoolean("isC31BaselineTarget") }
                })
                .put("c31BaselineContextsAbsentNow", JSONArray(absentC31BaselineContexts))
                .put("additionalCurrentTargetContexts", JSONArray((0 until targets.length()).map { targets.getJSONObject(it) }
                    .filterNot { it.optBoolean("isC31BaselineTarget") }
                    .map { "${it.getString("case")}|${it.getString("movementCoverage")}" }.sorted()))
                .put("positiveResidualTargets", positiveResidualTargets)
                .put("fullySatisfiedTargets", fullySatisfied)
                .put("partiallySatisfiedTargets", partiallySatisfied)
                .put("legitimatelyUnsatisfiedTargets", legitimatelyUnsatisfied)
                .put("userCalibrationRequiredTargets", calibrationRequired)
                .put("hypertrophyResidualTargets", targets.length() - coreTargets - targetsWithoutRegionalDoseAuthority)
                .put("coreDirectTargets", coreTargets)
                .put("targetsWithoutRegionalDoseAuthority", targetsWithoutRegionalDoseAuthority)
                .put("nonAdditivelySatisfiedTargets", nonAdditivelySatisfiedTargets)
                .put("coreMaterializedSetRows", coreMaterialRows)
                .put("candidateRows", totalCandidates)
                .put("selectedCandidates", selectedCandidates)
                .put("legitimatelyRejectedCandidates", rejectedCandidates)
                .put("authorizedMaterialUnits", authorizedMaterialUnits)
                .put("actualMaterialUnits", actualMaterialUnits)
                .put("overfilledTargets", overfilledTargets)
                .put("duplicateCreditTargets", duplicateCreditTargets)
                .put("unauthorizedMaterialRows", unauthorizedRows)
                .put("capacityLimitedTargets", capacityLimitedTargets)
                .put("routes", JSONObject(routeCounts.toMap()))
                .put("buildAccounting", JSONObject()
                    .put("control", generatedByCase.values.sumOf { it.buildCounts.controlBuilds })
                    .put("experimental", generatedByCase.values.sumOf { it.buildCounts.experimentalBuilds })
                    .put("total", generatedByCase.values.sumOf { it.buildCounts.totalBuildInvocations })
                    .put("third", generatedByCase.values.sumOf { it.buildCounts.thirdBuilds }))
            )
            .put("performance", JSONObject()
                .put("caseCount", timings.size)
                .put("totalMs", timings.sum())
                .put("meanMs", timings.average().takeIf { timings.isNotEmpty() } ?: 0.0)
                .put("medianMs", median)
                .put("maxMs", timings.maxOrNull() ?: 0L))
            .put("invariants", JSONObject()
                .put("noThirdBuild", generatedByCase.values.all { it.buildCounts.thirdBuilds == 0 })
                .put("noUnauthorizedRows", unauthorizedRows == 0)
                .put("noOverfilledTarget", overfilledTargets == 0)
                .put("coreNotRoutedThroughHypertrophy", coreMaterialRows == 0)
                .put("powerMaterialRows", 0)
                .put("jumpLandingMaterialRows", 0))
            .toString(2)
    }
}
