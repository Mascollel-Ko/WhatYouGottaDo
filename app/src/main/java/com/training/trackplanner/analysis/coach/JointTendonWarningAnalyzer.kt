package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.DailyCheckIn
import java.time.LocalDate
import java.util.Locale

class JointTendonWarningAnalyzer {
    fun analyze(
        today: LocalDate,
        checkIns: List<DailyCheckIn>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
        sleepSignal: SleepRecoverySignal
    ): JointTendonWarningSignal? {
        val start = today.minusDays(6)
        val recentCheckIns = checkIns.filter { checkIn ->
            runCatching { LocalDate.parse(checkIn.date) }.getOrNull()?.let { it in start..today } == true
        }
        val maxDiscomfort = recentCheckIns.mapNotNull { it.jointTendonDiscomfort }.maxOrNull()
        val stressLabels = recentJointStressLabels(start, today, entriesWithSets, exercises, runtimeMetadataCatalog)
        val hasMetadataStress = stressLabels.isNotEmpty()
        if ((maxDiscomfort ?: 0) < 4) return null

        val severity = when {
            (maxDiscomfort ?: 0) >= 5 -> CoachingSignalSeverity.CAUTION
            else -> CoachingSignalSeverity.WATCH
        }
        val sleepContext = if (sleepSignal.severity.priority() >= CoachingSignalSeverity.WATCH.priority()) {
            "수면 입력도 낮아 회복 해석을 보수적으로 봅니다."
        } else {
            null
        }
        val headline = "관절/건 불편감 입력을 확인합니다"
        val detail = when {
            hasMetadataStress ->
                "최근 불편감 입력과 관련 운동 기록이 함께 보입니다. 점프, 감속, 고중량 하체는 강도를 낮춰 확인합니다."
            else ->
                "최근 불편감 입력이 높습니다. 운동 기록만으로 단정하지 않고 오늘 부하를 보수적으로 조절합니다."
        }

        return JointTendonWarningSignal(
            severity = severity,
            headline = headline,
            detail = detail,
            relatedStressLabels = stressLabels.take(4),
            sleepContext = sleepContext,
            sampleSize = recentCheckIns.size + stressLabels.size
        )
    }

    private fun recentJointStressLabels(
        start: LocalDate,
        today: LocalDate,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog
    ): List<String> {
        val exerciseById = exercises.associateBy { it.id }
        return entriesWithSets.asSequence()
            .filter { record -> record.sets.any { it.confirmed } }
            .filter { record ->
                runCatching { LocalDate.parse(record.entry.date) }.getOrNull()?.let { it in start..today } == true
            }
            .mapNotNull { record -> exerciseById[record.entry.exerciseId] }
            .mapNotNull { exercise -> runtimeMetadataCatalog.resolve(exercise) }
            .filter { metadata -> metadata.hasJointTendonStress() }
            .flatMap { metadata -> metadata.jointStressTokens().asSequence() }
            .distinct()
            .toList()
    }

    private fun RuntimeExerciseMetadata.hasJointTendonStress(): Boolean =
        jointTendonImpactStressLevel.uppercase(Locale.ROOT) in setOf("HIGH", "VERY_HIGH") ||
            tendonStressTags.values.isNotEmpty() ||
            ligamentJointStabilityStressTags.values.isNotEmpty() ||
            jointImpactStressTags.values.isNotEmpty()

    private fun RuntimeExerciseMetadata.jointStressTokens(): List<String> =
        (tendonStressTags.values + ligamentJointStabilityStressTags.values + jointImpactStressTags.values)
            .ifEmpty { listOf(jointTendonImpactStressLevel) }
            .map { token -> token.replace('_', ' ').lowercase(Locale.ROOT) }
            .map { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
}
