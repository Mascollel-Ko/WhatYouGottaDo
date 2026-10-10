package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import kotlin.math.min
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** B4 dose family. These labels consume existing canonical qualities; they do not add taxonomy. */
enum class PowerJumpDoseKind { POWER, JUMP_LANDING }

enum class PowerJumpBodyRegion { LOWER, UPPER, WHOLE_BODY }

enum class PowerJumpWorkloadStatus { COMPLETE, UNKNOWN }

enum class PowerJumpDoseStatus {
    AUTHORIZED,
    NO_NEED,
    NEED_UNRESOLVED,
    WORKLOAD_UNKNOWN,
    CAPACITY_ZERO,
    LESS_THAN_MINIMUM_SET_DOSE,
    SHARED_CAP_ARBITRATION_REQUIRED
}

enum class BadmintonActivityBand { LOW, MODERATE, HIGH, UNKNOWN }

/**
 * Exact planned B5/B6 resistance-set observations used by the cap calculation. The identity is
 * the physical set identity, so a set authorized under both Strength and Hypertrophy is counted
 * once in each applicable region ledger.
 */
data class PlannedQualityResistanceSet(
    val physicalSetIdentity: String,
    val stableKey: String,
    val quality: TrainableQuality
)

data class PowerJumpResistanceWorkload(
    val status: PowerJumpWorkloadStatus,
    val lowerDirectSets: Int? = null,
    val upperDirectSets: Int? = null,
    val evidence: List<String> = emptyList()
) {
    init {
        require(lowerDirectSets == null || lowerDirectSets >= 0)
        require(upperDirectSets == null || upperDirectSets >= 0)
        if (status == PowerJumpWorkloadStatus.COMPLETE) {
            require(lowerDirectSets != null && upperDirectSets != null)
        }
    }
}

data class ExistingPowerJumpSet(
    val physicalSetIdentity: String,
    val regions: Set<PowerJumpBodyRegion>
)

data class PowerJumpDoseNeed(
    val targetId: String,
    val kind: PowerJumpDoseKind,
    val region: PowerJumpBodyRegion,
    val needDecision: TrainingNeedDecision,
    val priority: TargetPriority,
    val exactSuccessfulWeeks: Int = 0,
    val successfulWeeklySetCount: Int? = null
)

data class PowerJumpDoseEvidence(
    val resistanceWorkload: PowerJumpResistanceWorkload,
    /** null/empty means no reliable recorded minutes; it is never treated as zero. */
    val validRecentBadmintonWeekMinutes: List<Double>?,
    val existingAuthorizedDirectSets: List<ExistingPowerJumpSet> = emptyList(),
    val reasonCodes: List<String> = emptyList()
)

data class PowerJumpDoseDecision(
    val targetId: String,
    val status: PowerJumpDoseStatus,
    val numericAuthority: StimulusTargetNumericAuthority,
    val approvedWeeklySetUnits: Int,
    val applicableWeeklyCap: Int?,
    val existingAuthorizedSetUnits: Int,
    val lowerResistanceBand: ResistanceSetBand?,
    val upperResistanceBand: ResistanceSetBand?,
    val badmintonBand: BadmintonActivityBand,
    val provenance: String,
    val reasonCodes: List<String>,
    val evidence: List<String>,
    /** Exact B4 region survives into B8 so weekly frequency can be validated without reclassification. */
    val targetRegion: PowerJumpBodyRegion? = null
)

enum class ResistanceSetBand { LOW, MODERATE, HIGH }

/**
 * Keeps only observed, complete ISO weeks fully inside the recent 28-day window. Missing weeks
 * stay missing instead of becoming zero; explicitly excluded training weeks are not used.
 */
internal fun recentCompleteBadmintonWeekMinutes(
    cutoff: LocalDate,
    observedMinutesByDate: List<Pair<LocalDate, Double>>,
    excludedWeekStarts: Set<LocalDate> = emptySet()
): List<Double>? {
    val latestCompleteEnd = completedTrainingWeekEnd(cutoff)
    val earliestDate = cutoff.minusDays(27)
    val grouped = observedMinutesByDate.asSequence()
        .filter { (date, minutes) -> !date.isBefore(earliestDate) && !date.isAfter(latestCompleteEnd) &&
            minutes.isFinite() && minutes > 0.0 }
        .groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .filter { (weekStart, _) ->
            !weekStart.isBefore(earliestDate) && !weekStart.plusDays(6).isAfter(latestCompleteEnd) &&
                weekStart !in excludedWeekStarts
        }
    return grouped.toSortedMap().values.map { rows -> rows.sumOf { it.second } }.takeIf { it.isNotEmpty() }
}

/**
 * User-approved product policy for integrated Power and Jump/Landing planning. It is a bounded
 * B4 numeric policy only: B1-B3 must already have produced a need, B5 still selects an exact
 * owner, and B6 separately resolves whether that owner can be executed.
 */
object PowerJumpIntegratedDosePolicy {
    const val PROVENANCE = "USER_APPROVED_PROJECT_POLICY"
    const val INITIAL_DOSE_SETS = 2
    const val PROGRESSED_DOSE_SETS = 4
    const val MAX_TOTAL_NEW_WEEKLY_SETS = 8
    const val MIN_SETS_PER_EXPOSURE = 2
    const val MAX_SETS_PER_EXPOSURE = 4
    const val INITIAL_REPS = 4
    const val MIN_REPS = 3
    const val MAX_REPS = 6
    const val DEFAULT_REST_SECONDS = 120

    /** Direct-only and physical-set-deduplicated projection from exact planned B5/B6 rows. */
    fun projectResistanceWorkload(
        plannedSets: List<PlannedQualityResistanceSet>,
        physicalRelations: List<ExercisePhysicalQualityRelation>,
        projectionComplete: Boolean
    ): PowerJumpResistanceWorkload {
        if (!projectionComplete) return PowerJumpResistanceWorkload(
            PowerJumpWorkloadStatus.UNKNOWN,
            evidence = listOf("PLANNED_STRENGTH_HYPERTROPHY_SET_PROJECTION_INCOMPLETE")
        )
        val directByKey = physicalRelations.asSequence()
            .filter { it.qualityId in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) }
            .filter { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY && it.reviewStatus == "PASS" }
            .groupBy { it.exerciseStableKey }
        val lower = linkedSetOf<String>()
        val upper = linkedSetOf<String>()
        plannedSets.groupBy(PlannedQualityResistanceSet::physicalSetIdentity).forEach { (identity, rows) ->
            // If a set is represented by more than one target/quality projection, union the
            // exact quality evidence before deduplicating its physical training count.
            val keys = rows.map(PlannedQualityResistanceSet::stableKey).distinct()
            if (keys.size != 1) return@forEach
            val eligibleQualities = rows.mapTo(linkedSetOf(), PlannedQualityResistanceSet::quality)
            val relations = directByKey[keys.single()].orEmpty().filter { it.qualityId in eligibleQualities }
            if (relations.any { it.regionQualifier.isLowerBodyDirectRegion() }) lower += identity
            if (relations.any { it.regionQualifier.isUpperBodyDirectRegion() }) upper += identity
        }
        return PowerJumpResistanceWorkload(
            status = PowerJumpWorkloadStatus.COMPLETE,
            lowerDirectSets = lower.size,
            upperDirectSets = upper.size,
            evidence = listOf(
                "EXACT_B5_B6_PLANNED_QUALITY_SET_PROJECTION",
                "DIRECT_STRENGTH_HYPERTROPHY_RELATIONS_ONLY",
                "PHYSICAL_SET_IDENTITY_DEDUPLICATED",
                "lowerDirectSetCount=${lower.size}",
                "upperDirectSetCount=${upper.size}"
            )
        )
    }

    /** Median of caller-validated, complete recent weeks; no observations remain UNKNOWN. */
    fun representativeBadmintonMinutes(weekMinutes: List<Double>?): Double? {
        val values = weekMinutes.orEmpty().filter { it.isFinite() && it >= 0.0 }.takeLast(4).sorted()
        if (values.isEmpty()) return null
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
    }

    fun badmintonBand(weekMinutes: List<Double>?): BadmintonActivityBand {
        val minutes = representativeBadmintonMinutes(weekMinutes) ?: return BadmintonActivityBand.UNKNOWN
        return when {
            minutes >= 360.0 -> BadmintonActivityBand.HIGH
            minutes >= 120.0 -> BadmintonActivityBand.MODERATE
            else -> BadmintonActivityBand.LOW
        }
    }

    fun resolve(
        need: PowerJumpDoseNeed,
        evidence: PowerJumpDoseEvidence
    ): PowerJumpDoseDecision {
        val noNeed = need.needDecision == TrainingNeedDecision.NO_EXTRA_NEED
        val existingByIdentity = evidence.existingAuthorizedDirectSets
            .groupBy(ExistingPowerJumpSet::physicalSetIdentity)
            .mapValues { (_, rows) -> rows.flatMapTo(linkedSetOf(), ExistingPowerJumpSet::regions) }
        val relevantExisting = existingByIdentity.count { (_, regions) -> regionsMatch(need.region, regions) }
        val evidenceCodes = buildList {
            addAll(evidence.reasonCodes)
            addAll(evidence.resistanceWorkload.evidence)
            add("existingDirectPowerJumpPhysicalSets=${existingByIdentity.size}")
            add("targetRelevantExistingDirectPowerJumpSets=$relevantExisting")
        }
        val lowerBand = evidence.resistanceWorkload.lowerDirectSets?.let(::resistanceBand)
        val upperBand = evidence.resistanceWorkload.upperDirectSets?.let(::resistanceBand)
        val badminton = badmintonBand(evidence.validRecentBadmintonWeekMinutes)
        val baseReasons = buildList {
            add("$PROVENANCE")
            add("POWER_JUMP_CAP_IS_NOT_A_REQUIRED_TARGET")
            add("POWER_AND_JUMP_LANDING_SHARE_PHYSICAL_SET_CAPACITY")
            if (badminton == BadmintonActivityBand.UNKNOWN && need.region != PowerJumpBodyRegion.UPPER) {
                add("BADMINTON_MINUTES_UNKNOWN_USE_MODERATE_CONSERVATIVE_CAP_COLUMN")
            }
            if (need.exactSuccessfulWeeks >= 2 && need.priority == TargetPriority.PRIMARY) {
                add("TWO_DISTINCT_VALID_SUCCESSFUL_WEEKS_ALLOW_REVIEW_OF_4_SET_WEEKLY_DOSE")
            }
        }
        if (noNeed) return decision(need, evidence, PowerJumpDoseStatus.NO_NEED, 0, null, relevantExisting,
            lowerBand, upperBand, badminton, baseReasons + "B3_NO_EXTRA_NEED_NO_ADDITIONAL_POWER_JUMP_DOSE", evidenceCodes)
        if (need.needDecision == TrainingNeedDecision.UNKNOWN || need.priority == TargetPriority.UNRESOLVED) {
            return decision(need, evidence, PowerJumpDoseStatus.NEED_UNRESOLVED, 0, null, relevantExisting,
                lowerBand, upperBand, badminton, baseReasons + "B3_NEED_OR_PRIORITY_UNRESOLVED", evidenceCodes)
        }
        if (need.needDecision != TrainingNeedDecision.DEVELOP) {
            return decision(need, evidence, PowerJumpDoseStatus.NO_NEED, 0, null, relevantExisting,
                lowerBand, upperBand, badminton, baseReasons + "B3_DOES_NOT_AUTHORIZE_NEW_POWER_JUMP_OWNER", evidenceCodes)
        }
        if (evidence.resistanceWorkload.status != PowerJumpWorkloadStatus.COMPLETE) {
            return decision(need, evidence, PowerJumpDoseStatus.WORKLOAD_UNKNOWN, 0, null, relevantExisting,
                lowerBand, upperBand, badminton, baseReasons + "EXACT_PLANNED_STRENGTH_HYPERTROPHY_WORKLOAD_REQUIRED", evidenceCodes)
        }
        val lowerSets = requireNotNull(evidence.resistanceWorkload.lowerDirectSets)
        val upperSets = requireNotNull(evidence.resistanceWorkload.upperDirectSets)
        val effectiveCourtBand = if (badminton == BadmintonActivityBand.UNKNOWN) BadmintonActivityBand.MODERATE else badminton
        val cap = when (need.region) {
            PowerJumpBodyRegion.LOWER -> lowerPowerJumpCap(lowerSets, effectiveCourtBand)
            PowerJumpBodyRegion.UPPER -> upperPowerCap(upperSets)
            PowerJumpBodyRegion.WHOLE_BODY -> min(
                lowerPowerJumpCap(lowerSets, effectiveCourtBand),
                upperPowerCap(upperSets)
            )
        }
        val totalExisting = existingByIdentity.size
        val roomByRegion = (cap - relevantExisting).coerceAtLeast(0)
        val totalRoom = (MAX_TOTAL_NEW_WEEKLY_SETS - totalExisting).coerceAtLeast(0)
        val available = min(roomByRegion, totalRoom)
        if (available == 0) return decision(need, evidence, PowerJumpDoseStatus.CAPACITY_ZERO, 0, cap, relevantExisting,
            lowerBand, upperBand, badminton, baseReasons + "NO_NEW_SET_CAPACITY_AFTER_EXISTING_AUTHORIZED_POWER_JUMP_SETS", evidenceCodes)
        val progresses = need.exactSuccessfulWeeks >= 2 && need.priority == TargetPriority.PRIMARY &&
            (need.successfulWeeklySetCount ?: 0) >= PROGRESSED_DOSE_SETS
        val preferred = if (progresses) PROGRESSED_DOSE_SETS else INITIAL_DOSE_SETS
        val approved = min(preferred, available)
        if (approved < MIN_SETS_PER_EXPOSURE) return decision(need, evidence, PowerJumpDoseStatus.LESS_THAN_MINIMUM_SET_DOSE, 0,
            cap, relevantExisting, lowerBand, upperBand, badminton,
            baseReasons + "AVAILABLE_CAPACITY_BELOW_USER_APPROVED_2_SET_INITIAL_EXPOSURE", evidenceCodes)
        return decision(need, evidence, PowerJumpDoseStatus.AUTHORIZED, approved, cap, relevantExisting,
            lowerBand, upperBand, badminton,
            baseReasons + listOf("B3_DEVELOP_NEED_CONFIRMED", "B4_USER_APPROVED_POWER_JUMP_DOSE", "approvedWeeklySetUnits=$approved") +
                if (progresses) listOf("B2_EXACT_OWNER_SUCCESSFUL_VOLUME_AFTER_TWO_VALID_WEEKS") else listOf("COLD_START_INITIAL_2_SET_EXPOSURE"),
            evidenceCodes)
    }

    /**
     * Resolves coexisting Power and Jump/Landing needs against one shared weekly budget.
     * A target's approved units consume the cap before the next target is considered; the
     * deterministic target order is an allocation tie-break only, not a new physiology rank.
     */
    fun resolveAll(
        needs: List<PowerJumpDoseNeed>,
        evidence: PowerJumpDoseEvidence
    ): List<PowerJumpDoseDecision> {
        val original = evidence.existingAuthorizedDirectSets
        val byTarget = linkedMapOf<String, PowerJumpDoseDecision>()
        val synthetic = mutableListOf<ExistingPowerJumpSet>()
        val ordered = needs.distinctBy(PowerJumpDoseNeed::targetId).sortedWith(
            compareBy<PowerJumpDoseNeed> { priorityOrder(it.priority) }
                .thenBy(PowerJumpDoseNeed::targetId)
                .thenBy { it.kind.name }
        )
        ordered.forEach { need ->
            val decision = resolve(need, evidence.copy(existingAuthorizedDirectSets = original + synthetic))
            byTarget[need.targetId] = decision
            if (decision.status == PowerJumpDoseStatus.AUTHORIZED && decision.approvedWeeklySetUnits > 0) {
                val regions = when (need.region) {
                    PowerJumpBodyRegion.LOWER -> setOf(PowerJumpBodyRegion.LOWER)
                    PowerJumpBodyRegion.UPPER -> setOf(PowerJumpBodyRegion.UPPER)
                    PowerJumpBodyRegion.WHOLE_BODY -> setOf(PowerJumpBodyRegion.LOWER, PowerJumpBodyRegion.UPPER, PowerJumpBodyRegion.WHOLE_BODY)
                }
                repeat(decision.approvedWeeklySetUnits) { index ->
                    synthetic += ExistingPowerJumpSet("B4:${need.targetId}:$index", regions)
                }
            }
        }
        return needs.distinctBy(PowerJumpDoseNeed::targetId).mapNotNull { byTarget[it.targetId] }
    }

    private fun regionsMatch(target: PowerJumpBodyRegion, observed: Set<PowerJumpBodyRegion>): Boolean = when (target) {
        PowerJumpBodyRegion.LOWER -> PowerJumpBodyRegion.LOWER in observed || PowerJumpBodyRegion.WHOLE_BODY in observed
        PowerJumpBodyRegion.UPPER -> PowerJumpBodyRegion.UPPER in observed || PowerJumpBodyRegion.WHOLE_BODY in observed
        PowerJumpBodyRegion.WHOLE_BODY -> observed.any { it in setOf(PowerJumpBodyRegion.LOWER, PowerJumpBodyRegion.UPPER, PowerJumpBodyRegion.WHOLE_BODY) }
    }

    private fun priorityOrder(priority: TargetPriority): Int = when (priority) {
        TargetPriority.PRIMARY -> 0
        TargetPriority.SECONDARY -> 1
        TargetPriority.MAINTENANCE -> 2
        TargetPriority.BACKGROUND -> 3
        TargetPriority.NONE -> 4
        TargetPriority.UNRESOLVED -> 5
    }

    fun lowerPowerJumpCap(lowerDirectSets: Int, badmintonBand: BadmintonActivityBand): Int {
        require(lowerDirectSets >= 0)
        val resistance = resistanceBand(lowerDirectSets)
        val court = if (badmintonBand == BadmintonActivityBand.UNKNOWN) BadmintonActivityBand.MODERATE else badmintonBand
        return when (resistance) {
            ResistanceSetBand.LOW -> when (court) {
                BadmintonActivityBand.LOW -> 6
                BadmintonActivityBand.MODERATE, BadmintonActivityBand.UNKNOWN -> 4
                BadmintonActivityBand.HIGH -> 2
            }
            ResistanceSetBand.MODERATE -> when (court) {
                BadmintonActivityBand.LOW -> 4
                BadmintonActivityBand.MODERATE, BadmintonActivityBand.UNKNOWN, BadmintonActivityBand.HIGH -> 2
            }
            ResistanceSetBand.HIGH -> when (court) {
                BadmintonActivityBand.HIGH -> 0
                BadmintonActivityBand.LOW, BadmintonActivityBand.MODERATE, BadmintonActivityBand.UNKNOWN -> 2
            }
        }
    }

    fun upperPowerCap(upperDirectSets: Int): Int = when (resistanceBand(upperDirectSets)) {
        ResistanceSetBand.LOW -> 6
        ResistanceSetBand.MODERATE -> 4
        ResistanceSetBand.HIGH -> 2
    }

    private fun decision(
        need: PowerJumpDoseNeed,
        evidence: PowerJumpDoseEvidence,
        status: PowerJumpDoseStatus,
        units: Int,
        cap: Int?,
        existing: Int,
        lowerBand: ResistanceSetBand?,
        upperBand: ResistanceSetBand?,
        badmintonBand: BadmintonActivityBand,
        reasons: List<String>,
        evidenceCodes: List<String>
    ) = PowerJumpDoseDecision(
        targetId = need.targetId,
        status = status,
        numericAuthority = if (status == PowerJumpDoseStatus.AUTHORIZED) StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY
            else StimulusTargetNumericAuthority.NONE,
        approvedWeeklySetUnits = units,
        applicableWeeklyCap = cap,
        existingAuthorizedSetUnits = existing,
        lowerResistanceBand = lowerBand,
        upperResistanceBand = upperBand,
        badmintonBand = badmintonBand,
        provenance = PROVENANCE,
        reasonCodes = reasons.distinct(),
        evidence = evidenceCodes + listOf(
            "needDecision=${need.needDecision.name}",
            "targetRegion=${need.region.name}",
            "targetKind=${need.kind.name}",
            "resistanceWorkloadStatus=${evidence.resistanceWorkload.status.name}",
            "badmintonBand=${badmintonBand.name}",
            "applicableWeeklyCap=${cap ?: "UNKNOWN"}",
            "approvedWeeklySetUnits=$units"
        ),
        targetRegion = need.region
    )

    private fun resistanceBand(sets: Int): ResistanceSetBand = when {
        sets < 8 -> ResistanceSetBand.LOW
        sets < 16 -> ResistanceSetBand.MODERATE
        else -> ResistanceSetBand.HIGH
    }
}

enum class PowerJumpLoadMode {
    BODYWEIGHT_CONFIRMED,
    /** External load is known to be needed, but no identity-specific coefficient is authorized. */
    EXTERNAL_LOAD_USER_CONFIRMATION,
    /** Explicit caller-validated modes only; equipment by itself never selects these. */
    GENERAL_RESISTANCE_POWER,
    POWER_CLEAN,
    JUMP_SQUAT,
    UNKNOWN
}

enum class PowerJumpPrescriptionStatus { AUTHORIZED, USER_INPUT_REQUIRED, UNSUPPORTED }

data class PowerJumpPrescriptionRequest(
    val kind: PowerJumpDoseKind,
    val stableKey: String,
    val exactRelation: ExercisePhysicalQualityRelation?,
    val loadMode: PowerJumpLoadMode,
    val exactApprovedSetCount: Int,
    val exactExerciseOneRmKg: Double? = null,
    val exactOneRmReliable: Boolean = false,
    val userConfirmedExternalLoadKg: Double? = null,
    val approvedPersonalReps: Int? = null,
    val exactApprovedRestSeconds: Int? = null,
    /** True only if canonical exercise/session fields preserve per-side and actual landing counts. */
    val sideAndLandingSemanticsPreserved: Boolean = true,
    val exerciseSelectable: Boolean = true
)

/** Builds the same exact metadata/history-backed B6 request for B5 preflight and final B6. */
object PowerJumpB6RequestFactory {
    fun create(
        target: StimulusQualityTarget,
        stableKey: String,
        exactApprovedSetCount: Int,
        snapshot: PlanningHistorySnapshot,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog
    ): PowerJumpPrescriptionRequest {
        val quality = target.quality
        val kind = when (quality) {
            TrainableQuality.POWER -> PowerJumpDoseKind.POWER
            TrainableQuality.REACTIVE_STRENGTH_SSC -> PowerJumpDoseKind.JUMP_LANDING
            else -> PowerJumpDoseKind.POWER
        }
        val exactRelation = physicalQualityCatalog.relations(stableKey).singleOrNull { relation ->
            relation.exerciseStableKey == stableKey && relation.qualityId == quality &&
                relation.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY && relation.reviewStatus == "PASS" &&
                target.acceptsPhysicalMode(relation.modeQualifier.name)
        }
        val exercise = snapshot.exercises[stableKey]
        val signal = snapshot.canonicalStrengthSignals[stableKey]
        val personal = PerformancePrescriptionResolver.resolve(snapshot, stableKey)
            ?.takeIf { it.source == "RECENT_PERSONAL_EXECUTION_HOLD" || it.source.startsWith("CANONICAL_PROGRAM_") }
            ?.sets?.map { it.reps }?.distinct()?.singleOrNull()
        return PowerJumpPrescriptionRequest(
            kind = kind,
            stableKey = stableKey,
            exactRelation = exactRelation,
            loadMode = exactPowerJumpLoadMode(exercise?.equipment.orEmpty(), exercise?.equipmentTags.orEmpty()),
            exactApprovedSetCount = exactApprovedSetCount,
            exactExerciseOneRmKg = signal?.posteriorMedianKg,
            exactOneRmReliable = signal?.let {
                it.baselineEstablished && it.twoSidedObservationCount >= 2 &&
                    it.posteriorMedianKg?.isFinite() == true && it.posteriorMedianKg > 0.0
            } == true,
            approvedPersonalReps = personal,
            // Only bilateral exercises preserve the count as physical repetitions/landings in
            // the current set schema. Alternating/unilateral sides have no lossless side field.
            sideAndLandingSemanticsPreserved = exercise?.laterality?.uppercase() == "BILATERAL",
            exerciseSelectable = snapshot.metadata[stableKey]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE")
        )
    }
}

private fun exactPowerJumpLoadMode(equipment: String, tags: String): PowerJumpLoadMode {
    val normalizedEquipment = equipment.trim().uppercase()
    val exactTags = tags.split('|', ',', ';').map { it.trim().uppercase() }.toSet()
    if (normalizedEquipment in setOf("BODYWEIGHT", "BODY WEIGHT", "맨몸") || exactTags.any { it in setOf("BODYWEIGHT", "BODY_WEIGHT", "맨몸") }) {
        return PowerJumpLoadMode.BODYWEIGHT_CONFIRMED
    }
    if (normalizedEquipment in setOf("BARBELL", "바벨", "DUMBBELL", "덤벨", "MEDICINE BALL", "MED_BALL", "메디신볼", "KETTLEBELL", "케틀벨") ||
        exactTags.any { it in setOf("BARBELL", "DUMBBELL", "MEDICINE_BALL", "MED_BALL", "KETTLEBELL") }) {
        // Equipment proves an external load exists; it does not establish that this exercise's
        // power mode has an approved relationship to a 1RM coefficient.
        return PowerJumpLoadMode.EXTERNAL_LOAD_USER_CONFIRMATION
    }
    return PowerJumpLoadMode.UNKNOWN
}

private fun StimulusQualityTarget.acceptsPhysicalMode(mode: String): Boolean =
    requiredPhysicalModes?.contains(mode) ?: true

data class PowerJumpPrescriptionDecision(
    val status: PowerJumpPrescriptionStatus,
    val prescription: PlannedPrescription?,
    val suggestedExternalLoadKg: Double? = null,
    val reasonCodes: List<String>
)

/** B6 shape resolver; it never chooses B4 set count or assumes a load from a proxy lift. */
object PowerJumpPrescriptionShapeResolver {
    fun resolve(request: PowerJumpPrescriptionRequest): PowerJumpPrescriptionDecision {
        val relation = request.exactRelation
        val exactQuality = when (request.kind) {
            PowerJumpDoseKind.POWER -> TrainableQuality.POWER
            PowerJumpDoseKind.JUMP_LANDING -> TrainableQuality.REACTIVE_STRENGTH_SSC
        }
        val modeAllowed = when (request.kind) {
            PowerJumpDoseKind.POWER -> relation?.modeQualifier?.name in setOf("BALLISTIC", "PLYOMETRIC")
            PowerJumpDoseKind.JUMP_LANDING -> relation?.modeQualifier?.name == "LANDING"
        }
        if (!request.exerciseSelectable || relation == null || relation.exerciseStableKey != request.stableKey ||
            relation.qualityId != exactQuality || relation.relationLevel != StimulusCapabilityLevel.DIRECT_CAPABILITY ||
            relation.reviewStatus != "PASS" || !modeAllowed
        ) return unsupported("EXACT_REVIEWED_DIRECT_PHYSICAL_QUALITY_RELATION_REQUIRED")
        if (!request.sideAndLandingSemanticsPreserved) return unsupported("SIDE_OR_LANDING_COUNT_NOT_LOSSLESSLY_REPRESENTED")
        if (request.exactApprovedSetCount !in PowerJumpIntegratedDosePolicy.MIN_SETS_PER_EXPOSURE..
            PowerJumpIntegratedDosePolicy.MAX_SETS_PER_EXPOSURE) {
            return unsupported("B6_SET_COUNT_MUST_MATCH_EXACT_B4_2_TO_4_SET_AUTHORITY")
        }
        val reps = request.approvedPersonalReps?.takeIf {
            it in PowerJumpIntegratedDosePolicy.MIN_REPS..PowerJumpIntegratedDosePolicy.MAX_REPS
        } ?: PowerJumpIntegratedDosePolicy.INITIAL_REPS
        val confirmedLoad = request.userConfirmedExternalLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        val suggestion = loadSuggestion(request)
        val userInput = request.loadMode != PowerJumpLoadMode.BODYWEIGHT_CONFIRMED && confirmedLoad == null
        val loadState = when {
            request.loadMode == PowerJumpLoadMode.BODYWEIGHT_CONFIRMED -> ProgramLoadState.NOT_APPLICABLE
            confirmedLoad != null -> ProgramLoadState.EXPLICIT_LOAD
            else -> ProgramLoadState.USER_CALIBRATION_REQUIRED
        }
        if (request.loadMode == PowerJumpLoadMode.UNKNOWN) return PowerJumpPrescriptionDecision(
            PowerJumpPrescriptionStatus.UNSUPPORTED, null, suggestion,
            listOf("EXERCISE_EXTERNAL_LOAD_MODE_UNKNOWN", "NO_ZERO_KG_LOAD_FALLBACK")
        )
        if (request.loadMode == PowerJumpLoadMode.JUMP_SQUAT && confirmedLoad == null) return PowerJumpPrescriptionDecision(
            PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED, null, null,
            listOf("JUMP_SQUAT_LOAD_REQUIRES_EXACT_VARIANT_OR_USER_CALIBRATION", "NORMAL_SQUAT_1RM_NOT_TRANSFERRED")
        )
        val sets = List(request.exactApprovedSetCount) { index ->
            ProgramSetPrescription(
                setIndex = index + 1,
                reps = reps,
                weightKg = confirmedLoad ?: 0.0,
                seconds = 0,
                targetRpeMin = null,
                loadState = loadState
            )
        }
        val prescription = PlannedPrescription(
            text = "${sets.size}세트 × ${reps}회 · 속도와 수행 품질 우선",
            sets = sets,
            restSeconds = request.exactApprovedRestSeconds?.takeIf { it > 0 }
                ?: PowerJumpIntegratedDosePolicy.DEFAULT_REST_SECONDS,
            weightSource = when {
                loadState == ProgramLoadState.NOT_APPLICABLE -> "USER_APPROVED_POWER_JUMP_BODYWEIGHT_POLICY"
                userInput -> "POWER_JUMP_USER_CALIBRATION_REQUIRED"
                else -> "USER_CONFIRMED_POWER_JUMP_EXTERNAL_LOAD"
            }
        )
        return PowerJumpPrescriptionDecision(
            status = if (userInput) PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED else PowerJumpPrescriptionStatus.AUTHORIZED,
            prescription = prescription,
            suggestedExternalLoadKg = suggestion,
            reasonCodes = buildList {
                add("USER_APPROVED_POWER_JUMP_2_TO_4_SETS_AND_3_TO_6_REPS")
                add("B6_CONSUMES_EXACT_B4_SET_COUNT=${sets.size}")
                add("REPETITIONS=$reps")
                add("QUALITY_FIRST_NO_FAILURE_RPE_REQUIREMENT")
                add("REST_SECONDS=${prescription.restSeconds}")
                if (userInput) add("EXTERNAL_LOAD_REQUIRES_EXPLICIT_USER_CALIBRATION")
                if (suggestion != null) add("SAME_EXERCISE_1RM_ONLY_USER_CONFIRMATION_SUGGESTION")
                if (request.approvedPersonalReps == null) add("COLD_START_4_REP_ANCHOR_WITHIN_APPROVED_3_TO_6_BAND")
            }
        )
    }

    private fun loadSuggestion(request: PowerJumpPrescriptionRequest): Double? {
        if (!request.exactOneRmReliable || request.exactExerciseOneRmKg?.let { it.isFinite() && it > 0.0 } != true) return null
        val coefficient = when (request.loadMode) {
            PowerJumpLoadMode.GENERAL_RESISTANCE_POWER -> .50
            PowerJumpLoadMode.POWER_CLEAN -> .70
            PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, PowerJumpLoadMode.EXTERNAL_LOAD_USER_CONFIRMATION,
            PowerJumpLoadMode.JUMP_SQUAT, PowerJumpLoadMode.UNKNOWN -> return null
        }
        return requireNotNull(request.exactExerciseOneRmKg) * coefficient
    }

    private fun unsupported(reason: String) = PowerJumpPrescriptionDecision(
        PowerJumpPrescriptionStatus.UNSUPPORTED, null, null, listOf(reason)
    )
}

/**
 * B4 adapter used after the existing Strength/Hypertrophy B5+B6 pass. It consumes only B3
 * decisions and exact already-authorized weekly resistance rows; it does not infer need from
 * exercise metadata and does not authorize a candidate or an executable prescription.
 */
class CanonicalPowerJumpB4DoseResolver {
    fun resolve(
        targetPlan: StimulusTargetPlan,
        existingSelection: StimulusCandidateSelectionPlan,
        existingAuthorization: StimulusPrescriptionAuthorizationPlan,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        physicalQualityCatalog: com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
    ): StimulusTargetPlan {
        val baseQualities = setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
        val selectedPairs = existingSelection.selectedCandidates.flatMap { candidate ->
            candidate.coveredTargetIds.mapNotNull { id ->
                val quality = id.removePrefix("QUALITY:").let { value ->
                    runCatching { TrainableQuality.valueOf(value) }.getOrNull()
                }
                quality?.takeIf { it in baseQualities }?.let { Triple(candidate.stableKey, candidate.selectionRole, it) }
            }
        }.toSet()
        val authByIdentity = existingAuthorization.authorizations.mapNotNull { authorization ->
            val owner = authorization.owner ?: return@mapNotNull null
            val quality = authorization.quality ?: return@mapNotNull null
            Triple(owner.stableKey, owner.selectionRole, quality) to authorization
        }.toMap()
        val selectedRowsAuthorized = selectedPairs.all { (stableKey, role, quality) ->
            authByIdentity[Triple(stableKey, role, quality)]?.let { authorization ->
                authorization.authorizedPrescription != null && authorization.status in EXECUTABLE_B6_STATUSES
            } == true
        }
        val planned = existingAuthorization.authorizations.asSequence()
            .filter { it.quality?.let(baseQualities::contains) == true && it.owner != null &&
                it.authorizedPrescription != null && it.status in EXECUTABLE_B6_STATUSES }
            .flatMap { authorization ->
                val owner = requireNotNull(authorization.owner)
                val quality = requireNotNull(authorization.quality)
                authorization.authorizedPrescription!!.sets.asSequence().map { set ->
                    PlannedQualityResistanceSet(
                        physicalSetIdentity = "${owner.stableKey}#${owner.selectionRole}#${set.setIndex}",
                        stableKey = owner.stableKey,
                        quality = quality
                    )
                }
            }.toList()
        val workload = PowerJumpIntegratedDosePolicy.projectResistanceWorkload(
            plannedSets = planned,
            physicalRelations = physicalQualityCatalog.allRelations(),
            // B2 historical anchors are not part of the current planned program. Only an
            // actually B5-selected S/H row with missing exact B6 authority makes this
            // current-week projection incomplete.
            projectionComplete = selectedRowsAuthorized
        )
        val validWeeks = validRecentCourtWeekMinutes(snapshot, state)
        val needs = targetPlan.qualityTargets.mapNotNull { target ->
            val kind = when (target.quality) {
                TrainableQuality.POWER -> PowerJumpDoseKind.POWER
                TrainableQuality.REACTIVE_STRENGTH_SSC -> PowerJumpDoseKind.JUMP_LANDING
                else -> return@mapNotNull null
            }
            val acceptedModes = when (kind) {
                PowerJumpDoseKind.POWER -> setOf(com.training.trackplanner.data.PhysicalQualityMode.BALLISTIC,
                    com.training.trackplanner.data.PhysicalQualityMode.PLYOMETRIC)
                PowerJumpDoseKind.JUMP_LANDING -> setOf(com.training.trackplanner.data.PhysicalQualityMode.LANDING)
            }
            val exactRelations = physicalQualityCatalog.relations(target.quality).filter { relation ->
                relation.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY && relation.reviewStatus == "PASS" &&
                    relation.modeQualifier in acceptedModes
            }
            val region = regionFor(exactRelations.map { it.regionQualifier })
            val b3Need = if (region == null) TrainingNeedDecision.UNKNOWN else target.needDecision ?: TrainingNeedDecision.UNKNOWN
            PowerJumpDoseNeed(
                targetId = "QUALITY:${target.quality.name}",
                kind = kind,
                region = region ?: PowerJumpBodyRegion.WHOLE_BODY,
                needDecision = b3Need,
                priority = if (b3Need == TrainingNeedDecision.UNKNOWN) TargetPriority.UNRESOLVED else target.priority,
                // No dose upshift is inferred before an exact B5 owner is chosen.
                exactSuccessfulWeeks = 0,
                successfulWeeklySetCount = null
            )
        }
        val decisions = PowerJumpIntegratedDosePolicy.resolveAll(needs, PowerJumpDoseEvidence(
            resistanceWorkload = workload,
            validRecentBadmintonWeekMinutes = validWeeks,
            reasonCodes = listOf(
                "B4_CONSUMES_B3_QUALITY_NEED_DECISION",
                "B4_RESISTANCE_WORKLOAD_PROJECTED_FROM_EXACT_B5_B6_STRENGTH_HYPERTROPHY_ROWS",
                "B4_BADMINTON_MINUTES_FROM_CANONICAL_CONFIRMED_COURT_OBSERVATIONS",
                "NO_POWER_JUMP_DOSE_UPSHIFT_WITHOUT_EXACT_B5_OWNER_HISTORY",
                "B2_HISTORY_ANCHORS_ARE_NOT_CURRENT_WEEK_PLANNED_SETS"
            )
        ))
        val byTarget = decisions.associateBy(PowerJumpDoseDecision::targetId)
        val qualities = targetPlan.qualityTargets.map { target ->
            val decision = byTarget["QUALITY:${target.quality.name}"] ?: return@map target
            if (decision.status != PowerJumpDoseStatus.AUTHORIZED || decision.approvedWeeklySetUnits <= 0) return@map target.copy(
                reasonCodes = (target.reasonCodes + decision.reasonCodes).distinct(),
                evidence = (target.evidence + decision.evidence).distinct()
            )
            val requiredModes = when (decision.targetKind()) {
                PowerJumpDoseKind.POWER -> setOf("BALLISTIC", "PLYOMETRIC")
                PowerJumpDoseKind.JUMP_LANDING -> setOf("LANDING")
            }
            target.copy(
                numericAuthority = StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY,
                weeklyDirectUnitsTarget = StimulusTargetRange(
                    decision.approvedWeeklySetUnits.toDouble(), decision.approvedWeeklySetUnits.toDouble(),
                    decision.approvedWeeklySetUnits.toDouble()
                ),
                requiredPhysicalModes = requiredModes,
                reasonCodes = (target.reasonCodes + decision.reasonCodes + "B4_POWER_JUMP_TARGET_IS_AN_UPPER_BOUND_NOT_A_REQUIRED_DOSE").distinct(),
                evidence = (target.evidence + decision.evidence + "b4PowerJumpNumericAuthority=${decision.numericAuthority.name}").distinct()
            )
        }
        return targetPlan.copy(qualityTargets = qualities, powerJumpDoseDecisions = decisions)
    }

    private fun validRecentCourtWeekMinutes(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState
    ): List<Double>? {
        val excludedWeeks = state.trainingStateAssessment?.weeklyContext.orEmpty()
            .filter { it.excludedFromTolerance }.mapTo(hashSetOf()) { it.start }
        val observations = snapshot.stimulusExposureLedger.courtObservations.map { it.source.date to it.durationMinutes }
        return recentCompleteBadmintonWeekMinutes(snapshot.cutoff, observations, excludedWeeks)
    }

    private fun regionFor(regions: List<com.training.trackplanner.data.PhysicalQualityRegion>): PowerJumpBodyRegion? {
        if (regions.isEmpty()) return null
        val lower = regions.any { it in setOf(
            com.training.trackplanner.data.PhysicalQualityRegion.LOWER,
            com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN,
            com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE,
            com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING,
            com.training.trackplanner.data.PhysicalQualityRegion.ANKLE,
            com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER
        ) }
        val upper = regions.any { it in setOf(
            com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH,
            com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL,
            com.training.trackplanner.data.PhysicalQualityRegion.FOREARM_GRIP,
            com.training.trackplanner.data.PhysicalQualityRegion.CHEST,
            com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS,
            com.training.trackplanner.data.PhysicalQualityRegion.ARMS
        ) }
        val unresolved = regions.any { it !in setOf(
            com.training.trackplanner.data.PhysicalQualityRegion.LOWER,
            com.training.trackplanner.data.PhysicalQualityRegion.POSTERIOR_CHAIN,
            com.training.trackplanner.data.PhysicalQualityRegion.QUADS_GLUTE,
            com.training.trackplanner.data.PhysicalQualityRegion.HAMSTRING,
            com.training.trackplanner.data.PhysicalQualityRegion.ANKLE,
            com.training.trackplanner.data.PhysicalQualityRegion.UNILATERAL_LOWER,
            com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PUSH,
            com.training.trackplanner.data.PhysicalQualityRegion.UPPER_PULL,
            com.training.trackplanner.data.PhysicalQualityRegion.FOREARM_GRIP,
            com.training.trackplanner.data.PhysicalQualityRegion.CHEST,
            com.training.trackplanner.data.PhysicalQualityRegion.SHOULDERS,
            com.training.trackplanner.data.PhysicalQualityRegion.ARMS
        ) }
        return when {
            unresolved || lower && upper -> PowerJumpBodyRegion.WHOLE_BODY
            lower -> PowerJumpBodyRegion.LOWER
            upper -> PowerJumpBodyRegion.UPPER
            else -> PowerJumpBodyRegion.WHOLE_BODY
        }
    }

    private fun PowerJumpDoseDecision.targetKind(): PowerJumpDoseKind =
        if (targetId == "QUALITY:${TrainableQuality.POWER.name}") PowerJumpDoseKind.POWER else PowerJumpDoseKind.JUMP_LANDING

    private companion object {
        val EXECUTABLE_B6_STATUSES = setOf(
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR,
            StimulusPrescriptionAuthorizationStatus.AUTHORIZED_COLD_START_USER_CALIBRATION
        )
    }
}

private fun PhysicalQualityRegion.isLowerBodyDirectRegion(): Boolean = this in setOf(
    PhysicalQualityRegion.LOWER,
    PhysicalQualityRegion.POSTERIOR_CHAIN,
    PhysicalQualityRegion.QUADS_GLUTE,
    PhysicalQualityRegion.HAMSTRING,
    PhysicalQualityRegion.ANKLE,
    PhysicalQualityRegion.UNILATERAL_LOWER
)

private fun PhysicalQualityRegion.isUpperBodyDirectRegion(): Boolean = this in setOf(
    PhysicalQualityRegion.UPPER_PUSH,
    PhysicalQualityRegion.UPPER_PULL,
    PhysicalQualityRegion.CHEST,
    PhysicalQualityRegion.SHOULDERS,
    PhysicalQualityRegion.ARMS,
    PhysicalQualityRegion.FOREARM_GRIP
)
