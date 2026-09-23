package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceLoadResolver
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Technical integrity state for a resolved canonical facet profile. */
enum class StimulusFacetIntegrity { CONSISTENT, PARTIAL, CONFLICT }

data class StimulusResolutionIssue(
    val code: String,
    val message: String,
    val source: String = "CANONICAL"
)

/** One stable-key-level view of the existing canonical authorities. */
data class CanonicalStimulusFacetProfile(
    val stableKey: String,
    val physicalQualities: List<ExercisePhysicalQualityRelation> = emptyList(),
    val movementPatterns: List<CanonicalMetadataRelation> = emptyList(),
    val movementEvents: List<CanonicalMetadataRelation> = emptyList(),
    val intrinsicLaterality: String? = null,
    val coreProfile: com.training.trackplanner.analysis.core.CanonicalCoreProfile? = null,
    val badmintonObjectives: List<com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation> = emptyList(),
    val integrity: StimulusFacetIntegrity = StimulusFacetIntegrity.PARTIAL,
    val issues: List<StimulusResolutionIssue> = emptyList()
) {
    val hasCanonicalFacet: Boolean
        get() = physicalQualities.isNotEmpty() || movementPatterns.isNotEmpty() || movementEvents.isNotEmpty() ||
            intrinsicLaterality != null || coreProfile != null || badmintonObjectives.isNotEmpty()
}

data class StimulusSourceRef(
    val entryId: Long,
    val entrySourceId: String?,
    val setId: Long?,
    val setIndex: Int?,
    val sessionStableKey: String,
    val date: LocalDate,
    val stableKey: String
) {
    /** Stable portable trace identity when a backup source identity exists. */
    val portableObservationId: String
        get() = entrySourceId?.let { source -> "$source:${setIndex ?: "entry"}" }
            ?: "local:$entryId:${setId ?: setIndex ?: "entry"}"

    val sourceObservationId: String get() = portableObservationId
}

data class StimulusSetObservation(
    val source: StimulusSourceRef,
    val activityKind: PlannedActivityKind,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val rpe: Double?,
    val realizedPrescriptionClass: RealizedStimulusClass,
    val facetProfileKey: String,
    val classificationAuthority: StimulusClassificationAuthority,
    /** B6 reviewed realization authority. Defaults preserve older shadow fixtures only. */
    val realizedStimulusClassification: RealizedStimulusClassification =
        legacyClassification(realizedPrescriptionClass, classificationAuthority)
) {
    val sourceRef: StimulusSourceRef get() = source
}

data class CourtExposureObservation(
    val source: StimulusSourceRef,
    val durationMinutes: Double,
    val effectiveRpe: Double?,
    val practiceLoad: Double,
    val facetProfileKey: String
) {
    val sourceRef: StimulusSourceRef get() = source
}

data class StimulusFacetFilter(
    val quality: TrainableQuality? = null,
    val acceptedRegions: Set<PhysicalQualityRegion> = emptySet(),
    val acceptedModes: Set<PhysicalQualityMode> = emptySet(),
    val laterality: Set<String> = emptySet(),
    val movementPatterns: Set<String> = emptySet(),
    val movementEvents: Set<String> = emptySet(),
    val badmintonObjectives: Set<BadmintonObjective> = emptySet(),
    val activityKinds: Set<PlannedActivityKind> = emptySet()
) {
    private val normalizedLaterality = laterality.mapTo(mutableSetOf()) { it.uppercase(Locale.ROOT) }
    private val normalizedPatterns = movementPatterns.mapTo(mutableSetOf()) { it.uppercase(Locale.ROOT) }
    private val normalizedEvents = movementEvents.mapTo(mutableSetOf()) { it.uppercase(Locale.ROOT) }

    internal fun matches(profile: CanonicalStimulusFacetProfile, activityKind: PlannedActivityKind): Boolean {
        if (activityKinds.isNotEmpty() && activityKind !in activityKinds) return false
        val qualityRelations = profile.physicalQualities
        if ((quality != null || acceptedRegions.isNotEmpty() || acceptedModes.isNotEmpty()) &&
            qualityRelations.none(::matchesPhysicalRelation)
        ) return false
        if (normalizedLaterality.isNotEmpty() && profile.intrinsicLaterality?.uppercase(Locale.ROOT) !in normalizedLaterality) return false
        if (normalizedPatterns.isNotEmpty() && profile.movementPatterns.none { it.relationValue.uppercase(Locale.ROOT) in normalizedPatterns }) return false
        if (normalizedEvents.isNotEmpty() && profile.movementEvents.none { it.relationValue.uppercase(Locale.ROOT) in normalizedEvents }) return false
        if (badmintonObjectives.isNotEmpty() && profile.badmintonObjectives.none { it.objective in badmintonObjectives }) return false
        return true
    }

    internal fun matchesPhysicalRelation(relation: ExercisePhysicalQualityRelation): Boolean =
        (quality == null || relation.qualityId == quality) &&
            (acceptedRegions.isEmpty() || relation.regionQualifier in acceptedRegions) &&
            (acceptedModes.isEmpty() || relation.modeQualifier in acceptedModes)
}

enum class StimulusExposureWindow(val minimumAgeDays: Int, val maximumAgeDays: Int) {
    RECENT_7D(0, 6),
    CURRENT_28D(0, 27),
    PRIOR_28D(28, 55),
    CONTEXT_56D(0, 55)
}

data class StimulusExposureSummary(
    val confirmedSets: Int,
    val sessions: Int,
    val trainingDays: Int,
    val activeCutoffRelativeBins: Set<Int>,
    val directCapabilitySets: Int,
    val supportiveCapabilitySets: Int,
    val provisionalStrengthLikeSets: Int,
    val provisionalHypertrophyLikeSets: Int,
    val ambiguousPrescriptionSets: Int,
    val durationMinutes: Double = 0.0,
    val practiceLoad: Double = 0.0,
    val reviewedNonRealizationSets: Int = 0,
    val unclassifiedSourceSets: Int = 0
)

/**
 * Transient, shadow-only exposure evidence. Each underlying confirmed set is one observation;
 * a profile can satisfy several facet queries without creating additional source observations.
 * The bounded ledger is intentionally query-fold based: build is O(records), query is
 * O(observations), and memory is O(observations + distinct stable keys). There is no universal
 * cross-stimulus unit or total because resistance, structured work, and court exposure remain
 * separate channels.
 */
data class StimulusExposureLedger(
    val facetProfilesByStableKey: Map<String, CanonicalStimulusFacetProfile>,
    val setObservations: List<StimulusSetObservation>,
    val courtObservations: List<CourtExposureObservation>,
    val cutoff: LocalDate? = null,
    /** Transient source coverage start; B1 queries continue to use cutoff-relative windows. */
    val historyStart: LocalDate? = null,
    val reviewedCanonicalStableKeys: Set<String> = emptySet()
) {
    companion object {
        val EMPTY = StimulusExposureLedger(emptyMap(), emptyList(), emptyList())
    }

    fun matchingSetObservations(
        filter: StimulusFacetFilter = StimulusFacetFilter(),
        window: StimulusExposureWindow = StimulusExposureWindow.CONTEXT_56D
    ): List<StimulusSetObservation> = setObservations.filter { observation ->
        age(observation.source.date) in window.minimumAgeDays..window.maximumAgeDays &&
            facetProfilesByStableKey[observation.facetProfileKey]?.let { filter.matches(it, observation.activityKind) } == true
    }

    fun query(
        filter: StimulusFacetFilter = StimulusFacetFilter(),
        window: StimulusExposureWindow = StimulusExposureWindow.CONTEXT_56D
    ): List<StimulusSetObservation> = matchingSetObservations(filter, window)

    fun matchingCourtObservations(
        filter: StimulusFacetFilter = StimulusFacetFilter(),
        window: StimulusExposureWindow = StimulusExposureWindow.CONTEXT_56D
    ): List<CourtExposureObservation> = courtObservations.filter { observation ->
        age(observation.source.date) in window.minimumAgeDays..window.maximumAgeDays &&
            facetProfilesByStableKey[observation.facetProfileKey]?.let {
                filter.matches(it, PlannedActivityKind.GENERIC_COURT_SESSION)
            } == true
    }

    fun summary(
        filter: StimulusFacetFilter = StimulusFacetFilter(),
        window: StimulusExposureWindow = StimulusExposureWindow.CONTEXT_56D
    ): StimulusExposureSummary {
        var confirmedSets = 0
        var direct = 0
        var supportive = 0
        var strengthLike = 0
        var hypertrophyLike = 0
        var ambiguous = 0
        var reviewedNonRealization = 0
        var unclassifiedSource = 0
        val sessionOccurrences = HashSet<Pair<LocalDate, String>>()
        val trainingDays = HashSet<LocalDate>()
        val activeBins = HashSet<Int>()
        for (observation in setObservations) {
            val age = age(observation.source.date)
            if (age !in window.minimumAgeDays..window.maximumAgeDays) continue
            val profile = facetProfilesByStableKey[observation.facetProfileKey] ?: continue
            if (!filter.matches(profile, observation.activityKind)) continue
            confirmedSets++
            if (observation.classificationAuthority == StimulusClassificationAuthority.UNCLASSIFIED) unclassifiedSource++
            if (observation.realizedStimulusClassification.status == RealizedStimulusStatus.REVIEWED_NON_REALIZATION) reviewedNonRealization++
            sessionOccurrences += observation.source.date to observation.source.sessionStableKey
            trainingDays += observation.source.date
            activeBins += age / 7
            val relations = profile.physicalQualities
            if (relations.any { it.relationLevel.name == "DIRECT_CAPABILITY" && filter.matchesPhysicalRelation(it) }) direct++
            if (relations.any { it.relationLevel.name == "SUPPORTIVE_CAPABILITY" && filter.matchesPhysicalRelation(it) }) supportive++
            when (observation.realizedStimulusClassification.kind) {
                RealizedStimulusKind.STRENGTH_LIKE -> strengthLike++
                RealizedStimulusKind.HYPERTROPHY_LIKE -> hypertrophyLike++
                RealizedStimulusKind.NONE -> ambiguous++
            }
        }
        var durationMinutes = 0.0
        var practiceLoad = 0.0
        for (observation in courtObservations) {
            val age = age(observation.source.date)
            if (age !in window.minimumAgeDays..window.maximumAgeDays) continue
            val profile = facetProfilesByStableKey[observation.facetProfileKey] ?: continue
            if (!filter.matches(profile, PlannedActivityKind.GENERIC_COURT_SESSION)) continue
            sessionOccurrences += observation.source.date to observation.source.sessionStableKey
            trainingDays += observation.source.date
            activeBins += age / 7
            durationMinutes += observation.durationMinutes
            practiceLoad += observation.practiceLoad
        }
        return StimulusExposureSummary(
            confirmedSets = confirmedSets,
            sessions = sessionOccurrences.size,
            trainingDays = trainingDays.size,
            // Bin index 0 is cutoff-relative days 0..6, 1 is 7..13, and so on.
            activeCutoffRelativeBins = activeBins,
            directCapabilitySets = direct,
            supportiveCapabilitySets = supportive,
            provisionalStrengthLikeSets = strengthLike,
            provisionalHypertrophyLikeSets = hypertrophyLike,
            ambiguousPrescriptionSets = ambiguous,
            durationMinutes = durationMinutes,
            practiceLoad = practiceLoad,
            reviewedNonRealizationSets = reviewedNonRealization,
            unclassifiedSourceSets = unclassifiedSource
        )
    }

    private fun age(date: LocalDate): Int = ChronoUnit.DAYS.between(date, cutoff ?: date).toInt()
}

/** Builds the Phase A bounded ledger from already loaded workout history. */
class StimulusExposureLedgerBuilder(
    private val activityDomainResolver: PlannerActivityDomainResolver = PlannerActivityDomainResolver(),
    private val practiceLoadCalculatorFactory: (RuntimeExerciseMetadataCatalog) -> BadmintonPracticeLoadCalculator = ::BadmintonPracticeLoadCalculator,
    private val strengthLoadResolver: StrengthPerformanceLoadResolver? = null,
    private val strengthPerformanceRegistry: StrengthPerformanceRegistry? = null,
    private val strengthReferenceIndex: CanonicalStrengthReferenceIndex = CanonicalStrengthReferenceIndex(emptyList())
) {
    fun build(
        cutoff: LocalDate,
        history: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        metadata: Map<String, RuntimeExerciseMetadata>,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        movementRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        exerciseRoleCatalog: ExerciseRoleRelationCatalog = ExerciseRoleRelationCatalog.EMPTY,
        historyStart: LocalDate? = null,
        reviewedCanonicalStableKeys: Set<String> = emptySet(),
        strengthPerformanceHistory: List<StrengthExercisePerformanceHistoryEntity> = emptyList(),
        reviewedNonRealizationSetIds: Set<Long> = emptySet()
    ): StimulusExposureLedger = build(
        cutoff = cutoff,
        history = history,
        exercises = exercises.associateBy(Exercise::stableKey),
        metadata = metadata,
        physicalQualityCatalog = physicalQualityCatalog,
        movementRelations = movementRelations,
        coreCatalog = coreCatalog,
        badmintonCatalog = badmintonCatalog,
        exerciseRoleCatalog = exerciseRoleCatalog,
        historyStart = historyStart,
        reviewedCanonicalStableKeys = reviewedCanonicalStableKeys,
        strengthPerformanceHistory = strengthPerformanceHistory,
        reviewedNonRealizationSetIds = reviewedNonRealizationSetIds
    )

    fun build(
        cutoff: LocalDate,
        history: List<WorkoutEntryWithSets>,
        exercises: Map<String, Exercise>,
        metadata: Map<String, RuntimeExerciseMetadata>,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        movementRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        exerciseRoleCatalog: ExerciseRoleRelationCatalog = ExerciseRoleRelationCatalog.EMPTY,
        historyStart: LocalDate? = null,
        reviewedCanonicalStableKeys: Set<String> = emptySet(),
        strengthPerformanceHistory: List<StrengthExercisePerformanceHistoryEntity> = emptyList(),
        reviewedNonRealizationSetIds: Set<Long> = emptySet()
    ): StimulusExposureLedger {
        val referenceIndex = if (strengthPerformanceHistory.isEmpty()) strengthReferenceIndex
        else CanonicalStrengthReferenceIndex(strengthPerformanceHistory)
        val b6ClassifierEnabled = strengthLoadResolver != null || strengthPerformanceHistory.isNotEmpty()
        val windowStart = (historyStart ?: cutoff.minusDays(55)).coerceAtMost(cutoff)
        val boundedHistory = history.mapNotNull { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
            if (date == null || date.isBefore(windowStart) || date.isAfter(cutoff)) null else date to record
        }
        val exerciseMap = exercises
        val explicitReviewedKeys = reviewedCanonicalStableKeys.mapTo(linkedSetOf()) { it.trim().lowercase(Locale.ROOT) }
        // Direct builder callers from older shadow tests may not yet have the repository set.
        // Relation-bearing keys are a conservative compatibility fallback; production passes
        // the complete repository identity set, including history-only identities.
        val fallbackReviewedKeys = buildSet {
            addAll(physicalQualityCatalog.allRelations().map { it.exerciseStableKey.lowercase(Locale.ROOT) })
            addAll(movementRelations.flatMap { listOf(it.exerciseStableKey, it.sourceStableKey) }
                .filter(String::isNotBlank).map { it.lowercase(Locale.ROOT) })
            addAll(badmintonCatalog.allRelations().map { it.exerciseStableKey.lowercase(Locale.ROOT) })
        }
        val reviewedKeys = if (explicitReviewedKeys.isNotEmpty()) explicitReviewedKeys else fallbackReviewedKeys
        val functionalMovementByStableKey = movementRelations.asSequence()
            .filter { relation ->
                relation.domain == CanonicalRelationDomain.MOVEMENT &&
                    relation.relationType in setOf("MOVEMENT_PATTERN", "MOVEMENT_EVENT")
            }
            .flatMap { relation ->
                sequenceOf(relation.exerciseStableKey, relation.sourceStableKey)
                    .filter(String::isNotBlank)
                    .distinct()
                    .map { key -> key.lowercase(Locale.ROOT) to relation }
            }
            .groupBy({ it.first }, { it.second })
        val profiles = linkedMapOf<String, CanonicalStimulusFacetProfile>()
        val resolver = { stableKey: String ->
            profiles.getOrPut(stableKey) {
                profileFor(
                    stableKey,
                    exerciseMap[stableKey],
                    physicalQualityCatalog,
                    functionalMovementByStableKey[stableKey.lowercase(Locale.ROOT)].orEmpty(),
                    coreCatalog,
                    badmintonCatalog
                )
            }
        }
        // Resolve once per stable key in the bounded source history; observations only retain the key.
        boundedHistory.forEach { (_, record) -> resolver(record.entry.exerciseStableKey) }
        val runtimeCatalog = RuntimeExerciseMetadataCatalog.of(metadata.values)
        val practiceCalculator = practiceLoadCalculatorFactory(runtimeCatalog)
        val sets = ArrayList<StimulusSetObservation>()
        val courts = ArrayList<CourtExposureObservation>()
        boundedHistory.forEach { (date, record) ->
            val stableKey = record.entry.exerciseStableKey
            val profile = resolver(stableKey)
            val activity = activityDomainResolver.resolve(
                exercise = exerciseMap[stableKey],
                metadata = metadata[stableKey],
                exerciseRoleCatalog = exerciseRoleCatalog,
                supportiveObjectives = badmintonCatalog.relations(stableKey)
                    .filter { it.transferLevel.name == "SUPPORTIVE" }
                    .mapTo(mutableSetOf()) { it.objective.name }
            )
            if (activity == PlannedActivityKind.GENERIC_COURT_SESSION) {
                val confirmed = record.sets.filter { it.confirmed }
                val duration = confirmed.sumOf { it.seconds.coerceAtLeast(0) } / 60.0
                if (duration > 0.0) {
                    val effectiveRpe = confirmed.mapNotNull { it.rpe }.takeIf(List<Double>::isNotEmpty)?.average() ?: record.entry.rpe
                    val contribution = practiceCalculator.entryContributions(listOf(record), exerciseMap).singleOrNull()
                    courts += CourtExposureObservation(
                        source = StimulusSourceRef(record.entry.id, record.entry.backupSourceId, null, null, record.entry.sessionStableKey, date, stableKey),
                        durationMinutes = duration,
                        effectiveRpe = effectiveRpe,
                        practiceLoad = contribution?.practiceLoad ?: 0.0,
                        facetProfileKey = profile.stableKey
                    )
                }
                return@forEach
            }
            val authority = if (stableKey.lowercase(Locale.ROOT) in reviewedKeys) {
                StimulusClassificationAuthority.REVIEWED_CANONICAL
            } else {
                StimulusClassificationAuthority.UNCLASSIFIED
            }
            if (activity == PlannedActivityKind.OTHER && stableKey !in exerciseMap && stableKey !in metadata) return@forEach
            // Preserve confirmed non-court source observations even when no reviewed facet exists.
            // They remain outside canonical direct/supportive counts through the explicit authority.
            record.sets.filter { it.confirmed }.forEach { set ->
                val directQualities = profile.physicalQualities
                    .filter { it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY }
                    .mapTo(linkedSetOf()) { it.qualityId }
                val target = strengthPerformanceRegistry?.directTarget(stableKey)
                val semantics = target?.loadSemantics ?: com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics.EXTERNAL_LOAD
                val resolvedLoad = strengthLoadResolver?.resolve(date, set, semantics)?.totalLoadKg
                    ?: set.weightKg.takeIf { it.isFinite() && it > 0.0 }
                val reviewedRealization = if (!b6ClassifierEnabled) legacyClassification(
                    provisionalRealizedStimulusClass(set.reps), authority
                ) else RealizedStimulusClassifier.classify(
                    RealizedStimulusInput(
                        stableKey = stableKey,
                        date = date,
                        sessionStableKey = record.entry.sessionStableKey,
                        activityKind = activity,
                        reps = set.reps,
                        resolvedLoadKg = resolvedLoad,
                        rpe = set.rpe ?: record.entry.rpe,
                        directQualities = directQualities,
                        reviewedIdentity = authority == StimulusClassificationAuthority.REVIEWED_CANONICAL,
                        reviewedNonRealization = set.id in reviewedNonRealizationSetIds,
                        reference1RmKg = referenceIndex.reference1RmKg(stableKey, date, record.entry.sessionStableKey),
                        loadSemantics = semantics
                    )
                )
                sets += StimulusSetObservation(
                    source = StimulusSourceRef(record.entry.id, record.entry.backupSourceId, set.id, set.setIndex, record.entry.sessionStableKey, date, stableKey),
                    activityKind = activity,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    seconds = set.seconds,
                    rpe = set.rpe ?: record.entry.rpe,
                    realizedPrescriptionClass = reviewedRealization.toLegacyClass(),
                    facetProfileKey = profile.stableKey,
                    classificationAuthority = authority,
                    realizedStimulusClassification = reviewedRealization
                )
            }
        }
        return StimulusExposureLedger(profiles.toMap(), sets.toList(), courts.toList(), cutoff, windowStart, reviewedKeys)
    }

    private fun profileFor(
        stableKey: String,
        exercise: Exercise?,
        physicalQualityCatalog: CanonicalExercisePhysicalQualityCatalog,
        movementRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog
    ): CanonicalStimulusFacetProfile {
        val physical = physicalQualityCatalog.relations(stableKey)
        val patterns = movementRelations.filter { it.relationType == "MOVEMENT_PATTERN" }
        val events = movementRelations.filter { it.relationType == "MOVEMENT_EVENT" }
        val core = coreCatalog.resolve(stableKey)
        val badminton = badmintonCatalog.relations(stableKey)
        val laterality = exercise?.laterality?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank)
        val issues = mutableListOf<StimulusResolutionIssue>()
        val intrinsicUnilateral = laterality == "UNILATERAL" || laterality == "ALTERNATING" || laterality == "ASYMMETRIC"
        val intrinsicBilateral = laterality == "BILATERAL"
        val approvedUnilateral = physical.any {
            it.modeQualifier == PhysicalQualityMode.UNILATERAL || it.regionQualifier == PhysicalQualityRegion.UNILATERAL_LOWER
        }
        val approvedBilateral = physical.any { it.modeQualifier == PhysicalQualityMode.BILATERAL }
        if ((intrinsicBilateral && approvedUnilateral) || (intrinsicUnilateral && approvedBilateral)) {
            issues += StimulusResolutionIssue(
                code = "INTRINSIC_LATERALITY_CONFLICT",
                message = "Intrinsic laterality $laterality contradicts an approved physical-quality qualifier."
            )
        }
        val hasAuthority = physical.isNotEmpty() || patterns.isNotEmpty() || events.isNotEmpty() || core != null || badminton.isNotEmpty() || laterality != null
        val integrity = when {
            issues.isNotEmpty() -> StimulusFacetIntegrity.CONFLICT
            hasAuthority -> StimulusFacetIntegrity.CONSISTENT
            else -> StimulusFacetIntegrity.PARTIAL
        }
        return CanonicalStimulusFacetProfile(
            stableKey = stableKey,
            physicalQualities = physical,
            movementPatterns = patterns,
            movementEvents = events,
            intrinsicLaterality = laterality,
            coreProfile = core,
            badmintonObjectives = badminton,
            integrity = integrity,
            issues = issues
        )
    }
}
