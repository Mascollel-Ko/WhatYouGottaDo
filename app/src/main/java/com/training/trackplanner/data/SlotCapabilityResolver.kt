package com.training.trackplanner.data

import java.util.Locale

enum class SlotCapabilitySource { RUNTIME_METADATA, LEGACY_METADATA, NAME_FALLBACK, NONE }

enum class SlotCapabilityConfidence { HIGH, MODERATE, LOW, NONE }

data class SlotCapabilityProfile(
    val primary: Set<ProgramSlotId>,
    val secondary: Set<ProgramSlotId>,
    val weakMatches: Set<ProgramSlotId>,
    val source: SlotCapabilitySource,
    val confidence: SlotCapabilityConfidence = SlotCapabilityConfidence.NONE,
    val warnings: List<String> = emptyList()
) {
    fun hasAny(vararg ids: ProgramSlotId): Boolean =
        ids.any { it in primary || it in secondary || it in weakMatches }
}

class SlotCapabilityResolver {
    fun resolve(exercise: Exercise, metadata: RuntimeExerciseMetadata?): SlotCapabilityProfile {
        if (metadata != null) {
            val identityMatches = matchSlots(metadataIdentityTokens(metadata))
            val supportingMatches = matchSlots(metadataSupportingTokens(metadata))
                .filterNot(identityMatches::contains)
            if (identityMatches.isNotEmpty() || supportingMatches.isNotEmpty()) {
                return structuredProfile(
                    identityMatches = identityMatches,
                    supportingMatches = supportingMatches,
                    source = SlotCapabilitySource.RUNTIME_METADATA,
                    confidence = if (identityMatches.isNotEmpty()) {
                        SlotCapabilityConfidence.HIGH
                    } else {
                        SlotCapabilityConfidence.MODERATE
                    }
                )
            }
        }

        val legacyMatches = if (metadata == null) matchSlots(legacyTokens(exercise)) else emptyList()
        if (legacyMatches.isNotEmpty()) {
            return structuredProfile(
                identityMatches = legacyMatches,
                supportingMatches = emptyList(),
                source = SlotCapabilitySource.LEGACY_METADATA,
                confidence = SlotCapabilityConfidence.LOW,
                baseWarnings = listOf("LEGACY_METADATA_FALLBACK")
            )
        }

        val nameMatches = explicitNameFallback(exercise.name)
        if (nameMatches.isNotEmpty()) {
            return SlotCapabilityProfile(
                primary = emptySet(),
                secondary = emptySet(),
                weakMatches = nameMatches.take(MAX_WEAK).toSet(),
                source = SlotCapabilitySource.NAME_FALLBACK,
                confidence = SlotCapabilityConfidence.LOW,
                warnings = buildList {
                    if (metadata != null) add("RUNTIME_METADATA_NO_SLOT_MATCH")
                    add("NAME_ONLY_FALLBACK")
                    if (nameMatches.size > MAX_WEAK) add("CAPABILITY_TRUNCATED")
                }
            )
        }

        return SlotCapabilityProfile(
            primary = emptySet(),
            secondary = emptySet(),
            weakMatches = emptySet(),
            source = if (metadata != null) SlotCapabilitySource.RUNTIME_METADATA else SlotCapabilitySource.NONE,
            confidence = SlotCapabilityConfidence.NONE,
            warnings = listOf("NO_SLOT_CAPABILITY")
        )
    }

    private fun structuredProfile(
        identityMatches: List<ProgramSlotId>,
        supportingMatches: List<ProgramSlotId>,
        source: SlotCapabilitySource,
        confidence: SlotCapabilityConfidence,
        baseWarnings: List<String> = emptyList()
    ): SlotCapabilityProfile {
        val ordered = (identityMatches + supportingMatches).distinct()
        return SlotCapabilityProfile(
            primary = ordered.take(MAX_PRIMARY).toSet(),
            secondary = ordered.drop(MAX_PRIMARY).take(MAX_SECONDARY).toSet(),
            weakMatches = ordered.drop(MAX_PRIMARY + MAX_SECONDARY).take(MAX_WEAK).toSet(),
            source = source,
            confidence = confidence,
            warnings = buildList {
                addAll(baseWarnings)
                if (ordered.size > MAX_PRIMARY + MAX_SECONDARY + MAX_WEAK) add("CAPABILITY_TRUNCATED")
            }
        )
    }

    private fun metadataIdentityTokens(metadata: RuntimeExerciseMetadata): Set<String> = listOf(
        metadata.movementFamily,
        metadata.movementSubtype,
        metadata.programSlot,
        metadata.redundancyGroup,
        metadata.strengthProgressionGroup
    ).tokenSet()

    private fun metadataSupportingTokens(metadata: RuntimeExerciseMetadata): Set<String> = buildList {
        add(metadata.primaryStressProfile)
        addAll(metadata.analysisEligibility.values)
        addAll(metadata.secondaryStressTags.values)
        addAll(metadata.badmintonTransferType.values)
        addAll(metadata.badmintonPhysicalQualities.values)
    }.tokenSet()

    private fun legacyTokens(exercise: Exercise): Set<String> = listOf(
        exercise.movementPattern,
        exercise.movementCategory,
        exercise.trainingRole,
        exercise.strengthProgressionGroup,
        exercise.badmintonTransferRoles,
        exercise.stabilityRoles,
        exercise.accessoryRoles
    ).tokenSet()

    private fun matchSlots(tokens: Set<String>): List<ProgramSlotId> = RULES.mapNotNull { rule ->
        rule.slot.takeIf { tokens.hasAny(*rule.needles.toTypedArray()) }
    }

    private fun explicitNameFallback(name: String): List<ProgramSlotId> {
        val normalized = name.trim().uppercase(Locale.ROOT)
        if (normalized.isBlank()) return emptyList()
        return NAME_RULES.mapNotNull { rule ->
            rule.slot.takeIf { rule.needles.any(normalized::contains) }
        }.distinct()
    }

    companion object {
        val DEFAULT = SlotCapabilityResolver()

        private const val MAX_PRIMARY = 1
        private const val MAX_SECONDARY = 2
        private const val MAX_WEAK = 1

        private val RULES = listOf(
            CapabilityRule(ProgramSlotId.BADMINTON_DECEL_COD, "DECELERATION", "CHANGE_OF_DIRECTION", "BADMINTON_COD"),
            CapabilityRule(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, "FOOTWORK", "SPLIT_STEP", "REACTION_DRILL"),
            CapabilityRule(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME, "PLYOMETRIC", "REACTIVE_POWER", "ELASTIC_SSC", "JUMP", "HOP"),
            CapabilityRule(ProgramSlotId.LOWER_SQUAT_PATTERN, "SQUAT", "KNEE_DOMINANT"),
            CapabilityRule(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, "HINGE", "DEADLIFT", "POSTERIOR_CHAIN"),
            CapabilityRule(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, "SINGLE_LEG", "LUNGE", "STEP_UP", "SPLIT_SQUAT"),
            CapabilityRule(ProgramSlotId.UPPER_PULL_ANCHOR, "VERTICAL_PULL", "HORIZONTAL_PULL", "PULL_UP", "LAT_PULLDOWN", "ROW_VARIANTS"),
            CapabilityRule(
                ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
                "LANDMINE_PRESS",
                "HALF_KNEELING_PRESS",
                "OVERHEAD_PRESS",
                "VERTICAL_PUSH",
                "SHOULDER_PRESS",
                "KETTLEBELL_PRESS",
                "PUSH_PRESS"
            ),
            CapabilityRule(ProgramSlotId.UPPER_PUSH_SUPPORT, "HORIZONTAL_PUSH", "BENCH_PRESS", "PUSH_UP", "CHEST_PRESS"),
            CapabilityRule(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, "ANTI_ROTATION", "ANTI_EXTENSION", "CORE_BRACING", "TRUNK_STABILITY"),
            CapabilityRule(
                ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
                "ROTATIONAL",
                "ROTATION_POWER",
                "WOODCHOP",
                "CABLE_CHOP",
                "CABLE_LIFT",
                "VIPR_CHOP",
                "VIPR_LIFT",
                "VIPR_SHOVEL",
                "VIPR_SCOOP",
                "STEP_AND_ROTATE",
                "ROTATIONAL_THROW",
                "LANDMINE_ROTATION",
                "KETTLEBELL_HALO",
                "VIPR_DOWNWARD_TWIST"
            ),
            CapabilityRule(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, "SCAP", "ROTATOR_CUFF", "REAR_DELT", "SHOULDER_DURABILITY"),
            CapabilityRule(ProgramSlotId.CALF_ANKLE_CAPACITY, "CALF", "ANKLE", "ACHILLES"),
            CapabilityRule(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, "FOREARM", "GRIP", "WRIST", "ELBOW_SUPPORT"),
            CapabilityRule(ProgramSlotId.RECOVERY_PREHAB_LIGHT, "RECOVERY", "PREHAB", "MOBILITY", "LOW_LOAD_CONTROL"),
            CapabilityRule(ProgramSlotId.ACCESSORY_ROTATION, "ACCESSORY", "ISOLATION")
        )

        private val NAME_RULES = listOf(
            CapabilityRule(
                ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
                "LANDMINE PRESS",
                "HALF-KNEELING PRESS",
                "HALF KNEELING PRESS",
                "DUMBBELL SHOULDER PRESS",
                "KETTLEBELL PRESS",
                "랜드마인 프레스",
                "하프 닐링 프레스",
                "덤벨 숄더프레스",
                "케틀벨 프레스"
            ),
            CapabilityRule(
                ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
                "VIPR ROTATIONAL",
                "VIPR CHOP",
                "VIPR SHOVEL",
                "VIPR SCOOP",
                "STEP-AND-ROTATE",
                "CABLE CHOP",
                "CABLE LIFT",
                "ROTATIONAL THROW",
                "LANDMINE ROTATION",
                "DUMBBELL WOODCHOP",
                "BAND CHOP",
                "BAND LIFT",
                "KETTLEBELL HALO",
                "케이블 찹",
                "케이블 리프트",
                "랜드마인 로테이션",
                "덤벨 우드찹",
                "케틀벨 헤일로"
            )
        )
    }
}

private data class CapabilityRule(
    val slot: ProgramSlotId,
    val needles: List<String>
) {
    constructor(slot: ProgramSlotId, vararg needles: String) : this(slot, needles.toList())
}

private fun Iterable<String>.tokenSet(): Set<String> = flatMap { value ->
    value.split('|', ',', '/', ';', ' ')
        .map { it.trim().uppercase(Locale.ROOT) }
        .filter(String::isNotBlank)
}.toSet()

private fun Set<String>.hasAny(vararg needles: String): Boolean = needles.any { needle ->
    any { token -> token == needle || token.contains(needle) }
}
