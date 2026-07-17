package com.training.trackplanner.analysis.tissue

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TissuePriorProtocolDocumentationTest {
    @Test
    fun existingProtocolAuthorityRegistersPhaseOneWithoutCreatingAnotherCanonicalDocument() {
        val protocols = registry.getJSONArray("protocols")
        val personal = (0 until protocols.length())
            .map(protocols::getJSONObject)
            .single { it.getString("protocolId") == "CT-PERSONAL-CALIBRATION" }

        assertEquals(28, protocols.length())
        assertEquals("connective_tissue/PERSONAL_CALIBRATION.md", personal.getString("canonicalDocument"))
        assertEquals("1.1.0", personal.getString("currentVersion"))
        assertEquals(
            listOf("DESIGNED", "GENERATED", "VALIDATED", "NOT_YET_RUNTIME_ACTIVE"),
            (0 until personal.getJSONArray("phaseOneLifecycle").length())
                .map(personal.getJSONArray("phaseOneLifecycle")::getString)
        )

        val components = personal.getJSONArray("phaseOneComponents")
        assertEquals(9, components.length())
        (0 until components.length()).map(components::getJSONObject).forEach { component ->
            assertEquals(
                "docs/protocols/connective_tissue/PERSONAL_CALIBRATION.md",
                component.getString("canonicalDocument")
            )
            listOf(
                "protocolId",
                "authorityImplementationPath",
                "input",
                "output",
                "evidenceStatus",
                "productPolicyStatus",
                "generatorVersion",
                "registrySchemaVersion",
                "generatedArtifactChecksum",
                "implementationStatus",
                "knownLimitations",
                "downstreamDependency"
            ).forEach { field -> assertTrue("${component.getString("protocolId")} lacks $field", component.has(field)) }
            assertTrue(
                component.getJSONArray("implementationStatus")
                    .toString()
                    .contains("NOT_YET_RUNTIME_ACTIVE")
            )
        }
    }

    @Test
    fun canonicalContractDefinesBoundaryMixingAndLinksGeneratedAuthority() {
        val text = File(
            repoRoot,
            "docs/protocols/connective_tissue/PERSONAL_CALIBRATION.md"
        ).readText(Charsets.UTF_8)

        listOf(
            "CurrentLoad",
            "BasePriorBaseline",
            "AdjustedPriorBaseline",
            "PersonalBaseline",
            "w_perUnit",
            "FinalBoundary",
            "DESIGNED / GENERATED / VALIDATED / NOT_YET_RUNTIME_ACTIVE",
            "112 consecutive calendar days",
            "첫 56일",
            "Q30",
            "Q80",
            "Q95",
            "meaningfulFloor",
            "SIMULATION_FITTED",
            "POLICY_BOUNDED",
            "NEUTRAL_NOT_APPLICABLE",
            "7개 calendar days",
            "56 valid calendar days",
            "connective_tissue_prior_baselines.json",
            "generation_report.md"
        ).forEach { required -> assertTrue("Missing canonical contract term: $required", text.contains(required)) }
        assertTrue(text.contains("w_perUnit`은 비교 경계만 혼합하며 `CurrentLoad`를 곱하거나 줄이지 않습니다."))
    }

    @Test
    fun machineRegistryReferencesTheCommittedGeneratedArtifactChecksum() {
        val protocols = registry.getJSONArray("protocols")
        val personal = (0 until protocols.length())
            .map(protocols::getJSONObject)
            .single { it.getString("protocolId") == "CT-PERSONAL-CALIBRATION" }
        val manifest = JSONObject(
            File(
                repoRoot,
                "tools/connective-tissue-prior/generated/generation_manifest.json"
            ).readText(Charsets.UTF_8)
        )
        val checksum = manifest.getString("canonicalRegistrySha256")

        assertTrue(
            personal.getJSONArray("authorityAssets").toString()
                .contains("tools/connective-tissue-prior/generated/connective_tissue_prior_baselines.json")
        )
        (0 until personal.getJSONArray("phaseOneComponents").length())
            .map(personal.getJSONArray("phaseOneComponents")::getJSONObject)
            .filterNot { it.isNull("generatedArtifactChecksum") }
            .forEach { assertEquals(checksum, it.getString("generatedArtifactChecksum")) }
    }

    companion object {
        private val repoRoot: File = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .first { File(it, "docs/protocols/protocol_registry.json").isFile }
        private val registry = JSONObject(
            File(repoRoot, "docs/protocols/protocol_registry.json").readText(Charsets.UTF_8)
        )
    }
}
