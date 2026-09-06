package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.time.temporal.TemporalAccessor

internal fun separationFingerprint(skeleton: GeneratedProgramSkeleton): String {
    val normalized = skeleton.copy(personalizedDecision = skeleton.personalizedDecision?.copy(decisionId = "AUDIT_ID", generatedAtEpochMillis = 0))
    return MessageDigest.getInstance("SHA-256").digest(separationCanonical(normalized).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
private fun separationCanonical(value: Any?): String = when (value) {
    null -> "null"
    is String -> "${value.length}:$value"
    is Number, is Boolean, is TemporalAccessor -> value.toString()
    is Enum<*> -> value.name
    is Map<*, *> -> value.entries.sortedBy { separationCanonical(it.key) }
        .joinToString(prefix = "{", postfix = "}") { separationCanonical(it.key) + "=" + separationCanonical(it.value) }
    is Set<*> -> value.map(::separationCanonical).sorted().joinToString(prefix = "<", postfix = ">")
    is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { separationCanonical(it) }
    else -> value.javaClass.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
        .sortedBy { it.name }.joinToString(prefix = "(", postfix = ")") {
            it.isAccessible = true
            it.name + "=" + separationCanonical(it.get(value))
        }
}
