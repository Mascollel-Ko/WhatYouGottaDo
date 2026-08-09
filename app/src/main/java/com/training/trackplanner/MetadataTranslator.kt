package com.training.trackplanner

import android.content.Context
import com.training.trackplanner.data.ExerciseMetadataFieldDefinition
import com.training.trackplanner.data.ExerciseMetadataFieldPolicyRegistry
import com.training.trackplanner.data.ExerciseMetadataLocalizationMode
import com.training.trackplanner.data.ExerciseMetadataValueKind
import java.text.NumberFormat

internal class MetadataTranslator private constructor(
    private val context: Context,
    private val catalogue: MetadataDisplayCatalogue
) {
    fun translate(fieldKey: String, value: String): String? {
        val field = definition(fieldKey)
        if (value.isBlank()) return ""
        return when (field.localizationMode) {
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE ->
                if (field.valueKind == ExerciseMetadataValueKind.CANONICAL_TOKEN_SET) {
                    tokens(field, splitTokens(value)).joinToString(" · ")
                } else {
                    catalogue.label(domain(field), value)
                }
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH ->
                catalogue.registeredLabel(domain(field), value) ?: value
            ExerciseMetadataLocalizationMode.EXERCISE_NAME_CATALOGUE,
            ExerciseMetadataLocalizationMode.USER_TEXT_PASSTHROUGH -> value
            ExerciseMetadataLocalizationMode.ANDROID_STRING_RESOURCE -> when (value.toBooleanStrictOrNull()) {
                true -> context.getString(R.string.metadata_boolean_true)
                false -> context.getString(R.string.metadata_boolean_false)
                null -> context.getString(R.string.metadata_unknown_value)
            }
            ExerciseMetadataLocalizationMode.LOCALE_FORMATTER -> formatTyped(field, value)
            ExerciseMetadataLocalizationMode.NEVER_DISPLAY -> null
        }
    }

    fun translateTokens(fieldKey: String, values: Iterable<String>): List<String> =
        tokens(definition(fieldKey), values)

    fun option(fieldKey: String, canonicalCode: String): MetadataDisplayOption {
        val field = definition(fieldKey)
        return when (field.localizationMode) {
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE ->
                catalogue.option(domain(field), canonicalCode)
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH -> {
                val registered = catalogue.registeredLabel(domain(field), canonicalCode)
                if (registered == null) passthroughOption(canonicalCode)
                else catalogue.option(domain(field), canonicalCode)
            }
            ExerciseMetadataLocalizationMode.NEVER_DISPLAY -> passthroughOption("")
            else -> passthroughOption(canonicalCode, translate(fieldKey, canonicalCode).orEmpty())
        }
    }

    fun options(fieldKey: String, canonicalCodes: Collection<String>): List<MetadataDisplayOption> =
        canonicalCodes
            .filter(String::isNotBlank)
            .distinct()
            .map { option(fieldKey, it) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MetadataDisplayOption::label))

    private fun tokens(
        field: ExerciseMetadataFieldDefinition,
        values: Iterable<String>
    ): List<String> {
        val canonical = values.map(String::trim).filter(String::isNotBlank).distinct()
        val ordered = if (field.preservesTokenOrder) canonical else canonical.sorted()
        return ordered.map { catalogue.label(domain(field), it) }
    }

    private fun formatTyped(field: ExerciseMetadataFieldDefinition, value: String): String {
        val number = value.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: return context.getString(R.string.metadata_unknown_value)
        val formatted = NumberFormat.getNumberInstance(context.resources.configuration.locales[0])
            .format(number)
        return if (field.valueKind == ExerciseMetadataValueKind.DURATION) {
            context.getString(R.string.metadata_duration_seconds, formatted)
        } else {
            formatted
        }
    }

    private fun definition(fieldKey: String): ExerciseMetadataFieldDefinition =
        requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey)) {
            "Unknown exercise metadata presentation field: $fieldKey"
        }

    private fun domain(field: ExerciseMetadataFieldDefinition): MetadataDisplayField =
        MetadataDisplayField.valueOf(field.displayDomain)

    private fun passthroughOption(code: String, label: String = code) = MetadataDisplayOption(
        code = code,
        label = label,
        searchAliases = listOf(code).filter(String::isNotBlank)
    )

    private fun splitTokens(value: String): List<String> =
        value.split(',', '|', '/', ';')

    companion object {
        fun from(context: Context): MetadataTranslator = MetadataTranslator(
            context,
            MetadataDisplayCatalogue.from(context)
        )
    }
}
