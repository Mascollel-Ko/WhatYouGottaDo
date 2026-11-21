package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgressionLinkMode
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionRule
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.resolvedWeekDaySchedule
import com.training.trackplanner.data.ProgramSkeletonItem
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class BCoachProductionStatus {
    BOUNDED_AUTO_APPLIED,
    USER_REVIEW_PROPOSED,
    HOLD_CALIBRATE,
    SHADOW_DIAGNOSTIC_ONLY,
    INPUT_CONFLICT
}

enum class BCoachObjectiveAxis {
    GOAL_DEFICIT,
    GOAL_EXCESS,
    PROGRESSION_FIT,
    RECOVERY_LOAD,
    RAMP_CHANGE,
    EXECUTION,
    PLAN_CHANGE,
    UNCERTAINTY
}

enum class BCoachValueStatus { READY, PARTIAL, UNKNOWN, NOT_APPLICABLE }

enum class BCoachConstraintStatus { PASS, FAIL, UNKNOWN }

data class BCoachObjectiveValue(
    val name: String,
    val value: Double?,
    val unit: String,
    val provenance: String,
    val missingReason: String? = null,
    val status: BCoachValueStatus = if (value == null) BCoachValueStatus.UNKNOWN
        else BCoachValueStatus.READY,
    val reasonCodes: List<String> = listOfNotNull(missingReason)
)

data class BCoachAxisEvaluation(
    val axis: BCoachObjectiveAxis,
    val values: List<BCoachObjectiveValue>
)

data class BCoachGoalWeight(
    val goal: BBalancedGoal,
    val explicitImportance: Int?,
    val normalizedImportance: Double?,
    val source: String,
    val effectiveImportance: Int = explicitImportance ?: BCoachV2Policy.DEFAULT_GOAL_IMPORTANCE
)

data class BCoachNeedEnvelope(
    val goal: BBalancedGoal,
    val block: Int,
    val week: Int,
    val lower: Double?,
    val upper: Double?,
    val source: String,
    val status: BCoachValueStatus,
    val reasonCodes: List<String>,
    val taskId: String = goal.name,
    val center: Double? = null,
    val exemptFromNeedScoring: Boolean = false
)

data class BCoachConstraintResult(
    val id: String,
    val status: BCoachConstraintStatus,
    val reasonCodes: List<String> = emptyList()
)

data class BCoachPlanAlternative(
    val status: BCoachProductionStatus,
    val label: String,
    val weeklySetAverage: Double,
    val changeCount: Int,
    val candidateId: String = label,
    val candidateFingerprint: String? = null,
    val recommended: Boolean = false,
    val axisScores: Map<BCoachObjectiveAxis, Double> = emptyMap(),
    val scenarioLosses: Map<String, Double> = emptyMap(),
    val robustRegret: Double = 0.0,
    val paretoFrontier: Boolean = false,
    val reasonCodes: List<String> = emptyList(),
    val prescriptionSummary: List<String> = emptyList(),
    /** Structured review uses these codes without parsing the legacy summary text. */
    val changeSummary: List<String> = emptyList()
)

data class BCoachMultiobjectiveDecision(
    val engineVersion: String,
    val policyVersion: String,
    val sourceOwner: String,
    val evidenceCutoff: String,
    val status: BCoachProductionStatus,
    val baselineFingerprint: String,
    val candidateFingerprint: String?,
    val proposalFingerprint: String?,
    val inputFingerprint: String,
    val historyFingerprint: String,
    val exerciseFingerprint: String,
    val linkedEvidenceFingerprint: String,
    val goalWeights: List<BCoachGoalWeight>,
    val needEnvelopes: List<BCoachNeedEnvelope>,
    val baselineObjectives: List<BCoachAxisEvaluation>,
    val candidateObjectives: List<BCoachAxisEvaluation>,
    val hardConstraints: List<BCoachConstraintResult>,
    val changes: List<String>,
    val alternatives: List<BCoachPlanAlternative>,
    val rejectedActionReasons: List<String>,
    val inputAudit: Map<String, String>,
    val unknowns: List<String>,
    val mostAdverseModeledScenario: String,
    val fallbackReason: String?,
    val coefficientSource: String = "LEGACY_REVIEW_GATE",
    val baselineAndCandidatesCanonicalHash: String = "",
    val effectiveSetBreakdown: List<String> = emptyList(),
    val paretoFrontierCandidateIds: List<String> = emptyList(),
    val recommendedCandidateId: String? = null,
    val loadInstructions: Map<String, String> = emptyMap(),
    val scenarioFlip: Boolean = false
)

data class BCoachProposalAcceptance(
    val accepted: Boolean,
    val skeleton: GeneratedProgramSkeleton?,
    val reason: String
)

class BCoachMultiobjectiveProposal internal constructor(
    val decision: BCoachMultiobjectiveDecision,
    private val candidatesById: Map<String, GeneratedProgramSkeleton>,
    private val loadInstructionsByCandidateId: Map<String, Map<String, String>>,
    private val recommendedCandidateId: String,
    internal val sportShadowReport: BCoachSportShadowReport? = null,
    private val shadowCandidatesById: Map<String, GeneratedProgramSkeleton> = emptyMap()
) {
    /** Read-only structured candidate access for the explicit review UI. */
    fun candidateForReview(candidateId: String): GeneratedProgramSkeleton? =
        candidatesById[candidateId]

    /** Read-only shadow candidate, never passed to accept() or persistence. */
    internal fun shadowCandidateForReview(candidateId: String): GeneratedProgramSkeleton? =
        shadowCandidatesById[candidateId]

    internal constructor(
        decision: BCoachMultiobjectiveDecision,
        proposedSkeleton: GeneratedProgramSkeleton
    ) : this(
        decision,
        mapOf("RECOMMENDED" to proposedSkeleton),
        mapOf("RECOMMENDED" to decision.loadInstructions),
        "RECOMMENDED"
    )

    /**
     * Explicit opt-in: apply ONE Gate F first-squat-set change to the already
     * fingerprint-approved B candidate. The original candidate is first
     * accepted using the normal stale-input and proposal checks; any missing
     * shadow evidence rejects without mutating a draft.
     */
    fun acceptWithSquatShadow(
        expectedProposalFingerprint: String,
        currentSkeleton: GeneratedProgramSkeleton,
        candidateId: String,
        comparison: BCoachSquatShadowProgramComparison
    ): BCoachProposalAcceptance {
        if (comparison.snapshotDate?.toString() != decision.evidenceCutoff) {
            return BCoachProposalAcceptance(false, null, "SQUAT_SHADOW_EVIDENCE_STALE")
        }
        val regular = accept(expectedProposalFingerprint, currentSkeleton, candidateId)
        if (!regular.accepted || regular.skeleton == null) return regular
        val review = BCoachSquatShadowProgramBridge.inspect(
            regular.skeleton, comparison
        ) ?: return BCoachProposalAcceptance(false, null, "SQUAT_SHADOW_NOT_ELIGIBLE")
        val changed = BCoachSquatShadowProgramBridge.apply(
            regular.skeleton, comparison, review
        ) ?: return BCoachProposalAcceptance(false, null, "SQUAT_SHADOW_PROPOSAL_CHANGED")
        val instructions = loadInstructionsByCandidateId[candidateId].orEmpty() +
            (BCoachV2LoadCandidateResolver.setId(review.localId, 1) to
                "USER_APPROVED_GATE_F_FIRST_SET:${review.approvedLoadKg}kg:${review.approvedReps}reps")
        val signed = BCoachMultiobjectiveProduction.withDecisionMetadata(
            changed,
            decision,
            "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW",
            acceptedStructureFingerprint =
                BCoachMultiobjectiveProduction.persistenceStructureFingerprint(changed),
            acceptedCandidateFingerprint = BCoachMultiobjectiveProduction.fingerprint(changed),
            chosenAlternativeId = candidateId,
            chosenLoadInstructions = instructions
        )
        return BCoachProposalAcceptance(true, signed, "SQUAT_SHADOW_FIRST_SET_APPROVED")
    }

    /** Revalidates the displayed fallback and the selected candidate before applying it. */
    fun accept(
        expectedProposalFingerprint: String,
        currentSkeleton: GeneratedProgramSkeleton,
        candidateId: String = recommendedCandidateId
    ): BCoachProposalAcceptance {
        val candidate = decision.candidateFingerprint
        val proposal = decision.proposalFingerprint
        if (candidate == null || proposal == null || expectedProposalFingerprint != proposal) {
            return BCoachProposalAcceptance(false, null, "PROPOSAL_FINGERPRINT_MISMATCH")
        }
        if (BCoachMultiobjectiveProduction.fingerprint(currentSkeleton) != decision.baselineFingerprint) {
            return BCoachProposalAcceptance(false, null, "STALE_INPUT_REGENERATE_REQUIRED")
        }
        val proposedSkeleton = candidatesById[candidateId]
            ?: return BCoachProposalAcceptance(false, null, "UNKNOWN_CANDIDATE")
        val selectedFingerprint = decision.alternatives.firstOrNull { it.candidateId == candidateId }
            ?.candidateFingerprint ?: candidate.takeIf { candidateId == recommendedCandidateId }
        if (selectedFingerprint == null ||
            BCoachMultiobjectiveProduction.fingerprint(proposedSkeleton) != selectedFingerprint) {
            return BCoachProposalAcceptance(false, null, "PROPOSAL_CONTENT_MISMATCH")
        }
        return BCoachProposalAcceptance(
            accepted = true,
            skeleton = BCoachMultiobjectiveProduction.withDecisionMetadata(
                proposedSkeleton,
                decision,
                "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW",
                acceptedStructureFingerprint = BCoachMultiobjectiveProduction.persistenceStructureFingerprint(
                    proposedSkeleton
                ),
                acceptedCandidateFingerprint = BCoachMultiobjectiveProduction.fingerprint(proposedSkeleton),
                chosenAlternativeId = candidateId,
                chosenLoadInstructions = loadInstructionsByCandidateId.getValue(candidateId)
            ),
            reason = "ACCEPTED"
        )
    }
}

internal object BCoachMultiobjectiveProduction {
    const val ENGINE_VERSION = BCoachV2Policy.ENGINE_VERSION
    const val POLICY_VERSION = BCoachV2Policy.POLICY_VERSION
    const val SOURCE_OWNER = BCoachV2Policy.SOURCE_OWNER

    data class Outcome(
        val skeleton: GeneratedProgramSkeleton,
        val decision: BCoachMultiobjectiveDecision,
        val proposal: BCoachMultiobjectiveProposal?
    )

    fun prepare(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton,
        muscleRelations: List<CanonicalMetadataRelation> = emptyList(),
        coreCatalog: CanonicalCoreCatalog = CanonicalCoreCatalog.EMPTY,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
        mainExerciseKeys: Set<String> = candidate.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey),
        recommendedImportance: Map<BBalancedGoal, Int> = emptyMap(),
        modelSnapshots: Map<String, BCoachV2LoadModelSnapshot> = emptyMap(),
        rirPolicy: RpeRirPolicy? = null,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList()
    ): Outcome {
        val result = BCoachV2GlobalSelector.select(
            request = request,
            history = history,
            badmintonHistory = badmintonHistory,
            exercises = exercises,
            baseline = baseline,
            jointCandidate = candidate,
            muscleRelations = muscleRelations,
            coreCatalog = coreCatalog,
            badmintonCatalog = badmintonCatalog,
            mainExerciseKeys = mainExerciseKeys,
            recommendedImportance = recommendedImportance,
            modelSnapshots = modelSnapshots,
            rirPolicy = rirPolicy
        )
        val proposal = result.recommendedCandidateId?.let { id ->
            BCoachMultiobjectiveProposal(
                result.decision, result.candidatesById, result.loadInstructionsByCandidateId, id,
                result.sportShadowReport, result.shadowCandidatesById
            )
        }
        return Outcome(result.fallback, result.decision, proposal)
    }

    internal fun changes(
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton
    ): List<String> {
        fun slots(plan: GeneratedProgramSkeleton) = plan.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val before = slots(baseline)
        val after = slots(candidate)
        return (before.keys + after.keys).distinct().sortedWith(
            compareBy<Triple<Int, Int, String>> { it.first }.thenBy { it.second }.thenBy { it.third }
        ).mapNotNull { slot ->
            val old = before[slot]
            val new = after[slot]
            when {
                old == null -> "ADD:${slot.first}/${slot.second}/${new!!.exerciseStableKey}:${new.setCount}sets"
                new == null -> "REMOVE:${slot.first}/${slot.second}/${old.exerciseStableKey}:${old.setCount}sets"
                ProgramSetPrescriptionResolver.resolve(old) != ProgramSetPrescriptionResolver.resolve(new) ->
                    "PRESCRIPTION:${slot.first}/${slot.second}/${old.exerciseStableKey}:" +
                        "${old.setCount}->${new.setCount}sets:" +
                        "${ProgramSetPrescriptionResolver.resolve(old).map { it.reps }}->" +
                        ProgramSetPrescriptionResolver.resolve(new).map { it.reps }
                else -> null
            }
        }
    }

    internal fun fingerprint(skeleton: GeneratedProgramSkeleton): String =
        canonicalSkeletonFingerprint(skeleton, includeLoad = true, includeDerivedProgression = false)

    /** Manual kg calibration is an independent user action after accepting roster/set/rep changes. */
    internal fun persistenceStructureFingerprint(skeleton: GeneratedProgramSkeleton): String =
        canonicalSkeletonFingerprint(skeleton, includeLoad = false, includeDerivedProgression = false)

    /** Compatibility for acceptances written before derived progression was excluded. */
    private fun legacyPersistenceStructureFingerprint(skeleton: GeneratedProgramSkeleton): String =
        canonicalSkeletonFingerprint(skeleton, includeLoad = false, includeDerivedProgression = true)

    private fun canonicalSkeletonFingerprint(
        skeleton: GeneratedProgramSkeleton,
        includeLoad: Boolean,
        includeDerivedProgression: Boolean
    ): String = sha256(buildString {
        canonicalField(if (includeLoad) skeleton.suggestedName else "USER_PROGRAM_NAME_ALLOWED")
        canonicalField(skeleton.durationDays)
        canonicalField(skeleton.periodizationType.name)
        val request = skeleton.request
        canonicalField(if (includeLoad) request.name else "USER_PROGRAM_NAME_ALLOWED")
        canonicalField(request.goal.name)
        canonicalField(request.weeklyTrainingDays)
        canonicalField(request.sessionMinutes)
        canonicalField(request.availableEquipment.sorted().joinToString(","))
        canonicalField(request.excludedExerciseText)
        canonicalField(request.badmintonTransferRatio)
        canonicalField(request.sportStrengthRatio)
        canonicalField(request.periodizationType.name)
        canonicalField(request.durationWeeks)
        canonicalField(request.badmintonEventProfile.name)
        canonicalField(request.varietyPreference.name)
        canonicalField(request.excludedExerciseStableKeys.sorted().joinToString(","))
        canonicalField(request.preferredExerciseStableKeys.sorted().joinToString(","))
        skeleton.weekPlans.sortedBy { it.weekIndex }.forEach { canonicalField(it) }
        val normalizedSchedule = if (includeDerivedProgression) skeleton.weekDaySchedule
            else skeleton.resolvedWeekDaySchedule()
        normalizedSchedule.toSortedMap().forEach { (week, days) ->
            canonicalField(week)
            canonicalField(days.sorted().joinToString(","))
        }
        skeleton.items.sortedWith(compareBy<ProgramSkeletonItem> { it.weekNumber }
            .thenBy { it.dayOfWeek }.thenBy { it.orderIndex }.thenBy { it.exerciseStableKey }
            .thenBy { it.localId })
            .forEach { item ->
                val normalized = item.copy(
                    weightKg = if (includeLoad) item.weightKg else 0.0,
                    weightSource = if (includeLoad) item.weightSource else "MANUAL_LOAD_ALLOWED",
                    primarySlotCapabilities = item.primarySlotCapabilities.sorted(),
                    secondarySlotCapabilities = item.secondarySlotCapabilities.sorted(),
                    weakSlotCapabilities = item.weakSlotCapabilities.sorted(),
                    slotCapabilityWarnings = item.slotCapabilityWarnings.sorted(),
                    progressionBinding = if (includeDerivedProgression) item.progressionBinding else null,
                    setPrescriptions = emptyList()
                )
                canonicalField(normalized)
                ProgramSetPrescriptionResolver.resolve(item).forEach { set ->
                    canonicalField(if (includeLoad) set else set.copy(
                        weightKg = 0.0,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
                        estimatedLoadKg = null,
                        loadSource = com.training.trackplanner.data.ProgramLoadSource.CALIBRATION_REQUIRED,
                        freshRir = null,
                        expectedRir = null,
                        relativeIntensity = null,
                        evidenceVersion = null
                    ))
                }
            }
        if (includeDerivedProgression) {
            skeleton.progressionSessions.sortedBy { it.key }.forEach { canonicalField(it) }
        } else {
            explicitProgressionIntents(skeleton).forEach { canonicalField(it) }
        }
    })

    /** UUIDs and AUTO inference are derived; explicit grouping/mode/rule choices are user structure. */
    private fun explicitProgressionIntents(skeleton: GeneratedProgramSkeleton): List<String> {
        val sessions = skeleton.progressionSessions.associateBy { it.key }
        fun identity(item: ProgramSkeletonItem) =
            "${item.weekNumber}/${item.dayOfWeek}/${item.orderIndex}/${item.exerciseStableKey}"
        return skeleton.items.mapNotNull { item ->
            val binding = item.progressionBinding ?: return@mapNotNull null
            val session = sessions[binding.sessionKey] ?: return@mapNotNull null
            val track = session.track
            val explicit = binding.linkMode != ProgressionLinkMode.AUTO ||
                track.roleOverride != ProgressionRole.AUTO || track.mode != ProgressionMode.APP ||
                track.rule != ProgressionRule()
            if (!explicit) return@mapNotNull null
            val members = skeleton.items.filter { it.progressionBinding?.sessionKey == binding.sessionKey }
                .map(::identity).sorted().joinToString(",")
            "${identity(item)}|link=${binding.linkMode}|members=$members|role=${track.roleOverride}|" +
                "mode=${track.mode}|rule=${track.rule}"
        }.distinct().sorted()
    }

    internal fun inputFingerprint(
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList()
    ): String =
        sha256(buildString {
            canonicalField(request.name)
            canonicalField(request.weeks)
            canonicalField(request.days)
            canonicalField(request.mode.name)
            canonicalField(request.asOf)
            canonicalField(request.focusStableKey)
            canonicalField(request.sessionMinutes)
            canonicalField(request.backupName)
            canonicalField(request.bGoalImportance)
            canonicalField(request.bMaxTrainingDays)
            canonicalField(request.allowedWeekdays?.sorted()?.joinToString(","))
            canonicalField(request.fixedWeekdays?.sorted()?.joinToString(","))
            canonicalField(request.availableEquipment?.map(String::trim)?.sorted()?.joinToString(","))
            canonicalField(request.sourceProgramStableKey)
            canonicalField(request.linkedDuePlannedSets)
            canonicalField(request.linkedConfirmedSets)
            canonicalField(request.linkedEvidenceApplicationId)
            canonicalField(request.bShadowPlateOneSideKg)
            canonicalField(request.bShadowDumbbellIncrementKg)
            canonicalField(request.bShadowMachineIncrementKg)
            canonicalField(request.planningStartDate)
            request.blockProgram?.let { program ->
                program.blocks.forEach { canonicalField(it) }
                canonicalField(program.recoveryDaysBetween.joinToString(","))
            } ?: canonicalField(null)
            canonicalField(historyFingerprint(history, badmintonHistory))
        })

    internal fun historyFingerprint(
        history: List<V22HistoryRow>,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList()
    ): String = sha256(buildString {
        canonicalField("RESISTANCE_HISTORY")
        history.sortedWith(compareBy<V22HistoryRow> { it.date }.thenBy { it.stableKey }
            .thenBy { it.setIndex }.thenBy { it.exerciseName })
            .forEach { canonicalField(it) }
        canonicalField("BADMINTON_HISTORY")
        badmintonHistory.sortedWith(compareBy(V22BadmintonHistorySession::date,
            V22BadmintonHistorySession::sessionStableKey)).forEach { canonicalField(it) }
    })

    internal fun exerciseFingerprint(
        relevantKeys: List<String>,
        exercises: Map<String, Exercise>
    ): String = sha256(buildString {
        relevantKeys.sorted().forEach { key ->
            canonicalField(key)
            canonicalField(exercises[key]?.toString() ?: "MISSING")
        }
    })

    internal fun linkedEvidenceFingerprint(request: V22GenerationRequest): String =
        linkedEvidenceFingerprint(
            request.sourceProgramStableKey,
            request.linkedEvidenceApplicationId,
            request.linkedDuePlannedSets,
            request.linkedConfirmedSets
        )

    internal fun linkedEvidenceFingerprint(
        sourceProgramStableKey: String?,
        applicationId: String?,
        duePlannedSets: Int?,
        confirmedSets: Int?
    ): String = sha256(buildString {
        canonicalField(sourceProgramStableKey)
        canonicalField(applicationId)
        canonicalField(duePlannedSets)
        canonicalField(confirmedSets)
    })

    internal fun proposalFingerprint(
        baselineFingerprint: String,
        candidateFingerprint: String,
        inputFingerprint: String,
        historyFingerprint: String,
        exerciseFingerprint: String,
        linkedEvidenceFingerprint: String,
        engineVersion: String = ENGINE_VERSION,
        policyVersion: String = POLICY_VERSION,
        sourceOwner: String = SOURCE_OWNER
    ): String = sha256(buildString {
        listOf(
            engineVersion, policyVersion, sourceOwner, baselineFingerprint, candidateFingerprint,
            inputFingerprint, historyFingerprint, exerciseFingerprint, linkedEvidenceFingerprint
        ).forEach { canonicalField(it) }
    })

    internal data class AcceptedPersistenceSnapshot(
        val cutoff: java.time.LocalDate,
        val sourceProgramStableKey: String?,
        val relevantExerciseKeys: List<String>,
        val historyFingerprint: String,
        val exerciseFingerprint: String,
        val linkedEvidenceFingerprint: String
    )

    internal fun acceptedPersistenceSnapshot(
        skeleton: GeneratedProgramSkeleton
    ): AcceptedPersistenceSnapshot? {
        val decision = runCatching {
            JSONObject(skeleton.planningMetadataJson ?: return null)
                .getJSONObject("bCoachMultiobjectiveDecision")
        }.getOrNull() ?: return null
        // v1 accepted plans may already be persisted and lack a replayable snapshot.
        // Only v2+ transient acceptances are eligible for the new atomic revalidation.
        if (decision.optInt("acceptanceSchemaVersion", 1) !in 2..3) return null
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") {
            return null
        }
        val audit = decision.optJSONObject("inputAudit") ?: return null
        val source = audit.optString("sourceProgramStableKey")
            .takeUnless { it.isBlank() || it == "UNSET" }
        return AcceptedPersistenceSnapshot(
            cutoff = runCatching { java.time.LocalDate.parse(decision.getString("evidenceCutoff")) }
                .getOrElse { throw IllegalStateException("B_COACH_ACCEPTANCE_INVALID_CUTOFF") },
            sourceProgramStableKey = source,
            relevantExerciseKeys = audit.optString("relevantExerciseKeys").split(',')
                .filter(String::isNotBlank).sorted(),
            historyFingerprint = decision.getString("historyFingerprint"),
            exerciseFingerprint = decision.getString("exerciseFingerprint"),
            linkedEvidenceFingerprint = decision.getString("linkedEvidenceFingerprint")
        )
    }

    internal fun validateAcceptedForPersistence(
        skeleton: GeneratedProgramSkeleton,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        currentLinkedEvidenceFingerprint: String,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList()
    ) {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: return) }
            .getOrElse { throw IllegalStateException("B_COACH_ACCEPTANCE_METADATA_INVALID") }
        val decision = root.optJSONObject("bCoachMultiobjectiveDecision") ?: return
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") return
        val baseline = decision.optString("baselineFingerprint")
        val candidate = decision.optString("candidateFingerprint")
        val input = decision.optString("inputFingerprint")
        val originalHistory = decision.optString("historyFingerprint")
        val originalExercise = decision.optString("exerciseFingerprint")
        val originalLinked = decision.optString("linkedEvidenceFingerprint")
        val proposal = decision.optString("proposalFingerprint")
        val structure = decision.optString("acceptedStructureFingerprint")
        val audit = decision.optJSONObject("inputAudit")
            ?: throw IllegalStateException("B_COACH_ACCEPTANCE_INPUT_AUDIT_MISSING")
        val relevantKeys = audit.optString("relevantExerciseKeys").split(',')
            .filter(String::isNotBlank).sorted()
        if (listOf(baseline, candidate, input, originalHistory, originalExercise, originalLinked,
                proposal, structure).any(String::isBlank)) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_FINGERPRINT_MISSING")
        }
        val expectedProposal = proposalFingerprint(
            baseline, candidate, input, originalHistory, originalExercise, originalLinked,
            decision.optString("engineVersion", ENGINE_VERSION),
            decision.optString("policyVersion", POLICY_VERSION),
            decision.optString("sourceOwner", SOURCE_OWNER)
        )
        if (proposal != expectedProposal) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_PROPOSAL_TAMPERED")
        }
        val acceptanceSchemaVersion = decision.optInt("acceptanceSchemaVersion", 1)
        val currentStructure = if (acceptanceSchemaVersion >= 3) {
            persistenceStructureFingerprint(skeleton)
        } else {
            legacyPersistenceStructureFingerprint(skeleton)
        }
        if (currentStructure != structure) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_STRUCTURE_STALE")
        }
        if (historyFingerprint(history, badmintonHistory) != originalHistory) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_HISTORY_STALE")
        }
        if (exerciseFingerprint(relevantKeys, exercises) != originalExercise) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_EXERCISE_METADATA_STALE")
        }
        if (currentLinkedEvidenceFingerprint != originalLinked) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_LINKED_EVIDENCE_STALE")
        }
        if (skeleton.items.flatMap(ProgramSetPrescriptionResolver::resolve).any { set ->
                !set.weightKg.isFinite() || set.weightKg < 0.0 ||
                    (set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && set.weightKg != 0.0)
            }) {
            throw IllegalStateException("B_COACH_ACCEPTANCE_LOAD_STATE_INVALID")
        }
    }

    internal fun markAcceptedAndPersisted(
        skeleton: GeneratedProgramSkeleton
    ): GeneratedProgramSkeleton {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: return skeleton) }
            .getOrElse { return skeleton }
        val decision = root.optJSONObject("bCoachMultiobjectiveDecision") ?: return skeleton
        if (decision.optString("proposalAcceptance") != "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW") {
            return skeleton
        }
        decision.put("proposalAcceptance", "ACCEPTED_AND_PERSISTED")
        val acceptedCandidate = decision.optString("acceptedCandidateFingerprint").takeIf { it.isNotBlank() }
        if (acceptedCandidate != null) {
            val adjusted = fingerprint(skeleton) != acceptedCandidate
            decision.put("postApprovalLoadAdjusted", adjusted)
            decision.put("scoresReflectCurrentPrescription", !adjusted)
            if (adjusted) decision.put("currentPrescriptionFingerprint", fingerprint(skeleton))
        }
        return skeleton.copy(planningMetadataJson = root.toString())
    }

    internal fun withDecisionMetadata(
        skeleton: GeneratedProgramSkeleton,
        decision: BCoachMultiobjectiveDecision,
        acceptance: String,
        acceptedStructureFingerprint: String? = null,
        acceptedCandidateFingerprint: String? = null,
        chosenAlternativeId: String? = decision.recommendedCandidateId,
        chosenLoadInstructions: Map<String, String> = decision.loadInstructions
    ): GeneratedProgramSkeleton {
        val root = runCatching { JSONObject(skeleton.planningMetadataJson ?: "{}") }
            .getOrElse { JSONObject() }
        val decisionJson = JSONObject()
            .put("engineVersion", decision.engineVersion)
            .put("policyVersion", decision.policyVersion)
            .put("coefficientSource", decision.coefficientSource)
            .put("acceptanceSchemaVersion", 3)
            .put("sourceOwner", decision.sourceOwner)
            .put("evidenceCutoff", decision.evidenceCutoff)
            .put("status", decision.status.name)
            .put("proposalAcceptance", acceptance)
            .put("baselineFingerprint", decision.baselineFingerprint)
            .put("candidateFingerprint", decision.candidateFingerprint ?: JSONObject.NULL)
            .put("proposalFingerprint", decision.proposalFingerprint ?: JSONObject.NULL)
            .put("acceptedStructureFingerprint", acceptedStructureFingerprint ?: JSONObject.NULL)
            .put("acceptedCandidateFingerprint", acceptedCandidateFingerprint ?: JSONObject.NULL)
            .put("baselineAndCandidatesCanonicalHash",
                decision.baselineAndCandidatesCanonicalHash.ifBlank { JSONObject.NULL })
            .put("chosenAlternativeId", chosenAlternativeId ?: JSONObject.NULL)
            .put("userApprovedAt", if (acceptance == "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW")
                System.currentTimeMillis() else JSONObject.NULL)
            .put("inputFingerprint", decision.inputFingerprint)
            .put("historyFingerprint", decision.historyFingerprint)
            .put("exerciseFingerprint", decision.exerciseFingerprint)
            .put("linkedEvidenceFingerprint", decision.linkedEvidenceFingerprint)
            .put("fallbackReason", decision.fallbackReason ?: JSONObject.NULL)
            .put("mostAdverseModeledScenario", decision.mostAdverseModeledScenario)
            .put("scenarioFlip", decision.scenarioFlip)
            .put("unknowns", JSONArray(decision.unknowns))
            .put("changes", JSONArray(decision.changes))
            .put("effectiveSetBreakdown", JSONArray(decision.effectiveSetBreakdown))
            .put("paretoFrontier", JSONArray(decision.paretoFrontierCandidateIds))
            .put("loadInstructions", JSONObject().also { loads ->
                chosenLoadInstructions.forEach { (key, value) -> loads.put(key, value) }
            })
            .put("rejectedActionReasons", JSONArray(decision.rejectedActionReasons))
            .put("goalWeights", JSONArray(decision.goalWeights.map { weight ->
                JSONObject().put("goal", weight.goal.name)
                    .put("explicitImportance", weight.explicitImportance ?: JSONObject.NULL)
                    .put("effectiveImportance", weight.effectiveImportance)
                    .put("normalizedImportance", weight.normalizedImportance ?: JSONObject.NULL)
                    .put("source", weight.source)
            }))
            .put("needEnvelopes", JSONArray(decision.needEnvelopes.map { need ->
                JSONObject().put("goal", need.goal.name).put("block", need.block)
                    .put("week", need.week).put("lower", need.lower ?: JSONObject.NULL)
                    .put("upper", need.upper ?: JSONObject.NULL).put("source", need.source)
                    .put("taskId", need.taskId).put("center", need.center ?: JSONObject.NULL)
                    .put("exemptFromNeedScoring", need.exemptFromNeedScoring)
                    .put("status", need.status.name).put("reasonCodes", JSONArray(need.reasonCodes))
            }))
            .put("hardConstraints", JSONArray(decision.hardConstraints.map { constraint ->
                JSONObject().put("id", constraint.id).put("status", constraint.status.name)
                    .put("reasonCodes", JSONArray(constraint.reasonCodes))
            }))
            .put("objectiveVector", axesJson(decision.candidateObjectives))
            .put("baselineObjectiveVector", axesJson(decision.baselineObjectives))
            .put("candidateObjectiveVector", axesJson(decision.candidateObjectives))
            .put("alternatives", JSONArray(decision.alternatives.map { alternative ->
                JSONObject().put("status", alternative.status.name).put("label", alternative.label)
                    .put("weeklySetAverage", alternative.weeklySetAverage)
                    .put("changeCount", alternative.changeCount)
                    .put("candidateId", alternative.candidateId)
                    .put("candidateFingerprint", alternative.candidateFingerprint ?: JSONObject.NULL)
                    .put("recommended", alternative.recommended)
                    .put("robustRegret", alternative.robustRegret)
                    .put("paretoFrontier", alternative.paretoFrontier)
                    .put("reasonCodes", JSONArray(alternative.reasonCodes))
                    .put("prescriptionSummary", JSONArray(alternative.prescriptionSummary))
                    .put("axisScores", JSONObject().also { scores ->
                        alternative.axisScores.forEach { (axis, value) -> scores.put(axis.name, value) }
                    })
                    .put("scenarioLosses", JSONObject().also { losses ->
                        alternative.scenarioLosses.forEach { (scenario, value) -> losses.put(scenario, value) }
                    })
            }))
            .put("inputAudit", JSONObject().also { audit ->
                decision.inputAudit.forEach { (key, value) -> audit.put(key, value) }
            })
        root.put("bCoachMultiobjectiveDecision", decisionJson)
        val warning = when {
            acceptance == "ACCEPTED_AFTER_EXPLICIT_USER_REVIEW" ->
                "사용자가 검토한 B-Coach 후보를 적용했습니다. 미확정 중량은 계속 사용자 보정이 필요합니다."
            decision.status == BCoachProductionStatus.USER_REVIEW_PROPOSED ->
                "B-Coach 다목적 후보는 승인 전에는 저장 계획에 반영되지 않습니다."
            decision.status == BCoachProductionStatus.HOLD_CALIBRATE ->
                "B-Coach는 현재 구성을 유지하며 미확정 중량을 사용자 보정으로 남겼습니다."
            else -> null
        }
        return skeleton.copy(
            planningMetadataJson = root.toString(),
            warnings = (skeleton.warnings.filterNot { it.startsWith("B-Coach 다목적 후보는") ||
                it.startsWith("B-Coach는 현재 구성을 유지하며") ||
                it.startsWith("사용자가 검토한 B-Coach 후보를") || it == BCoachV2Policy.DISCLAIMER } +
                listOf(BCoachV2Policy.DISCLAIMER) + listOfNotNull(warning)).distinct()
        )
    }

    private fun axesJson(axes: List<BCoachAxisEvaluation>): JSONArray = JSONArray(axes.map { axis ->
        JSONObject().put("axis", axis.axis.name).put("values", JSONArray(axis.values.map { value ->
            JSONObject().put("name", value.name).put("value", value.value ?: JSONObject.NULL)
                .put("unit", value.unit).put("provenance", value.provenance)
                .put("missingReason", value.missingReason ?: JSONObject.NULL)
                .put("status", value.status.name).put("reasonCodes", JSONArray(value.reasonCodes))
        }))
    })

    private fun StringBuilder.canonicalField(value: Any?) {
        val encoded = value?.toString() ?: "<NULL>"
        append(encoded.length).append(':').append(encoded).append('|')
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
