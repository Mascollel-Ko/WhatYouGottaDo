package com.training.trackplanner.analysis.badminton

internal object BadmintonTransferColorPalette {
    private val colorsByKey = mapOf(
        "ACCELERATION" to 0xFFE15759L,
        "DECELERATION" to 0xFFF28E2BL,
        "FOOTWORK" to 0xFFB7791FL,
        "JUMP_LANDING" to 0xFF59A14FL,
        "LUNGE_REACH" to 0xFF2A9D8FL,
        "REACTION" to 0xFF4E79A7L,
        "CONDITIONING" to 0xFF6D4C41L,
        "ANTI_ROTATION" to 0xFF5E60CEL,
        "ROTATION_POWER" to 0xFFB07AA1L,
        "DECELERATION_LANDING" to 0xFFF28E2BL,
        "UNILATERAL_STABILITY" to 0xFF2A9D8FL,
        "LATERAL_MOVEMENT" to 0xFFB7791FL,
        "ROTATION_CONTROL" to 0xFF5E60CEL,
        "RACKET_SUPPORT" to 0xFF4E79A7L,
        "AEROBIC_FOOTWORK" to 0xFF59A14FL,
        "LOW_FATIGUE_CONTROL" to 0xFF6D4C41L,
        "DIRECT" to 0xFF4E79A7L,
        "SUPPORTIVE" to 0xFF2A9D8FL,
        "GENERAL_STRENGTH" to 0xFFB7791FL,
        "LOW" to 0xFF6D4C41L
    )

    private val aliases = mapOf(
        "STEP" to "FOOTWORK",
        "ROTATION" to "ROTATION_POWER",
        "ROTATION_GENERATION" to "ROTATION_POWER",
        "ANTI_ROTATION_STABILITY" to "ANTI_ROTATION"
    )

    fun colorForKey(key: String): Long {
        val normalized = key.trim().uppercase()
        val canonical = aliases[normalized] ?: normalized
        return colorsByKey[canonical] ?: fallbackColor(canonical)
    }

    private fun fallbackColor(key: String): Long {
        val fallback = listOf(
            0xFF8D6E63L,
            0xFF546E7AL,
            0xFF6A1B9AL,
            0xFF00897BL,
            0xFFC2185BL
        )
        return fallback[(key.hashCode() and Int.MAX_VALUE) % fallback.size]
    }
}
