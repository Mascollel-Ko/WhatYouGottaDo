package com.training.trackplanner.data

internal class ProgramSelectionReasonFormatter {
    fun format(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        gate: ProgramFatigueGate
    ): String = buildList {
        add("역할:${role.name}")
        candidate.metadata?.movementFamily?.takeIf { it.isNotBlank() && it != "NOT_APPLICABLE" }
            ?.let { add("동작:$it") }
        if (candidate.badmintonFit >= 0.8) add("전이:${candidate.metadata?.badmintonTransferLevel}")
        if (candidate.metadata?.appCueProfile == "RANDOM_BEEP_CUE") add("앱 cue 가능")
        if (gate.band != ProgramFatigueBand.GREEN) add("피로:${gate.band.name}")
    }.joinToString(" / ")
}
