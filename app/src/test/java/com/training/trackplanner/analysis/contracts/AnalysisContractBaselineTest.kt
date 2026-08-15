package com.training.trackplanner.analysis.contracts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonGenerator
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadataAssetLoader
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisContractBaselineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun canonicalOfiAxisAuthorityPreservesHistoricalProbeLoads() {
        val historical = AnalysisContractAssetLoader(context).load()
        val currentProfiles = CanonicalExerciseMetadataRepository(context).ofiAxisProfiles()

        assertEquals(224, historical.size)
        historical.all().forEach { relations ->
            val current = requireNotNull(currentProfiles[relations.exerciseStableKey])
            val expected = relations.ofiAxisContributions.associate { contribution ->
                contribution.axisId to contribution.coefficient
            }
            assertEquals(expected.getValue(OfiAxisId.HIGH_FORCE_NEURAL), current.highForceNeural, 0.0001)
            assertEquals(expected.getValue(OfiAxisId.SYSTEMIC_MUSCULAR), current.systemicMuscular, 0.0001)
            assertEquals(expected.getValue(OfiAxisId.LOCAL_MUSCULAR), current.localMuscular, 0.0001)
            assertEquals(expected.getValue(OfiAxisId.HIGH_SPEED), current.highSpeed, 0.0001)
            assertEquals(expected.getValue(OfiAxisId.REACTIVE), current.reactive, 0.0001)
        }
    }

    @Test
    fun programGoldenMatchesCurrentProductionOracle() {
        val exercises = SeedData.exercises(context)
        val catalog = RuntimeExerciseMetadataCatalog.of(
            RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(
                canonicalMetadataFile().readText(Charsets.UTF_8)
            )
        )
        val rendered = buildString {
            appendLine(PROGRAM_HEADER)
            PROGRAM_SCENARIOS.forEach { scenario ->
                val result = ProgramSkeletonGenerator().generate(
                    request = scenario.request,
                    exercises = exercises,
                    history = emptyList(),
                    today = PROBE_DATE,
                    runtimeMetadataCatalog = catalog
                )
                appendLine(
                    listOf(
                        scenario.id,
                        "SUMMARY",
                        "",
                        "",
                        "",
                        "",
                        "",
                        result.periodizationType.name,
                        result.templateId,
                        result.durationDays.toString(),
                        "",
                        "",
                        "",
                        ""
                    ).joinToString(",") { it.csvValue() }
                )
                result.weekPlans.forEach { week ->
                    appendLine(
                        listOf(
                            scenario.id,
                            "WEEK",
                            week.weekIndex.toString(),
                            "",
                            "",
                            "",
                            "",
                            week.weekType,
                            "",
                            week.volumeMultiplier.contractNumber(),
                            week.intensityMultiplier.contractNumber(),
                            week.targetRpeMin.contractNumber(),
                            week.targetRpeMax.contractNumber(),
                            week.deloadFlag.toString()
                        ).joinToString(",") { it.csvValue() }
                    )
                }
                result.items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
                    .forEach { item ->
                        appendLine(
                            listOf(
                                scenario.id,
                                "ITEM",
                                item.weekNumber.toString(),
                                item.dayOfWeek.toString(),
                                item.orderIndex.toString(),
                                item.exerciseStableKey,
                                item.selectionRole,
                                item.trainingSlot,
                                item.prescription,
                                item.setCount.toString(),
                                item.reps.toString(),
                                item.weightKg.contractNumber(),
                                item.seconds.toString(),
                                item.restSeconds.toString()
                            ).joinToString(",") { it.csvValue() }
                        )
                    }
            }
        }
        val generated = repoFile("app/build/generated/analysis-contract/analysis_contract_program_golden_v1.csv")
        requireNotNull(generated.parentFile).mkdirs()
        generated.writeText(rendered, Charsets.UTF_8)
        val golden = repoFile("app/src/test/resources/analysis-contract/analysis_contract_program_golden_v1.csv")
        check(golden.isFile) {
            "Program golden is missing. Copy ${generated.absolutePath} to ${golden.absolutePath}."
        }
        assertEquals(golden.readText(Charsets.UTF_8).normalizeLines(), rendered.normalizeLines())
    }

    @Test
    fun builtInRelationsCoverEveryStableKeyAndAnalysis() {
        val repository = AnalysisContractAssetLoader(context).load()

        assertEquals(224, repository.size)
        repository.all().forEach { relations ->
            assertEquals(REQUIRED_ANALYSES, relations.capabilities.map { it.analysisTypeId }.toSet())
            assertNotNull(relations.ofiDoseProfile)
            assertEquals(OfiAxisId.entries.toSet(), relations.ofiAxisContributions.map { it.axisId }.toSet())
            assertEquals(OfiAxisId.entries.toSet(), relations.ofiGoldenSnapshot?.roundedAxisScores?.keys)
            assertTrue(relations.capabilities.all {
                it.sourceStatus == AnalysisSourceStatus.MIGRATED_CURRENT_BEHAVIOR
            })
        }
    }

    @Test
    fun userExerciseProjectionPreservesIncompleteStateWithoutGuessing() {
        val metadata = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(
            canonicalMetadataFile().readText(Charsets.UTF_8)
        ).first().copy(
            stableKey = "user_ex_contract_test",
            exerciseName = "Squat-looking user label",
            movementFamily = "UNKNOWN_MOVEMENT",
            programSlot = "UNKNOWN_SLOT",
            badmintonTransferType = MetadataTokenField.parse("NONE"),
            badmintonPhysicalQualities = MetadataTokenField.parse("NONE"),
            badmintonTransferLevel = "NONE"
        )

        val projected = UserExerciseAnalysisContractProjector.project(metadata)

        assertEquals(
            AnalysisCapabilityStatus.INCOMPLETE,
            projected.capabilities.single { it.analysisTypeId == AnalysisTypeId.PROGRAM_GENERATION }.status
        )
        assertEquals(
            AnalysisCapabilityStatus.INCOMPLETE,
            projected.capabilities.single { it.analysisTypeId == AnalysisTypeId.OFI }.status
        )
        assertTrue(projected.programSlotCapabilities.isEmpty())
        assertTrue(projected.muscleContributions.isEmpty())
        assertFalse(projected.movementPatterns.any { it.movementPatternId == "SQUAT" })
    }

    private fun Double.contractNumber(): String =
        String.format(Locale.ROOT, "%.12f", this).trimEnd('0').trimEnd('.').ifBlank { "0" }

    private fun String.csvValue(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }

    private fun canonicalMetadataFile(): File =
        repoFile("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory -> directory.parentFile?.takeUnless { it == directory } }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${current.absolutePath}.")
        return File(root, path)
    }

    private fun String.normalizeLines(): String =
        replace("\r\n", "\n").replace('\r', '\n').trimEnd()

    private companion object {
        val PROBE_DATE: LocalDate = LocalDate.of(2026, 1, 15)
        val REQUIRED_ANALYSES = setOf(
            AnalysisTypeId.OFI,
            AnalysisTypeId.PROGRAM_GENERATION,
            AnalysisTypeId.MUSCLE_LOAD,
            AnalysisTypeId.BADMINTON_TRANSFER,
            AnalysisTypeId.CONNECTIVE_TISSUE
        )
        const val PROGRAM_HEADER =
            "scenario,rowType,week,day,order,exerciseStableKey,role,slot,value,number1,number2,number3,number4,number5"
        val PROGRAM_SCENARIOS = listOf(
            ProgramScenario(
                "BADMINTON_5D_4W_45",
                ProgramSkeletonRequest(
                    name = "Badminton support 5d",
                    goal = ProgramGoal.BADMINTON_SUPPORT,
                    weeklyTrainingDays = 5,
                    sessionMinutes = 45,
                    availableEquipment = emptySet(),
                    excludedExerciseText = "",
                    badmintonTransferRatio = 0.60,
                    sportStrengthRatio = "AUTO",
                    periodizationType = ProgramPeriodizationType.AUTO,
                    durationWeeks = 4
                )
            ),
            ProgramScenario(
                "STRENGTH_3D_4W_45",
                ProgramSkeletonRequest(
                    name = "Strength 3d",
                    goal = ProgramGoal.STRENGTH,
                    weeklyTrainingDays = 3,
                    sessionMinutes = 45,
                    availableEquipment = emptySet(),
                    excludedExerciseText = "",
                    badmintonTransferRatio = 0.0,
                    sportStrengthRatio = "AUTO",
                    periodizationType = ProgramPeriodizationType.LINEAR_STRENGTH,
                    durationWeeks = 4
                )
            ),
            ProgramScenario(
                "BADMINTON_4D_8W_60",
                ProgramSkeletonRequest(
                    name = "Badminton support 4d",
                    goal = ProgramGoal.BADMINTON_SUPPORT,
                    weeklyTrainingDays = 4,
                    sessionMinutes = 60,
                    availableEquipment = emptySet(),
                    excludedExerciseText = "",
                    badmintonTransferRatio = 0.30,
                    sportStrengthRatio = "AUTO",
                    periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
                    durationWeeks = 8
                )
            )
        )
    }
}

private data class ProgramScenario(
    val id: String,
    val request: ProgramSkeletonRequest
)
