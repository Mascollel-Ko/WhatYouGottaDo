package com.training.trackplanner.analysis.tissue

object TissueModifierResolver {
    fun resolve(
        baseWeight: Double,
        stableKey: String,
        movementFamily: String,
        tissueId: String,
        loadDimension: TissueLoadDimension,
        inputs: Map<TissueModifierFamily, String>,
        rules: List<TissueModifierRule>,
        allowNonProductionFixtures: Boolean = false
    ): TissueModifierResolution {
        require(baseWeight.isFinite() && baseWeight >= 0.0) { "Base tissue weight must be finite and non-negative." }
        val relevant = rules.filter { rule ->
            rule.tissueId == tissueId &&
                rule.loadDimension == loadDimension &&
                when (rule.specificityLevel) {
                    TissueModifierSpecificity.EXACT_STABLE_KEY,
                    TissueModifierSpecificity.EXACT_VARIANT -> rule.stableKey == stableKey
                    TissueModifierSpecificity.MOVEMENT_FAMILY -> rule.stableKey == movementFamily
                    TissueModifierSpecificity.GLOBAL_TISSUE_RULE -> true
                }
        }
        val missing = relevant.firstOrNull { rule ->
            inputs[rule.modifierFamily] == null &&
                rule.missingInputBehavior != TissueMissingInputBehavior.USE_REFERENCE_CONDITION
        }
        if (missing != null) return TissueModifierResolution(
            adjustedWeight = null,
            calculationStatus = TissueCalculationStatus.MISSING_RECORD_INPUT,
            skippedModifierIds = listOf(missing.modifierRuleId),
            diagnostics = listOf("${missing.modifierRuleId}: required modifier input is missing.")
        )

        val matching = relevant.filter { rule -> inputs[rule.modifierFamily] == rule.inputCondition }
        if (!allowNonProductionFixtures && matching.any { it.humanApprovedBy.isBlank() }) {
            return TissueModifierResolution(
                adjustedWeight = null,
                calculationStatus = TissueCalculationStatus.EVIDENCE_NOT_APPROVED,
                skippedModifierIds = matching.map(TissueModifierRule::modifierRuleId).sorted(),
                diagnostics = listOf("Matching modifier rules lack human approval.")
            )
        }
        val sorted = matching.sortedWith(
            compareByDescending<TissueModifierRule> { it.specificityLevel.ordinal }
                .thenByDescending(TissueModifierRule::precedence)
                .thenBy(TissueModifierRule::modifierRuleId)
        )
        val selected = sorted.fold(mutableListOf<TissueModifierRule>()) { kept, rule ->
            if (rule.exclusiveGroup.isBlank() || kept.none { it.exclusiveGroup == rule.exclusiveGroup }) kept += rule
            kept
        }
        val unsupportedInteraction = selected.groupBy(TissueModifierRule::interactionGroup)
            .filterKeys(String::isNotBlank)
            .values.firstOrNull { it.size > 1 }
        if (unsupportedInteraction != null) return TissueModifierResolution(
            adjustedWeight = null,
            calculationStatus = TissueCalculationStatus.UNSUPPORTED_MODIFIER_COMBINATION,
            skippedModifierIds = unsupportedInteraction.map(TissueModifierRule::modifierRuleId).sorted(),
            diagnostics = listOf("Explicit interaction rule is required before composing this modifier group.")
        )
        if (selected.any { it.operation == TissueModifierOperation.BLOCK_IF_PRESENT }) {
            return TissueModifierResolution(
                adjustedWeight = null,
                calculationStatus = TissueCalculationStatus.UNSUPPORTED_MODIFIER_COMBINATION,
                skippedModifierIds = selected.filter { it.operation == TissueModifierOperation.BLOCK_IF_PRESENT }
                    .map(TissueModifierRule::modifierRuleId).sorted(),
                diagnostics = listOf("A matching modifier rule blocks this combination.")
            )
        }

        var adjusted = selected.filter { it.operation == TissueModifierOperation.REPLACE_WEIGHT }
            .maxByOrNull(TissueModifierRule::precedence)?.factor ?: baseWeight
        selected.filter { it.operation == TissueModifierOperation.MULTIPLY_WEIGHT }
            .forEach { adjusted *= requireNotNull(it.factor) }
        val minimum = selected.mapNotNull(TissueModifierRule::minimumCombinedFactor).maxOrNull()
        val maximum = selected.mapNotNull(TissueModifierRule::maximumCombinedFactor).minOrNull()
        val unclamped = adjusted
        if (minimum != null) adjusted = adjusted.coerceAtLeast(baseWeight * minimum)
        if (maximum != null) adjusted = adjusted.coerceAtMost(baseWeight * maximum)
        return TissueModifierResolution(
            adjustedWeight = adjusted,
            calculationStatus = TissueCalculationStatus.CALCULABLE,
            appliedModifierIds = selected.map(TissueModifierRule::modifierRuleId),
            skippedModifierIds = relevant.map(TissueModifierRule::modifierRuleId)
                .filterNot { id -> selected.any { it.modifierRuleId == id } }.sorted(),
            diagnostics = if (adjusted != unclamped) listOf("Combined modifier factor was clamped to approved bounds.")
            else emptyList()
        )
    }
}
