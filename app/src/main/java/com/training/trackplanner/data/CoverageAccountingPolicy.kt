package com.training.trackplanner.data

enum class CoverageCredit(val value: Double) {
    FULL(1.0),
    PARTIAL(0.5),
    WEAK(0.25),
    NONE(0.0)
}

class CoverageAccountingPolicy {
    fun credit(profile: SlotCapabilityProfile, slot: ProgramSlotId): CoverageCredit = when {
        slot in profile.primary && ProgramSlotDefinitions.get(slot).isUmbrella -> CoverageCredit.PARTIAL
        slot in profile.primary -> CoverageCredit.FULL
        slot in profile.secondary -> CoverageCredit.PARTIAL
        slot in profile.weakMatches -> CoverageCredit.WEAK
        else -> CoverageCredit.NONE
    }

    fun creditedSlots(profile: SlotCapabilityProfile): Map<ProgramSlotId, CoverageCredit> =
        ProgramSlotId.entries.mapNotNull { slot ->
            credit(profile, slot).takeIf { it != CoverageCredit.NONE }?.let { slot to it }
        }.toMap()

    fun profile(item: ProgramSkeletonItem): SlotCapabilityProfile = SlotCapabilityProfile(
        primary = item.primarySlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        secondary = item.secondarySlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        weakMatches = item.weakSlotCapabilities.mapNotNull(::slotIdOrNull).toSet(),
        source = item.slotCapabilitySource
            .let { source -> SlotCapabilitySource.entries.firstOrNull { it.name == source } }
            ?: SlotCapabilitySource.NONE
    )

    private fun slotIdOrNull(value: String): ProgramSlotId? =
        ProgramSlotId.entries.firstOrNull { it.name == value }

    companion object {
        val DEFAULT = CoverageAccountingPolicy()
    }
}

data class SlotCoverageDiagnostic(
    val slot: ProgramSlotId,
    val candidateCount: Int,
    val strongMetadataMatchCount: Int,
    val legacyFallbackMatchCount: Int,
    val weakMatchCount: Int
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
            SlotCoverageDiagnostic(
                slot = slot,
                candidateCount = profiles.count { it.hasAny(slot) },
                strongMetadataMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.primary
                },
                legacyFallbackMatchCount = profiles.count {
                    it.source == SlotCapabilitySource.LEGACY_METADATA && it.hasAny(slot)
                },
                weakMatchCount = profiles.count { slot in it.weakMatches }
            )
        }
    }
}
