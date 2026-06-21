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

        assertEquals(235, exercises.size)
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
        assertEquals(235, exercises.size)
        assertEquals(235, catalog.size)

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

        val after = ProgramSlotCoverageDiagnostics().analyze(exercises, catalog)
        File(outputDir, "v0.3.5.8_metadata_slot_coverage_diagnostic.csv").writeText(
            policyCoverageCsv(after),
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.8_overhead_smash_recovery_policy_report.md").writeText(
            policyReport(after, results).trimEnd() + "\n",
            Charsets.UTF_8
        )
        File(outputDir, "v0.3.5.8_representative_template_output_audit_report.md").writeText(
            representativePolicyAudit(results).trimEnd() + "\n",
            Charsets.UTF_8
        )

        val newProfiles = exercises.filter { it.stableKey in V0357_CANDIDATE_KEYS }.map { exercise ->
            exercise to SlotCapabilityResolver.DEFAULT.resolve(exercise, catalog.resolve(exercise))
        }
        assertEquals(20, newProfiles.size)
        assertTrue(newProfiles.all { (_, profile) -> profile.source == SlotCapabilitySource.RUNTIME_METADATA })
        assertTrue(newProfiles.none { (_, profile) -> profile.source == SlotCapabilitySource.NAME_FALLBACK })
        val overheadDiagnostic = after.first { it.slot == ProgramSlotId.OVERHEAD_SMASH_SUPPORT }
        assertEquals(0, overheadDiagnostic.candidateCount)
        assertEquals(SlotCoverageMode.DERIVED_UMBRELLA, overheadDiagnostic.coverageMode)
        assertEquals(SlotCoverageStrength.ADEQUATE, overheadDiagnostic.coverageStrength)
        val recoveryDiagnostic = after.first { it.slot == ProgramSlotId.RECOVERY_PREHAB_LIGHT }
        assertEquals(SlotCoverageMode.DIRECT, recoveryDiagnostic.coverageMode)
        assertEquals(SlotCoverageStrength.ADEQUATE, recoveryDiagnostic.coverageStrength)
        assertEquals(0, recoveryDiagnostic.legacyFallbackMatchCount)
        assertEquals(0, recoveryDiagnostic.nameFallbackMatchCount)

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
            assertFalse("${example.label} contains a direct sport session", result.items.any(ProgramSkeletonItem::directSportSession))
            assertFalse(
                "${example.label} used a new transfer candidate as a core anchor",
                result.items.any {
                    it.stableKey in V0357_CANDIDATE_KEYS &&
                        it.requestedTemplateSlot in CORE_ANCHOR_SLOT_NAMES
                }
            )
        }

        results.filterKeys { it.requiresOverhead }.values.forEach { result ->
            val selected = result.items.filter { it.requestedTemplateSlot == ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name }
            assertTrue("${result.templateId} did not fill athletic overhead press", selected.isNotEmpty())
            assertFalse(selected.any(ProgramSkeletonItem::rehabLikeActivation))
            assertTrue(
                "${result.templateId} did not derive overhead-smash support from a component mix",
                overheadCoverageWindows(result).all(DerivedUmbrellaCoverage::satisfied)
            )
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
        appendLine("# v0.3.5.7 Representative Template Samples")
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
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
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

    private fun slotCoverageCsv(
        before: List<SlotCoverageDiagnostic>,
        after: List<SlotCoverageDiagnostic>
    ): String = buildString {
        appendLine("slot,beforeCandidates,afterCandidates,delta,beforeStrong,afterStrong,beforeNameFallback,afterNameFallback,afterStrength")
        ProgramSlotId.entries.forEach { slot ->
            val old = before.first { it.slot == slot }
            val current = after.first { it.slot == slot }
            appendLine(listOf(
                slot.name,
                old.candidateCount,
                current.candidateCount,
                current.candidateCount - old.candidateCount,
                old.strongMetadataMatchCount,
                current.strongMetadataMatchCount,
                old.nameFallbackMatchCount,
                current.nameFallbackMatchCount,
                current.coverageStrength.name
            ).joinToString(",") { csv(it.toString()) })
        }
    }

    private fun policyCoverageCsv(rows: List<SlotCoverageDiagnostic>): String = buildString {
        appendLine(
            "slot,coverageMode,directCandidates,strongPrimary,secondary,weakMetadata,legacyFallback," +
                "nameFallback,derivedComponents,representedDerivedComponents,derivedContributors,status"
        )
        rows.forEach { row ->
            appendLine(listOf(
                row.slot.name,
                row.coverageMode.name,
                row.candidateCount,
                row.strongMetadataMatchCount,
                row.secondaryMetadataMatchCount,
                row.weakMetadataMatchCount,
                row.legacyFallbackMatchCount,
                row.nameFallbackMatchCount,
                row.derivedComponentSlots.sortedBy(ProgramSlotId::name).joinToString("|", transform = ProgramSlotId::name),
                row.representedDerivedComponents.sortedBy(ProgramSlotId::name).joinToString("|", transform = ProgramSlotId::name),
                row.derivedContributorCount,
                row.coverageStrength.name
            ).joinToString(",") { csv(it.toString()) })
        }
    }

    private fun policyReport(
        diagnostics: List<SlotCoverageDiagnostic>,
        results: Map<Example, GeneratedProgramSkeleton>
    ): String = buildString {
        val overhead = diagnostics.first { it.slot == ProgramSlotId.OVERHEAD_SMASH_SUPPORT }
        val recovery = diagnostics.first { it.slot == ProgramSlotId.RECOVERY_PREHAB_LIGHT }
        appendLine("# v0.3.5.8 Overhead Smash / Recovery Policy")
        appendLine()
        appendLine("## OVERHEAD_SMASH_SUPPORT")
        appendLine()
        appendLine("- Direct candidates: ${overhead.candidateCount}")
        appendLine("- Status: `${overhead.coverageMode}` / `${overhead.coverageStrength}`")
        appendLine("- Derived components: ${overhead.derivedComponentSlots.sortedBy(ProgramSlotId::name).joinToString { "`${it.name}`" }}")
        appendLine("- Represented components in candidate pool: ${overhead.representedDerivedComponents.size}")
        appendLine("- Contributing candidate profiles: ${overhead.derivedContributorCount}")
        appendLine("- Rule: at least 3 distinct components and 2 distinct exercise contributors; no single exercise gives full umbrella coverage.")
        appendLine()
        appendLine("## RECOVERY_PREHAB_LIGHT")
        appendLine()
        appendLine("- v0.3.5.7 status: `WEAK`")
        appendLine("- v0.3.5.8 status: `${recovery.coverageStrength}`")
        appendLine("- Candidates: ${recovery.candidateCount}")
        appendLine("- Runtime metadata matches: primary ${recovery.strongMetadataMatchCount}, secondary ${recovery.secondaryMetadataMatchCount}, weak ${recovery.weakMetadataMatchCount}")
        appendLine("- Fallback matches: legacy ${recovery.legacyFallbackMatchCount}, name ${recovery.nameFallbackMatchCount}")
        appendLine("- Fix type: diagnostic-only. Existing recovery candidates are intentionally secondary to their movement role; no exercises or metadata rows were added.")
        appendLine("- Existing rehab-like activation caps remain unchanged.")
        appendLine()
        appendLine("## Representative Audit")
        appendLine()
        results.forEach { (example, result) ->
            val coverage = overheadCoverageWindows(result)
            appendLine(
                "- `${result.templateId}`: anchors preserved, hard issues " +
                    "${result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }}, " +
                    "rehab ${"%.1f".format(result.items.count(ProgramSkeletonItem::rehabLikeActivation) * 100.0 / result.items.size)}%, " +
                    "derived overhead ${if (coverage.isEmpty()) "NOT_REQUIRED" else if (coverage.all(DerivedUmbrellaCoverage::satisfied)) "SATISFIED" else "INCOMPLETE"}."
            )
        }
        appendLine()
        appendLine("## Mutations")
        appendLine()
        appendLine("- Canonical metadata: unchanged")
        appendLine("- Exercise seeds: unchanged")
        appendLine("- Room schema: unchanged")
        appendLine("- ProgramBuilder architecture: unchanged")
    }

    private fun representativePolicyAudit(
        results: Map<Example, GeneratedProgramSkeleton>
    ): String = buildString {
        appendLine("# v0.3.5.8 Representative Template Output Audit")
        appendLine()
        appendLine("| Template | Items | Missing anchors | Rehab share | Max 2-week footwork/COD/reactive | Overhead windows | Overhead status | Hard issues |")
        appendLine("|---|---:|---:|---:|---:|---:|---|---:|")
        results.forEach { (example, result) ->
            val requiredByWeek = result.items.filter(ProgramSkeletonItem::requiredTemplateAnchor)
                .groupBy(ProgramSkeletonItem::weekNumber)
            val missing = (1..example.weeks).sumOf { week ->
                val slots = requiredByWeek[week].orEmpty().map(ProgramSkeletonItem::requestedTemplateSlot).toSet()
                CORE_ANCHOR_SLOT_NAMES.count { it !in slots }
            }
            val rehabShare = result.items.count(ProgramSkeletonItem::rehabLikeActivation).toDouble() / result.items.size
            val footworkShare = twoWeekFootworkShares(result).maxOrNull() ?: 0.0
            val overheadWindows = overheadCoverageWindows(result)
            val overheadStatus = when {
                overheadWindows.isEmpty() -> "NOT_REQUIRED"
                overheadWindows.all(DerivedUmbrellaCoverage::satisfied) -> "SATISFIED"
                else -> "INCOMPLETE"
            }
            appendLine(
                "| `${result.templateId}` | ${result.items.size} | $missing | ${"%.1f".format(rehabShare * 100)}% | " +
                    "${"%.1f".format(footworkShare * 100)}% | ${overheadWindows.size} | $overheadStatus | " +
                    "${result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }} |"
            )
        }
    }

    private fun overheadCoverageWindows(result: GeneratedProgramSkeleton): List<DerivedUmbrellaCoverage> {
        val policy = CoverageAccountingPolicy.DEFAULT
        val windows = if (result.weekPlans.size >= 4) result.weekPlans.windowed(4) else listOf(result.weekPlans)
        return windows.mapNotNull { window ->
            val weekNumbers = window.map(ProgramWeekPlan::weekIndex).toSet()
            val items = result.items.filter { it.weekNumber in weekNumbers }
            val overheadIntent = items.any {
                it.requestedTemplateSlot in setOf(
                    ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name,
                    ProgramSlotId.OVERHEAD_SMASH_SUPPORT.name
                )
            }
            if (!overheadIntent) return@mapNotNull null
            policy.derivedUmbrellaCoverageByContributor(
                contributors = items.map { item ->
                    item.stableKey.ifBlank { item.localId } to policy.profile(item)
                },
                umbrellaSlot = ProgramSlotId.OVERHEAD_SMASH_SUPPORT
            )
        }
    }

    private fun expansionReport(
        exercises: List<Exercise>,
        catalog: RuntimeExerciseMetadataCatalog,
        before: List<SlotCoverageDiagnostic>,
        after: List<SlotCoverageDiagnostic>,
        results: Map<Example, GeneratedProgramSkeleton>
    ): String = buildString {
        fun row(rows: List<SlotCoverageDiagnostic>, slot: ProgramSlotId) = rows.first { it.slot == slot }
        val resolver = SlotCapabilityResolver.DEFAULT
        val added = exercises.filter { it.stableKey in V0357_CANDIDATE_KEYS }
        val fallbackCount = added.count { resolver.resolve(it, catalog.resolve(it)).source != SlotCapabilitySource.RUNTIME_METADATA }
        appendLine("# v0.3.5.7 Rotational / Overhead Candidate Expansion")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Added exercises: ${added.size}")
        appendLine("- Canonical rows: ${catalog.size}")
        appendLine("- New-row fallback count: $fallbackCount")
        appendLine("- Room schema change: NO")
        appendLine("- ProgramBuilder architecture rewrite: NO")
        appendLine()
        appendLine("## Slot Coverage")
        appendLine()
        appendLine("| Slot | Before | After | Strong after |")
        appendLine("|---|---:|---:|---:|")
        listOf(
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY,
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT
        ).forEach { slot ->
            appendLine("| `${slot.name}` | ${row(before, slot).candidateCount} | ${row(after, slot).candidateCount} | ${row(after, slot).strongMetadataMatchCount} |")
        }
        appendLine()
        appendLine("## Added Metadata")
        appendLine()
        added.sortedBy(Exercise::name).forEach { exercise ->
            val metadata = requireNotNull(catalog.resolve(exercise))
            val profile = resolver.resolve(exercise, metadata)
            appendLine("- ${exercise.name} (`${exercise.stableKey}`): `${metadata.movementSubtype}` -> `${profile.primary.singleOrNull()?.name}`; ${metadata.stressMagnitudeHint}/${metadata.recoveryDurationClass}")
        }
        appendLine()
        appendLine("## Representative Templates")
        appendLine()
        appendLine("| Template | Items | Missing anchors | Rehab share | Max 2-week footwork/COD/reactive | New rotation selections | Hard issues |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|")
        results.forEach { (example, result) ->
            val requiredByWeek = result.items.filter(ProgramSkeletonItem::requiredTemplateAnchor)
                .groupBy(ProgramSkeletonItem::weekNumber)
            val missing = (1..example.weeks).sumOf { week ->
                val slots = requiredByWeek[week].orEmpty().map(ProgramSkeletonItem::requestedTemplateSlot).toSet()
                CORE_ANCHOR_SLOT_NAMES.count { it !in slots }
            }
            val rehabShare = result.items.count(ProgramSkeletonItem::rehabLikeActivation).toDouble() / result.items.size
            val maxTransfer = twoWeekFootworkShares(result).maxOrNull() ?: 0.0
            val newRotation = result.items.count {
                it.stableKey in V0357_CANDIDATE_KEYS &&
                    it.requestedTemplateSlot == ProgramSlotId.ROTATIONAL_KINETIC_CHAIN.name
            }
            val hard = result.validationDetails.count { it.severity == ProgramValidationSeverity.HARD }
            appendLine("| `${result.templateId}` | ${result.items.size} | $missing | ${"%.1f".format(rehabShare * 100)}% | ${"%.1f".format(maxTransfer * 100)}% | $newRotation | $hard |")
        }
        appendLine()
        appendLine("## Safety")
        appendLine()
        appendLine("- Explosive med-ball, landmine, and fast ViPR rows use HIGH stress and LONG recovery; ORANGE selects controlled cable/band/dumbbell alternatives instead.")
        appendLine("- RED blocks the rotational kinetic-chain slot.")
        appendLine("- Kettlebell halo is scapular/shoulder control, not aggressive rotation power.")
        appendLine("- Face pull, wall slide, and scap push-up remain outside rotational kinetic-chain coverage.")
        appendLine("- New exercises are SUPPORTIVE training exercises, never DIRECT sport sessions.")
        appendLine()
        appendLine("## Remaining Risks")
        appendLine()
        after.filter { it.coverageStrength != SlotCoverageStrength.ADEQUATE }.forEach { diagnostic ->
            appendLine("- `${diagnostic.slot}` remains `${diagnostic.coverageStrength}` with ${diagnostic.candidateCount} candidates.")
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
        val V0357_CANDIDATE_KEYS = setOf(
            "med_ball_side_throw",
            "med_ball_rotational_scoop_toss",
            "med_ball_rotational_slam",
            "med_ball_overhead_slam",
            "med_ball_chest_pass",
            "cable_woodchop",
            "cable_lift",
            "band_woodchop",
            "band_lift",
            "landmine_rotation",
            "landmine_rainbow",
            "landmine_anti_rotation",
            "plate_rotational_press_out",
            "dumbbell_woodchop",
            "kettlebell_halo",
            "vipr_rotational_lift",
            "vipr_chop",
            "vipr_shovel_scoop",
            "vipr_step_and_rotate",
            "vipr_rotational_press_out"
        )
        val CORE_ANCHOR_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name
        )
        val RECOVERY_TRAINING_SLOTS = setOf(
            ProgramTrainingSlot.RECOVERY_PREHAB.name,
            ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
            ProgramTrainingSlot.MICRO_RECOVERY.name
        )
    }
}
