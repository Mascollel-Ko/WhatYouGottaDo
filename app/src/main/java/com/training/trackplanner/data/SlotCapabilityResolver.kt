package com.training.trackplanner.data

import java.util.Locale

enum class SlotCapabilitySource { RUNTIME_METADATA, LEGACY_METADATA, NONE }

data class SlotCapabilityProfile(
    val primary: Set<ProgramSlotId>,
    val secondary: Set<ProgramSlotId>,
    val weakMatches: Set<ProgramSlotId>,
    val source: SlotCapabilitySource
) {
    fun hasAny(vararg ids: ProgramSlotId): Boolean =
        ids.any { it in primary || it in secondary || it in weakMatches }
}

class SlotCapabilityResolver {
    fun resolve(exercise: Exercise, metadata: RuntimeExerciseMetadata?): SlotCapabilityProfile {
        val metadataTokens = metadata?.let { value ->
            buildList {
                add(value.movementFamily)
                add(value.movementSubtype)
                add(value.programSlot)
                add(value.redundancyGroup)
                add(value.strengthProgressionGroup)
                add(value.primaryStressProfile)
                addAll(value.analysisEligibility.values)
                addAll(value.secondaryStressTags.values)
                addAll(value.badmintonTransferType.values)
                addAll(value.badmintonPhysicalQualities.values)
            }.tokenSet()
        }.orEmpty()
        val legacyTokens = if (metadata == null) {
            listOf(
                exercise.movementPattern,
                exercise.movementCategory,
                exercise.trainingRole,
                exercise.strengthProgressionGroup,
                exercise.badmintonTransferRoles
            ).tokenSet()
        } else {
            emptySet()
        }
        val tokens = metadataTokens.ifEmpty { legacyTokens }
        if (tokens.isEmpty()) return SlotCapabilityProfile(emptySet(), emptySet(), emptySet(), SlotCapabilitySource.NONE)

        val matches = linkedSetOf<ProgramSlotId>()
        fun addIf(id: ProgramSlotId, vararg needles: String) {
            if (tokens.hasAny(*needles)) matches += id
        }
        addIf(ProgramSlotId.BADMINTON_DECEL_COD, "DECELERATION", "CHANGE_OF_DIRECTION", "BADMINTON_COD")
        addIf(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, "FOOTWORK", "SPLIT_STEP", "REACTION_DRILL")
        addIf(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME, "PLYOMETRIC", "REACTIVE_POWER", "ELASTIC_SSC", "JUMP", "HOP")
        addIf(ProgramSlotId.LOWER_SQUAT_PATTERN, "SQUAT", "KNEE_DOMINANT")
        addIf(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, "HINGE", "DEADLIFT", "POSTERIOR_CHAIN")
        addIf(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, "SINGLE_LEG", "LUNGE", "STEP_UP", "SPLIT_SQUAT")
        addIf(ProgramSlotId.UPPER_PULL_ANCHOR, "VERTICAL_PULL", "HORIZONTAL_PULL", "PULL_UP", "LAT_PULLDOWN", "ROW_VARIANTS")
        addIf(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, "OVERHEAD_PRESS", "LANDMINE_PRESS", "HALF_KNEELING_PRESS")
        addIf(ProgramSlotId.UPPER_PUSH_SUPPORT, "HORIZONTAL_PUSH", "BENCH_PRESS", "PUSH_UP", "CHEST_PRESS")
        addIf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, "ANTI_ROTATION", "ANTI_EXTENSION", "CORE_BRACING", "TRUNK")
        addIf(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, "ROTATIONAL", "ROTATION_POWER", "ROTATION_CONTROL")
        addIf(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, "SCAP", "ROTATOR_CUFF", "REAR_DELT", "SHOULDER_DURABILITY")
        addIf(ProgramSlotId.CALF_ANKLE_CAPACITY, "CALF", "ANKLE", "ACHILLES")
        addIf(ProgramSlotId.RECOVERY_PREHAB_LIGHT, "RECOVERY", "PREHAB", "MOBILITY", "LOW_LOAD_CONTROL")
        addIf(ProgramSlotId.ACCESSORY_ROTATION, "ACCESSORY", "ISOLATION")

        val ordered = matches.toList()
        val source = if (metadataTokens.isNotEmpty()) {
            SlotCapabilitySource.RUNTIME_METADATA
        } else {
            SlotCapabilitySource.LEGACY_METADATA
        }
        return SlotCapabilityProfile(
            primary = ordered.take(1).toSet(),
            secondary = ordered.drop(1).take(2).toSet(),
            weakMatches = ordered.drop(3).take(1).toSet(),
            source = source
        )
    }

    companion object {
        val DEFAULT = SlotCapabilityResolver()
    }
}

private fun Iterable<String>.tokenSet(): Set<String> = flatMap { value ->
    value.split('|', ',', '/', ';', ' ')
        .map { it.trim().uppercase(Locale.US) }
        .filter(String::isNotBlank)
}.toSet()

private fun Set<String>.hasAny(vararg needles: String): Boolean = needles.any { needle ->
    any { token -> token.contains(needle) }
}
