package com.training.trackplanner.data.personalized

import org.json.JSONObject

/** Engineering product priority: one recent-session unit, not a physiological coefficient. */
internal const val DIRECT_STRENGTH_ANCHOR_BONUS = 2.0

enum class IncumbentCutReason { BASE_SELECTED, MOVEMENT_ANCHOR_CUTOFF, GLOBAL_ANCHOR_CUTOFF }

data class EligibleIncumbentCandidate(
    val anchor: UserAnchor,
    val responseBonus: Double,
    val baseScore: Double,
    val directAnchorBonus: Double,
    val movementRank: Int,
    val globalPreCutRank: Int,
    val cutReason: IncumbentCutReason
) {
    val baseSelected: Boolean get() = cutReason == IncumbentCutReason.BASE_SELECTED
    val selectedByMovementCut: Boolean get() = movementRank <= 2
    val selectedByGlobalCut: Boolean get() = baseSelected
    fun toJson(expansionEligible: Boolean = false, expansionSelected: Boolean = false) = JSONObject()
        .put("stableKey", anchor.stableKey).put("exerciseName", anchor.exerciseName).put("movementGroup", anchor.movementGroup)
        .put("sessionCount", anchor.sessions).put("setCount", anchor.sets).put("response", anchor.response)
        .put("responseBonus", responseBonus).put("baseScore", baseScore).put("directAnchorBonus", directAnchorBonus)
        .put("finalScore", anchor.score).put("movementRank", movementRank).put("globalPreCutRank", globalPreCutRank)
        .put("selectedByMovementCut", selectedByMovementCut).put("selectedByGlobalCut", selectedByGlobalCut)
        .put("baseSelected", baseSelected).put("cutReason", cutReason.name)
        .put("style", anchor.style.name).put("styleConfidence", anchor.styleConfidence.name)
        .put("canonicalPerformanceSource", anchor.canonicalPerformanceSource)
        .put("posteriorChangePercent", anchor.posteriorChangePercent).put("posteriorObservationCount", anchor.posteriorObservationCount)
        .put("expansionEligible", expansionEligible).put("expansionSelected", expansionSelected)
}

internal fun rankEligibleIncumbents(anchors: List<UserAnchor>, directKeys: Set<String>): List<EligibleIncumbentCandidate> {
    val ranked = anchors.map { it.copy(score = it.score + if (it.stableKey in directKeys) DIRECT_STRENGTH_ANCHOR_BONUS else 0.0) }
        .sortedWith(compareByDescending<UserAnchor> { it.score }.thenBy(UserAnchor::stableKey))
    val movementCounts = mutableMapOf<String, Int>()
    var movementWinners = 0
    return ranked.mapIndexed { index, anchor ->
        val movementRank = (movementCounts[anchor.movementGroup] ?: 0) + 1
        movementCounts[anchor.movementGroup] = movementRank
        if (movementRank <= 2) movementWinners++
        val bonus = if (anchor.stableKey in directKeys) DIRECT_STRENGTH_ANCHOR_BONUS else 0.0
        val base = anchor.score - bonus
        val responseBonus = when (anchor.response) { "STRONG_POSITIVE" -> 4.0; "POSITIVE" -> 3.0; "NEGATIVE" -> -3.0; else -> 1.0 }
        EligibleIncumbentCandidate(anchor, responseBonus, base, bonus,
            movementRank, index + 1, when {
                movementRank > 2 -> IncumbentCutReason.MOVEMENT_ANCHOR_CUTOFF
                movementWinners > 9 -> IncumbentCutReason.GLOBAL_ANCHOR_CUTOFF
                else -> IncumbentCutReason.BASE_SELECTED
            })
    }
}
