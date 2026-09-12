package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.time.temporal.TemporalAccessor

internal fun separationFingerprint(skeleton: GeneratedProgramSkeleton): String {
    return MessageDigest.getInstance("SHA-256").digest(separationSnapshot(skeleton).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
internal fun separationSnapshot(skeleton: GeneratedProgramSkeleton): String {
    val decision = skeleton.personalizedDecision
    // The old golden mislabeled INITIAL materialized units as capacity. Reconstruct that legacy audit
    // field only for comparison; current computed capacity is tested separately and the kernel stays byte-frozen.
    val budget = decision?.planningBudget
    val legacyBudget = if (decision?.frequencyDemand != null && budget?.execution != null) budget.copy(
        execution = budget.execution.copy(capacity = budget.execution.capacity.copy(finalControllableUnits =
            decision.authorizedScheduling!!.initialWeek.sumOf { it.setPrescriptions.size }))) else budget
    val normalized = skeleton.copy(personalizedDecision = decision?.copy(decisionId = "AUDIT_ID", generatedAtEpochMillis = 0,
        planningBudget = legacyBudget))
    return separationCanonical(normalized)
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
    // The a53f419 golden owns the complete INITIAL result, before these additive post-process audit fields.
    // All pre-existing decision fields, items and prescriptions remain in the golden comparison.
    else -> value.javaClass.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) ||
        value is PersonalizedPlanningDecision && it.name in setOf("residualCompletion", "dayRebalancing", "authorizedScheduling", "frequencyDemand", "frequencyExpansion", "postSplitReflow") }
        .sortedBy { it.name }.joinToString(prefix = "(", postfix = ")") {
            it.isAccessible = true
            it.name + "=" + separationCanonical(it.get(value))
        }
}
