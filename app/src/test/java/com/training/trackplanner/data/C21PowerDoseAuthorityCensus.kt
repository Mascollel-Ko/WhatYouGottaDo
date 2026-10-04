package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.data.personalized.*
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic C21 audit output. It describes evidence; it grants no Power authority. */
internal object C21PowerDoseAuthorityCensus {
    private const val POWER_OWNER_KEY = "ex_314df428"

    fun render(
        records: List<Pair<StimulusProductionCoverageAuditTest.CoverageSpec, StimulusProductionGenerationResult? >>,
        planningByCase: Map<String, CanonicalStimulusPlanningResult>,
        contextByCase: Map<String, PreparedCanonicalGenerationContext>,
        metadataRepository: CanonicalExerciseMetadataRepository
    ): String {
        val physicalRelations = metadataRepository.physicalQualityRelations()
        val badmintonObjectiveCatalog = metadataRepository.badmintonObjectiveCatalog()
        val reviewedMembership = RecordBasedReviewedPolicy.badmintonKeys.entries
            .firstOrNull { (_, keys) -> POWER_OWNER_KEY in keys }
        val reviewedGuide = reviewedMembership?.let { RecordBasedReviewedPolicy.badminton(it.key) }
        val reviewedCategory = reviewedMembership?.key
        val reviewedGuideJson = reviewedGuide?.let { JSONObject()
            .put("setCount", it.setCount).put("reps", it.reps).put("seconds", it.seconds)
            .put("restSeconds", it.restSeconds).put("text", it.text).put("weightSource", it.weightSource)
        }
        val caseRows = JSONArray()
        var generatedCount = 0
        var directionOnlyBefore = 0
        var numericPowerAfter = 0
        var personalHistoryAuthorities = 0
        var reviewedStarterAuthorities = 0
        var stillDirectionOnly = 0
        var fullyMaterializedPower = 0
        var selectedPowerOwnerRows = 0
        var generatedPowerRows = 0
        val exactOwnerPowerObservationIds = linkedSetOf<String>()

        records.sortedBy { it.first.label }.forEach { (spec, result) ->
            val row = JSONObject().put("case", spec.label)
            if (result == null) {
                row.put("preflight", "REJECTED_NO_CONFIRMED_HISTORY")
                row.put("powerTarget", JSONObject.NULL)
                row.put("selectedPowerOwners", JSONArray())
                row.put("generatedPowerRows", JSONArray())
                caseRows.put(row)
                return@forEach
            }
            generatedCount++
            val comparison = requireNotNull(result.comparison) { "${spec.label}: no production comparison" }
            val planning = requireNotNull(planningByCase[spec.label]) { "${spec.label}: missing B1-B4 evidence" }
            val context = requireNotNull(contextByCase[spec.label]) { "${spec.label}: missing production source snapshot" }
            val powerNeed = planning.athleteStimulusNeedProfile.qualityNeeds.firstOrNull { it.quality == TrainableQuality.POWER }
            val powerDecision = planning.decisionPortfolio.qualityDecisions.firstOrNull { it.quality == TrainableQuality.POWER }
            val powerTarget = comparison.targetPlan.qualityTargets.firstOrNull { it.quality == TrainableQuality.POWER }
            if (powerTarget?.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) directionOnlyBefore++
            if (powerTarget?.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                )) numericPowerAfter++
            if (powerTarget?.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) stillDirectionOnly++

            val selectedPowerOwners = comparison.selectionPlan.selectedCandidates.filter { "QUALITY:POWER" in it.coveredTargetIds }
            selectedPowerOwnerRows += selectedPowerOwners.size
            val exactPowerOwnerHistory = context.snapshot.allConfirmedSets.filter { it.stableKey in selectedPowerOwners.map { owner -> owner.stableKey } }
            val allDirectPowerObservations = context.snapshot.stimulusExposureLedger.setObservations.filter { observation ->
                context.snapshot.stimulusExposureLedger.facetProfilesByStableKey[observation.facetProfileKey]
                    ?.physicalQualities?.any { it.qualityId == TrainableQuality.POWER && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } == true
            }
            val exactDirectPowerObservations = allDirectPowerObservations.filter { observation ->
                observation.source.stableKey in selectedPowerOwners.map { it.stableKey }
            }
            exactDirectPowerObservations.mapTo(exactOwnerPowerObservationIds) { it.source.portableObservationId }

            val powerResolution = comparison.prescriptionRealizationPlan?.resolutions?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val powerAuthorization = comparison.prescriptionAuthorizationPlan?.authorizations?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val powerMaterialization = comparison.prescriptionMaterializationAudits.firstOrNull { it.targetId == "QUALITY:POWER" }
            if (powerAuthorization?.authorizedPrescription != null) personalHistoryAuthorities++
            if (powerAuthorization?.reasonCodes?.contains("POWER_REVIEWED_TASK_STARTER_AUTHORIZED") == true) reviewedStarterAuthorities++
            if (powerMaterialization?.state == StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED) fullyMaterializedPower++

            val generatedRows = comparison.experimental.items.filter { item ->
                item.exerciseStableKey in selectedPowerOwners.map { it.stableKey } &&
                    selectedPowerOwners.any { it.selectionRole == item.selectionRole && it.stableKey == item.exerciseStableKey }
            }.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
                ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey))
            generatedPowerRows += generatedRows.size

            val powerBand = planning.qualityDoseHistory.bands[TrainableQuality.POWER]
            val powerB2 = JSONObject()
                .put("baselineObservability", planning.qualityDoseHistory.baselineObservability[TrainableQuality.POWER]?.name)
                .put("evidenceBasis", planning.qualityDoseHistory.evidenceBasis[TrainableQuality.POWER]?.name)
                .put("bandSource", powerBand?.source?.name)
                .put("eligibleWeeks", powerBand?.eligibleWeekCount ?: 0)
                .put("hasPersonalDirectBaseline", powerBand?.hasPersonalDirectBaseline ?: false)
                .put("weeklyDirectUnitsMedian", powerBand?.weeklyDirectUnitsMedian)
                .put("weeklyDirectSessionsMedian", powerBand?.weeklyDirectSessionsMedian)
                .put("directExposureWeekFrequency", powerBand?.directExposureWeekFrequency)
                .put("exactOwnerConfirmedSets", JSONArray(exactPowerOwnerHistory.sortedWith(compareBy({ it.date }, { it.setIndex }))
                    .map { set -> JSONObject().put("date", set.date.toString()).put("stableKey", set.stableKey)
                        .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds)
                        .put("rpe", set.rpe).put("setIndex", set.setIndex) }))
                .put("directPowerObservations", JSONArray(allDirectPowerObservations.sortedWith(compareBy({ it.source.stableKey }, { it.source.date }, { it.source.portableObservationId }))
                    .map { observation -> JSONObject().put("stableKey", observation.source.stableKey)
                        .put("observationId", observation.source.portableObservationId).put("date", observation.source.date.toString())
                        .put("activityKind", observation.activityKind.name).put("reps", observation.reps)
                        .put("weightKg", observation.weightKg).put("seconds", observation.seconds).put("rpe", observation.rpe)
                        .put("classification", observation.realizedStimulusClassification.kind.name)
                        .put("classificationAuthority", observation.classificationAuthority.name) }))
                .put("exactOwnerDirectPowerObservationCount", exactDirectPowerObservations.size)
                .put("sameQualityOtherOwnerObservationCount", allDirectPowerObservations.size - exactDirectPowerObservations.size)

            val ownerRows = JSONArray(selectedPowerOwners.sortedWith(compareBy({ it.stableKey }, { it.selectionRole })).map { owner ->
                JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                    .put("coveredTargets", JSONArray(owner.coveredTargetIds.sorted()))
                    .put("selectionReasons", JSONArray(owner.selectionReasons.sorted()))
                    .put("probeCompatibility", owner.probePrescriptionCompatibility.name)
                    .put("targetSetsFromExistingPrescription", owner.targetSetsFromExistingPrescription)
            })
            val powerRelations = physicalRelations.filter { it.exerciseStableKey == POWER_OWNER_KEY && it.qualityId == TrainableQuality.POWER }
            val objectiveRelations = badmintonObjectiveCatalog.relations(POWER_OWNER_KEY)
            val targetOutcome = comparison.experimentalReadinessAudit?.targetOutcomes?.firstOrNull { it.targetId == "QUALITY:POWER" }
            val b1Need = powerNeed?.let { need -> JSONObject().put("relevance", need.relevance.name)
                .put("decision", need.decision.name).put("confidence", need.confidence.name)
                .put("reasonCodes", JSONArray(need.reasonCodes.sorted())).put("evidence", JSONArray(need.evidence.sorted()))
                .put("directUnits28d", need.exposure.current28d.directUnits)
                .put("directSessions28d", need.exposure.current28d.directSessions)
                .put("supportiveUnits28d", need.exposure.current28d.supportiveUnits)
                .put("coverage", need.exposure.coverage.name)
            } ?: JSONObject.NULL
            val b1Tasks = JSONArray(planning.athleteStimulusNeedProfile.sportTaskNeeds.sortedBy { it.task }.map { need ->
                JSONObject().put("task", need.task).put("relevance", need.relevance.name)
                    .put("decision", need.decision.name).put("confidence", need.confidence.name)
                    .put("reasonCodes", JSONArray(need.reasonCodes.sorted())).put("evidence", JSONArray(need.evidence.sorted()))
            })
            val b3TaskDecisions = JSONArray(planning.decisionPortfolio.taskDecisions.sortedBy { it.task }.map { decision ->
                JSONObject().put("task", decision.task).put("strategy", decision.strategy.name)
                    .put("needDecision", decision.needDecision.name).put("priority", decision.priority.name)
                    .put("reasonCodes", JSONArray(decision.reasonCodes.sorted())).put("evidence", JSONArray(decision.evidence.sorted()))
            })
            val b4TaskTargets = JSONArray(comparison.targetPlan.taskTargets.sortedBy { it.task }.map { target ->
                JSONObject().put("task", target.task).put("strategy", target.strategy.name)
                    .put("numericAuthority", target.numericAuthority.name)
                    .put("weeklyDirectUnitsTarget", rangeJson(target.weeklyDirectUnitsTarget))
                    .put("weeklyDirectSessionsTarget", rangeJson(target.weeklyDirectSessionsTarget))
                    .put("reasonCodes", JSONArray(target.reasonCodes.sorted()))
            })
            val currentPrescriptionSource = selectedPowerOwners.firstOrNull()?.let { owner ->
                PerformancePrescriptionResolver.resolve(context.snapshot, owner.stableKey)
            }
            val selectedPowerKey = selectedPowerOwners.firstOrNull()?.stableKey ?: POWER_OWNER_KEY
            val canonicalExercise = context.snapshot.exercises[selectedPowerKey]
            val runtimeMetadata = context.snapshot.metadata[selectedPowerKey]
            val generatedPowerRowsJson = JSONArray(generatedRows.map { item ->
                JSONObject().put("week", item.weekNumber).put("day", item.dayOfWeek).put("order", item.orderIndex)
                    .put("stableKey", item.exerciseStableKey).put("selectionRole", item.selectionRole)
                    .put("quality", "POWER").put("setCount", item.setCount).put("reps", item.reps)
                    .put("sets", JSONArray(item.setPrescriptions.map { set -> JSONObject().put("setIndex", set.setIndex)
                        .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds)
                        .put("targetRpe", set.targetRpeMin) }))
                    .put("restSeconds", item.restSeconds).put("weightSource", item.weightSource)
                    .put("prescription", item.prescription)
            })
            val b6Auth = powerAuthorization?.let { auth -> JSONObject()
                .put("quality", auth.quality?.name).put("owner", auth.owner?.let { owner -> JSONObject()
                    .put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole) })
                .put("status", auth.status.name).put("reasonCodes", JSONArray(auth.reasonCodes.sorted()))
                .put("authorizedPrescriptionPresent", auth.authorizedPrescription != null)
                .put("inputPrescription", prescriptionJson(auth.inputPrescription))
                .put("authorizedPrescription", prescriptionJson(auth.authorizedPrescription))
            } ?: JSONObject.NULL
            val resolution = powerResolution?.let { value -> JSONObject()
                .put("targetId", value.targetId).put("quality", value.quality?.name)
                .put("evidenceBasis", value.evidenceBasis.name).put("strategy", value.strategy?.name)
                .put("numericAuthority", value.numericAuthority.name).put("status", value.status.name)
                .put("owner", value.owner?.let { JSONObject().put("stableKey", it.stableKey)
                    .put("selectionRole", it.selectionRole) } ?: JSONObject.NULL)
                .put("reasonCodes", JSONArray(value.reasonCodes.sorted()))
            } ?: JSONObject.NULL
            val materialization = powerMaterialization?.let { JSONObject()
                .put("state", it.state.name).put("authorizedWeeklySetUnits", it.authorizedWeeklySetUnits)
                .put("materializedWeeklySetUnits", it.materializedWeeklySetUnits)
                .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL
            val powerTargetJson = powerTarget?.let { target -> JSONObject()
                .put("strategy", target.strategy.name).put("priority", target.priority.name)
                .put("numericAuthority", target.numericAuthority.name).put("baselineSource", target.baselineSource?.name)
                .put("baselineConfidence", target.baselineConfidence?.name)
                .put("weeklyDirectUnitsTarget", rangeJson(target.weeklyDirectUnitsTarget))
                .put("weeklyDirectSessionsTarget", rangeJson(target.weeklyDirectSessionsTarget))
                .put("exposureWeekFrequencyReference", target.exposureWeekFrequencyReference)
                .put("reasonCodes", JSONArray(target.reasonCodes.sorted())).put("evidence", JSONArray(target.evidence.sorted()))
            } ?: JSONObject.NULL

            row.put("preflight", "GENERATED")
                .put("b1PowerNeed", b1Need)
                .put("b1TaskNeeds", b1Tasks)
                .put("b2PowerHistory", powerB2)
                .put("b3PowerDecision", powerDecision?.let { JSONObject()
                    .put("strategy", it.strategy.name).put("needDecision", it.needDecision.name)
                    .put("baselineAvailable", it.baselineAvailable).put("hasPersonalDirectBaseline", it.hasPersonalDirectBaseline)
                    .put("numericBaselineUsable", it.numericBaselineUsable).put("reasonCodes", JSONArray(it.reasonCodes.sorted()))
                    .put("evidence", JSONArray(it.evidence.sorted())) } ?: JSONObject.NULL)
                .put("b3TaskDecisions", b3TaskDecisions)
                .put("b4PowerTarget", powerTargetJson)
                .put("b4TaskTargets", b4TaskTargets)
                .put("b5SelectedPowerOwners", ownerRows)
                .put("canonicalPowerRelations", JSONArray(powerRelations.map { relation -> JSONObject()
                    .put("relationId", relation.relationId).put("quality", relation.qualityId.name)
                    .put("relationLevel", relation.relationLevel.name).put("region", relation.regionQualifier.name)
                    .put("mode", relation.modeQualifier.name).put("prescriptionDependent", relation.prescriptionDependent)
                    .put("provenance", relation.provenance).put("reviewStatus", relation.reviewStatus)
                    .put("notes", relation.notes) }))
                .put("badmintonObjectiveRelations", JSONArray(objectiveRelations.map(::objectiveJson)))
                .put("reviewedBadmintonCategory", reviewedCategory?.name)
                .put("reviewedCategoryMembers", JSONArray(reviewedCategory?.let { RecordBasedReviewedPolicy.badmintonKeys.getValue(it).sorted() }.orEmpty()))
                .put("reviewedPrescriptionGuide", reviewedGuideJson ?: JSONObject.NULL)
                .put("exerciseSemantics", JSONObject()
                    .put("stableKey", selectedPowerKey)
                    .put("name", canonicalExercise?.name)
                    .put("equipment", canonicalExercise?.equipment)
                    .put("runtimeActivityKind", context.snapshot.activityKind(selectedPowerKey).name)
                    .put("runtimePlanningEligibility", runtimeMetadata?.planningEligibility)
                    .put("canonicalFamilyRole", canonicalExercise?.familyRole)
                    .put("laterality", canonicalExercise?.laterality)
                    .put("weightSourceFromReviewedPrescription", currentPrescriptionSource?.source)
                    .put("bodyweightSemanticsExplicitInCanonicalExercise", canonicalExercise?.equipment?.contains("BODYWEIGHT") == true)
                )
                .put("currentPrescriptionSource", currentPrescriptionSource?.let { prescriptionAuthorityJson(it) } ?: JSONObject.NULL)
                .put("b6PowerResolution", resolution)
                .put("b6PowerAuthorization", b6Auth)
                .put("b6PowerMaterialization", materialization)
                .put("generatedPowerRows", generatedPowerRowsJson)
                .put("powerTargetOutcome", targetOutcome?.let { JSONObject().put("status", it.status.name)
                    .put("directlyAffected", it.directlyAffected).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("b7", comparison.experimentalReadinessAudit?.let { JSONObject().put("status", it.status.name)
                    .put("changeProvenanceClosed", it.changeProvenanceClosed)
                    .put("collateralRegressionFree", it.collateralRegressionFree)
                    .put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("b8", comparison.productionCutoverAuthority?.let { JSONObject().put("status", it.status.name)
                    .put("scope", it.scope?.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("route", result.routeDecision.selectedSource.name)
                .put("c20PowerInteraction", result.incumbentPlacementShadow?.let { shadow -> JSONObject()
                    .put("indexStatus", shadow.indexStatus.name)
                    .put("hardValid", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.HARD_VALID })
                    .put("hardInvalid", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.HARD_INVALID })
                    .put("unresolved", shadow.rows.count { it.feasibility == CanonicalIncumbentFeasibility.UNRESOLVED })
                    .put("preservationEvents", result.incumbentPlacementPreservations.size) } ?: JSONObject.NULL)
            caseRows.put(row)
        }

        val persona3 = records.firstOrNull { it.first.label == "persona3_reviewed" }?.second
        val persona3Comparison = persona3?.comparison
        val persona3PowerTarget = persona3Comparison?.targetPlan?.qualityTargets?.firstOrNull { it.quality == TrainableQuality.POWER }
        val powerRouteRows = records.mapNotNull { (spec, result) -> result?.comparison?.let { spec.label to it } }
            .filter { (_, comparison) -> comparison.selectionPlan.selectedCandidates.any { "QUALITY:POWER" in it.coveredTargetIds } }
        fun hasPowerB6Authority(comparison: StimulusSelectionProgramComparison) =
            comparison.prescriptionAuthorizationPlan?.authorizations?.any {
                it.quality == TrainableQuality.POWER && it.authorizedPrescription != null
            } == true
        val b7EligiblePowerCases = powerRouteRows.count { (_, comparison) ->
            comparison.experimentalReadinessAudit?.status == StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
        }
        val b8AuthorizedPowerCases = powerRouteRows.count { (_, comparison) ->
            hasPowerB6Authority(comparison) && comparison.productionCutoverAuthority?.status ==
                StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER
        }
        val powerRoutedCases = records.count { (_, result) ->
            val comparison = result?.comparison
            result?.routeDecision?.selectedSource != null &&
                result.routeDecision.selectedSource != StimulusProductionProgramSource.CONTROL &&
                comparison != null && hasPowerB6Authority(comparison)
        }
        val powerTargetsTotal = records.count { (_, result) ->
            result?.comparison?.targetPlan?.qualityTargets?.any { it.quality == TrainableQuality.POWER } == true
        }
        val routedResults = records.mapNotNull { it.second }
        val routeCounts = routedResults.groupingBy { it.routeDecision.selectedSource.name }.eachCount()
        val b7ReasonCounts = routedResults.flatMap { it.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }
            .groupingBy { it }.eachCount()
        val incumbentRows = routedResults.flatMap { it.incumbentPlacementShadow?.rows.orEmpty() }
        val incumbentFeasibilityCounts = incumbentRows.groupingBy { it.feasibility.name }.eachCount()

        val root = JSONObject()
            .put("schema", "c21-power-dose-authority-census-v2")
            .put("baselineMain", "69a58df6c210924cafbe7fc6ab478b8cba517c94")
            .put("startHead", "69a58df6c210924cafbe7fc6ab478b8cba517c94")
            .put("versions", JSONObject().put("protocol", "3.56.0")
                .put("runtime", "RECORD_BASED_PLANNER_0.14.8_KOTLIN_1").put("app", "0.5.1.5"))
            .put("standardCoverageSha256", "5BD1E9430352618C6C42F399ED28B8A065908CEDCA8F4448924BD9301CB44BD1")
            .put("policyConclusion", "POWER_REMAINS_DIRECTION_ONLY")
            .put("routeSnapshot", JSONObject()
                .put("CONTROL", routeCounts[StimulusProductionProgramSource.CONTROL.name] ?: 0)
                .put("STRENGTH_V1", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_V1.name] ?: 0)
                .put("STRENGTH_CALIBRATION_V1", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_CALIBRATION_V1.name] ?: 0)
                .put("HYPERTROPHY", routeCounts[StimulusProductionProgramSource.B8_HYPERTROPHY_V1.name] ?: 0)
                .put("COMBINED", routeCounts[StimulusProductionProgramSource.B8_STRENGTH_HYPERTROPHY_V1.name] ?: 0))
            .put("b7ReasonOccurrences", JSONObject()
                .put("CHANGE_PROVENANCE_UNCLOSED", b7ReasonCounts["CHANGE_PROVENANCE_UNCLOSED"] ?: 0)
                .put("AFFECTED_TARGET_REMAINS_UNMET", b7ReasonCounts["AFFECTED_TARGET_REMAINS_UNMET"] ?: 0)
                .put("TARGET_REGRESSED", b7ReasonCounts["TARGET_REGRESSED"] ?: 0))
            .put("c20LiveIncumbentFeasibility", JSONObject()
                .put("HARD_VALID", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.HARD_VALID.name] ?: 0)
                .put("HARD_INVALID", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.HARD_INVALID.name] ?: 0)
                .put("UNRESOLVED", incumbentFeasibilityCounts[CanonicalIncumbentFeasibility.UNRESOLVED.name] ?: 0)
                .put("preservedHardValidRows", routedResults.sumOf { it.incumbentPlacementPreservations.size })
                .put("hardInvalidOrUnresolvedForcedPreserved", routedResults.sumOf { result ->
                    result.incumbentPlacementPreservations.count { it.feasibility.status != CanonicalIncumbentFeasibility.HARD_VALID }
                }))
            .put("counts", JSONObject()
                .put("corpusCases", records.size).put("preflightRejected", records.count { it.second == null })
                .put("generatedCases", generatedCount).put("powerTargets", powerTargetsTotal)
                .put("powerTargetsTotal", powerTargetsTotal)
                .put("directionOnlyBefore", directionOnlyBefore).put("numericPowerAuthorityAfter", numericPowerAfter)
                .put("personalHistoryAuthority", personalHistoryAuthorities)
                .put("reviewedStarterAuthority", reviewedStarterAuthorities)
                .put("stillDirectionOnly", stillDirectionOnly)
                .put("fullyMaterializedPower", fullyMaterializedPower)
                .put("b7EligiblePowerCases", b7EligiblePowerCases)
                .put("b8AuthorizedPowerCases", b8AuthorizedPowerCases)
                .put("powerRoutedCases", powerRoutedCases)
                .put("exactB5PowerOwnerRows", selectedPowerOwnerRows)
                .put("uniqueExactB5PowerOwners", powerRouteRows.flatMap { (_, comparison) -> comparison.selectionPlan.selectedCandidates
                    .filter { "QUALITY:POWER" in it.coveredTargetIds }.map { "${it.stableKey}#${it.selectionRole}" } }.distinct().size)
                .put("generatedPowerRowsAfterAuthorityFilter", generatedPowerRows)
                .put("preC21BaselineGeneratedPowerRows", 8)
                .put("exactOwnerDirectPowerObservationIds", JSONArray(exactOwnerPowerObservationIds.sorted()))
            )
            .put("reviewedRuleSource", JSONObject()
                .put("category", reviewedCategory?.name).put("members", JSONArray(reviewedCategory?.let { RecordBasedReviewedPolicy.badmintonKeys.getValue(it).sorted() }.orEmpty()))
                .put("guide", reviewedGuideJson ?: JSONObject.NULL)
                .put("bridgeAccepted", false)
                .put("rejectionReasons", JSONArray(listOf(
                    "B4_POWER_HAS_NO_NUMERIC_DOSE_OR_WEEKLY_FREQUENCY_AUTHORITY",
                    "BADMINTON_DECELERATION_RELATION_IS_SUPPORTIVE_NOT_DIRECT",
                    "PROGRAM_SET_MODEL_DOES_NOT_REPRESENT_REPS_PER_SIDE",
                    "GUIDE_IS_CATEGORY_LEVEL_AND_NOT_PROVEN_AS_EXACT_OWNER_POWER_AUTHORITY"
                )))
            )
            .put("persona3Reviewed", JSONObject()
                .put("powerTarget", persona3PowerTarget?.let { JSONObject().put("strategy", it.strategy.name)
                    .put("numericAuthority", it.numericAuthority.name).put("reasonCodes", JSONArray(it.reasonCodes.sorted())) } ?: JSONObject.NULL)
                .put("selectedOwner", persona3Comparison?.selectionPlan?.selectedCandidates?.firstOrNull { "QUALITY:POWER" in it.coveredTargetIds }
                    ?.let { JSONObject().put("stableKey", it.stableKey).put("selectionRole", it.selectionRole) } ?: JSONObject.NULL)
                .put("exactOwnerPersonalHistoryCount", persona3Comparison?.let { comparison ->
                    val key = comparison.selectionPlan.selectedCandidates.firstOrNull { "QUALITY:POWER" in it.coveredTargetIds }?.stableKey
                    key?.let { stable -> contextByCase["persona3_reviewed"]?.snapshot?.allConfirmedSets?.count { it.stableKey == stable } } ?: 0
                } ?: 0)
                .put("currentPowerRowsAfterAuthorityFilter", persona3Comparison?.experimental?.items?.count { item ->
                    item.exerciseStableKey == POWER_OWNER_KEY && item.selectionRole == "CANONICAL_STIMULUS_QUALITY_POWER"
                } ?: 0)
                .put("currentB6Status", persona3Comparison?.prescriptionAuthorizationPlan?.authorizations?.firstOrNull { it.targetId == "QUALITY:POWER" }?.status?.name)
                .put("currentB6ReasonCodes", JSONArray(persona3Comparison?.prescriptionAuthorizationPlan?.authorizations
                    ?.firstOrNull { it.targetId == "QUALITY:POWER" }?.reasonCodes.orEmpty().sorted()))
                .put("routeBefore", persona3?.routeDecision?.selectedSource?.name)
            )
            .put("cases", caseRows)
        return root.toString(2)
    }

    private fun rangeJson(value: StimulusTargetRange?): Any = value?.let { JSONObject()
        .put("min", it.min).put("preferred", it.preferred).put("max", it.max) } ?: JSONObject.NULL

    private fun prescriptionJson(value: PlannedPrescription?): Any = value?.let { prescription -> JSONObject()
        .put("text", prescription.text).put("weightSource", prescription.weightSource).put("restSeconds", prescription.restSeconds)
        .put("sets", JSONArray(prescription.sets.map { set -> JSONObject().put("setIndex", set.setIndex)
            .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds).put("targetRpe", set.targetRpeMin) }))
    } ?: JSONObject.NULL

    private fun prescriptionAuthorityJson(value: PerformancePrescriptionAuthority): JSONObject = JSONObject()
        .put("source", value.source).put("text", value.text).put("restSeconds", value.restSeconds)
        .put("sets", JSONArray(value.sets.map { set -> JSONObject().put("setIndex", set.setIndex)
            .put("reps", set.reps).put("weightKg", set.weightKg).put("seconds", set.seconds) }))

    private fun objectiveJson(value: CanonicalBadmintonObjectiveRelation): JSONObject = JSONObject()
        .put("relationId", value.relationId).put("objective", value.objective.name)
        .put("transferLevel", value.transferLevel.name).put("provenance", value.provenance)
        .put("evidenceRelationKeys", JSONArray(value.evidenceRelationKeys.sorted())).put("reviewReason", value.reviewReason)
}
