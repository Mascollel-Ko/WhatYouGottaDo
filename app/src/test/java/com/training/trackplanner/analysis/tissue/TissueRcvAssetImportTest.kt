package com.training.trackplanner.analysis.tissue

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueRcvAssetImportTest {
    @Test
    fun importsReviewedRcvAll06AuthorityWithExactPhaseTwoCounts() {
        val catalog = repository().catalog

        assertEquals(239, catalog.exerciseStableKeys.size)
        assertEquals(3507, catalog.authorityRows.size)
        assertEquals(238, catalog.authorityRows.map { it.exerciseStableKey }.toSet().size)
        assertEquals(239, catalog.protocols.size)
        assertEquals(50, catalog.protocolClasses.size)
        assertEquals(13, catalog.diProfiles.size)
        assertEquals(21, catalog.curves.size)
        assertEquals(114, catalog.curves.values.sumOf { it.knots.size })
        assertEquals(7, catalog.routing.size)
        assertEquals(77, catalog.loadUnits.size)
        assertEquals(1, catalog.unresolvedExerciseCount)
    }

    @Test
    fun preservesLongAuthorityRowsWithoutDoubleCountingGroupConflictPairs() {
        val rows = repository().catalog.authorityRows
        val rowsByPair = rows.groupBy { it.exerciseStableKey to it.loadUnitStableKey }

        assertEquals(3501, rowsByPair.size)
        assertEquals(5, rowsByPair.count { it.value.size > 1 })
        assertEquals(3, rowsByPair.maxOf { it.value.size })
    }

    @Test
    fun resolvesEveryProtocolDiCurveRoutingAndCatalogForeignKey() {
        val catalog = repository().catalog

        assertEquals(catalog.exerciseStableKeys, catalog.protocols.keys)
        assertTrue(catalog.authorityRows.all { it.loadUnitStableKey in catalog.loadUnits })
        assertTrue(catalog.loadUnits.values.all { it.jointComplexStableKey in catalog.jointComplexes })
        assertTrue(catalog.protocols.values.all { protocol ->
            protocol.defaultProtocolClass in catalog.protocolClasses &&
                protocol.diProfileId in catalog.diProfiles &&
                protocol.functionalCurveId in catalog.curves &&
                protocol.jointProtectionCurveId in catalog.curves &&
                protocol.fastMechanicalCurveId in catalog.curves
        })
    }

    @Test
    fun leavesOnlyGenericStretchUnresolvedAndKeepsStateUnsided() {
        val unresolved = repository().catalog.protocols.values.single { it.mappingStatus == "UNRESOLVED_GENERIC" }
        val manifest = TissueMetadataParser.table(asset("tissue_rcv_asset_manifest_v1.csv"))

        assertEquals("스트레칭", unresolved.exerciseName)
        assertTrue(unresolved.runtimeFlags.contains("NO_NUMERIC_SCORE_UNTIL_SPECIFIED"))
        assertTrue(manifest.rows.all { it.getValue("stateIdentity") == "loadUnitStableKey|loadDimension|UNSIDED" })
        assertFalse(manifest.rows.any { it.getValue("stateIdentity").contains("LEFT") })
        assertFalse(manifest.rows.any { it.getValue("stateIdentity").contains("RIGHT") })
    }

    @Test
    fun generatedAssetHashesMatchTheCommittedDeterministicManifest() {
        val manifest = TissueMetadataParser.table(asset("tissue_rcv_asset_manifest_v1.csv"))

        assertEquals(15, manifest.rows.size)
        manifest.rows.forEach { row ->
            val file = assetFile(row.getValue("assetName"))
            assertEquals(row.getValue("assetName"), row.getValue("assetSha256"), sha256(file))
            assertEquals("d3be2a9af81bc42b8733fd953cc2cdc770be186b", row.getValue("baselineCommit"))
        }
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset)
        )

    private fun asset(name: String): String = assetFile(name).readText(Charsets.UTF_8)

    private fun assetFile(name: String): File = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists)

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
