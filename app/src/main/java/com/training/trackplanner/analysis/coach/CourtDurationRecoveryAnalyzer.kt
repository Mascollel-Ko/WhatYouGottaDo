package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import java.util.Locale

class CourtDurationRecoveryAnalyzer {
    fun analyze(
        today: LocalDate,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
        checkIns: List<DailyCheckIn>,
        history: List<DailyFatigueResult>,
        sleepSignal: SleepRecoverySignal
    ): CourtDurationRecoverySignal? {
        val exerciseById = exercises.associateBy { it.id }
        val checkInByDate = checkIns.associateBy { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val fatigueByDate = history.associateBy { it.state.date }
        val courtMinutesByDate = entriesWithSets.asSequence()
            .filter { record -> record.sets.any { it.confirmed } }
            .mapNotNull { record ->
                val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return@mapNotNull null
                if (date > today) return@mapNotNull null
                val exercise = exerciseById[record.entry.exerciseId] ?: return@mapNotNull null
                val metadata = runtimeMetadataCatalog.resolve(exercise) ?: return@mapNotNull null
                if (!metadata.isCourtSession()) return@mapNotNull null
                val seconds = record.sets.filter { it.confirmed }.sumOf { it.seconds }
                if (seconds <= 0) return@mapNotNull null
                date to seconds / 60.0
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.sum() }

        if (courtMinutesByDate.isEmpty()) return null

        val pairs = courtMinutesByDate.mapNotNull { (date, minutes) ->
            val nextDate = date.plusDays(1)
            val nextCheckIn = checkInByDate[nextDate]
            val nextFatigue = fatigueByDate[nextDate]?.state
            if (nextCheckIn == null && nextFatigue == null) return@mapNotNull null
            CourtRecoveryPair(
                minutes = minutes,
                nextOverallFatigue = nextCheckIn?.overallFatigue,
                nextLowerBodyFatigue = nextCheckIn?.lowerBodyFatigue,
                nextJointDiscomfort = nextCheckIn?.jointTendonDiscomfort,
                nextOfi = nextFatigue?.overallFatigueIndex
            )
        }

        if (pairs.size < 2) {
            return CourtDurationRecoverySignal(
                severity = CoachingSignalSeverity.INFO,
                headline = "코트 시간 반응 데이터가 적습니다",
                detail = "배드민턴 시간과 다음날 회복 입력을 날짜 기준으로 모으는 중입니다.",
                observedThresholdMinutes = null,
                sampleSize = pairs.size,
                sleepContext = null
            )
        }

        val longPairs = pairs.filter { it.minutes >= 90.0 }
        val strongestThreshold = when {
            pairs.any { it.minutes >= 120.0 && it.hasNextDayStrain() } -> 120
            longPairs.any { it.hasNextDayStrain() } -> 90
            else -> null
        }
        val severity = when {
            strongestThreshold == 120 -> CoachingSignalSeverity.CAUTION
            strongestThreshold == 90 -> CoachingSignalSeverity.WATCH
            else -> CoachingSignalSeverity.INFO
        }
        val sleepContext = if (sleepSignal.severity.priority() >= CoachingSignalSeverity.WATCH.priority()) {
            "최근 수면 입력이 낮아 긴 코트 시간 뒤 회복 해석을 보수적으로 봅니다."
        } else {
            null
        }
        return CourtDurationRecoverySignal(
            severity = severity,
            headline = when (severity) {
                CoachingSignalSeverity.CAUTION -> "긴 코트 시간 뒤 회복 입력을 확인합니다"
                CoachingSignalSeverity.WATCH -> "90분 이상 코트 시간 뒤 반응을 봅니다"
                else -> "코트 시간 반응은 참고 수준입니다"
            },
            detail = if (strongestThreshold != null) {
                "${strongestThreshold}분 이상 배드민턴 뒤 다음날 피로/불편감 입력이 높았던 기록이 있습니다. 같은 패턴을 단정하지 않고 다음날 강도를 보수적으로 봅니다."
            } else {
                "현재 기록에서는 긴 코트 시간이 항상 다음날 높은 피로 입력으로 이어진다고 보기 어렵습니다."
            },
            observedThresholdMinutes = strongestThreshold,
            sampleSize = pairs.size,
            sleepContext = sleepContext
        )
    }

    private fun RuntimeExerciseMetadata.isCourtSession(): Boolean {
        val activity = activityKind.uppercase(Locale.ROOT)
        val transfer = badmintonTransferLevel.uppercase(Locale.ROOT)
        val context = sportContextTags.values.joinToString("|").uppercase(Locale.ROOT)
        return (activity == "SPORT_SESSION" || activity == "MATCH_RECORD") &&
            (transfer == "DIRECT" || context.contains("BADMINTON") || context.contains("COURT"))
    }

    private data class CourtRecoveryPair(
        val minutes: Double,
        val nextOverallFatigue: Int?,
        val nextLowerBodyFatigue: Int?,
        val nextJointDiscomfort: Int?,
        val nextOfi: Int?
    ) {
        fun hasNextDayStrain(): Boolean =
            listOfNotNull(nextOverallFatigue, nextLowerBodyFatigue, nextJointDiscomfort).any { it >= 4 } ||
                (nextOfi ?: 0) >= 75
    }
}
