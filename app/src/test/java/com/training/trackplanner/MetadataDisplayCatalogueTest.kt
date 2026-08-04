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
        assertTrue("expected a complete catalogue", korean.registeredCount() >= 900)
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
        val RAW_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*_[A-Z0-9_]+")
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
