package com.training.trackplanner.analysis.tissue

import java.math.BigDecimal
import java.security.MessageDigest

object TissueMetadataValidator {
    private val catalogEvidenceTypes = setOf(
        "STANDARD_ANATOMY_TEXTBOOK",
        "STANDARD_TERMINOLOGY_SOURCE",
        "CONSENSUS_STATEMENT",
        "ANATOMY_REVIEW",
        "AUTHORITATIVE_ANATOMY_DATABASE"
    )
    private val catalogVerificationStatuses = setOf(
        "UNVERIFIED",
        "BIBLIOGRAPHICALLY_VERIFIED",
        "STANDARD_REFERENCE_VERIFIED",
        "DISPUTED"
    )

    fun catalog(entries: List<TissueCatalogEntry>): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (entries.map(TissueCatalogEntry::tissueId).distinct().size != entries.size) {
            errors += "Duplicate tissueId."
        }
        entries.forEach { entry ->
            if (entry.supportedLoadDimensions.isEmpty()) errors += "${entry.tissueId}: no supported dimensions."
            if (!TissueLoadDimension.byClass.getValue(entry.tissueClass).containsAll(entry.supportedLoadDimensions)) {
                errors += "${entry.tissueId}: dimension does not belong to ${entry.tissueClass}."
            }
            if (entry.catalogEvidenceType !in catalogEvidenceTypes) errors += "${entry.tissueId}: invalid catalog evidence type."
            if (entry.catalogVerificationStatus !in catalogVerificationStatuses) {
                errors += "${entry.tissueId}: invalid catalog verification status."
            }
            if (entry.catalogVerificationStatus != "UNVERIFIED" && entry.sourceRefs.isEmpty()) {
                errors += "${entry.tissueId}: verified catalog entry has no sourceRefs."
            }
            if (entry.functionalDescription.containsExerciseLoadClaim() && entry.sourceRefs.isEmpty()) {
                errors += "${entry.tissueId}: exercise-load or risk claim cannot use catalog-only evidence."
            }
            if (entry.tissueClass != TissueClass.JOINT && entry.isTrueJoint) {
                errors += "${entry.tissueId}: non-joint marked as a true joint."
            }
        }
        val expectedOrder = entries.sortedWith(compareBy<TissueCatalogEntry>({ it.tissueClass.name }, { it.tissueId }))
        if (entries != expectedOrder) errors += "Catalog order is not stable by tissueClass and tissueId."
        if (entries.find("PLANTAR_FASCIA")?.tissueClass != TissueClass.FASCIA) errors += "Plantar fascia is not fascia."
        listOf("KNEE_TIBIOFEMORAL", "KNEE_PATELLOFEMORAL").forEach { id ->
            if (entries.find(id)?.tissueClass != TissueClass.JOINT) errors += "$id is missing as a distinct joint."
        }
        listOf("KNEE_ACL", "KNEE_PCL", "KNEE_MCL", "KNEE_LCL").forEach { id ->
            if (entries.find(id)?.tissueClass != TissueClass.LIGAMENT) errors += "$id is missing as a distinct ligament."
        }
        if (entries.find("SCAPULOTHORACIC_FUNCTIONAL_COMPLEX")?.isTrueJoint != false) {
            errors += "Scapulothoracic complex must be a functional articulation."
        }
        return TissueValidationReport(errors)
    }

    fun scope(
        entries: List<TissueScopeEntry>,
        canonicalStableKeys: Set<String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val expected = canonicalStableKeys.flatMap { stableKey -> catalog.map { stableKey to it.tissueId } }.toSet()
        val actual = entries.map { it.stableKey to it.tissueId }
        if (actual.size != actual.distinct().size) errors += "Duplicate scope identities."
        if (actual.toSet() != expected) errors += "Scope manifest does not cover every canonical stableKey and tissue."
        val catalogById = catalog.associateBy(TissueCatalogEntry::tissueId)
        entries.forEach { entry ->
            if (entry.stableKey !in canonicalStableKeys) errors += "Unknown stableKey: ${entry.stableKey}"
            if (catalogById[entry.tissueId]?.tissueClass != entry.tissueClass) {
                errors += "${entry.stableKey}/${entry.tissueId}: tissue class mismatch."
            }
            if (entry.productionEligibility && entry.humanApprovedBy.isBlank()) {
                errors += "${entry.stableKey}/${entry.tissueId}: production scope lacks human approval."
            }
        }
        return TissueValidationReport(errors)
    }

    fun profiles(
        profiles: List<TissueLoadProfile>,
        canonicalStableKeys: Set<String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (profiles.map(TissueLoadProfile::identity).distinct().size != profiles.size) {
            errors += "Duplicate profile identity."
        }
        val catalogById = catalog.associateBy(TissueCatalogEntry::tissueId)
        profiles.forEach { profile ->
            if (profile.stableKey !in canonicalStableKeys) errors += "Unknown stableKey: ${profile.stableKey}"
            val tissue = catalogById[profile.tissueId]
            if (tissue == null || tissue.tissueClass != profile.tissueClass) {
                errors += "${profile.profileRowId}: tissue class mismatch."
            } else if (profile.loadDimension !in tissue.supportedLoadDimensions) {
                errors += "${profile.profileRowId}: unsupported load dimension."
            }
            when (profile.evidenceStatus) {
                TissueEvidenceStatus.NOT_YET_EVALUATED -> if (profile.loadBand != null) {
                    errors += "${profile.profileRowId}: NOT_YET_EVALUATED must have a blank loadBand."
                }
                TissueEvidenceStatus.EVALUATED_ABSENT -> if (profile.loadBand != TissueLoadBand.NONE) {
                    errors += "${profile.profileRowId}: EVALUATED_ABSENT must use NONE."
                }
                TissueEvidenceStatus.CONFLICTING_EVIDENCE,
                TissueEvidenceStatus.BLOCKED_INSUFFICIENT_EVIDENCE -> if (profile.productionEligibility) {
                    errors += "${profile.profileRowId}: blocked evidence cannot be production eligible."
                }
                else -> Unit
            }
            if (profile.productionEligibility) {
                if (profile.sourceRefs.isEmpty()) errors += "${profile.profileRowId}: production profile has no sourceRefs."
                if (profile.rubricId.isBlank()) errors += "${profile.profileRowId}: production profile has no rubric."
                if (profile.humanApprovedBy.isBlank()) errors += "${profile.profileRowId}: production profile lacks human approval."
            }
        }
        return TissueValidationReport(errors)
    }

    fun rubricIntervals(rubrics: List<TissueLoadRubric>): TissueValidationReport {
        val errors = mutableListOf<String>()
        rubrics.forEach { rubric ->
            if (rubric.loadBand == TissueLoadBand.NONE) errors += "${rubric.rubricId}: NONE cannot represent missing evidence."
            if (rubric.boundarySemanticsVersion.isBlank()) errors += "${rubric.rubricId}: boundary semantics version is missing."
            if ((rubric.metricLowerBound == null) != (rubric.lowerBoundInclusive == null)) {
                errors += "${rubric.rubricId}: lower-bound inclusivity must be present exactly when its bound is present."
            }
            if ((rubric.metricUpperBound == null) != (rubric.upperBoundInclusive == null)) {
                errors += "${rubric.rubricId}: upper-bound inclusivity must be present exactly when its bound is present."
            }
            if (rubric.metricLowerBound != null && rubric.metricUpperBound != null &&
                rubric.metricLowerBound > rubric.metricUpperBound
            ) {
                errors += "${rubric.rubricId}: interval is inverted."
            }
        }
        rubrics.filter { it.metricLowerBound != null || it.metricUpperBound != null }
            .groupBy { listOf(it.tissueId, it.loadDimension.name, it.metricType, it.metricUnit) }
            .values.forEach { group ->
                val sorted = group.sortedWith(compareBy<TissueLoadRubric> { it.metricLowerBound != null }
                    .thenBy { it.metricLowerBound })
                sorted.zipWithNext().forEach { (left, right) ->
                    if (left.sameInterval(right)) {
                        errors += "${left.rubricId}/${right.rubricId}: duplicate interval."
                    } else if (left.overlaps(right)) {
                        errors += "${left.rubricId}/${right.rubricId}: overlapping intervals."
                    }
                }
            }
        return TissueValidationReport(errors.distinct())
    }

    fun matchingRubricBands(rubrics: List<TissueLoadRubric>, value: BigDecimal): List<TissueLoadBand> =
        rubrics.filter { rubric ->
            val aboveLower = rubric.metricLowerBound?.let { lower ->
                value > lower || (value == lower && rubric.lowerBoundInclusive == true)
            } ?: true
            val belowUpper = rubric.metricUpperBound?.let { upper ->
                value < upper || (value == upper && rubric.upperBoundInclusive == true)
            } ?: true
            aboveLower && belowUpper
        }.map(TissueLoadRubric::loadBand)

    fun semanticCsvHash(csv: String): String {
        val table = TissueMetadataParser.table(csv)
        val header = table.header.joinToString("\u001F")
        val rows = table.rows.map { row -> table.header.joinToString("\u001F") { row[it].orEmpty() } }.sorted()
        return (header + "\n" + rows.joinToString("\u001E")).sha256()
    }

    fun combinedHash(parts: Map<String, String>): String =
        parts.entries.sortedBy(Map.Entry<String, String>::key).joinToString("\n") { (key, value) -> "$key=$value" }.sha256()

    private fun List<TissueCatalogEntry>.find(id: String): TissueCatalogEntry? = firstOrNull { it.tissueId == id }
    private fun TissueLoadRubric.sameInterval(other: TissueLoadRubric): Boolean =
        metricLowerBound == other.metricLowerBound && lowerBoundInclusive == other.lowerBoundInclusive &&
            metricUpperBound == other.metricUpperBound && upperBoundInclusive == other.upperBoundInclusive
    private fun TissueLoadRubric.overlaps(other: TissueLoadRubric): Boolean {
        val upper = metricUpperBound ?: return true
        val lower = other.metricLowerBound ?: return true
        return upper > lower || (upper == lower && upperBoundInclusive == true && other.lowerBoundInclusive == true)
    }
    private fun String.containsExerciseLoadClaim(): Boolean =
        listOf("load", "force", "injury", "risk", "strain", "stress", "compression")
            .any { token -> contains(token, ignoreCase = true) }
    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
