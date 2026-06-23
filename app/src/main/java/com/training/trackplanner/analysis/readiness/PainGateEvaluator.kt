package com.training.trackplanner.analysis.readiness

import java.time.LocalDate

class PainGateEvaluator {
    fun evaluate(
        today: LocalDate,
        painInputs: List<PainInput>
    ): PainGateSnapshot {
        val todayInputs = painInputs.filter { input -> input.date == today }
        if (todayInputs.isEmpty()) {
            return PainGateSnapshot(
                isLimited = false,
                level = FatigueLevel.NORMAL,
                restrictedTargets = emptyList(),
                reasons = emptyList(),
                confidence = AnalysisConfidence.LOW
            )
        }

        val strongest = todayInputs.maxByOrNull { input -> input.score ?: 0 }
        val score = strongest?.score ?: 0
        val hardGate = score >= 7 || todayInputs.any { input -> input.acute || input.worsening }
        val moderateGate = score in 4..6
        val bodyParts = todayInputs.mapNotNull { input -> input.bodyPart }.distinct()
        val targets = bodyParts.map { part -> "$part 고강도 운동" }
        val reasons = buildList {
            if (hardGate) add("불편감 입력이 높아 해당 부위는 조절이 필요합니다.")
            if (moderateGate) add("불편감 입력이 있어 해당 부위 부담을 낮춥니다.")
            if (targets.isEmpty() && (hardGate || moderateGate)) {
                add("불편감 입력이 있어 고강도 운동을 줄입니다.")
            }
        }

        return PainGateSnapshot(
            isLimited = hardGate,
            level = when {
                hardGate -> FatigueLevel.LIMITED
                moderateGate -> FatigueLevel.HIGH
                else -> FatigueLevel.NORMAL
            },
            restrictedTargets = if (targets.isEmpty() && (hardGate || moderateGate)) {
                listOf("불편감 부위 고강도 운동")
            } else {
                targets
            },
            reasons = reasons,
            confidence = AnalysisConfidence.MEDIUM
        )
    }
}
