package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.AnalysisEligibility
import com.training.trackplanner.data.AxialLoadLevel
import com.training.trackplanner.data.FatigueForceType
import com.training.trackplanner.data.FatigueLaterality
import com.training.trackplanner.data.MetadataConfidence
import com.training.trackplanner.data.MovementCategory
import com.training.trackplanner.data.MovementPattern
import com.training.trackplanner.data.ProgramSlotId
import com.training.trackplanner.data.ProgramSlotCapability
import com.training.trackplanner.data.RuntimeMetadataEditorOptions
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.TrainingRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataDisplayCatalogueTest {
    private val korean by lazy { MetadataDisplayCatalogue.from(context(Locale.KOREAN)) }
    private val english by lazy { MetadataDisplayCatalogue.from(context(Locale.ENGLISH)) }

    @Test
    fun knownCodesUseFieldAwareKoreanAndEnglishLabels() {
        assertEquals(
            "프로그램에 사용 가능",
            korean.label(MetadataDisplayField.PLANNING_ELIGIBILITY, "PROGRAM_SELECTABLE")
        )
        assertEquals(
            "Available for programs",
            english.label(MetadataDisplayField.PLANNING_ELIGIBILITY, "PROGRAM_SELECTABLE")
        )
        assertEquals(
            "직접 전이 없음",
            korean.label(MetadataDisplayField.BADMINTON_TRANSFER_LEVEL, "NONE")
        )
        assertEquals(
            "평가 없음",
            korean.label(MetadataDisplayField.TRANSFER_CONFIDENCE, "NONE")
        )
        assertEquals(
            "해당 없음",
            korean.label(MetadataDisplayField.PROGRAM_SLOT, "NOT_APPLICABLE")
        )
        assertEquals("매우 높음", korean.label(MetadataDisplayField.STRESS_LEVEL, "VERY_HIGH"))
        assertEquals("매우 김", korean.label(MetadataDisplayField.RECOVERY_DURATION, "VERY_LONG"))
    }

    @Test
    fun searchMatchesKoreanCanonicalAndEnglishWhileReturningCanonicalCode() {
        val option = korean.option(
            MetadataDisplayField.PLANNING_ELIGIBILITY,
            "PROGRAM_SELECTABLE"
        )

        assertTrue(option.matches("프로그램"))
        assertTrue(option.matches("PROGRAM_SELECTABLE"))
        assertTrue(option.matches("Available"))
        assertEquals("PROGRAM_SELECTABLE", option.code)
    }

    @Test
    fun officialCommonAndEnglishAliasesResolveToOneKoreanOption() {
        val option = korean.option(MetadataDisplayField.MUSCLE, "LAT")

        assertEquals("광배근", option.label)
        assertTrue(option.matches("넓은등근"))
        assertTrue(option.matches("lat"))
        assertTrue(option.matches("LAT"))
        assertEquals("LAT", option.code)
    }

    @Test
    fun productOwnerApprovedE1rmLabelRemainsExact() {
        assertEquals(
            "e1RM",
            korean.label(MetadataDisplayField.PROGRESS_METRIC, "ESTIMATED_1RM")
        )
    }

    @Test
    fun representativeProductionNamespacesUseKoreanLabels() {
        val expected = mapOf(
            MetadataDisplayField.EQUIPMENT to ("DUMBBELL" to "덤벨"),
            MetadataDisplayField.MOVEMENT_PATTERN to ("TRUNK_BRACE" to "몸통 고정"),
            MetadataDisplayField.BODY_REGION to ("LOWER" to "하체"),
            MetadataDisplayField.TISSUE to ("tissue_achilles_tendon" to "아킬레스건"),
            MetadataDisplayField.JOINT_COMPLEX to ("jc_ankle_hindfoot" to "발목·후족부 복합체"),
            MetadataDisplayField.TRAINING_ROLE_RELATION to ("STRENGTH" to "근력"),
            MetadataDisplayField.PROGRAM_SLOT_CAPABILITY to ("MAIN_STRENGTH_SLOT" to "메인 근력 슬롯"),
            MetadataDisplayField.OFI_AXIS to ("HIGH_SPEED" to "고속"),
            MetadataDisplayField.STRENGTH_PROXY_ROLE to ("DIRECT_ANCHOR" to "직접 기준 운동"),
            MetadataDisplayField.RECOVERY_PROFILE to ("LONG" to "긴")
        )

        expected.forEach { (field, pair) ->
            assertEquals("$field | ${pair.first}", pair.second, korean.label(field, pair.first))
        }
    }

    @Test
    fun everyProductionRegistryRowResolvesWithoutRawOrUnapprovedLatinFallback() {
        val assets = context(Locale.KOREAN).assets
        assets.open(DISPLAY_ASSET).bufferedReader().useLines { lines ->
            val rows = lines.iterator()
            val headers = SeedData.parseCsvLine(rows.next())
            val index = headers.withIndex().associate { it.value to it.index }
            rows.forEachRemaining { line ->
                val columns = SeedData.parseCsvLine(line)
                if (columns[index.getValue("displayScope")] != "PRODUCTION") return@forEachRemaining
                val field = MetadataDisplayField.valueOf(columns[index.getValue("displayField")])
                val code = columns[index.getValue("canonicalCode")]
                val expectedLabel = columns[index.getValue("koreanLabel")]
                val allowedLatin = columns[index.getValue("allowedLatinTokens")]
                    .split("|")
                    .filter(String::isNotBlank)
                    .toSet()
                val actual = korean.label(field, code)
                assertEquals("$field | $code", expectedLabel, actual)
                assertTrue("blank label: $field | $code", actual.isNotBlank())
                assertFalse("raw label: $field | $code", RAW_SNAKE_CASE.matches(actual))
                assertEquals("Latin allowlist: $field | $code", allowedLatin, LATIN_TOKEN.findAll(actual).map { it.value }.toSet())
            }
        }
    }

    @Test
    fun readableKoreanAndUnknownCodesRemainEditableWithoutChangingIdentity() {
        assertEquals(
            "대퇴사두근",
            korean.label(MetadataDisplayField.MOVEMENT_SUBTYPE, "대퇴사두근")
        )
        val unknown = korean.option(MetadataDisplayField.PROGRAM_SLOT, "FUTURE_SLOT_CODE")
        assertEquals("FUTURE_SLOT_CODE", unknown.code)
        assertEquals("등록되지 않은 값 · FUTURE_SLOT_CODE", unknown.label)
        assertTrue(unknown.matches("FUTURE_SLOT_CODE"))
    }

    @Test
    fun allEditorAssetAndEnumOptionsHaveKoreanAndEnglishLabels() {
        val values = knownValues()
        val missing = buildList {
            values.forEach { (field, codes) ->
                codes.forEach { code ->
                    listOf(Locale.KOREAN, Locale.ENGLISH).forEach { locale ->
                        if (!korean.hasRegisteredLabel(field, code, locale)) {
                            add("${field.name} | $code | ${locale.language}")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Missing metadata labels:\n${missing.joinToString("\n")}",
            missing.isEmpty()
        )
        assertTrue("expected a complete catalogue", korean.registeredCount() >= 1_600)
    }

    @Test
    fun registeredPresentationNeverReturnsRawSnakeCase() {
        val examples = mapOf(
            MetadataDisplayField.PROGRAM_SLOT to "UPPER_PUSH_SUPPORT",
            MetadataDisplayField.PRIMARY_STRESS_PROFILE to "HEAVY_AXIAL_LOWER_STRESS",
            MetadataDisplayField.FINAL_SOURCE_STATUS to "SOURCE_ACCEPTED_WITH_LIMITATION"
        )

        examples.forEach { (field, code) ->
            val label = korean.label(field, code)
            assertFalse("$field exposed $code", label == code)
            assertFalse("$field exposed snake case: $label", RAW_SNAKE_CASE.matches(label))
        }
    }

    private fun knownValues(): Map<MetadataDisplayField, Set<String>> {
        val values = mutableMapOf<MetadataDisplayField, MutableSet<String>>()
        fun add(field: MetadataDisplayField, codes: Iterable<String>) {
            values.getOrPut(field, ::mutableSetOf).addAll(codes.filter(String::isNotBlank))
        }

        RuntimeMetadataEditorOptions.knownValuesByField().forEach { (runtimeField, codes) ->
            MetadataDisplayField.fromRuntimeField(runtimeField)?.let { field -> add(field, codes) }
        }
        add(MetadataDisplayField.MOVEMENT_PATTERN, MovementPattern.entries.map(Enum<*>::name))
        add(MetadataDisplayField.MOVEMENT_CATEGORY, MovementCategory.entries.map(Enum<*>::name))
        add(MetadataDisplayField.FORCE_TYPE, FatigueForceType.entries.map(Enum<*>::name))
        add(MetadataDisplayField.TRAINING_ROLE_RELATION, TrainingRole.entries.map(Enum<*>::name))
        add(MetadataDisplayField.PROGRAM_SLOT_CAPABILITY, ProgramSlotCapability.entries.map(Enum<*>::name))
        add(MetadataDisplayField.AXIAL_LOAD, AxialLoadLevel.entries.map(Enum<*>::name))
        add(MetadataDisplayField.LATERALITY, FatigueLaterality.entries.map(Enum<*>::name))
        add(MetadataDisplayField.METADATA_CONFIDENCE, MetadataConfidence.entries.map(Enum<*>::name))
        add(MetadataDisplayField.PROGRAM_SLOT, ProgramSlotId.entries.map(Enum<*>::name))
        add(
            MetadataDisplayField.ANALYSIS_ELIGIBILITY,
            AnalysisEligibility.entries.map(Enum<*>::name)
        )

        val assets = context(Locale.KOREAN).assets
        assets.open(CANONICAL_ASSET).bufferedReader().useLines { lines ->
            val rows = lines.iterator()
            val headers = SeedData.parseCsvLine(rows.next())
            val indexes = CSV_FIELDS.mapValues { (_, column) -> headers.indexOf(column) }
            rows.forEachRemaining { line ->
                val columns = SeedData.parseCsvLine(line)
                indexes.forEach { (field, index) ->
                    if (index in columns.indices) {
                        add(field, columns[index].split("|"))
                    }
                }
            }
        }
        return values
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }

    private companion object {
        const val CANONICAL_ASSET = "metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
        const val DISPLAY_ASSET = "metadata/canonical_v1/metadata_display_labels_ko.csv"
        val RAW_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*_[A-Z0-9_]+")
        val LATIN_TOKEN = Regex("[A-Za-z][A-Za-z0-9]*")
        val CSV_FIELDS = mapOf(
            MetadataDisplayField.ACTIVITY_KIND to "currentActivityKind",
            MetadataDisplayField.PLANNING_ELIGIBILITY to "currentPlanningEligibility",
            MetadataDisplayField.MOVEMENT_FAMILY to "movementFamily",
            MetadataDisplayField.MOVEMENT_SUBTYPE to "movementSubtype",
            MetadataDisplayField.PROGRAM_SLOT to "programSlot",
            MetadataDisplayField.REDUNDANCY_GROUP to "redundancyGroup",
            MetadataDisplayField.PROGRESS_METRIC to "progressMetricType",
            MetadataDisplayField.STRENGTH_PROGRESSION_GROUP to "strengthProgressionGroup",
            MetadataDisplayField.ANALYSIS_ELIGIBILITY to "analysisEligibility",
            MetadataDisplayField.PRIMARY_STRESS_PROFILE to "primaryStressProfile",
            MetadataDisplayField.SECONDARY_STRESS to "secondaryStressTags",
            MetadataDisplayField.TENDON_STRESS to "tendonStressTags",
            MetadataDisplayField.LIGAMENT_JOINT_STABILITY to "ligamentJointStabilityStressTags",
            MetadataDisplayField.JOINT_IMPACT to "jointImpactStressTags",
            MetadataDisplayField.COGNITIVE_STRESS to "cognitiveStressTags",
            MetadataDisplayField.SPORT_CONTEXT to "sportContextTags",
            MetadataDisplayField.RECOVERY_DECAY to "recoveryDecayProfile",
            MetadataDisplayField.STRESS_LEVEL to "stressMagnitudeHint",
            MetadataDisplayField.BADMINTON_TRANSFER_LEVEL to "badmintonTransferLevel",
            MetadataDisplayField.BADMINTON_TRANSFER_TYPE to "badmintonTransferType",
            MetadataDisplayField.BADMINTON_SKILL_TARGET to "badmintonSkillTargets",
            MetadataDisplayField.BADMINTON_PHYSICAL_QUALITY to "badmintonPhysicalQualities",
            MetadataDisplayField.TRANSFER_CONFIDENCE to "transferConfidence",
            MetadataDisplayField.SOURCE_CONFIDENCE to "sourceConfidenceLevel",
            MetadataDisplayField.FINAL_SOURCE_STATUS to "finalSourceStatus",
            MetadataDisplayField.NEUROMUSCULAR_STRESS to "neuromuscularStressLevel",
            MetadataDisplayField.SYSTEMIC_MUSCULAR_STRESS to "systemicMuscularStressLevel",
            MetadataDisplayField.LOCAL_MUSCULAR_STRESS to "localMuscularStressLevel",
            MetadataDisplayField.JOINT_TENDON_IMPACT_STRESS to "jointTendonImpactStressLevel",
            MetadataDisplayField.MOVEMENT_FOCUS_DEMAND to "movementFocusDemandLevel",
            MetadataDisplayField.RECOVERY_DURATION to "recoveryDurationClass"
        )
    }
}
