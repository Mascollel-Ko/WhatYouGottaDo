package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.TrainableQuality

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
        materializationAudits: List<StimulusPrescriptionMaterializationAudit>
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

        if (program.items.isEmpty()) failures += "PROGRAM_HAS_NO_EXECUTABLE_ITEMS"
        if (projectionValidator.errors(program).isNotEmpty()) failures += "PROGRAM_PROJECTION_INVALID"
        if (program.request.weeklyTrainingDays !in 2..5) failures += "WEEKLY_FREQUENCY_OUTSIDE_SUPPORTED_RANGE"
        if (program.items.map { it.localId }.distinct().size != program.items.size) failures += "DUPLICATE_PROGRAM_ROW_IDENTITY"

        val itemsByWeekDay = program.items.groupBy { it.weekNumber to it.dayOfWeek }
        program.weekDaySchedule.forEach { (week, days) ->
            if (days.size != program.request.weeklyTrainingDays) failures += "WEEKLY_FREQUENCY_SCHEDULE_MISMATCH"
            if (days.size != days.distinct().size || days.any { it !in 1..7 }) {
                failures += "INVALID_WEEKLY_TRAINING_SCHEDULE"
            }
            if (week !in 1..program.request.durationWeeks) failures += "SCHEDULE_WEEK_OUTSIDE_PROGRAM_HORIZON"
        }
        if (program.weekDaySchedule.keys != (1..program.request.durationWeeks).toSet()) {
            failures += "WEEKLY_TRAINING_SCHEDULE_INCOMPLETE"
        }
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

        program.items.forEach { row ->
            if (row.exerciseStableKey.isBlank()) failures += "EXERCISE_IDENTITY_MISSING"
            if (row.selectionRole.isBlank()) failures += "EXERCISE_SELECTION_ROLE_MISSING"
            if (row.exerciseStableKey in program.request.excludedExerciseStableKeys) failures += "USER_EXCLUDED_EXERCISE_MATERIALIZED"
            if (snapshot.explicitlyRestricted(row.exerciseStableKey)) failures += "USER_RESTRICTED_EXERCISE_MATERIALIZED"
            val exercise = snapshot.exercises[row.exerciseStableKey]
            if (exercise == null) {
                failures += "EXERCISE_NOT_IN_CURRENT_CATALOG"
            } else {
                if (!row.category.equals(exercise.category, ignoreCase = true)) failures += "EXERCISE_CANONICAL_CATEGORY_MISMATCH"
                if (program.request.availableEquipment.isNotEmpty()) {
                    val unavailable = exercise.equipment.split('|', ',').map(String::trim).filter(String::isNotBlank)
                        .any { it != "BODYWEIGHT" && it !in program.request.availableEquipment }
                    if (unavailable) failures += "EXERCISE_EQUIPMENT_CONSTRAINT_VIOLATED"
                }
            }
            if (row.setCount <= 0 || row.restSeconds < 0 || row.estimatedDurationSeconds < 0) {
                failures += "INVALID_PROGRAM_ROW_SHAPE"
            }
            val sets = ProgramSetPrescriptionResolver.resolve(row)
            if (sets.size != row.setCount || sets.any { set ->
                    set.setIndex <= 0 || set.reps < 0 || set.seconds < 0 ||
                        (set.reps == 0 && set.seconds == 0) || !set.weightKg.isFinite() || set.weightKg < 0.0 ||
                        set.targetRpeMin?.let { !it.isFinite() || it !in 1.0..10.0 } == true ||
                        (set.loadState in setOf(ProgramLoadState.USER_CALIBRATION_REQUIRED,
                            ProgramLoadState.REAL_ZERO_LOAD, ProgramLoadState.NOT_APPLICABLE) && set.weightKg != 0.0)
                }
            ) failures += "INVALID_EXECUTABLE_SET_PRESCRIPTION"
            if (row.taskProtocolSemanticsJson != null) {
                val owner = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
                val grant = taskProtocolPlan.authorizedByOwner[owner]
                val semantics = runCatching {
                    TaskProtocolExposureMetadata.fromJsonString(row.taskProtocolSemanticsJson)
                }.getOrNull()
                if (grant == null || semantics == null ||
                    semantics.authorization.definition.protocolId != grant.definition.protocolId ||
                    !semantics.matchesMaterializedItem(
                        item = row,
                        actualActivityKind = snapshot.activityKind(row.exerciseStableKey),
                        currentB4Tasks = grant.attributedTasks,
                        exactDirectTasks = grant.attributedTasks,
                        selectedPrimaryTargetId = "TASK:${grant.definition.primaryTask.name}"
                    )
                ) failures += "TASK_B6_PROTOCOL_MATERIALIZATION_INVALID"
            }
        }

        // Every newly selected target owner with a physical row must have exact B4/B5/B6
        // authorization. A missing authorization can be reported as unmet demand only when
        // no unauthorized physical prescription was emitted.
        program.items.filterNot(ProgramSkeletonItem::requiredTemplateAnchor).forEach { row ->
            if (StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole) !in candidateByOwner) {
                failures += "B5_PHYSICAL_ROW_WITHOUT_SELECTED_CANDIDATE"
            }
        }
        candidateByOwner.forEach { (owner, candidate) ->
            if (candidate.coveredTargetIds.any { it !in targets }) failures += "B5_SELECTED_TARGET_NOT_IN_B4_PLAN"
            candidate.coveredTargetIds.filter { it.startsWith("MOVEMENT:") }.forEach { targetId ->
                val requestedCoverage = runCatching {
                    MovementCoverage.valueOf(targetId.removePrefix("MOVEMENT:"))
                }.getOrNull()
                val canonicalDecision = snapshot.canonicalMovementCoverage[owner.stableKey]
                if (requestedCoverage == null || canonicalDecision?.directCoverageAdmissible != true ||
                    !canonicalDecision.coverage.directlyRepresents(requestedCoverage)
                ) failures += "B5_MOVEMENT_CANONICAL_CLASSIFICATION_INVALID"
            }
            val ownerRows = rowsByOwner[owner].orEmpty()
            if (ownerRows.isEmpty()) return@forEach
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
                if (authorization?.quality == TrainableQuality.STRENGTH && !CanonicalStrengthExposureCapability.strengthPossible(owner.stableKey)) {
                    failures += "B5_STRENGTH_CANONICAL_CLASSIFICATION_INVALID"
                }
                val audit = materializationAudits.singleOrNull {
                    it.targetId == targetId && it.owner?.let { value ->
                        value.stableKey == owner.stableKey && value.selectionRole == owner.selectionRole
                    } == true
                }
                if (audit == null || audit.materializedWeeklySetUnits == 0 ||
                    audit.targetCompatibleMaterializedUnits != audit.materializedWeeklySetUnits ||
                    audit.executionAuthority == StimulusPrescriptionExecutionAuthority.UNRESOLVED ||
                    audit.executionAuthority == StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT
                ) failures += "B6_MATERIALIZED_DOSE_NOT_EXECUTABLE"
                if (authorization?.executionAuthority == StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT &&
                    ownerRows.flatMap(ProgramSetPrescriptionResolver::resolve).any {
                        it.loadState != ProgramLoadState.USER_CALIBRATION_REQUIRED
                    }
                ) failures += "B6_USER_LOAD_INPUT_REQUIRED_BUT_NOT_ENCODED"
                if (ownerRows.flatMap(ProgramSetPrescriptionResolver::resolve).any {
                        it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
                    } && (authorization?.status != StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION ||
                        authorization.executionAuthority != StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT)
                ) failures += "B6_USER_CALIBRATION_WITHOUT_TYPED_B6_AUTHORITY"
            }
            candidate.coveredTargetIds.filter { it.startsWith("TASK:") }.forEach {
                if (owner !in taskProtocolPlan.authorizedByOwner || ownerRows.any { row -> row.taskProtocolSemanticsJson == null }) {
                    failures += "B6_SELECTED_TASK_OWNER_WITHOUT_APPROVED_PROTOCOL"
                }
            }
            candidate.coveredTargetIds.filter { it.startsWith("MOVEMENT:") }.forEach { targetId ->
                val movement = authorizationPlan.movementAuthorizations.singleOrNull {
                    it.targetId == targetId && it.owner == owner
                }
                if (movement == null || movement.status == StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY ||
                    movement.status == StimulusMovementB6Status.NO_B5_MOVEMENT_OWNER
                ) failures += "B6_SELECTED_MOVEMENT_OWNER_WITHOUT_AUTHORITY"
                if (movement?.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6) {
                    val audit = materializationAudits.singleOrNull { value ->
                        value.targetId == targetId && value.owner?.let {
                            it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole
                        } == true
                    }
                    if (audit == null || audit.materializedWeeklySetUnits == 0 ||
                        audit.targetCompatibleMaterializedUnits != audit.materializedWeeklySetUnits ||
                        audit.executionAuthority == StimulusPrescriptionExecutionAuthority.UNRESOLVED ||
                        audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
                    ) failures += "B6_CORE_DIRECT_MOVEMENT_MATERIALIZATION_INVALID"
                }
            }
        }

        materializationAudits.forEach { audit ->
            if (audit.state == StimulusPrescriptionMaterializationState.INVARIANT_FAILURE || audit.overrun > 0 ||
                !audit.prescriptionPreservedOrSubset || audit.targetCompatibleMaterializedUnits > audit.materializedWeeklySetUnits
            ) failures += "B6_PHYSICAL_SET_INTEGRITY_FAILURE"
            if (audit.materializedWeeklySetUnits > 0 && audit.executionAuthority == StimulusPrescriptionExecutionAuthority.UNRESOLVED) {
                failures += "B6_MATERIALIZED_WITHOUT_EXECUTION_AUTHORITY"
            }
        }

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
