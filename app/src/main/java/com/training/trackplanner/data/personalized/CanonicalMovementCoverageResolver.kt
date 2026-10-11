package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.RuntimeExerciseMetadata

/**
 * Resolves direct movement coverage from the reviewed movement relation and its canonical
 * runtime family/slot. A slot is context; it is not, by itself, authority to claim a movement.
 */
data class CanonicalMovementCoverageDecision(
    val coverage: MovementCoverage,
    val directCoverageAdmissible: Boolean,
    val reason: String,
    val canonicalPatterns: List<String> = emptyList()
)

internal object CanonicalMovementCoverageResolver {
    private val acceptedStatuses = setOf("DRAFT_READY", "REVIEW_RECOMMENDED")

    fun resolve(
        metadata: RuntimeExerciseMetadata?,
        relations: Iterable<CanonicalMetadataRelation>,
        identityMovementPattern: String? = null,
        identityReviewStatus: String? = null
    ): CanonicalMovementCoverageDecision {
        if (metadata == null) return CanonicalMovementCoverageDecision(MovementCoverage.OTHER, false, "RUNTIME_METADATA_MISSING")

        if (identityReviewStatus != null && identityReviewStatus !in acceptedStatuses) {
            return CanonicalMovementCoverageDecision(
                MovementCoverage.OTHER,
                false,
                "IDENTITY_MASTER_MOVEMENT_REVIEW_REQUIRED"
            )
        }

        val rows = relations.asSequence()
            .filter { it.exerciseStableKey.equals(metadata.stableKey, ignoreCase = true) }
            .filter { it.relationType == "MOVEMENT_PATTERN" }
            .toList()
        val acceptedFamilies = relations.asSequence()
            .filter { it.exerciseStableKey.equals(metadata.stableKey, ignoreCase = true) }
            .filter { it.relationType == "MOVEMENT_FAMILY" && it.status in acceptedStatuses }
            .map(CanonicalMetadataRelation::relationValue).toSet()
        val compatible = rows.filter { row ->
            row.status in acceptedStatuses &&
                compatiblePattern(metadata.programSlot, metadata.movementFamily, row.relationValue) &&
                (acceptedFamilies.isEmpty() || metadata.movementFamily in acceptedFamilies) &&
                identityPatternAgrees(identityMovementPattern, row.relationValue)
        }
        val coverages = compatible.mapNotNull { patternCoverage(it.relationValue) }.distinct()
        if (coverages.size == 1) {
            return CanonicalMovementCoverageDecision(
                coverage = coverages.single(),
                directCoverageAdmissible = true,
                reason = "REVIEWED_MOVEMENT_PATTERN_MATCHES_CANONICAL_SLOT_AND_FAMILY",
                canonicalPatterns = compatible.map(CanonicalMetadataRelation::relationValue).distinct().sorted()
            )
        }
        if (coverages.size > 1) {
            return CanonicalMovementCoverageDecision(
                MovementCoverage.OTHER,
                false,
                "MULTIPLE_INCOMPATIBLE_DIRECT_MOVEMENT_PATTERNS",
                compatible.map(CanonicalMetadataRelation::relationValue).distinct().sorted()
            )
        }

        val recognizedIdentityPatterns = directPatterns.filter { pattern ->
            identityMovementPattern.orEmpty().split('|').any { it.trim() == pattern }
        }.toSet()
        val matchingPatternsAwaitCanonicalReview = rows.any { row ->
            row.relationValue in recognizedIdentityPatterns && row.status == "REVIEW_REQUIRED"
        }
        if (recognizedIdentityPatterns.isNotEmpty() &&
            compatible.none { it.relationValue in recognizedIdentityPatterns } &&
            !matchingPatternsAwaitCanonicalReview) {
            return CanonicalMovementCoverageDecision(
                MovementCoverage.OTHER,
                false,
                "IDENTITY_MASTER_MOVEMENT_PATTERN_CONFLICT",
                rows.map(CanonicalMetadataRelation::relationValue).distinct().sorted()
            )
        }

        // These existing MovementCoverage values also name exact local regions rather than
        // compound movement patterns. Keep only narrow, established slots; broad accessories
        // cannot inherit push/pull/squat/hinge coverage through their slot label.
        val regional = when (metadata.programSlot) {
            "BICEPS_ACCESSORY", "BRACHIALIS_FOREARM_ACCESSORY" -> MovementCoverage.ARMS_BICEPS
            "TRICEPS_ACCESSORY" -> MovementCoverage.ARMS_TRICEPS
            "ANKLE_CALF_SUPPORT" -> MovementCoverage.CALVES
            else -> null
        }
        if (regional != null) {
            val familyIsReviewed = acceptedFamilies == setOf(metadata.movementFamily)
            return if (familyIsReviewed) {
                CanonicalMovementCoverageDecision(regional, true, "EXACT_REGION_ONLY_CANONICAL_SLOT")
            } else {
                CanonicalMovementCoverageDecision(
                    MovementCoverage.OTHER,
                    false,
                    "REGIONAL_SLOT_FAMILY_REQUIRES_CANONICAL_REVIEW",
                    relations.asSequence()
                        .filter { it.exerciseStableKey.equals(metadata.stableKey, ignoreCase = true) }
                        .filter { it.relationType == "MOVEMENT_FAMILY" }
                        .map(CanonicalMetadataRelation::relationValue)
                        .distinct().sorted().toList()
                )
            }
        }

        val patterns = rows.map(CanonicalMetadataRelation::relationValue).distinct().sorted()
        val reason = when {
            rows.isEmpty() -> "NO_CANONICAL_MOVEMENT_PATTERN_OR_EXACT_REGION_SLOT"
            rows.any { it.status == "REVIEW_REQUIRED" } -> "MOVEMENT_PATTERN_REQUIRES_CANONICAL_REVIEW"
            rows.any { patternCoverage(it.relationValue) != null } ->
                "MOVEMENT_PATTERN_SLOT_OR_FAMILY_CONFLICT"
            else -> "CANONICAL_PATTERN_IS_NOT_A_DIRECT_RESISTANCE_COVERAGE_TARGET"
        }
        return CanonicalMovementCoverageDecision(MovementCoverage.OTHER, false, reason, patterns)
    }

    fun resolveAll(
        metadataByStableKey: Map<String, RuntimeExerciseMetadata>,
        relations: Iterable<CanonicalMetadataRelation>,
        identityMovementPatterns: Map<String, String> = emptyMap(),
        identityReviewStatuses: Map<String, String> = emptyMap()
    ): Map<String, CanonicalMovementCoverageDecision> {
        val byKey = relations.groupBy { it.exerciseStableKey.lowercase() }
        return metadataByStableKey.toSortedMap().mapValues { (key, metadata) ->
            resolve(
                metadata,
                byKey[key.lowercase()].orEmpty(),
                identityMovementPatterns[key],
                identityReviewStatuses[key]
            )
        }
    }

    /** Safe compatibility for older synthetic snapshots that carry no canonical relation set. */
    fun fallbackWithoutRelations(metadata: RuntimeExerciseMetadata?): MovementCoverage {
        if (metadata == null) return MovementCoverage.OTHER
        return when (metadata.programSlot) {
            "MAIN_LOWER_STRENGTH", "MACHINE_LOWER_BODY_STRENGTH", "UNILATERAL_LOWER_STRENGTH",
            "UNILATERAL_LOWER_ACCESSORY", "KNEE_DOMINANT_ACCESSORY", "BODYWEIGHT_LOWER_PATTERN" -> MovementCoverage.LOWER_KNEE
            "MAIN_HINGE_STRENGTH", "POSTERIOR_CHAIN_ACCESSORY", "POSTERIOR_CHAIN_CONTROL_ACCESSORY",
            "HAMSTRING_ISOLATION_ACCESSORY", "GLUTE_POSTERIOR_CHAIN_ACCESSORY", "UNILATERAL_HINGE_ACCESSORY" -> MovementCoverage.POSTERIOR_CHAIN
            "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", "UPPER_PUSH_STRENGTH", "UPPER_PUSH_COMPOUND",
            "UPPER_PUSH_ACCESSORY", "TRICEPS_EMPHASIS_COMPOUND_PUSH" -> MovementCoverage.HORIZONTAL_PUSH
            "HORIZONTAL_PULL_STRENGTH" -> MovementCoverage.HORIZONTAL_PULL
            "VERTICAL_PULL_STRENGTH" -> MovementCoverage.VERTICAL_PULL
            "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY", "BODYWEIGHT_VERTICAL_PUSH", "UNILATERAL_SHOULDER_PRESS" -> MovementCoverage.VERTICAL_PUSH
            "CORE_STABILITY_ACCESSORY", "CORE_FLEXION_ACCESSORY", "TRUNK_ANTI_ROTATION_STABILITY",
            "CORE_STABILITY", "LOADED_CARRY_BRACING" -> MovementCoverage.CORE_DIRECT
            "ANKLE_CALF_SUPPORT" -> MovementCoverage.CALVES
            "BICEPS_ACCESSORY", "BRACHIALIS_FOREARM_ACCESSORY" -> MovementCoverage.ARMS_BICEPS
            "TRICEPS_ACCESSORY" -> MovementCoverage.ARMS_TRICEPS
            else -> MovementCoverage.OTHER
        }
    }

    private fun patternCoverage(pattern: String): MovementCoverage? = when (pattern) {
        "SQUAT", "LUNGE" -> MovementCoverage.LOWER_KNEE
        "HINGE" -> MovementCoverage.POSTERIOR_CHAIN
        "HORIZONTAL_PUSH" -> MovementCoverage.HORIZONTAL_PUSH
        "HORIZONTAL_PULL" -> MovementCoverage.HORIZONTAL_PULL
        "VERTICAL_PUSH" -> MovementCoverage.VERTICAL_PUSH
        "VERTICAL_PULL" -> MovementCoverage.VERTICAL_PULL
        "AXIAL_BRACING", "ANTI_ROTATION", "ANTI_EXTENSION", "DYNAMIC_TRUNK_STABILIZATION",
        "TRUNK_ROTATION", "TRUNK_FLEXION", "TRUNK_EXTENSION", "CARRY" -> MovementCoverage.CORE_DIRECT
        else -> null
    }

    internal val directPatterns = listOf(
        "HORIZONTAL_PULL", "VERTICAL_PULL", "HORIZONTAL_PUSH", "VERTICAL_PUSH",
        "SQUAT", "LUNGE", "HINGE", "AXIAL_BRACING", "ANTI_ROTATION", "ANTI_EXTENSION",
        "DYNAMIC_TRUNK_STABILIZATION", "TRUNK_ROTATION", "TRUNK_FLEXION", "TRUNK_EXTENSION", "CARRY"
    )

    private fun identityPatternAgrees(identityPattern: String?, canonicalPattern: String): Boolean {
        val recognized = identityPattern.orEmpty().split('|').map(String::trim).filter { it in directPatterns }
        return recognized.isEmpty() || canonicalPattern in recognized
    }

    private fun compatiblePattern(slot: String, family: String, pattern: String): Boolean = when (pattern) {
        "HORIZONTAL_PULL" -> slot == "HORIZONTAL_PULL_STRENGTH" && family == "ROW_VARIANTS"
        "VERTICAL_PULL" -> slot == "VERTICAL_PULL_STRENGTH" && family == "PULL_UP_CHIN_UP_LAT_PULLDOWN_VARIANTS"
        "HORIZONTAL_PUSH" -> when (family) {
            "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS" -> slot in setOf(
                "UPPER_PUSH_STRENGTH", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", "TRICEPS_EMPHASIS_COMPOUND_PUSH",
                "UNILATERAL_UPPER_PUSH"
            )
            "PUSH_UP_VARIANTS" -> slot == "UPPER_PUSH_ACCESSORY"
            else -> false
        }
        "VERTICAL_PUSH" -> when (family) {
            "OVERHEAD_PRESS_VARIANTS" -> slot == "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY"
            "BODYWEIGHT_VERTICAL_PUSH_VARIANTS" -> slot == "BODYWEIGHT_VERTICAL_PUSH"
            "HALF_KNEELING_UNILATERAL_SHOULDER_PRESS_VARIANTS" -> slot == "UNILATERAL_SHOULDER_PRESS"
            "DIP_COMPOUND_PUSH_VARIANTS" -> slot == "UPPER_PUSH_COMPOUND"
            else -> false
        }
        "SQUAT" -> when (family) {
            "SQUAT_VARIANTS" -> slot in setOf(
                "MAIN_LOWER_STRENGTH", "KNEE_DOMINANT_ACCESSORY", "BODYWEIGHT_LOWER_PATTERN", "UNILATERAL_LOWER_ACCESSORY"
            )
            "STEP_UP_VARIANTS" -> slot == "UNILATERAL_LOWER_ACCESSORY"
            "LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS" -> slot == "MACHINE_LOWER_BODY_STRENGTH"
            else -> false
        }
        "LUNGE" -> family == "LUNGE_SPLIT_SQUAT_VARIANTS" && slot == "UNILATERAL_LOWER_STRENGTH"
        "HINGE" -> family == "DEADLIFT_HINGE_VARIANTS" && slot in setOf(
            "MAIN_HINGE_STRENGTH", "POSTERIOR_CHAIN_ACCESSORY", "UNILATERAL_HINGE_ACCESSORY"
        )
        "AXIAL_BRACING", "ANTI_ROTATION", "ANTI_EXTENSION", "DYNAMIC_TRUNK_STABILIZATION",
        "TRUNK_ROTATION", "TRUNK_FLEXION", "TRUNK_EXTENSION" -> slot in setOf(
            "CORE_STABILITY", "CORE_STABILITY_ACCESSORY", "TRUNK_ANTI_ROTATION_STABILITY", "CORE_FLEXION_ACCESSORY"
        ) && family in setOf(
            "ANTI_ROTATION_ANTI_EXTENSION_CORE", "ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS",
            "TRUNK_ANTI_ROTATION_STABILITY", "CORE_FLEXION_ANTERIOR_CORE"
        )
        "CARRY" -> slot == "LOADED_CARRY_BRACING" && family == "LOADED_CARRY_VARIANTS"
        else -> false
    }
}
