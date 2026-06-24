package com.training.trackplanner.data

internal object ProgramExerciseReasonBuilder {
    private const val MAX_LABELS = 4

    fun labels(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        requestedSlot: ProgramSlotId?,
        dayIntensity: ProgramDayIntensity,
        week: ProgramWeekPlan,
        fatigueGate: ProgramFatigueGate
    ): List<String> {
        val labels = mutableListOf<String>()

        labels += roleLabel(role, requestedSlot)
        labels += slotLabels(requestedSlot, candidate)
        labels += fatigueLabels(candidate, dayIntensity, week, fatigueGate)
        labels += sourceLabels(candidate)

        return labels
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_LABELS)
            .ifEmpty { listOf("프로그램 균형", "슬롯 조건 충족") }
    }

    private fun roleLabel(role: ProgramExerciseRole, requestedSlot: ProgramSlotId?): List<String> =
        when (role) {
            ProgramExerciseRole.ANCHOR -> listOf(anchorLabel(requestedSlot))
            ProgramExerciseRole.CORE -> listOf("코어 앵커")
            ProgramExerciseRole.PREHAB -> listOf("회복성 보조")
            ProgramExerciseRole.TRANSFER -> listOf("배드민턴 보조전이")
            ProgramExerciseRole.SUPPORT -> listOf("부위 균형")
            ProgramExerciseRole.ACCESSORY -> listOf("동작계열 균형")
        }

    private fun anchorLabel(slot: ProgramSlotId?): String =
        when (slot) {
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN -> "힌지 앵커"
            ProgramSlotId.UPPER_PULL_ANCHOR -> "상체 당기기 앵커"
            ProgramSlotId.UPPER_PUSH_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT -> "상체 밀기 앵커"
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> "코어 앵커"
            else -> "하체 앵커"
        }

    private fun slotLabels(
        requestedSlot: ProgramSlotId?,
        candidate: ProgramCandidate
    ): List<String> = buildList {
        when (requestedSlot) {
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION -> add("첫 스텝 보조")
            ProgramSlotId.BADMINTON_DECEL_COD -> {
                add("방향전환 보조")
                add("감속 안정성")
            }
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> add("항회전 코어")
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT -> add("어깨 안정성")
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT -> add("손목·전완 보조")
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME,
            ProgramSlotId.OVERHEAD_SMASH_SUPPORT -> add("배드민턴 보조전이")
            ProgramSlotId.RECOVERY_PREHAB_LIGHT -> add("관절 부담 완화")
            ProgramSlotId.CALF_ANKLE_CAPACITY -> add("첫 스텝 보조")
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL -> add("감속 안정성")
            else -> Unit
        }
        if (candidate.badmintonFit >= 0.75) add("배드민턴 보조전이")
        if (candidate.isScapularStabilityExposure) add("어깨 안정성")
    }

    private fun fatigueLabels(
        candidate: ProgramCandidate,
        dayIntensity: ProgramDayIntensity,
        week: ProgramWeekPlan,
        fatigueGate: ProgramFatigueGate
    ): List<String> = buildList {
        if (fatigueGate.band != ProgramFatigueBand.GREEN) add("피로도 고려")
        if (fatigueGate.band in setOf(ProgramFatigueBand.ORANGE, ProgramFatigueBand.RED) && candidate.isRecovery) {
            add("저강도 대체")
        }
        if (week.deloadFlag || candidate.isRecovery || candidate.isRehabLikeActivation) add("회복성 보조")
        if (candidate.isRehabLikeActivation) add("관절 부담 완화")
        when (dayIntensity) {
            ProgramDayIntensity.HARD -> add("강약 배치")
            ProgramDayIntensity.MODERATE -> add("중간 피로 배치")
            ProgramDayIntensity.LIGHT -> add("피로도 고려")
        }
    }

    private fun sourceLabels(candidate: ProgramCandidate): List<String> = buildList {
        if (candidate.slotCapabilities.source == SlotCapabilitySource.NAME_FALLBACK ||
            candidate.slotCapabilities.confidence == SlotCapabilityConfidence.LOW
        ) {
            add("fallback 선택")
        }
        if (candidate.canonical || candidate.slotCapabilities.source == SlotCapabilitySource.RUNTIME_METADATA) {
            add("슬롯 조건 충족")
        }
    }
}
