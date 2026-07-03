package com.training.trackplanner.data

import kotlin.math.roundToInt

enum class ProgramDraftIssueSeverity { GOOD, CAUTION, HIGH }

data class ProgramDraftIssue(
    val severity: ProgramDraftIssueSeverity,
    val message: String
)

data class ProgramDraftEvaluationSection(
    val title: String,
    val score: Int,
    val summary: String,
    val issues: List<ProgramDraftIssue>
)

data class ProgramDraftEvaluation(
    val overallScore: Int,
    val summary: String,
    val fatigue: ProgramDraftEvaluationSection,
    val strengthDistribution: ProgramDraftEvaluationSection,
    val badmintonTransferDistribution: ProgramDraftEvaluationSection
) {
    val sections: List<ProgramDraftEvaluationSection>
        get() = listOf(fatigue, strengthDistribution, badmintonTransferDistribution)
}

object ProgramDraftEvaluator {
    fun evaluate(
        skeleton: GeneratedProgramSkeleton?,
        metadataByExerciseId: Map<Long, RuntimeExerciseMetadata>
    ): ProgramDraftEvaluation {
        val draft = skeleton ?: return emptyEvaluation()
        if (draft.items.isEmpty()) return emptyEvaluation()
        val fatigue = fatigueSection(draft)
        val strength = strengthSection(draft, metadataByExerciseId)
        val transfer = transferSection(draft, metadataByExerciseId)
        val overall = ((fatigue.score + strength.score + transfer.score) / 3.0).roundToInt().coerceIn(0, 100)
        return ProgramDraftEvaluation(
            overallScore = overall,
            summary = when {
                overall >= 80 -> "전체 구성이 안정적입니다."
                overall >= 65 -> "대체로 괜찮지만 일부 편중이 있습니다."
                else -> "피로도나 분산 편중을 조정하는 편이 좋습니다."
            },
            fatigue = fatigue,
            strengthDistribution = strength,
            badmintonTransferDistribution = transfer
        )
    }

    private fun fatigueSection(draft: GeneratedProgramSkeleton): ProgramDraftEvaluationSection {
        val dayLoads = draft.items.groupBy { it.weekNumber to it.dayOfWeek }
            .mapValues { (_, rows) -> rows.sumOf(::fatigueLoad) }
        val weekLoads = dayLoads.entries.groupBy { it.key.first }.mapValues { (_, rows) -> rows.sumOf { it.value } }
        val clustered = dayLoads.keys.any { key ->
            val absolute = (key.first - 1) * 7 + key.second
            dayLoads.any { (other, load) ->
                val otherAbsolute = (other.first - 1) * 7 + other.second
                load >= 14.0 && dayLoads.getValue(key) >= 14.0 && otherAbsolute == absolute + 1
            }
        }
        val issues = mutableListOf<ProgramDraftIssue>()
        if (dayLoads.values.any { it >= 18.0 }) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.HIGH, "하루 피로 부하가 높은 날이 있습니다.")
        }
        if (clustered) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, "고피로일이 연속 또는 주차 경계에 붙어 있습니다.")
        }
        if (weekLoads.values.any { it >= 55.0 }) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, "한 주의 총 피로 부하가 높습니다.")
        }
        if (issues.isEmpty()) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.GOOD, "고피로일과 주간 부하가 과도하지 않습니다.")
        }
        val penalty = issues.map { issue ->
            when (issue.severity) {
                ProgramDraftIssueSeverity.HIGH -> 22
                ProgramDraftIssueSeverity.CAUTION -> 12
                ProgramDraftIssueSeverity.GOOD -> 0
            }
        }.sum()
        val score = (100 - penalty).coerceIn(0, 100)
        return ProgramDraftEvaluationSection("피로도", score, issues.first().message, issues)
    }

    private fun strengthSection(
        draft: GeneratedProgramSkeleton,
        metadataByExerciseId: Map<Long, RuntimeExerciseMetadata>
    ): ProgramDraftEvaluationSection {
        val groups = draft.items.map { item ->
            item.strengthProgressionGroup.ifBlank {
                metadataByExerciseId[item.exerciseId]?.strengthProgressionGroup.orEmpty()
            }.ifBlank {
                item.movementFamily.ifBlank { metadataByExerciseId[item.exerciseId]?.movementFamily.orEmpty() }
            }.ifBlank {
                item.category
            }
        }
        return distributionSection(
            title = "근력운동 분산",
            groups = groups,
            emptyMessage = "근력 분산을 판단할 운동 정보가 부족합니다.",
            goodMessage = "근력운동 분산이 과도하게 한쪽으로 몰리지 않았습니다.",
            concentrationMessage = "특정 근력/움직임 계열 비중이 높습니다.",
            missingMessage = "주요 움직임 계열 다양성이 부족합니다."
        )
    }

    private fun transferSection(
        draft: GeneratedProgramSkeleton,
        metadataByExerciseId: Map<Long, RuntimeExerciseMetadata>
    ): ProgramDraftEvaluationSection {
        val groups = draft.items.flatMap { item ->
            val metadata = metadataByExerciseId[item.exerciseId]
            val tokens = item.primarySlotCapabilities + item.secondarySlotCapabilities + metadata?.badmintonTransferType?.values.orEmpty()
            tokens.ifEmpty {
                listOf(item.badmintonTransferLevel.ifBlank { metadata?.badmintonTransferLevel.orEmpty() })
            }
        }.filter { it.isNotBlank() && it != "NONE" }
        return distributionSection(
            title = "배드민턴 전이 분산",
            groups = groups,
            emptyMessage = "배드민턴 전이 목적 정보가 부족합니다.",
            goodMessage = "배드민턴 전이 목적이 한쪽으로 과도하게 몰리지 않았습니다.",
            concentrationMessage = "특정 배드민턴 전이 목적 비중이 높습니다.",
            missingMessage = "배드민턴 전이 목적 다양성이 부족합니다."
        )
    }

    private fun distributionSection(
        title: String,
        groups: List<String>,
        emptyMessage: String,
        goodMessage: String,
        concentrationMessage: String,
        missingMessage: String
    ): ProgramDraftEvaluationSection {
        if (groups.isEmpty()) {
            val issue = ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, emptyMessage)
            return ProgramDraftEvaluationSection(title, 60, emptyMessage, listOf(issue))
        }
        val counts = groups.groupingBy { it }.eachCount()
        val topShare = counts.values.maxOrNull().orEmptyInt().toDouble() / groups.size
        val issues = mutableListOf<ProgramDraftIssue>()
        if (topShare >= 0.65) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, concentrationMessage)
        }
        if (counts.size <= 1 && groups.size >= 3) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.HIGH, missingMessage)
        } else if (counts.size <= 2 && groups.size >= 5) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, missingMessage)
        }
        if (issues.isEmpty()) {
            issues += ProgramDraftIssue(ProgramDraftIssueSeverity.GOOD, goodMessage)
        }
        val penalty = issues.map { issue ->
            when (issue.severity) {
                ProgramDraftIssueSeverity.HIGH -> 24
                ProgramDraftIssueSeverity.CAUTION -> 14
                ProgramDraftIssueSeverity.GOOD -> 0
            }
        }.sum()
        return ProgramDraftEvaluationSection(title, (100 - penalty).coerceIn(0, 100), issues.first().message, issues)
    }

    private fun fatigueLoad(item: ProgramSkeletonItem): Double {
        val volume = item.setCount.coerceAtLeast(1) * when {
            item.seconds > 0 -> (item.seconds / 30.0).coerceAtLeast(1.0)
            item.reps > 0 -> (item.reps / 8.0).coerceAtLeast(0.75)
            else -> 1.0
        }
        val weightFactor = if (item.weightKg > 0.0) 1.25 else 1.0
        val stressFactor = listOf(
            item.neuromuscularStressLevel,
            item.systemicMuscularStressLevel,
            item.localMuscularStressLevel,
            item.jointTendonImpactStressLevel,
            item.movementFocusDemandLevel
        ).maxOfOrNull(::stressScore) ?: 1.0
        val restFactor = if (item.restSeconds in 1..45) 1.15 else 1.0
        return volume * weightFactor * stressFactor * restFactor
    }

    private fun stressScore(value: String): Double = when (value.uppercase()) {
        "VERY_HIGH" -> 1.8
        "HIGH" -> 1.45
        "MODERATE" -> 1.15
        "LOW" -> 0.9
        else -> 1.0
    }

    private fun emptyEvaluation(): ProgramDraftEvaluation {
        val fatigue = ProgramDraftEvaluationSection(
            title = "피로도",
            score = 0,
            summary = "아직 평가할 운동이 없습니다.",
            issues = listOf(ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, "운동을 추가하면 피로도를 평가합니다."))
        )
        val strength = ProgramDraftEvaluationSection(
            title = "근력운동 분산",
            score = 0,
            summary = "아직 평가할 운동이 없습니다.",
            issues = listOf(ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, "운동을 추가하면 근력 분산을 평가합니다."))
        )
        val transfer = ProgramDraftEvaluationSection(
            title = "배드민턴 전이 분산",
            score = 0,
            summary = "아직 평가할 운동이 없습니다.",
            issues = listOf(ProgramDraftIssue(ProgramDraftIssueSeverity.CAUTION, "운동을 추가하면 전이 목적 분산을 평가합니다."))
        )
        return ProgramDraftEvaluation(0, "운동을 추가하면 평가가 표시됩니다.", fatigue, strength, transfer)
    }

    private fun Int?.orEmptyInt(): Int = this ?: 0
}
