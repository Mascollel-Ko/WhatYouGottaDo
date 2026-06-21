package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramBuilderCanonicalExamplesTest {
    @Test
    fun canonicalCoverageDiagnosticScansAllRowsWithoutLegacyFallback() {
        val exercises = loadSeedExercises()
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        val catalog = RuntimeExerciseMetadataCatalog.of(metadata)

        val diagnostics = ProgramSlotCoverageDiagnostics().analyze(exercises, catalog)

        assertEquals(215, exercises.size)
        assertEquals(ProgramSlotId.entries.size, diagnostics.size)
        assertTrue(diagnostics.all { it.legacyFallbackMatchCount == 0 })
        assertTrue(diagnostics.all { it.nameFallbackMatchCount == 0 })
        println("slot,candidates,strongPrimary,secondary,weakMetadata,legacyFallback,nameFallback,strength")
        diagnostics.forEach { row ->
            println(
                listOf(
                    row.slot.name,
                    row.candidateCount,
                    row.strongMetadataMatchCount,
                    row.secondaryMetadataMatchCount,
                    row.weakMetadataMatchCount,
                    row.legacyFallbackMatchCount,
                    row.nameFallbackMatchCount,
                    row.coverageStrength.name
                ).joinToString(",")
            )
        }
        setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
        ).forEach { slot ->
            assertTrue("Expected canonical candidates for $slot", diagnostics.first { it.slot == slot }.candidateCount > 0)
        }
    }

    @Test
    fun requiredExamplesGenerateFromCanonicalAssets() {
        val exercises = loadSeedExercises()
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        val catalog = RuntimeExerciseMetadataCatalog.of(metadata)
        assertEquals(215, exercises.size)
        assertEquals(215, catalog.size)

        (representativeExamples() + Example("7 days / 8 weeks / 80:20 fallback", 7, 8, 45, 0.80))
            .forEach { example ->
            val result = build(example, exercises, catalog)
            assertEquals(example.weeks, result.weekPlans.size)
            assertTrue(result.items.isNotEmpty())
            assertTrue(result.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { it.size <= 7 })
            assertTrue(
                "${example.label} must not contain hard validation failures: ${result.validationIssues}",
                result.validationDetails.none { it.severity == ProgramValidationSeverity.HARD }
            )
            if (example.days == 7) {
                assertTrue(result.items.groupBy { it.weekNumber }.values.all { weekItems ->
                    weekItems.groupBy { it.dayOfWeek }.count { (_, rows) -> rows.first().dayIntensity == "HARD" } <= 2
                })
            }
            val weeklySignatures = result.items.groupBy { it.weekNumber }.values.map { week ->
                week.sortedWith(compareBy(ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex))
                    .joinToString("|") { it.exerciseName }
            }
            assertTrue("${example.label} must vary across weeks", weeklySignatures.distinct().size > 1)
            println(example.render(result))
        }
    }

    @Test
    fun representativeTemplateOutputAuditWritesDetailedSamples() {
        val exercises = loadSeedExercises()
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        val catalog = RuntimeExerciseMetadataCatalog.of(metadata)
        val results = representativeExamples().associateWith { example -> build(example, exercises, catalog) }
        val outputDir = repositoryOutputDir().apply { mkdirs() }

        File(outputDir, "v0.3.5.6_representative_template_samples.csv").writeText(
            sampleCsv(results),
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.6_representative_template_samples.md").writeText(
            sampleMarkdown(results),
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.6_overhead_rotation_candidate_audit.csv").writeText(
            candidateAuditCsv(exercises, catalog),
            Charsets.UTF_8
        )

        results.forEach { (example, result) ->
            val requiredByWeek = result.items.filter(ProgramSkeletonItem::requiredTemplateAnchor)
                .groupBy(ProgramSkeletonItem::weekNumber)
            (1..example.weeks).forEach { week ->
                val slots = requiredByWeek[week].orEmpty().map(ProgramSkeletonItem::requestedTemplateSlot).toSet()
                assertTrue("${example.label} W$week missing squat anchor", ProgramSlotId.LOWER_SQUAT_PATTERN.name in slots)
                assertTrue("${example.label} W$week missing hinge anchor", ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name in slots)
                assertTrue("${example.label} W$week missing upper-pull anchor", ProgramSlotId.UPPER_PULL_ANCHOR.name in slots)
            }

            val weekTypes = result.weekPlans.associate { it.weekIndex to it.weekType }
            val rehabDominantSessions = result.items
                .groupBy { it.weekNumber to it.dayOfWeek }
                .filter { (key, rows) ->
                    weekTypes[key.first] !in setOf(ProgramWeekType.DELOAD.name, ProgramWeekType.FINAL_DELOAD.name) &&
                        rows.first().trainingSlot !in RECOVERY_TRAINING_SLOTS &&
                        rows.count(ProgramSkeletonItem::rehabLikeActivation) > 1
                }
            assertTrue("${example.label} has rehab-like session dominance", rehabDominantSessions.isEmpty())
            assertTrue(
                "${example.label} rehab-like share exceeds 15%",
                result.items.count(ProgramSkeletonItem::rehabLikeActivation).toDouble() / result.items.size <= 0.15
            )

            twoWeekFootworkShares(result).forEach { share ->
                assertTrue("${example.label} footwork/COD/reactive share $share exceeds 45%", share <= 0.45)
            }
        }

        results.filterKeys { it.requiresOverhead }.values.forEach { result ->
            val selected = result.items.filter { it.requestedTemplateSlot == ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name }
            assertTrue("${result.templateId} did not fill athletic overhead press", selected.isNotEmpty())
            assertFalse(selected.any(ProgramSkeletonItem::rehabLikeActivation))
        }
        results.filterKeys { it.requiresRotation }.values.forEach { result ->
            val selected = result.items.filter { it.requestedTemplateSlot == ProgramSlotId.ROTATIONAL_KINETIC_CHAIN.name }
            assertTrue("${result.templateId} did not fill rotational kinetic chain", selected.isNotEmpty())
            assertFalse(selected.any(ProgramSkeletonItem::rehabLikeActivation))
            selected.groupBy(ProgramSkeletonItem::stableKey).values.forEach { rows ->
                val weeks = rows.map(ProgramSkeletonItem::weekNumber).distinct().sorted()
                assertTrue(
                    "${result.templateId} repeats a scarce rotation candidate in consecutive weeks",
                    weeks.zipWithNext().all { (first, second) -> second - first >= 2 }
                )
            }
        }
    }

    private fun build(
        example: Example,
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = example.label,
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = example.days,
            sessionMinutes = example.minutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = example.ratio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = example.weeks
        ),
        exercises = exercises,
        history = emptyList(),
        runtimeMetadataCatalog = catalog
    )

    private fun representativeExamples(): List<Example> = listOf(
        Example("3 days / 4 weeks / 70:30 / Standard", 3, 4, 60, 0.70),
        Example("4 days / 4 weeks / 80:20 / Footwork-COD", 4, 4, 60, 0.80, requiresOverhead = true),
        Example("4 days / 4 weeks / 50:50 / Hybrid", 4, 4, 60, 0.50, requiresOverhead = true, requiresRotation = true),
        Example("5 days / 8 weeks / 70:30 / Standard", 5, 8, 60, 0.70, requiresOverhead = true, requiresRotation = true),
        Example("5 days / 8 weeks / 30:70 / Strength-biased", 5, 8, 60, 0.30, requiresOverhead = true, requiresRotation = true)
    )

    private fun sampleCsv(results: Map<Example, GeneratedProgramSkeleton>): String = buildString {
        appendLine("templateId,example,week,weekType,day,order,requestedSlot,requiredAnchor,role,exerciseName,stableKey,rehabLike,badmintonTransfer,stressMagnitude,capabilitySource,capabilityConfidence")
        results.forEach { (example, result) ->
            val weekTypes = result.weekPlans.associate { it.weekIndex to it.weekType }
            result.items.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex))
                .forEach { item ->
                    appendLine(listOf(
                        result.templateId,
                        example.label,
                        item.weekNumber,
                        weekTypes[item.weekNumber].orEmpty(),
                        item.dayOfWeek,
                        item.orderIndex,
                        item.requestedTemplateSlot,
                        item.requiredTemplateAnchor,
                        item.selectionRole,
                        item.exerciseName,
                        item.stableKey,
                        item.rehabLikeActivation,
                        item.badmintonTransferLevel,
                        item.stressMagnitudeHint,
                        item.slotCapabilitySource,
                        item.slotCapabilityConfidence
                    ).joinToString(",") { csv(it.toString()) })
                }
        }
    }

    private fun sampleMarkdown(results: Map<Example, GeneratedProgramSkeleton>): String = buildString {
        appendLine("# v0.3.5.6 Representative Template Samples")
        appendLine()
        results.forEach { (example, result) ->
            val rehabCount = result.items.count(ProgramSkeletonItem::rehabLikeActivation)
            val footworkShares = twoWeekFootworkShares(result)
            val overhead = result.items.filter { it.requestedTemplateSlot == ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name }
                .map(ProgramSkeletonItem::exerciseName).distinct()
            val rotation = result.items.filter { it.requestedTemplateSlot == ProgramSlotId.ROTATIONAL_KINETIC_CHAIN.name }
                .map(ProgramSkeletonItem::exerciseName).distinct()
            appendLine("## ${example.label}")
            appendLine()
            appendLine("- templateId: `${result.templateId}`")
            appendLine("- total items: ${result.items.size}")
            appendLine("- rehab-like items: $rehabCount")
            appendLine("- 2-week footwork/COD/reactive shares: ${footworkShares.joinToString { "%.1f%%".format(it * 100) }}")
            appendLine("- overhead selections: ${overhead.ifEmpty { listOf("NONE") }.joinToString()}")
            appendLine("- rotation selections: ${rotation.ifEmpty { listOf("NONE") }.joinToString()}")
            appendLine("- hard issues: ${result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }}")
            appendLine()
            result.weekPlans.forEach { week ->
                appendLine("### W${week.weekIndex} ${week.weekType}")
                result.items.filter { it.weekNumber == week.weekIndex }
                    .groupBy(ProgramSkeletonItem::dayOfWeek)
                    .toSortedMap()
                    .forEach { (day, rows) ->
                        appendLine("- D$day: ${rows.sortedBy(ProgramSkeletonItem::orderIndex).joinToString { "${it.exerciseName} [${it.requestedTemplateSlot.ifBlank { it.selectionRole }}]" }}")
                    }
            }
            appendLine()
        }
    }

    private fun candidateAuditCsv(
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog
    ): String = buildString {
        appendLine("slot,exerciseName,stableKey,credit,primary,secondary,weak,source,confidence,warnings")
        val policy = CoverageAccountingPolicy.DEFAULT
        val resolver = SlotCapabilityResolver.DEFAULT
        listOf(
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN
        ).forEach { slot ->
            exercises.forEach { exercise ->
                val metadata = catalog.resolve(exercise)
                if (metadata?.planningEligibility != "PROGRAM_SELECTABLE") return@forEach
                val profile = resolver.resolve(exercise, metadata)
                val credit = policy.credit(profile, slot)
                if (credit != CoverageCredit.NONE) {
                    appendLine(listOf(
                        slot.name,
                        exercise.name,
                        exercise.stableKey,
                        credit.name,
                        profile.primary.joinToString("|"),
                        profile.secondary.joinToString("|"),
                        profile.weakMatches.joinToString("|"),
                        profile.source.name,
                        profile.confidence.name,
                        profile.warnings.joinToString("|")
                    ).joinToString(",") { csv(it) })
                }
            }
        }
    }

    private fun twoWeekFootworkShares(result: GeneratedProgramSkeleton): List<Double> =
        (1..result.weekPlans.size step 2).map { startWeek ->
            val rows = result.items.filter { it.weekNumber in startWeek..minOf(startWeek + 1, result.weekPlans.size) }
            val transferCount = rows.count {
                it.requestedTemplateSlot in setOf(
                    ProgramSlotId.BADMINTON_DECEL_COD.name,
                    ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
                    ProgramSlotId.POWER_REACTIVE_LOW_VOLUME.name
                )
            }
            if (rows.isEmpty()) 0.0 else transferCount.toDouble() / rows.size
        }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun repositoryOutputDir(): File = if (File("app/src/main").exists()) {
        File("outputs")
    } else {
        File("../outputs")
    }

    private fun Example.render(result: GeneratedProgramSkeleton): String = buildString {
        appendLine("PROGRAM EXAMPLE: $label")
        result.weekPlans.forEach { week ->
            val weekItems = result.items.filter { it.weekNumber == week.weekIndex }
            val slots = weekItems.groupBy { it.dayOfWeek }.toSortedMap().entries.joinToString("; ") { (_, rows) ->
                "${rows.first().trainingSlot}=[${rows.take(3).joinToString { it.exerciseName }}]"
            }
            appendLine("W${week.weekIndex} ${week.weekType}: $slots")
        }
        appendLine("validationIssues=${result.validationIssues}")
    }

    private fun loadSeedExercises(): List<Exercise> {
        val lines = seedFile().readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val header = parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1).map(::parseCsvLine).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }.filter { it["row_type"] == "exercise" }.mapIndexed { index, row ->
            Exercise(
                id = (index + 1).toLong(),
                name = row["exercise_name"].orEmpty(),
                category = row["category"].orEmpty(),
                defaultRestSeconds = row["default_rest_seconds"]?.toIntOrNull() ?: 60,
                stableKey = row["stable_key"].orEmpty(),
                equipment = row["equipment_tags"].orEmpty(),
                isActive = true
            )
        }
    }

    private fun seedFile(): File = existingFile("src/main/assets/training_settings_seed.csv")
    private fun canonicalFile(): File = existingFile(
        "src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
    )

    private fun existingFile(relative: String): File = sequenceOf(
        File(relative),
        File("app/$relative")
    ).firstOrNull(File::exists) ?: error("Missing test asset: $relative")

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private data class Example(
        val label: String,
        val days: Int,
        val weeks: Int,
        val minutes: Int,
        val ratio: Double,
        val requiresOverhead: Boolean = false,
        val requiresRotation: Boolean = false
    )

    private companion object {
        val RECOVERY_TRAINING_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB.name,
            ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
            ProgramTrainingSlot.MICRO_RECOVERY.name
        )
    }
}
