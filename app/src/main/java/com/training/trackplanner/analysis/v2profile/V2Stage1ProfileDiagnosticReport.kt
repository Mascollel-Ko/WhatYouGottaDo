package com.training.trackplanner.analysis.v2profile

import java.time.format.DateTimeFormatter

object V2Stage1ProfileDiagnosticReport {
    private val displayDate = DateTimeFormatter.ISO_LOCAL_DATE

    /** Deterministic Korean text report; the profile also retains structured calculation evidence. */
    fun format(profile: V2Stage1TrainingProfile, topExerciseLimit: Int = 20): String {
        require(topExerciseLimit >= 0)
        val lines = mutableListOf(
            "V2-1 개인 훈련 상태 분석 (운동 처방 아님)",
            "기준일: ${profile.asOf.format(displayDate)} | 확인 완료 세트 총 ${profile.completedSetCount}개",
            ""
        )
        profile.windows.forEach { window ->
            lines += "${window.days}일 (${window.start}~${window.end}): 전체 ${window.completedSetCount}세트 / 근력 ${window.resistanceSetCount}세트 / 기타 ${window.otherSetCount}세트 / 근력 주간 세트 환산 ${decimal(window.resistanceWeeklySetEquivalent)} / 전체 주간 세트 환산 ${decimal(window.allWeeklySetEquivalent)} / 전체 활동일 ${window.activeDays}일 / 근력운동일 ${window.resistanceDays}일 / 세션 ${window.sessionCount}회"
            lines += "  날짜별 근거 (완료/근력): ${window.dailyEvidence.joinToString { "${it.date} ${it.completedSetCount}/${it.resistanceSetCount}" }.ifBlank { "없음" }}"
            lines += "  세션 중복 제거 근거: ${window.sessionEvidence.size}개 날짜-세션 키"
            lines += "  근거 운동별 세트 수: ${window.resistanceExerciseFrequencies.joinToString { "${it.name} ${it.completedSetCount}" }.ifBlank { "없음" }}"
        }
        lines += ""
        lines += "최근 56일 근력운동 상위 ${minOf(topExerciseLimit, profile.exercises.size)}종목:"
        profile.topExercises.take(topExerciseLimit).forEach { exercise ->
            val exposure = exercise.exposureByWindow.getValue(56)
            val latest = exercise.performance.latestExplicitLoad
            val latestText = latest?.let {
                "${decimal(it.explicitPositiveWeightKg!!)}kg×${it.repetitions?.let(::decimal) ?: "반복 미기록"} (RPE ${it.rpe?.let(::decimal) ?: "미기록"}; ${it.date})"
            } ?: "명시 중량 확인 불가"
            val peak = exercise.performance.highestLoggedLoad?.let {
                "${decimal(it.explicitPositiveWeightKg!!)}kg (${it.date})"
            } ?: "확인 불가"
            val rpe = exercise.performance.meanRecordedRpe?.let(::decimal) ?: "미기록"
            lines += "- ${exercise.name}: ${exposure.completedSetCount}세트 / ${exposure.sessionCount}세션 / ${exposure.trainingDays}일 / 마지막 ${exposure.lastCompletedDate ?: "기록 없음"} / 최근 명시 중량 $latestText / 최고 기록 중량 $peak / 기록 RPE ${exercise.performance.recordedRpeSetCount}세트 평균 $rpe"
            lines += "  유지 우선순위: 미평가 (훈련 빈도 외 목표 관련성·수행 추이·움직임 범위·선호·안전 검토·중복·장비·시간 정보가 필요)"
        }
        val exclusions = profile.exclusions
        lines += ""
        lines += "제외 진단: 미확인 ${exclusions.unconfirmedSets}세트 / 부하 보정 대기 ${exclusions.loadCalibrationPendingSets}세트 / 날짜 오류 ${exclusions.invalidDateCompletedSets}세트 / 기준일 이후 ${exclusions.futureCompletedSets}세트 / 56일 창 이전 완료 ${exclusions.olderThan56DaysCompletedSets}세트"
        lines += "자극 및 회복 부담: 계산하지 않음. 세트 수나 기록 중량을 자극·피로·회복량으로 해석하지 않음."
        lines += ""
        lines += "주의:"
        profile.caveats.forEach { lines += "- $it" }
        return lines.joinToString("\n") + "\n"
    }

    private fun decimal(value: Double): String = if (value % 1.0 == 0.0) {
        "%.1f".format(java.util.Locale.ROOT, value)
    } else {
        value.toString()
    }

    private fun decimal(value: Int): String = value.toString()
}
