package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/** Compact evidence for one cutoff-relative window. Units are ledger set observations. */
data class StimulusWindowEvidence(
    val directUnits: Int = 0,
    val supportiveUnits: Int = 0,
    val directSessions: Int = 0,
    val supportiveSessions: Int = 0,
    val directTrainingDays: Int = 0,
    val supportiveTrainingDays: Int = 0,
    val excludedDirectByPrescriptionUnits: Int = 0,
    val excludedSupportiveByPrescriptionUnits: Int = 0,
    val classifiedSourceUnits: Int = 0,
    val unclassifiedSourceUnits: Int = 0
) {
    val eligibleUnits: Int get() = directUnits + supportiveUnits
}

data class StimulusExposureEvidence(
    val recent7d: StimulusWindowEvidence = StimulusWindowEvidence(),
    val current28d: StimulusWindowEvidence = StimulusWindowEvidence(),
    val prior28d: StimulusWindowEvidence = StimulusWindowEvidence(),
    val context56d: StimulusWindowEvidence = StimulusWindowEvidence(),
    val currentDirectActiveBins: Int = 0,
    val currentExposure: ExposureState = ExposureState.UNKNOWN,
    val confidence: PlanningConfidence = PlanningConfidence.LOW,
    val reasonCodes: List<String> = emptyList(),
    val coverage: StimulusEvidenceCoverage = StimulusEvidenceCoverage.UNAVAILABLE,
    val classifiedSourceUnits: Int = 0,
    val unclassifiedSourceUnits: Int = 0,
    val evidenceBasis: StimulusEvidenceBasis = StimulusEvidenceBasis.UNCLASSIFIED
)

data class CourtWindowEvidence(
    val sessions: Int = 0,
    val trainingDays: Int = 0,
    val durationMinutes: Double = 0.0,
    val practiceLoad: Double = 0.0
)

data class CourtContextEvidence(
    val recent7d: CourtWindowEvidence = CourtWindowEvidence(),
    val current28d: CourtWindowEvidence = CourtWindowEvidence(),
    val prior28d: CourtWindowEvidence = CourtWindowEvidence(),
    val context56d: CourtWindowEvidence = CourtWindowEvidence()
)

data class AthleteStimulusQualityNeed(
    val quality: TrainableQuality,
    val relevance: NeedRelevance,
    val exposure: StimulusExposureEvidence,
    val response: TrainingResponseState,
    val decision: TrainingNeedDecision,
    val confidence: PlanningConfidence,
    val reasonCodes: List<String> = emptyList(),
    val evidence: List<String> = emptyList()
)

data class AthleteStimulusTaskNeed(
    val task: String,
    val relevance: NeedRelevance,
    val exposure: StimulusExposureEvidence,
    val response: TrainingResponseState,
    val decision: TrainingNeedDecision,
    val confidence: PlanningConfidence,
    val sportContextLoad: Double = 0.0,
    val reasonCodes: List<String> = emptyList(),
    val evidence: List<String> = emptyList()
)

data class AthleteStimulusNeedProfile(
    val generatedAtCutoff: LocalDate,
    val qualityNeeds: List<AthleteStimulusQualityNeed>,
    val sportTaskNeeds: List<AthleteStimulusTaskNeed>,
    val courtContext: CourtContextEvidence,
    val executionModifiers: List<ExecutionModifierTrace> = emptyList(),
    val unresolved: List<String> = emptyList(),
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val reasonCodes: List<String> = emptyList(),
    val finalAudit: FinalStimulusNeedAuditResult? = null,
    /** Phase B2 shadow comparison; it never supplies planner authority. */
    val qualityDoseHistoryShadow: LedgerBackedQualityDoseHistory? = null,
    /** Phase B3 canonical decision portfolio; it is observation-only and separate from legacy. */
    val trainingDecisionPortfolioShadow: StimulusTrainingDecisionPortfolio? = null,
    /** Phase B4 canonical target envelope; it never enters the legacy target planner. */
    val stimulusTargetPlanShadow: StimulusTargetPlan? = null,
    /** Phase B6.1 typed prescription proposal; shadow-only and never generation authority. */
    val stimulusPrescriptionRealizationPlanShadow: StimulusPrescriptionRealizationPlan? = null
)

internal data class StimulusNeedEvidenceIndex(
    val qualityEvidence: Map<TrainableQuality, StimulusExposureEvidence>,
    val taskEvidence: Map<String, StimulusExposureEvidence>,
    val courtContext: CourtContextEvidence,
    val currentStrengthStableKeys: Set<String>,
    val available: Boolean,
    val reasonCodes: List<String>
)

/** Builds all quality and reviewed-task aggregates in one bounded ledger pass. */
internal class StimulusNeedEvidenceIndexBuilder {
    fun build(snapshot: PlanningHistorySnapshot): StimulusNeedEvidenceIndex = build(snapshot.stimulusExposureLedger, snapshot)

    internal fun build(ledger: StimulusExposureLedger, snapshot: PlanningHistorySnapshot): StimulusNeedEvidenceIndex {
        val reasonCodes = linkedSetOf<String>()
        val available = when {
            ledger.cutoff == null -> {
                reasonCodes += "LEDGER_UNAVAILABLE"
                false
            }
            ledger.cutoff != snapshot.cutoff -> {
                reasonCodes += "LEDGER_CUTOFF_MISMATCH"
                false
            }
            else -> true
        }
        val quality = TrainableQuality.entries.associateWith { ExposureAccumulator() }.toMutableMap()
        val reviewedTasks = CanonicalPerformanceTaskQualityRequirements.rows.map { it.task }.toSet()
        val tasks = reviewedTasks.associateWith { ExposureAccumulator() }.toMutableMap()
        val classification = Window.values().associateWith { ClassificationCounts() }
        val strengthStableKeys = linkedSetOf<String>()
        if (available) {
            // One fold over the source observations. A source set can enter several quality/task
            // views, but never more than once within a view.
            ledger.setObservations.forEach observationLoop@{ observation ->
                val age = ChronoUnit.DAYS.between(observation.source.date, snapshot.cutoff).toInt()
                if (age !in 0..55) return@observationLoop
                val profile = ledger.facetProfilesByStableKey[observation.facetProfileKey]
                if (profile == null) {
                    reasonCodes += "FACET_PROFILE_UNAVAILABLE"
                    return@observationLoop
                }
                profile.issues.forEach { reasonCodes += it.code }
                val relationsByQuality = profile.physicalQualities.groupBy(ExercisePhysicalQualityRelation::qualityId)
                if (relationsByQuality.isEmpty()) {
                    TrainableQuality.entries.forEach { qualityId ->
                        quality.getValue(qualityId).observeClassification(age, observation.classificationAuthority)
                    }
                }
                if (observation.activityKind in STRUCTURED_TASK_EVIDENCE_KINDS) {
                    classification.forEach { (window, counts) ->
                        if (age in window.range) counts.observe(observation.classificationAuthority)
                    }
                }
                val realizationClassified = observation.classificationAuthority != StimulusClassificationAuthority.UNCLASSIFIED
                relationsByQuality.forEach { (qualityId, relations) ->
                    val direct = relations.any { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                    val supportive = !direct && relations.any { it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY }
                    if (!direct && !supportive) return@forEach
                    val accumulator = quality.getValue(qualityId)
                    accumulator.observeClassification(age, observation.classificationAuthority)
                    val compatible = if (qualityId in PRESCRIPTION_GATED_QUALITIES) {
                        realizationClassified && realizedPrescriptionCompatible(qualityId, observation.realizedStimulusClassification)
                    } else {
                        capabilityProxyCompatible(observation.classificationAuthority)
                    }
                    accumulator.add(age, observation.source.date, observation.source.sessionStableKey,
                        direct = direct, supportive = supportive, compatible = compatible)
                    if (qualityId == TrainableQuality.STRENGTH && direct && compatible && age in 0..27) {
                        strengthStableKeys += observation.source.stableKey
                    }
                }
                if (observation.activityKind in STRUCTURED_TASK_EVIDENCE_KINDS) {
                    // A source observation contributes at most once to each objective view.
                    // Canonical metadata may contain duplicate/future-compatible relations, so
                    // resolve DIRECT precedence before crediting the accumulator.
                    profile.badmintonObjectives
                        .filter { it.objective.name in reviewedTasks }
                        .groupBy { it.objective.name }
                        .forEach { (objective, relations) ->
                    val direct = relations.any { it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT }
                            val supportive = !direct && relations.any { it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE }
                            if (direct || supportive) {
                                tasks.getValue(objective).add(
                                    age, observation.source.date, observation.source.sessionStableKey,
                                    direct = direct, supportive = supportive,
                                    compatible = capabilityProxyCompatible(observation.classificationAuthority)
                                )
                            }
                        }
                }
            }
        }
        tasks.values.forEach { it.applyClassification(classification) }
        val courts = CourtAccumulator()
        if (available) {
            // Court is its own context channel; it never enters a quality or task bucket.
            ledger.courtObservations.forEach { observation ->
                val age = ChronoUnit.DAYS.between(observation.source.date, snapshot.cutoff).toInt()
                if (age in 0..55) courts.add(age, observation.source.date, observation.source.sessionStableKey,
                    observation.durationMinutes, observation.practiceLoad)
            }
        }
        val qualityEvidence = quality.mapValues { (qualityId, accumulator) ->
            accumulator.evidence(snapshot, available, reasonCodes, quality = qualityId)
        }
        val taskEvidence = tasks.mapValues { (task, accumulator) ->
            accumulator.evidence(snapshot, available, reasonCodes, task = task)
        }
        return StimulusNeedEvidenceIndex(
            qualityEvidence = qualityEvidence,
            taskEvidence = taskEvidence,
            courtContext = courts.evidence(),
            currentStrengthStableKeys = strengthStableKeys,
            available = available,
            reasonCodes = reasonCodes.toList()
        )
    }

    private class ExposureAccumulator {
        private val windows = Window.values().associateWith { WindowBucket() }.toMutableMap()
        private val directBins = linkedSetOf<Int>()

        fun applyClassification(classification: Map<Window, ClassificationCounts>) {
            windows.forEach { (window, bucket) -> bucket.applyClassification(classification.getValue(window)) }
        }

        fun observeClassification(age: Int, authority: StimulusClassificationAuthority) {
            windows.forEach { (window, bucket) ->
                if (age in window.range) bucket.observeClassification(authority)
            }
        }

        fun add(age: Int, date: LocalDate, session: String, direct: Boolean, supportive: Boolean, compatible: Boolean) {
            windows.forEach { (window, bucket) ->
                if (age !in window.range) return@forEach
                bucket.add(date, session, direct, supportive, compatible)
            }
            if (direct && compatible && age in 0..27) directBins += age / 7
        }

        fun evidence(snapshot: PlanningHistorySnapshot, available: Boolean, reasons: Set<String>, quality: TrainableQuality? = null, task: String? = null): StimulusExposureEvidence {
            if (!available) return StimulusExposureEvidence(
                currentExposure = ExposureState.UNKNOWN,
                confidence = PlanningConfidence.LOW,
                reasonCodes = reasons.toList(),
                coverage = StimulusEvidenceCoverage.UNAVAILABLE,
                evidenceBasis = quality?.let(::evidenceBasisForQuality) ?: StimulusEvidenceBasis.CANONICAL_TASK_RELATION
            )
            val recent = windows.getValue(Window.RECENT).toEvidence()
            val current = windows.getValue(Window.CURRENT).toEvidence()
            val prior = windows.getValue(Window.PRIOR).toEvidence()
            val context = windows.getValue(Window.CONTEXT).toEvidence()
            val evidenceReasons = linkedSetOf<String>()
            if (current.directUnits == 0 && current.supportiveUnits > 0) evidenceReasons += "SUPPORTIVE_EVIDENCE_ONLY"
            if (current.excludedDirectByPrescriptionUnits > 0) {
                evidenceReasons += "DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE"
            }
            if (current.excludedSupportiveByPrescriptionUnits > 0) {
                evidenceReasons += "SUPPORTIVE_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE"
            }
            if (quality != null && quality !in PRESCRIPTION_GATED_QUALITIES) {
                evidenceReasons += "CANONICAL_CAPABILITY_PROXY_SOURCE_AUTHORITY_ONLY"
            }
            if (current.unclassifiedSourceUnits > 0) {
                evidenceReasons += "UNCLASSIFIED_STIMULUS_SOURCE_PRESENT"
                if (current.directUnits == 0 && current.supportiveUnits == 0) {
                    evidenceReasons += "ZERO_EXPOSURE_NOT_CONFIRMED_DUE_TO_UNCLASSIFIED_SOURCE"
                } else {
                    evidenceReasons += "PARTIAL_CLASSIFICATION_EXPOSURE_UNDERCOUNT_POSSIBLE"
                }
            } else if (current.classifiedSourceUnits > 0 && current.directUnits == 0 && current.supportiveUnits == 0) {
                evidenceReasons += "REVIEWED_CANONICAL_ZERO_EXPOSURE"
            }
            val currentClassificationComplete = current.unclassifiedSourceUnits == 0
            val priorClassificationComplete = prior.unclassifiedSourceUnits == 0
            val comparisonClassificationComplete = currentClassificationComplete && priorClassificationComplete
            if (!priorClassificationComplete) {
                evidenceReasons += "PRIOR_WINDOW_CLASSIFICATION_INCOMPLETE"
                evidenceReasons += "CURRENT_PRIOR_TREND_COMPARISON_WITHHELD"
                evidenceReasons += "PARTIAL_CLASSIFICATION_EXPOSURE_UNDERCOUNT_POSSIBLE"
            }
            if (!comparisonClassificationComplete && current.directUnits + current.supportiveUnits > 0) {
                evidenceReasons += "CURRENT_WINDOW_ONLY_EXPOSURE_STATE_USED"
            }
            val mergedReasons = (reasons + evidenceReasons).toList()
            val coverage = if (!comparisonClassificationComplete) StimulusEvidenceCoverage.PARTIAL else StimulusEvidenceCoverage.COMPLETE
            val basis = if (current.unclassifiedSourceUnits > 0 && current.directUnits == 0 && current.supportiveUnits == 0) {
                StimulusEvidenceBasis.UNCLASSIFIED
            } else quality?.let(::evidenceBasisForQuality) ?: StimulusEvidenceBasis.CANONICAL_TASK_RELATION
            val observedExposure = if (current.unclassifiedSourceUnits > 0 && current.directUnits == 0 && current.supportiveUnits == 0) {
                ExposureState.UNKNOWN
            } else if (!comparisonClassificationComplete) {
                currentOnlyExposureState(current.directUnits, current.directSessions)
            } else exposureState(current.directUnits, prior.directUnits, current.directSessions)
            return StimulusExposureEvidence(
                recent7d = recent,
                current28d = current,
                prior28d = prior,
                context56d = context,
                currentDirectActiveBins = directBins.size,
                currentExposure = observedExposure,
                confidence = if (coverage == StimulusEvidenceCoverage.PARTIAL) PlanningConfidence.LOW
                else confidence(current, prior, context, snapshot, taskPolicy = task != null),
                reasonCodes = mergedReasons,
                coverage = coverage,
                classifiedSourceUnits = current.classifiedSourceUnits,
                unclassifiedSourceUnits = current.unclassifiedSourceUnits,
                evidenceBasis = basis
            )
        }
    }

    private class WindowBucket {
        var directUnits = 0
        var supportiveUnits = 0
        var excludedDirect = 0
        var excludedSupportive = 0
        var classifiedSourceUnits = 0
        var unclassifiedSourceUnits = 0
        val directSessions = linkedSetOf<Pair<LocalDate, String>>()
        val supportiveSessions = linkedSetOf<Pair<LocalDate, String>>()
        val directDays = linkedSetOf<LocalDate>()
        val supportiveDays = linkedSetOf<LocalDate>()

        fun observeClassification(authority: StimulusClassificationAuthority) {
            if (authority == StimulusClassificationAuthority.UNCLASSIFIED) unclassifiedSourceUnits++
            else classifiedSourceUnits++
        }

        fun applyClassification(counts: ClassificationCounts) {
            classifiedSourceUnits = counts.classified
            unclassifiedSourceUnits = counts.unclassified
        }

        fun add(date: LocalDate, session: String, direct: Boolean, supportive: Boolean, compatible: Boolean) {
            when {
                direct && compatible -> {
                    directUnits++
                    directSessions += date to session
                    directDays += date
                }
                direct -> excludedDirect++
                supportive && compatible -> {
                    supportiveUnits++
                    supportiveSessions += date to session
                    supportiveDays += date
                }
                supportive -> excludedSupportive++
            }
        }

        fun toEvidence() = StimulusWindowEvidence(directUnits, supportiveUnits, directSessions.size,
            supportiveSessions.size, directDays.size, supportiveDays.size, excludedDirect, excludedSupportive,
            classifiedSourceUnits, unclassifiedSourceUnits)
    }

    private class ClassificationCounts {
        var classified = 0
        var unclassified = 0
        fun observe(authority: StimulusClassificationAuthority) {
            if (authority == StimulusClassificationAuthority.UNCLASSIFIED) unclassified++ else classified++
        }
    }

    private class CourtAccumulator {
        private val buckets = Window.values().associateWith { CourtBucket() }.toMutableMap()
        fun add(age: Int, date: LocalDate, session: String, duration: Double, load: Double) {
            buckets.forEach { (window, bucket) -> if (age in window.range) bucket.add(date, session, duration, load) }
        }
        fun evidence() = CourtContextEvidence(
            recent7d = buckets.getValue(Window.RECENT).evidence(),
            current28d = buckets.getValue(Window.CURRENT).evidence(),
            prior28d = buckets.getValue(Window.PRIOR).evidence(),
            context56d = buckets.getValue(Window.CONTEXT).evidence()
        )
    }

    private class CourtBucket {
        val sessions = linkedSetOf<Pair<LocalDate, String>>()
        val days = linkedSetOf<LocalDate>()
        var duration = 0.0
        var load = 0.0
        fun add(date: LocalDate, session: String, minutes: Double, practice: Double) {
            sessions += date to session
            days += date
            duration += minutes
            load += practice
        }
        fun evidence() = CourtWindowEvidence(sessions.size, days.size, duration, load)
    }

    private enum class Window(val range: IntRange) { RECENT(0..6), CURRENT(0..27), PRIOR(28..55), CONTEXT(0..55) }

    private companion object {
        val PRESCRIPTION_GATED_QUALITIES = setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
        val STRUCTURED_TASK_EVIDENCE_KINDS = setOf(
            PlannedActivityKind.RESISTANCE,
            PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
            PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        )

        fun exposureState(current: Int, prior: Int, sessions: Int): ExposureState = when {
            current == 0 -> ExposureState.ABSENT
            prior == 0 && sessions <= 1 -> ExposureState.LOW
            prior > 0 && current < prior * 0.5 -> ExposureState.LOW
            prior > 0 && current > prior * 1.5 -> ExposureState.HIGH
            sessions >= 2 || current >= 3 -> ExposureState.ESTABLISHED
            else -> ExposureState.LOW
        }

        fun currentOnlyExposureState(current: Int, sessions: Int): ExposureState = when {
            current == 0 -> ExposureState.ABSENT
            sessions >= 2 || current >= 3 -> ExposureState.ESTABLISHED
            else -> ExposureState.LOW
        }

        fun confidence(current: StimulusWindowEvidence, prior: StimulusWindowEvidence, context: StimulusWindowEvidence,
            snapshot: PlanningHistorySnapshot, taskPolicy: Boolean): PlanningConfidence = when {
            snapshot.allConfirmedSets.minOfOrNull(PlanningSetRecord::date)?.let { ChronoUnit.DAYS.between(it, snapshot.cutoff).toInt() + 1 }?.let { it < 28 } == true -> PlanningConfidence.LOW
            context.eligibleUnits < 2 -> PlanningConfidence.LOW
            taskPolicy && current.eligibleUnits >= 3 -> PlanningConfidence.MODERATE
            !taskPolicy && current.eligibleUnits >= 3 && prior.eligibleUnits > 0 -> PlanningConfidence.HIGH
            else -> PlanningConfidence.MODERATE
        }
    }
}

/** Shadow-only need decisions over the ledger index; it deliberately does not select exercises. */
internal class AthleteStimulusNeedEngine(
    private val indexBuilder: StimulusNeedEvidenceIndexBuilder = StimulusNeedEvidenceIndexBuilder()
) {
    fun analyze(snapshot: PlanningHistorySnapshot, state: AthletePlanningState): AthleteStimulusNeedProfile {
        val index = indexBuilder.build(snapshot)
        val qualityNeeds = TrainableQuality.entries.map { quality ->
            val exposure = index.qualityEvidence.getValue(quality)
            val relevance = qualityRelevance(quality, snapshot, state)
            val response = if (quality == TrainableQuality.STRENGTH) strengthResponse(index.currentStrengthStableKeys, snapshot)
            else TrainingResponseState.INSUFFICIENT_EVIDENCE
            // An unclassified source is not a reviewed realization and cannot establish a
            // numeric baseline, but it is still a bounded signal that keeps a high-priority
            // quality in the directional-development path. This preserves the B5 identity
            // seam while B6.1 remains closed to prescription resolution without reviewed
            // realization evidence.
            val decision = decide(relevance, exposure.currentExposure, response).let { candidate ->
                if (candidate == TrainingNeedDecision.UNKNOWN &&
                    exposure.unclassifiedSourceUnits > 0 &&
                    relevance !in setOf(NeedRelevance.UNKNOWN, NeedRelevance.NONE, NeedRelevance.LOW)
                ) TrainingNeedDecision.DEVELOP else candidate
            }
            AthleteStimulusQualityNeed(quality, relevance, exposure, response, decision, exposure.confidence,
                qualityReasons(quality, relevance, exposure, response, decision), listOf(
                    "directUnits=${exposure.current28d.directUnits}",
                    "supportiveUnits=${exposure.current28d.supportiveUnits}",
                    "currentDirectActiveBins=${exposure.currentDirectActiveBins}"
                ))
        }
        val taskNames = CanonicalPerformanceTaskQualityRequirements.rows.map { it.task }.distinct().sorted()
        val taskNeeds = taskNames.map { task ->
            val exposure = index.taskEvidence.getValue(task)
            val relevance = taskRelevance(state, task)
            val decision = when {
                relevance == NeedRelevance.UNKNOWN -> TrainingNeedDecision.UNKNOWN
                relevance in setOf(NeedRelevance.NONE, NeedRelevance.LOW) -> TrainingNeedDecision.NO_EXTRA_NEED
                exposure.currentExposure == ExposureState.UNKNOWN -> TrainingNeedDecision.UNKNOWN
                exposure.currentExposure in setOf(ExposureState.ABSENT, ExposureState.LOW) -> TrainingNeedDecision.DEVELOP
                else -> TrainingNeedDecision.MAINTAIN
            }
            AthleteStimulusTaskNeed(task, relevance, exposure, TrainingResponseState.INSUFFICIENT_EVIDENCE, decision,
                exposure.confidence, state.badmintonObjectiveRepresentations.firstOrNull { it.objective == task }?.currentWeighted28d ?: 0.0,
                buildList {
                    addAll(exposure.reasonCodes)
                    add("TASK_REQUIREMENT_ORDINAL_ONLY")
                }, listOf("directUnits=${exposure.current28d.directUnits}", "supportiveUnits=${exposure.current28d.supportiveUnits}"))
        }
        val unresolved = buildList {
            if (qualityNeeds.any { it.relevance == NeedRelevance.UNKNOWN } || taskNeeds.any { it.relevance == NeedRelevance.UNKNOWN }) add("UNKNOWN_USER_PRIORITY")
            qualityNeeds.filter { it.decision == TrainingNeedDecision.UNKNOWN }.forEach { add("QUALITY_${it.quality.name}_UNKNOWN") }
            taskNeeds.filter { it.decision == TrainingNeedDecision.UNKNOWN }.forEach { add("TASK_${it.task}_UNKNOWN") }
            if (!index.available) addAll(index.reasonCodes)
        }.distinct()
        return AthleteStimulusNeedProfile(
            generatedAtCutoff = snapshot.cutoff,
            qualityNeeds = qualityNeeds,
            sportTaskNeeds = taskNeeds,
            courtContext = index.courtContext,
            executionModifiers = executionModifiers(snapshot, state),
            unresolved = unresolved,
            reasonCodes = index.reasonCodes
        )
    }

    private fun qualityRelevance(quality: TrainableQuality, snapshot: PlanningHistorySnapshot, state: AthletePlanningState): NeedRelevance {
        val goal = snapshot.profilePrimaryGoal.trim().uppercase()
        val badminton = state.badmintonIntent == BadmintonPlanningIntent.ENABLED
        val taskDerived = taskDerivedQualityRelevance(quality, badminton)
        return when (quality) {
            TrainableQuality.STRENGTH -> when {
                goal == "STRENGTH_GAIN" || state.strengthIntent == StrengthIntent.STRENGTH_PRIORITY -> NeedRelevance.HIGH
                goal == "STRENGTH_MAINTENANCE" || state.strengthIntent == StrengthIntent.MIXED -> NeedRelevance.MODERATE
                goal.isBlank() && state.strengthIntent == StrengthIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                else -> taskDerived ?: NeedRelevance.LOW
            }
            TrainableQuality.HYPERTROPHY -> when {
                goal == "HYPERTROPHY_PHYSIQUE" || state.strengthIntent == StrengthIntent.HYPERTROPHY_PRIORITY -> NeedRelevance.HIGH
                goal.isBlank() && state.strengthIntent == StrengthIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC -> when {
                goal == "HYPERTROPHY_PHYSIQUE" && !badminton -> NeedRelevance.NONE
                badminton -> taskDerived ?: NeedRelevance.LOW
                goal == "BADMINTON_PERFORMANCE" && state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                goal.isBlank() -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.MUSCULAR_ENDURANCE, TrainableQuality.CARDIORESPIRATORY_FITNESS -> when {
                goal == "WEIGHT_MANAGEMENT" -> NeedRelevance.HIGH
                badminton -> NeedRelevance.LOW
                goal == "BADMINTON_PERFORMANCE" && state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                goal.isBlank() -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.MOBILITY_ROM -> when {
                goal == "RECOVERY_INJURY_PREVENTION" -> NeedRelevance.HIGH
                goal.isBlank() -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
        }
    }

    private fun taskRelevance(state: AthletePlanningState, task: String): NeedRelevance = when (state.badmintonIntent) {
        BadmintonPlanningIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
        BadmintonPlanningIntent.DISABLED -> NeedRelevance.NONE
        BadmintonPlanningIntent.ENABLED -> if (CanonicalPerformanceTaskQualityRequirements.forTask(task).isNotEmpty()) NeedRelevance.MODERATE else NeedRelevance.UNKNOWN
    }

    private fun taskDerivedQualityRelevance(quality: TrainableQuality, badminton: Boolean): NeedRelevance? {
        if (!badminton) return null
        val roles = CanonicalPerformanceTaskQualityRequirements.rows.filter { it.quality == quality }.map { it.role }
        return when {
            RequirementRole.PRIMARY_REQUIREMENT in roles -> NeedRelevance.MODERATE
            RequirementRole.SUPPORTIVE_REQUIREMENT in roles -> NeedRelevance.LOW
            else -> null
        }
    }

    private fun strengthResponse(keys: Set<String>, snapshot: PlanningHistorySnapshot): TrainingResponseState {
        val changes = keys.mapNotNull { key -> snapshot.canonicalStrengthSignals[key]
            ?.takeIf { it.observationCount >= 2 && it.posteriorChangePercent?.isFinite() == true }?.posteriorChangePercent }
        if (changes.isEmpty()) return TrainingResponseState.INSUFFICIENT_EVIDENCE
        val sorted = changes.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return when {
            median > 2.0 -> TrainingResponseState.POSITIVE_RESPONSE
            median < -5.0 -> TrainingResponseState.NEGATIVE_RESPONSE
            else -> TrainingResponseState.STABLE_RESPONSE
        }
    }

    private fun decide(relevance: NeedRelevance, exposure: ExposureState, response: TrainingResponseState): TrainingNeedDecision = when {
        relevance == NeedRelevance.UNKNOWN -> TrainingNeedDecision.UNKNOWN
        relevance in setOf(NeedRelevance.NONE, NeedRelevance.LOW) -> TrainingNeedDecision.NO_EXTRA_NEED
        exposure == ExposureState.UNKNOWN -> TrainingNeedDecision.UNKNOWN
        exposure in setOf(ExposureState.ABSENT, ExposureState.LOW) && response in setOf(TrainingResponseState.INSUFFICIENT_EVIDENCE, TrainingResponseState.STABLE_RESPONSE) -> TrainingNeedDecision.DEVELOP
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) && response == TrainingResponseState.POSITIVE_RESPONSE -> TrainingNeedDecision.MAINTAIN_OR_PROGRESS
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) && response == TrainingResponseState.NEGATIVE_RESPONSE -> TrainingNeedDecision.REDUCE
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) -> TrainingNeedDecision.MAINTAIN
        response == TrainingResponseState.NEGATIVE_RESPONSE -> TrainingNeedDecision.REDISTRIBUTE
        else -> TrainingNeedDecision.DEVELOP
    }

    private fun qualityReasons(quality: TrainableQuality, relevance: NeedRelevance, exposure: StimulusExposureEvidence,
        response: TrainingResponseState, decision: TrainingNeedDecision): List<String> = buildList {
        add("GOAL_RELEVANCE_${relevance.name}")
        add("EXPOSURE_${exposure.currentExposure.name}")
        add("RESPONSE_${response.name}")
        add("DECISION_MATRIX_${decision.name}")
        addAll(exposure.reasonCodes)
        if (quality != TrainableQuality.STRENGTH) add("CANONICAL_OUTCOME_AUTHORITY_UNAVAILABLE")
        if (quality in setOf(TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC)) add("PERFORMANCE_TEST_REQUIRED_FOR_RESPONSE")
    }

    private fun executionModifiers(snapshot: PlanningHistorySnapshot, state: AthletePlanningState): List<ExecutionModifierTrace> = buildList {
        val restricted = snapshot.recoverySignals.tissueRestrictedStableKeys
        if (restricted.isNotEmpty()) add(ExecutionModifierTrace("TISSUE", restricted.sorted(), ExecutionModifier.SUBSTITUTE, listOf("TISSUE_RESTRICTION_EXECUTION_ONLY")))
        if (snapshot.recoverySignals.isConstrained) add(ExecutionModifierTrace("RECOVERY", emptyList(), ExecutionModifier.HOLD, listOf("RECOVERY_MODIFIER_NOT_NEED_SUPPRESSION")))
        if (state.courtDeviation > 0.0 && state.lowerNegativeEvidence > 0.0 && snapshot.recoverySignals.isConstrained) {
            add(ExecutionModifierTrace("BADMINTON", emptyList(), ExecutionModifier.REDUCE,
                listOf("COURT_DEVIATION_PLUS_LOWER_NEGATIVE_EVIDENCE_PLUS_RECOVERY_CONSTRAINT")))
        }
    }
}

internal fun stimulusPrescriptionCompatible(quality: TrainableQuality, realized: RealizedStimulusClass): Boolean = when (quality) {
    TrainableQuality.STRENGTH -> realized == RealizedStimulusClass.STRENGTH_LIKE
    TrainableQuality.HYPERTROPHY -> realized == RealizedStimulusClass.HYPERTROPHY_LIKE
    else -> true
}

/** Planned prescription shape only; it is never historical realized-stimulus authority. */
internal fun prescriptionShapeCompatible(quality: TrainableQuality, reps: Int): Boolean = when (quality) {
    TrainableQuality.STRENGTH -> reps in 1..6
    TrainableQuality.HYPERTROPHY -> reps in 7..15
    else -> true
}

/** B6 reviewed realization compatibility for the two qualities with a realization gate. */
internal fun realizedPrescriptionCompatible(quality: TrainableQuality, realized: RealizedStimulusClassification): Boolean = when (quality) {
    TrainableQuality.STRENGTH -> realized.isRealized && realized.kind == RealizedStimulusKind.STRENGTH_LIKE
    TrainableQuality.HYPERTROPHY -> realized.isRealized && realized.kind == RealizedStimulusKind.HYPERTROPHY_LIKE
    else -> false
}

/** Capability proxies use canonical source identity and relation authority only. */
internal fun capabilityProxyCompatible(authority: StimulusClassificationAuthority): Boolean =
    authority == StimulusClassificationAuthority.REVIEWED_CANONICAL

internal fun AthleteStimulusNeedProfile.toCompactJson(): JSONObject = JSONObject()
    .put("generatedAtCutoff", generatedAtCutoff.toString())
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("stimulusPrescriptionRealizationPlanShadow", stimulusPrescriptionRealizationPlanShadow?.toCompactJson())
    .put("unresolved", JSONArray(unresolved))
    .put("reasonCodes", JSONArray(reasonCodes))
    .put("qualityNeeds", JSONArray(qualityNeeds.map { need -> JSONObject()
        .put("quality", need.quality.name).put("relevance", need.relevance.name)
        .put("currentExposure", need.exposure.currentExposure.name).put("response", need.response.name)
        .put("decision", need.decision.name).put("confidence", need.confidence.name)
        .put("recent7d", need.exposure.recent7d.directUnits + need.exposure.recent7d.supportiveUnits)
        .put("current28dDirectUnits", need.exposure.current28d.directUnits)
        .put("current28dSupportiveUnits", need.exposure.current28d.supportiveUnits)
        .put("prior28dDirectUnits", need.exposure.prior28d.directUnits)
        .put("coverage", need.exposure.coverage.name)
        .put("classifiedSourceUnits", need.exposure.classifiedSourceUnits)
        .put("unclassifiedSourceUnits", need.exposure.unclassifiedSourceUnits)
        .put("evidenceBasis", need.exposure.evidenceBasis.name)
        .put("activeBins", need.exposure.currentDirectActiveBins)
        .put("reasonCodes", JSONArray(need.reasonCodes))
    }))
    .put("sportTaskNeeds", JSONArray(sportTaskNeeds.map { need -> JSONObject()
        .put("task", need.task).put("relevance", need.relevance.name)
        .put("currentExposure", need.exposure.currentExposure.name).put("decision", need.decision.name)
        .put("confidence", need.confidence.name)
        .put("directUnits", need.exposure.current28d.directUnits)
        .put("supportiveUnits", need.exposure.current28d.supportiveUnits)
        .put("coverage", need.exposure.coverage.name)
        .put("classifiedSourceUnits", need.exposure.classifiedSourceUnits)
        .put("unclassifiedSourceUnits", need.exposure.unclassifiedSourceUnits)
        .put("evidenceBasis", need.exposure.evidenceBasis.name)
        .put("sportContextLoad", need.sportContextLoad)
        .put("reasonCodes", JSONArray(need.reasonCodes))
    }))
    .put("courtContext", JSONObject()
        .put("currentSessions", courtContext.current28d.sessions)
        .put("currentTrainingDays", courtContext.current28d.trainingDays)
        .put("currentDurationMinutes", courtContext.current28d.durationMinutes)
        .put("currentPracticeLoad", courtContext.current28d.practiceLoad))
    .put("qualityDoseHistoryShadow", qualityDoseHistoryShadow?.toCompactJson())
    .put("trainingDecisionPortfolioShadow", trainingDecisionPortfolioShadow?.toCompactJson())
    .put("stimulusTargetPlanShadow", stimulusTargetPlanShadow?.toCompactJson())
    .put("finalAudit", finalAudit?.toCompactJson())
