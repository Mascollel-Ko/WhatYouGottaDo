package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.BvarDesignMatrixMaterializer
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarComparisonDesign
import com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningResult
import com.training.trackplanner.analysis.lab.strictbayes.StrictBayesianFailureCode
import com.training.trackplanner.analysis.lab.strictbayes.StrictBayesianSamplingStage
import com.training.trackplanner.analysis.lab.strictbayes.StrictBayesianV07Outcome
import com.training.trackplanner.analysis.lab.strictbayes.StrictBayesianV07Sampler
import com.training.trackplanner.analysis.lab.strictbayes.StrictSamplingPolicy
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal open class StrictBayesianLabService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val samplerFactory: (
        PreparedBvarComparisonDesign,
        StrictSamplingPolicy,
        Int
    ) -> StrictBayesianV07Sampler = { design, policy, retryAttempt ->
        StrictBayesianV07Sampler(design, policy, retryAttempt)
    }
) {
    open suspend fun preflight(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest
    ): StrictLabPreflight = withContext(dispatcher) {
        val normalized = request.normalized()
        val requested = (listOf(normalized.xFeature) + normalized.yFeatures + normalized.controls).distinct()
        val blockers = buildList {
            if (normalized.requestedHorizon !in 1..8) add(StrictLabBlocker(StrictLabBlockerCode.INVALID_HORIZON))
            if (normalized.yFeatures.isEmpty()) add(StrictLabBlocker(StrictLabBlockerCode.RESPONSE_REQUIRED))
            requested.filterNot { it in snapshot.descriptors }.forEach { feature ->
                add(StrictLabBlocker(StrictLabBlockerCode.FEATURE_UNAVAILABLE, feature))
            }
            requested.filter { it in snapshot.featureAvailabilityIndex }.forEach { feature ->
                val availability = snapshot.featureAvailabilityIndex.getValue(feature)
                when {
                    !availability.hasData -> add(
                        StrictLabBlocker(StrictLabBlockerCode.FEATURE_UNAVAILABLE, feature, "no closed-week values")
                    )
                    !availability.hasVariation -> add(
                        StrictLabBlocker(StrictLabBlockerCode.INSUFFICIENT_VARIATION, feature, "closed-week values are constant")
                    )
                }
            }
        }
        StrictLabPreflight(
            snapshotFingerprint = snapshot.fingerprint,
            availableFrom = snapshot.closedWeeks.firstOrNull(),
            availableUntil = snapshot.closedWeeks.lastOrNull(),
            closedWeeks = snapshot.closedWeeks.size,
            blockers = blockers.distinct(),
            warnings = buildList {
                if (snapshot.closedWeeks.size < 32) {
                    add("적분 진단 표본은 짧지만 승인된 단기 기록 표현으로 가능한 엄격 모형을 시도합니다.")
                }
            }
        )
    }

    open suspend fun execute(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        request: StrictLabAnalysisRequest,
        preflight: StrictLabPreflight,
        analysisMode: StrictLabAnalysisMode = StrictLabAnalysisMode.STRICT,
        retryAttempt: Int = 0,
        onStage: (StrictLabExecutionStage) -> Unit = {}
    ): StrictLabExecutionOutcome = withContext(dispatcher) {
        require(retryAttempt >= 0)
        val samplingReliabilityMode = analysisMode.toSamplingMode()
        val context = currentCoroutineContext()
        if (!preflight.canAnalyze) {
            return@withContext StrictLabExecutionOutcome.Failure(
                StrictFailureDiagnostics(
                    code = StrictLabFailureCode.PREFLIGHT_INELIGIBLE,
                    stage = StrictFailureStage.PREFLIGHT,
                    primaryReason = "선택한 지표 조합은 엄격 분석을 시작할 수 없습니다.",
                    affectedFeatureOrSource = preflight.blockers.firstNotNullOfOrNull { it.feature?.value },
                    availableClosedWeeks = preflight.closedWeeks,
                    analysisMode = analysisMode,
                    samplingReliabilityMode = samplingReliabilityMode,
                    retryAttempt = retryAttempt,
                    technicalDetails = preflight.blockers.map { "${it.code}:${it.feature}:${it.detail}" }
                )
            )
        }
        if (preflight.snapshotFingerprint != snapshot.fingerprint) {
            return@withContext StrictLabExecutionOutcome.Failure(
                StrictFailureDiagnostics(
                    code = StrictLabFailureCode.STALE_RESULT_REJECTED,
                    stage = StrictFailureStage.COORDINATION,
                    primaryReason = "분석 데이터가 갱신되어 다시 준비해야 합니다.",
                    availableClosedWeeks = snapshot.closedWeeks.size,
                    analysisMode = analysisMode,
                    samplingReliabilityMode = samplingReliabilityMode,
                    retryAttempt = retryAttempt,
                    technicalDetails = listOf(
                        "preflight=${preflight.snapshotFingerprint}",
                        "snapshot=${snapshot.fingerprint}"
                    )
                )
            )
        }
        try {
            onStage(StrictLabExecutionStage.PREPARING_STRICT_INPUT)
            val planningOutcome = StrictLabRelaxedPlanningPolicy.plan(snapshot, request, analysisMode)
            if (planningOutcome is StrictLabPlanningOutcome.Failure) {
                return@withContext StrictLabExecutionOutcome.Failure(
                    phaseAFailureDiagnostics(
                        snapshot,
                        planningOutcome,
                        analysisMode,
                        retryAttempt
                    )
                )
            }
            planningOutcome as StrictLabPlanningOutcome.Success
            val planned = planningOutcome.planned
            val design = BvarDesignMatrixMaterializer.materialize(planned.context, planned.input)
            val samplingPolicy = analysisMode.samplingPolicy()
            val relaxationTrace = planningOutcome.relaxationTrace.withSamplingMode(analysisMode)
            val sampled = samplerFactory(design, samplingPolicy, retryAttempt).sample(
                onStage = { stage -> onStage(stage.toExecutionStage()) },
                isCancelled = { !context.isActive }
            )
            when (sampled) {
                is StrictBayesianV07Outcome.Success -> {
                    val descriptors = snapshot.descriptors
                    val responses = sampled.result.responses.map { (feature, summaries) ->
                        val key = feature as? AnalysisFeatureKey
                            ?: error("strict response feature has no AnalysisFeatureKey identity")
                        StrictLabResponse(
                            feature = key,
                            displayName = StrictLabFeatureCatalog.from(snapshot).option(key)?.displayName
                                ?: descriptors.getValue(key).displayName,
                            points = summaries.map { summary ->
                                StrictLabResponsePoint(
                                    horizonWeeks = summary.horizonWeeks,
                                    estimate = summary.posterior.median,
                                    low80 = summary.posterior.lower80,
                                    high80 = summary.posterior.upper80,
                                    diagnostics = summary.posterior
                                )
                            }
                        )
                    }
                    val simplifications = buildList {
                        addAll(planned.diagnostics.filter { diagnostic ->
                            diagnostic.contains("reduced", ignoreCase = true) ||
                                diagnostic.contains("removed auto candidate", ignoreCase = true) ||
                                diagnostic.contains("feasible lag", ignoreCase = true)
                        })
                        addAll(relaxationTrace.representationOverrides)
                        addAll(relaxationTrace.planningDetails)
                    }.distinct()
                    StrictLabExecutionOutcome.Success(
                        StrictBayesianLabResult(
                            request = planningOutcome.originalRequest,
                            responses = responses,
                            officialLagProbability = sampled.result.officialLagProbability,
                            simplificationDiagnostics = simplifications,
                            summary = when {
                                analysisMode == StrictLabAnalysisMode.RELAXED ->
                                    "완화된 분석 기준으로 탐색적 Bayesian posterior를 계산했습니다."
                                simplifications.isEmpty() ->
                                    "엄격 Bayesian posterior를 계산했습니다. 구간이 넓으면 관계의 불확실성이 큰 것으로 해석하세요."
                                else -> "현재 기록에서 계산 가능한 더 단순한 엄격 모형으로 posterior를 계산했습니다."
                            },
                            preparedInputFingerprint = sampled.result.preparedInputFingerprint,
                            posteriorFingerprint = sampled.result.fingerprint,
                            effectiveRequest = planningOutcome.effectiveRequest,
                            analysisMode = analysisMode,
                            relaxationTrace = relaxationTrace,
                            preparationPolicyFingerprint = planningOutcome.bundle.policy.fingerprint,
                            effectivePlanFingerprint = planned.input.fingerprint,
                            samplingReliabilityMode = sampled.result.samplingReliabilityMode,
                            samplingPolicyFingerprint = sampled.result.samplingPolicyFingerprint,
                            retryAttempt = sampled.result.retryAttempt,
                            samplingIdentityFingerprint = sampled.result.samplingIdentityFingerprint
                        )
                    )
                }
                is StrictBayesianV07Outcome.Failure -> {
                    val failureCode = sampled.failure.code
                    StrictLabExecutionOutcome.Failure(
                        sampled.failure.copy(
                            primaryReason = sampled.code.userMessage(sampled.failure.stage),
                            affectedFeatureOrSource = sampled.failure.observations
                                .firstOrNull { it.passed == false }?.name
                                ?: sampled.failure.affectedFeatureOrSource,
                            analysisMode = analysisMode,
                            availableRelaxationRoutes = StrictLabRelaxedPlanningPolicy.availableRoutes(
                                snapshot,
                                planningOutcome.originalRequest,
                                failureCode,
                                analysisMode
                            ),
                            attemptedRelaxationRoutes = relaxationTrace.attemptedRoutes,
                            appliedRelaxationRoutes = relaxationTrace.appliedRoutes,
                            originalControls = planningOutcome.originalRequest.controls.map { it.value },
                            effectiveControls = planningOutcome.effectiveRequest.controls.map { it.value },
                            representationOverrides = relaxationTrace.representationOverrides,
                            snapshotFingerprint = snapshot.fingerprint,
                            preparationPolicyFingerprint = planningOutcome.bundle.policy.fingerprint,
                            effectivePlanFingerprint = planned.input.fingerprint,
                            samplingIdentityFingerprint = sampled.failure.samplingIdentityFingerprint
                        )
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            val diagnosticId = diagnosticId(failure)
            LOGGER.log(Level.SEVERE, "$diagnosticId strict Bayesian Lab execution failed", failure)
            StrictLabExecutionOutcome.Failure(
                StrictFailureDiagnostics(
                    code = StrictLabFailureCode.INTERNAL_ERROR,
                    stage = StrictFailureStage.INTERNAL,
                    primaryReason = "엄격 Bayesian 분석을 완료하지 못했습니다.",
                    availableClosedWeeks = snapshot.closedWeeks.size,
                    analysisMode = analysisMode,
                    samplingReliabilityMode = samplingReliabilityMode,
                    retryAttempt = retryAttempt,
                    originalControls = request.controls.map { it.value },
                    effectiveControls = request.controls.map { it.value },
                    snapshotFingerprint = snapshot.fingerprint,
                    technicalDetails = listOfNotNull(failure::class.qualifiedName, failure.message),
                    diagnosticId = diagnosticId
                )
            )
        }
    }

    private fun phaseAFailureDiagnostics(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        outcome: StrictLabPlanningOutcome.Failure,
        analysisMode: StrictLabAnalysisMode,
        retryAttempt: Int
    ): StrictFailureDiagnostics {
        val request = outcome.originalRequest
        val failure = outcome.planned
        val code = failure.code.toLabFailureCode()
        val affected = when (code) {
            StrictLabFailureCode.NO_TARGET_VARIATION -> request.yFeatures.firstOrNull()
            StrictLabFailureCode.FOCAL_FEATURE_UNAVAILABLE,
            StrictLabFailureCode.NO_FOCAL_VARIATION -> request.xFeature
            else -> null
        }
        val availability = affected?.let(snapshot.featureAvailabilityIndex::get)
        val routes = StrictLabRelaxedPlanningPolicy.availableRoutes(
            snapshot,
            request,
            code,
            analysisMode,
            failure.diagnostics
        )
        return StrictFailureDiagnostics(
            code = code,
            stage = StrictFailureStage.PHASE_A,
            primaryReason = phaseAFailureMessage(failure),
            affectedFeatureOrSource = affected?.value,
            availableClosedWeeks = snapshot.closedWeeks.size,
            attemptedLags = (1..4).toList(),
            attemptedSimplifications = failure.diagnostics.filter { diagnostic ->
                diagnostic.contains("removed auto candidate", ignoreCase = true) ||
                    diagnostic.contains("reduced Pmax", ignoreCase = true)
            },
            observations = buildList {
                availability?.let {
                    add(
                        StrictDiagnosticObservation(
                            "${affected.value}.usableClosedWeeks",
                            it.activeWeeks.toString()
                        )
                    )
                    add(
                        StrictDiagnosticObservation(
                            "${affected.value}.distinctFiniteValues",
                            it.distinctFiniteValues.toString(),
                            "at least 2 for variation",
                            it.hasVariation
                        )
                    )
                }
            },
            analysisMode = analysisMode,
            samplingReliabilityMode = analysisMode.toSamplingMode(),
            retryAttempt = retryAttempt,
            availableRelaxationRoutes = routes,
            attemptedRelaxationRoutes = outcome.relaxationTrace.attemptedRoutes,
            appliedRelaxationRoutes = outcome.relaxationTrace.appliedRoutes,
            originalControls = outcome.originalRequest.controls.map { it.value },
            effectiveControls = outcome.effectiveRequest.controls.map { it.value },
            representationOverrides = outcome.relaxationTrace.representationOverrides,
            attemptedCommonRowsByPmax = failure.attemptedCommonRowsByPmax,
            snapshotFingerprint = snapshot.fingerprint,
            preparationPolicyFingerprint = outcome.bundle.policy.fingerprint,
            technicalDetails = failure.diagnostics
        )
    }

    private fun phaseAFailureMessage(failure: StrictBvarPlanningResult.Failure): String = when (failure.code) {
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.FOCAL_FEATURE_UNAVAILABLE ->
            "선택한 핵심 지표를 엄격 모형 표현으로 준비할 수 없습니다."
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_FOCAL_VARIATION,
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_TARGET_VARIATION ->
            "선택한 지표의 주간 변화가 충분하지 않습니다."
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN ->
            "공통 주간 행을 유지하는 시차 모형을 만들 수 없습니다."
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.METADATA_INCOMPLETE ->
            "선택한 지표에 필요한 canonical metadata가 완전하지 않습니다."
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.REPRESENTATION_POLICY_UNAVAILABLE ->
            "선택한 지표에 적용할 승인된 단기 기록 표현 정책이 없습니다."
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT ->
            "기록 길이 부족이 아니라 표현 진단과 승인 정책이 서로 충돌했습니다."
        else -> "현재 기록으로 승인된 엄격 모형 입력을 만들 수 없습니다."
    }

    private fun diagnosticId(failure: Throwable): String =
        "SB-${"${failure::class.qualifiedName}:${failure.message}".hashCode().toUInt().toString(16).uppercase()}"

    private companion object {
        val LOGGER: Logger = Logger.getLogger(StrictBayesianLabService::class.java.name)
    }
}

private fun com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.toLabFailureCode(): StrictLabFailureCode =
    when (this) {
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.FOCAL_FEATURE_UNAVAILABLE ->
            StrictLabFailureCode.FOCAL_FEATURE_UNAVAILABLE
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_TARGET_VARIATION ->
            StrictLabFailureCode.NO_TARGET_VARIATION
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_FOCAL_VARIATION ->
            StrictLabFailureCode.NO_FOCAL_VARIATION
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN ->
            StrictLabFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.METADATA_INCOMPLETE ->
            StrictLabFailureCode.METADATA_INCOMPLETE
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.REPRESENTATION_POLICY_UNAVAILABLE ->
            StrictLabFailureCode.REPRESENTATION_POLICY_UNAVAILABLE
        com.training.trackplanner.analysis.lab.pipeline.StrictBvarPlanningFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT ->
            StrictLabFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT
        else -> StrictLabFailureCode.PREFLIGHT_INELIGIBLE
    }

private fun StrictBayesianSamplingStage.toExecutionStage(): StrictLabExecutionStage = when (this) {
    StrictBayesianSamplingStage.STABILIZING -> StrictLabExecutionStage.STABILIZING_CHAINS
    StrictBayesianSamplingStage.SAMPLING_POSTERIOR -> StrictLabExecutionStage.SAMPLING_POSTERIOR
    StrictBayesianSamplingStage.CHECKING_RELIABILITY -> StrictLabExecutionStage.CHECKING_PRECISION
    StrictBayesianSamplingStage.EXTENDING_SAMPLING -> StrictLabExecutionStage.EXTENDING_SAMPLING
    StrictBayesianSamplingStage.SUMMARIZING -> StrictLabExecutionStage.SUMMARIZING_POSTERIOR
}

private fun StrictLabAnalysisMode.toSamplingMode(): StrictSamplingReliabilityMode = when (this) {
    StrictLabAnalysisMode.STRICT -> StrictSamplingReliabilityMode.STRICT
    StrictLabAnalysisMode.RELAXED -> StrictSamplingReliabilityMode.RELAXED
}

internal fun StrictLabAnalysisMode.samplingPolicy(): StrictSamplingPolicy = when (this) {
    StrictLabAnalysisMode.STRICT -> StrictSamplingPolicy.appRuntime()
    StrictLabAnalysisMode.RELAXED -> StrictSamplingPolicy.relaxedAppRuntime()
}

private fun StrictRelaxationTrace.withSamplingMode(mode: StrictLabAnalysisMode): StrictRelaxationTrace =
    if (mode == StrictLabAnalysisMode.RELAXED) {
        copy(
            attemptedRoutes = attemptedRoutes + StrictRelaxationRoute.RELAX_SAMPLING_RELIABILITY,
            appliedRoutes = appliedRoutes + StrictRelaxationRoute.RELAX_SAMPLING_RELIABILITY
        )
    } else {
        this
    }

private fun StrictBayesianFailureCode.userMessage(stage: StrictFailureStage): String = when (this) {
    StrictBayesianFailureCode.MCMC_CONVERGENCE_FAILED -> if (stage == StrictFailureStage.STABILIZATION) {
        "Bayesian chain의 시차 상태와 일부 반응 추정치가 충분히 안정되지 않았습니다."
    } else {
        "Bayesian chain의 일부 추정치가 엄격 신뢰도 기준에 도달하지 못했습니다."
    }
    StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED -> "시차 posterior의 혼합 신뢰도를 확보하지 못했습니다."
    StrictBayesianFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED -> "허용된 계산 범위에서 posterior 정밀도에 도달하지 못했습니다."
    StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE -> "행렬 계산의 수치 신뢰도 조건을 통과하지 못했습니다."
    StrictBayesianFailureCode.NONFINITE_STATE -> "posterior 계산 중 유한하지 않은 수치가 발생했습니다."
    StrictBayesianFailureCode.CANCELLED -> "분석이 취소되었습니다."
}
