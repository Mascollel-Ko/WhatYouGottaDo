package com.training.trackplanner

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentationLinksTest {
    @Test
    fun exposedDestinationsMatchThePublicMainBranch() {
        assertEquals("https://github.com/Mascollel-Ko/WhatYouGottaDo-Docs", DocumentationLinks.HOME)
        assertDocumentPath(DocumentationLinks.ANALYSIS_OVERVIEW, "docs/02_기능/분석.md")
        assertDocumentPath(DocumentationLinks.OFI_GUIDE, "docs/03_결과_읽는법/OFI.md")
        assertDocumentPath(
            DocumentationLinks.STRENGTH_ESTIMATE_GUIDE,
            "docs/03_결과_읽는법/근력_수행능력_추정.md"
        )
        assertDocumentPath(
            DocumentationLinks.CONNECTIVE_TISSUE_GUIDE,
            "docs/03_결과_읽는법/연결조직_사용량.md"
        )
        assertDocumentPath(DocumentationLinks.BADMINTON_GUIDE, "docs/03_결과_읽는법/배드민턴.md")
        assertDocumentPath(
            DocumentationLinks.LAB_STRENGTH_PERFORMANCE_GUIDE,
            "docs/03_결과_읽는법/실험실/근력운동_퍼포먼스.md"
        )
        assertEquals(7, DocumentationLinks.all.size)
        assertEquals(7, DocumentationLinks.all.distinct().size)
    }

    @Test
    fun KoreanPathsAreEncodedOnceAndKeepDirectorySeparators() {
        DocumentationLinks.all.drop(1).forEach { url ->
            assertTrue(url.startsWith("https://github.com/Mascollel-Ko/WhatYouGottaDo-Docs/blob/main/"))
            assertFalse(url.contains("%25", ignoreCase = true))
            assertFalse(Uri.decode(url).contains("%"))
            assertTrue(Uri.parse(url).pathSegments.contains("main"))
        }
    }

    @Test
    fun mainStrengthAndLabGuidesStayDistinctAndNoCalculationDocumentIsExposed() {
        assertNotEquals(
            DocumentationLinks.STRENGTH_ESTIMATE_GUIDE,
            DocumentationLinks.LAB_STRENGTH_PERFORMANCE_GUIDE
        )
        assertTrue(
            DocumentationLinks.all.none { url ->
                Uri.decode(url).substringAfterLast('/').contains("_계산")
            }
        )
    }

    @Test
    fun browserSecurityFailureDoesNotEscape() {
        assertFalse(
            launchPublicDocumentation(DocumentationLinks.OFI_GUIDE) {
                throw SecurityException("Blocked")
            }
        )
    }

    @Test
    fun approvedUiPlacementsUseEachDestinationOnce() {
        val explanation = source("app/src/main/java/com/training/trackplanner/AppExplanationScreens.kt")
        val coach = source("app/src/main/java/com/training/trackplanner/AnalysisCoachUi.kt")
        val tissue = source("app/src/main/java/com/training/trackplanner/ConnectiveTissueAnalysisUi.kt")
        val strength = source("app/src/main/java/com/training/trackplanner/AnalysisPersistentStrengthPerformanceUi.kt")
        val detail = source("app/src/main/java/com/training/trackplanner/AnalysisDetailScreens.kt")

        assertOrdered(explanation, "analysis_guide_calibration_note", "DocumentationLinks.ANALYSIS_OVERVIEW")
        assertOrdered(explanation, "ProtocolFamilyRow(R.string.protocol_family_program_builder)", "DocumentationLinks.HOME")
        assertOrdered(coach, "projectedOfi?.let", "DocumentationLinks.OFI_GUIDE")
        assertOrdered(coach, "Text(section.shortInterpretation", "DocumentationLinks.LAB_STRENGTH_PERFORMANCE_GUIDE")
        assertOrdered(tissue, "TissueBaselineProvenanceFooter(ui.provenance)", "DocumentationLinks.CONNECTIVE_TISSUE_GUIDE")
        assertOrdered(strength, "PersistentStrengthHistoryCard(selectedTargets", "DocumentationLinks.STRENGTH_ESTIMATE_GUIDE")
        assertOrdered(detail, "BadmintonTrainingLoadCharts(", "DocumentationLinks.BADMINTON_GUIDE")

        val usages = listOf(explanation, coach, tissue, strength, detail)
            .sumOf { text -> Regex("DocumentationLinks\\.(HOME|ANALYSIS_OVERVIEW|OFI_GUIDE|STRENGTH_ESTIMATE_GUIDE|CONNECTIVE_TISSUE_GUIDE|BADMINTON_GUIDE|LAB_STRENGTH_PERFORMANCE_GUIDE)").findAll(text).count() }
        assertEquals(7, usages)
    }

    @Test
    fun oldUserFacingProtocolDestinationIsAbsentFromProduction() {
        val production = sourceTree("app/src/main/java")
        assertFalse(production.contains("PUBLIC_" + "PROTOCOL_INDEX_URL"))
        assertFalse(production.contains("launchPublic" + "ProtocolIndex"))
        val retiredUrl = listOf(
            "https://github.com/Mascollel-Ko/WhatYouGottaDo",
            "tree",
            "main",
            "docs",
            "protocols"
        ).joinToString("/")
        assertFalse(production.contains(retiredUrl))
    }

    private fun assertDocumentPath(url: String, path: String) {
        val uri = Uri.parse(url)
        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/Mascollel-Ko/WhatYouGottaDo-Docs/blob/main/$path", uri.path)
    }

    private fun assertOrdered(source: String, first: String, second: String) {
        assertTrue("Expected $first before $second", source.indexOf(first) in 0 until source.indexOf(second))
    }

    private fun source(relativePath: String): String = repoFile(relativePath).readText()

    private fun sourceTree(relativePath: String): String = repoFile(relativePath)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    private fun repoFile(relativePath: String): File = sequenceOf(File(relativePath), File("..", relativePath))
        .first(File::exists)
}
