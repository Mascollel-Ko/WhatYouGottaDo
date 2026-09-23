package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality

/** Authority used to decide whether a source identity has reviewed canonical meaning. */
enum class StimulusClassificationAuthority { REVIEWED_CANONICAL, UNCLASSIFIED }

/** Quality-specific completeness of one canonical source observation. */
enum class QualityObservationClassification {
    CLASSIFIED,
    UNCLASSIFIED,
    IRRELEVANT
}

/** Completeness of the source evidence relevant to one quality or task view. */
enum class StimulusEvidenceCoverage { COMPLETE, PARTIAL, UNAVAILABLE }

/** The semantic authority behind a stimulus observation or target. */
enum class StimulusEvidenceBasis {
    REALIZED_PRESCRIPTION_CLASSIFIED,
    CANONICAL_CAPABILITY_PROXY,
    CANONICAL_TASK_RELATION,
    UNCLASSIFIED
}

/** Whether a B2 weekly distribution can establish an exact numeric baseline. */
enum class DoseBaselineObservability {
    COMPLETE,
    PARTIAL_UNCLASSIFIED,
    NO_ELIGIBLE_CLASSIFIED_HISTORY,
    UNAVAILABLE
}

/** Compatibility of the existing prescription probe; this is not final materialization. */
enum class SelectionProbePrescriptionCompatibility {
    REALIZED_COMPATIBLE,
    REALIZED_INCOMPATIBLE,
    REALIZATION_UNCLASSIFIED,
    DIRECTIONAL_TASK_IDENTITY_ONLY
}

fun evidenceBasisForQuality(quality: TrainableQuality): StimulusEvidenceBasis = when (quality) {
    TrainableQuality.STRENGTH,
    TrainableQuality.HYPERTROPHY -> StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED
    else -> StimulusEvidenceBasis.CANONICAL_CAPABILITY_PROXY
}

/**
 * Resolves observability without conflating source identity with realization authority.
 * Strength and hypertrophy require a reviewed realization (including a reviewed zero), while
 * capability-proxy qualities only require reviewed canonical source identity.
 */
internal fun qualityObservationClassification(
    quality: TrainableQuality,
    observation: StimulusSetObservation,
    hasCanonicalRelation: Boolean
): QualityObservationClassification {
    if (!hasCanonicalRelation) return QualityObservationClassification.IRRELEVANT
    if (observation.classificationAuthority == StimulusClassificationAuthority.UNCLASSIFIED) {
        return QualityObservationClassification.UNCLASSIFIED
    }
    if (quality == TrainableQuality.STRENGTH || quality == TrainableQuality.HYPERTROPHY) {
        return when (observation.realizedStimulusClassification.status) {
            RealizedStimulusStatus.REALIZED,
            RealizedStimulusStatus.REVIEWED_NON_REALIZATION -> QualityObservationClassification.CLASSIFIED
            RealizedStimulusStatus.UNCLASSIFIED -> QualityObservationClassification.UNCLASSIFIED
        }
    }
    return QualityObservationClassification.CLASSIFIED
}
