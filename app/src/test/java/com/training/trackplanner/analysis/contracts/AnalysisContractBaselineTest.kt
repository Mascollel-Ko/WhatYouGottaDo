package com.training.trackplanner.analysis.contracts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonTransferMetadataMapper
import com.training.trackplanner.analysis.badminton.BadmintonTransferType
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.lab.MuscleLoadInputBuilder
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramCandidate
import com.training.trackplanner.data.ProgramExerciseRole
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonGenerator
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramTrainingSlot
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataAssetLoader
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.SlotCapabilityResolver
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
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
import java.util.StringTokenizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisContractBaselineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tissueCatalog by lazy { TissueRcvAssetRepository.fromAssets(context).catalog }

    @Test
    fun baselineAssetMatchesCurrentBuiltInOracle() {
        val rendered = renderCurrentOracle()
        val generated = repoFile("app/build/generated/analysis-contract/analysis_contract_baseline_v1.csv")
        requireNotNull(generated.parentFile).mkdirs()
        generated.writeText(rendered, Charsets.UTF_8)

        val asset = repoFile("app/src/main/assets/${AnalysisContractAssetLoader.ASSET_PATH}")
        check(asset.isFile) {
            "Baseline asset is missing. Copy ${generated.absolutePath} to ${asset.absolutePath}."
        }
        val committed = asset.readText(Charsets.UTF_8)
        val differences = AnalysisContractShadowParity.compare(
            oldOracle = AnalysisContractAssetLoader.parse(rendered),
            newRelations = AnalysisContractAssetLoader.parse(committed)
        )
        assertTrue(
            "Analysis contract shadow parity failed:\n${differences.take(20).joinToString("\n")}",
            differences.isEmpty()
        )
        assertEquals(committed.normalizeLines(), rendered.normalizeLines())
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
        val exercises = legacyExercises()
        val repository = AnalysisContractAssetLoader(context).load()

        assertEquals(224, exercises.size)
        assertEquals(exercises.map(Exercise::stableKey).toSet(), repository.all().map { it.exerciseStableKey }.toSet())
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

    private fun renderCurrentOracle(): String {
        val exercises = legacyExercises().sortedBy(Exercise::stableKey)
        val metadataByKey = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(
            legacyMetadataFile().readText(Charsets.UTF_8)
        ).associateBy(RuntimeExerciseMetadata::stableKey)
        return buildString {
            appendLine(HEADER)
            exercises.forEach { exercise ->
                val metadata = requireNotNull(metadataByKey[exercise.stableKey])
                rowsFor(exercise, metadata).forEach { row ->
                    appendLine(row.joinToString(",") { value -> value.csvValue() })
                }
            }
        }
    }

    private fun rowsFor(exercise: Exercise, metadata: RuntimeExerciseMetadata): List<List<String>> {
        val features = ExerciseAnalysisMapper.fromExercise(exercise, metadata)
        val slotProfile = SlotCapabilityResolver.DEFAULT.resolve(exercise, metadata)
        val candidate = ProgramCandidate(exercise, metadata, canonical = true, slotCapabilities = slotProfile)
        val entry = WorkoutEntry(
            id = 1,
            date = PROBE_DATE.toString(),
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            rpe = 8.0,
            completedAt = 1L
        )
        val sets = (1..3).map { index ->
            WorkoutSet(
                id = index.toLong(),
                entryId = entry.id,
                setIndex = index,
                reps = 8,
                weightKg = 20.0,
                seconds = 600,
                confirmed = true,
                rpe = 8.0
            )
        }
        val fatigue = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata))).calculate(
            targetDate = PROBE_DATE,
            exercises = listOf(exercise),
            entriesWithSets = listOf(WorkoutEntryWithSets(entry, sets)),
            initialProfile = null
        )
        val contribution = fatigue.recordContributions.single()
        val muscle = MuscleLoadInputBuilder.contributions(exercise, entry, metadata)
        val badmintonType = BadmintonTransferMetadataMapper.transferType(features)
        val badmintonAxes = BadmintonTransferMetadataMapper.transferAxes(features)
        val badmintonFatigue = BadmintonTransferMetadataMapper.fatigueCost(features)
        val rows = mutableListOf<List<String>>()

        fun add(
            type: String,
            relationId: String,
            role: String = "",
            qualifier: String = "",
            coefficient: Double = 0.0,
            confidence: Double = 1.0,
            status: String = ""
        ) {
            rows += listOf(
                exercise.stableKey,
                type,
                relationId,
                role,
                qualifier,
                coefficient.contractNumber(),
                confidence.contractNumber(),
                AnalysisSourceStatus.MIGRATED_CURRENT_BEHAVIOR.name,
                AnalysisContractAssetLoader.CONTRACT_VERSION,
                status
            )
        }

        add("CAPABILITY", "OFI", status = AnalysisCapabilityStatus.ENABLED.name)
        add(
            "CAPABILITY",
            "PROGRAM_GENERATION",
            status = when {
                metadata.planningEligibility != "PROGRAM_SELECTABLE" -> AnalysisCapabilityStatus.DISABLED
                slotProfile.allCapabilities.isEmpty() -> AnalysisCapabilityStatus.INCOMPLETE
                else -> AnalysisCapabilityStatus.ENABLED
            }.name
        )
        add(
            "CAPABILITY",
            "MUSCLE_LOAD",
            status = if (muscle.isEmpty()) AnalysisCapabilityStatus.DISABLED.name else AnalysisCapabilityStatus.ENABLED.name
        )
        add(
            "CAPABILITY",
            "BADMINTON_TRANSFER",
            status = if (badmintonType == BadmintonTransferType.NONE && badmintonAxes.isEmpty()) {
                AnalysisCapabilityStatus.DISABLED.name
            } else {
                AnalysisCapabilityStatus.ENABLED.name
            }
        )
        add(
            "CAPABILITY",
            "CONNECTIVE_TISSUE",
            status = if (exercise.stableKey in tissueCatalog.exerciseStableKeys) {
                AnalysisCapabilityStatus.ENABLED.name
            } else {
                AnalysisCapabilityStatus.INCOMPLETE.name
            }
        )

        add("OFI_DOSE", metadata.progressBehavior.name, qualifier = PROBE_INPUT_POLICY)
        mapOf(
            OfiAxisId.HIGH_FORCE_NEURAL to contribution.axes.highForceNeural,
            OfiAxisId.SYSTEMIC_MUSCULAR to contribution.axes.systemicMuscular,
            OfiAxisId.LOCAL_MUSCULAR to contribution.axes.localMuscular,
            OfiAxisId.HIGH_SPEED to contribution.axes.highSpeed,
            OfiAxisId.REACTIVE to contribution.axes.reactive
        ).forEach { (axis, value) ->
            add("OFI_AXIS", axis.name, qualifier = metadata.recoveryDurationClass, coefficient = value)
        }
        listOf(
            OfiComparisonPurpose.WORKLOAD_BASELINE to metadata.strengthProgressionGroup,
            OfiComparisonPurpose.LOCAL_REPEAT_DETECTION to metadata.redundancyGroup,
            OfiComparisonPurpose.STRENGTH_COMPARISON to metadata.strengthProgressionGroup
        ).forEach { (purpose, group) ->
            add("OFI_GROUP", group.ifBlank { "NOT_APPLICABLE" }, qualifier = purpose.name)
        }
        mapOf(
            OfiAxisId.HIGH_FORCE_NEURAL to fatigue.state.highForceNeuralScore,
            OfiAxisId.SYSTEMIC_MUSCULAR to fatigue.state.systemicMuscularScore,
            OfiAxisId.LOCAL_MUSCULAR to fatigue.state.localMuscularScore,
            OfiAxisId.HIGH_SPEED to fatigue.state.highSpeedScore,
            OfiAxisId.REACTIVE to fatigue.state.reactiveScore
        ).forEach { (axis, score) -> add("OFI_SCORE", axis.name, coefficient = score.toDouble()) }
        add(
            "OFI_SNAPSHOT",
            "SUMMARY",
            role = fatigue.state.readinessLabel.name,
            qualifier = fatigue.state.confidence.name,
            coefficient = fatigue.state.overallFatigueIndex.toDouble()
        )
        fatigue.state.cautionReasons.forEach { reason -> add("OFI_CAUTION", reason) }

        slotProfile.primary.forEach { slot ->
            add("PROGRAM_SLOT", slot.name, role = ProgramCapabilityRole.PRIMARY.name, coefficient = 1.0)
        }
        slotProfile.secondary.forEach { slot ->
            add("PROGRAM_SLOT", slot.name, role = ProgramCapabilityRole.SECONDARY.name, coefficient = 0.65)
        }
        slotProfile.weakMatches.forEach { slot ->
            add("PROGRAM_SLOT", slot.name, role = ProgramCapabilityRole.LIMITED.name, coefficient = 0.35)
        }
        ProgramExerciseRole.entries.forEach { role ->
            val eligible = ProgramTrainingSlot.entries.any { slot -> candidate.allowedForRole(slot, role) }
            add(
                "PROGRAM_ROLE",
                role.name,
                qualifier = if (eligible) ProgramRoleEligibility.ELIGIBLE.name else ProgramRoleEligibility.INELIGIBLE.name
            )
        }
        metadata.redundancyGroup.takeIf { it.isContractValue() }?.let { add("VARIANT_GROUP", it) }
        metadata.strengthProgressionGroup.takeIf { it.isContractValue() }?.let { add("PROGRESSION_GROUP", it) }

        muscle.toSortedMap(compareBy(Enum<*>::name)).forEach { (bucket, coefficient) ->
            add(
                "MUSCLE",
                bucket.name,
                role = when {
                    coefficient >= 0.75 -> MuscleContributionRole.PRIMARY
                    coefficient >= 0.5 -> MuscleContributionRole.SECONDARY
                    else -> MuscleContributionRole.STABILIZER
                }.name,
                coefficient = coefficient
            )
        }
        badmintonAxes.sortedBy(Enum<*>::name).forEach { axis ->
            add(
                "BADMINTON_TRANSFER",
                axis.name,
                qualifier = badmintonType.toContractLevel().name,
                coefficient = 1.0
            )
        }
        add("BADMINTON_FATIGUE_COST", badmintonFatigue.name)
        metadata.badmintonPhysicalQualities.values.sorted().forEach { quality ->
            add("PHYSICAL_QUALITY", quality, coefficient = 1.0)
        }
        metadata.movementFamily.takeIf { it.isContractValue() }?.let { add("MOVEMENT_PATTERN", it) }
        exercise.bodyRegion.contractTokens().forEach { add("BODY_REGION", it) }
        sequenceOf(metadata.activityKind).filter { it.isContractValue() }.forEach { add("MODALITY", it) }
        return rows
    }

    private fun BadmintonTransferType.toContractLevel(): ContractBadmintonTransferLevel = when (this) {
        BadmintonTransferType.DIRECT -> ContractBadmintonTransferLevel.DIRECT
        BadmintonTransferType.SUPPORTIVE -> ContractBadmintonTransferLevel.SUPPORTIVE
        BadmintonTransferType.GENERAL_STRENGTH -> ContractBadmintonTransferLevel.GENERAL
        BadmintonTransferType.LOW -> ContractBadmintonTransferLevel.LOW
        BadmintonTransferType.NONE -> ContractBadmintonTransferLevel.NONE
    }

    private fun String.contractTokens(): List<String> =
        StringTokenizer(this, "|,/;").toList().map(Any::toString)
            .map(String::trim)
            .filter { it.isContractValue() }
            .distinct()
            .sorted()

    private fun String.isContractValue(): Boolean =
        isNotBlank() && this != "NONE" && this != "NOT_APPLICABLE"

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

    private fun legacyMetadataFile(): File =
        repoFile("app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")

    private fun legacyExercises(): List<Exercise> {
        fun rows(asset: String): List<Map<String, String>> =
            context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { reader ->
                val parsed = reader.lineSequence().filter(String::isNotBlank).map(SeedData::parseCsvLine).toList()
                val header = parsed.first().map { it.removePrefix("\uFEFF") }
                parsed.drop(1).map { values ->
                    header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
                }
            }
        val images = rows("exercise_image_mapping.csv").associate { row ->
            row.getValue("stable_key") to (row.getValue("image_asset_name") to (row.getValue("needs_review") == "1"))
        }
        return SeedData.exercisesFromParsedRows(rows("training_settings_seed.csv")).map { exercise ->
            val image = images[exercise.stableKey] ?: return@map exercise
            exercise.copy(imageAssetName = image.first, needsReview = exercise.needsReview || image.second)
        }
    }

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
        const val PROBE_INPUT_POLICY = "THREE_CONFIRMED_SETS_20KG_8REPS_600SECONDS_RPE8"
        const val HEADER =
            "exerciseStableKey,relationType,relationId,role,qualifier,coefficient,confidence,sourceStatus,version,status"
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
