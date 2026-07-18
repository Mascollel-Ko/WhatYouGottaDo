package com.training.trackplanner.analysis.tissue

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueEducationalCopyContractTest {
    private val catalog by lazy {
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset)
        ).catalog
    }

    @Test
    fun educationalAuthorityCoversEveryCanonicalEntryWithFinalCopyContract() {
        val table = TissueMetadataParser.table(asset(TissueRcvAssetFiles.EDUCATIONAL_INFO))
        val entries = catalog.educationalInfo.values
        val obsolete = listOf(
            "복합체 안의",
            "관련 부하를 보는 연결조직 분석 단위",
            "상위 단위",
            "부하 단위",
            "관절 접촉 압력을 분산",
            "전단 부하에 대응",
            "인장 부하를 전달",
            "비틀림 부하를 제어",
            "빠른 신장 부하에 대응",
            "분석 단위"
        )

        assertEquals(92, table.rows.size)
        assertEquals(92, table.rows.map { it.getValue("stableKey") }.distinct().size)
        assertEquals(15, entries.count { it.scope == TissueEducationalInfoScope.JOINT_COMPLEX })
        assertEquals(77, entries.count { it.scope == TissueEducationalInfoScope.LOAD_UNIT })
        assertEquals(catalog.jointComplexes.keys + catalog.loadUnits.keys, catalog.educationalInfo.keys)
        assertTrue(entries.all { it.metadataVersion == TissueRcvAssetFiles.EDUCATIONAL_INFO_VERSION })
        assertTrue(entries.all { it.shortDescriptionKo == null })
        assertTrue(entries.flatMap(::prose).all { it.isNotBlank() && it.last() in ".!?" })
        assertFalse(entries.flatMap(::prose).any { text -> obsolete.any(text::contains) })
        assertFalse(entries.flatMap(::prose).any { " · " in it })
        assertEquals(entries.size, entries.map { prose(it).joinToString("|") }.distinct().size)
        assertTrue(entries.all { info ->
            when (info.scope) {
                TissueEducationalInfoScope.JOINT_COMPLEX ->
                    catalog.jointComplexes.getValue(info.stableKey).nameKo == info.displayNameKo
                TissueEducationalInfoScope.LOAD_UNIT ->
                    catalog.loadUnits.getValue(info.stableKey).nameKo == info.displayNameKo
            }
        })
    }

    @Test
    fun parentsRemainBroaderAndDistinctFromEveryMappedChild() {
        val childrenByParent = catalog.loadUnits.values.groupBy(TissueRcvLoadUnit::jointComplexStableKey)

        assertEquals(catalog.jointComplexes.keys, childrenByParent.keys)
        catalog.jointComplexes.values.forEach { joint ->
            val parent = catalog.educationalInfo.getValue(joint.stableKey)
            val parentText = prose(parent).joinToString(" ")
            val children = childrenByParent.getValue(joint.stableKey)

            assertTrue(children.isNotEmpty())
            children.forEach { child ->
                val childInfo = catalog.educationalInfo.getValue(child.stableKey)
                assertEquals(TissueEducationalInfoScope.LOAD_UNIT, childInfo.scope)
                assertFalse(parentText == prose(childInfo).joinToString(" "))
            }
            assertFalse(children.joinToString(" ") { it.nameKo } in parentText)
        }
    }

    @Test
    fun completeReviewStaysSynchronizedAndListsChildrenBeforeParent() {
        val review = reviewFile().readText(Charsets.UTF_8)
        val childrenByParent = catalog.loadUnits.values.groupBy(TissueRcvLoadUnit::jointComplexStableKey)

        assertTrue("Metadata version: ${TissueRcvAssetFiles.EDUCATIONAL_INFO_VERSION}" in review)
        assertTrue("Review result: 92/92 PASS" in review)
        assertEquals(92, Regex("<!-- ENTRY:").findAll(review).count())
        assertEquals(92, Regex("(?m)^- 손상 기전 표현:").findAll(review).count())
        catalog.jointComplexes.values.forEach { joint ->
            val groupStart = review.indexOf("## ${joint.nameKo}")
            val parentPosition = review.indexOf("<!-- ENTRY:${joint.stableKey} -->")
            assertTrue(groupStart >= 0)
            assertTrue(parentPosition > groupStart)
            childrenByParent.getValue(joint.stableKey).forEach { child ->
                val childPosition = review.indexOf("<!-- ENTRY:${child.stableKey} -->")
                assertTrue(childPosition in (groupStart + 1) until parentPosition)
            }
        }
        catalog.educationalInfo.values.forEach { info ->
            val marker = "<!-- ENTRY:${info.stableKey} -->"
            assertEquals(1, Regex(Regex.escape(marker)).findAll(review).count())
            val start = review.indexOf(marker)
            val next = review.indexOf("<!-- ENTRY:", start + marker.length).let {
                if (it == -1) review.length else it
            }
            val block = review.substring(start, next)
            assertTrue("- 위치: ${info.anatomicalLocationKo}" in block)
            assertTrue("- 주요 기능: ${info.primaryFunctionsKo.joinToString(" ")}" in block)
            assertTrue("- 주로 사용되는 동작: ${info.commonLoadContextsKo.joinToString(" ")}" in block)
            assertTrue("- 검토 결과:" in block)
        }
    }

    @Test
    fun educationalRewriteLeavesNumericalAuthoritiesByteIdentical() {
        val expected = mapOf(
            "connective_tissue_prior_baselines_v1.json" to
                "52afc97806cf5135fcc12e2e550b6d136bbdd05094e4912904f1c8a3c8ff7baf",
            "tissue_rcv_exercise_load_unit_authority_v1.csv" to
                "7efd022c6b7b1ec3b927bfd81b61c6ac5195425da8b7f2e607b057f2ee529ac5",
            "tissue_rcv_recovery_curve_knots_v1.csv" to
                "0282bcf10426dfea744aa20aa3500cac960ad43950e4c057ab05dfa0b9311837",
            "tissue_rcv_load_units_v1.csv" to
                "36ace1900713e91301d67c5375db80a951c51dcd34d9476ee75eaef0e5a2f371"
        )

        expected.forEach { (name, hash) -> assertEquals(name, hash, sha256(assetFile(name))) }
    }

    @Test
    fun validatorRejectsStaleVersionBoilerplateAndDuplicateCompleteCopy() {
        val assets = TissueRcvAssetFiles.required.associateWith(::asset)
        val original = assets.getValue(TissueRcvAssetFiles.EDUCATIONAL_INFO)
        val rows = TissueMetadataParser.table(original).rows

        assertThrows(IllegalArgumentException::class.java) {
            repositoryWith(original.replaceFirst(TissueRcvAssetFiles.EDUCATIONAL_INFO_VERSION, "RCV-ALL-0.6-EDU-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repositoryWith(original.replaceFirst(rows.first().getValue("anatomicalLocationKo"), "복합체 안의 분석 단위."))
        }

        val first = rows[0]
        val second = rows[1]
        val duplicateCopy = listOf("anatomicalLocationKo", "primaryFunctionsKo", "commonLoadContextsKo")
            .fold(original) { csv, field ->
                csv.replaceFirst(second.getValue(field), first.getValue(field))
            }
        assertThrows(IllegalArgumentException::class.java) { repositoryWith(duplicateCopy) }
    }

    private fun prose(info: TissueEducationalInfo): List<String> = buildList {
        add(info.anatomicalLocationKo)
        addAll(info.primaryFunctionsKo)
        addAll(info.commonLoadContextsKo)
        info.shortDescriptionKo?.let(::add)
    }

    private fun asset(name: String): String = assetFile(name).readText(Charsets.UTF_8)

    private fun repositoryWith(educationalCsv: String): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset) +
                (TissueRcvAssetFiles.EDUCATIONAL_INFO to educationalCsv)
        )

    private fun assetFile(name: String): File = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::isFile)

    private fun reviewFile(): File = sequenceOf(
        File("../docs/reviews/connective_tissue_educational_copy_review_v2.md"),
        File("docs/reviews/connective_tissue_educational_copy_review_v2.md")
    ).first(File::isFile)

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
