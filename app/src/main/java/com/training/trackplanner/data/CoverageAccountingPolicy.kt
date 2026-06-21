package com.training.trackplanner.data

enum class CoverageCredit(val value: Double) {
    FULL(1.0),
    PARTIAL(0.5),
    WEAK(0.25),
    NONE(0.0)
}

data class DerivedUmbrellaCoverage(
    val umbrellaSlot: ProgramSlotId,
    val representedComponents: Set<ProgramSlotId>,
    val contributorCount: Int,
    val requiredComponentCount: Int,
    val requiredContributorCount: Int
) {
    val satisfied: Boolean =
        representedComponents.size >= requiredComponentCount && contributorCount >= requiredContributorCount
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
        val definition = ProgramSlotDefinitions.get(umbrellaSlot)
        if (definition.derivedFrom.isEmpty()) return credit(profile, umbrellaSlot)
        val componentHits = definition.derivedFrom.count { slot ->
            credit(profile, slot).value >= CoverageCredit.PARTIAL.value
        }
        return when {
            componentHits >= 2 -> CoverageCredit.PARTIAL
            componentHits == 1 -> CoverageCredit.WEAK
            else -> CoverageCredit.NONE
        }
    }

    fun derivedUmbrellaCoverage(
        profiles: Iterable<SlotCapabilityProfile>,
        umbrellaSlot: ProgramSlotId
    ): DerivedUmbrellaCoverage = derivedUmbrellaCoverageByContributor(
        contributors = profiles.mapIndexed { index, profile -> "profile-$index" to profile },
        umbrellaSlot = umbrellaSlot
    )

    fun derivedUmbrellaCoverageByContributor(
        contributors: Iterable<Pair<String, SlotCapabilityProfile>>,
        umbrellaSlot: ProgramSlotId
    ): DerivedUmbrellaCoverage {
        val definition = ProgramSlotDefinitions.get(umbrellaSlot)
        require(definition.derivedFrom.isNotEmpty()) { "$umbrellaSlot is not a derived umbrella slot" }
        val componentHitsByContributor = contributors.map { (contributorKey, profile) ->
            contributorKey to definition.derivedFrom.filterTo(mutableSetOf()) { component ->
                credit(profile, component).value >= CoverageCredit.PARTIAL.value
            }
        }.filter { (_, hits) -> hits.isNotEmpty() }
        return DerivedUmbrellaCoverage(
            umbrellaSlot = umbrellaSlot,
            representedComponents = componentHitsByContributor.flatMap { it.second }.toSet(),
            contributorCount = componentHitsByContributor.map { it.first }.distinct().size,
            requiredComponentCount = definition.minimumDerivedComponents,
            requiredContributorCount = definition.minimumDerivedContributors
        )
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

    companion object {
        val DEFAULT = CoverageAccountingPolicy()
        const val MAX_CREDIT_PER_EXERCISE = 2.0
    }
}

enum class SlotCoverageStrength { ZERO, WEAK, ADEQUATE }

enum class SlotCoverageMode { DIRECT, DERIVED_UMBRELLA }

data class SlotCoverageDiagnostic(
    val slot: ProgramSlotId,
    val candidateCount: Int,
    val strongMetadataMatchCount: Int,
    val secondaryMetadataMatchCount: Int,
    val weakMetadataMatchCount: Int,
    val legacyFallbackMatchCount: Int,
    val nameFallbackMatchCount: Int,
    val coverageMode: SlotCoverageMode,
    val derivedComponentSlots: Set<ProgramSlotId>,
    val representedDerivedComponents: Set<ProgramSlotId>,
    val derivedContributorCount: Int,
    val coverageStrength: SlotCoverageStrength
)

class ProgramSlotCoverageDiagnostics(
    private val resolver: SlotCapabilityResolver = SlotCapabilityResolver.DEFAULT,
    private val coveragePolicy: CoverageAccountingPolicy = CoverageAccountingPolicy.DEFAULT
) {
    fun analyze(
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog
    ): List<SlotCoverageDiagnostic> {
        val profiles = exercises.filter(Exercise::isActive).map { exercise ->
            resolver.resolve(exercise, catalog.resolve(exercise))
        }
        return ProgramSlotId.entries.map { slot ->
            val definition = ProgramSlotDefinitions.get(slot)
            val candidateCount = profiles.count { it.hasAny(slot) }
            val strongCount = profiles.count {
                it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.primary
            }
            val secondaryCount = profiles.count {
                it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.secondary
            }
            val weakCount = profiles.count {
                it.source == SlotCapabilitySource.RUNTIME_METADATA && slot in it.weakMatches
            }
            val legacyCount = profiles.count {
                it.source == SlotCapabilitySource.LEGACY_METADATA && it.hasAny(slot)
            }
            val nameCount = profiles.count {
                it.source == SlotCapabilitySource.NAME_FALLBACK && it.hasAny(slot)
            }
            val derived = definition.derivedFrom.takeIf(Set<ProgramSlotId>::isNotEmpty)?.let {
                coveragePolicy.derivedUmbrellaCoverage(profiles, slot)
            }
            val runtimeMetadataCount = strongCount + secondaryCount + weakCount
            SlotCoverageDiagnostic(
                slot = slot,
                candidateCount = candidateCount,
                strongMetadataMatchCount = strongCount,
                secondaryMetadataMatchCount = secondaryCount,
                weakMetadataMatchCount = weakCount,
                legacyFallbackMatchCount = legacyCount,
                nameFallbackMatchCount = nameCount,
                coverageMode = if (derived == null) SlotCoverageMode.DIRECT else SlotCoverageMode.DERIVED_UMBRELLA,
                derivedComponentSlots = definition.derivedFrom,
                representedDerivedComponents = derived?.representedComponents.orEmpty(),
                derivedContributorCount = derived?.contributorCount ?: 0,
                coverageStrength = when {
                    derived?.satisfied == true -> SlotCoverageStrength.ADEQUATE
                    derived != null && derived.representedComponents.isNotEmpty() -> SlotCoverageStrength.WEAK
                    derived != null -> SlotCoverageStrength.ZERO
                    candidateCount == 0 -> SlotCoverageStrength.ZERO
                    definition.isRecoveryOrPrehab &&
                        runtimeMetadataCount >= MIN_ADEQUATE_CANDIDATES &&
                        legacyCount == 0 && nameCount == 0 -> SlotCoverageStrength.ADEQUATE
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
