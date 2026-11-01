package com.training.trackplanner.data.personalized

import android.content.Context
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * B-specific task descriptions are reviewed metadata, NOT prescription or set-credit authority.
 * This reader deliberately accepts only the currently published V2 contract. An unknown version,
 * authority, or task cannot silently become an executable training target.
 */
enum class BBalancedTaskDomain { CORE, MOVEMENT }
enum class BBalancedTaskReview { REVIEWED, REVIEW_REQUIRED }

data class BBalancedTaskRelation(
    val relationId: String,
    val stableKey: String,
    val domain: BBalancedTaskDomain,
    val task: String,
    val review: BBalancedTaskReview,
    val recordingUnit: String
)

class BBalancedTaskCatalog private constructor(val relations: List<BBalancedTaskRelation>) {
    private val byKey = relations.groupBy(BBalancedTaskRelation::stableKey)

    fun forStableKey(stableKey: String): List<BBalancedTaskRelation> =
        byKey[stableKey.trim().lowercase(Locale.ROOT)].orEmpty()

    companion object {
        val EMPTY: BBalancedTaskCatalog = of(emptyList())
        const val ASSET_PATH = "metadata/b_balanced_v1/target_task_relations.csv"
        private val REQUIRED_HEADER = listOf(
            "relationId", "exerciseStableKey", "targetDomain", "targetTask", "relationMeaning",
            "evidenceSource", "reviewStatus", "numericDoseAuthority", "recordingUnit"
        )
        private val CORE_TASKS = setOf(
            "ANTI_EXTENSION", "ANTI_ROTATION", "ANTI_LATERAL_FLEXION",
            "AXIAL_BRACING", "DYNAMIC_TRUNK_STABILIZATION", "ROTATION_GENERATION",
            "TRUNK_FLEXION", "TRUNK_EXTENSION"
        )
        private val MOVEMENT_TASKS = setOf("HIP_ADDUCTION", "HIP_ABDUCTION")

        fun fromAssets(context: Context): BBalancedTaskCatalog =
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { fromCsv(it.readText()) }

        fun fromCsv(csv: String): BBalancedTaskCatalog {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.isNotEmpty()) { "Missing B task overlay" }
            val header = SeedData.parseCsvLine(lines.first()).map { it.trim().trimStart('\uFEFF') }
            require(header == REQUIRED_HEADER) { "Unsupported B task overlay schema" }
            val rows = lines.drop(1).map { line ->
                val columns = SeedData.parseCsvLine(line).map(String::trim)
                require(columns.size == header.size) { "Malformed B task overlay row" }
                header.zip(columns).toMap()
            }
            return of(rows.map { row ->
                fun field(key: String) = row.getValue(key).also { require(it.isNotBlank()) { "Missing $key" } }
                val domain = BBalancedTaskDomain.valueOf(field("targetDomain"))
                val task = field("targetTask")
                require(task in if (domain == BBalancedTaskDomain.CORE) CORE_TASKS else MOVEMENT_TASKS) {
                    "Unsupported B task: $domain/$task"
                }
                val requiredMeaning = if (domain == BBalancedTaskDomain.CORE) "CORE_TASK_DETAIL"
                    else "SPECIFIC_MOVEMENT_TARGET"
                require(field("relationMeaning") == requiredMeaning)
                require(field("numericDoseAuthority") == "NONE") {
                    "B task overlay may not grant numeric dose"
                }
                val review = when (field("reviewStatus")) {
                    "REVIEWED_B_OVERLAY_V2" -> BBalancedTaskReview.REVIEWED
                    "REVIEW_REQUIRED" -> BBalancedTaskReview.REVIEW_REQUIRED
                    else -> error("Unsupported B task review/version")
                }
                field("evidenceSource")
                BBalancedTaskRelation(
                    relationId = field("relationId"),
                    stableKey = field("exerciseStableKey").lowercase(Locale.ROOT),
                    domain = domain,
                    task = task,
                    review = review,
                    recordingUnit = field("recordingUnit")
                )
            })
        }

        fun of(relations: Collection<BBalancedTaskRelation>): BBalancedTaskCatalog {
            val normalized = relations.map { it.copy(stableKey = it.stableKey.trim().lowercase(Locale.ROOT)) }
            require(normalized.all {
                it.relationId.isNotBlank() && it.stableKey.isNotBlank() && it.recordingUnit.isNotBlank() &&
                    it.task in if (it.domain == BBalancedTaskDomain.CORE) CORE_TASKS else MOVEMENT_TASKS
            })
            require(normalized.map { it.relationId }.distinct().size == normalized.size) {
                "Duplicate B task relation ID"
            }
            require(normalized.map { Triple(it.stableKey, it.domain, it.task) }.distinct().size == normalized.size) {
                "Duplicate B task exercise/domain/task"
            }
            return BBalancedTaskCatalog(normalized.sortedWith(compareBy({ it.stableKey }, { it.relationId })))
        }
    }
}

/**
 * Only Strength and Hypertrophy receive reviewed realized-dose credit.
 * Core direct observations are counted as performed physical sets, not a validated
 * physiological dose. Indirect muscle observations are anatomical participation
 * hints and NEVER equivalent/direct sets.
 */
enum class BStimulusEvidenceState {
    REALIZED_DIRECT,
    OBSERVED_DIRECT_CORE,
    REVIEWED_NON_REALIZATION,
    UNRESOLVED,
    NOT_APPLICABLE
}

data class BStimulusSetAttribution(
    val source: StimulusSourceRef,
    val strength: BStimulusEvidenceState,
    val strengthAnchor: CanonicalStrengthExposureCapability.MovementAnchor?,
    val hypertrophy: BStimulusEvidenceState,
    val directHypertrophyMuscles: Set<String>,
    val unresolvedHypertrophyMuscles: Set<String>,
    val indirectMuscleParticipation: Set<String>,
    val core: BStimulusEvidenceState,
    val reviewedCoreTasks: Set<String>,
    val reviewedMovementTasks: Set<String>,
    val reviewRequiredTasks: Set<String>,
    val reasons: Set<String>
) {
    /** A source set can have several target roles, but is still one physical set. */
    val sourceIdentity: String get() =
        "${source.date}|${source.sessionStableKey}|${source.sourceObservationId}"
}

data class BStimulusWindowSummary(
    val window: StimulusExposureWindow,
    val observedPhysicalSets: Int,
    val realizedStrengthSets: Int,
    val realizedHypertrophyPhysicalSets: Int,
    val observedDirectCorePhysicalSets: Int,
    val physicalSetsWithUnresolvedEvidence: Int,
    /** Independent target projections; NEVER sum these maps into a physical-set total. */
    val strengthSetsByAnchor: Map<String, Int>,
    val directHypertrophySetsByMuscle: Map<String, Int>,
    val indirectMuscleParticipationOccurrences: Map<String, Int>,
    val coreTaskObservations: Map<String, Int>,
    val movementTaskObservations: Map<String, Int>,
    val distinctSessions: Int,
    val distinctTrainingDays: Int,
    val reviewRequiredPhysicalSets: Int
)

/** Read-only, bounded source attribution; it cannot select exercises or prescribe training. */
data class BBalancedStimulusLedgerV1(
    val cutoff: LocalDate,
    val sets: List<BStimulusSetAttribution>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val selectionAuthority: Boolean = false,
    val schedulingAuthority: Boolean = false
) {
    fun summary(window: StimulusExposureWindow): BStimulusWindowSummary {
        val selected = sets.filter { set ->
            ChronoUnit.DAYS.between(set.source.date, cutoff) in
                window.minimumAgeDays.toLong()..window.maximumAgeDays.toLong()
        }
        fun countTargets(project: (BStimulusSetAttribution) -> Collection<String>): Map<String, Int> =
            selected.flatMap { set -> project(set).distinct() }.groupingBy { it }.eachCount().toSortedMap()
        return BStimulusWindowSummary(
            window = window,
            observedPhysicalSets = selected.size,
            realizedStrengthSets = selected.count { it.strength == BStimulusEvidenceState.REALIZED_DIRECT },
            realizedHypertrophyPhysicalSets = selected.count { it.hypertrophy == BStimulusEvidenceState.REALIZED_DIRECT },
            observedDirectCorePhysicalSets = selected.count { it.core == BStimulusEvidenceState.OBSERVED_DIRECT_CORE },
            physicalSetsWithUnresolvedEvidence = selected.count {
                it.strength == BStimulusEvidenceState.UNRESOLVED ||
                    it.hypertrophy == BStimulusEvidenceState.UNRESOLVED ||
                    it.core == BStimulusEvidenceState.UNRESOLVED ||
                    it.reviewRequiredTasks.isNotEmpty()
            },
            strengthSetsByAnchor = countTargets { item ->
                if (item.strength == BStimulusEvidenceState.REALIZED_DIRECT)
                    listOfNotNull(item.strengthAnchor?.name) else emptyList()
            },
            directHypertrophySetsByMuscle = countTargets(BStimulusSetAttribution::directHypertrophyMuscles),
            indirectMuscleParticipationOccurrences = countTargets(BStimulusSetAttribution::indirectMuscleParticipation),
            coreTaskObservations = countTargets { item ->
                if (item.core == BStimulusEvidenceState.OBSERVED_DIRECT_CORE) item.reviewedCoreTasks else emptySet()
            },
            movementTaskObservations = countTargets(BStimulusSetAttribution::reviewedMovementTasks),
            distinctSessions = selected.map { it.source.date to it.source.sessionStableKey }.distinct().size,
            distinctTrainingDays = selected.map { it.source.date }.distinct().size,
            reviewRequiredPhysicalSets = selected.count { it.reviewRequiredTasks.isNotEmpty() }
        )
    }

    fun windows(): Map<StimulusExposureWindow, BStimulusWindowSummary> =
        StimulusExposureWindow.entries.associateWith(::summary)

    /** Exact B evidence for B5 familiar-owner ranking. No new sets, dose, or selection permission. */
    fun realizedHistoryStableKeys(quality: TrainableQuality): Set<String> = sets.asSequence()
        .filter { attribution ->
            when (quality) {
                TrainableQuality.STRENGTH -> attribution.strength == BStimulusEvidenceState.REALIZED_DIRECT
                TrainableQuality.HYPERTROPHY -> attribution.hypertrophy == BStimulusEvidenceState.REALIZED_DIRECT &&
                    attribution.directHypertrophyMuscles.isNotEmpty()
                else -> false
            }
        }
        .mapTo(linkedSetOf()) { it.source.stableKey }
}

/**
 * Projects already-confirmed B1 observations, canonical muscle relations, reviewed CoreClass,
 * and the non-numeric B overlay. No inference from exercise names or legacy muscle coefficients.
 * A missing authority is UNRESOLVED, not an invented zero, percentage, or equivalent set.
 */
class BBalancedStimulusLedgerV1Builder {
    fun build(
        source: StimulusExposureLedger,
        muscleRelations: List<CanonicalMetadataRelation>,
        taskCatalog: BBalancedTaskCatalog
    ): BBalancedStimulusLedgerV1 {
        val cutoff = requireNotNull(source.cutoff) { "B attribution requires an explicit cutoff" }
        val musclesByKey = muscleRelations.filter { it.domain == CanonicalRelationDomain.MUSCLE }
            .groupBy { it.exerciseStableKey.trim().lowercase(Locale.ROOT) }
        val result = source.setObservations.asSequence()
            .filter {
                val age = ChronoUnit.DAYS.between(it.source.date, cutoff)
                age in 0L..55L
            }
            .map { observation ->
                val profile = source.facetProfilesByStableKey[observation.facetProfileKey]
                val sourceKey = observation.source.stableKey.trim().lowercase(Locale.ROOT)
                val tasks = taskCatalog.forStableKey(sourceKey)
                val reasons = linkedSetOf<String>()
                val safeIdentity = observation.classificationAuthority == StimulusClassificationAuthority.REVIEWED_CANONICAL &&
                    profile?.integrity == StimulusFacetIntegrity.CONSISTENT &&
                    profile.stableKey.equals(observation.source.stableKey, ignoreCase = true) &&
                    observation.activityKind !in setOf(PlannedActivityKind.GENERIC_COURT_SESSION, PlannedActivityKind.OTHER)
                if (!safeIdentity) reasons += "REVIEWED_EXACT_CANONICAL_PROFILE_UNAVAILABLE"
                val muscles = if (safeIdentity) musclesByKey[sourceKey].orEmpty() else emptyList()
                val primary = muscles.filter { it.relationType == "PRIMARY" }
                    .mapTo(linkedSetOf()) { it.relationValue }
                val secondary = muscles.filter { it.relationType == "SECONDARY" }
                    .mapTo(linkedSetOf()) { it.relationValue }
                val overlapping = primary.intersect(secondary)
                if (overlapping.isNotEmpty()) reasons += "MUSCLE_RELATION_ROLE_CONFLICT"
                val usablePrimary = primary - overlapping
                val usableSecondary = secondary - overlapping
                val realization = observation.realizedStimulusClassification
                val strengthEligible = CanonicalStrengthExposureCapability.strengthPossible(sourceKey)
                val strength = when {
                    !safeIdentity -> BStimulusEvidenceState.UNRESOLVED
                    !strengthEligible -> BStimulusEvidenceState.NOT_APPLICABLE
                    else -> when (qualityObservationDisposition(TrainableQuality.STRENGTH, observation, true)) {
                        QualityObservationDisposition.COMPATIBLE_REALIZATION ->
                            if (realization.kind == RealizedStimulusKind.STRENGTH_LIKE) BStimulusEvidenceState.REALIZED_DIRECT
                            else BStimulusEvidenceState.REVIEWED_NON_REALIZATION
                        QualityObservationDisposition.UNCLASSIFIED -> BStimulusEvidenceState.UNRESOLVED
                        else -> BStimulusEvidenceState.REVIEWED_NON_REALIZATION
                    }
                }
                val hypertrophyCapability = profile?.physicalQualities?.any {
                    it.qualityId == TrainableQuality.HYPERTROPHY &&
                        it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                } == true
                val hypertrophy = when {
                    !safeIdentity -> BStimulusEvidenceState.UNRESOLVED
                    !hypertrophyCapability -> BStimulusEvidenceState.NOT_APPLICABLE
                    usablePrimary.isEmpty() -> {
                        reasons += "DIRECT_HYPERTROPHY_PRIMARY_MUSCLE_AUTHORITY_UNAVAILABLE"
                        BStimulusEvidenceState.UNRESOLVED
                    }
                    else -> when (qualityObservationDisposition(TrainableQuality.HYPERTROPHY, observation, true)) {
                        QualityObservationDisposition.COMPATIBLE_REALIZATION ->
                            if (realization.kind == RealizedStimulusKind.HYPERTROPHY_LIKE) BStimulusEvidenceState.REALIZED_DIRECT
                            else BStimulusEvidenceState.REVIEWED_NON_REALIZATION
                        QualityObservationDisposition.UNCLASSIFIED -> BStimulusEvidenceState.UNRESOLVED
                        else -> BStimulusEvidenceState.REVIEWED_NON_REALIZATION
                    }
                }
                val reviewedCore = tasks.filter {
                    it.domain == BBalancedTaskDomain.CORE && it.review == BBalancedTaskReview.REVIEWED
                }
                val reviewedMovement = tasks.filter {
                    it.domain == BBalancedTaskDomain.MOVEMENT && it.review == BBalancedTaskReview.REVIEWED
                }
                val coreIsDirect = profile?.coreProfile?.coreClass == CoreClass.DIRECT &&
                    profile.coreProfile.directTarget != null
                val core = when {
                    !safeIdentity -> BStimulusEvidenceState.UNRESOLVED
                    !coreIsDirect -> BStimulusEvidenceState.NOT_APPLICABLE
                    observation.reps <= 0 && observation.seconds <= 0 -> {
                        reasons += "DIRECT_CORE_RECORDING_UNIT_UNAVAILABLE"
                        BStimulusEvidenceState.UNRESOLVED
                    }
                    reviewedCore.isEmpty() -> {
                        reasons += "DIRECT_CORE_TASK_SPECIFICITY_UNAVAILABLE"
                        // Retain the reviewed DIRECT superclass as an observed set, not a numeric B task.
                        BStimulusEvidenceState.OBSERVED_DIRECT_CORE
                    }
                    else -> BStimulusEvidenceState.OBSERVED_DIRECT_CORE
                }
                if (reviewedCore.isNotEmpty() && !coreIsDirect) reasons += "CORE_TASK_CANNOT_PROMOTE_NON_DIRECT_PROFILE"
                val pending = if (safeIdentity) tasks.filter { it.review == BBalancedTaskReview.REVIEW_REQUIRED }
                    .mapTo(linkedSetOf()) { "${it.domain.name}:${it.task}" } else emptySet()
                BStimulusSetAttribution(
                    source = observation.source,
                    strength = strength,
                    strengthAnchor = if (strength == BStimulusEvidenceState.REALIZED_DIRECT)
                        CanonicalStrengthExposureCapability.movementAnchor(sourceKey) else null,
                    hypertrophy = hypertrophy,
                    directHypertrophyMuscles = if (hypertrophy == BStimulusEvidenceState.REALIZED_DIRECT)
                        usablePrimary else emptySet(),
                    unresolvedHypertrophyMuscles = if (hypertrophy == BStimulusEvidenceState.UNRESOLVED)
                        usablePrimary else emptySet(),
                    indirectMuscleParticipation = usableSecondary,
                    core = core,
                    reviewedCoreTasks = if (core == BStimulusEvidenceState.OBSERVED_DIRECT_CORE)
                        reviewedCore.mapTo(linkedSetOf()) { it.task } else emptySet(),
                    reviewedMovementTasks = if (safeIdentity)
                        reviewedMovement.mapTo(linkedSetOf()) { it.task } else emptySet(),
                    reviewRequiredTasks = pending,
                    reasons = reasons
                )
            }
            .sortedWith(compareBy({ it.source.date }, { it.source.sessionStableKey }, { it.sourceIdentity }))
            .toList()
        require(result.map { it.sourceIdentity }.distinct().size == result.size) {
            "Duplicate source physical set identity in B attribution"
        }
        return BBalancedStimulusLedgerV1(cutoff, result)
    }
}
