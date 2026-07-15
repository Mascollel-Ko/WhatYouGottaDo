package com.training.trackplanner.analysis.tissue

object TissueMtcValidator {
    fun foundation(
        complexes: List<TissueFunctionalComplex>,
        rules: List<TissueMtcAxisMetricRule>,
        stabilizationProfiles: List<TissueDynamicStabilizationProfile>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (complexes.size != 9 || complexes.map { it.complexId }.distinct().size != complexes.size) errors += "Functional-complex registry must contain nine unique complexes."
        complexes.forEach { complex ->
            if (complex.componentIds.isEmpty()) errors += "${complex.complexId}: component set is empty."
            if (complex.outputPolicy != "SEPARATE_WHEN_DIRECT_EVIDENCE") errors += "${complex.complexId}: child outputs must remain separate."
        }
        if (rules.map { it.axisMetricRuleId }.distinct().size != rules.size) errors += "Duplicate M/T/C axis metric rule."
        rules.groupBy { it.targetType to it.targetId }.forEach { (target, targetRules) ->
            if (targetRules.map { it.axis }.toSet() != TissueMtcAxis.entries.toSet()) errors += "$target: M/T/C coverage is incomplete."
        }
        rules.forEach { rule ->
            if (rule.primaryMetricTypes.isEmpty() || rule.primaryMetricTypes.any { !it.startsWith("${rule.axis.name}_") }) errors += "${rule.axisMetricRuleId}: primary metrics do not match axis ${rule.axis}."
            if (rule.allowedMeasurementFamilies.isEmpty() || rule.allowedNormalizationBases.isEmpty()) errors += "${rule.axisMetricRuleId}: compatibility is not explicit."
            if (rule.axis == TissueMtcAxis.C && rule.requiredContextFields.isEmpty()) errors += "${rule.axisMetricRuleId}: mechanical context requires structured fields."
        }
        if (stabilizationProfiles.size != 3 || stabilizationProfiles.any { !it.separateFromMechanicalLoad }) errors += "Hamstring, peroneal, and posterior-tibial stabilization must remain separate from tissue mechanical load."
        val profileIds = stabilizationProfiles.map { it.profileId }.toSet()
        if (rules.filter { it.targetType == TissueMtcTargetType.DYNAMIC_STABILIZATION }.map { it.targetId }.toSet() != profileIds) errors += "Dynamic-stabilization profiles and axis rules disagree."
        return TissueValidationReport(errors)
    }
}
