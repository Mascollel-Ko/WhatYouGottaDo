package com.training.trackplanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
            field.localizationMode == ExerciseMetadataLocalizationMode.USER_TEXT_PASSTHROUGH
        })
        assertTrue(fields.filter { it.localizationMode == ExerciseMetadataLocalizationMode.NEVER_DISPLAY }.all { field ->
            field.displayDisposition == ExerciseMetadataDisplayDisposition.NON_DISPLAY_IDENTIFIER
        })
        assertEquals(10, ExerciseMetadataFieldPolicyRegistry.semanticProjection().lineSequence().first().split('|').size)
        assertEquals(6, ExerciseMetadataFieldPolicyRegistry.displayProjection().lineSequence().first().split('|').size)
    }

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
}
