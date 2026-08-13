package com.training.trackplanner.analysis.core

import java.util.Locale

enum class CoreClass(val coefficient: Double) {
    DIRECT(1.00),
    HIDDEN_HIGH(0.80),
    HIDDEN_MODERATE(0.40),
    HIDDEN_LOW(0.15),
    NONE(0.00)
}

enum class CoreDirectTarget {
    BRACING,
    ANTI_ROTATION,
    ROTATION_GENERATION,
    TRUNK_FLEXION,
    TRUNK_EXTENSION
}

data class CanonicalCoreProfile(
    val exerciseStableKey: String,
    val coreClass: CoreClass,
    val directTarget: CoreDirectTarget?
)

class CanonicalCoreCatalog private constructor(
    private val profiles: Map<String, CanonicalCoreProfile>,
    private val historySourceByStableKey: Map<String, String>
) {
    fun resolve(stableKey: String): CanonicalCoreProfile? {
        val normalized = stableKey.normalizedKey()
        return profiles[normalized]
            ?: historySourceByStableKey[normalized]?.let(profiles::get)
    }

    fun selectableProfiles(): List<CanonicalCoreProfile> = profiles.values.sortedBy { it.exerciseStableKey }

    companion object {
        val EMPTY = of(emptyList())

        fun of(
            profiles: Collection<CanonicalCoreProfile>,
            historySourceByStableKey: Map<String, String> = emptyMap()
        ): CanonicalCoreCatalog {
            val normalized = profiles.associateBy { it.exerciseStableKey.normalizedKey() }
            require(normalized.size == profiles.size) { "Duplicate canonical core profile." }
            require(normalized.values.all { profile ->
                (profile.coreClass == CoreClass.DIRECT) == (profile.directTarget != null)
            }) { "Only DIRECT core profiles may have exactly one direct target." }
            return CanonicalCoreCatalog(
                profiles = normalized,
                historySourceByStableKey = historySourceByStableKey.mapKeys { it.key.normalizedKey() }
                    .mapValues { it.value.normalizedKey() }
            )
        }
    }
}

private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)
