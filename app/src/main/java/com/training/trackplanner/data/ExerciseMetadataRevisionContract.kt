package com.training.trackplanner.data

import android.content.Context
import android.content.res.Configuration
import com.training.trackplanner.R
import java.security.MessageDigest
import java.util.Locale

data class ExerciseMetadataRevisionManifest(
    val semanticCanonicalMetadataRevision: String,
    val metadataDisplayDictionaryRevision: String,
    val semanticFieldProjectionSha256: String,
    val displayFieldProjectionSha256: String
)

internal object ExerciseMetadataRevisionPolicy {
    fun project(
        context: Context,
        repository: CanonicalExerciseMetadataRepository
    ): ExerciseMetadataRevisionManifest {
        val semanticProjection = ExerciseMetadataFieldPolicyRegistry.semanticProjection()
        val displayProjection = ExerciseMetadataFieldPolicyRegistry.displayProjection()
        val runtimeByKey = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val rolesByKey = repository.trainingRoleRelations().groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
        val capabilitiesByKey = repository.programSlotCapabilityRelations()
            .groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)
        val semanticPayload = buildString {
            appendLine(semanticProjection)
            repository.exercises(includeHistory = true).sortedBy(Exercise::stableKey).forEach { exercise ->
                val source = ExerciseMetadataSnapshotSource(
                    exercise = exercise,
                    runtimeMetadata = checkNotNull(runtimeByKey[exercise.stableKey]),
                    trainingRoles = rolesByKey[exercise.stableKey].orEmpty()
                        .mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode),
                    programSlotCapabilities = capabilitiesByKey[exercise.stableKey].orEmpty()
                        .mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode)
                )
                ExerciseMetadataFieldPolicyRegistry.snapshot(source)
                    .filter { row ->
                        ExerciseMetadataFieldPolicyRegistry.definition(row.fieldKey)?.policy !in setOf(
                            ExerciseMetadataFieldPolicy.USER_STATE,
                            ExerciseMetadataFieldPolicy.DERIVED_REBUILD
                        )
                    }
                    .sortedBy(ExerciseMetadataSnapshotRow::fieldKey)
                    .forEach { row ->
                        append(exercise.stableKey).append('|')
                            .append(row.fieldKey).append('|')
                            .append(row.valueEncoding.name).append('|')
                            .append(row.isExplicitEmpty).append('|')
                            .appendLine(row.value)
                    }
            }
        }
        val displayPayload = buildString {
            appendLine(displayProjection)
            listOf(Locale.KOREAN, Locale.ENGLISH).forEach { locale ->
                val resources = context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply { setLocale(locale) }
                ).resources
                appendLine(locale.language)
                resources.getStringArray(R.array.metadata_display_entries).sorted().forEach(::appendLine)
                resources.getStringArray(R.array.metadata_display_alias_entries).sorted().forEach(::appendLine)
            }
            append(
                context.assets.open("metadata/canonical_v1/metadata_display_labels_ko.csv")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            )
        }
        return ExerciseMetadataRevisionManifest(
            semanticCanonicalMetadataRevision = semanticPayload.metadataSha256(),
            metadataDisplayDictionaryRevision = displayPayload.metadataSha256(),
            semanticFieldProjectionSha256 = semanticProjection.metadataSha256(),
            displayFieldProjectionSha256 = displayProjection.metadataSha256()
        )
    }
}

internal object ExerciseMetadataContractArtifacts {
    private val orderedFields: List<ExerciseMetadataFieldDefinition>
        get() = ExerciseMetadataFieldPolicyRegistry.fields.sortedWith(
            compareBy({ it.fieldScope.name }, { it.fieldKey })
        )

    fun csv(): String = buildString {
        appendLine(
            "fieldScope,fieldKey,ownershipPolicy,valueEncoding,valueKind,displayDomain," +
                "localizationMode,displayDisposition,allowsUserFreeText,supportsExplicitEmpty," +
                "preservesTokenOrder,editorWritable,sourceCodeOwner,semanticNormalizerSchemaVersion"
        )
        orderedFields.forEach { field ->
            appendLine(
                listOf(
                    field.fieldScope.name,
                    field.fieldKey,
                    field.policy.name,
                    field.valueEncoding.name,
                    field.valueKind.name,
                    field.displayDomain,
                    field.localizationMode.name,
                    field.displayDisposition.name,
                    field.allowsUserFreeText,
                    field.supportsExplicitEmpty,
                    field.preservesTokenOrder,
                    field.editorWritable,
                    field.sourceCodeOwner,
                    field.semanticNormalizerSchemaVersion
                ).joinToString(",") { it.toString().csvEscape() }
            )
        }
    }

    fun json(): String = orderedFields.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { field ->
        "  {" + listOf(
            "\"fieldScope\":\"${field.fieldScope.name.jsonEscape()}\"",
            "\"fieldKey\":\"${field.fieldKey.jsonEscape()}\"",
            "\"ownershipPolicy\":\"${field.policy.name.jsonEscape()}\"",
            "\"valueEncoding\":\"${field.valueEncoding.name.jsonEscape()}\"",
            "\"valueKind\":\"${field.valueKind.name.jsonEscape()}\"",
            "\"displayDomain\":\"${field.displayDomain.jsonEscape()}\"",
            "\"localizationMode\":\"${field.localizationMode.name.jsonEscape()}\"",
            "\"displayDisposition\":\"${field.displayDisposition.name.jsonEscape()}\"",
            "\"allowsUserFreeText\":${field.allowsUserFreeText}",
            "\"supportsExplicitEmpty\":${field.supportsExplicitEmpty}",
            "\"preservesTokenOrder\":${field.preservesTokenOrder}",
            "\"editorWritable\":${field.editorWritable}",
            "\"sourceCodeOwner\":\"${field.sourceCodeOwner.jsonEscape()}\"",
            "\"semanticNormalizerSchemaVersion\":${field.semanticNormalizerSchemaVersion}"
        ).joinToString(",") + "}"
    }

    fun revisionManifestJson(manifest: ExerciseMetadataRevisionManifest): String =
        """{
  "semanticCanonicalMetadataRevision": "${manifest.semanticCanonicalMetadataRevision}",
  "metadataDisplayDictionaryRevision": "${manifest.metadataDisplayDictionaryRevision}",
  "semanticFieldProjectionSha256": "${manifest.semanticFieldProjectionSha256}",
  "displayFieldProjectionSha256": "${manifest.displayFieldProjectionSha256}"
}
"""
}

internal fun String.metadataSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

private fun Any.csvEscape(): String {
    val value = toString()
    return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
}

private fun String.jsonEscape(): String = buildString(length) {
    this@jsonEscape.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
