package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.StimulusExperimentalReadinessStatus
import com.training.trackplanner.data.personalized.StimulusExperimentalTargetOutcomeStatus
import com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus
import com.training.trackplanner.data.personalized.StimulusProductionProgramSource
import com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity
import java.security.MessageDigest

/** Exact row identity for a replacement review; display names and stable keys alone are insufficient. */
data class ProgramReplacementRowIdentity(
    val localId: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val stableKey: String,
    val selectionRole: String
) {
    companion object {
        fun from(item: ProgramSkeletonItem) = ProgramReplacementRowIdentity(
            item.localId, item.weekNumber, item.dayOfWeek, item.orderIndex,
            item.exerciseStableKey, item.selectionRole
        )
    }
}

/** A B5 candidate to be checked on demand. It is explicitly not B6 execution authority. */
data class ProgramReplacementCandidateOption(
    val optionId: String,
    val targetId: String,
    val sourceRows: List<ProgramReplacementRowIdentity>,
    /** Exact keep rows, including prescription and schedule, retained only for the preview. */
    val sourceItems: List<ProgramSkeletonItem>,
    val sourceStableKey: String,
    val sourceSelectionRole: String,
    val candidateStableKey: String,
    val candidateSelectionRole: String,
    val reasonCodes: List<String>,
    val b5CandidateEligible: Boolean,
    val b6Validated: Boolean = false,
    val b7Validated: Boolean = false,
    val b8Validated: Boolean = false
) {
    init {
        require(optionId.isNotBlank() && targetId.isNotBlank())
        require(sourceRows.isNotEmpty())
        require(sourceRows.map { it.localId }.toSet() == sourceItems.map { it.localId }.toSet())
        require(sourceItems.all { item ->
            item.exerciseStableKey == sourceStableKey && item.selectionRole == sourceSelectionRole
        })
        require(sourceRows.all {
            it.stableKey == sourceStableKey && it.selectionRole == sourceSelectionRole
        })
        require(candidateStableKey != sourceStableKey) { "Role-only changes are not exercise replacements" }
        require(!b6Validated || b5CandidateEligible)
        require(!b7Validated || b6Validated)
        require(!b8Validated || b7Validated)
    }
}

enum class ProgramReplacementValidationStatus {
    NOT_VALIDATED,
    VALIDATED,
    REJECTED,
    STALE
}

/** B7 outcome delta for a target, calculated against the same CONTROL comparator on both plans. */
data class ProgramReplacementTargetImpact(
    val targetId: String,
    val keepStatus: StimulusExperimentalTargetOutcomeStatus?,
    val alternativeStatus: StimulusExperimentalTargetOutcomeStatus?,
    val keepWeeklyUnitsDistance: Double?,
    val alternativeWeeklyUnitsDistance: Double?,
    val keepWeeklySessionsDistance: Double?,
    val alternativeWeeklySessionsDistance: Double?
)

/** Exact B7 causal chain preserved with the validated pre-save choice. */
data class ProgramReplacementCausalEvidence(
    val optionId: String,
    val targetId: String,
    val displacedControlOwner: StimulusPrescriptionOwnerIdentity,
    val keepOwner: StimulusPrescriptionOwnerIdentity,
    val replacementOwner: StimulusPrescriptionOwnerIdentity,
    val controlRows: List<ProgramSkeletonItem>,
    val keepRows: List<ProgramSkeletonItem>,
    val replacementRows: List<ProgramSkeletonItem>
) {
    init {
        require(optionId.isNotBlank() && targetId.isNotBlank())
        require(displacedControlOwner != keepOwner && keepOwner != replacementOwner)
        require(controlRows.isNotEmpty() && keepRows.isNotEmpty() && replacementRows.isNotEmpty())
        require(controlRows.all { it.exerciseStableKey == displacedControlOwner.stableKey && it.selectionRole == displacedControlOwner.selectionRole })
        require(keepRows.all { it.exerciseStableKey == keepOwner.stableKey && it.selectionRole == keepOwner.selectionRole })
        require(replacementRows.all { it.exerciseStableKey == replacementOwner.stableKey && it.selectionRole == replacementOwner.selectionRole })
        require(controlRows.map { it.weekNumber }.toSet() == keepRows.map { it.weekNumber }.toSet())
        require(keepRows.map { it.weekNumber }.toSet() == replacementRows.map { it.weekNumber }.toSet())
    }
}

/** Exact whole-program validation result for one user-selected combination. */
data class ProgramReplacementCombinationValidation(
    val selectedOptionIds: Set<String>,
    val sourceDraftFingerprint: String,
    val resultingDraftFingerprint: String?,
    val status: ProgramReplacementValidationStatus,
    val b7Status: StimulusExperimentalReadinessStatus? = null,
    val b8Status: StimulusProductionCutoverAuthorityStatus? = null,
    val b9SelectedSource: StimulusProductionProgramSource? = null,
    val targetImpacts: List<ProgramReplacementTargetImpact> = emptyList(),
    val causalEvidence: List<ProgramReplacementCausalEvidence> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
    val proofId: String? = null
) {
    init {
        require(sourceDraftFingerprint.isNotBlank())
        require(status != ProgramReplacementValidationStatus.VALIDATED ||
            (!resultingDraftFingerprint.isNullOrBlank() &&
                b7Status == StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
                b8Status == StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER &&
                b9SelectedSource != null && b9SelectedSource != StimulusProductionProgramSource.CONTROL &&
                !proofId.isNullOrBlank()))
        require(causalEvidence.map(ProgramReplacementCausalEvidence::optionId).distinct().size == causalEvidence.size)
        require(status != ProgramReplacementValidationStatus.VALIDATED || selectedOptionIds.isEmpty() ||
            causalEvidence.map(ProgramReplacementCausalEvidence::optionId).toSet() == selectedOptionIds)
    }
}

data class ProgramReplacementReview(
    val sessionId: String,
    val sourceDraftFingerprint: String,
    val options: List<ProgramReplacementCandidateOption>,
    val validation: ProgramReplacementCombinationValidation? = null,
    /** Set only after the complete chosen combination passed B7 and B8. */
    val appliedOptionIds: Set<String> = emptySet()
) {
    init {
        require(sessionId.isNotBlank() && sourceDraftFingerprint.isNotBlank())
        require(options.map(ProgramReplacementCandidateOption::optionId).distinct().size == options.size)
        require(appliedOptionIds.all { id -> options.any { it.optionId == id } })
        require(validation == null || validation.sourceDraftFingerprint == sourceDraftFingerprint)
        require(validation == null || validation.selectedOptionIds.all { id -> options.any { it.optionId == id } })
        require(appliedOptionIds.isEmpty() || validation?.let {
            it.status == ProgramReplacementValidationStatus.VALIDATED && it.selectedOptionIds == appliedOptionIds
        } == true)
    }
}

class ProgramReplacementSaveRejectedException(val reasonCode: String) :
    IllegalStateException("PROGRAM_REPLACEMENT_SAVE_REJECTED:$reasonCode")

internal object ProgramReplacementSaveGate {
    fun requireSaveable(skeleton: GeneratedProgramSkeleton) {
        val review = skeleton.replacementReview ?: return
        val validation = review.validation
        if (validation?.status == ProgramReplacementValidationStatus.VALIDATED &&
            validation.selectedOptionIds.isNotEmpty() && review.appliedOptionIds != validation.selectedOptionIds
        ) throw ProgramReplacementSaveRejectedException("REPLACEMENT_NOT_EXPLICITLY_APPLIED")
        if (review.appliedOptionIds.isNotEmpty() &&
            (validation?.status != ProgramReplacementValidationStatus.VALIDATED ||
                validation.selectedOptionIds != review.appliedOptionIds ||
                validation.resultingDraftFingerprint != ProgramReplacementReviewFingerprint.create(skeleton))
        ) throw ProgramReplacementSaveRejectedException("REPLACEMENT_VALIDATION_STALE")
        if (review.appliedOptionIds.isNotEmpty()) {
            val evidenceById = validation?.causalEvidence.orEmpty().associateBy(ProgramReplacementCausalEvidence::optionId)
            val optionsById = review.options.associateBy(ProgramReplacementCandidateOption::optionId)
            val exactEvidenceMatches = review.appliedOptionIds.all { optionId ->
                val evidence = evidenceById[optionId] ?: return@all false
                val option = optionsById[optionId] ?: return@all false
                val weeks = option.sourceRows.mapTo(sortedSetOf(), ProgramReplacementRowIdentity::weekNumber)
                evidence.targetId == option.targetId &&
                    evidence.keepOwner.stableKey == option.sourceStableKey &&
                    evidence.keepOwner.selectionRole == option.sourceSelectionRole &&
                    evidence.replacementOwner.stableKey == option.candidateStableKey &&
                    evidence.replacementOwner.selectionRole == option.candidateSelectionRole &&
                    evidence.keepRows == option.sourceItems &&
                    evidence.keepRows.map { it.weekNumber }.toSet() == weeks &&
                    evidence.replacementRows == skeleton.items.filter {
                        it.exerciseStableKey == option.candidateStableKey && it.selectionRole == option.candidateSelectionRole && it.weekNumber in weeks
                    }.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId })) &&
                    evidence.replacementRows.map { it.weekNumber }.toSet() == weeks &&
                    evidence.controlRows.all { it.weekNumber in weeks }
            }
            if (!exactEvidenceMatches || evidenceById.keys != review.appliedOptionIds) {
                throw ProgramReplacementSaveRejectedException("REPLACEMENT_CAUSAL_EVIDENCE_MISMATCH")
            }
        }
        if (review.appliedOptionIds.isNotEmpty() &&
            validation?.causalEvidence?.map(ProgramReplacementCausalEvidence::optionId)?.toSet() != review.appliedOptionIds
        ) throw ProgramReplacementSaveRejectedException("REPLACEMENT_CAUSAL_EVIDENCE_MISSING")
    }

    fun requiresFreshValidation(skeleton: GeneratedProgramSkeleton): Boolean =
        skeleton.replacementReview?.appliedOptionIds?.isNotEmpty() == true
}

/** Restores the exact reviewed keep rows so a user can undo an applied preview and choose again. */
internal object ProgramReplacementReviewReverter {
    fun restoreKeepDraft(appliedDraft: GeneratedProgramSkeleton): GeneratedProgramSkeleton {
        ProgramReplacementSaveGate.requireSaveable(appliedDraft)
        val review = appliedDraft.replacementReview
            ?: throw ProgramReplacementSaveRejectedException("REPLACEMENT_REVIEW_MISSING")
        if (review.appliedOptionIds.isEmpty()) {
            throw ProgramReplacementSaveRejectedException("REPLACEMENT_NOT_APPLIED")
        }
        val validation = review.validation
            ?: throw ProgramReplacementSaveRejectedException("REPLACEMENT_VALIDATION_STALE")
        val evidence = validation.causalEvidence.associateBy(ProgramReplacementCausalEvidence::optionId)
        val selectedEvidence = review.appliedOptionIds.map { optionId ->
            evidence[optionId] ?: throw ProgramReplacementSaveRejectedException("REPLACEMENT_CAUSAL_EVIDENCE_MISSING")
        }
        val replacementRows = selectedEvidence.flatMap(ProgramReplacementCausalEvidence::replacementRows)
        val replacementIds = replacementRows.map(ProgramSkeletonItem::localId)
        if (replacementIds.distinct().size != replacementIds.size ||
            replacementRows.any { row -> appliedDraft.items.singleOrNull { it.localId == row.localId } != row }
        ) throw ProgramReplacementSaveRejectedException("REPLACEMENT_CAUSAL_EVIDENCE_MISMATCH")

        val removedIds = replacementIds.toSet()
        val restoredItems = appliedDraft.items.filterNot { it.localId in removedIds } +
            selectedEvidence.flatMap(ProgramReplacementCausalEvidence::keepRows)
        if (restoredItems.map(ProgramSkeletonItem::localId).distinct().size != restoredItems.size) {
            throw ProgramReplacementSaveRejectedException("REPLACEMENT_CAUSAL_EVIDENCE_MISMATCH")
        }
        val keepDraft = appliedDraft.copy(
            items = restoredItems,
            replacementReview = review.copy(
                validation = null,
                appliedOptionIds = emptySet(),
                options = review.options.map { it.copy(b6Validated = false, b7Validated = false, b8Validated = false) }
            )
        )
        if (ProgramReplacementReviewFingerprint.create(keepDraft) != review.sourceDraftFingerprint) {
            throw ProgramReplacementSaveRejectedException("REPLACEMENT_VALIDATION_STALE")
        }
        return keepDraft
    }
}

object ProgramReplacementReviewFingerprint {
    fun requestConstraints(request: ProgramSkeletonRequest): String =
        sha256(request.copy(name = "").toString())

    fun create(skeleton: GeneratedProgramSkeleton): String = sha256(buildString {
        // Program name is user-editable and does not alter the evidence for an exercise choice.
        append(skeleton.request.copy(name = ""))
        append('|').append(skeleton.durationDays)
        append('|').append(skeleton.periodizationType).append('|').append(skeleton.templateId)
        append('|').append(skeleton.representativeTemplate).append('|').append(skeleton.weekPlans)
        // Save-time B5/B6 revalidation creates a fresh audit decision id/time even when the
        // authorized plan is identical. Those two audit metadata values are not user-visible
        // program content; retain every other decision field in the stale-draft fingerprint.
        val decisionFingerprint = skeleton.personalizedDecision?.copy(
            decisionId = "",
            generatedAtEpochMillis = 0L
        )
        append('|').append(decisionFingerprint)
        append('|').append(skeleton.progressionSessions)
        append('|').append(skeleton.taskProtocolFrequencyOutcomes)
        append('|').append(skeleton.optimizationSummary)
        append('|').append(skeleton.warnings)
        append('|').append(skeleton.incumbentSourceSnapshotToken?.value)
        skeleton.weekDaySchedule.toSortedMap().forEach { (week, days) ->
            append("|schedule:").append(week).append(':').append(days.sorted().joinToString(","))
        }
        skeleton.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.localId }))
            .forEach { append("|row:").append(it) }
    })

    fun identityFingerprint(identity: ProgramReplacementRowIdentity): String = sha256(identity.toString())

    fun itemFingerprint(item: ProgramSkeletonItem): String = sha256(item.toString())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

/** Rejects a replacement rebuild that also silently changes unrelated physical owner-weeks. */
internal fun replacementLeavesUnselectedOwnerWeeksUnchanged(
    keep: GeneratedProgramSkeleton,
    alternative: GeneratedProgramSkeleton,
    options: List<ProgramReplacementCandidateOption>
): Boolean {
    data class OwnerWeek(val stableKey: String, val role: String, val week: Int)
    val allowed = options.flatMap { option ->
        val weeks = option.sourceRows.mapTo(linkedSetOf(), ProgramReplacementRowIdentity::weekNumber)
        weeks.flatMap { week ->
            listOf(
                OwnerWeek(option.sourceStableKey, option.sourceSelectionRole, week),
                OwnerWeek(option.candidateStableKey, option.candidateSelectionRole, week)
            )
        }
    }.toSet()
    fun unchangedRows(program: GeneratedProgramSkeleton) = program.items
        .filterNot(ProgramSkeletonItem::requiredTemplateAnchor)
        .filter { OwnerWeek(it.exerciseStableKey, it.selectionRole, it.weekNumber) !in allowed }
        .map { it.copy(localId = "") }
        .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex },
            { it.exerciseStableKey }, { it.selectionRole }, { it.toString() }))
    return unchangedRows(keep) == unchangedRows(alternative)
}
