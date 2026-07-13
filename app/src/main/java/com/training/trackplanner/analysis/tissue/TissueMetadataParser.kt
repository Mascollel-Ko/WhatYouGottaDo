package com.training.trackplanner.analysis.tissue

import java.util.Locale

data class TissueCsvTable(
    val header: List<String>,
    val rows: List<Map<String, String>>
)

object TissueMetadataParser {
    fun table(csv: String): TissueCsvTable {
        val records = parseCsv(csv)
        require(records.isNotEmpty()) { "CSV is empty." }
        val header = records.first().map { it.trim().trimStart('\uFEFF') }
        require(header.none(String::isBlank)) { "CSV contains a blank header." }
        require(header.distinct().size == header.size) { "CSV contains duplicate headers." }
        return TissueCsvTable(
            header = header,
            rows = records.drop(1).filter { row -> row.any(String::isNotBlank) }.map { row ->
                header.mapIndexed { index, key -> key to row.getOrElse(index) { "" }.trim() }.toMap()
            }
        )
    }

    fun catalog(csv: String): List<TissueCatalogEntry> = table(csv).rows.map { row ->
        TissueCatalogEntry(
            tissueClass = row.enum("tissueClass"),
            tissueId = row.required("tissueId"),
            displayGroup = row.required("displayGroup"),
            anatomicalRegion = row.required("anatomicalRegion"),
            anatomicalName = row.required("anatomicalName"),
            functionalDescription = row.value("functionalDescription"),
            isTrueJoint = row.boolean("isTrueJoint"),
            supportsLaterality = row.boolean("supportsLaterality"),
            supportedLoadDimensions = row.tokens("supportedLoadDimensions").mapTo(linkedSetOf()) {
                enumValueOf<TissueLoadDimension>(it)
            },
            defaultScopePolicy = row.required("defaultScopePolicy"),
            catalogVersion = row.required("catalogVersion"),
            catalogEvidenceType = row.required("catalogEvidenceType"),
            catalogVerificationStatus = row.required("catalogVerificationStatus"),
            sourceRefs = row.tokens("sourceRefs"),
            catalogNotes = row.value("catalogNotes")
        )
    }

    fun profiles(csv: String): List<TissueLoadProfile> = table(csv).rows.map { row ->
        TissueLoadProfile(
            profileRowId = row.required("profileRowId"),
            stableKey = row.required("stableKey"),
            tissueClass = row.enum("tissueClass"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            loadBand = row.value("loadBand").takeIf(String::isNotBlank)?.let { enumValueOf<TissueLoadBand>(it) },
            evidenceStatus = row.enum("evidenceStatus"),
            evidenceLevel = row.value("evidenceLevel"),
            confidenceLevel = row.value("confidenceLevel"),
            reviewStatus = row.required("reviewStatus"),
            productionEligibility = row.boolean("productionEligibility"),
            doseBasis = row.value("doseBasis"),
            referenceConditionId = row.value("referenceConditionId"),
            modifierSetId = row.value("modifierSetId"),
            recoveryProfileId = row.value("recoveryProfileId"),
            sideAllocationPolicy = row.value("sideAllocationPolicy"),
            rubricId = row.value("rubricId"),
            evidenceSetId = row.value("evidenceSetId"),
            evidenceClaimIds = row.tokens("evidenceClaimIds"),
            sourceRefs = row.tokens("sourceRefs"),
            reviewBatchId = row.value("reviewBatchId"),
            preparedBy = row.value("preparedBy"),
            preparedByType = row.optionalEnum<TissueActorType>("preparedByType"),
            preparedAt = row.value("preparedAt"),
            blindReviewedBy = row.value("blindReviewedBy"),
            blindReviewedByType = row.optionalEnum<TissueActorType>("blindReviewedByType"),
            blindReviewedAt = row.value("blindReviewedAt"),
            humanApprovedBy = row.value("humanApprovedBy"),
            humanApprovedAt = row.value("humanApprovedAt"),
            reviewNotes = row.value("reviewNotes")
        )
    }

    fun scope(csv: String): List<TissueScopeEntry> = table(csv).rows.map { row ->
        TissueScopeEntry(
            stableKey = row.required("stableKey"),
            tissueClass = row.enum("tissueClass"),
            tissueId = row.required("tissueId"),
            scopeStatus = row.enum("scopeStatus"),
            legacySeedTags = row.tokens("legacySeedTags"),
            reviewPriority = row.value("reviewPriority"),
            reviewBatchId = row.value("reviewBatchId"),
            productionEligibility = row.boolean("productionEligibility"),
            reviewedCatalogVersion = row.required("reviewedCatalogVersion"),
            preparedBy = row.value("preparedBy"),
            preparedByType = row.optionalEnum<TissueActorType>("preparedByType"),
            preparedAt = row.value("preparedAt"),
            blindReviewedBy = row.value("blindReviewedBy"),
            blindReviewedByType = row.optionalEnum<TissueActorType>("blindReviewedByType"),
            blindReviewedAt = row.value("blindReviewedAt"),
            humanApprovedBy = row.value("humanApprovedBy"),
            humanApprovedAt = row.value("humanApprovedAt"),
            reviewNotes = row.value("reviewNotes")
        )
    }

    fun auditManifest(csv: String): TissueMetadataAuditManifest =
        TissueMetadataAuditManifest(table(csv).rows.single())

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                char == '"' && quoted && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += value.toString()
                    value.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') index++
                    row += value.toString()
                    value.clear()
                    if (row.any(String::isNotEmpty)) rows += row
                    row = mutableListOf()
                }
                else -> value.append(char)
            }
            index++
        }
        if (value.isNotEmpty() || row.isNotEmpty()) {
            row += value.toString()
            if (row.any(String::isNotEmpty)) rows += row
        }
        require(!quoted) { "CSV contains an unterminated quoted value." }
        return rows
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
        value(name).split('|').map(String::trim).filter { it.isNotBlank() && it != "NONE" }.distinct()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase(Locale.ROOT))
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.let { enumValueOf<T>(it) }
}
