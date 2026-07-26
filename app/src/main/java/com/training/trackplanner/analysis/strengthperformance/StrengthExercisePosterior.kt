package com.training.trackplanner.analysis.strengthperformance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class StrengthExerciseLocalState(
    val exerciseStableKey: String,
    val logMean: Double,
    val logVariance: Double,
    val lastProcessedEventUuid: String,
    val lastProcessedSessionKey: String,
    val lastProcessedDate: LocalDate,
    val baselineEstablished: Boolean,
    val observationCount: Int,
    val twoSidedObservationCount: Int
) {
    init {
        require(exerciseStableKey.isNotBlank())
        require(logMean.isFinite())
        require(logVariance.isFinite() && logVariance > 0.0)
        require(observationCount >= 1)
        require(twoSidedObservationCount in 0..observationCount)
    }
}

data class StrengthExerciseLocalHistory(
    val eventUuid: String,
    val sessionKey: String,
    val sessionDate: LocalDate,
    val exerciseStableKey: String,
    val priorLogMean: Double,
    val priorLogVariance: Double,
    val sessionLikelihoodLogMean: Double?,
    val sessionLikelihoodLogVariance: Double?,
    val sessionLikelihoodProper: Boolean,
    val innovationResidualLog: Double?,
    val innovationVariance: Double?,
    val posteriorLogMean: Double,
    val posteriorLogVariance: Double,
    val posteriorMeanIncrementLog: Double,
    val transitionDays: Long,
    val baselineEstablishedBefore: Boolean,
    val baselineEstablishedAfter: Boolean,
    val proxyTransferEligible: Boolean,
    val numericalDiagnostics: ScalarGridDiagnostics?,
    val evidenceFingerprint: String
)

data class StrengthExercisePosteriorComputation(
    val state: StrengthExerciseLocalState?,
    val history: StrengthExerciseLocalHistory?,
    val diagnostics: List<String>
)

object StrengthExercisePosteriorEngine {
    fun update(
        eventUuid: String,
        observation: StrengthExerciseSessionObservation,
        currentState: StrengthExerciseLocalState?
    ): StrengthExercisePosteriorComputation {
        require(currentState == null || currentState.exerciseStableKey == observation.exerciseStableKey)
        val properMoments = observation.sessionLikelihood
            .takeIf(StrengthExerciseSessionLikelihood::hasProperLikelihood)
            ?.let { ScalarGridPosteriorEngine.likelihoodMoments(it.asScalarLikelihood(properOnly = true)) }
        if (currentState == null && properMoments == null) {
            return StrengthExercisePosteriorComputation(
                state = null,
                history = null,
                diagnostics = listOf("LOCAL_BASELINE_REQUIRES_TWO_SIDED_LIKELIHOOD")
            )
        }
        if (currentState == null) {
            val likelihood = checkNotNull(properMoments)
            val posteriorVariance = maxOf(
                likelihood.variance,
                INITIAL_BASELINE_LOG_VARIANCE
            )
            val state = StrengthExerciseLocalState(
                exerciseStableKey = observation.exerciseStableKey,
                logMean = likelihood.mean,
                logVariance = posteriorVariance,
                lastProcessedEventUuid = eventUuid,
                lastProcessedSessionKey = observation.sessionKey,
                lastProcessedDate = observation.date,
                baselineEstablished = true,
                observationCount = 1,
                twoSidedObservationCount = 1
            )
            return StrengthExercisePosteriorComputation(
                state = state,
                history = StrengthExerciseLocalHistory(
                    eventUuid = eventUuid,
                    sessionKey = observation.sessionKey,
                    sessionDate = observation.date,
                    exerciseStableKey = observation.exerciseStableKey,
                    priorLogMean = likelihood.mean,
                    priorLogVariance = INITIAL_BASELINE_LOG_VARIANCE,
                    sessionLikelihoodLogMean = likelihood.mean,
                    sessionLikelihoodLogVariance = likelihood.variance,
                    sessionLikelihoodProper = true,
                    innovationResidualLog = null,
                    innovationVariance = null,
                    posteriorLogMean = state.logMean,
                    posteriorLogVariance = state.logVariance,
                    posteriorMeanIncrementLog = 0.0,
                    transitionDays = 0,
                    baselineEstablishedBefore = false,
                    baselineEstablishedAfter = true,
                    proxyTransferEligible = false,
                    numericalDiagnostics = likelihood.diagnostics,
                    evidenceFingerprint = observation.evidenceFingerprint
                ),
                diagnostics = listOf("LOCAL_BASELINE_ESTABLISHED")
            )
        }

        val days = ChronoUnit.DAYS.between(currentState.lastProcessedDate, observation.date).coerceAtLeast(0)
        val priorVariance = (
            currentState.logVariance + LOCAL_DAILY_PROCESS_VARIANCE * days.coerceAtMost(MAX_PROCESS_DAYS)
            ).coerceAtLeast(MIN_VARIANCE)
        val posterior = ScalarGridPosteriorEngine.posteriorMoments(
            priorMean = currentState.logMean,
            priorVariance = priorVariance,
            likelihood = observation.sessionLikelihood.asScalarLikelihood()
        )
        val innovationResidual = properMoments?.mean?.minus(currentState.logMean)
        val innovationVariance = properMoments?.variance?.plus(priorVariance)
        val state = StrengthExerciseLocalState(
            exerciseStableKey = observation.exerciseStableKey,
            logMean = posterior.mean,
            logVariance = posterior.variance,
            lastProcessedEventUuid = eventUuid,
            lastProcessedSessionKey = observation.sessionKey,
            lastProcessedDate = observation.date,
            baselineEstablished = true,
            observationCount = currentState.observationCount + 1,
            twoSidedObservationCount = currentState.twoSidedObservationCount + if (properMoments != null) 1 else 0
        )
        return StrengthExercisePosteriorComputation(
            state = state,
            history = StrengthExerciseLocalHistory(
                eventUuid = eventUuid,
                sessionKey = observation.sessionKey,
                sessionDate = observation.date,
                exerciseStableKey = observation.exerciseStableKey,
                priorLogMean = currentState.logMean,
                priorLogVariance = priorVariance,
                sessionLikelihoodLogMean = properMoments?.mean,
                sessionLikelihoodLogVariance = properMoments?.variance,
                sessionLikelihoodProper = properMoments != null,
                innovationResidualLog = innovationResidual,
                innovationVariance = innovationVariance,
                posteriorLogMean = state.logMean,
                posteriorLogVariance = state.logVariance,
                posteriorMeanIncrementLog = state.logMean - currentState.logMean,
                transitionDays = days,
                baselineEstablishedBefore = true,
                baselineEstablishedAfter = true,
                proxyTransferEligible = properMoments != null,
                numericalDiagnostics = posterior.diagnostics,
                evidenceFingerprint = observation.evidenceFingerprint
            ),
            diagnostics = emptyList()
        )
    }

    private const val INITIAL_BASELINE_LOG_VARIANCE = 0.08
    private const val LOCAL_DAILY_PROCESS_VARIANCE = 0.00010
    private const val MAX_PROCESS_DAYS = 3650L
    private const val MIN_VARIANCE = 1e-8
}
