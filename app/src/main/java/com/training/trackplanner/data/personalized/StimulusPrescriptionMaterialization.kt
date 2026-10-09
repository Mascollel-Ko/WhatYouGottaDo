package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.validatedTargetRpeMin

enum class StimulusPrescriptionAuthorizationSource {
    CANONICAL_HISTORY_PRESCRIPTION,
    B5_SELECTION_PROBE
}

enum class StimulusPrescriptionAuthorizationStatus {
    AUTHORIZED_EXISTING_COMPATIBLE,
    AUTHORIZED_SAFE_REPAIR,
    AUTHORIZED_COLD_START_USER_CALIBRATION,
    CONFLICTING_MULTI_QUALITY_AUTHORITY,
    NO_EXECUTABLE_AUTHORIZATION,
    AMBIGUOUS_OWNER,
    MODEL_UNAVAILABLE
}

enum class StimulusMovementB6Status {
    COVERED_BY_EXISTING_QUALITY_B6,
    COVERED_BY_APPROVED_TASK_B6,
    AUTHORIZED_REGIONAL_HYPERTROPHY_B6,
    AUTHORIZED_CORE_DIRECT_B6,
    NO_EXECUTABLE_MOVEMENT_AUTHORITY,
    NO_B5_MOVEMENT_OWNER
}

/** B6 observes exact pre-existing authority for a shared owner; this grants no separate movement dose. */
data class StimulusMovementB6Authorization(
    val targetId: String,
    val owner: StimulusPrescriptionOwnerIdentity?,
    val status: StimulusMovementB6Status,
    val reasonCodes: List<String>,
    val existingAuthorityTargetId: String? = null
)

data class StimulusPrescriptionAuthorization(
    val targetId: String,
    val quality: TrainableQuality?,
    val owner: StimulusPrescriptionOwner?,
    val source: StimulusPrescriptionAuthorizationSource?,
    val inputPrescription: PlannedPrescription?,
    val plannedCompatibility: PlannedStimulusCompatibility?,
    val authorizedPrescription: PlannedPrescription?,
    val status: StimulusPrescriptionAuthorizationStatus,
    val reasonCodes: List<String> = emptyList(),
    val executionAuthority: StimulusPrescriptionExecutionAuthority = canonicalExecutionAuthority(quality, authorizedPrescription),
    val coldStartCalibration: ColdStartStrengthCalibrationProposal? = null,
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false,
    val authorityRecovery: ExecutionAuthorityResolution? = null
)

data class StimulusPrescriptionAuthorizationPlan(
    val authorizations: List<StimulusPrescriptionAuthorization>,
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false,
    /** Canonical B5 owner prescriptions; only history-backed owners may be preserved on conflict. */
    val canonicalPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = emptyMap(),
    val historyBackedOwners: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
    /** Movement targets may reuse an already-authorized physical row but never grant a new dose. */
    val movementAuthorizations: List<StimulusMovementB6Authorization> = emptyList(),
    /** Exact owner-level B6 prescriptions for non-TrainableQuality movement targets such as Core. */
    val movementOwnerPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = emptyMap()
) {
    private val executableAuthorizations: List<StimulusPrescriptionAuthorization> = authorizations
        .mapNotNull { authorization ->
            val owner = authorization.owner ?: return@mapNotNull null
            val prescription = authorization.authorizedPrescription ?: return@mapNotNull null
            if (authorization.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION,
                    StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
                ) || authorization.quality == null) return@mapNotNull null
            authorization
        }

    /** Lossless canonical authority keyed by owner plus quality. */
    val authorizedPrescriptions: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription> =
        executableAuthorizations
            .sortedWith(compareBy({ it.owner!!.stableKey }, { it.owner!!.selectionRole }, { it.quality?.name.orEmpty() }))
            .associate { authorization ->
                val owner = requireNotNull(authorization.owner)
                StimulusPrescriptionAuthorityIdentity(owner.stableKey, owner.selectionRole, requireNotNull(authorization.quality)) to
                    requireNotNull(authorization.authorizedPrescription)
            }

    /** Arbitration is explicit and deterministic; conflicting owners are absent from owner-only lookup. */
    val multiQualityResolutions: Map<StimulusPrescriptionOwnerIdentity, StimulusMultiQualityPrescriptionResolution> =
        executableAuthorizations.groupBy { authorization ->
            val owner = requireNotNull(authorization.owner)
            StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
        }.mapValues { (owner, rows) ->
            val authorities = rows.map { authorization ->
                val rowOwner = requireNotNull(authorization.owner)
                StimulusPrescriptionAuthorityIdentity(rowOwner.stableKey, rowOwner.selectionRole, requireNotNull(authorization.quality))
            }.sortedWith(compareBy({ it.stableKey }, { it.selectionRole }, { it.quality.name }))
            val prescriptions = rows.mapNotNull { it.authorizedPrescription }
            val semanticShared = prescriptions.firstOrNull()?.takeIf { first ->
                prescriptions.all { candidate ->
                    candidate.sets == first.sets && candidate.restSeconds == first.restSeconds && candidate.weightSource == first.weightSource
                }
            }
            when {
                rows.isEmpty() -> StimulusMultiQualityPrescriptionResolution(owner, StimulusMultiQualityPrescriptionResolutionStatus.NO_EXECUTABLE_AUTHORITY)
                rows.size == 1 -> StimulusMultiQualityPrescriptionResolution(owner, StimulusMultiQualityPrescriptionResolutionStatus.SINGLE_EXECUTABLE_AUTHORITY, authorities, prescriptions.singleOrNull())
                semanticShared != null -> StimulusMultiQualityPrescriptionResolution(owner, StimulusMultiQualityPrescriptionResolutionStatus.IDENTICAL_MULTI_QUALITY_AUTHORITY, authorities, semanticShared, listOf("B6_MULTI_QUALITY_SHARED_PRESCRIPTION"))
                else -> StimulusMultiQualityPrescriptionResolution(owner, StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY, authorities, reasonCodes = listOf("B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT", "B6_MULTI_QUALITY_DISTINCT_OWNER_REQUIRED"))
            }
        }

    /** Compatibility projection is exposed only for a single or identical shared authority. */
    val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> =
        multiQualityResolutions.mapNotNull { (owner, resolution) ->
            resolution.sharedPrescription?.let { owner to it }
        }.toMap()

    val ownerExecutionDispositions: Map<StimulusPrescriptionOwnerIdentity, StimulusPrescriptionOwnerExecutionDisposition> =
        multiQualityResolutions.mapValues { (owner, resolution) ->
            when (resolution.status) {
                StimulusMultiQualityPrescriptionResolutionStatus.SINGLE_EXECUTABLE_AUTHORITY,
                StimulusMultiQualityPrescriptionResolutionStatus.IDENTICAL_MULTI_QUALITY_AUTHORITY,
                StimulusMultiQualityPrescriptionResolutionStatus.COMPATIBLE_SHARED_PRESCRIPTION ->
                    StimulusPrescriptionOwnerExecutionDisposition.EXECUTABLE_EXACT_AUTHORITY
                StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY ->
                    if (owner in historyBackedOwners) StimulusPrescriptionOwnerExecutionDisposition.PRESERVE_INCUMBENT_OWNER
                    else StimulusPrescriptionOwnerExecutionDisposition.EXCLUDE_CONFLICTING_ADDITION
                StimulusMultiQualityPrescriptionResolutionStatus.NO_EXECUTABLE_AUTHORITY ->
                    StimulusPrescriptionOwnerExecutionDisposition.NO_EXECUTABLE_AUTHORITY
            }
        }

    val executionAuthorityResolutions: Map<StimulusPrescriptionOwnerIdentity, ExecutionAuthorityResolution> =
        authorizations.mapNotNull { authorization ->
            val owner = authorization.owner ?: return@mapNotNull null
            val identity = StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
            val recovery = authorization.authorityRecovery ?: when (authorization.status) {
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION ->
                    ExecutionAuthorityResolution(ExecutionAuthorityResolutionStatus.READY,
                        ExecutionAuthorityResolutionReason.EXACT_AUTHORITY_AVAILABLE,
                        ExecutionAuthorityReturnTarget.NONE, identity, listOf(identity), identity)
                StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER ->
                    ExecutionAuthorityResolution(ExecutionAuthorityResolutionStatus.NEEDS_OWNER_RESELECTION,
                        ExecutionAuthorityResolutionReason.AMBIGUOUS_OWNER,
                        ExecutionAuthorityReturnTarget.B5_OWNER_SELECTION, identity)
                StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
                StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY ->
                    ExecutionAuthorityResolution(ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                        ExecutionAuthorityResolutionReason.NO_EXECUTABLE_AUTHORIZATION,
                        ExecutionAuthorityReturnTarget.NONE, identity)
            }
            identity to recovery
        }.groupBy({ it.first }, { it.second }).toSortedMap(compareBy({ it.stableKey }, { it.selectionRole }))
            .mapValues { (identity, recoveries) ->
                val distinct = recoveries.distinct()
                if (distinct.size == 1) distinct.single() else ExecutionAuthorityResolution(
                    status = ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                    reason = ExecutionAuthorityResolutionReason.UNSUPPORTED_PRESCRIPTION_AUTHORITY,
                    returnTarget = ExecutionAuthorityReturnTarget.NONE,
                    originalOwner = identity,
                    attemptedOwners = listOf(identity)
                )
            }

    val conflictingOwners: Set<StimulusPrescriptionOwnerIdentity>
        get() = multiQualityResolutions.filterValues {
            it.status == StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
        }.keys

    fun provider(): ExactPrescriptionAuthorizationProvider = object : ExactPrescriptionAuthorizationProvider {
        override val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> =
            this@StimulusPrescriptionAuthorizationPlan.authorizedOwners + this@StimulusPrescriptionAuthorizationPlan.movementOwnerPrescriptions
        override val authorizedPrescriptions: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription> = this@StimulusPrescriptionAuthorizationPlan.authorizedPrescriptions
        override val multiQualityResolutions: Map<StimulusPrescriptionOwnerIdentity, StimulusMultiQualityPrescriptionResolution> = this@StimulusPrescriptionAuthorizationPlan.multiQualityResolutions
        override val ownerExecutionDispositions: Map<StimulusPrescriptionOwnerIdentity, StimulusPrescriptionOwnerExecutionDisposition> =
            this@StimulusPrescriptionAuthorizationPlan.ownerExecutionDispositions + this@StimulusPrescriptionAuthorizationPlan.movementOwnerPrescriptions.keys.associateWith {
                StimulusPrescriptionOwnerExecutionDisposition.EXECUTABLE_EXACT_AUTHORITY
            }
        override val executionAuthorityResolutions: Map<StimulusPrescriptionOwnerIdentity, ExecutionAuthorityResolution> =
            this@StimulusPrescriptionAuthorizationPlan.executionAuthorityResolutions + authorizations.mapNotNull { authorization ->
                val owner = authorization.owner ?: return@mapNotNull null
                val recovery = authorization.authorityRecovery ?: return@mapNotNull null
                StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole) to recovery
            }.toMap()
        override val b5SelectedQualityOwners: Set<StimulusPrescriptionOwnerIdentity> = authorizations.mapNotNullTo(linkedSetOf()) { authorization ->
            val owner = authorization.owner ?: return@mapNotNullTo null
            owner.takeIf { authorization.quality != null }?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        }
        override val b5SelectedMovementOwners: Set<StimulusPrescriptionOwnerIdentity> = movementAuthorizations.mapNotNullTo(linkedSetOf()) { authorization ->
            val owner = authorization.owner ?: return@mapNotNullTo null
            owner.takeIf {
                authorization.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6 &&
                    it in movementOwnerPrescriptions
            }
        }
        override val canonicalPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = this@StimulusPrescriptionAuthorizationPlan.canonicalPrescriptions

        override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? {
            if (requestedSets < 0) return null
            val prescription = authorizedOwners[StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)] ?: return null
            if (requestedSets > prescription.sets.size) return null
            return prescription.copy(sets = prescription.sets.take(requestedSets).mapIndexed { index, set -> set.copy(setIndex = index + 1) })
        }

        override fun authorizedPrescriptionFor(item: PlannedExercise, quality: TrainableQuality, requestedSets: Int): PlannedPrescription? {
            if (requestedSets < 0) return null
            val prescription = authorizedPrescriptions[StimulusPrescriptionAuthorityIdentity(item.stableKey, item.role, quality)] ?: return null
            if (requestedSets > prescription.sets.size) return null
            return prescription.copy(sets = prescription.sets.take(requestedSets).mapIndexed { index, set -> set.copy(setIndex = index + 1) })
        }
    }
}

enum class StimulusPrescriptionMaterializationState {
    FULLY_MATERIALIZED,
    PARTIALLY_MATERIALIZED,
    NOT_MATERIALIZED,
    INVARIANT_FAILURE
}

data class StimulusPrescriptionMaterializationAudit(
    val targetId: String,
    val quality: TrainableQuality?,
    val owner: StimulusPrescriptionOwner?,
    val authorizedWeeklySetUnits: Int,
    val materializedWeeklySetUnits: Int,
    val targetCompatibleMaterializedUnits: Int,
    val shortfall: Int,
    val overrun: Int,
    val prescriptionPreservedOrSubset: Boolean,
    val state: StimulusPrescriptionMaterializationState,
    val reasonCodes: List<String> = emptyList(),
    val executionAuthority: StimulusPrescriptionExecutionAuthority = StimulusPrescriptionExecutionAuthority.UNRESOLVED,
    /** One row for every expected program week, including weeks with no owner row. */
    val weeklyAudits: List<StimulusPrescriptionWeekMaterializationAudit> = emptyList(),
    /** Conservative minimum across the complete expected horizon. */
    val minimumWeeklyMaterializedUnits: Int = materializedWeeklySetUnits,
    val minimumWeeklyCompatibleUnits: Int = targetCompatibleMaterializedUnits,
    val maximumWeeklyShortfall: Int = shortfall,
    val maximumWeeklyOverrun: Int = overrun,
    val fullyMaterializedWeekCount: Int = 0,
    val partiallyMaterializedWeekCount: Int = 0,
    val missingWeekCount: Int = 0,
    val totalAuthorizedUnits: Int = authorizedWeeklySetUnits,
    val totalMaterializedUnits: Int = materializedWeeklySetUnits,
    val totalCompatibleUnits: Int = targetCompatibleMaterializedUnits,
    val totalShortfallUnits: Int = shortfall
)

data class StimulusPrescriptionWeekMaterializationAudit(
    val weekNumber: Int,
    val authorizedSetUnits: Int,
    val materializedSetUnits: Int,
    val targetCompatibleMaterializedUnits: Int,
    val shortfall: Int,
    val overrun: Int,
    val prescriptionPreservedOrSubset: Boolean,
    val reasonCodes: List<String> = emptyList()
)

/** Builds B6.2 authority from the B6.1 oracle without building another program. */
class StimulusPrescriptionAuthorizationEngine(
    private val realizationEngine: StimulusPrescriptionRealizationPlanEngine = StimulusPrescriptionRealizationPlanEngine()
) {
    /** Map adapter for focused mechanism tests; production passes the typed actual-history context. */
    internal fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        canonicalPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
    ): StimulusPrescriptionAuthorizationPlan = build(
        targetPlan,
        selectionPlan,
        snapshot,
        CanonicalPrescriptionContext(canonicalPrescriptions, canonicalPrescriptions.keys)
    )

    internal fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        canonicalPrescriptionContext: CanonicalPrescriptionContext,
        approvedTaskB6Owners: Set<StimulusPrescriptionOwnerIdentity> = emptySet()
    ): StimulusPrescriptionAuthorizationPlan {
        val realization = realizationEngine.build(
            targetPlan, selectionPlan, snapshot,
            canonicalPrescriptionContext.prescriptions,
            canonicalPrescriptionContext.historyBackedOwners,
            currentPrescriptionsByQuality = canonicalPrescriptionContext.prescriptionsByQuality,
            historyBackedAuthorities = canonicalPrescriptionContext.historyBackedAuthorities
        )
        val authorizations = targetPlan.qualityTargets.map { target ->
            val targetId = "QUALITY:${target.quality.name}"
            val resolution = realization.resolutions.firstOrNull { it.targetId == targetId }
            authorizationFor(target, resolution)
        }
        // First retain all executable quality rows to determine owner-local arbitration, then
        // mark both rows of a real conflict as typed non-executable authority. The rows remain
        // lossless and auditable; only the builder disposition is localized downstream.
        val preliminary = StimulusPrescriptionAuthorizationPlan(
            authorizations,
            canonicalPrescriptions = canonicalPrescriptionContext.prescriptions,
            historyBackedOwners = canonicalPrescriptionContext.historyBackedOwners
        )
        val localized = authorizations.map { authorization ->
            val owner = authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
            if (owner != null && owner in preliminary.conflictingOwners && authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                )) {
                authorization.copy(
                    status = StimulusPrescriptionAuthorizationStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY,
                    reasonCodes = (authorization.reasonCodes + preliminary.multiQualityResolutions.getValue(owner).reasonCodes).distinct()
                )
            } else authorization
        }
        return StimulusPrescriptionAuthorizationPlan(
            localized,
            canonicalPrescriptions = canonicalPrescriptionContext.prescriptions,
            historyBackedOwners = canonicalPrescriptionContext.historyBackedOwners,
            movementAuthorizations = movementB6Authorizations(targetPlan, selectionPlan, localized, approvedTaskB6Owners)
        )
    }

    private fun movementB6Authorizations(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        qualityAuthorizations: List<StimulusPrescriptionAuthorization>,
        approvedTaskB6Owners: Set<StimulusPrescriptionOwnerIdentity>
    ): List<StimulusMovementB6Authorization> = targetPlan.movementTargets.map { target ->
        val candidate = selectionPlan.selectedCandidates.firstOrNull { target.targetId in it.coveredTargetIds }
        val owner = candidate?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        val qualityAuthority = owner?.let { selectedOwner -> qualityAuthorizations.firstOrNull { authorization ->
            authorization.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) == selectedOwner } == true &&
                authorization.authorizedPrescription != null && authorization.status in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
                )
        } }
        when {
            owner == null -> StimulusMovementB6Authorization(
                targetId = target.targetId,
                owner = null,
                status = StimulusMovementB6Status.NO_B5_MOVEMENT_OWNER,
                reasonCodes = listOf("B6_NOT_REACHED_WITHOUT_EXACT_B5_MOVEMENT_OWNER")
            )
            qualityAuthority != null -> StimulusMovementB6Authorization(
                targetId = target.targetId,
                owner = owner,
                status = StimulusMovementB6Status.COVERED_BY_EXISTING_QUALITY_B6,
                reasonCodes = listOf("MOVEMENT_COVERAGE_REUSES_EXISTING_QUALITY_AUTHORIZED_ROW", "NO_ADDITIONAL_MOVEMENT_DOSE_GRANTED"),
                existingAuthorityTargetId = qualityAuthority.targetId
            )
            owner in approvedTaskB6Owners -> StimulusMovementB6Authorization(
                targetId = target.targetId,
                owner = owner,
                status = StimulusMovementB6Status.COVERED_BY_APPROVED_TASK_B6,
                reasonCodes = listOf("MOVEMENT_COVERAGE_REUSES_APPROVED_TASK_ROW", "NO_ADDITIONAL_MOVEMENT_DOSE_GRANTED")
            )
            else -> StimulusMovementB6Authorization(
                targetId = target.targetId,
                owner = owner,
                status = StimulusMovementB6Status.NO_EXECUTABLE_MOVEMENT_AUTHORITY,
                reasonCodes = listOf(
                    "NO_EXACT_MOVEMENT_PRESCRIPTION_AUTHORITY",
                    "NO_APPROVED_MOVEMENT_DOSE_POLICY",
                    "MOVEMENT_TARGET_HAS_NO_NUMERIC_DOSE_AUTHORITY"
                )
            )
        }
    }

    private fun authorizationFor(
        target: StimulusQualityTarget,
        resolution: StimulusPrescriptionResolution?
    ): StimulusPrescriptionAuthorization {
        val targetId = "QUALITY:${target.quality.name}"
        if (target.quality == TrainableQuality.HYPERTROPHY && target.numericAuthority in setOf(
                StimulusTargetNumericAuthority.NONE,
                StimulusTargetNumericAuthority.DIRECTION_ONLY,
                StimulusTargetNumericAuthority.UNRESOLVED
            )) {
            return StimulusPrescriptionAuthorization(
                targetId, target.quality, resolution?.owner,
                resolution?.owner?.let { sourceFor(it.source) },
                resolution?.currentPrescription ?: resolution?.probePrescription,
                resolution?.plannedCompatibility, null,
                StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                listOf("HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE"),
                authorityRecovery = ExecutionAuthorityResolution(
                    ExecutionAuthorityResolutionStatus.NEEDS_TARGET_RESOLUTION,
                    ExecutionAuthorityResolutionReason.HYPERTROPHY_NUMERIC_AUTHORITY_UNAVAILABLE,
                    ExecutionAuthorityReturnTarget.B4_TARGET_RESOLUTION,
                    resolution?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                )
            )
        }
        if (target.quality != TrainableQuality.STRENGTH && target.quality != TrainableQuality.HYPERTROPHY) {
            return StimulusPrescriptionAuthorization(targetId, target.quality, resolution?.owner,
                resolution?.owner?.let { sourceFor(it.source) }, resolution?.currentPrescription ?: resolution?.probePrescription,
                resolution?.plannedCompatibility, null,
                StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
                listOf("CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE"),
                authorityRecovery = ExecutionAuthorityResolution(
                    ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                    ExecutionAuthorityResolutionReason.UNSUPPORTED_PRESCRIPTION_AUTHORITY,
                    ExecutionAuthorityReturnTarget.NONE,
                    resolution?.owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
                ))
        }
        if (resolution == null) return StimulusPrescriptionAuthorization(targetId, target.quality, null, null, null, null, null,
            StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE, listOf("STRENGTH_REALIZATION_RESOLUTION_UNAVAILABLE"),
            authorityRecovery = ExecutionAuthorityResolution(
                ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                ExecutionAuthorityResolutionReason.NO_EXECUTABLE_AUTHORIZATION,
                ExecutionAuthorityReturnTarget.NONE
            ))
        val source = resolution.owner?.let { sourceFor(it.source) }
        val input = resolution.currentPrescription ?: resolution.probePrescription
        val effort = target.quality.canonicalEffortTarget()
        fun executableInput(value: PlannedPrescription): PlannedPrescription = if (target.quality == TrainableQuality.HYPERTROPHY) {
            value.copy(sets = value.sets.map { set -> set.copy(targetRpeMin = effort.minimumRpe.validatedTargetRpeMin()) })
        } else value
        fun executionAuthority(value: PlannedPrescription?): StimulusPrescriptionExecutionAuthority =
            canonicalExecutionAuthority(target.quality, value)
        return when {
            resolution.status == StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE &&
                resolution.owner != null && input != null && executableHypertrophyPrescription(target, input) -> StimulusPrescriptionAuthorization(targetId, target.quality,
                resolution.owner, source, input, resolution.plannedCompatibility, executableInput(input),
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                listOf(if (target.quality == TrainableQuality.HYPERTROPHY) "HYPERTROPHY_PRESCRIPTION_ALREADY_COMPATIBLE" else "EXISTING_COMPATIBLE_PRESCRIPTION"),
                executionAuthority(executableInput(input)),
                authorityRecovery = resolution.authorityRecovery)
            resolution.status == StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED &&
                resolution.owner != null && input != null && resolution.proposedPrescription != null &&
                executableHypertrophySets(target, resolution.proposedPrescription.sets) -> {
                val proposal = resolution.proposedPrescription
                val authorized = input.copy(sets = proposal.sets)
                StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                    resolution.plannedCompatibility, authorized,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    listOf(if (target.quality == TrainableQuality.HYPERTROPHY) "HYPERTROPHY_SAFE_REPAIR_RESOLVED" else "SAFE_REPAIRED_PRESCRIPTION"),
                    executionAuthority(authorized),
                    authorityRecovery = resolution.authorityRecovery)
            }
            resolution.status == StimulusPrescriptionResolutionStatus.COLD_START_USER_CALIBRATION_RESOLVED &&
                resolution.owner != null && input != null && resolution.coldStartStrengthCalibration?.available == true &&
                target.quality == TrainableQuality.STRENGTH -> {
                val coldStart = requireNotNull(resolution.coldStartStrengthCalibration.proposal)
                val authorized = input.copy(
                    text = "${coldStart.setCount}세트 × ${coldStart.repetitions}회 · RPE ${coldStart.targetRpe} · 중량 직접 선택",
                    sets = coldStart.sets,
                    restSeconds = coldStart.restSeconds,
                    weightSource = "COLD_START_USER_CALIBRATION"
                )
                StimulusPrescriptionAuthorization(
                    targetId, target.quality, resolution.owner, source, input,
                    resolution.plannedCompatibility, authorized,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION,
                    coldStart.reasonCodes,
                    StimulusPrescriptionExecutionAuthority.REQUIRES_USER_LOAD_INPUT,
                    coldStartCalibration = coldStart,
                    authorityRecovery = ExecutionAuthorityResolution(
                        ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED,
                        ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                        ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT,
                        StimulusPrescriptionOwnerIdentity(resolution.owner.stableKey, resolution.owner.selectionRole),
                        listOf(StimulusPrescriptionOwnerIdentity(resolution.owner.stableKey, resolution.owner.selectionRole)),
                        StimulusPrescriptionOwnerIdentity(resolution.owner.stableKey, resolution.owner.selectionRole)
                    )
                )
            }
            resolution.status in setOf(
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER,
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_EXISTING_REALIZATION_OWNER
            ) -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER,
                resolution.reasonCodes, authorityRecovery = resolution.authorityRecovery)
            else -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                resolution.reasonCodes.ifEmpty { listOf("NO_EXECUTABLE_STRENGTH_AUTHORIZATION") },
                authorityRecovery = resolution.authorityRecovery)
        }
    }

    private fun executableHypertrophyPrescription(
        target: StimulusQualityTarget,
        prescription: PlannedPrescription
    ): Boolean = executableHypertrophySets(target, prescription.sets)

    private fun executableHypertrophySets(
        target: StimulusQualityTarget,
        sets: List<com.training.trackplanner.data.ProgramSetPrescription>
    ): Boolean = target.quality != TrainableQuality.HYPERTROPHY || (
        sets.isNotEmpty() && sets.all { set ->
            set.reps in 7..15 && set.weightKg.isFinite() && set.weightKg > 0.0
        }
    )

    private fun sourceFor(value: String): StimulusPrescriptionAuthorizationSource = when {
        value == "B5_SELECTION" -> StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE
        else -> StimulusPrescriptionAuthorizationSource.CANONICAL_HISTORY_PRESCRIPTION
    }
}

class StimulusPrescriptionMaterializationAuditEngine(
    private val plannedResolver: StimulusPlannedPrescriptionResolver = StimulusPlannedPrescriptionResolver()
) {
    fun audit(
        plan: StimulusPrescriptionAuthorizationPlan,
        experimental: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot
    ): List<StimulusPrescriptionMaterializationAudit> = plan.authorizations.map { authorization ->
        val expectedWeeks = (1..experimental.request.durationWeeks.coerceAtLeast(1)).toList()
        val owner = authorization.owner
        val authorized = authorization.authorizedPrescription
        val effectiveExecutionAuthority = if (authorization.quality == null && authorization.targetId.startsWith("MOVEMENT:")) {
            authorization.executionAuthority
        } else canonicalExecutionAuthority(authorization.quality, authorized)
        val ownerIdentity = owner?.let { StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole) }
        if (ownerIdentity != null && ownerIdentity in plan.conflictingOwners) {
            val conflictReasons = (authorization.reasonCodes +
                "B6_MULTI_QUALITY_OWNER_PRESCRIPTION_CONFLICT" +
                "B6_MULTI_QUALITY_DISTINCT_OWNER_REQUIRED").distinct()
            val weekly = expectedWeeks.map { week ->
                StimulusPrescriptionWeekMaterializationAudit(
                    weekNumber = week,
                    authorizedSetUnits = 0,
                    materializedSetUnits = 0,
                    targetCompatibleMaterializedUnits = 0,
                    shortfall = 0,
                    overrun = 0,
                    prescriptionPreservedOrSubset = true,
                    reasonCodes = conflictReasons
                )
            }
            return@map StimulusPrescriptionMaterializationAudit(
                targetId = authorization.targetId,
                quality = authorization.quality,
                owner = owner,
                authorizedWeeklySetUnits = 0,
                materializedWeeklySetUnits = 0,
                targetCompatibleMaterializedUnits = 0,
                shortfall = 0,
                overrun = 0,
                prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.NOT_MATERIALIZED,
                reasonCodes = conflictReasons,
                executionAuthority = effectiveExecutionAuthority,
                weeklyAudits = weekly,
                fullyMaterializedWeekCount = 0,
                partiallyMaterializedWeekCount = 0,
                missingWeekCount = 0,
                totalAuthorizedUnits = 0,
                totalMaterializedUnits = 0,
                totalCompatibleUnits = 0,
                totalShortfallUnits = 0
            )
        }
        if (owner == null || authorized == null) {
            val weekly = expectedWeeks.map { week ->
                StimulusPrescriptionWeekMaterializationAudit(
                    weekNumber = week,
                    authorizedSetUnits = 0,
                    materializedSetUnits = 0,
                    targetCompatibleMaterializedUnits = 0,
                    shortfall = 0,
                    overrun = 0,
                    prescriptionPreservedOrSubset = true,
                    reasonCodes = authorization.reasonCodes + "NO_EXECUTABLE_AUTHORIZATION"
                )
            }
            return@map StimulusPrescriptionMaterializationAudit(
                targetId = authorization.targetId,
                quality = authorization.quality,
                owner = owner,
                authorizedWeeklySetUnits = 0,
                materializedWeeklySetUnits = 0,
                targetCompatibleMaterializedUnits = 0,
                shortfall = 0,
                overrun = 0,
                prescriptionPreservedOrSubset = true,
                state = StimulusPrescriptionMaterializationState.NOT_MATERIALIZED,
                reasonCodes = authorization.reasonCodes + "NO_EXECUTABLE_AUTHORIZATION",
                executionAuthority = effectiveExecutionAuthority,
                weeklyAudits = weekly,
                fullyMaterializedWeekCount = 0,
                partiallyMaterializedWeekCount = 0,
                missingWeekCount = 0,
                totalAuthorizedUnits = 0,
                totalMaterializedUnits = 0,
                totalCompatibleUnits = 0,
                totalShortfallUnits = 0
            )
        }
        val rowsByWeek = experimental.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            .groupBy(ProgramSkeletonItem::weekNumber)
        val exactCoreMovementAuthorization = authorization.quality == null && ownerIdentity != null &&
            plan.movementOwnerPrescriptions[ownerIdentity] == authorized &&
            plan.movementAuthorizations.any {
                it.targetId == authorization.targetId && it.owner == ownerIdentity &&
                    it.status == StimulusMovementB6Status.AUTHORIZED_CORE_DIRECT_B6
            } && plan.authorizations.count {
                it.targetId == authorization.targetId && it.quality == null && it.owner == owner
            } == 1
        val weeklyAudits = expectedWeeks.map { week ->
            val rows = rowsByWeek[week].orEmpty()
            val materialized = rows.sumOf { it.setPrescriptions.size }
            val overrun = (materialized - authorized.sets.size).coerceAtLeast(0)
            val subsetValidation = validateAuthorizedWeeklySubset(rows, authorized, owner.stableKey, owner.selectionRole)
            val compatible = if (exactCoreMovementAuthorization && subsetValidation.valid) {
                materialized
            } else authorization.quality?.let { quality -> rows.sumOf { row ->
                val planned = PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource)
                val compatibility = plannedResolver.compatibility(quality, planned, snapshot, owner.stableKey)
                val calibrationCompatible = authorization.status == StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION &&
                    compatibility.status == PlannedStimulusCompatibilityStatus.COMPATIBLE_REQUIRES_USER_LOAD_INPUT
                if (compatibility.status == PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT || calibrationCompatible) {
                    row.setPrescriptions.size
                } else 0
            } } ?: 0
            val preserved = subsetValidation.valid
            val shortfall = (authorized.sets.size - materialized).coerceAtLeast(0)
            buildList {
                if (shortfall > 0) add(if (materialized == 0) "B6_AUTHORIZATION_MISSING_WEEK" else "B6_AUTHORIZATION_SHORTFALL")
                if (overrun > 0) add("B6_AUTHORIZATION_OVERRUN")
                if (!preserved) add("B6_PRESCRIPTION_NOT_PRESERVED")
                addAll(subsetValidation.reasonCodes)
                if (compatible < materialized) add("B6_TARGET_COMPATIBILITY_SHORTFALL")
                if (exactCoreMovementAuthorization) add("B6_EXACT_CORE_DIRECT_AUTHORIZATION_MATCHED")
                if (authorization.source == StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE) add("B5_SELECTION_PROBE_AUTHORIZED")
            }.let { reasons ->
                StimulusPrescriptionWeekMaterializationAudit(week, authorized.sets.size, materialized, compatible, shortfall, overrun, preserved, reasons)
            }
        }
        val materialized = weeklyAudits.sumOf { it.materializedSetUnits }
        val compatible = weeklyAudits.sumOf { it.targetCompatibleMaterializedUnits }
        val shortfall = weeklyAudits.maxOfOrNull { it.shortfall } ?: 0
        val overrun = weeklyAudits.maxOfOrNull { it.overrun } ?: 0
        val preserved = weeklyAudits.all { it.prescriptionPreservedOrSubset }
        val fully = weeklyAudits.count { it.materializedSetUnits == it.authorizedSetUnits && it.targetCompatibleMaterializedUnits == it.materializedSetUnits && it.overrun == 0 && it.prescriptionPreservedOrSubset }
        val partial = weeklyAudits.count {
            it.materializedSetUnits > 0 &&
                !(it.materializedSetUnits == it.authorizedSetUnits &&
                    it.targetCompatibleMaterializedUnits == it.materializedSetUnits &&
                    it.overrun == 0 && it.prescriptionPreservedOrSubset)
        }
        val missing = weeklyAudits.count { it.materializedSetUnits == 0 && it.authorizedSetUnits > 0 }
        val reasons = buildList {
            if (shortfall > 0) add("B6_AUTHORIZATION_SHORTFALL")
            if (missing > 0) add("B6_AUTHORIZATION_MISSING_WEEK")
            if (overrun > 0) add("B6_AUTHORIZATION_OVERRUN")
            if (!preserved) add("B6_PRESCRIPTION_NOT_PRESERVED")
            if (compatible < materialized) add("B6_TARGET_COMPATIBILITY_SHORTFALL")
            addAll(weeklyAudits.flatMap { it.reasonCodes }.filter { it.startsWith("B6_") })
            authorization.quality?.takeIf { it == TrainableQuality.HYPERTROPHY }?.let { quality ->
                authorized.effortInsufficiencyReason(quality.canonicalEffortTarget())?.let(::add)
            }
            if (authorization.source == StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE) add("B5_SELECTION_PROBE_AUTHORIZED")
        }
        val state = when {
            overrun > 0 || !preserved -> StimulusPrescriptionMaterializationState.INVARIANT_FAILURE
            materialized == 0 -> StimulusPrescriptionMaterializationState.NOT_MATERIALIZED
            shortfall > 0 || compatible < materialized -> StimulusPrescriptionMaterializationState.PARTIALLY_MATERIALIZED
            else -> StimulusPrescriptionMaterializationState.FULLY_MATERIALIZED
        }
        StimulusPrescriptionMaterializationAudit(authorization.targetId, authorization.quality, owner,
            authorized.sets.size,
            weeklyAudits.minOfOrNull { it.materializedSetUnits } ?: 0,
            weeklyAudits.minOfOrNull { it.targetCompatibleMaterializedUnits } ?: 0,
            shortfall, overrun, preserved, state, reasons, effectiveExecutionAuthority,
            weeklyAudits = weeklyAudits,
            minimumWeeklyMaterializedUnits = weeklyAudits.minOfOrNull { it.materializedSetUnits } ?: 0,
            minimumWeeklyCompatibleUnits = weeklyAudits.minOfOrNull { it.targetCompatibleMaterializedUnits } ?: 0,
            maximumWeeklyShortfall = shortfall,
            maximumWeeklyOverrun = overrun,
            fullyMaterializedWeekCount = fully,
            partiallyMaterializedWeekCount = partial,
            missingWeekCount = missing,
            totalAuthorizedUnits = authorized.sets.size * expectedWeeks.size,
            totalMaterializedUnits = materialized,
            totalCompatibleUnits = compatible,
            totalShortfallUnits = weeklyAudits.sumOf { it.shortfall })
    }
}
