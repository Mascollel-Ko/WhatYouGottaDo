package com.training.trackplanner.data

object ExerciseTaxonomy {
    val movementPatterns = setOf(
        "HORIZONTAL_PUSH",
        "VERTICAL_PUSH",
        "HORIZONTAL_PULL",
        "VERTICAL_PULL",
        "KNEE_DOMINANT_LOWER",
        "HINGE_LOWER",
        "SINGLE_LEG_STRENGTH",
        "SINGLE_LEG_DECEL",
        "ROTATION_CORE",
        "ANTI_ROTATION_CORE",
        "CARRY",
        "CARDIO",
        "SPORT_SKILL",
        "SHOULDER_ABDUCTION",
        "SHOULDER_HORIZONTAL_ABDUCTION",
        "SHOULDER_EXTERNAL_ROTATION",
        "ELBOW_EXTENSION",
        "ELBOW_FLEXION",
        "WRIST_GRIP",
        "MOBILITY"
    )

    val movementCategories = setOf(
        "STRENGTH",
        "FUNCTIONAL",
        "CONDITIONING",
        "SPORT",
        "MOBILITY",
        "GENERAL"
    )

    val muscles = setOf(
        "CHEST",
        "UPPER_CHEST",
        "BACK",
        "LAT",
        "RHOMBOID",
        "TRAPEZIUS",
        "LOWER_TRAP",
        "SHOULDER",
        "ANTERIOR_DELTOID",
        "LATERAL_DELTOID",
        "REAR_DELT",
        "ROTATOR_CUFF",
        "SCAPULAR_STABILIZERS",
        "BICEPS",
        "TRICEPS",
        "FOREARM",
        "GRIP",
        "QUADRICEPS",
        "RECTUS_FEMORIS",
        "HAMSTRING",
        "GLUTE",
        "GLUTE_MEDIUS",
        "HIP_ADDUCTOR",
        "CALF",
        "TIBIALIS",
        "CORE",
        "DEEP_CORE",
        "OBLIQUE",
        "ERECTOR_SPINAE"
    )

    val equipment = setOf(
        "BARBELL",
        "DUMBBELL",
        "CABLE",
        "MACHINE",
        "SMITH_MACHINE",
        "KETTLEBELL",
        "LANDMINE",
        "WEIGHT_PLATE",
        "BAND",
        "BENCH",
        "BOX",
        "PULLUP_BAR",
        "LEG_PRESS_MACHINE",
        "HACK_SQUAT_MACHINE",
        "LEG_CURL_MACHINE",
        "LEG_EXTENSION_MACHINE",
        "HIP_ABDUCTION_MACHINE",
        "TREADMILL",
        "BIKE",
        "ROWER",
        "CONE_MARKER",
        "MEDICINE_BALL",
        "VIPR",
        "TRX_RING",
        "BODYWEIGHT"
    )

    val forceTypes = setOf(
        "PUSH",
        "PULL",
        "LOWER_BODY",
        "MIXED",
        "STRENGTH",
        "HYPERTROPHY",
        "POWER",
        "PLYOMETRIC",
        "DECELERATION_DIRECT",
        "ANTI_ROTATION",
        "MOTOR_CONTROL",
        "LOW_LOAD"
    )

    val bodyRegions = setOf("UPPER", "LOWER", "TRUNK", "WHOLE_BODY")

    val lateralities = setOf(
        "BILATERAL",
        "BILATERAL_OR_GENERAL",
        "UNILATERAL",
        "UNILATERAL_UPPER",
        "UNILATERAL_LOWER",
        "UNILATERAL_ALTERNATING",
        "CONTRALATERAL"
    )

    val trainingRoles = setOf(
        "STRENGTH",
        "HYPERTROPHY",
        "POWER",
        "PLYOMETRIC",
        "STABILITY",
        "MOBILITY",
        "PREHAB",
        "SKILL_DRILL",
        "CONDITIONING",
        "TEST",
        "RECOVERY"
    )

    val stabilityRoles = setOf(
        "CORE_STABILITY",
        "ANTI_ROTATION",
        "LUMBOPELVIC_CONTROL",
        "TRUNK_CONTROL",
        "CONTRALATERAL_COORDINATION",
        "HIP_STABILITY",
        "PELVIC_STABILITY",
        "KNEE_CONTROL",
        "ANKLE_STABILITY",
        "SCAPULAR_STABILITY",
        "ROTATOR_CUFF_CONTROL",
        "LANDING_STABILITY",
        "SINGLE_LEG_STABILITY"
    )

    val sportTransferDirect = setOf(
        "DECELERATION",
        "LANDING_CONTROL",
        "CHANGE_OF_DIRECTION",
        "BADMINTON_FOOTWORK",
        "LATERAL_MOVEMENT",
        "REACTION",
        "JUMP_POWER",
        "SMASH_POWER",
        "GRIP_ENDURANCE",
        "COURT_CONDITIONING"
    )

    val sportTransferSupportive = setOf(
        "DECELERATION_SUPPORT",
        "LANDING_SUPPORT",
        "COD_SUPPORT",
        "BADMINTON_SUPPORT_LIGHT",
        "HIP_STABILITY_SUPPORT",
        "TRUNK_CONTROL_SUPPORT",
        "SHOULDER_STABILITY_SUPPORT",
        "ROTATOR_CUFF_SUPPORT",
        "GRIP_FOREARM_SUPPORT",
        "ANKLE_SSC_SUPPORT",
        "POSTERIOR_CHAIN_CAPACITY",
        "SINGLE_LEG_STRENGTH_SUPPORT"
    )

    val accessoryRoles = setOf(
        "SHOULDER_ACCESSORY",
        "SHOULDER_ISOLATION",
        "ARM_ACCESSORY",
        "BICEPS_ACCESSORY",
        "TRICEPS_ACCESSORY",
        "TRICEPS_ISOLATION",
        "CHEST_ACCESSORY",
        "BACK_ACCESSORY",
        "LEG_ACCESSORY",
        "POSTERIOR_CHAIN_ACCESSORY",
        "HYPERTROPHY_ACCESSORY"
    )

    val loadProfiles = setOf(
        "LOW_LOAD",
        "MODERATE_LOAD",
        "HIGH_LOAD",
        "HIGH_AXIAL_LOAD",
        "LOW_AXIAL_LOAD",
        "SHOULDER_STRESS_HIGH",
        "LUMBAR_STRESS_HIGH",
        "PLYOMETRIC_JUMP",
        "SHOULDER_STRESS_LOW",
        "SINGLE_LEG_BALANCE_DEMAND"
    )

    val metadataConfidence = setOf(
        "USER_CONFIRMED",
        "SEED_VERIFIED",
        "SEED_INFERRED",
        "FALLBACK_HEURISTIC",
        "UNKNOWN",
        "MANUAL_HIGH",
        "SEED_REVIEWED",
        "PROFILE_INFERRED"
    )

    fun single(value: String, allowed: Set<String>, field: String): String {
        if (value.isBlank()) return ""
        require(value in allowed) { "Invalid $field token: $value" }
        return value
    }

    fun list(value: String, allowed: Set<String>, field: String): String {
        tokens(value).forEach { token ->
            require(token in allowed) { "Invalid $field token: $token" }
        }
        return value
    }

    fun canonicalMuscleToken(raw: String): String? {
        val token = raw.trim()
            .uppercase()
            .replace('-', '_')
            .replace(' ', '_')
        if (token.isBlank()) return null
        if (token in muscles) return token
        return when {
            token.hasAny("QUAD", "대퇴사두", "대퇴근", "허벅지", "사두") -> "QUADRICEPS"
            token.hasAny("HAMSTRING", "햄스트링") -> "HAMSTRING"
            token.hasAny("GLUTE", "둔근", "엉덩") -> "GLUTE"
            token.hasAny("CALF", "CALVES", "GASTROCNEMIUS", "SOLEUS", "종아리", "카프") -> "CALF"
            token.hasAny("ADDUCTOR", "ABDUCTOR", "내전", "외전") -> "HIP_ADDUCTOR"
            token.hasAny("ERECTOR", "SPINAL", "LOW_BACK", "POSTERIOR_CHAIN", "척추", "기립", "후면사슬") -> "ERECTOR_SPINAE"
            token.hasAny("CHEST", "PECTORAL", "가슴", "흉근") -> "CHEST"
            token.hasAny("LAT", "광배") -> "LAT"
            token.hasAny("BACK", "등") -> "BACK"
            token.hasAny("SHOULDER", "DELT", "어깨", "삼각") -> "SHOULDER"
            token.hasAny("ROTATOR", "회전근") -> "ROTATOR_CUFF"
            token.hasAny("BICEP", "이두") -> "BICEPS"
            token.hasAny("TRICEP", "삼두") -> "TRICEPS"
            token.hasAny("FOREARM", "전완", "손목") -> "FOREARM"
            token.hasAny("GRIP", "그립") -> "GRIP"
            token.hasAny("ANTI_ROTATION", "ROTATION_CORE", "ROTATIONAL", "항회전", "회전코어") -> "ROTATION_CORE"
            token.hasAny("OBLIQUE", "측면코어", "사선", "회전코어") -> "OBLIQUE"
            token.hasAny("CORE", "ABS", "ABDOMINAL", "복근", "복부", "코어", "몸통") -> "CORE"
            else -> null
        }
    }

    private fun tokens(value: String): List<String> =
        value.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun String.hasAny(vararg fragments: String): Boolean =
        fragments.any { fragment -> contains(fragment, ignoreCase = true) }
}
