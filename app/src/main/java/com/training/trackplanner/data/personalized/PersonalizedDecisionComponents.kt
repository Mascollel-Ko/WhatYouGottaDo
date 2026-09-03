package com.training.trackplanner.data.personalized

import kotlin.math.roundToInt

class PlanningQuestionPolicy {
    fun questions(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, answers: PersonalizedPlanningAnswers): List<PersonalizedPlanningQuestion> = buildList {
        if (state.historyDays >= 42 && state.strengthExposure != StrengthExposure.PRESENT && snapshot.preferences.strengthIntent == null && QUESTION_STRENGTH_INTENT !in answers.values) {
            add(PersonalizedPlanningQuestion(QUESTION_STRENGTH_INTENT, "최근 기록만으로는 낮은 반복·고중량 훈련이 부족한 것인지, 의도적으로 피하는 것인지 구분하기 어렵습니다. 앞으로 저항훈련에서 근력 향상을 어느 정도 원하나요?", listOf(
                PersonalizedPlanningAnswerOption(StrengthIntent.HYPERTROPHY_PRIORITY.name, "근비대만 우선"),
                PersonalizedPlanningAnswerOption(StrengthIntent.MIXED.name, "근비대 + 근력"),
                PersonalizedPlanningAnswerOption(StrengthIntent.STRENGTH_PRIORITY.name, "근력 우선"),
                PersonalizedPlanningAnswerOption(StrengthIntent.AVOID_HEAVY.name, "고중량은 의도적으로 피하고 싶음"),
                PersonalizedPlanningAnswerOption(StrengthIntent.UNRESOLVED.name, "잘 모르겠음")
            )))
        }
        if (state.structuredBadmintonSessions == 0 && snapshot.badmintonTrainingYears <= 0 && snapshot.preferences.badmintonIntent == null && QUESTION_BADMINTON_INTENT !in answers.values) {
            add(PersonalizedPlanningQuestion(QUESTION_BADMINTON_INTENT, "배드민턴 경기력 향상을 위한 구조화 훈련을 프로그램에 포함할까요?", listOf(
                PersonalizedPlanningAnswerOption(BadmintonPlanningIntent.ENABLED.name, "포함"),
                PersonalizedPlanningAnswerOption(BadmintonPlanningIntent.DISABLED.name, "포함하지 않음"),
                PersonalizedPlanningAnswerOption(BadmintonPlanningIntent.UNRESOLVED.name, "잘 모르겠음")
            )))
        }
        val modalityChallengeConsidered = state.machineSetRatio >= .65 && state.freeWeightSetRatio <= .12 && state.primaryAdaptation != "HYPERTROPHY"
        if (modalityChallengeConsidered && snapshot.preferences.freeWeightWillingness == null && QUESTION_FREE_WEIGHT !in answers.values) {
            add(PersonalizedPlanningQuestion(QUESTION_FREE_WEIGHT, "낮은 강도의 프리웨이트 또는 편측 운동을 새 자극으로 포함해도 될까요?", listOf(
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.WILLING.name, "포함 가능"),
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.AVOID.name, "피하고 싶음"),
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.UNRESOLVED.name, "잘 모르겠음")
            )))
        }
    }
}

class AdaptationGapAnalyzer {
    fun analyze(snapshot: PlanningHistorySnapshot, state: AthletePlanningState): List<AdaptationGap> {
        val recent = snapshot.allConfirmedSets.filter { !it.date.isBefore(snapshot.cutoff.minusDays(27)) && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
        val counts = recent.groupingBy { snapshot.movementCoverage(it.stableKey) }.eachCount()
        val required = linkedMapOf("LOWER_KNEE" to "HIGH", "POSTERIOR_CHAIN" to "HIGH", "HORIZONTAL_PUSH" to "HIGH", "UPPER_PULL" to "HIGH", "CORE_DIRECT" to "MODERATE")
        if (state.primaryAdaptation.startsWith("HYPERTROPHY")) required.putAll(mapOf("ARMS_BICEPS" to "MODERATE", "ARMS_TRICEPS" to "MODERATE", "CALVES" to "MODERATE"))
        return buildList {
            required.forEach { (target, priority) ->
                val count = when (target) {
                    "UPPER_PULL" -> counts.getOrDefault(MovementCoverage.HORIZONTAL_PULL, 0) + counts.getOrDefault(MovementCoverage.VERTICAL_PULL, 0)
                    else -> runCatching { MovementCoverage.valueOf(target) }.getOrNull()?.let { counts.getOrDefault(it, 0) } ?: 0
                }
                if (count == 0) add(AdaptationGap(target, priority, "최근 4주간 $target 직접 훈련 기록이 없습니다."))
            }
            if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED && state.structuredBadmintonSessions == 0) {
                add(AdaptationGap("BADMINTON_FOUNDATIONAL_ONRAMP", "HIGH", "배드민턴 의도는 활성화됐지만 구조화 훈련 기록이 없어 기초 온램프가 필요합니다."))
            }
            if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED) {
                state.objectiveDropGaps.sorted().forEach { objective ->
                    add(AdaptationGap("BADMINTON_DROP_$objective", "HIGH", "과거에 관찰된 $objective 자극이 최근 4주에서 사라졌습니다."))
                }
                state.objectiveDevelopmentalGaps.sorted().take(2).forEach { objective ->
                    add(AdaptationGap("BADMINTON_DEVELOP_$objective", "MODERATE", "$objective 축은 아직 직접 관찰되지 않은 발달 후보입니다."))
                }
            }
            if (state.primaryAdaptation.startsWith("HYPERTROPHY") && state.hypertrophyStimulusByMovement.isNotEmpty()) {
                val positive = state.hypertrophyStimulusByMovement.filterValues { it > 0.0 }
                val max = positive.maxByOrNull { it.value }
                val min = positive.minByOrNull { it.value }
                if (max != null && min != null && max.value >= min.value * 3.0) {
                    add(AdaptationGap("HYPERTROPHY_REBALANCE_${min.key.name}", "HIGH", "유효 근비대 자극이 ${max.key.name}에 과도하게 치우쳐 ${min.key.name} 보완이 필요합니다."))
                }
            }
        }
    }
}

class PlanningHorizonPlanner {
    fun choose(state: AthletePlanningState, gaps: List<AdaptationGap>): Int = when {
        state.recoverySignals.readinessStatus == "LIMITED" -> 2
        state.recoverySignals.isConstrained -> 3
        state.historyDays < 28 -> 2
        state.strengthIntent == StrengthIntent.UNRESOLVED || state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED -> 3
        gaps.any { it.code == "BADMINTON_FOUNDATIONAL_ONRAMP" } -> 3
        state.scheduleVolatility >= .9 -> 4
        state.historyDays >= 56 && state.confidence == PlanningConfidence.HIGH && gaps.none { it.priority == "HIGH" } -> 6
        state.historyDays >= 42 && state.confidence != PlanningConfidence.LOW -> 5
        else -> 4
    }
}

class WeeklyDosePlanner {
    fun chooseDays(state: AthletePlanningState, itemCount: Int): Int {
        val historical = state.recentTrainingDaysPerWeek.roundToInt().coerceIn(2, 5)
        val densityFloor = ((itemCount + 3) / 4).coerceIn(2, 5)
        val courtAdjusted = if (state.genericCourtLoad >= 180.0) minOf(historical, 3) else historical
        val recoveryCeiling = when {
            state.recoverySignals.readinessStatus == "LIMITED" -> 2
            state.recoverySignals.isConstrained -> 3
            else -> 5
        }
        return maxOf(courtAdjusted, densityFloor).coerceAtMost(recoveryCeiling)
    }
}

data class BlockIntent(
    val primary: String,
    val adaptationMinWeeks: Int,
    val adaptationMaxWeeks: Int,
    val selectedStyle: StrengthProgrammingStyle,
    val styleProvenance: String,
    val reasons: List<String>,
    val constraints: List<String>,
    val reasonCodes: List<String>
)

class BlockIntentPlanner {
    fun decide(state: AthletePlanningState, gaps: List<AdaptationGap>): BlockIntent {
        val preserveObserved = state.observedStrengthStyle !in setOf(StrengthProgrammingStyle.UNRESOLVED, StrengthProgrammingStyle.NONE) && state.observedStyleConfidence != PlanningConfidence.LOW
        val selectedStyle = when {
            preserveObserved -> state.observedStrengthStyle
            state.strengthIntent in setOf(StrengthIntent.HYPERTROPHY_PRIORITY, StrengthIntent.AVOID_HEAVY, StrengthIntent.UNRESOLVED) -> StrengthProgrammingStyle.NONE
            state.strengthTrainingIsNovice() -> StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS
            else -> StrengthProgrammingStyle.TOP_SET_BACKOFF
        }
        val duration = when (state.primaryAdaptation) {
            "HYPERTROPHY" -> 8 to 12
            "HYPERTROPHY_STRENGTH" -> 6 to 10
            "STRENGTH_SUPPORT", "BADMINTON_SUPPORT" -> 4 to 8
            else -> 6 to 12
        }
        val reasons = buildList {
            if (state.anchors.isNotEmpty()) add("최근 반복 사용한 ${state.anchors.take(3).joinToString { it.exerciseName }}을(를) 연속성 운동으로 유지했습니다.")
            add("기록된 저항훈련 행동은 ${state.observedBehavior.name}이며, 행동과 사용자의 의도는 별도로 판단했습니다.")
            if (gaps.isNotEmpty()) add("확인된 보완 대상은 ${gaps.take(3).joinToString { it.code }}입니다.")
            if (preserveObserved) add("수행 기록에서 확인된 ${selectedStyle.name} 구성을 유지했습니다.")
        }
        val constraints = buildList {
            if (state.strengthIntent == StrengthIntent.UNRESOLVED) add("고중량 선호가 미해결이어서 새로운 고중량 특화를 추가하지 않은 임시 계획입니다.")
            if (state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED) add("배드민턴 계획 의도가 미해결이어서 배드민턴 드릴을 새로 추가하지 않았습니다.")
            add("확인되지 않은 새 운동의 시작 중량은 RPE 기반으로 결정하며 기계 중량을 프리웨이트 중량으로 변환하지 않습니다.")
            add(state.recoveryConstraint)
            if (state.genericCourtLoad > 0.0) add("최근 일반 코트 부하 ${state.genericCourtLoad.roundToInt()}가 주간 빈도·밀도·회복 여유에 반영됐습니다. 이 부하는 Objective V2 자극으로 계산하지 않았습니다.")
            if (state.recoverySignals.tissueRestrictedStableKeys.isNotEmpty()) add("조직 회복 상태가 높은 기여 운동의 증량을 제한합니다.")
        }
        return BlockIntent(state.primaryAdaptation, duration.first, duration.second, selectedStyle, if (preserveObserved) "PRESERVED_INCUMBENT" else "AUTO_CONSERVATIVE", reasons, constraints, listOf("HISTORY_CUTOFF_ENFORCED", "STABLE_KEY_AUTHORITY", "CONTINUITY_HYSTERESIS") + state.recoverySignals.sourceCodes.sorted() + gaps.map { "GAP_${it.code}" })
    }
}

private fun AthletePlanningState.strengthTrainingIsNovice(): Boolean = confidence == PlanningConfidence.LOW
