package com.training.trackplanner.analysis.tissue

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TissuePriorBaselineGenerationTest {
    @Test
    fun generatedRegistryCoversEveryCurrentUnsidedLoadUnitExactlyOnce() {
        val assignments = registry.getJSONArray("loadUnitAssignments").objects()
        val catalog = repository().catalog

        assertEquals(catalog.loadUnits.size, registry.getInt("loadUnitCount"))
        assertEquals(catalog.loadUnits.size, assignments.size)
        assertEquals(
            catalog.loadUnits.keys,
            assignments.mapTo(linkedSetOf()) { it.getString("loadUnitStableKey") }
        )
        assertEquals(assignments.size, assignments.map { it.getString("loadUnitStableKey") }.distinct().size)
        assertTrue(assignments.all { it.getString("priorProfileId").isNotBlank() })
        assertFalse(assignments.any {
            it.getString("loadUnitStableKey").contains("LEFT", true) ||
                it.getString("loadUnitStableKey").contains("RIGHT", true)
        })
    }

    @Test
    fun everyGeneratedBucketHasFiniteStrictlyOrderedBoundaries() {
        val profiles = registry.getJSONArray("profiles").objects()
        assertEquals(registry.getInt("priorProfileCount"), profiles.size)

        profiles.forEach { profile ->
            val buckets = profile.getJSONArray("evaluationBuckets").objects()
            assertEquals((0..23).toList(), buckets.map { it.getInt("localHour") })
            buckets.forEach { bucket ->
                val floor = bucket.getDouble("meaningfulFloor")
                val q30 = bucket.getDouble("q30")
                val q80 = bucket.getDouble("q80")
                val q95 = bucket.getDouble("q95")
                assertTrue(listOf(floor, q30, q80, q95).all(Double::isFinite))
                assertTrue(0.0 <= floor && floor < q30 && q30 < q80 && q80 < q95)
                assertTrue(0.0 < q30)
            }
        }
    }

    @Test
    fun simulationValidationsCoverEveryProfile() {
        val count = registry.getInt("priorProfileCount")
        val validation = registry.getJSONObject("simulationValidation")

        assertEquals(count, validation.getInt("fullyRecoveredBelowQ30Profiles"))
        assertEquals(count, validation.getInt("moderateSingleExposureNotAboveQ95Profiles"))
        assertEquals(count, validation.getInt("repeatedHardExposureExceedsQ95Profiles"))
        assertTrue(validation.getBoolean("slowerRecoveryRetainsMoreAt24Hours"))
    }

    @Test
    fun twoCleanInMemoryRegenerationsAreIdenticalAndCommittedFilesHaveNoDrift() {
        val first = generator.generate()
        val second = generator.generate()

        assertEquals(first, second)
        generator.validate(first)
        assertEquals(first.canonicalRegistry, first.appReadyRegistry)
    }

    @Test
    fun driftGuardRejectsManualGeneratedChanges() {
        assertThrows(IllegalArgumentException::class.java) {
            requireNoGeneratedDrift("manually changed", "generated authority", "fixture")
        }
    }

    @Test
    fun manifestChecksumsMatchTheActualCommittedRegistryBytes() {
        val manifest = JSONObject(
            File(repoRoot, ConnectiveTissuePriorGenerator.MANIFEST_PATH).readText(Charsets.UTF_8)
        )
        val canonical = File(repoRoot, ConnectiveTissuePriorGenerator.CANONICAL_PATH)
        val appReady = File(repoRoot, ConnectiveTissuePriorGenerator.APP_READY_PATH)

        assertEquals(manifest.getString("canonicalRegistrySha256"), sha256(canonical.readBytes()))
        assertEquals(manifest.getString("appReadySha256"), sha256(appReady.readBytes()))
        assertEquals(canonical.readBytes().toList(), appReady.readBytes().toList())
    }

    @Test
    fun productionInputFingerprintsAreLineEndingIndependent() {
        val lf = "authority\nrow\n".toByteArray(Charsets.UTF_8)
        val crlf = "authority\r\nrow\r\n".toByteArray(Charsets.UTF_8)

        assertEquals(
            normalizePriorFingerprintText(lf).toList(),
            normalizePriorFingerprintText(crlf).toList()
        )
    }

    @Test
    fun generatedArtifactKeepsPhaseOneStatusWhileRuntimeUsesItsProductionAuthority() {
        assertEquals(
            listOf("DESIGNED", "GENERATED", "VALIDATED", "NOT_YET_RUNTIME_ACTIVE"),
            registry.getJSONArray("lifecycleStatus").strings()
        )
        val source = File(
            repoRoot,
            "tools/connective-tissue-prior/src/main/kotlin/" +
                "com/training/trackplanner/analysis/tissue/ConnectiveTissuePriorGenerator.kt"
        ).readText(Charsets.UTF_8)
        assertTrue(source.contains("TissueRcvEventLedgerBuilder"))
        assertTrue(source.contains("TissueResidualCalculator"))
        assertFalse(source.contains("fun interpolate"))

        val runtimeCallers = File(repoRoot, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "TissuePriorAdjustment.kt" }
            .filter { file ->
                val text = file.readText(Charsets.UTF_8)
                text.contains("connective_tissue_prior_baselines_v1") ||
                    text.contains("TissuePriorAdjustment.adjust")
            }
            .map { it.name }
            .sorted()
            .toList()
        assertEquals(
            listOf("TissueEffectiveBaselinePolicy.kt", "TissuePriorRegistry.kt"),
            runtimeCallers
        )
    }

    companion object {
        private val repoRoot: File = sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .first { File(it, "tools/connective-tissue-prior/scenario_catalog.json").isFile }
        private val generator by lazy { ConnectiveTissuePriorGenerator(repoRoot.toPath()) }
        private val artifacts by lazy { generator.generate() }
        private val registry by lazy { JSONObject(artifacts.canonicalRegistry) }

        private fun repository(): TissueRcvAssetRepository =
            TissueRcvAssetRepository.fromCsv(
                TissueRcvAssetFiles.required.associateWith { name ->
                    File(repoRoot, "app/src/main/assets/metadata/tissue_load_v1/$name")
                        .readText(Charsets.UTF_8)
                }
            )

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

private fun org.json.JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun org.json.JSONArray.strings(): List<String> = (0 until length()).map(::getString)
