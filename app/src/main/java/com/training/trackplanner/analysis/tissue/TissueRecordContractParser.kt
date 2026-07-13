package com.training.trackplanner.analysis.tissue

import java.util.Locale

object TissueRecordContractParser {
    fun legacyMigrations(csv: String): List<LegacyTissueTagMigration> =
        TissueMetadataParser.table(csv).rows.map { row ->
            LegacyTissueTagMigration(
                legacyField = row.required("legacyField"),
                legacyToken = row.required("legacyToken"),
                candidateTissueIds = row.tokens("candidateTissueIds"),
                candidateDimensions = row.tokens("candidateDimensions").map { enumValueOf<TissueLoadDimension>(it) },
                mappingSpecificity = row.enum("mappingSpecificity"),
                reviewPriority = row.required("reviewPriority"),
                automaticBandAllowed = row.boolean("automaticBandAllowed"),
                migrationStatus = row.enum("migrationStatus"),
                migrationNotes = row.value("migrationNotes")
            )
        }

    fun doseCapabilities(csv: String): List<TissueDoseCapability> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueDoseCapability(
                doseBasis = row.enum("doseBasis"),
                recordSource = row.value("recordSource"),
                availabilityStatus = row.enum("availabilityStatus"),
                derivationMethod = row.value("derivationMethod"),
                requiredFields = row.tokens("requiredFields"),
                fallbackDoseBasis = row.optionalEnum<TissueDoseBasis>("fallbackDoseBasis"),
                fallbackAllowed = row.boolean("fallbackAllowed"),
                fallbackConfidence = row.value("fallbackConfidence"),
                fallbackEvidenceClaimIds = row.tokens("fallbackEvidenceClaimIds"),
                sourceRefs = row.tokens("sourceRefs"),
                requiresSchemaChange = row.boolean("requiresSchemaChange"),
                requiresUiChange = row.boolean("requiresUiChange"),
                implementationNotes = row.value("implementationNotes")
            )
        }

    fun modifierRules(csv: String): List<TissueModifierRule> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueModifierRule(
                modifierRuleId = row.required("modifierRuleId"),
                stableKey = row.value("stableKey"),
                tissueId = row.required("tissueId"),
                loadDimension = row.enum("loadDimension"),
                modifierFamily = row.enum("modifierFamily"),
                inputCondition = row.required("inputCondition"),
                referenceCondition = row.required("referenceCondition"),
                operation = row.enum("operation"),
                factor = row.doubleOrNull("factor"),
                bandShift = row.intOrNull("bandShift"),
                specificityLevel = row.enum("specificityLevel"),
                exclusiveGroup = row.value("exclusiveGroup"),
                interactionGroup = row.value("interactionGroup"),
                precedence = row.value("precedence").ifBlank { "0" }.toInt(),
                minimumCombinedFactor = row.doubleOrNull("minimumCombinedFactor"),
                maximumCombinedFactor = row.doubleOrNull("maximumCombinedFactor"),
                requiredRecordInputs = row.tokens("requiredRecordInputs"),
                missingInputBehavior = row.enum("missingInputBehavior"),
                evidenceStatus = row.enum("evidenceStatus"),
                evidenceClaimIds = row.tokens("evidenceClaimIds"),
                sourceRefs = row.tokens("sourceRefs"),
                confidenceLevel = row.value("confidenceLevel"),
                reviewStatus = row.required("reviewStatus"),
                humanApprovedBy = row.value("humanApprovedBy"),
                reviewNotes = row.value("reviewNotes")
            )
        }

    fun recoveryProfiles(csv: String): List<TissueRecoveryProfile> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueRecoveryProfile(
                recoveryProfileId = row.required("recoveryProfileId"),
                tissueClass = row.enum("tissueClass"),
                tissueId = row.required("tissueId"),
                loadDimension = row.enum("loadDimension"),
                calculationMode = row.enum("calculationMode"),
                kernelType = row.enum("kernelType"),
                parameterSet = row.value("parameterSet"),
                validWindowHours = row.intOrNull("validWindowHours"),
                evidenceStatus = row.enum("evidenceStatus"),
                evidenceClaimIds = row.tokens("evidenceClaimIds"),
                sourceRefs = row.tokens("sourceRefs"),
                confidenceLevel = row.value("confidenceLevel"),
                productionEligibility = row.boolean("productionEligibility"),
                humanApprovedBy = row.value("humanApprovedBy"),
                recoveryNotes = row.value("recoveryNotes")
            )
        }

    private fun Map<String, String>.value(name: String): String = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String): String =
        value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String): Boolean = when (value(name).uppercase(Locale.ROOT)) {
        "TRUE", "1", "YES" -> true
        "FALSE", "0", "NO", "" -> false
        else -> error("Invalid boolean in $name: ${value(name)}")
    }
    private fun Map<String, String>.tokens(name: String): List<String> =
        value(name).split('|').map(String::trim).filter(String::isNotBlank).distinct()
    private fun Map<String, String>.doubleOrNull(name: String): Double? = value(name).takeIf(String::isNotBlank)?.toDouble()
    private fun Map<String, String>.intOrNull(name: String): Int? = value(name).takeIf(String::isNotBlank)?.toInt()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase(Locale.ROOT))
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.let { enumValueOf<T>(it) }
}
