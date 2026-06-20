package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState

internal class FatigueSlotPolicy {
    fun gate(state: DailyFatigueState?): ProgramFatigueGate = ProgramFatigueGate.from(state)

    fun adapt(planned: PlannedSlot, gate: ProgramFatigueGate): PlannedSlot = when (gate.band) {
        ProgramFatigueBand.RED -> planned.copy(intensity = ProgramDayIntensity.LIGHT)
        ProgramFatigueBand.ORANGE -> planned.copy(
            intensity = if (planned.intensity == ProgramDayIntensity.HARD) {
                ProgramDayIntensity.MODERATE
            } else {
                planned.intensity
            }
        )
        else -> planned
    }

    fun allows(candidate: ProgramCandidate, gate: ProgramFatigueGate): Boolean = gate.allows(candidate)

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
    val lowerBodyRestricted: Boolean
) {
    fun allows(candidate: ProgramCandidate): Boolean {
        if (!allowsHeavyLower && candidate.isHeavyLower) return false
        if (!allowsHighImpact && candidate.isHighImpact) return false
        if (!allowsHighIntensityCod && candidate.isHighIntensityCod) return false
        if (lowerBodyRestricted && candidate.isHeavyLower) return false
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
    }
}
