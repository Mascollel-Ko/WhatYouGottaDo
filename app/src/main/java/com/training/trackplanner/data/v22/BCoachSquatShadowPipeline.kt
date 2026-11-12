package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import java.time.LocalDate

/**
 * Read-only vertical slice from the existing canonical posterior and last confirmed squat
 * through relative RIR candidates to Gate C experimental ranking. Not used by the B generator.
 * No writes, no training prescription, no background activity.
 */
internal sealed interface BCoachSquatShadowPipelineResult {
    data class ModelUnavailable(
        val reason: BCoachSquatEvidenceResult.Reason
    ) : BCoachSquatShadowPipelineResult

    data class ShadowReport(
        val input: BCoachSquatEvidence,
        val relative: BCoachSquatRelativeShadow.Result,
        val selection: BCoachSquatShadowSelector.Decision
    ) : BCoachSquatShadowPipelineResult
}

internal object BCoachSquatShadowPipeline {
    fun evaluate(
        input: BCoachSquatEvidenceResult,
        rirPolicy: RpeRirPolicy,
        smallestPlateOneSideKg: Double,
        targetRirMin: Double,
        targetRirMax: Double,
        policies: List<BCoachSquatShadowSelector.Weights>,
        omegaBands: List<BCoachSquatShadowSelector.OmegaBand>
    ): BCoachSquatShadowPipelineResult = when (input) {
        is BCoachSquatEvidenceResult.Unavailable ->
            BCoachSquatShadowPipelineResult.ModelUnavailable(input.reason)
        is BCoachSquatEvidenceResult.Ready -> {
            val relative = BCoachSquatRelativeShadow.evaluate(
                evidence = input.evidence,
                rirPolicy = rirPolicy,
                smallestPlateOneSideKg = smallestPlateOneSideKg,
                targetRirMin = targetRirMin,
                targetRirMax = targetRirMax
            )
            val comparison = BCoachSquatShadowSelector.compare(
                input = relative,
                policies = policies,
                omegaBands = omegaBands,
                targetRirMin = targetRirMin,
                targetRirMax = targetRirMax
            )
            BCoachSquatShadowPipelineResult.ShadowReport(input.evidence, relative, comparison)
        }
    }
}

internal class BCoachSquatShadowPipelineReader(
    private val evidenceReader: BCoachSquatShadowEvidenceReader,
    private val rirPolicy: RpeRirPolicy
) {
    suspend fun inspect(
        asOf: LocalDate,
        smallestPlateOneSideKg: Double,
        targetRirMin: Double,
        targetRirMax: Double,
        policies: List<BCoachSquatShadowSelector.Weights>,
        omegaBands: List<BCoachSquatShadowSelector.OmegaBand>
    ): BCoachSquatShadowPipelineResult =
        BCoachSquatShadowPipeline.evaluate(
            input = evidenceReader.read(asOf),
            rirPolicy = rirPolicy,
            smallestPlateOneSideKg = smallestPlateOneSideKg,
            targetRirMin = targetRirMin,
            targetRirMax = targetRirMax,
            policies = policies,
            omegaBands = omegaBands
        )
}
