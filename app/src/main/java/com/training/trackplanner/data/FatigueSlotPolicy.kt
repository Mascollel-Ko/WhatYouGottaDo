package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import kotlin.math.roundToInt

enum class FatigueSlotGroup {
    STRENGTH_ANCHOR,
    SINGLE_LEG_DECEL,
    FOOTWORK_COD_REACTIVE,
    ATHLETIC_OVERHEAD_PRESS,
    ROTATIONAL_KINETIC_CHAIN,
    TRUNK_STABILITY,
    SCAPULAR_SUPPORT,
    CALF_ANKLE,
    RECOVERY_PREHAB,
    REHAB_LIKE_ACTIVATION,
    ACCESSORY
}

enum class FatigueSlotDisposition {
    NORMAL,
    CONTROLLED,
    LOW_LOAD_ONLY,
    AVOID
}

enum class ProgramFatigueUseCase {
    PROGRAM_PLANNING,
    TODAY_EXECUTION
}

private val HEAVY_LOWER_SLOTS = setOf(
    ProgramSlotId.LOWER_SQUAT_PATTERN,
    ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN
)
private val HIGH_IMPACT_SLOTS = setOf(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME)
private val COD_REACTIVE_SLOTS = setOf(
    ProgramSlotId.BADMINTON_DECEL_COD,
    ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
    ProgramSlotId.POWER_REACTIVE_LOW_VOLUME
)
private val UPPER_PUSH_SLOTS = setOf(ProgramSlotId.UPPER_PUSH_SUPPORT)
private val OVERHEAD_SLOTS = setOf(
    ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
    ProgramSlotId.OVERHEAD_SMASH_SUPPORT
)
private val GRIP_FOREARM_SLOTS = setOf(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT)

internal class FatigueSlotPolicy {
    fun gate(state: DailyFatigueState?): ProgramFatigueGate = ProgramFatigueGate.from(state)

    fun gate(trainingGate: TrainingGateSnapshot?): ProgramFatigueGate = ProgramFatigueGate.from(trainingGate)

    fun adapt(
        planned: PlannedSlot,
        gate: ProgramFatigueGate,
        useCase: ProgramFatigueUseCase = ProgramFatigueUseCase.TODAY_EXECUTION
    ): PlannedSlot = when {
        useCase == ProgramFatigueUseCase.PROGRAM_PLANNING && planned.intensity == ProgramDayIntensity.HARD -> when (gate.band) {
            ProgramFatigueBand.RED -> planned.copy(intensity = ProgramDayIntensity.MODERATE)
            ProgramFatigueBand.ORANGE -> planned.copy(intensity = ProgramDayIntensity.MODERATE)
            else -> planned
        }
        gate.band == ProgramFatigueBand.RED -> planned.copy(intensity = ProgramDayIntensity.LIGHT)
        gate.band == ProgramFatigueBand.ORANGE -> planned.copy(
            intensity = if (planned.intensity == ProgramDayIntensity.HARD) {
                ProgramDayIntensity.MODERATE
            } else {
                planned.intensity
            }
        )
        else -> planned
    }

    fun allows(
        candidate: ProgramCandidate,
        gate: ProgramFatigueGate,
        useCase: ProgramFatigueUseCase = ProgramFatigueUseCase.TODAY_EXECUTION
    ): Boolean {
        if (useCase == ProgramFatigueUseCase.PROGRAM_PLANNING) return allowsForPlanning(candidate, gate)
        if (!gate.allows(candidate)) return false
        val primarySlot = candidate.slotCapabilities.primary.firstOrNull() ?: return true
        return when (disposition(primarySlot, gate.band, candidate.isRehabLikeActivation)) {
            FatigueSlotDisposition.AVOID -> candidate.isRecovery
            FatigueSlotDisposition.LOW_LOAD_ONLY ->
                !candidate.highStress &&
                    !candidate.isHeavyLower &&
                    !candidate.isHighImpact &&
                    !candidate.isHighIntensityCod
            FatigueSlotDisposition.NORMAL,
            FatigueSlotDisposition.CONTROLLED -> true
        }
    }

    private fun allowsForPlanning(candidate: ProgramCandidate, gate: ProgramFatigueGate): Boolean {
        if (gate.band >= ProgramFatigueBand.ORANGE && candidate.matchesExplosivePlanningWork()) {
            return false
        }
        val primarySlot = candidate.slotCapabilities.primary.firstOrNull() ?: return true
        return when (disposition(primarySlot, gate.band, candidate.isRehabLikeActivation)) {
            FatigueSlotDisposition.AVOID -> candidate.isRecovery || candidate.isRehabLikeActivation
            FatigueSlotDisposition.NORMAL,
            FatigueSlotDisposition.CONTROLLED,
            FatigueSlotDisposition.LOW_LOAD_ONLY -> true
        }
    }

    fun adjustTodayItem(
        item: TrainingProgramItem,
        candidate: ProgramCandidate?,
        gate: ProgramFatigueGate
    ): TrainingProgramItem? {
        val action = when {
            candidate == null -> TodayGateAction.KEEP
            !gate.allowsHighImpact && candidate.matchesHighImpactWork() -> TodayGateAction.AVOID
            !gate.allowsHighIntensityCod && candidate.matchesCodReactiveWork() -> TodayGateAction.AVOID
            !gate.allowsOverhead && candidate.matchesOverheadWork() -> TodayGateAction.AVOID
            (!gate.allowsHeavyLower || gate.lowerBodyRestricted) && candidate.matchesHeavyLowerWork() -> TodayGateAction.DOWNGRADE
            !gate.allowsUpperPush && candidate.matchesUpperPushWork() -> TodayGateAction.DOWNGRADE
            !gate.allowsGripForearm && candidate.matchesGripForearmWork() -> TodayGateAction.DOWNGRADE
            else -> TodayGateAction.KEEP
        }
        return when (action) {
            TodayGateAction.AVOID -> null
            TodayGateAction.DOWNGRADE -> item.scaledSets(gate.volumeFactor, forceMinimum = true)
            TodayGateAction.KEEP -> item.scaledSets(gate.volumeFactor, forceMinimum = false)
        }
    }

    fun adjustItemForResolvedDate(
        item: TrainingProgramItem,
        itemDate: String,
        todayDate: String?,
        candidate: ProgramCandidate?,
        gate: ProgramFatigueGate?
    ): TrainingProgramItem? =
        if (todayDate == null || gate == null || itemDate != todayDate) {
            item
        } else {
            adjustTodayItem(item, candidate, gate)
        }

    fun disposition(
        slot: ProgramSlotId,
        band: ProgramFatigueBand,
        rehabLikeActivation: Boolean = false
    ): FatigueSlotDisposition {
        val group = groupFor(slot, rehabLikeActivation)
        return when (band) {
            ProgramFatigueBand.GREEN -> FatigueSlotDisposition.NORMAL
            ProgramFatigueBand.YELLOW -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN,
                FatigueSlotGroup.SINGLE_LEG_DECEL -> FatigueSlotDisposition.CONTROLLED
                else -> FatigueSlotDisposition.NORMAL
            }
            ProgramFatigueBand.ORANGE -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN,
                FatigueSlotGroup.SINGLE_LEG_DECEL -> FatigueSlotDisposition.LOW_LOAD_ONLY
                FatigueSlotGroup.STRENGTH_ANCHOR,
                FatigueSlotGroup.TRUNK_STABILITY,
                FatigueSlotGroup.SCAPULAR_SUPPORT,
                FatigueSlotGroup.CALF_ANKLE,
                FatigueSlotGroup.ACCESSORY -> FatigueSlotDisposition.CONTROLLED
                FatigueSlotGroup.RECOVERY_PREHAB,
                FatigueSlotGroup.REHAB_LIKE_ACTIVATION -> FatigueSlotDisposition.NORMAL
            }
            ProgramFatigueBand.RED -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN -> FatigueSlotDisposition.AVOID
                FatigueSlotGroup.STRENGTH_ANCHOR,
                FatigueSlotGroup.SINGLE_LEG_DECEL,
                FatigueSlotGroup.TRUNK_STABILITY,
                FatigueSlotGroup.SCAPULAR_SUPPORT,
                FatigueSlotGroup.CALF_ANKLE,
                FatigueSlotGroup.ACCESSORY -> FatigueSlotDisposition.LOW_LOAD_ONLY
                FatigueSlotGroup.RECOVERY_PREHAB,
                FatigueSlotGroup.REHAB_LIKE_ACTIVATION -> FatigueSlotDisposition.NORMAL
            }
        }
    }

    fun groupFor(slot: ProgramSlotId, rehabLikeActivation: Boolean = false): FatigueSlotGroup {
        if (rehabLikeActivation) return FatigueSlotGroup.REHAB_LIKE_ACTIVATION
        return when (slot) {
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR -> FatigueSlotGroup.STRENGTH_ANCHOR
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL -> FatigueSlotGroup.SINGLE_LEG_DECEL
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME -> FatigueSlotGroup.FOOTWORK_COD_REACTIVE
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.OVERHEAD_SMASH_SUPPORT -> FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN -> FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> FatigueSlotGroup.TRUNK_STABILITY
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT -> FatigueSlotGroup.SCAPULAR_SUPPORT
            ProgramSlotId.CALF_ANKLE_CAPACITY -> FatigueSlotGroup.CALF_ANKLE
            ProgramSlotId.RECOVERY_PREHAB_LIGHT -> FatigueSlotGroup.RECOVERY_PREHAB
            ProgramSlotId.UPPER_PUSH_SUPPORT,
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT,
            ProgramSlotId.ACCESSORY_ROTATION -> FatigueSlotGroup.ACCESSORY
        }
    }

    companion object {
        val DEFAULT = FatigueSlotPolicy()
    }
}

internal data class ProgramFatigueGate(
    val band: ProgramFatigueBand,
    val volumeFactor: Double,
    val rpeCap: Int,
    val allowsHeavyLower: Boolean,
    val allowsHighImpact: Boolean,
    val allowsHighIntensityCod: Boolean,
    val lowerBodyRestricted: Boolean,
    val allowsUpperPush: Boolean = true,
    val allowsOverhead: Boolean = true,
    val allowsGripForearm: Boolean = true
) {
    fun allows(candidate: ProgramCandidate): Boolean {
        if (!allowsHeavyLower && candidate.isHeavyLower) return false
        if (!allowsHighImpact && candidate.isHighImpact) return false
        if (!allowsHighIntensityCod && candidate.isHighIntensityCod) return false
        if (lowerBodyRestricted && candidate.isHeavyLower) return false
        if (!allowsUpperPush && candidate.matchesUpperPushWork()) return false
        if (!allowsOverhead && candidate.matchesOverheadWork()) return false
        if (!allowsGripForearm && candidate.matchesGripForearmWork()) return false
        return true
    }

    companion object {
        fun from(state: DailyFatigueState?): ProgramFatigueGate {
            val ofi = state?.overallFatigueIndex ?: 0
            val band = when (ofi) {
                in 0..44 -> ProgramFatigueBand.GREEN
                in 45..59 -> ProgramFatigueBand.YELLOW
                in 60..74 -> ProgramFatigueBand.ORANGE
                else -> ProgramFatigueBand.RED
            }
            val jointRestricted = (state?.jointTendonImpactScore ?: 0) >= 65
            val neuralRestricted = (state?.neuromuscularScore ?: 0) >= 70
            val localRestricted = (state?.localMuscularScore ?: 0) >= 70
            return ProgramFatigueGate(
                band = band,
                volumeFactor = when (band) {
                    ProgramFatigueBand.GREEN -> 1.0
                    ProgramFatigueBand.YELLOW -> 0.75
                    ProgramFatigueBand.ORANGE -> 0.50
                    ProgramFatigueBand.RED -> 0.25
                },
                rpeCap = if (neuralRestricted || band >= ProgramFatigueBand.ORANGE) 7 else 9,
                allowsHeavyLower = band < ProgramFatigueBand.ORANGE && !localRestricted,
                allowsHighImpact = band < ProgramFatigueBand.ORANGE && !jointRestricted,
                allowsHighIntensityCod = band < ProgramFatigueBand.ORANGE,
                lowerBodyRestricted = localRestricted
            )
        }

        fun from(trainingGate: TrainingGateSnapshot?): ProgramFatigueGate {
            if (trainingGate == null) return unrestricted()
            val volumeFactor = trainingGate.volumeFactor.coerceIn(0.0, 1.0)
            val hasTypedRestriction = trainingGate.heavyLowerRestricted ||
                trainingGate.highImpactRestricted ||
                trainingGate.codReactiveRestricted ||
                trainingGate.upperPushRestricted ||
                trainingGate.overheadRestricted ||
                trainingGate.gripForearmRestricted
            return ProgramFatigueGate(
                band = if (hasTypedRestriction || volumeFactor < 1.0) {
                    ProgramFatigueBand.YELLOW
                } else {
                    ProgramFatigueBand.GREEN
                },
                volumeFactor = volumeFactor,
                rpeCap = trainingGate.rpeCap?.coerceIn(1, 10) ?: 9,
                allowsHeavyLower = !trainingGate.heavyLowerRestricted,
                allowsHighImpact = !trainingGate.highImpactRestricted,
                allowsHighIntensityCod = !trainingGate.codReactiveRestricted,
                lowerBodyRestricted = trainingGate.heavyLowerRestricted,
                allowsUpperPush = !trainingGate.upperPushRestricted,
                allowsOverhead = !trainingGate.overheadRestricted,
                allowsGripForearm = !trainingGate.gripForearmRestricted
            )
        }

        private fun unrestricted(): ProgramFatigueGate = ProgramFatigueGate(
            band = ProgramFatigueBand.GREEN,
            volumeFactor = 1.0,
            rpeCap = 9,
            allowsHeavyLower = true,
            allowsHighImpact = true,
            allowsHighIntensityCod = true,
            lowerBodyRestricted = false
        )
    }
}

private enum class TodayGateAction {
    KEEP,
    DOWNGRADE,
    AVOID
}

private fun ProgramCandidate.matchesHeavyLowerWork(): Boolean =
    isHeavyLower || hasProgramSlot(HEAVY_LOWER_SLOTS)

private fun ProgramCandidate.matchesHighImpactWork(): Boolean =
    isHighImpact || hasProgramSlot(HIGH_IMPACT_SLOTS)

private fun ProgramCandidate.matchesCodReactiveWork(): Boolean =
    isHighIntensityCod || hasProgramSlot(COD_REACTIVE_SLOTS)

private fun ProgramCandidate.matchesUpperPushWork(): Boolean =
    hasProgramSlot(UPPER_PUSH_SLOTS)

private fun ProgramCandidate.matchesOverheadWork(): Boolean =
    hasProgramSlot(OVERHEAD_SLOTS)

private fun ProgramCandidate.matchesGripForearmWork(): Boolean =
    hasProgramSlot(GRIP_FOREARM_SLOTS)

private fun ProgramCandidate.matchesExplosivePlanningWork(): Boolean =
    matchesHighImpactWork() ||
        matchesCodReactiveWork() ||
        listOf(
            metadata?.movementSubtype.orEmpty(),
            metadata?.movementFamily.orEmpty(),
            metadata?.programSlot.orEmpty(),
            metadata?.primaryStressProfile.orEmpty()
        ).any { value -> EXPLOSIVE_PLANNING_TOKENS.any { token -> value.contains(token, ignoreCase = true) } }

private fun ProgramCandidate.hasProgramSlot(slots: Set<ProgramSlotId>): Boolean =
    slots.any { slot -> slotCapabilities.hasAny(slot) }

private val EXPLOSIVE_PLANNING_TOKENS = setOf("SLAM", "THROW", "TOSS", "PUSH_PRESS", "PLYOMETRIC", "EXPLOSIVE")

private fun TrainingProgramItem.scaledSets(
    volumeFactor: Double,
    forceMinimum: Boolean
): TrainingProgramItem {
    val original = setCount.coerceAtLeast(1)
    val scaled = if (forceMinimum) {
        1
    } else {
        (original * volumeFactor.coerceIn(0.0, 1.0)).roundToInt().coerceAtLeast(1)
    }.coerceAtMost(original)
    return if (scaled == setCount) this else copy(setCount = scaled)
}
