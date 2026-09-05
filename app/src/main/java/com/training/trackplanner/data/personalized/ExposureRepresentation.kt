package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.core.AnalysisStimulusRpePolicy
import com.training.trackplanner.data.ProgramSlotCapability
import com.training.trackplanner.data.ProgressMetricRuntimeBehavior
import com.training.trackplanner.data.TrainingRole
import java.time.LocalDate

class PlannerActivityDomainResolver {
    fun resolve(snapshot: PlanningHistorySnapshot, stableKey: String): PlannedActivityKind {
        val metadata = snapshot.metadata[stableKey] ?: return PlannedActivityKind.OTHER
        if (metadata.activityKind == "SPORT_SESSION" || snapshot.exercises[stableKey]?.activityKind == "SPORT_SESSION") {
            return PlannedActivityKind.GENERIC_COURT_SESSION
        }
        if (metadata.activityKind != "EXERCISE") return PlannedActivityKind.OTHER

        if (metadata.programSlot == "BADMINTON_FOOTWORK" &&
            "BADMINTON_TRANSFER" in metadata.analysisEligibility &&
            metadata.badmintonTransferLevel == "DIRECT"
        ) return PlannedActivityKind.STRUCTURED_BADMINTON_DRILL

        val roles = snapshot.exerciseRoleCatalog.trainingRoles(stableKey)
        val capabilities = snapshot.exerciseRoleCatalog.programSlotCapabilities(stableKey)
        val strengthAuthority = roles.any { it == TrainingRole.STRENGTH || it == TrainingRole.HYPERTROPHY } ||
            capabilities.any { it == ProgramSlotCapability.MAIN_STRENGTH_SLOT || it == ProgramSlotCapability.SECONDARY_STRENGTH_SLOT || it == ProgramSlotCapability.ACCESSORY_SLOT } ||
            "STRENGTH_PROGRESS" in metadata.analysisEligibility || "HYPERTROPHY_VOLUME" in metadata.analysisEligibility
        val loadBased = metadata.progressBehavior in setOf(
            ProgressMetricRuntimeBehavior.LOAD_REPS,
            ProgressMetricRuntimeBehavior.VOLUME_LOAD,
            ProgressMetricRuntimeBehavior.ESTIMATED_1RM,
            ProgressMetricRuntimeBehavior.REPS_OR_TIME
        )
        val athleticAuthority = roles.any { it == TrainingRole.PLYOMETRIC || it == TrainingRole.SKILL_DRILL || it == TrainingRole.CONDITIONING } ||
            capabilities.any { it == ProgramSlotCapability.PLYOMETRIC_SLOT || it == ProgramSlotCapability.SPEED_REACTIVE_SLOT } ||
            metadata.programSlot in ATHLETIC_PROGRAM_SLOTS || metadata.movementFamily in ATHLETIC_MOVEMENT_FAMILIES
        if (athleticAuthority) return PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL

        val powerAuthority = TrainingRole.POWER in roles || ProgramSlotCapability.POWER_SLOT in capabilities
        if (powerAuthority && !(strengthAuthority && loadBased)) return PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        if (strengthAuthority || loadBased) return PlannedActivityKind.RESISTANCE
        // Explicit objective assistance with stability authority is executable performance work,
        // not a resistance set or a DIRECT objective relation.
        if (snapshot.badmintonSupportiveObjectives[stableKey].orEmpty().isNotEmpty() &&
            (TrainingRole.STABILITY in roles || ProgramSlotCapability.STABILITY_SLOT in capabilities ||
                metadata.programSlot in setOf("CORE_STABILITY", "TRUNK_ANTI_ROTATION_STABILITY") ||
                "BADMINTON_SUPPORTIVE" in metadata.analysisEligibility)
        ) return PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
        return PlannedActivityKind.OTHER
    }

    private companion object {
        val ATHLETIC_PROGRAM_SLOTS = setOf(
            "PLYOMETRIC_POWER", "ANKLE_SSC_CONDITIONING", "DECELERATION_LANDING",
            "SPEED_REACTIVE", "RUNNING_MECHANICS_SUPPORT", "ROTATIONAL_KINETIC_CHAIN"
        )
        val ATHLETIC_MOVEMENT_FAMILIES = setOf(
            "PLYOMETRIC_POWER", "ANKLE_SSC_CONDITIONING", "DECELERATION_LANDING",
            "PLYOMETRIC_JUMP_VARIANTS", "ANKLE_STIFFNESS_SSC_CONDITIONING",
            "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "LATERAL_BOUND_CONTINUOUS_VARIANTS",
            "JUMP_LUNGE_PLYOMETRIC"
        )
    }
}

object ExposureRepresentationPolicy {
    const val SEVERE_RATIO = .25
    const val CLEAR_RATIO = .50

    fun confidence(activeBins: Int): PlanningConfidence = when (activeBins) {
        4 -> PlanningConfidence.HIGH
        in 2..3 -> PlanningConfidence.MODERATE
        else -> PlanningConfidence.LOW
    }

    fun movementState(current: Double, confidence: PlanningConfidence, peer: Double?, personal: Double?): RepresentationState = when {
        current == 0.0 -> RepresentationState.ABSENT
        confidence == PlanningConfidence.LOW -> RepresentationState.UNKNOWN
        peer != null && peer <= SEVERE_RATIO -> RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL
        personal != null && personal <= SEVERE_RATIO -> RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL
        peer != null && personal != null && peer <= CLEAR_RATIO && personal <= CLEAR_RATIO -> RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL
        peer != null && peer <= CLEAR_RATIO -> RepresentationState.UNDERREPRESENTATION_SIGNAL
        personal != null && personal <= CLEAR_RATIO -> RepresentationState.UNDERREPRESENTATION_SIGNAL
        peer != null || personal != null -> RepresentationState.NO_CLEAR_DEFICIT_SIGNAL
        else -> RepresentationState.UNKNOWN
    }

    fun badmintonState(current: Double, confidence: PlanningConfidence, peer: Double?, personal: Double?): RepresentationState = when {
        current == 0.0 -> RepresentationState.ABSENT
        confidence == PlanningConfidence.LOW -> RepresentationState.UNKNOWN
        personal != null && personal <= SEVERE_RATIO -> RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL
        personal != null && personal <= CLEAR_RATIO && peer != null && peer <= SEVERE_RATIO -> RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL
        personal != null && personal <= CLEAR_RATIO -> RepresentationState.UNDERREPRESENTATION_SIGNAL
        personal == null && confidence == PlanningConfidence.HIGH && peer != null && peer <= SEVERE_RATIO -> RepresentationState.UNDERREPRESENTATION_SIGNAL
        personal != null || peer != null -> RepresentationState.NO_CLEAR_DEFICIT_SIGNAL
        else -> RepresentationState.UNKNOWN
    }

    fun movementGapPriority(base: RepresentationPriority, state: RepresentationState, confidence: PlanningConfidence): String? = when (base) {
        RepresentationPriority.HIGH -> when (state) {
            RepresentationState.ABSENT -> if (confidence == PlanningConfidence.LOW) "MODERATE" else "HIGH"
            RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL -> if (confidence == PlanningConfidence.HIGH) "HIGH" else "MODERATE"
            RepresentationState.UNDERREPRESENTATION_SIGNAL -> "MODERATE"
            else -> null
        }
        RepresentationPriority.MODERATE -> when (state) {
            RepresentationState.ABSENT, RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL -> "MODERATE"
            RepresentationState.UNDERREPRESENTATION_SIGNAL -> "LOW"
            else -> null
        }
    }

    fun badmintonGapPriority(
        state: RepresentationState,
        confidence: PlanningConfidence,
        peerOnly: Boolean,
        directDrop: Boolean
    ): String? = when {
        directDrop -> "HIGH"
        state !in setOf(RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL, RepresentationState.UNDERREPRESENTATION_SIGNAL) -> null
        state == RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL && confidence == PlanningConfidence.HIGH && !peerOnly -> "HIGH"
        else -> "MODERATE"
    }
}

class MovementExposureRepresentationAnalyzer {
    fun analyze(snapshot: PlanningHistorySnapshot, hypertrophyOriented: Boolean): List<MovementExposureRepresentation> {
        val required = linkedMapOf(
            "LOWER_KNEE" to RepresentationPriority.HIGH,
            "POSTERIOR_CHAIN" to RepresentationPriority.HIGH,
            "HORIZONTAL_PUSH" to RepresentationPriority.HIGH,
            "UPPER_PULL" to RepresentationPriority.HIGH,
            "CORE_DIRECT" to RepresentationPriority.MODERATE
        )
        if (hypertrophyOriented) required.putAll(mapOf(
            "ARMS_BICEPS" to RepresentationPriority.MODERATE,
            "ARMS_TRICEPS" to RepresentationPriority.MODERATE,
            "CALVES" to RepresentationPriority.MODERATE
        ))
        val current = counts(snapshot, snapshot.cutoff.minusDays(27), snapshot.cutoff)
        val prior = counts(snapshot, snapshot.cutoff.minusDays(55), snapshot.cutoff.minusDays(28))
        val currentTotal = required.keys.sumOf { current[it] ?: 0.0 }
        val priorTotal = required.keys.sumOf { prior[it] ?: 0.0 }
        val currentActiveBins = activeBins(snapshot, snapshot.cutoff, required.keys)
        val confidence = ExposureRepresentationPolicy.confidence(currentActiveBins)
        return required.map { (target, priority) ->
            val currentExposure = current[target] ?: 0.0
            val priorExposure = prior[target] ?: 0.0
            val currentShare = currentExposure.shareOf(currentTotal)
            val priorShare = priorExposure.shareOf(priorTotal)
            val personal = ratio(currentShare, priorShare)
            val peers = required.filterValues { it == priority }.keys
                .filterNot { it == target }
                .map { current[it] ?: 0.0 }
                .filter { it > 0.0 }
            val peerReference = peers.takeIf { it.size >= 2 }?.median()
            val peerRatio = peerReference?.takeIf { it > 0.0 }?.let { currentExposure / it }
            val state = ExposureRepresentationPolicy.movementState(currentExposure, confidence, peerRatio, personal)
            MovementExposureRepresentation(
                movementCoverage = target,
                basePriority = priority,
                currentExposure28d = currentExposure,
                priorExposure28d = priorExposure,
                currentActiveBins = currentActiveBins,
                currentShare = currentShare,
                priorShare = priorShare,
                peerReference = peerReference,
                peerRepresentationRatio = peerRatio,
                personalRetentionRatio = personal,
                representationState = state,
                evidenceConfidence = confidence,
                reasonCodes = reasonCodes(currentExposure, confidence, peerRatio, personal, state)
            )
        }
    }

    private fun counts(snapshot: PlanningHistorySnapshot, start: LocalDate, end: LocalDate): Map<String, Double> =
        snapshot.allConfirmedSets.asSequence()
            .filter { !it.date.isBefore(start) && !it.date.isAfter(end) && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
            .groupingBy { movementDomain(snapshot.movementCoverage(it.stableKey)) }
            .eachCount()
            .mapValues { it.value.toDouble() }

    private fun activeBins(snapshot: PlanningHistorySnapshot, end: LocalDate, required: Set<String>): Int = (0..3).count { index ->
        val binEnd = end.minusDays(index * 7L)
        val binStart = binEnd.minusDays(6)
        snapshot.allConfirmedSets.any {
            !it.date.isBefore(binStart) && !it.date.isAfter(binEnd) &&
                snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE &&
                movementDomain(snapshot.movementCoverage(it.stableKey)) in required
        }
    }

    private fun movementDomain(coverage: MovementCoverage): String = when (coverage) {
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL -> "UPPER_PULL"
        else -> coverage.name
    }

    private fun reasonCodes(current: Double, confidence: PlanningConfidence, peer: Double?, personal: Double?, state: RepresentationState) = buildList {
        if (current == 0.0) add("CURRENT_EXPOSURE_ABSENT")
        if (confidence == PlanningConfidence.LOW) add("SPARSE_CURRENT_WINDOW")
        if (peer != null && peer <= ExposureRepresentationPolicy.SEVERE_RATIO) add("PEER_RATIO_AT_OR_BELOW_SEVERE")
        else if (peer != null && peer <= ExposureRepresentationPolicy.CLEAR_RATIO) add("PEER_RATIO_AT_OR_BELOW_CLEAR")
        if (personal != null && personal <= ExposureRepresentationPolicy.SEVERE_RATIO) add("PERSONAL_RETENTION_AT_OR_BELOW_SEVERE")
        else if (personal != null && personal <= ExposureRepresentationPolicy.CLEAR_RATIO) add("PERSONAL_RETENTION_AT_OR_BELOW_CLEAR")
        if (state == RepresentationState.NO_CLEAR_DEFICIT_SIGNAL) add("NO_CLEAR_DEFICIT_SIGNAL")
        if (peer == null && personal == null && current > 0.0) add("NO_USABLE_COMPARATOR")
    }
}

class BadmintonObjectiveRepresentationAnalyzer {
    fun analyze(snapshot: PlanningHistorySnapshot): List<BadmintonObjectiveRepresentation> {
        val currentRows = rows(snapshot, snapshot.cutoff.minusDays(27), snapshot.cutoff)
        val priorRows = rows(snapshot, snapshot.cutoff.minusDays(55), snapshot.cutoff.minusDays(28))
        val currentWeighted = weighted(snapshot, currentRows)
        val priorWeighted = weighted(snapshot, priorRows)
        val currentDirect = direct(snapshot, currentRows)
        val priorDirect = direct(snapshot, priorRows)
        val currentTotal = currentWeighted.values.sum()
        val priorTotal = priorWeighted.values.sum()
        val activeBins = (0..3).count { index ->
            val end = snapshot.cutoff.minusDays(index * 7L)
            val start = end.minusDays(6)
            weighted(snapshot, currentRows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }).values.any { it > 0.0 }
        }
        val confidence = ExposureRepresentationPolicy.confidence(activeBins)
        return BadmintonObjective.entries.map { objective ->
            val key = objective.name
            val current = currentWeighted.getValue(key)
            val prior = priorWeighted.getValue(key)
            val currentShare = current.shareOf(currentTotal)
            val priorShare = prior.shareOf(priorTotal)
            val personal = ratio(currentShare, priorShare)
            val peers = currentWeighted.filterKeys { it != key }.values.filter { it > 0.0 }
            val peerMedian = peers.takeIf { it.size >= 3 }?.median()
            val peerRatio = peerMedian?.takeIf { it > 0.0 }?.let { current / it }
            val directDrop = priorDirect.getValue(key) > 0.0 && currentDirect.getValue(key) == 0.0
            val neverDirect = priorDirect.getValue(key) == 0.0 && currentDirect.getValue(key) == 0.0
            val state = ExposureRepresentationPolicy.badmintonState(current, confidence, peerRatio, personal)
            BadmintonObjectiveRepresentation(
                objective = key,
                currentWeighted28d = current,
                priorWeighted28d = prior,
                currentDirect28d = currentDirect.getValue(key),
                priorDirect28d = priorDirect.getValue(key),
                currentShare = currentShare,
                priorShare = priorShare,
                personalRetentionRatio = personal,
                peerMedianCurrent = peerMedian,
                peerRepresentationRatio = peerRatio,
                currentActiveBins = activeBins,
                evidenceConfidence = confidence,
                directDrop = directDrop,
                neverDirectObserved = neverDirect,
                representationState = state,
                reasonCodes = buildList {
                    if (directDrop) add("DIRECT_DROP")
                    if (neverDirect) add("NEVER_DIRECT_OBSERVED")
                    if (confidence == PlanningConfidence.LOW) add("SPARSE_CURRENT_WINDOW")
                    if (current > 0.0 && currentDirect.getValue(key) == 0.0) add("WEIGHTED_WITHOUT_DIRECT")
                    if (personal != null && personal <= ExposureRepresentationPolicy.SEVERE_RATIO) add("PERSONAL_RETENTION_AT_OR_BELOW_SEVERE")
                    else if (personal != null && personal <= ExposureRepresentationPolicy.CLEAR_RATIO) add("PERSONAL_RETENTION_AT_OR_BELOW_CLEAR")
                    if (personal == null && peerRatio != null && peerRatio <= ExposureRepresentationPolicy.SEVERE_RATIO) add("PEER_ONLY_UNDERREPRESENTATION")
                    if (state == RepresentationState.NO_CLEAR_DEFICIT_SIGNAL) add("NO_CLEAR_DEFICIT_SIGNAL")
                }
            )
        }
    }

    private fun rows(snapshot: PlanningHistorySnapshot, start: LocalDate, end: LocalDate) = snapshot.allConfirmedSets.filter {
        !it.date.isBefore(start) && !it.date.isAfter(end) && snapshot.activityKind(it.stableKey) != PlannedActivityKind.GENERIC_COURT_SESSION
    }

    private fun weighted(snapshot: PlanningHistorySnapshot, rows: List<PlanningSetRecord>): Map<String, Double> =
        BadmintonObjective.entries.associate { objective ->
            objective.name to rows.sumOf { row ->
                val coefficient = snapshot.badmintonObjectives[row.stableKey]?.get(objective.name) ?: 0.0
                coefficient * AnalysisStimulusRpePolicy.modifier(row.rpe)
            }
        }

    private fun direct(snapshot: PlanningHistorySnapshot, rows: List<PlanningSetRecord>): Map<String, Double> =
        BadmintonObjective.entries.associate { objective ->
            objective.name to rows.sumOf { row ->
                if (objective.name in snapshot.badmintonDirectObjectives[row.stableKey].orEmpty()) AnalysisStimulusRpePolicy.modifier(row.rpe) else 0.0
            }
        }
}

private fun Double.shareOf(total: Double): Double? = if (total > 0.0) this / total else null
private fun ratio(current: Double?, prior: Double?): Double? = if (current != null && prior != null && prior > 0.0) current / prior else null
private fun List<Double>.median(): Double = sorted().let { if (size % 2 == 1) it[size / 2] else (it[size / 2 - 1] + it[size / 2]) / 2.0 }
