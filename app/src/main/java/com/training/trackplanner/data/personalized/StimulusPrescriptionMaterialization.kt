package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality

enum class StimulusPrescriptionAuthorizationSource {
    CONTROL_EXISTING_DIRECT_IDENTITY,
    B5_SELECTION_PROBE
}

enum class StimulusPrescriptionAuthorizationStatus {
    AUTHORIZED_EXISTING_COMPATIBLE,
    AUTHORIZED_SAFE_REPAIR,
    NO_EXECUTABLE_AUTHORIZATION,
    AMBIGUOUS_OWNER,
    MODEL_UNAVAILABLE
}

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
    val executionAuthority: StimulusPrescriptionExecutionAuthority = when (quality) {
        TrainableQuality.HYPERTROPHY -> StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT
        TrainableQuality.STRENGTH -> StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
        else -> StimulusPrescriptionExecutionAuthority.UNRESOLVED
    },
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
)

data class StimulusPrescriptionAuthorizationPlan(
    val authorizations: List<StimulusPrescriptionAuthorization>,
    val shadowOnly: Boolean = true,
    val productionAuthority: Boolean = false
) {
    private val executableAuthorizations: List<StimulusPrescriptionAuthorization> = authorizations
        .mapNotNull { authorization ->
            val owner = authorization.owner ?: return@mapNotNull null
            val prescription = authorization.authorizedPrescription ?: return@mapNotNull null
            if (authorization.status !in setOf(
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
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

    fun provider(): ExactPrescriptionAuthorizationProvider = object : ExactPrescriptionAuthorizationProvider {
        override val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> = this@StimulusPrescriptionAuthorizationPlan.authorizedOwners
        override val authorizedPrescriptions: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription> = this@StimulusPrescriptionAuthorizationPlan.authorizedPrescriptions
        override val multiQualityResolutions: Map<StimulusPrescriptionOwnerIdentity, StimulusMultiQualityPrescriptionResolution> = this@StimulusPrescriptionAuthorizationPlan.multiQualityResolutions

        override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
            authorizedOwners[StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)]

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
    fun build(
        targetPlan: StimulusTargetPlan,
        selectionPlan: StimulusCandidateSelectionPlan,
        snapshot: PlanningHistorySnapshot,
        controlPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
    ): StimulusPrescriptionAuthorizationPlan {
        val realization = realizationEngine.build(targetPlan, selectionPlan, snapshot, controlPrescriptions)
        val authorizations = targetPlan.qualityTargets.map { target ->
            val targetId = "QUALITY:${target.quality.name}"
            val resolution = realization.resolutions.firstOrNull { it.targetId == targetId }
            authorizationFor(target, resolution)
        }
        return StimulusPrescriptionAuthorizationPlan(authorizations)
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
                listOf("HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE")
            )
        }
        if (target.quality != TrainableQuality.STRENGTH && target.quality != TrainableQuality.HYPERTROPHY) {
            return StimulusPrescriptionAuthorization(targetId, target.quality, resolution?.owner,
                resolution?.owner?.let { sourceFor(it.source) }, resolution?.currentPrescription ?: resolution?.probePrescription,
                resolution?.plannedCompatibility, null,
                StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
                listOf("CAPABILITY_PROXY_QUALITY_NON_PRESCRIPTIVE"))
        }
        if (resolution == null) return StimulusPrescriptionAuthorization(targetId, target.quality, null, null, null, null, null,
            StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE, listOf("STRENGTH_REALIZATION_RESOLUTION_UNAVAILABLE"))
        val source = resolution.owner?.let { sourceFor(it.source) }
        val input = resolution.currentPrescription ?: resolution.probePrescription
        return when {
            resolution.status == StimulusPrescriptionResolutionStatus.ALREADY_TARGET_COMPATIBLE &&
                resolution.owner != null && input != null && executableHypertrophyPrescription(target, input) -> StimulusPrescriptionAuthorization(targetId, target.quality,
                resolution.owner, source, input, resolution.plannedCompatibility, input,
                StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
                listOf(if (target.quality == TrainableQuality.HYPERTROPHY) "HYPERTROPHY_PRESCRIPTION_ALREADY_COMPATIBLE" else "EXISTING_COMPATIBLE_PRESCRIPTION"))
            resolution.status == StimulusPrescriptionResolutionStatus.SAFE_TARGET_COMPATIBLE_PRESCRIPTION_RESOLVED &&
                resolution.owner != null && input != null && resolution.proposedPrescription != null &&
                executableHypertrophySets(target, resolution.proposedPrescription.sets) -> {
                val proposal = resolution.proposedPrescription
                val authorized = input.copy(sets = proposal.sets)
                StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                    resolution.plannedCompatibility, authorized,
                    StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
                    listOf(if (target.quality == TrainableQuality.HYPERTROPHY) "HYPERTROPHY_SAFE_REPAIR_RESOLVED" else "SAFE_REPAIRED_PRESCRIPTION"))
            }
            resolution.status in setOf(
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_OWNER,
                StimulusPrescriptionResolutionStatus.AMBIGUOUS_EXISTING_REALIZATION_OWNER
            ) -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.AMBIGUOUS_OWNER,
                resolution.reasonCodes)
            else -> StimulusPrescriptionAuthorization(targetId, target.quality, resolution.owner, source, input,
                resolution.plannedCompatibility, null, StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                resolution.reasonCodes.ifEmpty { listOf("NO_EXECUTABLE_STRENGTH_AUTHORIZATION") })
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
        else -> StimulusPrescriptionAuthorizationSource.CONTROL_EXISTING_DIRECT_IDENTITY
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
                executionAuthority = authorization.executionAuthority,
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
        val weeklyAudits = expectedWeeks.map { week ->
            val rows = rowsByWeek[week].orEmpty()
            val materialized = rows.sumOf { it.setPrescriptions.size }
            val overrun = (materialized - authorized.sets.size).coerceAtLeast(0)
            val subsetValidation = validateAuthorizedWeeklySubset(rows, authorized, owner.stableKey, owner.selectionRole)
            val compatible = authorization.quality?.let { quality -> rows.sumOf { row ->
                val planned = PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource)
                if (plannedResolver.compatibility(quality, planned, snapshot, owner.stableKey).status == PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT) row.setPrescriptions.size else 0
            } } ?: 0
            val preserved = subsetValidation.valid
            val shortfall = (authorized.sets.size - materialized).coerceAtLeast(0)
            buildList {
                if (shortfall > 0) add(if (materialized == 0) "B6_AUTHORIZATION_MISSING_WEEK" else "B6_AUTHORIZATION_SHORTFALL")
                if (overrun > 0) add("B6_AUTHORIZATION_OVERRUN")
                if (!preserved) add("B6_PRESCRIPTION_NOT_PRESERVED")
                addAll(subsetValidation.reasonCodes)
                if (compatible < materialized) add("B6_TARGET_COMPATIBILITY_SHORTFALL")
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
            shortfall, overrun, preserved, state, reasons, authorization.executionAuthority,
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
