package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonItem

/**
 * Mechanical seam for an exact prescription authorized before feasibility. The provider is
 * keyed by the full B5 identity and, when quality-specific, the exact executable authority
 * identity. It may return a prefix when downstream capacity asks for fewer sets.
 */
fun interface ExactPrescriptionAuthorizationProvider {
    fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription?

    /** Quality-specific authority lookup; callers must never infer quality from prescription shape. */
    fun authorizedPrescriptionFor(
        item: PlannedExercise,
        quality: com.training.trackplanner.data.TrainableQuality,
        requestedSets: Int
    ): PlannedPrescription? = authorizedPrescriptions[
        StimulusPrescriptionAuthorityIdentity(item.stableKey, item.role, quality)
    ]?.let { prescription ->
        if (requestedSets < 0 || requestedSets > prescription.sets.size) null
        else prescription.copy(sets = prescription.sets.take(requestedSets).mapIndexed { index, set -> set.copy(setIndex = index + 1) })
    }

    val authorizedOwners: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
        get() = emptyMap()

    /** Lossless authority table. The owner-only compatibility map is intentionally optional. */
    val authorizedPrescriptions: Map<StimulusPrescriptionAuthorityIdentity, PlannedPrescription>
        get() = emptyMap()

    val multiQualityResolutions: Map<StimulusPrescriptionOwnerIdentity, StimulusMultiQualityPrescriptionResolution>
        get() = emptyMap()

    /** Owner-local B6 execution decisions. A conflict must not look like ordinary absence. */
    val ownerExecutionDispositions: Map<StimulusPrescriptionOwnerIdentity, StimulusPrescriptionOwnerExecutionDisposition>
        get() = emptyMap()

    /** Typed next-step guidance from B6 when an exact owner was considered but not executable. */
    val executionAuthorityResolutions: Map<StimulusPrescriptionOwnerIdentity, ExecutionAuthorityResolution>
        get() = emptyMap()

    /** Exact B5-selected Quality owners, including those for which B6 denied executable authority. */
    val b5SelectedQualityOwners: Set<StimulusPrescriptionOwnerIdentity>
        get() = emptySet()

    /** Actual-history prescriptions used only for PRESERVE_INCUMBENT_OWNER dispositions. */
    val canonicalPrescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
        get() = emptyMap()

    val conflictingOwners: Set<StimulusPrescriptionOwnerIdentity>
        get() = multiQualityResolutions.filterValues {
            it.status == StimulusMultiQualityPrescriptionResolutionStatus.CONFLICTING_MULTI_QUALITY_AUTHORITY
        }.keys
}

enum class ExecutionAuthorityResolutionStatus {
    READY,
    NEEDS_TARGET_RESOLUTION,
    NEEDS_OWNER_RESELECTION,
    NEEDS_REFERENCE_RESOLUTION,
    NEEDS_LOAD_INPUT,
    USER_INPUT_REQUIRED,
    NO_SUPPORTED_AUTHORITY
}

enum class ExecutionAuthorityReturnTarget {
    B4_TARGET_RESOLUTION,
    B5_OWNER_SELECTION,
    MATERIAL_DEMAND_CANDIDATE_SELECTION,
    HISTORY_REFERENCE_RESOLUTION,
    EXPLICIT_USER_INPUT,
    NONE
}

enum class ExecutionAuthorityResolutionReason {
    EXACT_AUTHORITY_AVAILABLE,
    EXACT_OWNER_NOT_SELECTED,
    B4_NUMERIC_AUTHORITY_MISSING,
    CANONICAL_REFERENCE_UNAVAILABLE,
    RESISTANCE_LOAD_UNAVAILABLE,
    HYPERTROPHY_NUMERIC_AUTHORITY_UNAVAILABLE,
    AMBIGUOUS_OWNER,
    OWNER_CANDIDATES_EXHAUSTED,
    NO_EXECUTABLE_AUTHORIZATION,
    UNSUPPORTED_PRESCRIPTION_AUTHORITY
}

/** Typed recovery path; it never grants prescription authority by itself. */
data class ExecutionAuthorityResolution(
    val status: ExecutionAuthorityResolutionStatus,
    val reason: ExecutionAuthorityResolutionReason,
    val returnTarget: ExecutionAuthorityReturnTarget,
    val originalOwner: StimulusPrescriptionOwnerIdentity? = null,
    val attemptedOwners: List<StimulusPrescriptionOwnerIdentity> = emptyList(),
    val finalOwner: StimulusPrescriptionOwnerIdentity? = null
)

/** Typed owner lookup keeps a known conflict distinct from an ordinary missing authority row. */
internal sealed interface ExactOwnerPrescriptionResolution {
    data class Authorized(val prescription: PlannedPrescription) : ExactOwnerPrescriptionResolution
    data class PreserveIncumbent(val prescription: PlannedPrescription) : ExactOwnerPrescriptionResolution
    object ExcludeConflictingAddition : ExactOwnerPrescriptionResolution
    object NoExecutableAuthority : ExactOwnerPrescriptionResolution
    object NoExactAuthority : ExactOwnerPrescriptionResolution
}

internal fun ExactPrescriptionAuthorizationProvider.resolveOwnerPrescription(
    item: PlannedExercise
): ExactOwnerPrescriptionResolution {
    val identity = StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)
    return when (ownerExecutionDispositions[identity] ?:
        if (identity in conflictingOwners) StimulusPrescriptionOwnerExecutionDisposition.EXCLUDE_CONFLICTING_ADDITION
        else if (identity in authorizedOwners) StimulusPrescriptionOwnerExecutionDisposition.EXECUTABLE_EXACT_AUTHORITY
        else StimulusPrescriptionOwnerExecutionDisposition.NO_EXECUTABLE_AUTHORITY) {
        StimulusPrescriptionOwnerExecutionDisposition.EXECUTABLE_EXACT_AUTHORITY ->
            authorizedOwners[identity]?.let(ExactOwnerPrescriptionResolution::Authorized)
                ?: prefixFor(item)?.let(ExactOwnerPrescriptionResolution::Authorized)
                ?: ExactOwnerPrescriptionResolution.NoExactAuthority
        StimulusPrescriptionOwnerExecutionDisposition.PRESERVE_INCUMBENT_OWNER ->
            canonicalPrescriptions[identity]?.let(ExactOwnerPrescriptionResolution::PreserveIncumbent)
                ?: ExactOwnerPrescriptionResolution.ExcludeConflictingAddition
        StimulusPrescriptionOwnerExecutionDisposition.EXCLUDE_CONFLICTING_ADDITION ->
            ExactOwnerPrescriptionResolution.ExcludeConflictingAddition
        StimulusPrescriptionOwnerExecutionDisposition.NO_EXECUTABLE_AUTHORITY ->
            ExactOwnerPrescriptionResolution.NoExactAuthority
    }
}

internal fun ExactPrescriptionAuthorizationProvider.authorizedPrescriptionFor(item: PlannedExercise): PlannedPrescription? =
    authorizedPrescriptionFor(item, item.targetSets)

/** Returns the exact owner-only authorized slice at a funded offset. Conflicting owners have no projection. */
internal fun ExactPrescriptionAuthorizationProvider.sliceFor(
    item: PlannedExercise,
    startOffset: Int,
    requestedSets: Int
): PlannedPrescription? {
    if (startOffset < 0 || requestedSets < 0) return null
    val identity = StimulusPrescriptionOwnerIdentity(item.stableKey, item.role)
    // A non-zero offset is safe only when the provider exposes the complete owner
    // authorization. A callback may legally return a truncated prefix, so it cannot
    // establish the funded suffix boundary and must fail closed for offset slices.
    val authorizedCandidate = authorizedOwners[identity]
        ?: if (startOffset == 0) authorizedPrescriptionFor(item, item.targetSets) else null
    if (authorizedCandidate == null) return null
    val authorized: PlannedPrescription = authorizedCandidate
    if (startOffset > authorized.sets.size || requestedSets > authorized.sets.size - startOffset) return null
    return authorized.copy(sets = authorized.sets.drop(startOffset).take(requestedSets)
        .mapIndexed { index, set -> set.copy(setIndex = index + 1) })
}

/** Quality-specific funded slice. No other quality's offset or set multiset may be borrowed. */
internal fun ExactPrescriptionAuthorizationProvider.sliceFor(
    item: PlannedExercise,
    quality: com.training.trackplanner.data.TrainableQuality,
    startOffset: Int,
    requestedSets: Int
): PlannedPrescription? {
    if (startOffset < 0 || requestedSets < 0) return null
    val authorized = authorizedPrescriptions[
        StimulusPrescriptionAuthorityIdentity(item.stableKey, item.role, quality)
    ] ?: return null
    if (startOffset > authorized.sets.size || requestedSets > authorized.sets.size - startOffset) return null
    return authorized.copy(sets = authorized.sets.drop(startOffset).take(requestedSets)
        .mapIndexed { index, set -> set.copy(setIndex = index + 1) })
}

internal fun ExactPrescriptionAuthorizationProvider.prefixFor(
    item: PlannedExercise,
    requestedSets: Int = item.targetSets
): PlannedPrescription? = sliceFor(item, 0, requestedSets)

internal fun ExactPrescriptionAuthorizationProvider.prefixFor(
    item: PlannedExercise,
    quality: com.training.trackplanner.data.TrainableQuality,
    requestedSets: Int = item.targetSets
): PlannedPrescription? = sliceFor(item, quality, 0, requestedSets)

internal data class AuthorizedPrescriptionSubsetValidation(
    val valid: Boolean,
    val reasonCodes: List<String> = emptyList()
)

/**
 * Validates a complete week's rows as a multiset subset of one exact authorization. Rows may be
 * split or moved between days, but no authorized set instance may be reused or replaced.
 */
internal fun validateAuthorizedWeeklySubset(
    rows: List<ProgramSkeletonItem>,
    authorized: PlannedPrescription,
    stableKey: String,
    selectionRole: String
): AuthorizedPrescriptionSubsetValidation {
    val reasons = linkedSetOf<String>()
    rows.groupBy(ProgramSkeletonItem::weekNumber).values.forEach { weekRows ->
        val remaining = authorized.sets.groupingBy { it.semanticKey() }.eachCount().toMutableMap()
        weekRows.forEach { row ->
            if (row.exerciseStableKey != stableKey || row.selectionRole != selectionRole) {
                reasons += "B6_UNAUTHORIZED_SET_IDENTITY"
            }
            if (row.restSeconds != authorized.restSeconds || row.weightSource != authorized.weightSource) {
                reasons += "B6_PRESCRIPTION_AUTHORITY_MISMATCH"
            }
            row.setPrescriptions.forEach { set ->
                val key = set.semanticKey()
                val available = remaining[key] ?: 0
                if (available <= 0) {
                    if (authorized.sets.any { it.semanticKey() == key }) {
                        reasons += "B6_AUTHORIZED_SET_REUSED"
                    } else if (authorized.sets.any {
                            it.reps == set.reps && it.weightKg == set.weightKg && it.seconds == set.seconds
                        }) {
                        reasons += "B6_EFFORT_TARGET_NOT_PRESERVED"
                    } else {
                        reasons += "B6_UNAUTHORIZED_SET_CONTENT"
                    }
                    reasons += "B6_AUTHORIZED_SET_MULTIPLICITY_EXCEEDED"
                } else {
                    remaining[key] = available - 1
                }
            }
        }
    }
    return AuthorizedPrescriptionSubsetValidation(reasons.isEmpty(), reasons.toList())
}

private fun com.training.trackplanner.data.ProgramSetPrescription.semanticKey(): String =
    "$reps|$weightKg|$seconds|${targetRpeMin?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()}"
