package com.training.trackplanner.data

enum class CoverageCredit(val value: Double) {
    FULL(1.0),
    PARTIAL(0.5),
    WEAK(0.25),
    NONE(0.0)
}

class CoverageAccountingPolicy {
    fun credit(profile: SlotCapabilityProfile, slot: ProgramSlotId): CoverageCredit = when {
        slot == effectivePrimary(profile) && ProgramSlotDefinitions.get(slot).isUmbrella -> CoverageCredit.PARTIAL
        slot == effectivePrimary(profile) -> CoverageCredit.FULL
        slot in profile.primary -> CoverageCredit.PARTIAL
        slot in profile.secondary -> CoverageCredit.PARTIAL
        slot in profile.weakMatches -> CoverageCredit.WEAK
        else -> CoverageCredit.NONE
    }

    fun creditedSlots(profile: SlotCapabilityProfile): Map<ProgramSlotId, CoverageCredit> =
        ProgramSlotId.entries.mapNotNull { slot ->
            credit(profile, slot).takeIf { it != CoverageCredit.NONE }?.let { slot to it }
        }.toMap()

    fun totalCredit(
        profile: SlotCapabilityProfile,
        requestedSlots: Set<ProgramSlotId>
    ): Double = requestedSlots.sumOf { slot -> credit(profile, slot).value }.coerceAtMost(MAX_CREDIT_PER_EXERCISE)

    fun derivedUmbrellaCredit(
        profile: SlotCapabilityProfile,
        umbrellaSlot: ProgramSlotId
    ): CoverageCredit {
        if (!ProgramSlotDefinitions.get(umbrellaSlot).isUmbrella) return credit(profile, umbrellaSlot)
        val componentHits = umbrellaComponents(umbrellaSlot).count { slot ->
            credit(profile, slot).value >= CoverageCredit.PARTIAL.value
        }
        return when {
            componentHits >= 2 -> CoverageCredit.PARTIAL
            componentHits == 1 -> CoverageCredit.WEAK
            else -> CoverageCredit.NONE
        }
    }

    fun profile(item: ProgramSkeletonItem): SlotCapabilityProfile = SlotCapabilityProfile(
        primary = item.primarySlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        secondary = item.secondarySlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        weakMatches = item.weakSlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        source = item.slotCapabilitySource
            .let { source -> SlotCapabilitySource.entries.firstOrNull { it.name == source } }
            ?: SlotCapabilitySource.NONE,
        confidence = item.slotCapabilityConfidence
            .let { confidence -> SlotCapabilityConfidence.entries.firstOrNull { it.name == confidence } }
            ?: SlotCapabilityConfidence.NONE,
        warnings = item.slotCapabilityWarnings
    )

    private fun slotIdOrNull(value: String): ProgramSlotId? =
        ProgramSlotId.entries.firstOrNull { it.name == value }

    private fun effectivePrimary(profile: SlotCapabilityProfile): ProgramSlotId? =
        profile.primary.firstOrNull()

    private fun umbrellaComponents(slot: ProgramSlotId): Set<ProgramSlotId> = when (slot) {
        ProgramSlotId.OVERHEAD_SMASH_SUPPORT -> setOf(
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
        )
        else -> emptySet()
    }

    companion object {
        val DEFAULT = CoverageAccountingPolicy()
        const val MAX_CREDIT_PER_EXERCISE = 2.0
    }
}

enum class SlotCoverageStrength { ZERO, WEAK, ADEQUATE }

data class SlotCoverageDiagnostic(
    val slot: ProgramSlotId,
    val candidateCount: Int,
    val strongMetadataMatchCount: Int,
    val secondaryMetadataMatchCount: Int,
    val weakMetadataMatchCount: Int,
    val legacyFallbackMatchCount: Int,
    val nameFallbackMatchCount: Int,
    val coverageStrength: SlotCoverageStrength
)

class ProgramSlotCoverageDiagnostics(
    private val resolver: SlotCapabilityResolver = SlotCapabilityResolver.DEFAULT
) {
    fun analyze(
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog
    ): List<SlotCoverageDiagnostic> {
        val profiles = exercises.filter(Exercise::isActive).map { exercise ->
            resolver.resolve(exercise, catalog.resolve(exercise))
        }
        return ProgramSlotId.entries.map { slot ->
            val candidateCount = profiles.count { it.hasAny(slot) }
            val strongCount = profiles.count {
                it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.primary
            }
            SlotCoverageDiagnostic(
                slot = slot,
                candidateCount = candidateCount,
                strongMetadataMatchCount = strongCount,
                secondaryMetadataMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.secondary
                },
                weakMetadataMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.weakMatches
                },
                legacyFallbackMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.LEGACY_METADATA && it.hasAny(slot)
                },
                nameFallbackMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.NAME_FALLBACK && it.hasAny(slot)
                },
                coverageStrength = when {
                    candidateCount == 0 -> SlotCoverageStrength.ZERO
                    strongCount < MIN_ADEQUATE_STRONG_MATCHES || candidateCount < MIN_ADEQUATE_CANDIDATES ->
                        SlotCoverageStrength.WEAK
                    else -> SlotCoverageStrength.ADEQUATE
                }
            )
        }
    }

    private companion object {
        const val MIN_ADEQUATE_CANDIDATES = 4
        const val MIN_ADEQUATE_STRONG_MATCHES = 3
    }
}
