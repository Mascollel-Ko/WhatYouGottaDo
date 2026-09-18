package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** User intent is interpreted by the app; these are never user-selectable quality deficits. */
enum class NeedRelevance { HIGH, MODERATE, LOW, NONE, UNKNOWN }

enum class ExposureState { ABSENT, LOW, ESTABLISHED, HIGH, UNKNOWN }

enum class TrainingResponseState {
    POSITIVE_RESPONSE,
    STABLE_RESPONSE,
    NEGATIVE_RESPONSE,
    INSUFFICIENT_EVIDENCE
}

/** A composite state is explicit so positive high exposure is not mislabelled as overload. */
enum class TrainingNeedDecision {
    DEVELOP,
    MAINTAIN,
    MAINTAIN_OR_PROGRESS,
    PROGRESS,
    REDISTRIBUTE,
    REDUCE,
    NO_EXTRA_NEED,
    UNKNOWN
}

enum class RealizedStimulusClass {
    STRENGTH_LIKE,
    HYPERTROPHY_LIKE,
    AMBIGUOUS_REALIZED_STIMULUS
}

enum class RequirementRole { PRIMARY_REQUIREMENT, SUPPORTIVE_REQUIREMENT }

enum class ExecutionModifier { NONE, HOLD, SUBSTITUTE, REDUCE }

data class QualityExposureSummary(
    val quality: TrainableQuality,
    val recent7dBouts: Int,
    val current28dBouts: Int,
    val previous28dBouts: Int,
    val context56dBouts: Int,
    val directSessions: Int,
    val directBouts: Int,
    val supportiveSessions: Int,
    val supportiveBouts: Int,
    val strengthLikeBouts: Int,
    val hypertrophyLikeBouts: Int,
    val ambiguousBouts: Int,
    val currentExposure: ExposureState,
    val confidence: PlanningConfidence,
    val evidence: List<String>
)

data class QualityNeed(
    val quality: TrainableQuality,
    val relevance: NeedRelevance,
    val currentExposure: ExposureState,
    val response: TrainingResponseState,
    val decision: TrainingNeedDecision,
    val confidence: PlanningConfidence,
    val reasonCodes: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val exposure: QualityExposureSummary = QualityExposureSummary(
        quality = quality,
        recent7dBouts = 0,
        current28dBouts = 0,
        previous28dBouts = 0,
        context56dBouts = 0,
        directSessions = 0,
        directBouts = 0,
        supportiveSessions = 0,
        supportiveBouts = 0,
        strengthLikeBouts = 0,
        hypertrophyLikeBouts = 0,
        ambiguousBouts = 0,
        currentExposure = ExposureState.UNKNOWN,
        confidence = PlanningConfidence.LOW
    )
)

data class SportTaskNeed(
    val task: String,
    val relevance: NeedRelevance,
    val currentExposure: ExposureState,
    val response: TrainingResponseState,
    val decision: TrainingNeedDecision,
    val confidence: PlanningConfidence,
    val structuredDirectBouts: Int,
    val structuredSupportiveBouts: Int,
    val sportContextLoad: Double,
    val reasonCodes: List<String> = emptyList(),
    val evidence: List<String> = emptyList()
)

data class ExecutionModifierTrace(
    val domain: String,
    val stableKeys: List<String>,
    val modifier: ExecutionModifier,
    val reasonCodes: List<String>
)

data class AthleteNeedsEvidenceSummary(
    val recentWindowDays: Int = 7,
    val currentWindowDays: Int = 28,
    val previousWindowDays: Int = 28,
    val contextWindowDays: Int = 56,
    val historyDays: Int = 0,
    val source: String = "PLANNING_HISTORY_SNAPSHOT",
    val notes: List<String> = emptyList()
)

data class AthleteNeedsProfile(
    val generatedAtCutoff: LocalDate,
    val qualityNeeds: List<QualityNeed>,
    val sportTaskNeeds: List<SportTaskNeed>,
    val maintenanceDomains: List<String>,
    val unresolved: List<String>,
    val evidenceSummary: AthleteNeedsEvidenceSummary,
    val executionModifiers: List<ExecutionModifierTrace> = emptyList(),
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

data class PerformanceTaskQualityRequirement(
    val task: String,
    val quality: TrainableQuality,
    val role: RequirementRole,
    val provenance: String
)

/**
 * Ordinal task requirements are deliberately small. They describe relevance only; they do not
 * assign volume, load, or a biological weight to any quality.
 */
object CanonicalPerformanceTaskQualityRequirements {
    val rows: List<PerformanceTaskQualityRequirement> = listOf(
        row("ACCELERATION", TrainableQuality.STRENGTH, RequirementRole.PRIMARY_REQUIREMENT),
        row("ACCELERATION", TrainableQuality.POWER, RequirementRole.PRIMARY_REQUIREMENT),
        row("ACCELERATION", TrainableQuality.RAPID_FORCE_PRODUCTION, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("DECELERATION", TrainableQuality.STRENGTH, RequirementRole.PRIMARY_REQUIREMENT),
        row("DECELERATION", TrainableQuality.RAPID_FORCE_PRODUCTION, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("DECELERATION", TrainableQuality.REACTIVE_STRENGTH_SSC, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("JUMP_LANDING", TrainableQuality.POWER, RequirementRole.PRIMARY_REQUIREMENT),
        row("JUMP_LANDING", TrainableQuality.REACTIVE_STRENGTH_SSC, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("FOOTWORK", TrainableQuality.POWER, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("REACTION", TrainableQuality.RAPID_FORCE_PRODUCTION, RequirementRole.SUPPORTIVE_REQUIREMENT),
        row("LUNGE_REACH", TrainableQuality.STRENGTH, RequirementRole.PRIMARY_REQUIREMENT),
        row("LUNGE_REACH", TrainableQuality.REACTIVE_STRENGTH_SSC, RequirementRole.SUPPORTIVE_REQUIREMENT)
    )

    private fun row(task: String, quality: TrainableQuality, role: RequirementRole) =
        PerformanceTaskQualityRequirement(task, quality, role, "TASK_QUALITY_REVIEW_2026_09")

    fun forTask(task: String): List<PerformanceTaskQualityRequirement> =
        rows.filter { it.task == task }
}

/**
 * Shadow-only need analysis. It is intentionally independent from selection and prescription
 * code. A single pass builds exposure buckets for all quality relations.
 */
class AthleteNeedsProfileEngine(
    private val requirements: List<PerformanceTaskQualityRequirement> =
        CanonicalPerformanceTaskQualityRequirements.rows
) {
    fun analyze(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): AthleteNeedsProfile {
        val rows = snapshot.allConfirmedSets
        val qualityBuckets = TrainableQuality.entries.associateWith { MutableQualityBucket() }.toMutableMap()
        val taskBuckets = linkedMapOf<String, MutableTaskBucket>()
        val physicalRelations = catalog.trainingRelations().groupBy(ExercisePhysicalQualityRelation::exerciseStableKey)
        val taskKeys = requirements.map(PerformanceTaskQualityRequirement::task).toSet()
        taskKeys.forEach { taskBuckets[it] = MutableTaskBucket() }

        rows.forEach { row ->
            val age = ChronoUnit.DAYS.between(row.date, snapshot.cutoff).toInt()
            if (age !in 0..55) return@forEach
            val relations = physicalRelations[row.stableKey].orEmpty()
            relations.forEach { relation ->
                val bucket = qualityBuckets.getValue(relation.qualityId)
                bucket.add(row, relation, age)
            }
            snapshot.badmintonDirectObjectives[row.stableKey].orEmpty().forEach { task ->
                taskBuckets.getOrPut(task) { MutableTaskBucket() }.addDirect(row, age)
            }
            snapshot.badmintonSupportiveObjectives[row.stableKey].orEmpty().forEach { task ->
                taskBuckets.getOrPut(task) { MutableTaskBucket() }.addSupportive(row, age)
            }
        }

        val qualityNeeds = TrainableQuality.entries.map { quality ->
            val exposure = qualityBuckets.getValue(quality).summary(quality, snapshot)
            val relevance = qualityRelevance(quality, snapshot, state)
            val response = responseFor(quality, exposure, snapshot)
            val decision = decide(relevance, exposure.currentExposure, response)
            QualityNeed(
                quality = quality,
                relevance = relevance,
                currentExposure = exposure.currentExposure,
                response = response,
                decision = decision,
                confidence = confidence(exposure, snapshot),
                reasonCodes = qualityReasons(quality, relevance, exposure, response, decision),
                evidence = exposure.evidence,
                exposure = exposure
            )
        }

        val sportTasks = taskBuckets.entries.sortedBy { it.key }.map { (task, bucket) ->
            val structuredDirect = bucket.directBouts(0..27)
            val structuredSupportive = bucket.supportiveBouts(0..27)
            val contextLoad = snapshot.badmintonObjectiveRepresentations
                .firstOrNull { it.objective == task }?.currentWeighted28d ?: 0.0
            val relevant = taskRelevance(snapshot, state, task)
            val directExposure = if (structuredDirect == 0 && contextLoad > 0.0) ExposureState.UNKNOWN
            else exposureState(structuredDirect + structuredSupportive, bucket.directBouts(28..55) + bucket.supportiveBouts(28..55), bucket.currentSessions())
            val response = if (structuredDirect > 0) TrainingResponseState.INSUFFICIENT_EVIDENCE else TrainingResponseState.INSUFFICIENT_EVIDENCE
            val decision = when {
                relevant == NeedRelevance.UNKNOWN -> TrainingNeedDecision.UNKNOWN
                relevant in setOf(NeedRelevance.NONE, NeedRelevance.LOW) -> TrainingNeedDecision.NO_EXTRA_NEED
                directExposure == ExposureState.UNKNOWN -> TrainingNeedDecision.UNKNOWN
                directExposure in setOf(ExposureState.ABSENT, ExposureState.LOW) -> TrainingNeedDecision.DEVELOP
                else -> TrainingNeedDecision.MAINTAIN
            }
            SportTaskNeed(
                task = task,
                relevance = relevant,
                currentExposure = directExposure,
                response = response,
                decision = decision,
                confidence = if (directExposure == ExposureState.UNKNOWN) PlanningConfidence.LOW else bucket.confidence(snapshot),
                structuredDirectBouts = structuredDirect,
                structuredSupportiveBouts = structuredSupportive,
                sportContextLoad = contextLoad,
                reasonCodes = buildList {
                    if (contextLoad > 0.0) add("SPORT_CONTEXT_EXPOSURE_SEPARATE_FROM_STRUCTURED_STIMULUS")
                    if (structuredDirect == 0) add("NO_STRUCTURED_DIRECT_TASK_EVIDENCE")
                    add("TASK_REQUIREMENT_ORDINAL_ONLY")
                },
                evidence = listOf("structuredDirectBouts=$structuredDirect", "structuredSupportiveBouts=$structuredSupportive", "sportContextLoad=$contextLoad")
            )
        }

        val modifiers = executionModifiers(snapshot, qualityNeeds)
        val unresolved = buildList {
            if (snapshot.profilePrimaryGoal.isBlank() && state.strengthIntent == StrengthIntent.UNRESOLVED && state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED) {
                add("UNKNOWN_USER_PRIORITY")
            }
            qualityNeeds.filter { it.decision == TrainingNeedDecision.UNKNOWN }.forEach { add("QUALITY_${it.quality.name}_UNKNOWN") }
            sportTasks.filter { it.decision == TrainingNeedDecision.UNKNOWN }.forEach { add("TASK_${it.task}_UNKNOWN") }
        }.distinct()
        val maintenance = qualityNeeds.filter { it.decision in setOf(TrainingNeedDecision.MAINTAIN, TrainingNeedDecision.MAINTAIN_OR_PROGRESS) }
            .map { it.quality.name }
        return AthleteNeedsProfile(
            generatedAtCutoff = snapshot.cutoff,
            qualityNeeds = qualityNeeds,
            sportTaskNeeds = sportTasks,
            maintenanceDomains = maintenance,
            unresolved = unresolved,
            evidenceSummary = AthleteNeedsEvidenceSummary(historyDays = snapshot.historyDays),
            executionModifiers = modifiers
        )
    }

    private fun qualityRelevance(quality: TrainableQuality, snapshot: PlanningHistorySnapshot, state: AthletePlanningState): NeedRelevance {
        val goal = snapshot.profilePrimaryGoal.trim().uppercase()
        val badminton = state.badmintonIntent == BadmintonPlanningIntent.ENABLED || goal == "BADMINTON_PERFORMANCE"
        return when (quality) {
            TrainableQuality.STRENGTH -> when {
                goal == "STRENGTH_GAIN" || state.strengthIntent == StrengthIntent.STRENGTH_PRIORITY -> NeedRelevance.HIGH
                goal == "STRENGTH_MAINTENANCE" || state.strengthIntent == StrengthIntent.MIXED -> NeedRelevance.MODERATE
                goal.isBlank() && state.strengthIntent == StrengthIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.HYPERTROPHY -> when {
                goal == "HYPERTROPHY_PHYSIQUE" || state.strengthIntent == StrengthIntent.HYPERTROPHY_PRIORITY -> NeedRelevance.HIGH
                goal.isBlank() && state.strengthIntent == StrengthIntent.UNRESOLVED -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC -> when {
                goal == "HYPERTROPHY_PHYSIQUE" && !badminton -> NeedRelevance.NONE
                badminton -> NeedRelevance.HIGH
                goal.isBlank() -> NeedRelevance.UNKNOWN
                else -> NeedRelevance.LOW
            }
            TrainableQuality.MUSCULAR_ENDURANCE, TrainableQuality.CARDIORESPIRATORY_FITNESS -> when {
                goal == "WEIGHT_MANAGEMENT" || goal == "BADMINTON_PERFORMANCE" -> NeedRelevance.HIGH
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

    private fun taskRelevance(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, task: String): NeedRelevance {
        if (snapshot.profilePrimaryGoal.isBlank() && state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED) return NeedRelevance.UNKNOWN
        return if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED || snapshot.profilePrimaryGoal == "BADMINTON_PERFORMANCE") {
            if (CanonicalPerformanceTaskQualityRequirements.forTask(task).isNotEmpty()) NeedRelevance.HIGH else NeedRelevance.UNKNOWN
        } else NeedRelevance.NONE
    }

    private fun responseFor(quality: TrainableQuality, exposure: QualityExposureSummary, snapshot: PlanningHistorySnapshot): TrainingResponseState {
        if (exposure.context56dBouts == 0) return TrainingResponseState.INSUFFICIENT_EVIDENCE
        if (quality in setOf(TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION, TrainableQuality.REACTIVE_STRENGTH_SSC)) {
            return TrainingResponseState.INSUFFICIENT_EVIDENCE
        }
        if (quality == TrainableQuality.STRENGTH) {
            val signals = snapshot.canonicalStrengthSignals.values.filter { it.observationCount >= 2 }
            val change = signals.mapNotNull(CanonicalStrengthSignal::posteriorChangePercent).averageOrNull()
            return when {
                change != null && change > 2.0 && exposure.current28dBouts >= 2 -> TrainingResponseState.POSITIVE_RESPONSE
                change != null && change < -5.0 && exposure.current28dBouts >= 2 -> TrainingResponseState.NEGATIVE_RESPONSE
                exposure.current28dBouts >= 2 -> TrainingResponseState.STABLE_RESPONSE
                else -> TrainingResponseState.INSUFFICIENT_EVIDENCE
            }
        }
        return if (exposure.current28dBouts >= 2) TrainingResponseState.POSITIVE_RESPONSE else TrainingResponseState.INSUFFICIENT_EVIDENCE
    }

    private fun decide(relevance: NeedRelevance, exposure: ExposureState, response: TrainingResponseState): TrainingNeedDecision = when {
        relevance == NeedRelevance.UNKNOWN -> TrainingNeedDecision.UNKNOWN
        relevance in setOf(NeedRelevance.NONE, NeedRelevance.LOW) -> TrainingNeedDecision.NO_EXTRA_NEED
        exposure in setOf(ExposureState.ABSENT, ExposureState.LOW) && response in setOf(TrainingResponseState.INSUFFICIENT_EVIDENCE, TrainingResponseState.STABLE_RESPONSE) -> TrainingNeedDecision.DEVELOP
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) && response == TrainingResponseState.POSITIVE_RESPONSE -> TrainingNeedDecision.MAINTAIN_OR_PROGRESS
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) && response == TrainingResponseState.NEGATIVE_RESPONSE -> TrainingNeedDecision.REDUCE
        exposure in setOf(ExposureState.ESTABLISHED, ExposureState.HIGH) -> TrainingNeedDecision.MAINTAIN
        response == TrainingResponseState.NEGATIVE_RESPONSE -> TrainingNeedDecision.REDISTRIBUTE
        else -> TrainingNeedDecision.DEVELOP
    }

    private fun qualityReasons(quality: TrainableQuality, relevance: NeedRelevance, exposure: QualityExposureSummary, response: TrainingResponseState, decision: TrainingNeedDecision): List<String> = buildList {
        add("GOAL_RELEVANCE_${relevance.name}")
        add("EXPOSURE_${exposure.currentExposure.name}")
        add("RESPONSE_${response.name}")
        add("DECISION_MATRIX_${decision.name}")
        if (exposure.supportiveBouts > 0 && exposure.directBouts == 0) add("SUPPORTIVE_EVIDENCE_ONLY")
        if (quality == TrainableQuality.POWER || quality == TrainableQuality.RAPID_FORCE_PRODUCTION || quality == TrainableQuality.REACTIVE_STRENGTH_SSC) add("PERFORMANCE_TEST_REQUIRED_FOR_RESPONSE")
    }

    private fun confidence(exposure: QualityExposureSummary, snapshot: PlanningHistorySnapshot): PlanningConfidence = when {
        snapshot.historyDays < 28 || exposure.context56dBouts < 2 -> PlanningConfidence.LOW
        exposure.current28dBouts >= 3 && exposure.previous28dBouts > 0 -> PlanningConfidence.HIGH
        else -> PlanningConfidence.MODERATE
    }

    private fun exposureState(current: Int, previous: Int, sessions: Int): ExposureState = when {
        current == 0 -> ExposureState.ABSENT
        previous == 0 && sessions <= 1 -> ExposureState.LOW
        previous > 0 && current < previous * 0.5 -> ExposureState.LOW
        previous > 0 && current > previous * 1.5 -> ExposureState.HIGH
        sessions >= 2 || current >= 3 -> ExposureState.ESTABLISHED
        else -> ExposureState.LOW
    }

    private fun executionModifiers(snapshot: PlanningHistorySnapshot, qualityNeeds: List<QualityNeed>): List<ExecutionModifierTrace> = buildList {
        val restricted = snapshot.recoverySignals.tissueRestrictedStableKeys
        if (restricted.isNotEmpty()) add(ExecutionModifierTrace("TISSUE", restricted.sorted(), ExecutionModifier.SUBSTITUTE, listOf("TISSUE_RESTRICTION_EXECUTION_ONLY")))
        if (snapshot.recoverySignals.isConstrained) add(ExecutionModifierTrace("RECOVERY", emptyList(), ExecutionModifier.HOLD, listOf("RECOVERY_MODIFIER_NOT_NEED_SUPPRESSION")))
        if (snapshot.courtDeviation > 0.0 && snapshot.recoverySignals.isConstrained) add(ExecutionModifierTrace("BADMINTON", emptyList(), ExecutionModifier.REDUCE, listOf("COURT_DEVIATION_PLUS_RECOVERY_EVIDENCE")))
    }

    private class MutableQualityBucket {
        private val records = mutableListOf<QualityObservation>()
        fun add(row: PlanningSetRecord, relation: ExercisePhysicalQualityRelation, age: Int) {
            records += QualityObservation(row, relation.relationLevel, age, classify(row))
        }
        fun summary(quality: TrainableQuality, snapshot: PlanningHistorySnapshot): QualityExposureSummary {
            val recent = records.count { it.age in 0..6 }
            val current = records.count { it.age in 0..27 }
            val previous = records.count { it.age in 28..55 }
            val direct = records.filter { it.level == StimulusCapabilityLevel.DIRECT_CAPABILITY && it.age in 0..27 }
            val supportive = records.filter { it.level == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY && it.age in 0..27 }
            val exposure = exposureState(current, previous, direct.map { it.record.date }.toSet().size + supportive.map { it.record.date }.toSet().size)
            return QualityExposureSummary(quality, recent, current, previous, records.size, direct.map { it.record.date }.toSet().size, direct.size,
                supportive.map { it.record.date }.toSet().size, supportive.size,
                records.count { it.realized == RealizedStimulusClass.STRENGTH_LIKE && it.age in 0..27 },
                records.count { it.realized == RealizedStimulusClass.HYPERTROPHY_LIKE && it.age in 0..27 },
                records.count { it.realized == RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS && it.age in 0..27 },
                exposure, if (snapshot.historyDays < 28) PlanningConfidence.LOW else PlanningConfidence.MODERATE,
                listOf("recent7d=$recent", "current28d=$current", "previous28d=$previous", "directBouts=${direct.size}", "supportiveBouts=${supportive.size}"))
        }
    }

    private class MutableTaskBucket {
        private val direct = mutableListOf<Int>()
        private val supportive = mutableListOf<Int>()
        private val dates = mutableSetOf<LocalDate>()
        fun addDirect(row: PlanningSetRecord, age: Int) { if (age in 0..55) { direct += age; dates += row.date } }
        fun addSupportive(row: PlanningSetRecord, age: Int) { if (age in 0..55) { supportive += age; dates += row.date } }
        fun directBouts(range: IntRange): Int = direct.count { it in range }
        fun supportiveBouts(range: IntRange): Int = supportive.count { it in range }
        fun currentSessions(): Int = dates.size
        fun confidence(snapshot: PlanningHistorySnapshot): PlanningConfidence = if (snapshot.historyDays < 28) PlanningConfidence.LOW else if (direct.size + supportive.size >= 3) PlanningConfidence.MODERATE else PlanningConfidence.LOW
    }

    private data class QualityObservation(val record: PlanningSetRecord, val level: StimulusCapabilityLevel, val age: Int, val realized: RealizedStimulusClass)

    private fun classify(row: PlanningSetRecord): RealizedStimulusClass = when {
        row.reps in 1..6 -> RealizedStimulusClass.STRENGTH_LIKE
        row.reps in 7..15 -> RealizedStimulusClass.HYPERTROPHY_LIKE
        else -> RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
}
