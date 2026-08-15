package com.training.trackplanner.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramAutoBuilderParityMatrixTest {
    @Test
    fun publicBuilderMatchesPreAuthorityHardeningMatrix() {
        val rendered = renderMatrix()
        val generated = repoFile("app/build/generated/program-authority/program_auto_builder_parity_matrix_v1.csv")
        requireNotNull(generated.parentFile).mkdirs()
        generated.writeText(rendered, Charsets.UTF_8)

        val golden = repoFile("app/src/test/resources/program-authority/program_auto_builder_parity_matrix_v1.csv")
        check(golden.isFile) {
            "Program parity golden is missing. Copy ${generated.absolutePath} to ${golden.absolutePath}."
        }
        assertEquals(golden.readText(Charsets.UTF_8).normalizeLines(), rendered.normalizeLines())
    }

    private fun renderMatrix(): String {
        val exercises = loadSeedExercises() + Exercise(
            stableKey = "unknown_pallof_direct_core",
            name = "Pallof anti rotation direct core",
            category = "기능성운동",
            movementPattern = "CORE|ANTI_ROTATION",
            badmintonTransferStrength = "DIRECT",
            activityKind = "TRAINING_EXERCISE",
            planningEligibility = "PROGRAM_SELECTABLE"
        )
        return buildString {
            appendLine("durationWeeks,weeklyDays,sessionMinutes,badmintonRatio,outputSha256")
            DURATIONS.forEach { duration ->
                WEEKLY_DAYS.forEach { days ->
                    SESSION_MINUTES.forEach { minutes ->
                        BADMINTON_RATIOS.forEach { ratio ->
                            val skeleton = ProgramAutoBuilder().build(
                                request = ProgramSkeletonRequest(
                                    name = "parity",
                                    goal = ProgramGoal.BADMINTON_SUPPORT,
                                    weeklyTrainingDays = days,
                                    sessionMinutes = minutes,
                                    availableEquipment = emptySet(),
                                    excludedExerciseText = "",
                                    badmintonTransferRatio = ratio,
                                    sportStrengthRatio = "AUTO",
                                    periodizationType = ProgramPeriodizationType.AUTO,
                                    durationWeeks = duration
                                ),
                                exercises = exercises
                            )
                            check(skeleton.items.all { item -> ProgramCandidateAuthority.allows(item.exerciseStableKey) })
                            check(skeleton.items.none { item -> item.exerciseStableKey == "unknown_pallof_direct_core" })
                            appendLine("$duration,$days,$minutes,$ratio,${skeleton.fingerprint()}")
                        }
                    }
                }
            }
        }
    }

    private fun GeneratedProgramSkeleton.fingerprint(): String {
        val canonical = buildString {
            appendLine("${periodizationType.name}|$templateId|$durationDays")
            weekPlans.forEach { week ->
                appendLine(
                    listOf(
                        week.weekIndex,
                        week.weekType,
                        week.volumeMultiplier,
                        week.intensityMultiplier,
                        week.targetRpeMin,
                        week.targetRpeMax,
                        week.deloadFlag
                    ).joinToString("|")
                )
            }
            items.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex))
                .forEach { item ->
                    appendLine(
                        listOf(
                            item.weekNumber,
                            item.dayOfWeek,
                            item.orderIndex,
                            item.exerciseStableKey,
                            item.selectionRole,
                            item.trainingSlot,
                            item.prescription,
                            item.setCount,
                            item.reps,
                            item.seconds
                        ).joinToString("|")
                    )
                }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun loadSeedExercises(): List<Exercise> {
        val lines = repoFile("app/src/main/assets/metadata/canonical_v1/exercise_bootstrap.csv")
            .readLines(Charsets.UTF_8)
            .filter(String::isNotBlank)
        val header = SeedData.parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1)
            .map(SeedData::parseCsvLine)
            .map { values -> header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap() }
            .filter { row -> row["isActive"] == "YES" && row["planningEligibility"] != "HISTORY_ONLY" }
            .map { row ->
                Exercise(
                    name = row["name"].orEmpty(),
                    category = row["category"].orEmpty(),
                    defaultRestSeconds = row["defaultRestSeconds"]?.toIntOrNull() ?: 60,
                    stableKey = row["stableKey"].orEmpty(),
                    equipment = row["equipmentTags"].orEmpty(),
                    isActive = true
                )
            }
    }

    private fun repoFile(path: String): File = File(repoRoot(), path)

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile, File::getParentFile)
            .first { directory -> File(directory, "settings.gradle.kts").isFile }

    private fun String.normalizeLines(): String = replace("\r\n", "\n").trimEnd() + "\n"

    private companion object {
        val DURATIONS = listOf(3, 4, 6, 8)
        val WEEKLY_DAYS = listOf(3, 4, 5, 7)
        val SESSION_MINUTES = listOf(30, 45, 60)
        val BADMINTON_RATIOS = listOf(0.0, 0.30, 0.50, 0.70)
    }
}
