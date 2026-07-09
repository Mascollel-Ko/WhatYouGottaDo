package com.training.trackplanner.data

internal data class ProgramItemRestoreMetadata(
    val trainingSlot: String,
    val dayIntensity: String,
    val weightSource: String
)

internal enum class ProgramItemRestoreMetadataField {
    TRAINING_SLOT,
    DAY_INTENSITY,
    WEIGHT_SOURCE
}

internal sealed interface ProgramItemRestoreMetadataParseResult {
    val metadata: ProgramItemRestoreMetadata
    val fallbackFields: Set<ProgramItemRestoreMetadataField>

    data class Success(
        override val metadata: ProgramItemRestoreMetadata
    ) : ProgramItemRestoreMetadataParseResult {
        override val fallbackFields: Set<ProgramItemRestoreMetadataField> = emptySet()
    }

    data class PartialFallback(
        override val metadata: ProgramItemRestoreMetadata,
        override val fallbackFields: Set<ProgramItemRestoreMetadataField>
    ) : ProgramItemRestoreMetadataParseResult

    data class Failed(
        override val metadata: ProgramItemRestoreMetadata,
        override val fallbackFields: Set<ProgramItemRestoreMetadataField>,
        val reason: String
    ) : ProgramItemRestoreMetadataParseResult
}

internal data class ProgramItemRestoreMetadataResolution(
    val metadata: ProgramItemRestoreMetadata,
    val legacyFields: Set<ProgramItemRestoreMetadataField>,
    val unresolvedFields: Set<ProgramItemRestoreMetadataField>
)

internal object ProgramItemRestoreMetadataParser {
    private const val DEFAULT_TRAINING_SLOT = "FULL_BODY_BADMINTON_SUPPORT"
    private const val DEFAULT_DAY_INTENSITY = "MODERATE"
    private const val DEFAULT_WEIGHT_SOURCE = "MANUAL_OR_EXISTING"
    private val metadataValue = Regex("[A-Z][A-Z0-9_]*")
    private val weightSources = setOf(
        "DIRECT_HISTORY_HIGH",
        "DIRECT_HISTORY_MEDIUM",
        "MANUAL_INPUT",
        "MANUAL_OR_EXISTING",
        "NONE",
        "RULE_TABLE",
        "TIMED_OR_QUALITY"
    )

    fun parse(prescription: String): ProgramItemRestoreMetadataParseResult {
        val parts = prescription.split('·').map(String::trim).filter(String::isNotEmpty)
        val trainingSlot = parts.taggedValue("SLOT:")
        val dayIntensity = parts.taggedValue("DAY:")
        val weightSource = parts.firstNotNullOfOrNull { part ->
            when {
                part.startsWith("WEIGHT:") -> part.removePrefix("WEIGHT:").trim()
                    .takeIf(metadataValue::matches)
                part in weightSources -> part
                else -> null
            }
        }
        val fallbackFields = buildSet {
            if (trainingSlot == null) add(ProgramItemRestoreMetadataField.TRAINING_SLOT)
            if (dayIntensity == null) add(ProgramItemRestoreMetadataField.DAY_INTENSITY)
            if (weightSource == null) add(ProgramItemRestoreMetadataField.WEIGHT_SOURCE)
        }
        val metadata = ProgramItemRestoreMetadata(
            trainingSlot = trainingSlot ?: DEFAULT_TRAINING_SLOT,
            dayIntensity = dayIntensity ?: DEFAULT_DAY_INTENSITY,
            weightSource = weightSource ?: DEFAULT_WEIGHT_SOURCE
        )
        return when {
            fallbackFields.isEmpty() -> ProgramItemRestoreMetadataParseResult.Success(metadata)
            fallbackFields.size < ProgramItemRestoreMetadataField.entries.size ->
                ProgramItemRestoreMetadataParseResult.PartialFallback(metadata, fallbackFields)
            else -> ProgramItemRestoreMetadataParseResult.Failed(
                metadata = metadata,
                fallbackFields = fallbackFields,
                reason = "Prescription contains no restorable program metadata."
            )
        }
    }

    fun resolve(item: TrainingProgramItem): ProgramItemRestoreMetadataResolution {
        val structured = mapOf(
            ProgramItemRestoreMetadataField.TRAINING_SLOT to item.trainingSlot.nonBlankOrNull(),
            ProgramItemRestoreMetadataField.DAY_INTENSITY to item.dayIntensity.nonBlankOrNull(),
            ProgramItemRestoreMetadataField.WEIGHT_SOURCE to item.weightSource.nonBlankOrNull()
        )
        val missingFields = structured.filterValues { it == null }.keys
        if (missingFields.isEmpty()) {
            return ProgramItemRestoreMetadataResolution(
                metadata = ProgramItemRestoreMetadata(
                    trainingSlot = item.trainingSlot!!,
                    dayIntensity = item.dayIntensity!!,
                    weightSource = item.weightSource!!
                ),
                legacyFields = emptySet(),
                unresolvedFields = emptySet()
            )
        }

        val legacy = parse(item.prescription)
        val unresolvedFields = missingFields.intersect(legacy.fallbackFields)
        return ProgramItemRestoreMetadataResolution(
            metadata = ProgramItemRestoreMetadata(
                trainingSlot = structured[ProgramItemRestoreMetadataField.TRAINING_SLOT]
                    ?: legacy.metadata.trainingSlot,
                dayIntensity = structured[ProgramItemRestoreMetadataField.DAY_INTENSITY]
                    ?: legacy.metadata.dayIntensity,
                weightSource = structured[ProgramItemRestoreMetadataField.WEIGHT_SOURCE]
                    ?: legacy.metadata.weightSource
            ),
            legacyFields = missingFields - unresolvedFields,
            unresolvedFields = unresolvedFields
        )
    }

    private fun List<String>.taggedValue(prefix: String): String? =
        firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf(metadataValue::matches)

    private fun String?.nonBlankOrNull(): String? = this?.takeIf(String::isNotBlank)
}
