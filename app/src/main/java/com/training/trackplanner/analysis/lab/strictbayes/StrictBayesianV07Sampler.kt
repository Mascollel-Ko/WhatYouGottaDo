package com.training.trackplanner.analysis.lab.strictbayes

import com.training.trackplanner.analysis.lab.StrictDiagnosticObservation
import com.training.trackplanner.analysis.lab.StrictFailureDiagnostics
import com.training.trackplanner.analysis.lab.StrictFailureStage
import com.training.trackplanner.analysis.lab.StrictLabFailureCode
import com.training.trackplanner.analysis.lab.StrictSamplingReliabilityMode
import com.training.trackplanner.analysis.lab.StrictSamplingAssessment
import com.training.trackplanner.analysis.lab.StrictSamplingDiagnosticClassification
import com.training.trackplanner.analysis.lab.StrictSamplingDiagnosticWindow
import com.training.trackplanner.analysis.lab.StrictSamplingPolicySnapshot
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.PreparedBvarComparisonDesign
import com.training.trackplanner.analysis.lab.pipeline.StrictSeriesKey
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class StrictSamplingMode {
    APP_RUNTIME,
    VALIDATION
}

internal data class StrictSamplingPolicy(
    val mode: StrictSamplingMode,
    val reliabilityMode: StrictSamplingReliabilityMode,
    val chains: Int,
    val stabilizationMinimum: Int,
    val diagnosticWindow: Int,
    val blockSize: Int,
    val consecutiveStabilizationPasses: Int,
    val stabilizationCap: Int,
    val productionMinimum: Int,
    val productionMaximum: Int,
    val precisionExtensionMaximum: Int,
    val maximumRhat: Double,
    val minimumEss: Double,
    val maximumMcseToSd: Double,
    val fingerprint: String
) {
    fun snapshot(identity: String = reliabilityMode.name): StrictSamplingPolicySnapshot =
        StrictSamplingPolicySnapshot(
            identity = identity,
            chains = chains,
            maximumRhat = maximumRhat,
            minimumEss = minimumEss,
            maximumMcseToSd = maximumMcseToSd,
            consecutiveStabilizationPasses = consecutiveStabilizationPasses,
            stabilizationCap = stabilizationCap,
            productionMaximum = productionMaximum,
            precisionExtensionMaximum = precisionExtensionMaximum,
            fingerprint = fingerprint
        )

    companion object {
        fun appRuntime(): StrictSamplingPolicy = create(
            StrictSamplingMode.APP_RUNTIME,
            StrictSamplingReliabilityMode.STRICT,
            chains = 4,
            stabilizationMinimum = 500,
            diagnosticWindow = 500,
            blockSize = 250,
            consecutiveStabilizationPasses = 2,
            stabilizationCap = 2_000,
            productionMinimum = 500,
            productionMaximum = 5_000,
            precisionExtensionMaximum = 10_000,
            minimumEss = 100.0,
            maximumMcseToSd = 0.10
        )

        fun validation(): StrictSamplingPolicy = create(
            StrictSamplingMode.VALIDATION,
            StrictSamplingReliabilityMode.STRICT,
            chains = 4,
            stabilizationMinimum = 500,
            diagnosticWindow = 500,
            blockSize = 250,
            consecutiveStabilizationPasses = 2,
            stabilizationCap = 2_000,
            productionMinimum = 500,
            productionMaximum = 5_000,
            precisionExtensionMaximum = 10_000,
            minimumEss = 400.0,
            maximumMcseToSd = 0.05
        )

        fun relaxedAppRuntime(): StrictSamplingPolicy = create(
            StrictSamplingMode.APP_RUNTIME,
            StrictSamplingReliabilityMode.RELAXED,
            chains = 4,
            stabilizationMinimum = 500,
            diagnosticWindow = 500,
            blockSize = 250,
            consecutiveStabilizationPasses = 1,
            stabilizationCap = 4_000,
            productionMinimum = 500,
            productionMaximum = 5_000,
            precisionExtensionMaximum = 10_000,
            minimumEss = 50.0,
            maximumMcseToSd = 0.20,
            maximumRhat = 1.05
        )

        fun relaxedContinuation(strict: StrictSamplingPolicy): StrictSamplingPolicy =
            if (strict.mode == StrictSamplingMode.APP_RUNTIME) {
                relaxedAppRuntime()
            } else {
                create(
                    mode = strict.mode,
                    reliabilityMode = StrictSamplingReliabilityMode.RELAXED,
                    chains = strict.chains,
                    stabilizationMinimum = strict.stabilizationMinimum,
                    diagnosticWindow = strict.diagnosticWindow,
                    blockSize = strict.blockSize,
                    consecutiveStabilizationPasses = 1,
                    stabilizationCap = max(strict.stabilizationCap, strict.stabilizationMinimum),
                    productionMinimum = strict.productionMinimum,
                    productionMaximum = strict.productionMaximum,
                    precisionExtensionMaximum = strict.precisionExtensionMaximum,
                    minimumEss = min(strict.minimumEss, 50.0),
                    maximumMcseToSd = max(strict.maximumMcseToSd, 0.20),
                    maximumRhat = max(strict.maximumRhat, 1.05)
                )
            }

        internal fun testing(
            chains: Int = 4,
            stabilization: Int = 20,
            production: Int = 40,
            consecutiveStabilizationPasses: Int = 1,
            stabilizationCap: Int = stabilization,
            productionMaximum: Int = production,
            precisionExtensionMaximum: Int = productionMaximum,
            maximumRhat: Double = 10.0,
            minimumEss: Double = 1.0,
            maximumMcseToSd: Double = 1.0,
            reliabilityMode: StrictSamplingReliabilityMode = StrictSamplingReliabilityMode.STRICT
        ): StrictSamplingPolicy = create(
            StrictSamplingMode.VALIDATION,
            reliabilityMode,
            chains,
            stabilization,
            stabilization,
            blockSize = 10,
            consecutiveStabilizationPasses = consecutiveStabilizationPasses,
            stabilizationCap = stabilizationCap,
            productionMinimum = production,
            productionMaximum = productionMaximum,
            precisionExtensionMaximum = precisionExtensionMaximum,
            minimumEss = minimumEss,
            maximumMcseToSd = maximumMcseToSd,
            maximumRhat = maximumRhat
        )

        private fun create(
            mode: StrictSamplingMode,
            reliabilityMode: StrictSamplingReliabilityMode,
            chains: Int,
            stabilizationMinimum: Int,
            diagnosticWindow: Int,
            blockSize: Int,
            consecutiveStabilizationPasses: Int,
            stabilizationCap: Int,
            productionMinimum: Int,
            productionMaximum: Int,
            precisionExtensionMaximum: Int,
            minimumEss: Double,
            maximumMcseToSd: Double,
            maximumRhat: Double = 1.01
        ): StrictSamplingPolicy {
            require(chains >= 4 && stabilizationMinimum > 0 && diagnosticWindow > 0 && blockSize > 0)
            require(stabilizationCap >= stabilizationMinimum)
            require(productionMaximum >= productionMinimum && precisionExtensionMaximum >= productionMaximum)
            require(maximumRhat > 1.0 && minimumEss > 0.0 && maximumMcseToSd > 0.0)
            val fingerprintParts = mutableListOf<Any>(
                    mode.name,
                    chains,
                    stabilizationMinimum,
                    diagnosticWindow,
                    blockSize,
                    consecutiveStabilizationPasses,
                    stabilizationCap,
                    productionMinimum,
                    productionMaximum,
                    precisionExtensionMaximum,
                    maximumRhat,
                    minimumEss,
                    maximumMcseToSd,
                    STRICT_SAMPLING_POLICY_VERSION
            )
            if (reliabilityMode == StrictSamplingReliabilityMode.RELAXED) {
                fingerprintParts += RELAXED_SAMPLING_POLICY_VERSION
            }
            val fingerprint = strictFingerprint(fingerprintParts)
            return StrictSamplingPolicy(
                mode,
                reliabilityMode,
                chains,
                stabilizationMinimum,
                diagnosticWindow,
                blockSize,
                consecutiveStabilizationPasses,
                stabilizationCap,
                productionMinimum,
                productionMaximum,
                precisionExtensionMaximum,
                maximumRhat,
                minimumEss,
                maximumMcseToSd,
                fingerprint
            )
        }
    }
}

internal enum class StrictBayesianFailureCode {
    MCMC_CONVERGENCE_FAILED,
    LAG_POSTERIOR_MIXING_FAILED,
    MONTE_CARLO_PRECISION_NOT_REACHED,
    NUMERICAL_SPD_FAILURE,
    NONFINITE_STATE,
    CANCELLED
}

internal data class StrictPosteriorSummary(
    val mean: Double,
    val median: Double,
    val lower80: Double,
    val upper80: Double,
    val rhat: Double,
    val bulkEss: Double,
    val tailEss: Double,
    val mcseToSd: Double
)

internal data class StrictSourcePosteriorSummary(
    val source: AnalysisSourceKey,
    val openness: StrictPosteriorSummary,
    val contribution: StrictPosteriorSummary,
    val coefficientRms: StrictPosteriorSummary,
    val relevanceAvailable: Boolean
)

internal data class StrictResponsePosteriorSummary(
    val feature: StrictSeriesKey,
    val horizonWeeks: Int,
    val posterior: StrictPosteriorSummary
)

internal data class StrictBayesianV07Result(
    val officialLagProbability: Map<Int, Double>,
    val lagVisitationFrequency: Map<Int, Double>,
    val responses: Map<StrictSeriesKey, List<StrictResponsePosteriorSummary>>,
    val sourceSummaries: Map<AnalysisSourceKey, StrictSourcePosteriorSummary>,
    val globalCandidateScale: StrictPosteriorSummary,
    val dynamicScale: StrictPosteriorSummary,
    val retainedDrawsPerChain: Int,
    val samplingPolicyFingerprint: String,
    val samplingReliabilityMode: StrictSamplingReliabilityMode,
    val retryAttempt: Int,
    val samplingIdentityFingerprint: String,
    val preparedInputFingerprint: String,
    val fingerprint: String,
    val samplingAssessment: StrictSamplingAssessment? = null
)

internal sealed interface StrictBayesianV07Outcome {
    data class Success(val result: StrictBayesianV07Result, val diagnostics: List<String>) : StrictBayesianV07Outcome
    data class Failure(
        val code: StrictBayesianFailureCode,
        val failure: StrictFailureDiagnostics
    ) : StrictBayesianV07Outcome
}

internal data class StrictSamplingIdentity(
    val preparedInputFingerprint: String,
    val designFingerprint: String,
    val samplingPolicyFingerprint: String,
    val retryAttempt: Int,
    val fingerprint: String
) {
    init {
        require(retryAttempt >= 0)
    }

    fun seedForChain(chainIndex: Int): Long {
        require(chainIndex >= 0)
        return fingerprint.take(16).fold(0xcbf29ce484222325UL) { value, character ->
            (value xor character.code.toULong()) * 0x100000001b3UL
        }.toLong() xor (chainIndex + 1).toLong() * -7046029254386353131L
    }

    companion object {
        fun create(
            preparedInputFingerprint: String,
            designFingerprint: String,
            samplingPolicyFingerprint: String,
            retryAttempt: Int
        ): StrictSamplingIdentity = StrictSamplingIdentity(
            preparedInputFingerprint,
            designFingerprint,
            samplingPolicyFingerprint,
            retryAttempt,
            strictFingerprint(
                listOf(
                    preparedInputFingerprint,
                    designFingerprint,
                    samplingPolicyFingerprint,
                    retryAttempt,
                    STRICT_SAMPLING_ATTEMPT_VERSION
                )
            )
        )
    }
}

internal enum class StrictBayesianSamplingStage {
    STABILIZING,
    SAMPLING_POSTERIOR,
    CHECKING_RELIABILITY,
    EXTENDING_SAMPLING,
    SUMMARIZING
}

internal class StrictBayesianV07Sampler(
    private val design: PreparedBvarComparisonDesign,
    private val policy: StrictSamplingPolicy = StrictSamplingPolicy.appRuntime(),
    private val retryAttempt: Int = 0
) {
    internal val samplingIdentity: StrictSamplingIdentity = StrictSamplingIdentity.create(
        design.input.fingerprint,
        design.fingerprint,
        policy.fingerprint,
        retryAttempt
    )

    init {
        require(retryAttempt >= 0)
    }

    fun sample(
        onStage: (StrictBayesianSamplingStage) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): StrictBayesianV07Outcome {
        return try {
            val kernel = StrictBayesianV07Kernel(design)
            val seeds = LongArray(policy.chains, samplingIdentity::seedForChain)
            val randoms = seeds.map(::StrictRandom)
            var states = List(policy.chains) { chain ->
                kernel.initialState(design.designsByLag.keys.sorted()[chain % design.designsByLag.size])
            }

            onStage(StrictBayesianSamplingStage.STABILIZING)
            val warmup = List(policy.chains) { ChainTrace(design) }
            var warmupDraws = 0
            var consecutivePasses = 0
            var finalStabilizationDiagnostics = emptyList<NamedStatistics>()
            while (warmupDraws < policy.stabilizationCap && consecutivePasses < policy.consecutiveStabilizationPasses) {
                checkCancellation(isCancelled)
                val target = if (warmupDraws == 0) policy.stabilizationMinimum else min(
                    policy.stabilizationCap,
                    warmupDraws + policy.blockSize
                )
                states = runUntil(kernel, states, randoms, warmup, warmupDraws, target, isCancelled)
                warmupDraws = target
                if (warmupDraws >= policy.stabilizationMinimum) {
                    onStage(StrictBayesianSamplingStage.CHECKING_RELIABILITY)
                    finalStabilizationDiagnostics = stabilizationDiagnostics(
                        warmup.map { it.tail(policy.diagnosticWindow) }
                    )
                    val pass = finalStabilizationDiagnostics.all { diagnostic ->
                        diagnostic.statistics.rhat.isFinite() && diagnostic.statistics.rhat < policy.maximumRhat
                    }
                    consecutivePasses = if (pass) consecutivePasses + 1 else 0
                    if (consecutivePasses < policy.consecutiveStabilizationPasses) {
                        onStage(StrictBayesianSamplingStage.STABILIZING)
                    }
                }
            }
            if (consecutivePasses < policy.consecutiveStabilizationPasses) {
                return StrictBayesianV07Outcome.Failure(
                    StrictBayesianFailureCode.MCMC_CONVERGENCE_FAILED,
                    samplingFailure(
                        code = StrictBayesianFailureCode.MCMC_CONVERGENCE_FAILED,
                        stage = StrictFailureStage.STABILIZATION,
                        primaryReason = "Functional R-hat stabilization did not reach the required consecutive windows",
                        observations = finalStabilizationDiagnostics.map { it.rhatObservation(policy.maximumRhat) },
                        warmupDrawsPerChain = warmupDraws,
                        technicalDetails = listOf(
                            "diagnosticWindow=${policy.diagnosticWindow}",
                            "requiredConsecutivePasses=${policy.consecutiveStabilizationPasses}",
                            "observedConsecutivePasses=$consecutivePasses"
                        )
                    )
                )
            }

            val production = List(policy.chains) { ChainTrace(design) }
            var productionDraws = 0
            var precisionOnly = false
            var lastReliability: Reliability? = null
            onStage(StrictBayesianSamplingStage.SAMPLING_POSTERIOR)
            while (productionDraws < policy.precisionExtensionMaximum) {
                checkCancellation(isCancelled)
                val normalLimit = if (precisionOnly) policy.precisionExtensionMaximum else policy.productionMaximum
                val initialTarget = if (productionDraws == 0) policy.productionMinimum else productionDraws + policy.blockSize
                val target = min(normalLimit, initialTarget)
                states = runUntil(kernel, states, randoms, production, productionDraws, target, isCancelled)
                productionDraws = target
                onStage(StrictBayesianSamplingStage.CHECKING_RELIABILITY)
                val reliability = reliability(production)
                lastReliability = reliability
                if (reliability.rhatFailure) {
                    if (productionDraws >= policy.productionMaximum) {
                        val code = reliability.failureCode ?: StrictBayesianFailureCode.MCMC_CONVERGENCE_FAILED
                        return StrictBayesianV07Outcome.Failure(
                            code,
                            samplingFailure(
                                code = code,
                                stage = StrictFailureStage.PRODUCTION,
                                primaryReason = "Production posterior reliability checks failed",
                                observations = reliability.failedObservations(policy),
                                productionDrawsPerChain = productionDraws,
                                technicalDetails = if (code == StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED) {
                                    lagVisitationLines(production, productionDraws) +
                                        "official Rao-Blackwellized lag probabilities are unavailable because the lag gate failed"
                                } else {
                                    emptyList()
                                }
                            )
                        )
                    }
                } else if (!reliability.precisionFailure) {
                    onStage(StrictBayesianSamplingStage.SUMMARIZING)
                    return StrictBayesianV07Outcome.Success(
                        summarize(production, productionDraws),
                        listOf(
                            "official lag probabilities are Rao-Blackwellized mean omega",
                            "raw local Horseshoe scales are diagnostic-only",
                            "stabilization draws were discarded"
                        )
                    )
                } else if (productionDraws >= policy.productionMaximum) {
                    precisionOnly = true
                    onStage(StrictBayesianSamplingStage.EXTENDING_SAMPLING)
                } else {
                    onStage(StrictBayesianSamplingStage.SAMPLING_POSTERIOR)
                }
                if (productionDraws >= policy.precisionExtensionMaximum) break
            }
            StrictBayesianV07Outcome.Failure(
                StrictBayesianFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED,
                samplingFailure(
                    code = StrictBayesianFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED,
                    stage = StrictFailureStage.PRODUCTION,
                    primaryReason = "Posterior precision remained below the sampling acceptance policy",
                    observations = lastReliability?.failedObservations(policy).orEmpty(),
                    productionDrawsPerChain = productionDraws,
                    technicalDetails = listOf("precisionExtensionMaximum=${policy.precisionExtensionMaximum}")
                )
            )
        } catch (failure: CancellationException) {
            StrictBayesianV07Outcome.Failure(
                StrictBayesianFailureCode.CANCELLED,
                samplingFailure(
                    StrictBayesianFailureCode.CANCELLED,
                    StrictFailureStage.PRODUCTION,
                    "Sampling was cancelled",
                    technicalDetails = listOf("Sampling cancelled")
                )
            )
        } catch (failure: StrictBayesianNumericalException) {
            val code = if (failure.message.orEmpty().contains("SPD")) {
                StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE
            } else {
                StrictBayesianFailureCode.NONFINITE_STATE
            }
            StrictBayesianV07Outcome.Failure(
                code,
                samplingFailure(
                    code,
                    StrictFailureStage.NUMERICAL,
                    if (code == StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE) {
                        "A strict SPD operation failed"
                    } else {
                        "A non-finite sampler state was detected"
                    },
                    technicalDetails = listOfNotNull(
                        failure.message,
                        failure.cause?.let { "cause=${it::class.qualifiedName}:${it.message}" }
                    )
                )
            )
        }
    }

    /** App path: preserve one prepared model and chain state while diagnostics become descriptive. */
    fun sampleAutomatically(
        relaxedPolicy: StrictSamplingPolicy = StrictSamplingPolicy.relaxedContinuation(policy),
        onStage: (StrictBayesianSamplingStage) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): StrictBayesianV07Outcome {
        require(relaxedPolicy.chains == policy.chains)
        require(relaxedPolicy.stabilizationCap >= policy.stabilizationCap)
        require(relaxedPolicy.productionMaximum == policy.productionMaximum)
        require(relaxedPolicy.precisionExtensionMaximum == policy.precisionExtensionMaximum)
        return try {
            val kernel = StrictBayesianV07Kernel(design)
            val seeds = LongArray(policy.chains, samplingIdentity::seedForChain)
            val randoms = seeds.map(::StrictRandom)
            var states = List(policy.chains) { chain ->
                kernel.initialState(design.designsByLag.keys.sorted()[chain % design.designsByLag.size])
            }
            val windows = ArrayDeque<StrictSamplingDiagnosticWindow>(4)
            fun retain(window: StrictSamplingDiagnosticWindow) {
                if (windows.size == 4) windows.removeFirst()
                windows.addLast(window)
            }

            onStage(StrictBayesianSamplingStage.STABILIZING)
            val warmup = List(policy.chains) { ChainTrace(design) }
            var warmupDraws = 0
            var strictPasses = 0
            var relaxedPasses = 0
            while (warmupDraws < relaxedPolicy.stabilizationCap) {
                checkCancellation(isCancelled)
                val target = if (warmupDraws == 0) {
                    policy.stabilizationMinimum
                } else {
                    min(relaxedPolicy.stabilizationCap, warmupDraws + policy.blockSize)
                }
                states = runUntil(kernel, states, randoms, warmup, warmupDraws, target, isCancelled)
                warmupDraws = target
                onStage(StrictBayesianSamplingStage.CHECKING_RELIABILITY)
                val statistics = stabilizationDiagnostics(warmup.map { it.tail(policy.diagnosticWindow) })
                val strictPass = statistics.all { it.statistics.rhat.isFinite() && it.statistics.rhat < policy.maximumRhat }
                val relaxedPass = statistics.all {
                    it.statistics.rhat.isFinite() && it.statistics.rhat < relaxedPolicy.maximumRhat
                }
                strictPasses = if (strictPass) strictPasses + 1 else 0
                relaxedPasses = if (relaxedPass) relaxedPasses + 1 else 0
                retain(
                    diagnosticWindow(
                        StrictFailureStage.STABILIZATION,
                        warmupDraws,
                        statistics,
                        strictPass,
                        relaxedPass
                    )
                )
                if (strictPasses >= policy.consecutiveStabilizationPasses) break
                if (warmupDraws >= policy.stabilizationCap &&
                    relaxedPasses >= relaxedPolicy.consecutiveStabilizationPasses
                ) break
                onStage(StrictBayesianSamplingStage.STABILIZING)
            }
            val strictStabilized = strictPasses >= policy.consecutiveStabilizationPasses
            val relaxedStabilized = relaxedPasses >= relaxedPolicy.consecutiveStabilizationPasses

            val production = List(policy.chains) { ChainTrace(design) }
            var productionDraws = 0
            var strictReliability: Reliability? = null
            var relaxedReliability: Reliability? = null
            onStage(StrictBayesianSamplingStage.SAMPLING_POSTERIOR)
            while (productionDraws < policy.precisionExtensionMaximum) {
                checkCancellation(isCancelled)
                val target = if (productionDraws == 0) {
                    policy.productionMinimum
                } else {
                    min(policy.precisionExtensionMaximum, productionDraws + policy.blockSize)
                }
                states = runUntil(kernel, states, randoms, production, productionDraws, target, isCancelled)
                productionDraws = target
                onStage(StrictBayesianSamplingStage.CHECKING_RELIABILITY)
                strictReliability = reliability(production, policy)
                relaxedReliability = reliability(production, relaxedPolicy)
                val strictMet = strictStabilized && strictReliability.passed
                val relaxedMet = relaxedStabilized && relaxedReliability.passed
                retain(
                    diagnosticWindow(
                        StrictFailureStage.PRODUCTION,
                        productionDraws,
                        strictReliability.statistics,
                        strictMet,
                        relaxedMet
                    )
                )
                val classification = when {
                    strictMet -> StrictSamplingDiagnosticClassification.STRICT
                    relaxedMet -> StrictSamplingDiagnosticClassification.RELAXED
                    else -> null
                }
                if (classification != null) {
                    onStage(StrictBayesianSamplingStage.SUMMARIZING)
                    val assessment = samplingAssessment(
                        classification,
                        relaxedPolicy,
                        windows.toList(),
                        warmupDraws,
                        productionDraws,
                        strictMet,
                        relaxedMet,
                        relaxedReliability.failureCode == StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED
                    )
                    return StrictBayesianV07Outcome.Success(
                        summarize(production, productionDraws, assessment),
                        automaticSamplingDiagnostics(assessment)
                    )
                }
                if (productionDraws >= policy.productionMaximum) {
                    onStage(StrictBayesianSamplingStage.EXTENDING_SAMPLING)
                } else {
                    onStage(StrictBayesianSamplingStage.SAMPLING_POSTERIOR)
                }
            }
            onStage(StrictBayesianSamplingStage.SUMMARIZING)
            val finalRelaxed = requireNotNull(relaxedReliability)
            val assessment = samplingAssessment(
                StrictSamplingDiagnosticClassification.LIMITED,
                relaxedPolicy,
                windows.toList(),
                warmupDraws,
                productionDraws,
                strictCriteriaMet = false,
                relaxedCriteriaMet = false,
                lagMixingConcern = finalRelaxed.failureCode == StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED
            )
            StrictBayesianV07Outcome.Success(
                summarize(production, productionDraws, assessment),
                automaticSamplingDiagnostics(assessment)
            )
        } catch (failure: CancellationException) {
            StrictBayesianV07Outcome.Failure(
                StrictBayesianFailureCode.CANCELLED,
                samplingFailure(
                    StrictBayesianFailureCode.CANCELLED,
                    StrictFailureStage.PRODUCTION,
                    "Sampling was cancelled",
                    technicalDetails = listOf("Sampling cancelled")
                )
            )
        } catch (failure: StrictBayesianNumericalException) {
            val code = if (failure.message.orEmpty().contains("SPD")) {
                StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE
            } else {
                StrictBayesianFailureCode.NONFINITE_STATE
            }
            StrictBayesianV07Outcome.Failure(
                code,
                samplingFailure(
                    code,
                    StrictFailureStage.NUMERICAL,
                    if (code == StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE) {
                        "A strict SPD operation failed"
                    } else {
                        "A non-finite sampler state was detected"
                    },
                    technicalDetails = listOfNotNull(
                        failure.message,
                        failure.cause?.let { "cause=${it::class.qualifiedName}:${it.message}" }
                    )
                )
            )
        }
    }

    private fun runUntil(
        kernel: StrictBayesianV07Kernel,
        initialStates: List<StrictBayesianV07State>,
        randoms: List<StrictRandom>,
        traces: List<ChainTrace>,
        from: Int,
        until: Int,
        isCancelled: () -> Boolean
    ): List<StrictBayesianV07State> = initialStates.mapIndexed { chain, initial ->
        var state = initial
        for (iteration in from until until) {
            if (iteration % 8 == 0) checkCancellation(isCancelled)
            val step = kernel.step(state, randoms[chain])
            state = step.state
            traces[chain].append(step.draw)
        }
        state
    }

    private fun stabilizationDiagnostics(traces: List<ChainTrace>): List<NamedStatistics> = buildList {
            add(NamedStatistics("gZ", StrictChainDiagnostics.statistics(traces.map(ChainTrace::globalCandidateScale))))
            add(NamedStatistics("tauDyn", StrictChainDiagnostics.statistics(traces.map(ChainTrace::dynamicScale))))
            add(NamedStatistics("lag", StrictChainDiagnostics.statistics(traces.map { it.lagAsDouble() })))
            design.input.sourceGrouping.featuresBySource.keys.forEach { source ->
                add(NamedStatistics("E[$source]", StrictChainDiagnostics.statistics(traces.map { it.contribution(source) })))
            }
            design.responseFeatures.forEach { feature ->
                (1..design.maximumResponseHorizon).forEach { horizon ->
                    add(
                        NamedStatistics(
                            "response[${feature.stableId},$horizon]",
                            StrictChainDiagnostics.statistics(traces.map { it.response(feature, horizon) })
                        )
                    )
                }
            }
        }

    private fun reliability(
        traces: List<ChainTrace>,
        criteria: StrictSamplingPolicy = policy
    ): Reliability {
        val statistics = mutableListOf<NamedStatistics>()
        var rhatFailure = false
        var precisionFailure = false
        var lagFailure = false
        fun inspect(name: String, chains: List<DoubleArray>, lagQuantity: Boolean = false) {
            val observed = StrictChainDiagnostics.statistics(chains)
            statistics += NamedStatistics(name, observed)
            if (!observed.rhat.isFinite() || observed.rhat >= criteria.maximumRhat) {
                rhatFailure = true
                lagFailure = lagFailure || lagQuantity
            }
            if (observed.bulkEss < criteria.minimumEss || observed.tailEss < criteria.minimumEss ||
                observed.mcseToSd > criteria.maximumMcseToSd
            ) {
                precisionFailure = true
            }
        }
        inspect("gZ", traces.map(ChainTrace::globalCandidateScale))
        inspect("tauDyn", traces.map(ChainTrace::dynamicScale))
        inspect("lag", traces.map { it.lagAsDouble() }, lagQuantity = true)
        design.designsByLag.keys.forEach { lag -> inspect("omega[$lag]", traces.map { it.omega(lag) }, lagQuantity = true) }
        design.responseFeatures.indices.forEach { response -> inspect("Sigma[$response]", traces.map { it.sigma(response) }) }
        design.input.sourceGrouping.featuresBySource.keys.forEach { source ->
            inspect("E[$source]", traces.map { it.contribution(source) })
            inspect("B[$source]", traces.map { it.coefficientRms(source) })
        }
        design.responseFeatures.forEach { feature ->
            (1..design.maximumResponseHorizon).forEach { horizon ->
                inspect("response[${feature.stableId},$horizon]", traces.map { it.response(feature, horizon) })
            }
        }
        return Reliability(
            rhatFailure,
            precisionFailure,
            if (lagFailure) StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED else null,
            statistics
        )
    }

    private fun summarize(
        traces: List<ChainTrace>,
        drawsPerChain: Int,
        assessment: StrictSamplingAssessment? = null
    ): StrictBayesianV07Result {
        val lagProbability = design.designsByLag.keys.associateWith { lag -> traces.flatMap { it.omega(lag).asIterable() }.average() }
        val visits = design.designsByLag.keys.associateWith { lag ->
            traces.sumOf { trace -> trace.lags().count { it == lag } }.toDouble() / (drawsPerChain * traces.size)
        }
        val responses = design.responseFeatures.associateWith { feature ->
            (1..design.maximumResponseHorizon).map { horizon ->
                StrictResponsePosteriorSummary(
                    feature,
                    horizon,
                    StrictChainDiagnostics.summary(traces.map { it.response(feature, horizon) })
                )
            }
        }
        val sourceSummaries = design.input.sourceGrouping.featuresBySource.keys.associateWith { source ->
            val openness = StrictChainDiagnostics.summary(traces.map { it.openness(source) })
            val contribution = StrictChainDiagnostics.summary(traces.map { it.contribution(source) })
            val coefficient = StrictChainDiagnostics.summary(traces.map { it.coefficientRms(source) })
            StrictSourcePosteriorSummary(
                source,
                openness,
                contribution,
                coefficient,
                relevanceAvailable = listOf(openness, contribution, coefficient).all { it.hasFiniteEstimate() }
            )
        }
        val global = StrictChainDiagnostics.summary(traces.map(ChainTrace::globalCandidateScale))
        val dynamic = StrictChainDiagnostics.summary(traces.map(ChainTrace::dynamicScale))
        val classification = assessment?.classification ?: when (policy.reliabilityMode) {
            StrictSamplingReliabilityMode.STRICT -> StrictSamplingDiagnosticClassification.STRICT
            StrictSamplingReliabilityMode.RELAXED -> StrictSamplingDiagnosticClassification.RELAXED
        }
        val result = StrictBayesianV07Result(
            lagProbability,
            visits,
            responses,
            sourceSummaries,
            global,
            dynamic,
            drawsPerChain,
            policy.fingerprint,
            if (classification == StrictSamplingDiagnosticClassification.STRICT) {
                StrictSamplingReliabilityMode.STRICT
            } else {
                StrictSamplingReliabilityMode.RELAXED
            },
            retryAttempt,
            samplingIdentity.fingerprint,
            design.input.fingerprint,
            strictFingerprint(
                listOf(
                    design.fingerprint,
                    policy.fingerprint,
                    samplingIdentity.fingerprint,
                    lagProbability.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" },
                    responses.toSortedMap(compareBy { it.stableId }).entries.joinToString("|") { (feature, summaries) ->
                        "${feature.stableId}:${summaries.joinToString(",") { "${it.horizonWeeks}:${it.posterior.mean}" }}"
                    },
                    sourceSummaries.toSortedMap().entries.joinToString("|") { "${it.key}:${it.value.contribution.mean}" },
                    drawsPerChain,
                    classification.name,
                    STRICT_BAYESIAN_V07_RESULT_VERSION
                )
            ),
            assessment
        )
        if (!result.isFinite()) {
            throw StrictBayesianNumericalException("NONFINITE_STATE: posterior summary")
        }
        return result
    }

    private fun diagnosticWindow(
        stage: StrictFailureStage,
        drawsPerChain: Int,
        statistics: List<NamedStatistics>,
        strictCriteriaMet: Boolean,
        relaxedCriteriaMet: Boolean
    ): StrictSamplingDiagnosticWindow {
        val worstRhat = statistics.maxByOrNull { it.statistics.rhat } ?: error("missing diagnostics")
        return StrictSamplingDiagnosticWindow(
            stage = stage,
            drawsPerChain = drawsPerChain,
            worstRhat = worstRhat.statistics.rhat,
            worstRhatFunctional = worstRhat.name,
            minimumBulkEss = statistics.minOf { it.statistics.bulkEss },
            minimumTailEss = statistics.minOf { it.statistics.tailEss },
            worstMcseToSd = statistics.maxOf { it.statistics.mcseToSd },
            strictCriteriaMet = strictCriteriaMet,
            relaxedCriteriaMet = relaxedCriteriaMet
        )
    }

    private fun samplingAssessment(
        classification: StrictSamplingDiagnosticClassification,
        relaxedPolicy: StrictSamplingPolicy,
        windows: List<StrictSamplingDiagnosticWindow>,
        warmupDraws: Int,
        productionDraws: Int,
        strictCriteriaMet: Boolean,
        relaxedCriteriaMet: Boolean,
        lagMixingConcern: Boolean
    ): StrictSamplingAssessment = StrictSamplingAssessment(
        classification = classification,
        strictPolicy = policy.snapshot("STRICT"),
        relaxedPolicy = relaxedPolicy.snapshot("RELAXED"),
        recentWindows = windows.takeLast(4),
        stabilizationDrawsPerChain = warmupDraws,
        productionDrawsPerChain = productionDraws,
        strictCriteriaMet = strictCriteriaMet,
        relaxedCriteriaMet = relaxedCriteriaMet,
        lagMixingConcern = lagMixingConcern
    )

    private fun automaticSamplingDiagnostics(assessment: StrictSamplingAssessment): List<String> = buildList {
        add("official lag probabilities are Rao-Blackwellized mean omega")
        add("raw local Horseshoe scales are diagnostic-only")
        add("stabilization draws were discarded")
        add("sampling classification=${assessment.classification.name}")
        if (assessment.classification == StrictSamplingDiagnosticClassification.LIMITED) {
            add("finite posterior retained despite unmet relaxed diagnostic targets")
        }
        if (assessment.lagMixingConcern) add("lag mixing diagnostic target was not met")
    }

    private fun samplingFailure(
        code: StrictBayesianFailureCode,
        stage: StrictFailureStage,
        primaryReason: String,
        observations: List<StrictDiagnosticObservation> = emptyList(),
        warmupDrawsPerChain: Int? = null,
        productionDrawsPerChain: Int? = null,
        technicalDetails: List<String> = emptyList()
    ): StrictFailureDiagnostics = StrictFailureDiagnostics(
        code = code.toLabFailureCode(),
        stage = stage,
        primaryReason = primaryReason,
        affectedFeatureOrSource = observations.firstOrNull { it.passed == false }?.name
            ?: design.focalFeature.stableId,
        usableCommonRows = design.comparisonRowCount,
        attemptedLags = design.designsByLag.keys.sorted(),
        selectedPmax = design.input.comparisonPlan.pmax,
        observations = observations,
        chainsAttempted = policy.chains,
        warmupDrawsPerChain = warmupDrawsPerChain,
        productionDrawsPerChain = productionDrawsPerChain,
        preparedInputFingerprint = design.input.fingerprint,
        samplingPolicyFingerprint = policy.fingerprint,
        samplingReliabilityMode = policy.reliabilityMode,
        retryAttempt = retryAttempt,
        samplingIdentityFingerprint = samplingIdentity.fingerprint,
        technicalDetails = technicalDetails + "samplingIdentityFingerprint=${samplingIdentity.fingerprint}"
    )

    private fun lagVisitationLines(traces: List<ChainTrace>, drawsPerChain: Int): List<String> {
        val denominator = drawsPerChain * traces.size.toDouble()
        return design.designsByLag.keys.sorted().map { lag ->
            val visits = traces.sumOf { trace -> trace.lags().count { it == lag } }
            "lagVisit[$lag]=${if (denominator > 0.0) visits / denominator else 0.0}"
        }
    }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException()
    }

    private data class NamedStatistics(
        val name: String,
        val statistics: StrictChainDiagnostics.Statistics
    ) {
        fun rhatObservation(maximumRhat: Double): StrictDiagnosticObservation = StrictDiagnosticObservation(
            name,
            "Rhat=${statistics.rhat}",
            "Rhat<$maximumRhat",
            statistics.rhat.isFinite() && statistics.rhat < maximumRhat
        )

        fun reliabilityObservation(policy: StrictSamplingPolicy): StrictDiagnosticObservation {
            val passed = statistics.rhat.isFinite() && statistics.rhat < policy.maximumRhat &&
                statistics.bulkEss >= policy.minimumEss && statistics.tailEss >= policy.minimumEss &&
                statistics.mcseToSd <= policy.maximumMcseToSd
            return StrictDiagnosticObservation(
                name,
                "Rhat=${statistics.rhat}; bulkESS=${statistics.bulkEss}; tailESS=${statistics.tailEss}; MCSE/SD=${statistics.mcseToSd}",
                "Rhat<${policy.maximumRhat}; ESS>=${policy.minimumEss}; MCSE/SD<=${policy.maximumMcseToSd}",
                passed
            )
        }
    }

    private data class Reliability(
        val rhatFailure: Boolean,
        val precisionFailure: Boolean,
        val failureCode: StrictBayesianFailureCode?,
        val statistics: List<NamedStatistics>
    ) {
        val passed: Boolean
            get() = !rhatFailure && !precisionFailure

        fun failedObservations(policy: StrictSamplingPolicy): List<StrictDiagnosticObservation> =
            statistics.map { it.reliabilityObservation(policy) }.filterNot { it.passed == true }
    }

    private class CancellationException : RuntimeException()
}

private fun StrictPosteriorSummary.hasFiniteEstimate(): Boolean = listOf(
    mean,
    median,
    lower80,
    upper80
).all(Double::isFinite)

private fun StrictBayesianV07Result.isFinite(): Boolean =
    officialLagProbability.values.all(Double::isFinite) &&
        lagVisitationFrequency.values.all(Double::isFinite) &&
        responses.values.flatten().all { it.posterior.hasFiniteEstimate() } &&
        sourceSummaries.values.all { summary ->
            summary.openness.hasFiniteEstimate() && summary.contribution.hasFiniteEstimate() &&
                summary.coefficientRms.hasFiniteEstimate()
        } && globalCandidateScale.hasFiniteEstimate() && dynamicScale.hasFiniteEstimate()

private fun StrictBayesianFailureCode.toLabFailureCode(): StrictLabFailureCode = when (this) {
    StrictBayesianFailureCode.MCMC_CONVERGENCE_FAILED -> StrictLabFailureCode.MCMC_CONVERGENCE_FAILED
    StrictBayesianFailureCode.LAG_POSTERIOR_MIXING_FAILED -> StrictLabFailureCode.LAG_POSTERIOR_MIXING_FAILED
    StrictBayesianFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED -> StrictLabFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED
    StrictBayesianFailureCode.NUMERICAL_SPD_FAILURE -> StrictLabFailureCode.NUMERICAL_SPD_FAILURE
    StrictBayesianFailureCode.NONFINITE_STATE -> StrictLabFailureCode.NONFINITE_STATE
    StrictBayesianFailureCode.CANCELLED -> StrictLabFailureCode.CANCELLED
}

private class ChainTrace private constructor(
    private val lagKeys: List<Int>,
    private val sources: List<AnalysisSourceKey>,
    private val responseCount: Int,
    private val lag: IntTrace,
    private val global: DoubleTrace,
    private val dynamic: DoubleTrace,
    private val omegaByLag: Map<Int, DoubleTrace>,
    private val sigmaByResponse: List<DoubleTrace>,
    private val opennessBySource: Map<AnalysisSourceKey, DoubleTrace>,
    private val contributionBySource: Map<AnalysisSourceKey, DoubleTrace>,
    private val coefficientBySource: Map<AnalysisSourceKey, DoubleTrace>,
    private val responseByFeature: Map<StrictSeriesKey, List<DoubleTrace>>
) {
    constructor(design: PreparedBvarComparisonDesign) : this(
        design.designsByLag.keys.sorted(),
        design.input.sourceGrouping.featuresBySource.keys.sorted(),
        design.responseFeatures.size,
        IntTrace(),
        DoubleTrace(),
        DoubleTrace(),
        design.designsByLag.keys.associateWith { DoubleTrace() },
        List(design.responseFeatures.size) { DoubleTrace() },
        design.input.sourceGrouping.featuresBySource.keys.associateWith { DoubleTrace() },
        design.input.sourceGrouping.featuresBySource.keys.associateWith { DoubleTrace() },
        design.input.sourceGrouping.featuresBySource.keys.associateWith { DoubleTrace() },
        design.responseFeatures.associateWith {
            List(design.maximumResponseHorizon) { DoubleTrace() }
        }
    )

    fun append(draw: StrictBayesianV07Draw) {
        lag.add(draw.lag)
        global.add(draw.globalCandidateScale)
        dynamic.add(draw.dynamicScale)
        lagKeys.forEach { omegaByLag.getValue(it).add(draw.omegaByLag.getValue(it)) }
        repeat(responseCount) { sigmaByResponse[it].add(draw.sigmaDiagonal[it]) }
        sources.forEach { source ->
            opennessBySource.getValue(source).add(draw.opennessBySource.getValue(source))
            contributionBySource.getValue(source).add(draw.contributionBySource.getValue(source))
            coefficientBySource.getValue(source).add(draw.coefficientRmsBySource.getValue(source))
        }
        responseByFeature.forEach { (feature, horizons) ->
            val values = draw.responseByFeature.getValue(feature)
            horizons.indices.forEach { index -> horizons[index].add(values[index]) }
        }
    }

    fun tail(size: Int): ChainTrace {
        val from = max(0, lag.size - size)
        val copy = ChainTraceSkeleton(
            lagKeys,
            sources,
            responseCount,
            responseByFeature.keys.toList(),
            responseByFeature.values.first().size
        ).trace
        for (index in from until lag.size) {
            copy.lag.add(lag[index])
            copy.global.add(global[index])
            copy.dynamic.add(dynamic[index])
            lagKeys.forEach { copy.omegaByLag.getValue(it).add(omegaByLag.getValue(it)[index]) }
            repeat(responseCount) { copy.sigmaByResponse[it].add(sigmaByResponse[it][index]) }
            sources.forEach { source ->
                copy.opennessBySource.getValue(source).add(opennessBySource.getValue(source)[index])
                copy.contributionBySource.getValue(source).add(contributionBySource.getValue(source)[index])
                copy.coefficientBySource.getValue(source).add(coefficientBySource.getValue(source)[index])
            }
            responseByFeature.forEach { (feature, horizons) ->
                horizons.indices.forEach { horizon ->
                    copy.responseByFeature.getValue(feature)[horizon].add(horizons[horizon][index])
                }
            }
        }
        return copy
    }

    fun globalCandidateScale(): DoubleArray = global.toArray()
    fun dynamicScale(): DoubleArray = dynamic.toArray()
    fun lagAsDouble(): DoubleArray = DoubleArray(lag.size) { lag[it].toDouble() }
    fun lags(): IntArray = lag.toArray()
    fun omega(value: Int): DoubleArray = omegaByLag.getValue(value).toArray()
    fun sigma(response: Int): DoubleArray = sigmaByResponse[response].toArray()
    fun openness(source: AnalysisSourceKey): DoubleArray = opennessBySource.getValue(source).toArray()
    fun contribution(source: AnalysisSourceKey): DoubleArray = contributionBySource.getValue(source).toArray()
    fun coefficientRms(source: AnalysisSourceKey): DoubleArray = coefficientBySource.getValue(source).toArray()
    fun response(feature: StrictSeriesKey, horizon: Int): DoubleArray =
        responseByFeature.getValue(feature)[horizon - 1].toArray()

    private data class ChainTraceSkeleton(
        val lagKeys: List<Int>,
        val sources: List<AnalysisSourceKey>,
        val responseCount: Int,
        val responseFeatures: List<StrictSeriesKey>,
        val maximumResponseHorizon: Int
    ) {
        val trace = ChainTrace(
            lagKeys,
            sources,
            responseCount,
            IntTrace(),
            DoubleTrace(),
            DoubleTrace(),
            lagKeys.associateWith { DoubleTrace() },
            List(responseCount) { DoubleTrace() },
            sources.associateWith { DoubleTrace() },
            sources.associateWith { DoubleTrace() },
            sources.associateWith { DoubleTrace() },
            responseFeatures.associateWith { List(maximumResponseHorizon) { DoubleTrace() } }
        )
    }
}

private class DoubleTrace(initialCapacity: Int = 512) {
    private var values = DoubleArray(initialCapacity)
    var size: Int = 0
        private set
    operator fun get(index: Int): Double = values[index]
    fun add(value: Double) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }
    fun toArray(): DoubleArray = values.copyOf(size)
}

private class IntTrace(initialCapacity: Int = 512) {
    private var values = IntArray(initialCapacity)
    var size: Int = 0
        private set
    operator fun get(index: Int): Int = values[index]
    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }
    fun toArray(): IntArray = values.copyOf(size)
}

internal object StrictChainDiagnostics {
    data class Statistics(val rhat: Double, val bulkEss: Double, val tailEss: Double, val mcseToSd: Double)

    fun statistics(chains: List<DoubleArray>): Statistics {
        val rhat = rankFoldedRhat(chains)
        val ranked = rankNormalize(chains)
        val bulkEss = effectiveSampleSize(ranked)
        val pooled = chains.flatMap(DoubleArray::asIterable).sorted()
        val low = quantile(pooled, 0.05)
        val high = quantile(pooled, 0.95)
        val lowEss = effectiveSampleSize(chains.map { chain -> DoubleArray(chain.size) { if (chain[it] <= low) 1.0 else 0.0 } })
        val highEss = effectiveSampleSize(chains.map { chain -> DoubleArray(chain.size) { if (chain[it] >= high) 1.0 else 0.0 } })
        val tailEss = min(lowEss, highEss)
        return Statistics(rhat, bulkEss, tailEss, if (bulkEss > 0.0) 1.0 / sqrt(bulkEss) else Double.POSITIVE_INFINITY)
    }

    fun summary(chains: List<DoubleArray>): StrictPosteriorSummary {
        val values = chains.flatMap(DoubleArray::asIterable).sorted()
        val statistics = statistics(chains)
        return StrictPosteriorSummary(
            values.average(),
            quantile(values, 0.50),
            quantile(values, 0.10),
            quantile(values, 0.90),
            statistics.rhat,
            statistics.bulkEss,
            statistics.tailEss,
            statistics.mcseToSd
        )
    }

    fun rankFoldedRhat(chains: List<DoubleArray>): Double {
        val ranked = rankNormalize(chains)
        val rankRhat = splitRhat(ranked)
        val pooledMedian = quantile(ranked.flatMap(DoubleArray::asIterable).sorted(), 0.50)
        val folded = ranked.map { chain -> DoubleArray(chain.size) { abs(chain[it] - pooledMedian) } }
        return max(rankRhat, splitRhat(rankNormalize(folded)))
    }

    private fun splitRhat(chains: List<DoubleArray>): Double {
        if (chains.size < 2 || chains.any { it.size < 4 }) return Double.POSITIVE_INFINITY
        val half = chains.minOf { it.size } / 2
        val split = chains.flatMap { chain ->
            listOf(chain.copyOfRange(0, half), chain.copyOfRange(chain.size - half, chain.size))
        }
        val means = split.map(DoubleArray::average)
        val variances = split.map(::sampleVariance)
        val within = variances.average()
        if (within <= 0.0) return if (means.distinct().size == 1) 1.0 else Double.POSITIVE_INFINITY
        val between = half * sampleVariance(means.toDoubleArray())
        val variance = ((half - 1.0) / half) * within + between / half
        return sqrt(variance / within)
    }

    private fun rankNormalize(chains: List<DoubleArray>): List<DoubleArray> {
        val indexed = chains.flatMapIndexed { chainIndex, chain ->
            chain.indices.map { index -> RankedValue(chain[index], chainIndex, index) }
        }.sortedBy(RankedValue::value)
        val normalized = chains.map { DoubleArray(it.size) }.toMutableList()
        var cursor = 0
        while (cursor < indexed.size) {
            var end = cursor + 1
            while (end < indexed.size && indexed[end].value == indexed[cursor].value) end++
            val rank = (cursor + 1 + end).toDouble() / 2.0
            val probability = (rank - 0.375) / (indexed.size + 0.25)
            val z = inverseNormal(probability)
            for (position in cursor until end) {
                val item = indexed[position]
                normalized[item.chain][item.index] = z
            }
            cursor = end
        }
        return normalized
    }

    private fun effectiveSampleSize(chains: List<DoubleArray>): Double {
        if (chains.isEmpty() || chains.any { it.size < 3 }) return 0.0
        val length = chains.minOf { it.size }
        val total = chains.size * length.toDouble()
        val maxLag = min(100, length - 1)
        val correlations = DoubleArray(maxLag + 1)
        correlations[0] = 1.0
        for (lag in 1..maxLag) {
            correlations[lag] = chains.map { autocorrelation(it, lag) }.average()
        }
        var sum = 0.0
        var lag = 1
        while (lag + 1 <= maxLag) {
            val pair = correlations[lag] + correlations[lag + 1]
            if (!pair.isFinite() || pair <= 0.0) break
            sum += pair
            lag += 2
        }
        return (total / (1.0 + 2.0 * sum)).coerceIn(1.0, total)
    }

    private fun autocorrelation(values: DoubleArray, lag: Int): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) }
        if (variance <= 0.0) return 0.0
        var covariance = 0.0
        for (index in lag until values.size) covariance += (values[index] - mean) * (values[index - lag] - mean)
        return covariance / variance
    }

    private fun sampleVariance(values: DoubleArray): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
    }

    private fun quantile(values: List<Double>, probability: Double): Double {
        require(values.isNotEmpty())
        val position = probability.coerceIn(0.0, 1.0) * (values.size - 1)
        val lower = position.toInt()
        val upper = min(values.lastIndex, lower + 1)
        val fraction = position - lower
        return values[lower] * (1.0 - fraction) + values[upper] * fraction
    }

    // Acklam's inverse-normal approximation; deterministic diagnostics only.
    private fun inverseNormal(probability: Double): Double {
        val p = probability.coerceIn(1e-15, 1.0 - 1e-15)
        val a = doubleArrayOf(-39.69683028665376, 220.9460984245205, -275.9285104469687, 138.3577518672690, -30.66479806614716, 2.506628277459239)
        val b = doubleArrayOf(-54.47609879822406, 161.5858368580409, -155.6989798598866, 66.80131188771972, -13.28068155288572)
        val c = doubleArrayOf(-0.007784894002430293, -0.3223964580411365, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
        val d = doubleArrayOf(0.007784695709041462, 0.3224671290700398, 2.445134137142996, 3.754408661907416)
        val low = 0.02425
        val high = 1.0 - low
        return when {
            p < low -> {
                val q = sqrt(-2.0 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            p > high -> {
                val q = sqrt(-2.0 * ln(1.0 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            else -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
            }
        }
    }

    private data class RankedValue(val value: Double, val chain: Int, val index: Int)
}

internal const val STRICT_SAMPLING_POLICY_VERSION = "strict-bayesian-sampling-policy-v0.7"
internal const val RELAXED_SAMPLING_POLICY_VERSION = "strict-bayesian-relaxed-sampling-policy-v1"
internal const val STRICT_SAMPLING_ATTEMPT_VERSION = "strict-bayesian-sampling-attempt-v1"
internal const val STRICT_BAYESIAN_V07_RESULT_VERSION = "strict-bayesian-result-v0.7.1-attempt-identity"
