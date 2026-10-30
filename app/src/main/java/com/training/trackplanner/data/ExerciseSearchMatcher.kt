package com.training.trackplanner.data

import java.util.Locale

/**
 * Searches literal characters, not edit distance or character permutations.
 * Whitespace is ignored for a contiguous match; otherwise all whitespace-
 * delimited terms must occur somewhere in the candidate, in any order.
 */
object ExerciseSearchMatcher {
    private fun compact(value: String): String =
        value.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)

    fun matches(candidate: String, query: String): Boolean {
        val normalized = compact(query)
        if (normalized.isEmpty()) return true
        val target = compact(candidate)
        if (target.contains(normalized)) return true
        val terms = query.trim().split(Regex("\\s+")).map(::compact).filter(String::isNotEmpty)
        return terms.isNotEmpty() && terms.all(target::contains)
    }

    /** AND across terms and OR across approved searchable fields. */
    fun matchesAny(candidates: Collection<String>, query: String): Boolean {
        if (compact(query).isEmpty()) return true
        val terms = query.trim().split(Regex("\\s+")).map(::compact).filter(String::isNotEmpty)
        val targets = candidates.map(::compact)
        return targets.any { it.contains(compact(query)) } ||
            terms.isNotEmpty() && terms.all { term -> targets.any { it.contains(term) } }
    }
}
