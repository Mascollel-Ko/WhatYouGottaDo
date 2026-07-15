package com.training.trackplanner.analysis.tissue

object TissueMtcValidator {
    fun rubricFoundation(
        scales: List<TissueMtcAxisScale>,
        rubrics: List<TissueMtcRubric>,
        provenance: List<TissueMtcAxisProvenance>,
        fallbacks: List<TissueMtcFallbackRule>,
        coefficientSets: List<TissueMtcCoefficientSet>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (scales.map { it.axisScaleId }.distinct().size != scales.size) errors += "Duplicate axis scale."
        if (scales.map { listOf(it.targetId, it.axis, it.physicalMetricType, it.normalizationBasis, it.measurementFamily, it.comparisonFamily, it.populationScope, it.conditionFamily) }.distinct().size != scales.size) errors += "Duplicate axis-scale identity."
        val scaleIds = scales.map { it.axisScaleId }.toSet()
        rubrics.forEach { rubric ->
            if (rubric.axisScaleId !in scaleIds) errors += "${rubric.rubricId}: unknown axis scale."
            rubric.score?.let { score ->
                if (score !in 0.0..4.0 || score * 2 % 1.0 != 0.0) errors += "${rubric.rubricId}: score must be 0..4 in supported 0.5 increments."
                if (score == 0.0 && rubric.rubricKind != TissueMtcRubricKind.ABSOLUTE_INTERVAL) errors += "${rubric.rubricId}: UNKNOWN or fallback cannot become zero."
            }
            when (rubric.rubricKind) {
                TissueMtcRubricKind.ABSOLUTE_INTERVAL -> {
                    val calibrated = rubric.independentSourceCount >= 2 ||
                        (rubric.distinctConditionCount >= 12 && rubric.externalValidationSourceIds.isNotEmpty())
                    if (!calibrated || rubric.lowerBound == null || rubric.upperBound == null ||
                        rubric.lowerInclusive == null || rubric.upperInclusive == null || rubric.boundaryDerivation.isBlank() ||
                        rubric.sensitivityAnalysisStatus != "PASSED") errors += "${rubric.rubricId}: absolute-interval calibration gate failed."
                }
                TissueMtcRubricKind.CONDITION_ANCHOR -> if (rubric.anchorValue == null || rubric.lowerBound != null || rubric.upperBound != null || rubric.sourceConditionIds.isEmpty()) errors += "${rubric.rubricId}: invalid condition anchor."
                TissueMtcRubricKind.ORDERING_RULE -> if (rubric.score != null || rubric.lowerBound != null || rubric.upperBound != null) errors += "${rubric.rubricId}: ordering rules cannot create numeric thresholds."
                TissueMtcRubricKind.FAMILY_DEFAULT, TissueMtcRubricKind.CONSERVATIVE_FALLBACK -> if (!rubric.operationalOnly || rubric.researchEligible) errors += "${rubric.rubricId}: fallback rubric leaked into research."
            }
        }
        val rubricIds = rubrics.map { it.rubricId }.toSet()
        provenance.forEach { row ->
            if (row.axisScaleId !in scaleIds || row.rubricId !in rubricIds) errors += "${row.provenanceId}: unresolved scale or rubric."
            if (row.operationalScore !in 0.0..4.0 || (row.researchScore == null && row.operationalScore == 0.0)) errors += "${row.provenanceId}: operational score is null-equivalent or UNKNOWN became zero."
        }
        if (fallbacks.map { it.priority } != (1..6).toList()) errors += "Fallback ladder must contain ordered levels 1 through 6."
        if (fallbacks.any { it.allocationPolicy != "PARENT_ONLY_WHEN_COMPLEX_FALLBACK" }) errors += "Parent-child allocation policy is unsafe."
        if (coefficientSets.map { it.coefficientSetId }.distinct().size != coefficientSets.size) errors += "Duplicate coefficient-set ID."
        if (coefficientSets.any { it.status.contains("PRODUCTION") && it.status != "DRAFT_NON_PRODUCTION" }) errors += "C4A cannot activate a production coefficient set."
        return TissueValidationReport(errors)
    }

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
