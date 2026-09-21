package com.training.trackplanner.data.personalized

/**
 * Request-scoped planner measurements. These counters are observation-only: no
 * placement, ranking, prescription, OFI, tissue, or repair decision reads them.
 */
internal class PlannerPerformanceMetrics {
    var fundingFitsTrials: Int = 0
    var weeklyPlacementCalls: Int = 0
    var placementAtomEvaluations: Int = 0
    var candidateDayChecks: Int = 0
    var initialPlacementSearchNodes: Int = 0
    var initialPlacementLegalChecks: Int = 0
    var dayProjectionCalls: Int = 0
    /** Candidate-specific canonical day projection gates; baseline/metric projections are excluded. */
    var candidateDayProjectionChecks: Int = 0
    var weekTissueProjectionCalls: Int = 0

    var conditionalSplitParents: Int = 0
    var fullTrials: Int = 0
    var splitTrials: Int = 0
    var mandatorySplitCandidates: Int = 0
    var mandatorySplitDayProjectionCalls: Int = 0
    var mandatorySplitTissueCalls: Int = 0

    var rebalanceCandidates: Int = 0
    var postSplitCandidates: Int = 0
    var acceptedMoves: Int = 0
    var dayProjectionCacheHits: Int = 0
    var dayProjectionCacheMisses: Int = 0
    var tissueProjectionCacheHits: Int = 0
    var tissueProjectionCacheMisses: Int = 0

    fun reset() {
        fundingFitsTrials = 0
        weeklyPlacementCalls = 0
        placementAtomEvaluations = 0
        candidateDayChecks = 0
        initialPlacementSearchNodes = 0
        initialPlacementLegalChecks = 0
        dayProjectionCalls = 0
        candidateDayProjectionChecks = 0
        weekTissueProjectionCalls = 0
        conditionalSplitParents = 0
        fullTrials = 0
        splitTrials = 0
        mandatorySplitCandidates = 0
        mandatorySplitDayProjectionCalls = 0
        mandatorySplitTissueCalls = 0
        rebalanceCandidates = 0
        postSplitCandidates = 0
        acceptedMoves = 0
        dayProjectionCacheHits = 0
        dayProjectionCacheMisses = 0
        tissueProjectionCacheHits = 0
        tissueProjectionCacheMisses = 0
    }

    fun asMap(): Map<String, Int> = linkedMapOf(
        "fundingFitsTrials" to fundingFitsTrials,
        "weeklyPlacementCalls" to weeklyPlacementCalls,
        "placementAtomEvaluations" to placementAtomEvaluations,
        "candidateDayChecks" to candidateDayChecks,
        "initialPlacementSearchNodes" to initialPlacementSearchNodes,
        "initialPlacementLegalChecks" to initialPlacementLegalChecks,
        "dayProjectionCalls" to dayProjectionCalls,
        "candidateDayProjectionChecks" to candidateDayProjectionChecks,
        "weekTissueProjectionCalls" to weekTissueProjectionCalls,
        "conditionalSplitParents" to conditionalSplitParents,
        "fullTrials" to fullTrials,
        "splitTrials" to splitTrials,
        "mandatorySplitCandidates" to mandatorySplitCandidates,
        "mandatorySplitDayProjectionCalls" to mandatorySplitDayProjectionCalls,
        "mandatorySplitTissueCalls" to mandatorySplitTissueCalls,
        "rebalanceCandidates" to rebalanceCandidates,
        "postSplitCandidates" to postSplitCandidates,
        "acceptedMoves" to acceptedMoves,
        "dayProjectionCacheHits" to dayProjectionCacheHits,
        "dayProjectionCacheMisses" to dayProjectionCacheMisses,
        "tissueProjectionCacheHits" to tissueProjectionCacheHits,
        "tissueProjectionCacheMisses" to tissueProjectionCacheMisses,
    )
}

/**
 * Generation-scoped immutable metadata used by every placement trial. It is
 * deliberately limited to cheap structural facts; canonical OFI/tissue
 * projections stay outside the context and remain final validation gates.
 */
internal class PlacementContext(
    val snapshot: PlanningHistorySnapshot,
    val state: AthletePlanningState?,
    val days: Int,
    val sessionMinutes: Int,
) {
    val sessionSeconds: Int = sessionMinutes * 60
    val actualDays: List<Int> = RecordBasedReviewedPolicy.defaultSchedule(1, days).getValue(1).sorted()
    private val lowerStressByKey = mutableMapOf<String, Boolean>()
    private val protectedPrimaryByKey = mutableMapOf<Pair<String, Boolean>, Boolean>()

    fun lowerStress(stableKey: String): Boolean = lowerStressByKey.getOrPut(stableKey) {
        snapshot.movementCoverage(stableKey) in setOf(
            MovementCoverage.LOWER_KNEE,
            MovementCoverage.POSTERIOR_CHAIN,
            MovementCoverage.CALVES,
        ) || snapshot.metadata[stableKey]?.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
    }

    fun protectedPrimary(stableKey: String, continuity: Boolean): Boolean = protectedPrimaryByKey.getOrPut(stableKey to continuity) {
        state?.let { current ->
            PrimaryStrengthAnchorSpacingPolicy.protects(snapshot, current, stableKey, continuity)
        } ?: false
    }

    fun atom(row: TimedPlannedExercise, isMain: Boolean): PlacementAtomContext = PlacementAtomContext(
        stableKey = row.item.stableKey,
        estimatedSeconds = row.estimatedSeconds,
        priority = row.item.priority,
        scheduleTier = row.item.scheduleTier(),
        lowerStress = lowerStress(row.item.stableKey),
        primary = StrengthPrimaryMainPolicy.isPrimary(row.item.stableKey),
        main = isMain,
        protectedPrimary = protectedPrimary(row.item.stableKey, isMain),
    )
}

internal data class PlacementAtomContext(
    val stableKey: String,
    val estimatedSeconds: Int,
    val priority: Int,
    val scheduleTier: ScheduleTier,
    val lowerStress: Boolean,
    val primary: Boolean,
    val main: Boolean,
    val protectedPrimary: Boolean,
)
