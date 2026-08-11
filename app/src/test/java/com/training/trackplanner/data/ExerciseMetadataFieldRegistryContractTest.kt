package com.training.trackplanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.MetadataDisplayField
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExerciseMetadataFieldRegistryContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun registryHasStableUniqueFieldsAndEditorOwnership() {
        val fields = ExerciseMetadataFieldPolicyRegistry.fields
        assertEquals(fields.size, fields.distinctBy { it.fieldScope to it.fieldKey }.size)
        assertTrue(fields.isNotEmpty())
        assertTrue(ExerciseMetadataFieldPolicyRegistry.editorWritableDefinitions().all { field ->
            field.policy == ExerciseMetadataFieldPolicy.USER_OVERRIDE_ELIGIBLE
        })
        assertTrue(fields.filter { it.valueKind == ExerciseMetadataValueKind.FREE_TEXT }.all { field ->
            field.localizationMode in setOf(
                ExerciseMetadataLocalizationMode.USER_TEXT_PASSTHROUGH,
                ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH
            )
        })
        assertTrue(fields.filter { it.localizationMode == ExerciseMetadataLocalizationMode.NEVER_DISPLAY }.all { field ->
            field.displayDisposition == ExerciseMetadataDisplayDisposition.NON_DISPLAY_IDENTIFIER
        })
        assertEquals(10, ExerciseMetadataFieldPolicyRegistry.semanticProjection().lineSequence().first().split('|').size)
        assertEquals(6, ExerciseMetadataFieldPolicyRegistry.displayProjection().lineSequence().first().split('|').size)
    }

    @Test
    fun displayDomainsAndLocalizationModesMatchFieldSemantics() {
        val fields = ExerciseMetadataFieldPolicyRegistry.fields
        val catalogueModes = setOf(
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE,
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH
        )

        fields.filter { it.localizationMode in catalogueModes }.forEach { field ->
            MetadataDisplayField.valueOf(field.displayDomain)
        }
        assertEquals("MOVEMENT_PATTERN", definition("exercise.movementPattern").displayDomain)
        assertEquals("AXIAL_LOAD", definition("exercise.axialLoadLevel").displayDomain)
        assertEquals("LATERALITY", definition("exercise.laterality").displayDomain)
        assertEquals("FORCE_TYPE", definition("exercise.forceType").displayDomain)
        assertEquals("EXERCISE_MODE", definition("exercise.mode").displayDomain)
        assertEquals(
            ExerciseMetadataLocalizationMode.LOCALE_FORMATTER,
            definition("exercise.defaultRestSeconds").localizationMode
        )
        assertEquals(
            ExerciseMetadataLocalizationMode.ANDROID_STRING_RESOURCE,
            definition("exercise.estimated1RmEligible").localizationMode
        )
        assertEquals(
            ExerciseMetadataLocalizationMode.METADATA_DISPLAY_CATALOGUE_OR_USER_TEXT_PASSTHROUGH,
            definition("exercise.category").localizationMode
        )
        assertEquals(
            ExerciseMetadataLocalizationMode.NEVER_DISPLAY,
            definition("exercise.archivedAt").localizationMode
        )
        assertEquals(
            ExerciseMetadataDisplayDisposition.NON_DISPLAY_IDENTIFIER,
            definition("exercise.archivedAt").displayDisposition
        )
    }

    private fun definition(fieldKey: String): ExerciseMetadataFieldDefinition =
        requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))

    @Test
    fun artifactsAreDeterministicAndCurrent() {
        val manifest = ExerciseMetadataRevisionPolicy.project(context, repository)
        val artifacts = linkedMapOf(
            "../docs/generated/metadata_field_display_contract.csv" to ExerciseMetadataContractArtifacts.csv(),
            "src/main/assets/metadata/canonical_v1/metadata_field_display_contract.json" to
                ExerciseMetadataContractArtifacts.json(),
            "src/main/assets/metadata/canonical_v1/metadata_revision_manifest.json" to
                ExerciseMetadataContractArtifacts.revisionManifestJson(manifest)
        )
        if (System.getenv("UPDATE_METADATA_CONTRACT_ARTIFACTS") == "1") {
            artifacts.forEach { (path, content) ->
                File(path).apply { parentFile?.mkdirs() }.writeText(content, Charsets.UTF_8)
            }
        }
        artifacts.forEach { (path, expected) ->
            assertEquals("Stale generated artifact: $path", expected, File(path).readText(Charsets.UTF_8))
        }
        assertEquals(
            ExerciseMetadataFieldPolicyRegistry.fields.size,
            ExerciseMetadataContractArtifacts.csv().lineSequence().filter(String::isNotBlank).count() - 1
        )
        assertEquals(
            ExerciseMetadataFieldPolicyRegistry.fields.size,
            "\"fieldScope\"".toRegex().findAll(ExerciseMetadataContractArtifacts.json()).count()
        )
        assertEquals(ExerciseMetadataContractArtifacts.csv(), ExerciseMetadataContractArtifacts.csv())
        assertEquals(ExerciseMetadataContractArtifacts.json(), ExerciseMetadataContractArtifacts.json())
    }

    @Test
    fun semanticAndDisplayRevisionsAreIndependentDeterministicHashes() {
        val first = ExerciseMetadataRevisionPolicy.project(context, repository)
        val second = ExerciseMetadataRevisionPolicy.project(context, repository)
        assertEquals(first, second)
        assertTrue(first.semanticCanonicalMetadataRevision.matches(Regex("[0-9a-f]{64}")))
        assertTrue(first.metadataDisplayDictionaryRevision.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(first.semanticCanonicalMetadataRevision, first.metadataDisplayDictionaryRevision)
    }

    @Test
    fun releasePreservesPersistenceAndRevisionInvariants() {
        val revisions = ExerciseMetadataRevisionPolicy.project(context, repository)
        assertEquals(
            "3d2b2c343f82463d00bf5d453e019526924e296402ce0e6f731a3cf3379b966d",
            revisions.semanticCanonicalMetadataRevision
        )
        assertEquals(
            "d2eb2865aa3c4ddaa1b2c71bc49d07b63278c2257df77b415cd2bb0ae5c9f305",
            revisions.metadataDisplayDictionaryRevision
        )
        assertTrue(
            Regex("version\\s*=\\s*29").containsMatchIn(
                File("src/main/java/com/training/trackplanner/data/TrainingDatabase.kt")
                    .readText(Charsets.UTF_8)
            )
        )
        assertEquals(12, RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION)
        assertEquals(11, RecordCsvBackupRestore.CURRENT_RESTORE_SCHEMA_VERSION)
    }
}
