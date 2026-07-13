package com.training.trackplanner.analysis.tissue

object TissueRecordContractValidator {
    fun legacyMigrations(
        migrations: List<LegacyTissueTagMigration>,
        actualLegacyTokens: Set<Pair<String, String>>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val actual = migrations.map { it.legacyField to it.legacyToken }
        if (actual.size != actual.distinct().size) errors += "Duplicate legacy migration identity."
        if (actual.toSet() != actualLegacyTokens) errors += "Every actual legacy token must have exactly one migration decision."
        val catalogById = catalog.associateBy(TissueCatalogEntry::tissueId)
        migrations.forEach { migration ->
            if (migration.automaticBandAllowed) errors += "${migration.identity}: legacy mapping cannot assign a band."
            if (migration.migrationStatus == TissueMigrationStatus.LEGACY_SEEDED_NOT_EVALUATED &&
                migration.candidateTissueIds.isEmpty()
            ) errors += "${migration.identity}: seeded migration has no tissue candidates."
            migration.candidateTissueIds.forEach { id ->
                if (id !in catalogById) errors += "${migration.identity}: unknown candidate tissue $id."
            }
            migration.candidateDimensions.forEach { dimension ->
                if (migration.candidateTissueIds.none { id ->
                        catalogById[id]?.supportedLoadDimensions?.contains(dimension) == true
                    }
                ) errors += "${migration.identity}: candidate dimension $dimension is unsupported."
            }
        }
        return TissueValidationReport(errors)
    }

    fun doseCapabilities(capabilities: List<TissueDoseCapability>): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (capabilities.map(TissueDoseCapability::doseBasis).toSet() != TissueDoseBasis.entries.toSet()) {
            errors += "Dose capability audit does not cover every supported dose basis."
        }
        if (capabilities.size != capabilities.map(TissueDoseCapability::doseBasis).distinct().size) {
            errors += "Duplicate dose capability."
        }
        capabilities.forEach { capability ->
            if (capability.availabilityStatus != TissueInputAvailabilityStatus.NOT_APPLICABLE &&
                capability.requiredFields.isEmpty()
            ) errors += "${capability.doseBasis}: required fields are not explicit."
            if (capability.fallbackAllowed &&
                (capability.fallbackDoseBasis == null || capability.fallbackEvidenceClaimIds.isEmpty())
            ) errors += "${capability.doseBasis}: fallback lacks a declared basis or approved evidence."
            if (capability.availabilityStatus == TissueInputAvailabilityStatus.NOT_CURRENTLY_AVAILABLE &&
                capability.fallbackAllowed
            ) errors += "${capability.doseBasis}: unavailable input cannot use an unimplemented fallback."
        }
        return TissueValidationReport(errors)
    }

    fun modifierRules(
        rules: List<TissueModifierRule>,
        canonicalStableKeys: Set<String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (rules.size != rules.map(TissueModifierRule::modifierRuleId).distinct().size) {
            errors += "Duplicate modifier rule ID."
        }
        val catalogById = catalog.associateBy(TissueCatalogEntry::tissueId)
        rules.forEach { rule ->
            if (rule.specificityLevel == TissueModifierSpecificity.EXACT_STABLE_KEY &&
                rule.stableKey !in canonicalStableKeys
            ) errors += "${rule.modifierRuleId}: unknown exact stableKey."
            if (catalogById[rule.tissueId]?.supportedLoadDimensions?.contains(rule.loadDimension) != true) {
                errors += "${rule.modifierRuleId}: tissue/dimension mismatch."
            }
            if (rule.operation == TissueModifierOperation.MULTIPLY_WEIGHT &&
                (rule.factor == null || rule.factor <= 0.0)
            ) errors += "${rule.modifierRuleId}: multiplier must be finite and positive."
            if (rule.operation == TissueModifierOperation.REPLACE_WEIGHT &&
                (rule.factor == null || rule.factor < 0.0)
            ) errors += "${rule.modifierRuleId}: replacement must be finite and non-negative."
            if (rule.factor?.isFinite() == false) errors += "${rule.modifierRuleId}: factor is not finite."
            if (rule.evidenceStatus in setOf(
                    TissueEvidenceStatus.NOT_YET_EVALUATED,
                    TissueEvidenceStatus.CONFLICTING_EVIDENCE,
                    TissueEvidenceStatus.BLOCKED_INSUFFICIENT_EVIDENCE
                ) && rule.humanApprovedBy.isNotBlank()
            ) errors += "${rule.modifierRuleId}: blocked evidence cannot be approved."
        }
        return TissueValidationReport(errors)
    }
}
