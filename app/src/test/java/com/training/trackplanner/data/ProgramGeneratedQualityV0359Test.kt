package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Locale

class ProgramGeneratedQualityV0359Test {
    @Test
    fun writesCoachStyleAuditForRepresentativeAndFallbackScenarios() {
        val exercises = loadSeedExercises()
        val catalog = RuntimeExerciseMetadataCatalog.of(
            ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        )
        val results = SCENARIOS.associateWith { scenario -> build(scenario, exercises, catalog) }
        val audits = results.mapValues { (_, result) -> audit(result) }
        val safety = listOf(
            SafetySample("ORANGE_4D_4W_80_20", build(SCENARIOS[1], exercises, catalog, fatigueState(70))),
            SafetySample("RED_4D_4W_80_20", build(SCENARIOS[1], exercises, catalog, fatigueState(90)))
        )
        val outputDir = repositoryOutputDir().apply { mkdirs() }

        File(outputDir, "v0.3.5.10_generated_program_quality_samples.json").writeText(
            renderJson(results, audits, safety),
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.10_generated_program_quality_audit_report.md").writeText(
            renderReport(results, audits, safety).trimEnd() + "\n",
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.10_fallback_role_slot_cleanup_report.md").writeText(
            renderCleanupReport(audits).trimEnd() + "\n",
            Charsets.UTF_8
        )

        assertEquals(14, results.size)
        assertEquals(239, exercises.size)
        assertEquals(239, catalog.size)
        assertTrue(results.values.all { it.items.isNotEmpty() })
        assertFalse(results.values.any { result -> result.items.any(ProgramSkeletonItem::directSportSession) })
        assertTrue(audits.values.all { it.hardIssueCodes.isEmpty() })
        assertTrue(audits.filterKeys { it.days >= 3 }.values.all { it.missingAnchorWindows == 0 })
        assertTrue(audits.values.all { it.normalRehabShare <= 0.15 })
        assertTrue(audits.values.all { it.maxTwoWeekFootworkShare <= 0.45 })
        assertTrue(audits.values.all { it.randomFunctionalFillerCount == 0 })
        assertTrue(audits.values.all { it.benchDominantOverheadSelections == 0 })
        assertTrue(audits.values.all { it.rotationInAnchorSlots == 0 })
        assertTrue(audits.values.all { it.timeBudgetIssues == 0 })
        val fallbackAudits = audits.filterKeys { it.id.startsWith("FALLBACK_") }.values
        assertEquals(0, fallbackAudits.sumOf(ScenarioAudit::fallbackBlankRequestedSlots))
        assertEquals(0, fallbackAudits.sumOf(ScenarioAudit::fallbackAnchorRoleViolations))
        assertEquals(0, fallbackAudits.sumOf(ScenarioAudit::fallbackCorePrehabRoleViolations))
        assertEquals(0, fallbackAudits.sumOf(ScenarioAudit::normalRecoveryRehabClusters))
        val redSafety = safety.single { sample -> sample.id.startsWith("RED_") }
        assertTrue(redSafety.result.items.none { it.isExplosiveTransfer() })
    }

    private fun audit(result: GeneratedProgramSkeleton): ScenarioAudit {
        val windows = (1..result.weekPlans.size step 2).map { start ->
            result.items.filter { it.weekNumber in start..minOf(start + 1, result.weekPlans.size) }
        }
        val missingAnchors = windows.sumOf { rows ->
            REQUIRED_ANCHOR_GROUPS.count { slots -> rows.none { it.hasCapability(slots) } }
        }
        val supportMissing = windows.sumOf { rows ->
            SUPPORT_GROUPS.count { slots -> rows.none { it.hasCapability(slots) } }
        }
        val normalItems = result.items.filterNot { item -> item.isRecoveryContext(result) }
        val rehabCount = normalItems.count(ProgramSkeletonItem::rehabLikeActivation)
        val footworkShares = windows.map { rows ->
            rows.count { it.hasCapability(FOOTWORK_SLOT_NAMES) }.toDouble() / rows.size.coerceAtLeast(1)
        }
        val sessionGroups = result.items.groupBy { it.weekNumber to it.dayOfWeek }
        val clutteredSessions = sessionGroups.count { (_, rows) ->
            rows.size >= 6 && rows.map(ProgramSkeletonItem::requestedTemplateSlot).filter(String::isNotBlank).distinct().size >= 6
        }
        val overheadRows = result.items.filter {
            it.hasCapability(setOf(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name))
        }
        val rotationRows = result.items.filter {
            it.hasCapability(setOf(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN.name))
        }
        val benchOverhead = overheadRows.count { item ->
            listOf(item.movementFamily, item.movementSubtype, item.metadataProgramSlot).any { value ->
                value.contains("BENCH", true) || value.contains("HORIZONTAL_PRESS", true) || value.contains("PUSH_UP", true)
            }
        }
        val roleMismatchCount = result.items.count { item ->
            item.selectionRole in setOf(ProgramExerciseRole.CORE.name, ProgramExerciseRole.PREHAB.name) &&
                item.isExplosiveTransfer()
        }
        val fallbackItems = if (result.representativeTemplate) emptyList() else result.items
        val fallbackAnchorViolations = fallbackItems.count { item ->
            item.selectionRole == ProgramExerciseRole.ANCHOR.name &&
                item.primarySlotCapabilities.none(ANCHOR_SLOT_NAMES::contains)
        }
        val fallbackCorePrehabViolations = fallbackItems.count { item ->
            when (item.selectionRole) {
                ProgramExerciseRole.CORE.name ->
                    !item.hasCapability(setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name)) ||
                        item.isExplosiveTransfer()
                ProgramExerciseRole.PREHAB.name ->
                    !item.hasCapability(setOf(ProgramSlotId.RECOVERY_PREHAB_LIGHT.name)) ||
                        item.isExplosiveTransfer()
                else -> false
            }
        }
        val normalRecoveryClusters = sessionGroups.count { (key, rows) ->
            val week = result.weekPlans.first { it.weekIndex == key.first }
            !week.deloadFlag && rows.first().trainingSlot == ProgramTrainingSlot.RECOVERY_WEAKPOINT.name &&
                rows.count(ProgramSkeletonItem::rehabLikeActivation) > 1
        }
        val hardIssues = result.validationDetails.filter { it.severity == ProgramValidationSeverity.HARD }
        val warnings = result.validationDetails.filter { it.severity == ProgramValidationSeverity.WARNING }
        return ScenarioAudit(
            items = result.items.size,
            sessions = sessionGroups.size,
            missingAnchorWindows = missingAnchors,
            missingSupportWindows = supportMissing,
            normalRehabShare = rehabCount.toDouble() / normalItems.size.coerceAtLeast(1),
            maxTwoWeekFootworkShare = footworkShares.maxOrNull() ?: 0.0,
            rotationExposure = rotationRows.size,
            athleticOverheadExposure = overheadRows.size,
            recoveryPrehabExposure = result.items.count {
                it.hasCapability(setOf(ProgramSlotId.RECOVERY_PREHAB_LIGHT.name))
            },
            directSportSessions = result.items.count(ProgramSkeletonItem::directSportSession),
            timeBudgetIssues = result.validationDetails.count { it.code == "SESSION_TIME_BUDGET" },
            rehabDominantSessions = result.validationDetails.count {
                it.code in setOf("REHAB_ACTIVATION_SESSION_CAP", "REHAB_ACTIVATION_DOMINANCE")
            },
            benchDominantOverheadSelections = benchOverhead,
            rotationInAnchorSlots = rotationRows.count { it.requestedTemplateSlot in CORE_ANCHOR_SLOT_NAMES },
            clutteredSessions = clutteredSessions,
            randomFunctionalFillerCount = roleMismatchCount,
            fallbackItems = fallbackItems.size,
            fallbackBlankRequestedSlots = fallbackItems.count { it.requestedTemplateSlot.isBlank() },
            fallbackResolvedRequestedSlots = fallbackItems.count { it.requestedTemplateSlot.isNotBlank() },
            fallbackWeakRequestedSlots = fallbackItems.count { it.requestedTemplateSlot in it.weakSlotCapabilities },
            fallbackAnchorRoleViolations = fallbackAnchorViolations,
            fallbackCorePrehabRoleViolations = fallbackCorePrehabViolations,
            normalRecoveryRehabClusters = normalRecoveryClusters,
            hardIssueCodes = hardIssues.map(ProgramValidationIssue::code),
            warningCodes = warnings.map(ProgramValidationIssue::code)
        )
    }

    private fun renderReport(
        results: Map<Scenario, GeneratedProgramSkeleton>,
        audits: Map<Scenario, ScenarioAudit>,
        safety: List<SafetySample>
    ): String = buildString {
        appendLine("# v0.3.5.10 Generated Program Quality Audit")
        appendLine()
        appendLine("## Scenario Summary")
        appendLine()
        appendLine("| Scenario | Template | Items | Missing anchors | Missing support | Rehab | Max footwork/COD | Rotation | Overhead | Hard | Warnings |")
        appendLine("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        results.forEach { (scenario, result) ->
            val row = audits.getValue(scenario)
            appendLine("| `${scenario.id}` | `${result.templateId}` | ${row.items} | ${row.missingAnchorWindows} | ${row.missingSupportWindows} | ${percent(row.normalRehabShare)} | ${percent(row.maxTwoWeekFootworkShare)} | ${row.rotationExposure} | ${row.athleticOverheadExposure} | ${row.hardIssueCodes.size} | ${row.warningCodes.size} |")
        }
        appendLine()
        appendLine("## Coach-Style Findings")
        appendLine()
        results.forEach { (scenario, result) ->
            val row = audits.getValue(scenario)
            appendLine("### ${scenario.label}")
            appendLine()
            appendLine("- Template: `${result.templateId}` (${if (result.representativeTemplate) "representative" else "fallback"})")
            appendLine("- Day identity: ${if (row.clutteredSessions == 0) "CLEAR" else "CLUTTER_WARNING (${row.clutteredSessions})"}")
            appendLine("- Major anchors: ${if (row.missingAnchorWindows == 0) "PRESERVED" else "MISSING_WINDOWS (${row.missingAnchorWindows})"}")
            appendLine("- Single-leg/trunk/scapular/calf support: ${if (row.missingSupportWindows == 0) "PRESENT" else "MISSING_WINDOWS (${row.missingSupportWindows})"}")
            appendLine("- Rehab-like dominance: ${if (row.rehabDominantSessions == 0 && row.normalRehabShare <= 0.15) "CONTROLLED" else "WARNING"} (${percent(row.normalRehabShare)})")
            appendLine("- Footwork/COD/reactive: ${if (row.maxTwoWeekFootworkShare <= 0.45) "CONTROLLED" else "OVER_LIMIT"} (${percent(row.maxTwoWeekFootworkShare)})")
            appendLine("- Rotation / athletic overhead / recovery-prehab exposures: ${row.rotationExposure} / ${row.athleticOverheadExposure} / ${row.recoveryPrehabExposure}")
            appendLine("- Bench-dominant overhead selections: ${row.benchDominantOverheadSelections}")
            appendLine("- Rotation replacing anchor slots: ${row.rotationInAnchorSlots}")
            appendLine("- Explosive/COD filler in core-prehab roles: ${row.randomFunctionalFillerCount}")
            if (!result.representativeTemplate) {
                appendLine("- Fallback requested slots: ${row.fallbackResolvedRequestedSlots} resolved / ${row.fallbackBlankRequestedSlots} blank / ${row.fallbackWeakRequestedSlots} weak")
                appendLine("- Fallback role violations: anchor ${row.fallbackAnchorRoleViolations}, core-prehab ${row.fallbackCorePrehabRoleViolations}, recovery clusters ${row.normalRecoveryRehabClusters}")
            }
            appendLine("- Direct sport sessions / time issues: ${row.directSportSessions} / ${row.timeBudgetIssues}")
            appendLine("- Hard issues: ${row.hardIssueCodes.ifEmpty { listOf("NONE") }.joinToString()}")
            appendLine("- Warnings: ${row.warningCodes.ifEmpty { listOf("NONE") }.distinct().joinToString()}")
            appendLine()
            result.weekPlans.forEach { week ->
                appendLine("#### W${week.weekIndex} ${week.weekType}")
                result.items.filter { it.weekNumber == week.weekIndex }
                    .groupBy(ProgramSkeletonItem::dayOfWeek)
                    .toSortedMap()
                    .forEach { (day, rows) ->
                        appendLine("- D$day ${rows.first().trainingSlot}: ${rows.sortedBy(ProgramSkeletonItem::orderIndex).joinToString { "${it.exerciseName} [${it.requestedTemplateSlot.ifBlank { it.selectionRole }}]" }}")
                    }
            }
            appendLine()
        }
        appendLine("## ORANGE / RED Safety Samples")
        appendLine()
        safety.forEach { sample ->
            val explosive = sample.result.items.filter { it.isExplosiveTransfer() }
            appendLine("- `${sample.id}`: hard=${sample.result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }}, explosive=${explosive.size}, directSport=${sample.result.items.count(ProgramSkeletonItem::directSportSession)}, explosiveItems=${explosive.map(ProgramSkeletonItem::exerciseName).distinct().ifEmpty { listOf("NONE") }.joinToString()}")
        }
        appendLine()
        appendLine("## Quality Issues Found and Tuning Applied")
        appendLine()
        appendLine("- v0.3.5.9 fallback rows had 1,200 blank requested slots, so role labels could not be audited against actual slot intent.")
        appendLine("- Fallback ANCHOR now requires a primary squat, hinge, upper-pull, or single-leg capability.")
        appendLine("- CORE, PREHAB, SUPPORT, TRANSFER, and ACCESSORY use explicit role-capability sets; reactive work is rejected from CORE/PREHAB/SUPPORT.")
        appendLine("- Fallback requested slots are resolved from primary, then secondary, then acceptable structured weak capability.")
        appendLine("- Normal recovery-weakpoint sessions allow at most one rehab-like activation item; expanded allowance remains for deload, RED, and explicit recovery/prehab contexts.")
        appendLine()
        appendLine("## Remaining Warnings")
        appendLine()
        appendLine("- Joint-impact wave warnings remain in selected 1-day, 2-day, and 5-day fallback samples. They remain coach-facing warnings because direct sport is excluded, impact share stays below the 45% dominance cap, and no hard safety rule is violated.")
        appendLine("- The 7-day fallback retains one non-anchor repetition warning. Rehab share remains controlled and anchors are preserved; broader accessory rotation belongs to later fallback-template expansion, not this narrow patch.")
        appendLine("- No metadata, seed, Room schema, or hidden-template matrix expansion was required.")
    }

    private fun renderJson(
        results: Map<Scenario, GeneratedProgramSkeleton>,
        audits: Map<Scenario, ScenarioAudit>,
        safety: List<SafetySample>
    ): String = buildString {
        appendLine("{")
        appendLine("  \"version\": \"v0.3.5.10\",")
        appendLine("  \"scenarioCount\": ${results.size},")
        appendLine("  \"scenarios\": [")
        results.entries.forEachIndexed { index, (scenario, result) ->
            val audit = audits.getValue(scenario)
            appendLine("    {")
            appendLine("      \"id\": \"${json(scenario.id)}\",")
            appendLine("      \"label\": \"${json(scenario.label)}\",")
            appendLine("      \"templateId\": \"${json(result.templateId)}\",")
            appendLine("      \"representativeTemplate\": ${result.representativeTemplate},")
            appendLine("      \"days\": ${scenario.days}, \"weeks\": ${scenario.weeks}, \"ratio\": ${decimal(scenario.ratio)},")
            appendLine("      \"audit\": {\"items\": ${audit.items}, \"missingAnchorWindows\": ${audit.missingAnchorWindows}, \"missingSupportWindows\": ${audit.missingSupportWindows}, \"normalRehabShare\": ${decimal(audit.normalRehabShare)}, \"maxTwoWeekFootworkShare\": ${decimal(audit.maxTwoWeekFootworkShare)}, \"rotationExposure\": ${audit.rotationExposure}, \"athleticOverheadExposure\": ${audit.athleticOverheadExposure}, \"recoveryPrehabExposure\": ${audit.recoveryPrehabExposure}, \"randomFunctionalFillerCount\": ${audit.randomFunctionalFillerCount}, \"fallbackBlankRequestedSlots\": ${audit.fallbackBlankRequestedSlots}, \"fallbackAnchorRoleViolations\": ${audit.fallbackAnchorRoleViolations}, \"fallbackCorePrehabRoleViolations\": ${audit.fallbackCorePrehabRoleViolations}, \"hardIssues\": ${audit.hardIssueCodes.size}, \"warnings\": ${audit.warningCodes.size}},")
            appendLine("      \"weeksOutput\": [")
            result.weekPlans.forEachIndexed { weekIndex, week ->
                appendLine("        {\"week\": ${week.weekIndex}, \"weekType\": \"${week.weekType}\", \"days\": [")
                val days = result.items.filter { it.weekNumber == week.weekIndex }.groupBy(ProgramSkeletonItem::dayOfWeek).toSortedMap().entries.toList()
                days.forEachIndexed { dayIndex, (day, rows) ->
                    appendLine("          {\"day\": $day, \"trainingSlot\": \"${json(rows.first().trainingSlot)}\", \"items\": [")
                    rows.sortedBy(ProgramSkeletonItem::orderIndex).forEachIndexed { itemIndex, item ->
                        append("            {\"name\": \"${json(item.exerciseName)}\", \"stableKey\": \"${json(item.stableKey)}\", \"requestedSlot\": \"${json(item.requestedTemplateSlot)}\", \"role\": \"${json(item.selectionRole)}\", \"rehabLike\": ${item.rehabLikeActivation}}")
                        appendLine(if (itemIndex == rows.lastIndex) "" else ",")
                    }
                    append("          ]}")
                    appendLine(if (dayIndex == days.lastIndex) "" else ",")
                }
                append("        ]}")
                appendLine(if (weekIndex == result.weekPlans.lastIndex) "" else ",")
            }
            appendLine("      ]")
            append("    }")
            appendLine(if (index == results.size - 1) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"fatigueSafety\": [")
        safety.forEachIndexed { index, sample ->
            val explosive = sample.result.items.filter { it.isExplosiveTransfer() }
            append("    {\"id\": \"${sample.id}\", \"hardIssues\": ${sample.result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }}, \"explosiveItems\": ${explosive.size}}")
            appendLine(if (index == safety.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun build(
        scenario: Scenario,
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog,
        fatigue: DailyFatigueState? = null
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = scenario.label,
            goal = scenario.goal,
            weeklyTrainingDays = scenario.days,
            sessionMinutes = scenario.minutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = scenario.ratio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = scenario.weeks
        ),
        exercises = exercises,
        history = emptyList(),
        runtimeMetadataCatalog = catalog,
        fatigueState = fatigue
    )

    private fun renderCleanupReport(audits: Map<Scenario, ScenarioAudit>): String = buildString {
        val fallback = audits.filterKeys { it.id.startsWith("FALLBACK_") }.values
        appendLine("# v0.3.5.10 Fallback Role / Slot Cleanup")
        appendLine()
        appendLine("## Scope")
        appendLine()
        appendLine("- Files changed: `app/build.gradle.kts`, `ProgramBuilder.kt`, `ProgramBuilderValidation.kt`, and the generated-quality audit test/output files.")
        appendLine("- Metadata/seed/Room schema changes: NONE.")
        appendLine("- Representative hidden templates changed: NO; shared fallback selection correctness only.")
        appendLine()
        appendLine("## Before / After")
        appendLine()
        appendLine("- v0.3.5.9 fallback requested-slot blanks: 1,200.")
        appendLine("- Fallback items: ${fallback.sumOf(ScenarioAudit::fallbackItems)}")
        appendLine("- Requested slots resolved: ${fallback.sumOf(ScenarioAudit::fallbackResolvedRequestedSlots)}")
        appendLine("- Requested slots blank: ${fallback.sumOf(ScenarioAudit::fallbackBlankRequestedSlots)}")
        appendLine("- Weak capability resolutions: ${fallback.sumOf(ScenarioAudit::fallbackWeakRequestedSlots)}")
        appendLine("- Anchor role violations: ${fallback.sumOf(ScenarioAudit::fallbackAnchorRoleViolations)}")
        appendLine("- CORE/PREHAB role violations: ${fallback.sumOf(ScenarioAudit::fallbackCorePrehabRoleViolations)}")
        appendLine("- Normal recovery/weakpoint rehab clusters: ${fallback.sumOf(ScenarioAudit::normalRecoveryRehabClusters)}")
        appendLine()
        appendLine("## Rules Applied")
        appendLine()
        appendLine("- ANCHOR: primary capability must be squat, hinge, upper pull, or single-leg strength/control.")
        appendLine("- CORE: controlled trunk/anti-rotation capability; no ballistic/reactive/high-impact work.")
        appendLine("- PREHAB: recovery/prehab-light capability; no ballistic/reactive/high-impact work.")
        appendLine("- SUPPORT: structured strength/support capability; no reactive/COD dumping.")
        appendLine("- RECOVERY_WEAKPOINT: one rehab-like activation maximum in normal weeks.")
        appendLine()
        appendLine("## Remaining TODO")
        appendLine()
        appendLine("- Expand hidden templates only when a future request explicitly covers additional ratio/goal/day combinations.")
    }

    private fun ProgramSkeletonItem.hasCapability(slots: Set<String>): Boolean =
        requestedTemplateSlot in slots || primarySlotCapabilities.any(slots::contains) || secondarySlotCapabilities.any(slots::contains)

    private fun ProgramSkeletonItem.isRecoveryContext(result: GeneratedProgramSkeleton): Boolean =
        trainingSlot in RECOVERY_TRAINING_SLOTS || result.weekPlans.firstOrNull { it.weekIndex == weekNumber }?.deloadFlag == true

    private fun ProgramSkeletonItem.isExplosiveTransfer(): Boolean =
        listOf(movementSubtype, movementFamily, metadataProgramSlot, primaryStressProfile).any { value ->
            EXPLOSIVE_TOKENS.any { token -> value.contains(token, true) }
        }

    private fun fatigueState(score: Int): DailyFatigueState = DailyFatigueState(
        date = LocalDate.of(2026, 6, 21),
        neuromuscularFatigue = score.toDouble(), systemicMuscularFatigue = score.toDouble(),
        localMuscularFatigue = score.toDouble(), jointTendonImpactFatigue = score.toDouble(),
        movementFocusFatigue = score.toDouble(), recoveryPressure = score.toDouble(),
        neuromuscularScore = score, systemicMuscularScore = score, localMuscularScore = score,
        jointTendonImpactScore = score, movementFocusScore = score, recoveryPressureScore = score,
        overallFatigueIndex = score,
        readinessLabel = if (score >= 85) FatigueReadinessLabel.HIGH_FATIGUE else FatigueReadinessLabel.ELEVATED,
        cautionReasons = emptyList(), confidence = FatigueConfidence.HIGH
    )

    private fun loadSeedExercises(): List<Exercise> {
        val lines = seedFile().readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val header = parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1).map(::parseCsvLine).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }.filter { it["row_type"] == "exercise" }.mapIndexed { index, row ->
            Exercise(
                id = (index + 1).toLong(), name = row["exercise_name"].orEmpty(),
                category = row["category"].orEmpty(), defaultRestSeconds = row["default_rest_seconds"]?.toIntOrNull() ?: 60,
                stableKey = row["stable_key"].orEmpty(), equipment = row["equipment_tags"].orEmpty(), isActive = true
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val char = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"'); index++
                } else quoted = !quoted
                ',' -> if (quoted) current.append(char) else { values += current.toString(); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun repositoryOutputDir(): File = if (File("app/src/main").exists()) File("outputs") else File("../outputs")
    private fun seedFile(): File = existingFile("src/main/assets/training_settings_seed.csv")
    private fun canonicalFile(): File = existingFile("src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
    private fun existingFile(relative: String): File = sequenceOf(File(relative), File("app/$relative")).firstOrNull(File::exists)
        ?: error("Missing test asset: $relative")
    private fun percent(value: Double): String = "%.1f%%".format(Locale.US, value * 100.0)
    private fun decimal(value: Double): String = "%.4f".format(Locale.US, value)
    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private data class Scenario(
        val id: String, val label: String, val days: Int, val weeks: Int, val minutes: Int,
        val ratio: Double, val goal: ProgramGoal = ProgramGoal.BADMINTON_SUPPORT
    )

    private data class ScenarioAudit(
        val items: Int, val sessions: Int, val missingAnchorWindows: Int, val missingSupportWindows: Int,
        val normalRehabShare: Double, val maxTwoWeekFootworkShare: Double, val rotationExposure: Int,
        val athleticOverheadExposure: Int, val recoveryPrehabExposure: Int, val directSportSessions: Int,
        val timeBudgetIssues: Int, val rehabDominantSessions: Int, val benchDominantOverheadSelections: Int,
        val rotationInAnchorSlots: Int, val clutteredSessions: Int, val randomFunctionalFillerCount: Int,
        val fallbackItems: Int, val fallbackBlankRequestedSlots: Int, val fallbackResolvedRequestedSlots: Int,
        val fallbackWeakRequestedSlots: Int, val fallbackAnchorRoleViolations: Int,
        val fallbackCorePrehabRoleViolations: Int, val normalRecoveryRehabClusters: Int,
        val hardIssueCodes: List<String>,
        val warningCodes: List<String>
    )

    private data class SafetySample(val id: String, val result: GeneratedProgramSkeleton)

    private companion object {
        val SCENARIOS = listOf(
            Scenario("STANDARD_3D_4W_70_30", "3 days / 4 weeks / 70:30 / Standard", 3, 4, 60, 0.70),
            Scenario("FOOTWORK_COD_4D_4W_80_20", "4 days / 4 weeks / 80:20 / Footwork-COD", 4, 4, 60, 0.80),
            Scenario("HYBRID_4D_4W_50_50", "4 days / 4 weeks / 50:50 / Hybrid", 4, 4, 60, 0.50, ProgramGoal.FUNCTIONAL_CONDITIONING),
            Scenario("STANDARD_5D_8W_70_30", "5 days / 8 weeks / 70:30 / Standard", 5, 8, 60, 0.70),
            Scenario("STRENGTH_BIASED_5D_8W_30_70", "5 days / 8 weeks / 30:70 / Strength-biased", 5, 8, 60, 0.30, ProgramGoal.STRENGTH),
            Scenario("FALLBACK_1D_4W_70_30", "1 day / 4 weeks / 70:30 / Standard", 1, 4, 45, 0.70),
            Scenario("FALLBACK_2D_4W_70_30", "2 days / 4 weeks / 70:30 / Standard", 2, 4, 50, 0.70),
            Scenario("FALLBACK_3D_4W_90_10", "3 days / 4 weeks / 90:10 / Footwork", 3, 4, 60, 0.90),
            Scenario("FALLBACK_4D_4W_90_10", "4 days / 4 weeks / 90:10 / COD", 4, 4, 60, 0.90),
            Scenario("FALLBACK_5D_8W_80_20", "5 days / 8 weeks / 80:20 / Footwork", 5, 8, 60, 0.80),
            Scenario("FALLBACK_6D_8W_70_30", "6 days / 8 weeks / 70:30 / Standard", 6, 8, 45, 0.70),
            Scenario("FALLBACK_7D_8W_80_20", "7 days / 8 weeks / 80:20 / High-frequency support", 7, 8, 45, 0.80),
            Scenario("FALLBACK_4D_4W_30_70", "4 days / 4 weeks / 30:70 / Strength-biased", 4, 4, 60, 0.30, ProgramGoal.STRENGTH),
            Scenario("FALLBACK_5D_8W_50_50", "5 days / 8 weeks / 50:50 / Hybrid", 5, 8, 60, 0.50, ProgramGoal.FUNCTIONAL_CONDITIONING)
        )
        val CORE_ANCHOR_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name, ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name
        )
        val ANCHOR_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name, ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name, ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name
        )
        val REQUIRED_ANCHOR_GROUPS = listOf(
            setOf(ProgramSlotId.LOWER_SQUAT_PATTERN.name, ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name),
            setOf(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name), setOf(ProgramSlotId.UPPER_PULL_ANCHOR.name)
        )
        val SUPPORT_GROUPS = listOf(
            setOf(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name), setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name),
            setOf(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT.name), setOf(ProgramSlotId.CALF_ANKLE_CAPACITY.name)
        )
        val FOOTWORK_SLOT_NAMES = setOf(
            ProgramSlotId.BADMINTON_DECEL_COD.name, ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME.name
        )
        val RECOVERY_TRAINING_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB.name, ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
            ProgramTrainingSlot.MICRO_RECOVERY.name
        )
        val EXPLOSIVE_TOKENS = setOf("SLAM", "THROW", "TOSS", "PUSH_PRESS", "PLYOMETRIC", "EXPLOSIVE")
    }
}
