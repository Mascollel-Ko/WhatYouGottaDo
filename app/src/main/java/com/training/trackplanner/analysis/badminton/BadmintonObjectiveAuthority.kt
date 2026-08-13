package com.training.trackplanner.analysis.badminton

import java.util.Locale

enum class BadmintonObjective {
    ACCELERATION,
    DECELERATION,
    FOOTWORK,
    JUMP_LANDING,
    LUNGE_REACH,
    REACTION,
    CONDITIONING,
    ROTATION_GENERATION,
    ANTI_ROTATION;

    companion object {
        fun fromCanonicalOrAlias(value: String): BadmintonObjective = when (value.uppercase(Locale.ROOT)) {
            "ROTATION_POWER", "ROTATION", "ROTATIONAL_POWER", "ROTATION_SEQUENCING" -> ROTATION_GENERATION
            else -> valueOf(value.uppercase(Locale.ROOT))
        }
    }
}

enum class BadmintonObjectiveTransferLevel(val coefficient: Double) {
    DIRECT(1.00),
    SUPPORTIVE(0.60),
    GENERAL(0.25),
    LOW(0.10),
    NONE(0.00)
}

data class CanonicalBadmintonObjectiveRelation(
    val relationId: String,
    val exerciseStableKey: String,
    val objective: BadmintonObjective,
    val transferLevel: BadmintonObjectiveTransferLevel,
    val provenance: String,
    val evidenceRelationKeys: Set<String>,
    val reviewReason: String
)

class CanonicalBadmintonObjectiveCatalog private constructor(
    private val relationsByStableKey: Map<String, List<CanonicalBadmintonObjectiveRelation>>,
    private val historySourceByStableKey: Map<String, String>
) {
    fun relations(stableKey: String): List<CanonicalBadmintonObjectiveRelation> {
        val normalized = stableKey.normalizedKey()
        return relationsByStableKey[normalized]
            ?: historySourceByStableKey[normalized]?.let(relationsByStableKey::get)
            ?: emptyList()
    }

    fun allRelations(): List<CanonicalBadmintonObjectiveRelation> =
        relationsByStableKey.values.flatten().sortedWith(compareBy({ it.exerciseStableKey }, { it.objective.ordinal }))

    companion object {
        val EMPTY = of(emptyList())

        fun of(
            relations: Collection<CanonicalBadmintonObjectiveRelation>,
            historySourceByStableKey: Map<String, String> = emptyMap()
        ): CanonicalBadmintonObjectiveCatalog {
            require(relations.distinctBy { it.relationId }.size == relations.size) {
                "Duplicate canonical badminton objective relation ID."
            }
            require(relations.distinctBy { it.exerciseStableKey.normalizedKey() to it.objective }.size == relations.size) {
                "Each exercise may have at most one relation per badminton objective."
            }
            return CanonicalBadmintonObjectiveCatalog(
                relationsByStableKey = relations.groupBy { it.exerciseStableKey.normalizedKey() },
                historySourceByStableKey = historySourceByStableKey.mapKeys { it.key.normalizedKey() }
                    .mapValues { it.value.normalizedKey() }
            )
        }
    }
}

private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)
