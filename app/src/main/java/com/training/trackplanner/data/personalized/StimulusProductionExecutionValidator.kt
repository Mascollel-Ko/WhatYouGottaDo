package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** Execution status for the generated draft; B7/B8 comparison results do not participate. */
internal data class StimulusProductionExecutionValidation(
    val executable: Boolean,
    val reasonCodes: List<String>,
    val sessionTimeChecks: List<StimulusProductionSessionTimeCheck> = emptyList(),
    val dayOfiChecks: List<StimulusProductionDayOfiCheck> = emptyList(),
    val tissueRecoveryChecks: List<StimulusProductionTissueRecoveryCheck> = emptyList(),
    val unmetAuthorizedSetUnits: Int = 0,
    val unresolvedMaterialDemandGaps: Set<String> = emptySet(),
    val deferredMaterialDemandGaps: Set<String> = emptySet(),
    val userLoadCalibrationOwners: List<StimulusPrescriptionOwnerIdentity> = emptyList()
) {
    init {
        require(reasonCodes == reasonCodes.distinct().sorted())
        require(unmetAuthorizedSetUnits >= 0)
        require(userLoadCalibrationOwners == userLoadCalibrationOwners.distinct().sortedWith(
            compareBy({ it.stableKey }, { it.selectionRole })
        ))
    }
}

internal data class StimulusProductionSessionTimeCheck(
    val week: Int,
    val day: Int,
    val estimatedSeconds: Int,
    val availableSeconds: Int,
    val withinLimit: Boolean
)

internal data class StimulusProductionDayOfiCheck(
    val week: Int,
    val day: Int,
    val feasible: Boolean,
    val ofi: Int?,
    val axisScores: List<Int>,
    val cautionReasons: List<String>
)

internal data class StimulusProductionTissueRecoveryCheck(
    val week: Int,
    val feasible: Boolean,
    val conditionalOnTypedB6Execution: Boolean,
    val diagnostic: String?,
    val blockedUnits: Set<String>,
    val unresolvedExerciseKeys: Set<String>
)

internal class StimulusProductionExecutionRejectedException(
    val reasonCodes: List<String>,
    val detailCodes: List<String> = emptyList(),
    val unresolvedMaterialDemandGaps: Set<String> = emptySet(),
    val materialDemandAuthorityResolutions: List<ExecutionAuthorityResolution> = emptyList(),
    val strengthShortfalls: List<StimulusStrengthShortfall> = emptyList()
) : IllegalStateException(
    "새 운동 프로그램을 안전하게 완성하지 못했습니다. 기록과 운동별 중량 보정 정보를 확인한 뒤 다시 생성해 주세요. " +
        (reasonCodes + detailCodes).distinct().sorted().joinToString(",")
) {
    init {
        require(reasonCodes == reasonCodes.distinct().sorted())
        require(detailCodes == detailCodes.distinct().sorted())
    }
}

/**
 * Independent final gate for B1–B6 output. It checks the produced program and the exact B6
 * materialization evidence. It deliberately has no CONTROL, B7, B8, or B9 input.
 */
internal class StimulusProductionExecutionValidator(
    private val projectionValidator: ProgramProjectionValidator = ProgramProjectionValidator()
) {
    fun validate(
        program: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        authorizationPlan: StimulusPrescriptionAuthorizationPlan,
        taskProtocolPlan: TaskProtocolAuthorizationPlan,
        materializationAudits: List<StimulusPrescriptionMaterializationAudit>,
        traceId: String? = null
    ): StimulusProductionExecutionValidation {
        val failures = sortedSetOf<String>()
        val rowsByOwner = program.items.groupBy {
            StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole)
        }
        val candidateByOwner = selectionPlan.selectedCandidates.associateBy {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
        }
        val targets = buildSet {
            targetPlan.qualityTargets.forEach { add("QUALITY:${it.quality.name}") }
            targetPlan.taskTargets.forEach { add("TASK:${it.task}") }
            targetPlan.movementTargets.forEach { add(it.targetId) }
        }
        val sessionTimeChecks = mutableListOf<StimulusProductionSessionTimeCheck>()
        val dayOfiChecks = mutableListOf<StimulusProductionDayOfiCheck>()
        val tissueRecoveryChecks = mutableListOf<StimulusProductionTissueRecoveryCheck>()
        val scheduleChecks = JSONArray()
        val rowValidationChecks = JSONArray()
        val ownerAuthorityChecks = JSONArray()
        val materializationIntegrityChecks = JSONArray()

        if (program.items.isEmpty()) failures += "PROGRAM_HAS_NO_EXECUTABLE_ITEMS"
        val projectionErrors = projectionValidator.errors(program)
        if (projectionErrors.isNotEmpty()) failures += "PROGRAM_PROJECTION_INVALID"
        if (program.request.weeklyTrainingDays !in 2..5) failures += "WEEKLY_FREQUENCY_OUTSIDE_SUPPORTED_RANGE"
        if (program.items.map { it.localId }.distinct().size != program.items.size) failures += "DUPLICATE_PROGRAM_ROW_IDENTITY"

        val scheduleStarted = System.nanoTime()
        val itemsByWeekDay = program.items.groupBy { it.weekNumber to it.dayOfWeek }
        program.weekDaySchedule.forEach { (week, days) ->
            val frequencyMatches = days.size == program.request.weeklyTrainingDays
            val uniqueAndValidDays = days.size == days.distinct().size && days.all { it in 1..7 }
            val weekWithinHorizon = week in 1..program.request.durationWeeks
            if (!frequencyMatches) failures += "WEEKLY_FREQUENCY_SCHEDULE_MISMATCH"
            if (!uniqueAndValidDays) {
                failures += "INVALID_WEEKLY_TRAINING_SCHEDULE"
            }
            if (!weekWithinHorizon) failures += "SCHEDULE_WEEK_OUTSIDE_PROGRAM_HORIZON"
            scheduleChecks.put(JSONObject().put("week", week).put("requestedDays", program.request.weeklyTrainingDays)
                .put("scheduledDays", JSONArray(days.sorted())).put("frequencyMatches", frequencyMatches)
                .put("uniqueAndValidDays", uniqueAndValidDays).put("weekWithinProgramHorizon", weekWithinHorizon))
        }
        val completeScheduleHorizon = program.weekDaySchedule.keys == (1..program.request.durationWeeks).toSet()
        if (!completeScheduleHorizon) {
            failures += "WEEKLY_TRAINING_SCHEDULE_INCOMPLETE"
        }
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_SCHEDULE", "StimulusProductionExecutionValidator.validate/schedule shape",
            inputs = JSONObject().put("requestedWeeklyTrainingDays", program.request.weeklyTrainingDays)
                .put("durationWeeks", program.request.durationWeeks).put("programRowCount", program.items.size),
            criteria = JSONObject().put("supportedDays", "2..5").put("scheduledDayCountMustMatchRequest", true)
                .put("uniqueDaysInRange1to7", true).put("completeWeekHorizon", true),
            comparisons = JSONObject().put("checks", scheduleChecks).put("completeScheduleHorizon", completeScheduleHorizon)
                .put("projectionErrors", JSONArray(projectionErrors)),
            decision = if (failures.none { it in setOf("WEEKLY_FREQUENCY_OUTSIDE_SUPPORTED_RANGE", "WEEKLY_FREQUENCY_SCHEDULE_MISMATCH", "INVALID_WEEKLY_TRAINING_SCHEDULE", "SCHEDULE_WEEK_OUTSIDE_PROGRAM_HORIZON", "WEEKLY_TRAINING_SCHEDULE_INCOMPLETE", "PROGRAM_PROJECTION_INVALID") }) "SCHEDULE_VALID" else "SCHEDULE_REJECTED",
            reasonCodes = failures.filter { it in setOf("WEEKLY_FREQUENCY_OUTSIDE_SUPPORTED_RANGE", "WEEKLY_FREQUENCY_SCHEDULE_MISMATCH", "INVALID_WEEKLY_TRAINING_SCHEDULE", "SCHEDULE_WEEK_OUTSIDE_PROGRAM_HORIZON", "WEEKLY_TRAINING_SCHEDULE_INCOMPLETE", "PROGRAM_PROJECTION_INVALID") },
            nextStageInputs = JSONObject().put("completeScheduleHorizon", completeScheduleHorizon), startedNanos = scheduleStarted)
        val sessionTimeStarted = System.nanoTime()
        itemsByWeekDay.forEach { (weekDay, rows) ->
            val allowedDays = program.weekDaySchedule[weekDay.first]
            if (allowedDays == null || weekDay.second !in allowedDays) failures += "PROGRAM_DAY_OUTSIDE_GENERATED_SCHEDULE"
            val estimatedSeconds = rows.sumOf(ProgramSkeletonItem::estimatedDurationSeconds)
            val availableSeconds = program.request.sessionMinutes.coerceAtLeast(0) * 60
            val withinLimit = estimatedSeconds <= availableSeconds
            sessionTimeChecks += StimulusProductionSessionTimeCheck(
                weekDay.first, weekDay.second, estimatedSeconds, availableSeconds, withinLimit
            )
            if (!withinLimit) failures += "SESSION_TIME_LIMIT_EXCEEDED"
            if (rows.groupBy(ProgramSkeletonItem::exerciseStableKey).any { (_, sameExercise) -> sameExercise.size > 1 }) {
                failures += "DUPLICATE_PHYSICAL_EXERCISE_WITHIN_SESSION"
            }
            if (rows.groupBy(ProgramSkeletonItem::orderIndex).any { (_, sameOrder) -> sameOrder.size > 1 }) {
                failures += "DUPLICATE_SESSION_ORDER_INDEX"
            }
        }
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_SESSION_TIME", "StimulusProductionExecutionValidator.validate/session-time + duplicate-session checks",
            inputs = JSONObject().put("sessionMinutes", program.request.sessionMinutes).put("sessionCount", itemsByWeekDay.size),
            criteria = JSONObject().put("estimatedSecondsMustNotExceedAvailableSeconds", true)
                .put("sameExerciseAndOrderDuplicatesRejected", true),
            comparisons = JSONObject().put("checks", JSONArray(sessionTimeChecks.map { check -> JSONObject()
                .put("week", check.week).put("day", check.day).put("estimatedSeconds", check.estimatedSeconds)
                .put("availableSeconds", check.availableSeconds).put("withinLimit", check.withinLimit) })),
            decision = if (sessionTimeChecks.all { it.withinLimit }) "SESSION_TIME_CHECK_COMPLETE" else "SESSION_TIME_LIMIT_FAILURE",
            reasonCodes = failures.filter { it.startsWith("SESSION_TIME_") || it.startsWith("DUPLICATE_PHYSICAL_EXERCISE") || it.startsWith("DUPLICATE_SESSION_ORDER") },
            startedNanos = sessionTimeStarted)

        val ofiStarted = System.nanoTime()
        val dayProjection = snapshot.planDayProjection
        if (dayProjection == null) {
            failures += "DAY_OFI_PROJECTION_UNAVAILABLE"
        } else {
            itemsByWeekDay.forEach { (weekDay, rows) ->
                val load = runCatching {
                    dayProjection.evaluate(rows.sortedBy(ProgramSkeletonItem::orderIndex))
                }.getOrNull()
                dayOfiChecks += StimulusProductionDayOfiCheck(
                    week = weekDay.first,
                    day = weekDay.second,
                    feasible = load?.feasible == true,
                    ofi = load?.ofi,
                    axisScores = load?.axisScores.orEmpty(),
                    cautionReasons = load?.cautionReasons.orEmpty().sorted()
                )
                if (load == null || !load.feasible) failures += "DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED"
            }
        }
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_OFI", "StimulusProductionExecutionValidator.validate/day OFI projection",
            inputs = JSONObject().put("dayProjectionAvailable", dayProjection != null).put("weekDayCount", itemsByWeekDay.size),
            criteria = JSONObject().put("everyMaterializedDayMustPassProjection", true),
            comparisons = JSONObject().put("checks", JSONArray(dayOfiChecks.map { check -> JSONObject()
                .put("week", check.week).put("day", check.day).put("feasible", check.feasible).put("ofi", check.ofi)
                .put("axisScores", JSONArray(check.axisScores)).put("cautionReasons", JSONArray(check.cautionReasons)) })),
            decision = if (dayOfiChecks.all { it.feasible } && dayProjection != null) "OFI_CHECK_COMPLETE" else "OFI_CHECK_FAILURE",
            reasonCodes = failures.filter { it == "DAY_OFI_PROJECTION_UNAVAILABLE" || it == "DAY_OFI_OR_RECOVERY_LIMIT_EXCEEDED" },
            startedNanos = ofiStarted)

        val tissueStarted = System.nanoTime()
        val tissueProjection = snapshot.planWeekTissueProjection
        if (tissueProjection == null) {
            failures += "CONNECTIVE_TISSUE_PROJECTION_UNAVAILABLE"
        } else {
            program.items.groupBy(ProgramSkeletonItem::weekNumber).forEach { (week, rows) ->
                val targetRpeMax = program.weekPlans.singleOrNull { it.weekIndex == week }?.targetRpeMax
                val projectedWeek = targetRpeMax?.let { effort -> runCatching {
                    tissueProjection.evaluate(rows, effort)
                }.getOrNull() }
                val unresolvedKeys = projectedWeek?.days.orEmpty().flatMapTo(sortedSetOf()) { it.unresolvedKeys }
                val blockedUnits = projectedWeek?.days.orEmpty().flatMapTo(sortedSetOf()) { it.blockedUnits }
                val unresolvedRowsHaveTypedB6Execution = unresolvedKeys.isNotEmpty() &&
                    unresolvedKeys.all { stableKey ->
                        val unresolvedRows = rows.filter { it.exerciseStableKey == stableKey }
                        unresolvedRows.isNotEmpty() && unresolvedRows.all { row ->
                            val owner = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
                            val ownerAuthorizations = authorizationPlan.authorizations.filter { authorization ->
                                authorization.owner?.let { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole } == true
                            }
                            val b6Calibration = ownerAuthorizations.any { authorization ->
                                    authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                                    authorization.authorizedPrescription != null
                            }
                            val coreDirectB6 = "MOVEMENT:CORE_DIRECT" in candidateByOwner[owner]?.coveredTargetIds.orEmpty() &&
                                authorizationPlan.movementAuthorizations.any {
                                    it.targetId == "MOVEMENT:CORE_DIRECT" && it.owner == owner &&
                                        it.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6
                                } && ownerAuthorizations.any {
                                    it.targetId == "MOVEMENT:CORE_DIRECT" && it.authorizedPrescription != null &&
                                        it.status in setOf(
                                            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                                            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                                        )
                                }
                            val sets = ProgramSetPrescriptionResolver.resolve(row)
                            (b6Calibration && sets.all {
                                it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
                            }) || (coreDirectB6 && sets.all { it.loadState == ProgramLoadState.NOT_APPLICABLE })
                        }
                    }
                val conditionalB6 = projectedWeek?.let { !it.feasible && blockedUnits.isEmpty() &&
                    unresolvedRowsHaveTypedB6Execution } == true
                tissueRecoveryChecks += StimulusProductionTissueRecoveryCheck(
                    week = week,
                    feasible = projectedWeek?.feasible == true,
                    conditionalOnTypedB6Execution = conditionalB6,
                    diagnostic = projectedWeek?.diagnostic,
                    blockedUnits = projectedWeek?.days.orEmpty().flatMapTo(sortedSetOf()) { it.blockedUnits },
                    unresolvedExerciseKeys = projectedWeek?.days.orEmpty().flatMapTo(sortedSetOf()) { it.unresolvedKeys }
                )
                if (projectedWeek == null || (!projectedWeek.feasible && !conditionalB6)) {
                    failures += "CONNECTIVE_TISSUE_RECOVERY_LIMIT_EXCEEDED"
                }
            }
        }
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_CONNECTIVE_TISSUE", "StimulusProductionExecutionValidator.validate/week tissue recovery projection",
            inputs = JSONObject().put("tissueProjectionAvailable", tissueProjection != null)
                .put("weekCount", program.items.map { it.weekNumber }.distinct().size),
            criteria = JSONObject().put("infeasibleOnlyConditionalOnTypedB6MayRemainExecutable", true),
            comparisons = JSONObject().put("checks", JSONArray(tissueRecoveryChecks.map { check -> JSONObject()
                .put("week", check.week).put("feasible", check.feasible)
                .put("conditionalOnTypedB6Execution", check.conditionalOnTypedB6Execution)
                .put("diagnostic", check.diagnostic).put("blockedUnits", JSONArray(check.blockedUnits.sorted()))
                .put("unresolvedExerciseKeys", JSONArray(check.unresolvedExerciseKeys.sorted())) })),
            decision = if (tissueRecoveryChecks.all { it.feasible || it.conditionalOnTypedB6Execution } && tissueProjection != null)
                "TISSUE_RECOVERY_CHECK_COMPLETE" else "TISSUE_RECOVERY_CHECK_FAILURE",
            reasonCodes = failures.filter { it == "CONNECTIVE_TISSUE_PROJECTION_UNAVAILABLE" || it == "CONNECTIVE_TISSUE_RECOVERY_LIMIT_EXCEEDED" },
            startedNanos = tissueStarted)

        program.items.forEach { row ->
            val rowStarted = System.nanoTime()
            val rowFailureCodes = mutableListOf<String>()
            if (row.exerciseStableKey.isBlank()) { failures += "EXERCISE_IDENTITY_MISSING"; rowFailureCodes += "EXERCISE_IDENTITY_MISSING" }
            if (row.selectionRole.isBlank()) { failures += "EXERCISE_SELECTION_ROLE_MISSING"; rowFailureCodes += "EXERCISE_SELECTION_ROLE_MISSING" }
            val excludedByUser = row.exerciseStableKey in program.request.excludedExerciseStableKeys
            val explicitlyRestricted = snapshot.explicitlyRestricted(row.exerciseStableKey)
            if (excludedByUser) { failures += "USER_EXCLUDED_EXERCISE_MATERIALIZED"; rowFailureCodes += "USER_EXCLUDED_EXERCISE_MATERIALIZED" }
            if (explicitlyRestricted) { failures += "USER_RESTRICTED_EXERCISE_MATERIALIZED"; rowFailureCodes += "USER_RESTRICTED_EXERCISE_MATERIALIZED" }
            val exercise = snapshot.exercises[row.exerciseStableKey]
            var categoryMatches: Boolean? = null
            var equipmentAvailable: Boolean? = null
            if (exercise == null) {
                failures += "EXERCISE_NOT_IN_CURRENT_CATALOG"; rowFailureCodes += "EXERCISE_NOT_IN_CURRENT_CATALOG"
            } else {
                categoryMatches = row.category.equals(exercise.category, ignoreCase = true)
                if (categoryMatches == false) { failures += "EXERCISE_CANONICAL_CATEGORY_MISMATCH"; rowFailureCodes += "EXERCISE_CANONICAL_CATEGORY_MISMATCH" }
                if (program.request.availableEquipment.isNotEmpty()) {
                    val unavailable = exercise.equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
                        .any { it != "BODYWEIGHT" && it !in program.request.availableEquipment }
                    equipmentAvailable = !unavailable
                    if (unavailable) { failures += "EXERCISE_EQUIPMENT_CONSTRAINT_VIOLATED"; rowFailureCodes += "EXERCISE_EQUIPMENT_CONSTRAINT_VIOLATED" }
                }
            }
            val rowShapeValid = row.setCount > 0 && row.restSeconds >= 0 && row.estimatedDurationSeconds >= 0
            if (!rowShapeValid) {
                failures += "INVALID_PROGRAM_ROW_SHAPE"; rowFailureCodes += "INVALID_PROGRAM_ROW_SHAPE"
            }
            val sets = ProgramSetPrescriptionResolver.resolve(row)
            val setsValid = sets.size == row.setCount && sets.none { set ->
                    set.setIndex <= 0 || set.reps < 0 || set.seconds < 0 ||
                        (set.reps == 0 && set.seconds == 0) || !set.weightKg.isFinite() || set.weightKg < 0.0 ||
                        set.targetRpeMin?.let { !it.isFinite() || it !in 1.0..10.0 } == true ||
                        (set.loadState in setOf(ProgramLoadState.USER_CALIBRATION_REQUIRED,
                            ProgramLoadState.REAL_ZERO_LOAD, ProgramLoadState.NOT_APPLICABLE) && set.weightKg != 0.0)
                }
            if (!setsValid) { failures += "INVALID_EXECUTABLE_SET_PRESCRIPTION"; rowFailureCodes += "INVALID_EXECUTABLE_SET_PRESCRIPTION" }
            var taskProtocolValid: Boolean? = null
            if (row.taskProtocolSemanticsJson != null) {
                val owner = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
                val grant = taskProtocolPlan.authorizedByOwner[owner]
                val semantics = runCatching {
                    TaskProtocolExposureMetadata.fromJsonString(row.taskProtocolSemanticsJson)
                }.getOrNull()
                taskProtocolValid = grant != null && semantics != null &&
                    semantics.authorization.definition.protocolId == grant.definition.protocolId &&
                    semantics.matchesMaterializedItem(
                        item = row,
                        actualActivityKind = snapshot.activityKind(row.exerciseStableKey),
                        currentB4Tasks = grant.attributedTasks,
                        exactDirectTasks = grant.attributedTasks,
                        selectedPrimaryTargetId = "TASK:${grant.definition.primaryTask.name}"
                    )
                if (taskProtocolValid == false) { failures += "TASK_B6_PROTOCOL_MATERIALIZATION_INVALID"; rowFailureCodes += "TASK_B6_PROTOCOL_MATERIALIZATION_INVALID" }
            }
            val rowIdentityValid = row.exerciseStableKey.isNotBlank() && row.selectionRole.isNotBlank()
            val rowValid = rowIdentityValid && categoryMatches != false && equipmentAvailable != false && !excludedByUser &&
                !explicitlyRestricted && rowShapeValid && setsValid && taskProtocolValid != false && exercise != null
            rowValidationChecks.put(JSONObject().put("stableKey", row.exerciseStableKey)
                .put("selectionRole", row.selectionRole).put("week", row.weekNumber).put("day", row.dayOfWeek)
                .put("exerciseName", exercise?.name).put("catalogEntryPresent", exercise != null)
                .put("programCategory", row.category).put("canonicalCategory", exercise?.category)
                .put("categoryMatches", categoryMatches).put("equipmentAvailable", equipmentAvailable)
                .put("identityValid", rowIdentityValid)
                .put("userExcluded", excludedByUser).put("explicitlyRestricted", explicitlyRestricted)
                .put("rowShapeValid", rowShapeValid).put("setCountMatchesResolvedSets", sets.size == row.setCount)
                .put("setPrescriptionValid", setsValid).put("taskProtocolValid", taskProtocolValid)
                .put("setCount", row.setCount).put("resolvedSetCount", sets.size)
                .put("decision", if (rowValid) "ROW_VALID" else "ROW_INVALID")
                .put("reasonCodes", JSONArray(rowFailureCodes.distinct().sorted())))
            GenerationDecisionTraceStore.record(traceId, "VALIDATION_ROW_CLASSIFICATION_AND_PRESCRIPTION",
                "StimulusProductionExecutionValidator.validate/catalog + equipment + row/set + Task protocol",
                inputs = JSONObject().put("stableKey", row.exerciseStableKey).put("selectionRole", row.selectionRole)
                    .put("week", row.weekNumber).put("day", row.dayOfWeek).put("setCount", row.setCount),
                criteria = JSONObject().put("canonicalCategoryMustMatchCurrentCatalog", true)
                    .put("userExcludedAndRestrictedRowsRejected", true).put("equipmentMustBeAvailable", true)
                    .put("executableSetShapeRequired", true),
                comparisons = rowValidationChecks.getJSONObject(rowValidationChecks.length() - 1),
                decision = if (rowValid) "ROW_VALID" else "ROW_INVALID",
                reasonCodes = rowValidationChecks.getJSONObject(rowValidationChecks.length() - 1).optJSONArray("reasonCodes")?.let { codes ->
                    (0 until codes.length()).map(codes::optString)
                }.orEmpty(), nextStageInputs = JSONObject().put("resolvedSets", JSONArray(sets.map { set -> JSONObject()
                    .put("setIndex", set.setIndex).put("reps", set.reps).put("seconds", set.seconds)
                    .put("weightKg", set.weightKg).put("loadState", set.loadState.name).put("rpe", set.targetRpeMin) })),
                startedNanos = rowStarted)
        }

        GenerationDecisionTraceStore.record(traceId, "VALIDATION_ROW_CLASSIFICATION_SUMMARY",
            "StimulusProductionExecutionValidator.validate/materialized row checks",
            criteria = JSONObject().put("allRowsMustMatchCatalogAndConstraints", true),
            comparisons = JSONObject().put("rowCount", rowValidationChecks.length()).put("checks", rowValidationChecks),
            decision = if (rowValidationChecks.length() > 0 && rowValidationChecks.length() == program.items.size && (0 until rowValidationChecks.length()).all {
                    rowValidationChecks.optJSONObject(it)?.optString("decision") != "ROW_INVALID"
                }) "ROWS_VALID" else "ROWS_INVALID",
            nextStageInputs = JSONObject().put("rowCount", rowValidationChecks.length()))

        // Every newly selected target owner with a physical row must have exact B4/B5/B6
        // authorization. A missing authorization can be reported as unmet demand only when
        // no unauthorized physical prescription was emitted.
        val ownerValidationStarted = System.nanoTime()
        program.items.filterNot(ProgramSkeletonItem::requiredTemplateAnchor).forEach { row ->
            val owner = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
            val selectedByB5 = owner in candidateByOwner
            if (!selectedByB5) {
                failures += "B5_PHYSICAL_ROW_WITHOUT_SELECTED_CANDIDATE"
            }
            ownerAuthorityChecks.put(JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                .put("materializedPhysicalRow", true).put("selectedByB5", selectedByB5).put("requiredTemplateAnchor", false))
        }
        candidateByOwner.forEach { (owner, candidate) ->
            val targetChecks = JSONArray()
            val allTargetsInB4 = candidate.coveredTargetIds.all { it in targets }
            if (!allTargetsInB4) failures += "B5_SELECTED_TARGET_NOT_IN_B4_PLAN"
            candidate.coveredTargetIds.filter { it.startsWith("MOVEMENT:") }.forEach { targetId ->
                val requestedCoverage = runCatching {
                    MovementCoverage.valueOf(targetId.removePrefix("MOVEMENT:"))
                }.getOrNull()
                val canonicalDecision = snapshot.canonicalMovementCoverage[owner.stableKey]
                val movementClassificationValid = requestedCoverage != null && canonicalDecision?.directCoverageAdmissible == true &&
                    canonicalDecision.coverage.directlyRepresents(requestedCoverage)
                if (!movementClassificationValid) failures += "B5_MOVEMENT_CANONICAL_CLASSIFICATION_INVALID"
                targetChecks.put(JSONObject().put("targetId", targetId).put("targetExistsInB4", targetId in targets)
                    .put("requestedMovement", requestedCoverage?.name).put("canonicalMovement", canonicalDecision?.coverage?.name)
                    .put("directCoverageAdmissible", canonicalDecision?.directCoverageAdmissible)
                    .put("directlyRepresentsTarget", movementClassificationValid))
            }
            val ownerRows = rowsByOwner[owner].orEmpty()
            if (ownerRows.isEmpty()) {
                ownerAuthorityChecks.put(JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                    .put("coveredTargetIds", JSONArray(candidate.coveredTargetIds.sorted())).put("allTargetsInB4", allTargetsInB4)
                    .put("materializedRows", 0).put("targetChecks", targetChecks))
                return@forEach
            }
            candidate.coveredTargetIds.filter { it.startsWith("QUALITY:") }.forEach { targetId ->
                val authorization = authorizationPlan.authorizations.singleOrNull {
                    it.targetId == targetId && it.owner?.let { value ->
                        value.stableKey == owner.stableKey && value.selectionRole == owner.selectionRole
                    } == true
                }
                val authorized = authorization != null && authorization.authorizedPrescription != null &&
                    authorization.status in setOf(
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION,
                        StimulusPrescriptionAuthorizationStatus.AUTHORIZED_USER_APPROVED_POWER_JUMP_POLICY
                    )
                if (!authorized) failures += "B6_SELECTED_OWNER_WITHOUT_EXECUTABLE_AUTHORIZATION"
                val canonicalStrengthValid = authorization?.quality != TrainableQuality.STRENGTH ||
                    CanonicalStrengthExposureCapability.strengthPossible(owner.stableKey)
                if (!canonicalStrengthValid) {
                    failures += "B5_STRENGTH_CANONICAL_CLASSIFICATION_INVALID"
                }
                val audit = materializationAudits.singleOrNull {
                    it.targetId == targetId && it.owner?.let { value ->
                        value.stableKey == owner.stableKey && value.selectionRole == owner.selectionRole
                    } == true
                }
                val materializationExecutable = audit != null && audit.materializedWeeklySetUnits > 0 &&
                    audit.targetCompatibleMaterializedUnits == audit.materializedWeeklySetUnits &&
                    audit.executionAuthority != StimulusPrescriptionExecutionAuthority.UNRESOLVED &&
                    audit.executionAuthority != StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT
                if (!materializationExecutable) {
                    failures += "B6_MATERIALIZED_DOSE_NOT_EXECUTABLE"
                }
                val resolvedOwnerSets = ownerRows.flatMap(ProgramSetPrescriptionResolver::resolve)
                val userLoadInputRequired = authorization?.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT
                val allSetsUseCalibrationState = resolvedOwnerSets.all { it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED }
                if (userLoadInputRequired && !allSetsUseCalibrationState) failures += "B6_USER_LOAD_INPUT_REQUIRED_BUT_NOT_ENCODED"
                val ownerHasCalibrationState = resolvedOwnerSets.any {
                        it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
                    }
                val calibrationAuthorityValid = !ownerHasCalibrationState ||
                    (authorization?.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                        authorization?.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT)
                if (!calibrationAuthorityValid) failures += "B6_USER_CALIBRATION_WITHOUT_TYPED_B6_AUTHORITY"
                targetChecks.put(JSONObject().put("targetId", targetId).put("targetExistsInB4", targetId in targets)
                    .put("authorizationStatus", authorization?.status?.name)
                    .put("executionAuthority", authorization?.executionAuthority?.name)
                    .put("authorizedPrescriptionPresent", authorization?.authorizedPrescription != null)
                    .put("canonicalStrengthClassificationValid", canonicalStrengthValid)
                    .put("materializationAuditPresent", audit != null).put("materializationExecutable", materializationExecutable)
                    .put("authorizedUnits", audit?.authorizedWeeklySetUnits).put("materializedUnits", audit?.materializedWeeklySetUnits)
                    .put("targetCompatibleUnits", audit?.targetCompatibleMaterializedUnits)
                    .put("userLoadInputRequired", userLoadInputRequired).put("ownerHasCalibrationState", ownerHasCalibrationState)
                    .put("allSetsUseCalibrationState", allSetsUseCalibrationState)
                    .put("calibrationAuthorityValid", calibrationAuthorityValid))
            }
            candidate.coveredTargetIds.filter { it.startsWith("TASK:") }.forEach { targetId ->
                val taskAuthorized = owner in taskProtocolPlan.authorizedByOwner && ownerRows.none { row -> row.taskProtocolSemanticsJson == null }
                if (!taskAuthorized) {
                    failures += "B6_SELECTED_TASK_OWNER_WITHOUT_APPROVED_PROTOCOL"
                }
                val grant = taskProtocolPlan.authorizedByOwner[owner]
                targetChecks.put(JSONObject().put("targetId", targetId).put("targetExistsInB4", targetId in targets)
                    .put("taskProtocolAuthorized", taskAuthorized).put("protocolId", grant?.definition?.protocolId)
                    .put("attributedTasks", JSONArray(grant?.attributedTasks.orEmpty().map(Enum<*>::name))))
            }
            candidate.coveredTargetIds.filter { it.startsWith("MOVEMENT:") }.forEach { targetId ->
                val movement = authorizationPlan.movementAuthorizations.singleOrNull {
                    it.targetId == targetId && it.owner == owner
                }
                val movementAuthorized = movement != null && movement.status != StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY &&
                    movement.status != StimulusMovementB6Status.NO_B5_MOVEMENT_OWNER
                if (!movementAuthorized) failures += "B6_SELECTED_MOVEMENT_OWNER_WITHOUT_AUTHORITY"
                var movementMaterializationValid: Boolean? = null
                if (movement?.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6) {
                    val audit = materializationAudits.singleOrNull { value ->
                        value.targetId == targetId && value.owner?.let {
                            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                        } == true
                    }
                    movementMaterializationValid = audit != null && audit.materializedWeeklySetUnits > 0 &&
                        audit.targetCompatibleMaterializedUnits == audit.materializedWeeklySetUnits &&
                        audit.executionAuthority != StimulusPrescriptionExecutionAuthority.UNRESOLVED &&
                        audit.state != StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
                    if (movementMaterializationValid == false) {
                        failures += "B6_CORE_DIRECT_MOVEMENT_MATERIALIZATION_INVALID"
                    }
                }
                targetChecks.put(JSONObject().put("targetId", targetId).put("targetExistsInB4", targetId in targets)
                    .put("movementAuthorizationStatus", movement?.status?.name).put("movementAuthorized", movementAuthorized)
                    .put("movementMaterializationValid", movementMaterializationValid))
            }
            ownerAuthorityChecks.put(JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole)
                .put("coveredTargetIds", JSONArray(candidate.coveredTargetIds.sorted())).put("allTargetsInB4", allTargetsInB4)
                .put("materializedRows", ownerRows.size).put("targetChecks", targetChecks))
        }

        val materializationStarted = System.nanoTime()
        materializationAudits.forEach { audit ->
            val physicalSetIntegrityValid = audit.state != StimulusPrescriptionMaterializationState.INVARIANT_FAILURE &&
                audit.overrun <= 0 && audit.prescriptionPreservedOrSubset &&
                audit.targetCompatibleMaterializedUnits <= audit.materializedWeeklySetUnits
            if (!physicalSetIntegrityValid) failures += "B6_PHYSICAL_SET_INTEGRITY_FAILURE"
            if (audit.materializedWeeklySetUnits > 0 && audit.executionAuthority == StimulusPrescriptionExecutionAuthority.UNRESOLVED) {
                failures += "B6_MATERIALIZED_WITHOUT_EXECUTION_AUTHORITY"
            }
            materializationIntegrityChecks.put(JSONObject().put("targetId", audit.targetId)
                .put("stableKey", audit.owner?.stableKey).put("selectionRole", audit.owner?.selectionRole)
                .put("state", audit.state.name).put("authorizedUnits", audit.authorizedWeeklySetUnits)
                .put("materializedUnits", audit.materializedWeeklySetUnits)
                .put("targetCompatibleUnits", audit.targetCompatibleMaterializedUnits).put("overrun", audit.overrun)
                .put("prescriptionPreservedOrSubset", audit.prescriptionPreservedOrSubset)
                .put("executionAuthority", audit.executionAuthority.name).put("physicalSetIntegrityValid", physicalSetIntegrityValid)
                .put("reasonCodes", JSONArray(audit.reasonCodes)))
        }
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_B5_B6_AUTHORITY",
            "StimulusProductionExecutionValidator.validate/selected target owner authority",
            criteria = JSONObject().put("eachPhysicalOwnerMustHaveB5B6Authority", true).put("targetMustBelongToB4", true),
            comparisons = JSONObject().put("ownerChecks", ownerAuthorityChecks),
            decision = if (failures.none { it.startsWith("B5_") || it.startsWith("B6_") }) "AUTHORITY_VALID" else "AUTHORITY_REJECTED",
            reasonCodes = failures.filter { it.startsWith("B5_") || it.startsWith("B6_") },
            nextStageInputs = JSONObject().put("ownerCount", ownerAuthorityChecks.length()), startedNanos = ownerValidationStarted)
        GenerationDecisionTraceStore.record(traceId, "VALIDATION_PHYSICAL_SET_INTEGRITY",
            "StimulusProductionExecutionValidator.validate/materialization audit",
            criteria = JSONObject().put("noOverrunOrUnauthorizedPhysicalSets", true).put("materializedSetMustHaveExecutionAuthority", true),
            comparisons = JSONObject().put("materializationAudits", materializationIntegrityChecks)
                .put("unmetAuthorizedSetUnits", materializationAudits.sumOf { it.totalShortfallUnits.coerceAtLeast(0) }),
            decision = if (materializationIntegrityChecks.length() == materializationAudits.size && (0 until materializationIntegrityChecks.length()).all {
                    materializationIntegrityChecks.optJSONObject(it)?.optBoolean("physicalSetIntegrityValid") == true
                }) "PHYSICAL_SET_INTEGRITY_VALID" else "PHYSICAL_SET_INTEGRITY_FAILURE",
            reasonCodes = failures.filter { it.contains("MATERIALIZED") || it.contains("PHYSICAL_SET") },
            nextStageInputs = JSONObject().put("auditCount", materializationIntegrityChecks.length()), startedNanos = materializationStarted)

        val unmet = materializationAudits.sumOf { it.totalShortfallUnits.coerceAtLeast(0) }
        val unresolvedGaps = program.personalizedDecision?.planningBudget?.execution
            ?.unresolvedMaterialDemandGaps.orEmpty().toSortedSet()
        val deferredGaps = program.personalizedDecision?.planningBudget?.execution
            ?.deferredMaterialGaps?.keys.orEmpty().toSortedSet()
        val calibrationOwners = authorizationPlan.authorizations.asSequence()
            .filter { authorization ->
                authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
            }
            .mapNotNull { authorization ->
                authorization.owner?.let { owner ->
                    StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
                }
            }
            .filter { owner ->
                rowsByOwner[owner].orEmpty().any { row ->
                    ProgramSetPrescriptionResolver.resolve(row).any { set ->
                        set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
                    }
                }
            }
            .distinct()
            .sortedWith(compareBy<StimulusPrescriptionOwnerIdentity> { it.stableKey }.thenBy { it.selectionRole })
            .toList()

        return StimulusProductionExecutionValidation(
            executable = failures.isEmpty(),
            reasonCodes = failures.toList(),
            sessionTimeChecks = sessionTimeChecks.sortedWith(compareBy({ it.week }, { it.day })),
            dayOfiChecks = dayOfiChecks.sortedWith(compareBy({ it.week }, { it.day })),
            tissueRecoveryChecks = tissueRecoveryChecks.sortedBy(StimulusProductionTissueRecoveryCheck::week),
            unmetAuthorizedSetUnits = unmet,
            unresolvedMaterialDemandGaps = unresolvedGaps,
            deferredMaterialDemandGaps = deferredGaps,
            userLoadCalibrationOwners = calibrationOwners
        )
    }
}
