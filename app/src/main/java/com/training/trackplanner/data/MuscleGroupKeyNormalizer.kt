package com.training.trackplanner.data

import java.util.Locale

object MuscleGroupKeyNormalizer {
    fun canonicalKey(raw: String): String? {
        val token = raw.trim()
            .uppercase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_')
        if (token.isBlank()) return null
        if (token in ExerciseTaxonomy.muscles) return token
        return when {
            token.hasAny("QUAD", "대퇴사두", "대퇴근", "허벅지", "사두") -> "QUADRICEPS"
            token.hasAny("HAMSTRING", "햄스트링") -> "HAMSTRING"
            token.hasAny("GLUTE", "둔근", "엉덩") -> "GLUTE"
            token.hasAny("CALF", "CALVES", "GASTROCNEMIUS", "SOLEUS", "종아리", "카프") -> "CALF"
            token.hasAny("ADDUCTOR", "ABDUCTOR", "내전", "외전") -> "HIP_ADDUCTOR"
            token.hasAny("ERECTOR", "SPINAL", "LOW_BACK", "POSTERIOR_CHAIN", "척추", "기립", "후면사슬") ->
                "ERECTOR_SPINAE"
            token.hasAny("CHEST", "PECTORAL", "가슴", "흉근") -> "CHEST"
            token.hasAny("LAT", "광배") -> "LAT"
            token.hasAny("BACK", "등") -> "BACK"
            token.hasAny("SHOULDER", "DELT", "어깨", "삼각") -> "SHOULDER"
            token.hasAny("ROTATOR", "회전근") -> "ROTATOR_CUFF"
            token.hasAny("BICEP", "이두") -> "BICEPS"
            token.hasAny("TRICEP", "삼두") -> "TRICEPS"
            token.hasAny("FOREARM", "전완", "손목") -> "FOREARM"
            token.hasAny("GRIP", "그립") -> "GRIP"
            token.hasAny("ANTI_ROTATION", "ROTATION_CORE", "ROTATIONAL", "항회전", "회전코어") ->
                "ROTATION_CORE"
            token.hasAny("OBLIQUE", "측면코어", "사선", "회전코어") -> "OBLIQUE"
            token.hasAny("CORE", "ABS", "ABDOMINAL", "복근", "복부", "코어", "몸통") -> "CORE"
            else -> null
        }
    }

    fun canonicalKeys(raw: String): List<String> =
        raw.split(',', '|', '/', ';')
            .mapNotNull(::canonicalKey)
            .distinct()

    private fun String.hasAny(vararg fragments: String): Boolean =
        fragments.any { fragment -> contains(fragment, ignoreCase = true) }
}
