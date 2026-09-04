package com.training.trackplanner.data.personalized

import kotlin.math.roundToInt
import kotlin.math.tanh

class PlanningQuestionPolicy {
    fun questions(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, answers: PersonalizedPlanningAnswers): List<PersonalizedPlanningQuestion> = buildList {
        val strengthPreferenceFresh = snapshot.preferences.strengthIntent != null &&
            snapshot.preferences.strengthIntentProfileGoal == snapshot.profilePrimaryGoal &&
            snapshot.preferences.strengthIntentAnsweredAtEpochMillis?.let { answeredAt ->
                val cutoffMillis = snapshot.cutoff.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                answeredAt <= cutoffMillis && cutoffMillis - answeredAt <= 56L * 24 * 60 * 60 * 1000
            } == true
        if (state.historyDays >= 42 && state.strengthExposure != StrengthExposure.PRESENT && !strengthPreferenceFresh && QUESTION_STRENGTH_INTENT !in answers.values) {
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
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.PREFER_FAMILIAR.name, "익숙한 방식 우선"),
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.AVOID.name, "피하고 싶음"),
                PersonalizedPlanningAnswerOption(FreeWeightWillingness.UNRESOLVED.name, "잘 모르겠음")
            )))
        }
    }
}

class AdaptationTransitionPlanner {
    private val confidenceScore = mapOf(PlanningConfidence.LOW to .40, PlanningConfidence.MODERATE to .75, PlanningConfidence.HIGH to .95)
    private val gapScore = mapOf("HIGH" to .85, "MEDIUM" to .55, "MODERATE" to .55, "LOW" to .30)
    private fun clip(value: Double, min: Double = 0.0, max: Double = 1.0) = value.coerceIn(min, max)

    fun systemicRecoveryPressure(signals: PlanningRecoverySignals): Double {
        val readiness = mapOf("READY" to 0.0, "NORMAL" to 0.0, "GOOD" to 0.0, "CAUTION" to .35, "FATIGUED" to .65, "LIMITED" to .85, "UNKNOWN" to .15)
            .getOrDefault(signals.readinessStatus, .15)
        val tissue =
            tissueRecoveryPressure(signals.tissueStatus)
        val ofi = signals.overallFatigueIndex?.let { clip((it - 55.0) / 35.0) } ?: .10
        return clip(.75 * maxOf(readiness, tissue, ofi) + .25 * ((readiness + tissue + ofi) / 3.0))
    }

    fun decide(anchor: UserAnchor, state: AthletePlanningState, gaps: List<AdaptationGap>): AnchorTransition {
        val features = state.styleFeaturesByAnchor[anchor.stableKey] ?: StyleFeatures()
        val canonical = anchor.canonicalPerformanceSource != "CANONICAL_SIGNAL_UNAVAILABLE" &&
            anchor.posteriorChangePercent?.isFinite() == true && anchor.posteriorObservationCount >= 2
        val response = if (canonical) tanh(requireNotNull(anchor.posteriorChangePercent) / 5.0) else 0.0
        val responseConfidence = if (canonical) clip(anchor.posteriorObservationCount / 6.0) else 0.0
        val maturity = clip(features.weeksObserved / 8.0)
        val rotationReadiness = clip(maturity * (.55 + .45 * maxOf(0.0, response) * maxOf(responseConfidence, .35)))
        val gapWeights = gaps.filter(AdaptationGap::contributesTransitionPressure).map { gapScore.getOrDefault(it.priority, .55) }
        val gapPressure = if (gapWeights.isEmpty()) 0.0 else clip(gapWeights.max() + .12 * (gapWeights.size - 1))
        val systemicRecovery = systemicRecoveryPressure(state.recoverySignals)
        val frequencyPressure = clip((features.weeklyFrequency - 2.0) / 2.0)
        val styleDemand = clip(.45 * features.heavyExposure + .35 * frequencyPressure + .20 * features.withinSessionRamping)
        val lowerAnchor = anchor.movementGroup in setOf(MovementCoverage.LOWER_KNEE.name, MovementCoverage.POSTERIOR_CHAIN.name)
        val sportInterference = if (lowerAnchor) clip((state.genericCourtLoad / 240.0) * styleDemand) else 0.0
        val goalAlignment = goalAlignment(state, features, styleDemand)
        val evidence = confidenceScore.getValue(anchor.styleConfidence)
        val continuityBase = .40 * evidence + .20 * features.frequencyStability + .20 * clip(.50 + .50 * response) + .20 * goalAlignment
        val rotation = clip(.45 * gapPressure + .30 * rotationReadiness + .25 * (1.0 - goalAlignment))
        val continuity = clip(continuityBase - .35 * rotation - .15 * maxOf(0.0, -response) * maxOf(responseConfidence, .35))
        var localDose = clip(1.0 - .22 * systemicRecovery - .18 * sportInterference, .65, 1.0)
        if (anchor.stableKey in state.recoverySignals.tissueRestrictedStableKeys) localDose = minOf(localDose, .80)
        val structure = when {
            continuity >= .76 && rotation < .42 -> StructureTreatment.PRESERVE
            continuity >= .60 && rotation >= .42 -> StructureTreatment.PRESERVE_CORE_REBALANCE
            continuity >= .42 -> StructureTreatment.PARTIAL_CONTINUITY
            else -> StructureTreatment.ROTATE_EMPHASIS
        }
        val dose = when {
            localDose >= .92 -> DoseTreatment.MAINTAIN
            localDose >= .82 -> DoseTreatment.REDUCE_SLIGHTLY
            else -> DoseTreatment.REDUCE_MODERATELY
        }
        val featureScores = mapOf(
            "straight_set_consistency" to features.straightSetConsistency,
            "top_set_backoff" to features.topSetBackoff,
            "load_undulation" to features.loadUndulation,
            "rep_zone_undulation" to features.repZoneUndulation,
            "hlm_ordering" to features.hlmOrdering,
            "within_session_ramping" to features.withinSessionRamping,
            "frequency_stability" to features.frequencyStability
        )
        val preserved = featureScores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .filter { it.value >= .55 + .15 * rotation }.map(Map.Entry<String, Double>::key)
        val moderated = buildList {
            if (maxOf(systemicRecovery, sportInterference) >= .45) {
                if (features.heavyExposure >= .55) add("heavy_exposure")
                if (features.weeklyFrequency >= 3.0) add("weekly_frequency")
                if (features.withinSessionRamping >= .30) add("within_session_ramping")
            }
            if (state.strengthIntent in setOf(StrengthIntent.AVOID_HEAVY, StrengthIntent.HYPERTROPHY_PRIORITY, StrengthIntent.UNRESOLVED) && features.heavyExposure > 0.0) add("heavy_exposure")
            if (rotation >= .50 && features.repZoneUndulation < .35) add("single_rep_zone_dominance")
        }.distinct()
        return AnchorTransition(
            stableKey = anchor.stableKey,
            observedStyle = anchor.style,
            observedConfidence = anchor.styleConfidence,
            styleFeatures = features,
            adaptation = AdaptationState(response, responseConfidence, maturity, rotationReadiness, gapPressure, systemicRecovery, sportInterference, goalAlignment, styleDemand),
            structureTreatment = structure,
            doseTreatment = dose,
            continuityScore = continuity,
            localDoseFactor = localDose,
            rotationPressure = rotation,
            preservedFeatures = preserved,
            moderatedFeatures = moderated,
            reasons = buildList {
                if (evidence >= .75) add("반복 기록에서 신뢰할 수 있는 기존 구성이 확인됐습니다.")
                if (gapPressure >= .55) add("확인된 보완 대상 때문에 다음 블록의 배분을 조정했습니다.")
                if (systemicRecovery >= .45) add("회복 신호에 따라 구성보다 용량을 먼저 낮췄습니다.")
                if (sportInterference >= .30) add("실제 주간 코트 부하가 하체 앵커의 비용을 높였습니다.")
                if (anchor.stableKey in state.recoverySignals.tissueRestrictedStableKeys) add("조직 제한 stableKey라서 이 앵커의 증량을 차단했습니다.")
            }
        )
    }

    private fun goalAlignment(state: AthletePlanningState, features: StyleFeatures, styleDemand: Double): Double {
        val base = when (state.primaryAdaptation) {
            "BADMINTON_SUPPORT" -> clip(1.0 - .28 * styleDemand)
            "HYPERTROPHY" -> clip(.92 - .25 * features.heavyExposure + .15 * features.moderateHighRepExposure)
            "HYPERTROPHY_STRENGTH" -> clip(.90 + .05 * features.moderateHighRepExposure)
            "STRENGTH_SUPPORT", "STRENGTH_MAINTENANCE" -> 1.0
            "RECOVERY_FOUNDATION", "CONDITIONING_FOUNDATION" -> clip(.78 - .25 * styleDemand)
            else -> .85
        }
        return when (state.strengthIntent) {
            StrengthIntent.STRENGTH_PRIORITY -> clip(base + .08 * features.heavyExposure)
            StrengthIntent.MIXED -> base
            StrengthIntent.HYPERTROPHY_PRIORITY -> clip(base - .12 * features.heavyExposure)
            StrengthIntent.AVOID_HEAVY -> clip(base - .25 * features.heavyExposure)
            StrengthIntent.UNRESOLVED -> clip(base - .10 * features.heavyExposure)
        }
    }
}

class AdaptationGapAnalyzer {
    fun analyze(snapshot: PlanningHistorySnapshot, state: AthletePlanningState): List<AdaptationGap> {
        val gaps = mutableListOf<AdaptationGap>()
        if (state.resistanceFoundationalOnramp) {
            gaps += AdaptationGap(
                code = "RESISTANCE_FOUNDATIONAL_ONRAMP",
                priority = "MODERATE",
                reason = "최근 28일 저항운동 기록이 없어 균형 잡힌 재진입 구조를 사용합니다.",
                sourceType = "MOVEMENT_REPRESENTATION",
                evidenceConfidence = PlanningConfidence.LOW,
                reasonCodes = listOf("NO_CURRENT_RESISTANCE_EXPOSURE", "SINGLE_FOUNDATIONAL_ONRAMP")
            )
        } else {
            state.movementRepresentations.forEach { representation ->
                val priority = ExposureRepresentationPolicy.movementGapPriority(
                    representation.basePriority,
                    representation.representationState,
                    representation.evidenceConfidence
                ) ?: return@forEach
                gaps += representation.toGap(priority)
            }
        }

        if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED) {
            if (state.badmintonFoundationalOnramp) {
                gaps += AdaptationGap(
                    code = "BADMINTON_FOUNDATIONAL_ONRAMP",
                    priority = "HIGH",
                    reason = "배드민턴 계획 의도는 활성화됐지만 최근 구조화 훈련 기록이 없어 기초 온램프를 사용합니다.",
                    sourceType = "BADMINTON_OBJECTIVE_REPRESENTATION",
                    reasonCodes = listOf("NO_STRUCTURED_BADMINTON_HISTORY", "SINGLE_FOUNDATIONAL_ONRAMP")
                )
            } else state.badmintonObjectiveRepresentations.forEach { representation ->
                when {
                    representation.directDrop -> gaps += representation.toGap(
                        code = "BADMINTON_DROP_${representation.objective}",
                        priority = "HIGH",
                        reason = "최근 직접 ${representation.objective} 훈련 기록이 직전 기간 대비 사라졌습니다."
                    )
                    representation.representationState in setOf(
                        RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL,
                        RepresentationState.UNDERREPRESENTATION_SIGNAL
                    ) -> {
                        val peerOnly = "PEER_ONLY_UNDERREPRESENTATION" in representation.reasonCodes
                        val priority = requireNotNull(ExposureRepresentationPolicy.badmintonGapPriority(
                            representation.representationState,
                            representation.evidenceConfidence,
                            peerOnly,
                            directDrop = false
                        ))
                        gaps += representation.toGap(
                            code = "BADMINTON_UNDERREPRESENTED_${representation.objective}",
                            priority = priority,
                            reason = "최근 ${representation.objective} 자극의 상대적 비중이 직전 기록 또는 현재 비교축보다 크게 낮았습니다."
                        )
                    }
                }
            }
            if (!state.badmintonFoundationalOnramp) {
                state.badmintonObjectiveRepresentations
                    .asSequence()
                    .filter(BadmintonObjectiveRepresentation::neverDirectObserved)
                    .sortedBy(BadmintonObjectiveRepresentation::objective)
                    .firstOrNull()
                    ?.let { representation ->
                        gaps += representation.toGap(
                            code = "BADMINTON_DEVELOP_${representation.objective}",
                            priority = "LOW",
                            reason = "${representation.objective} 직접 훈련은 두 비교 기간 모두 관찰되지 않아 선택적 발달 후보로만 보존합니다.",
                            contributesTransitionPressure = false
                        )
                    }
            }
        }

        if (state.primaryAdaptation.startsWith("HYPERTROPHY") && state.hypertrophyStimulusByMovement.isNotEmpty()) {
            val positive = state.hypertrophyStimulusByMovement.filterValues { it > 0.0 }
            val max = positive.maxByOrNull { it.value }
            val min = positive.minByOrNull { it.value }
            if (max != null && min != null && max.value >= min.value * 3.0) {
                val existingIndex = gaps.indexOfFirst { it.code == min.key.name }
                if (existingIndex >= 0) {
                    val existing = gaps[existingIndex]
                    gaps[existingIndex] = existing.copy(reasonCodes = (existing.reasonCodes + "HYPERTROPHY_STIMULUS_SKEW").distinct())
                } else {
                    gaps += AdaptationGap(
                        "HYPERTROPHY_REBALANCE_${min.key.name}",
                        "HIGH",
                        "기존 유효 근비대 자극 기록이 ${max.key.name}에 크게 치우쳐 ${min.key.name} 재배분 후보를 만들었습니다.",
                        sourceType = "HYPERTROPHY_STIMULUS",
                        reasonCodes = listOf("HYPERTROPHY_STIMULUS_SKEW")
                    )
                }
            }
        }
        return gaps
    }

    private fun MovementExposureRepresentation.toGap(priority: String) = AdaptationGap(
        code = movementCoverage,
        priority = priority,
        reason = when (representationState) {
            RepresentationState.ABSENT -> "최근 28일 $movementCoverage 저항운동 기록이 없습니다."
            RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL -> "최근 $movementCoverage 비중이 같은 우선순위 운동 또는 직전 분포보다 현저히 낮았습니다."
            else -> "최근 $movementCoverage 비중이 같은 우선순위 운동 또는 직전 분포보다 낮았습니다."
        },
        sourceType = "MOVEMENT_REPRESENTATION",
        representationState = representationState,
        evidenceConfidence = evidenceConfidence,
        currentExposure = currentExposure28d,
        priorExposure = priorExposure28d,
        currentShare = currentShare,
        priorShare = priorShare,
        peerRatio = peerRepresentationRatio,
        personalRetentionRatio = personalRetentionRatio,
        reasonCodes = reasonCodes
    )

    private fun BadmintonObjectiveRepresentation.toGap(
        code: String,
        priority: String,
        reason: String,
        contributesTransitionPressure: Boolean = true
    ) = AdaptationGap(
        code = code,
        priority = priority,
        reason = reason,
        sourceType = "BADMINTON_OBJECTIVE_REPRESENTATION",
        representationState = representationState,
        evidenceConfidence = evidenceConfidence,
        currentExposure = currentWeighted28d,
        priorExposure = priorWeighted28d,
        currentShare = currentShare,
        priorShare = priorShare,
        peerRatio = peerRepresentationRatio,
        personalRetentionRatio = personalRetentionRatio,
        reasonCodes = reasonCodes,
        contributesTransitionPressure = contributesTransitionPressure
    )
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
        // Kept only for the legacy BlockIntent shape. Future structure is decided per anchor.
        val selectedStyle = StrengthProgrammingStyle.NONE
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
            if (preserveObserved) add("수행 기록에서 ${state.observedStrengthStyle.name} 구성을 관찰했습니다. 다음 블록 처리는 앵커별 전환 판단으로 별도 결정합니다.")
        }
        val constraints = buildList {
            if (state.strengthIntent == StrengthIntent.UNRESOLVED) add("고중량 선호가 미해결이어서 새로운 고중량 특화를 추가하지 않은 임시 계획입니다.")
            if (state.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED) add("배드민턴 계획 의도가 미해결이어서 배드민턴 드릴을 새로 추가하지 않았습니다.")
            add("확인되지 않은 새 운동의 시작 중량은 RPE 기반으로 결정하며 기계 중량을 프리웨이트 중량으로 변환하지 않습니다.")
            add(state.recoveryConstraint)
            if (state.genericCourtLoad > 0.0) add("최근 일반 코트 부하 ${state.genericCourtLoad.roundToInt()}가 주간 빈도·밀도·회복 여유에 반영됐습니다. 이 부하는 Objective V2 자극으로 계산하지 않았습니다.")
            if (state.recoverySignals.tissueRestrictedStableKeys.isNotEmpty()) add("조직 회복 상태가 높은 기여 운동의 증량을 제한합니다.")
        }
        return BlockIntent(state.primaryAdaptation, duration.first, duration.second, selectedStyle, if (preserveObserved) "OBSERVED_HISTORY_ONLY" else "UNRESOLVED_OBSERVATION", reasons, constraints, listOf("HISTORY_CUTOFF_ENFORCED", "FIXED_56_DAY_DECISION_WINDOW", "STABLE_KEY_AUTHORITY", "CONTINUITY_HYSTERESIS") + state.recoverySignals.sourceCodes.sorted() + gaps.map { "GAP_${it.code}" })
    }
}

private fun AthletePlanningState.strengthTrainingIsNovice(): Boolean = confidence == PlanningConfidence.LOW

internal fun tissueRecoveryPressure(status: String): Double = when (status) {
    "NORMAL" -> 0.0
    "LOW" -> .10
    "MODERATE", "ELEVATED" -> .30
    "HIGH" -> .60
    "VERY_HIGH" -> .85
    "BLOCKED" -> 1.0
    else -> .10
}
