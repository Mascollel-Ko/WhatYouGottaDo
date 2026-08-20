package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureDescriptor
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshot
import com.training.trackplanner.analysis.lab.weekly.WeeklyCellState
import java.time.LocalDate

internal class StrictPhaseAInputBundle private constructor(
    val rawInput: RawTimeSeriesInput,
    val request: StrictPreparationRequest,
    val policy: StrictPreparationPolicy,
    sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey>,
    descriptors: Map<StrictSeriesKey, AnalysisFeatureDescriptor>,
    conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey>,
    val snapshotFingerprint: String,
    val fingerprint: String
) {
    val sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey> = sourceByFeature.toMap()
    val descriptors: Map<StrictSeriesKey, AnalysisFeatureDescriptor> = descriptors.toMap()
    val conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey> = conditionalOnFeatureByFeature.toMap()

    companion object {
        fun createValidated(
            rawInput: RawTimeSeriesInput,
            request: StrictPreparationRequest,
            policy: StrictPreparationPolicy,
            sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey>,
            descriptors: Map<StrictSeriesKey, AnalysisFeatureDescriptor>,
            conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey>,
            snapshotFingerprint: String
        ): StrictPhaseAInputBundle {
            require(snapshotFingerprint.isNotBlank())
            require(sourceByFeature.keys == request.allMetrics)
            require(descriptors.keys == request.allMetrics)
            require(conditionalOnFeatureByFeature.keys.all { it in request.allMetrics })
            require(conditionalOnFeatureByFeature.values.all { it in request.allMetrics })
            val fingerprint = strictFingerprint(
                listOf(
                    snapshotFingerprint,
                    request.xMetric.stableId,
                    request.yMetrics.joinToString(",") { it.stableId },
                    request.controls.joinToString(",") { it.stableId },
                    request.supportMetrics.joinToString(",") { it.stableId },
                    request.optionalCandidates.joinToString(",") { it.stableId },
                    sourceByFeature.toSortedMap(compareBy { it.stableId }).entries.joinToString(",") {
                        "${it.key.stableId}:${it.value.value}"
                    },
                    conditionalOnFeatureByFeature.toSortedMap(compareBy { it.stableId }).entries.joinToString(",") {
                        "${it.key.stableId}:${it.value.stableId}"
                    },
                    STRICT_SOURCE_GROUPING_VERSION,
                    policy.fingerprint,
                    WEEKLY_SNAPSHOT_PHASE_A_ADAPTER_VERSION
                )
            )
            return StrictPhaseAInputBundle(
                rawInput,
                request,
                policy,
                sourceByFeature,
                descriptors,
                conditionalOnFeatureByFeature,
                snapshotFingerprint,
                fingerprint
            )
        }
    }
}

internal data class StrictFeatureSelection(
    val xFeature: AnalysisFeatureKey,
    val yFeatures: List<AnalysisFeatureKey>,
    val controls: List<AnalysisFeatureKey>,
    val requestedHorizon: Int
) {
    fun normalized(): StrictFeatureSelection = copy(
        yFeatures = yFeatures.distinct().filterNot { it == xFeature },
        controls = controls.distinct().filterNot { it == xFeature || it in yFeatures }
    )
}

internal object WeeklySnapshotPhaseAAdapter {
    fun adapt(
        snapshot: WeeklyAnalysisFeatureSnapshot,
        strictRequest: StrictFeatureSelection
    ): StrictPhaseAInputBundle {
        val normalized = strictRequest.normalized()
        val x = normalized.xFeature
        val responses = normalized.yFeatures
        val controls = normalized.controls
        val focal: Set<StrictSeriesKey> = (listOf(x) + responses + controls).toSet()
        require(focal.all { it in snapshot.descriptors }) { "requested strict feature is absent from weekly snapshot" }
        val optional: List<StrictSeriesKey> = snapshot.descriptors.keys
            .filterNot { it in focal }
            .filter { key ->
                snapshot.descriptors.getValue(key).family != AnalysisFeatureFamily.CUMULATIVE_OR_UNKNOWN &&
                    isAutomaticPhaseBCandidate(key)
            }
            .sorted()
        val conditionalSupport: List<StrictSeriesKey> = (focal + optional)
            .mapNotNull { feature ->
                val descriptor = snapshot.descriptors.getValue(feature as AnalysisFeatureKey)
                if (descriptor.family != AnalysisFeatureFamily.CONDITIONAL_RPE) return@mapNotNull null
                AnalysisFeatureKey.exercise(exerciseStableKey(feature), "on")
                    .takeIf { it in snapshot.descriptors }
            }
            .distinct()
            .sorted()
        val required = focal + conditionalSupport
        val allFeatures: Set<StrictSeriesKey> = (required + optional).toSet()
        val descriptors: Map<StrictSeriesKey, AnalysisFeatureDescriptor> = allFeatures.associateWith {
            snapshot.descriptors.getValue(it as AnalysisFeatureKey)
        }
        val conditionalOn: Map<StrictSeriesKey, StrictSeriesKey> = descriptors.values
            .filter { it.family == AnalysisFeatureFamily.CONDITIONAL_RPE }
            .associate { descriptor ->
                descriptor.featureKey to AnalysisFeatureKey.exercise(
                    exerciseStableKey(descriptor.featureKey),
                    "on"
                )
            }
            .filterValues { it in allFeatures }
        val observations = allFeatures.flatMap { feature ->
            val family = descriptors.getValue(feature).family
            snapshot.closedWeeks.map { week ->
                val cell = requireNotNull(snapshot.cell(feature as AnalysisFeatureKey, week))
                val carrier = family == AnalysisFeatureFamily.CONDITIONAL_RPE && cell.state == WeeklyCellState.NOT_APPLICABLE
                RawTimeSeriesObservation(
                    metric = feature,
                    date = week,
                    value = if (carrier) 0.0 else cell.value,
                    declaredState = when {
                        carrier -> StrictCellState.STRUCTURAL_ZERO
                        cell.state == WeeklyCellState.OBSERVED -> StrictCellState.OBSERVED_VALUE
                        cell.state == WeeklyCellState.STRUCTURAL_ZERO -> StrictCellState.STRUCTURAL_ZERO
                        cell.state == WeeklyCellState.NOT_APPLICABLE -> StrictCellState.NOT_APPLICABLE
                        else -> StrictCellState.MISSING
                    },
                    source = cell.provenance,
                    sourceIndex = snapshot.weeks.indexOf(week)
                )
            }
        }
        val lifecycle: Map<StrictSeriesKey, StrictMetricLifecycle> = allFeatures.associateWith { feature ->
            val featureCells = snapshot.closedWeeks.map { week -> requireNotNull(snapshot.cell(feature as AnalysisFeatureKey, week)) }
            val conditional = descriptors.getValue(feature).family == AnalysisFeatureFamily.CONDITIONAL_RPE
            val notApplicable = if (conditional) emptyList() else contiguousRanges(
                featureCells.filter { it.state == WeeklyCellState.NOT_APPLICABLE }.map { it.weekStart }
            )
            StrictMetricLifecycle.createValidated(
                availableFromWeek = snapshot.closedWeeks.firstOrNull(),
                availableUntilWeek = snapshot.closedWeeks.lastOrNull(),
                structuralZeroAllowed = conditional || featureCells.any { it.state == WeeklyCellState.STRUCTURAL_ZERO },
                notApplicableRanges = notApplicable,
                provenance = "weekly snapshot ${snapshot.fingerprint}"
            )
        }
        val request = StrictPreparationRequest(
            xMetric = x,
            yMetrics = responses,
            controls = controls,
            supportMetrics = conditionalSupport,
            optionalCandidates = optional,
            horizons = setOf(normalized.requestedHorizon)
        )
        val shortHistory: Map<StrictSeriesKey, CanonicalSeriesTransformation> = descriptors.mapNotNull { (feature, descriptor) ->
            shortHistoryTransformation(descriptor.family)?.let { feature to it }
        }.toMap()
        return StrictPhaseAInputBundle.createValidated(
            rawInput = RawTimeSeriesInput.createValidated(observations, lifecycle),
            request = request,
            policy = StrictPreparationPolicy.createValidated(shortHistoryTransformations = shortHistory),
            sourceByFeature = descriptors.mapValues { (feature, _) -> modelSource(feature) },
            descriptors = descriptors,
            conditionalOnFeatureByFeature = conditionalOn,
            snapshotFingerprint = snapshot.fingerprint
        )
    }

    private fun isAutomaticPhaseBCandidate(featureKey: AnalysisFeatureKey): Boolean {
        if (featureKey.value.startsWith("exercise:")) {
            return featureKey.value.substringAfterLast(':') in setOf("base_dose", "mean_rpe")
        }
        return true
    }

    private fun modelSource(feature: StrictSeriesKey): AnalysisSourceKey =
        AnalysisSourceKey.parse("feature:${feature.stableId}")

    private fun shortHistoryTransformation(family: AnalysisFeatureFamily): CanonicalSeriesTransformation? = when (family) {
        AnalysisFeatureFamily.TRAINING_FLOW,
        AnalysisFeatureFamily.ANATOMY_LOAD,
        AnalysisFeatureFamily.EXPOSURE_INDICATOR,
        AnalysisFeatureFamily.CONDITIONAL_RPE,
        AnalysisFeatureFamily.RECOVERY_CHECK_IN -> CanonicalSeriesTransformation.LEVEL
        AnalysisFeatureFamily.PERFORMANCE,
        AnalysisFeatureFamily.PERSISTENT_PERFORMANCE -> CanonicalSeriesTransformation.FIRST_DIFFERENCE
        AnalysisFeatureFamily.CUMULATIVE_OR_UNKNOWN -> null
    }

    private fun exerciseStableKey(feature: AnalysisFeatureKey): String =
        feature.value.split(':').getOrNull(1).orEmpty().also { require(it.isNotBlank()) }

    private fun contiguousRanges(weeks: List<LocalDate>): List<StrictWeekRange> {
        if (weeks.isEmpty()) return emptyList()
        val ordered = weeks.distinct().sorted()
        val ranges = mutableListOf<StrictWeekRange>()
        var start = ordered.first()
        var previous = start
        ordered.drop(1).forEach { week ->
            if (previous.plusWeeks(1) != week) {
                ranges += StrictWeekRange(start, previous)
                start = week
            }
            previous = week
        }
        ranges += StrictWeekRange(start, previous)
        return ranges
    }
}

internal const val WEEKLY_SNAPSHOT_PHASE_A_ADAPTER_VERSION = "weekly-snapshot-phase-a-adapter-v1"
internal const val STRICT_SOURCE_GROUPING_VERSION = "strict-single-feature-source-group-v1"
