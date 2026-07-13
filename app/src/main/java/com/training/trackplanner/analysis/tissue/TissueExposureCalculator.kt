package com.training.trackplanner.analysis.tissue

object TissueExposureCalculator {
    fun calculate(
        record: TissueWorkoutRecord,
        profiles: List<TissueLoadProfile>,
        dimensionWeights: Map<String, TissueProfileDimensionWeight>,
        modifierRules: List<TissueModifierRule> = emptyList(),
        allowNonProductionFixtures: Boolean = false
    ): List<RecordTissueExposure> = profiles.sortedBy(TissueLoadProfile::identity).map { profile ->
        calculate(record, profile, dimensionWeights[profile.profileRowId], modifierRules, allowNonProductionFixtures)
    }

    private fun calculate(
        record: TissueWorkoutRecord,
        profile: TissueLoadProfile,
        dimensionWeight: TissueProfileDimensionWeight?,
        modifierRules: List<TissueModifierRule>,
        allowNonProductionFixtures: Boolean
    ): RecordTissueExposure {
        val sidePolicy = runCatching { enumValueOf<TissueSideAllocationPolicy>(profile.sideAllocationPolicy) }.getOrNull()
        val side = sidePolicy?.let {
            TissueSideResolver.resolve(it, record.performedSide, record.balancedAlternatingProtocol)
        } ?: TissueSideResolution(
            TissueSide.UNSIDED,
            TissueSideResolutionStatus.UNRESOLVED,
            TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA,
            listOf("Missing or invalid side allocation policy.")
        )
        val key = TissueLoadKey(profile.tissueClass, profile.tissueId, profile.loadDimension, side.side)
        if (profile.stableKey != record.exercise.stableKey) {
            return failed(record, profile, key, side, TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA,
                "Profile stableKey does not match the record stableKey.")
        }
        val basis = runCatching { enumValueOf<TissueDoseBasis>(profile.doseBasis) }.getOrNull()
            ?: return failed(record, profile, key, side, TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA,
                "Missing or invalid dose basis.")
        if (profile.referenceConditionId.isBlank()) {
            return failed(record, profile, key, side, TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA,
                "Reference condition is required.")
        }
        if (dimensionWeight == null || dimensionWeight.profileRowId != profile.profileRowId ||
            !dimensionWeight.weight.isFinite() || dimensionWeight.weight < 0.0
        ) {
            return failed(record, profile, key, side, TissueCalculationStatus.INCOMPLETE_TISSUE_METADATA,
                "A finite non-negative tissue dimension weight is required.")
        }
        val fixtureAllowed = allowNonProductionFixtures && dimensionWeight.explicitlyNonProductionFixture
        if (!(profile.productionEligibility && dimensionWeight.productionEligibility) && !fixtureAllowed) {
            return failed(record, profile, key, side, TissueCalculationStatus.EVIDENCE_NOT_APPROVED,
                "Profile weight is not production approved.")
        }
        val dose = TissueDoseResolver.resolve(record, basis)
        if (dose.resolvedDose == null) {
            return failed(record, profile, key, side, TissueCalculationStatus.MISSING_RECORD_INPUT,
                dose.diagnostics.joinToString(" "), dose.status)
        }
        val modifier = TissueModifierResolver.resolve(
            baseWeight = dimensionWeight.weight,
            stableKey = record.exercise.stableKey,
            movementFamily = record.movementFamily,
            tissueId = profile.tissueId,
            loadDimension = profile.loadDimension,
            inputs = record.modifierInputs,
            rules = modifierRules,
            allowNonProductionFixtures = allowNonProductionFixtures
        )
        if (modifier.adjustedWeight == null) {
            return failed(record, profile, key, side, modifier.calculationStatus,
                modifier.diagnostics.joinToString(" "), dose.status)
        }
        val raw = dose.resolvedDose * dimensionWeight.weight
        val effortMultiplier = if (dose.rpeAlreadyApplied) 1.0 else record.approvedRpeMultiplier ?: 1.0
        val adjusted = dose.resolvedDose * modifier.adjustedWeight * effortMultiplier
        require(raw.isFinite() && raw >= 0.0 && adjusted.isFinite() && adjusted >= 0.0)
        return RecordTissueExposure(
            recordId = record.entry.id,
            date = record.date,
            stableKey = record.exercise.stableKey,
            tissueLoadKey = key,
            resolvedDose = dose.resolvedDose,
            rawExposure = raw,
            adjustedExposure = adjusted,
            calculationStatus = if (side.calculationStatus == TissueCalculationStatus.SIDE_UNRESOLVED) {
                TissueCalculationStatus.SIDE_UNRESOLVED
            } else TissueCalculationStatus.CALCULABLE,
            doseResolutionStatus = dose.status,
            sideResolutionStatus = side.status,
            appliedModifierIds = modifier.appliedModifierIds,
            evidenceStatus = profile.evidenceStatus,
            confidenceLevel = profile.confidenceLevel,
            evidenceClaimIds = profile.evidenceClaimIds.distinct().sorted(),
            sourceRefs = profile.sourceRefs.distinct().sorted(),
            diagnostics = (side.diagnostics + dose.diagnostics + modifier.diagnostics).distinct()
        )
    }

    private fun failed(
        record: TissueWorkoutRecord,
        profile: TissueLoadProfile,
        key: TissueLoadKey,
        side: TissueSideResolution,
        status: TissueCalculationStatus,
        diagnostic: String,
        doseStatus: TissueDoseResolutionStatus = TissueDoseResolutionStatus.MISSING_RECORD_INPUT
    ) = RecordTissueExposure(
        recordId = record.entry.id,
        date = record.date,
        stableKey = record.exercise.stableKey,
        tissueLoadKey = key,
        resolvedDose = null,
        rawExposure = null,
        adjustedExposure = null,
        calculationStatus = status,
        doseResolutionStatus = doseStatus,
        sideResolutionStatus = side.status,
        appliedModifierIds = emptyList(),
        evidenceStatus = profile.evidenceStatus,
        confidenceLevel = profile.confidenceLevel,
        evidenceClaimIds = profile.evidenceClaimIds.distinct().sorted(),
        sourceRefs = profile.sourceRefs.distinct().sorted(),
        diagnostics = (side.diagnostics + diagnostic).filter(String::isNotBlank).distinct()
    )
}
