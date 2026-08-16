package com.training.trackplanner.analysis.badminton

object BadmintonPracticeCatalog {
    const val BADMINTON_STABLE_KEY = "ex_ae9ecdbc"
    const val BADMINTON_LESSON_STABLE_KEY = "ex_badminton_lesson"

    val stableKeys: Set<String> = setOf(BADMINTON_STABLE_KEY, BADMINTON_LESSON_STABLE_KEY)

    fun admits(stableKey: String, resolvedActivityKind: String): Boolean =
        stableKey in stableKeys && resolvedActivityKind == "SPORT_SESSION"
}
