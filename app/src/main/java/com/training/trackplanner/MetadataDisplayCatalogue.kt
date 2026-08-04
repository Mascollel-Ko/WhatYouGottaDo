package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal enum class MetadataDisplayField(val runtimeField: String?) {
    ACTIVITY_KIND("activityKind"),
    PLANNING_ELIGIBILITY("planningEligibility"),
    PROGRAM_SLOT("programSlot"),
    REDUNDANCY_GROUP("redundancyGroup"),
    PROGRESS_METRIC("progressMetricType"),
    STRENGTH_PROGRESSION_GROUP("strengthProgressionGroup"),
    ANALYSIS_ELIGIBILITY("analysisEligibility"),
    MOVEMENT_PATTERN(null),
    MOVEMENT_CATEGORY(null),
    FORCE_TYPE(null),
    TRAINING_ROLE_RELATION(null),
    PROGRAM_SLOT_CAPABILITY(null),
    MOVEMENT_FAMILY("movementFamily"),
    MOVEMENT_SUBTYPE("movementSubtype"),
    PRIMARY_STRESS_PROFILE("primaryStressProfile"),
    SECONDARY_STRESS("secondaryStressTags"),
    TENDON_STRESS("tendonStressTags"),
    LIGAMENT_JOINT_STABILITY("ligamentJointStabilityStressTags"),
    JOINT_IMPACT("jointImpactStressTags"),
    COGNITIVE_STRESS("cognitiveStressTags"),
    SPORT_CONTEXT("sportContextTags"),
    RECOVERY_DECAY("recoveryDecayProfile"),
    STRESS_LEVEL("stressMagnitudeHint"),
    BADMINTON_TRANSFER_LEVEL("badmintonTransferLevel"),
    BADMINTON_TRANSFER_TYPE("badmintonTransferType"),
    BADMINTON_SKILL_TARGET("badmintonSkillTargets"),
    BADMINTON_PHYSICAL_QUALITY("badmintonPhysicalQualities"),
    TRANSFER_CONFIDENCE("transferConfidence"),
    SOURCE_CONFIDENCE("sourceConfidenceLevel"),
    FINAL_SOURCE_STATUS("finalSourceStatus"),
    NEUROMUSCULAR_STRESS("neuromuscularStressLevel"),
    SYSTEMIC_MUSCULAR_STRESS("systemicMuscularStressLevel"),
    LOCAL_MUSCULAR_STRESS("localMuscularStressLevel"),
    JOINT_TENDON_IMPACT_STRESS("jointTendonImpactStressLevel"),
    MOVEMENT_FOCUS_DEMAND("movementFocusDemandLevel"),
    RECOVERY_DURATION("recoveryDurationClass"),
    AXIAL_LOAD(null),
    LATERALITY(null),
    METADATA_CONFIDENCE(null),
    DIRECT_TRANSFER(null),
    SUPPORTIVE_TRANSFER(null);

    companion object {
        private val byRuntimeField = entries
            .mapNotNull { field -> field.runtimeField?.let { it to field } }
            .toMap()

        fun fromRuntimeField(field: String): MetadataDisplayField? = byRuntimeField[field]
    }
}

internal data class MetadataDisplayOption(
    val code: String,
    val label: String,
    val searchAliases: List<String> = emptyList()
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        return normalized.isEmpty() ||
            searchAliases.any { alias -> alias.contains(normalized, ignoreCase = true) }
    }
}

internal class MetadataDisplayCatalogue private constructor(
    private val localized: Map<MetadataKey, String>,
    private val korean: Map<MetadataKey, String>,
    private val english: Map<MetadataKey, String>,
    private val unknownValueLabel: (String) -> String
) {
    fun label(field: MetadataDisplayField, canonicalCode: String): String {
        val code = canonicalCode.trim()
        if (code.isEmpty()) return ""
        val key = MetadataKey(field, canonicalCodeForLookup(field, code))
        return localized[key]
            ?: if (code.contains(KOREAN_TEXT)) code else unknownValueLabel(code)
    }

    fun option(field: MetadataDisplayField, canonicalCode: String): MetadataDisplayOption {
        val code = canonicalCode.trim()
        val key = MetadataKey(field, canonicalCodeForLookup(field, code))
        return MetadataDisplayOption(
            code = code,
            label = label(field, code),
            searchAliases = listOfNotNull(
                code,
                localized[key],
                korean[key],
                english[key]
            ).filter(String::isNotBlank).distinct()
        )
    }

    fun options(
        field: MetadataDisplayField,
        canonicalCodes: Collection<String>
    ): List<MetadataDisplayOption> =
        canonicalCodes
            .filter(String::isNotBlank)
            .distinct()
            .map { code -> option(field, code) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MetadataDisplayOption::label))

    fun hasRegisteredLabel(
        field: MetadataDisplayField,
        canonicalCode: String,
        locale: Locale
    ): Boolean {
        val key = MetadataKey(field, canonicalCodeForLookup(field, canonicalCode.trim()))
        return when (locale.language) {
            Locale.KOREAN.language -> korean.containsKey(key)
            Locale.ENGLISH.language -> english.containsKey(key)
            else -> localized.containsKey(key)
        }
    }

    internal fun registeredCount(): Int = korean.keys.intersect(english.keys).size

    companion object {
        fun from(context: Context): MetadataDisplayCatalogue {
            val currentLocale = context.resources.configuration.locales[0]
            val localizedResources = context.resourcesForLocale(currentLocale)
            val koreanResources = context.resourcesForLocale(Locale.KOREAN)
            val englishResources = context.resourcesForLocale(Locale.ENGLISH)
            return MetadataDisplayCatalogue(
                localized = parseEntries(
                    localizedResources.getStringArray(R.array.metadata_display_entries)
                ),
                korean = parseEntries(
                    koreanResources.getStringArray(R.array.metadata_display_entries)
                ),
                english = parseEntries(
                    englishResources.getStringArray(R.array.metadata_display_entries)
                ),
                unknownValueLabel = { code ->
                    localizedResources.getString(R.string.metadata_unknown_value, code)
                }
            )
        }

        private fun parseEntries(entries: Array<String>): Map<MetadataKey, String> =
            entries.associate { entry ->
                val parts = entry.split(ENTRY_SEPARATOR, limit = 3)
                require(parts.size == 3) { "Invalid metadata display entry: $entry" }
                MetadataKey(
                    field = MetadataDisplayField.valueOf(parts[0]),
                    code = parts[1]
                ) to parts[2]
            }

        private fun canonicalCodeForLookup(
            field: MetadataDisplayField,
            code: String
        ): String = FIELD_ALIASES[field]?.get(code) ?: code

        private fun Context.resourcesForLocale(locale: Locale) =
            createConfigurationContext(
                Configuration(resources.configuration).apply { setLocale(locale) }
            ).resources

        private const val ENTRY_SEPARATOR = "|"
        private val KOREAN_TEXT = Regex("[가-힣]")
        private val FIELD_ALIASES = mapOf(
            MetadataDisplayField.ACTIVITY_KIND to mapOf(
                "TRAINING_EXERCISE" to "EXERCISE"
            )
        )
    }
}

private data class MetadataKey(
    val field: MetadataDisplayField,
    val code: String
)
