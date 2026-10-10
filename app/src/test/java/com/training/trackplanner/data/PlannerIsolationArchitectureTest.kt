package com.training.trackplanner.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PlannerIsolationArchitectureTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
        .first { File(it, "settings.gradle.kts").isFile }
    private val source = File(root, "app/src/main/java/com/training/trackplanner")
    private fun normalized(text: String) = text.replace("\r\n", "\n")
    private fun text(path: String) = normalized(File(source, path).readText())

    /** Source dependency checks must ignore KDoc and comments, which are not executable edges. */
    private fun codeWithoutComments(sourceText: String): String {
        val input = normalized(sourceText)
        val output = StringBuilder(input.length)
        var index = 0
        var blockDepth = 0
        var lineComment = false
        var quote: Char? = null
        var rawString = false
        while (index < input.length) {
            val current = input[index]
            val next = input.getOrNull(index + 1)
            val third = input.getOrNull(index + 2)
            when {
                lineComment -> {
                    if (current == '\n') {
                        output.append('\n')
                        lineComment = false
                    } else output.append(' ')
                    index++
                }
                blockDepth > 0 -> {
                    if (current == '/' && next == '*') {
                        output.append("  ")
                        blockDepth++
                        index += 2
                    } else if (current == '*' && next == '/') {
                        output.append("  ")
                        blockDepth--
                        index += 2
                    } else {
                        output.append(if (current == '\n') '\n' else ' ')
                        index++
                    }
                }
                rawString -> {
                    if (current == '"' && next == '"' && third == '"') {
                        output.append("\"\"\"")
                        index += 3
                        rawString = false
                    } else {
                        output.append(current)
                        index++
                    }
                }
                quote != null -> {
                    output.append(current)
                    if (current == '\\' && index + 1 < input.length) {
                        output.append(input[index + 1])
                        index += 2
                    } else {
                        if (current == quote) quote = null
                        index++
                    }
                }
                current == '/' && next == '/' -> {
                    output.append("  ")
                    index += 2
                    lineComment = true
                }
                current == '/' && next == '*' -> {
                    output.append("  ")
                    index += 2
                    blockDepth = 1
                }
                current == '"' && next == '"' && third == '"' -> {
                    output.append("\"\"\"")
                    index += 3
                    rawString = true
                }
                current == '"' || current == '\'' -> {
                    quote = current
                    output.append(current)
                    index++
                }
                else -> {
                    output.append(current)
                    index++
                }
            }
        }
        return output.toString()
    }

    @Test fun frozenPlannerImportsOnlyOwnTypesIdentityDaoAndNeutralRowNoticePrimitives() {
        val allowed = setOf("Exercise", "ExerciseDao", "ProgramSetPrescription", "ProgramOptimizationSummary",
            "ProgramUserNotice", "ProgramUserNoticeCode", "ProgramUserNoticeLevel")
        val forbidden = listOf("personalized.", "AthletePlanningState", "TrainingState", "AdaptationGap", "BlockIntent",
            "ExecutionAllocation", "PersonalizedProgramBuilder", "PersonalizedPlanningDecision", "reconcileProgression",
            "GeneratedProgramSkeleton", "ProgramSkeletonItem", "progressionStyle", "progressionVariant",
            "progressionAnchorSetIndex", "progressionBinding", "progressionSessions")
        File(source, "data/program/legacy").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = normalized(file.readText())
            // Historical strengthProgressionGroup is metadata, not an execution dependency.
            assertFalse("${file.name}: progression dependency", Regex(
                "ProgramExecutionDraft|LegacyProgressionDraft|ProgramProgression|ProgressionTrack|ProgressionRule|ProgressionRole|ProgressionMode|ProgressionLink"
            ).containsMatchIn(content))
            forbidden.forEach { assertFalse("${file.name}: $it", content.contains(it)) }
            Regex("(?m)^import com\\.training\\.trackplanner\\.([^\\n]+)").findAll(content).forEach {
                assertTrue("${file.name}: ${it.value}", it.groupValues[1].startsWith("data.program.legacy.") ||
                    it.groupValues[1].removePrefix("data.") in allowed)
            }
        }
    }

    @Test fun recordBasedTransitiveSourceDependenciesCannotReachFrozenPlanningInternals() {
        val files = source.walkTopDown().filter { it.extension == "kt" }.toList()
        val contents = files.associateWith { codeWithoutComments(it.readText()) }
        val packages = contents.mapValues { Regex("(?m)^package ([\\w.]+)").find(it.value.removePrefix("\uFEFF"))!!.groupValues[1] }
        val declarations = mutableMapOf<String, File>()
        contents.forEach { (file, content) ->
            Regex("(?:class|object|interface|typealias)\\s+(\\w+)").findAll(content).forEach {
                declarations[packages.getValue(file) + "." + it.groupValues[1]] = file
            }
            // Follow imported/top-level bridge functions too, not only class constructors.
            // Private helpers cannot be referenced across files; treating them as package-wide
            // declarations creates false transitive edges from unrelated record code.
            Regex("(?m)^(?:(?:internal|public|suspend|inline|tailrec)\\s+)*fun\\s+(?:[\\w<>?]+\\.)?(\\w+)\\s*\\(")
                .findAll(content).forEach { declarations[packages.getValue(file) + "." + it.groupValues[1]] = file }
        }
        val queue = ArrayDeque(files.filter { it.invariantSeparatorsPath.contains("/data/personalized/") })
        val seen = mutableSetOf<File>()
        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            if (!seen.add(file)) continue
            assertFalse("Record-Based dependency reaches ${file.name}", file.invariantSeparatorsPath.contains("/data/program/legacy/"))
            val content = contents.getValue(file)
            assertFalse("${file.name} references frozen planner", content.contains("LegacyAutoProgramBuilder") ||
                content.contains("LegacyAutoRuleTables") || content.contains("LegacyAutoSlotAllocator"))
            Regex("(?m)^import ([\\w.]+)").findAll(content).forEach { declarations[it.groupValues[1]]?.let(queue::addLast) }
            Regex("\\b[A-Za-z_]\\w*\\b").findAll(content).forEach {
                declarations[packages.getValue(file) + "." + it.value]?.let(queue::addLast)
            }
        }
    }

    @Test fun uiGenerationStateAndPreSavePathsAreDisjoint() {
        val screen = text("PlanScreen.kt")
        val legacyAction = screen.substringAfter("fun generateSkeleton()").substringBefore("fun runPreparedPersonalized")
        assertTrue(legacyAction.contains("generateLegacyAutoSkeleton"))
        assertTrue(legacyAction.contains("legacyAutoDraft = generated"))
        listOf("reconcileProgression", "withResolvedWeekDaySchedule", "PersonalizedProgramBuilder", "saveGeneratedProgram").forEach {
            assertFalse(it, legacyAction.contains(it))
        }
        assertTrue(screen.contains("personalizedDraft = personalizedDraft?.reconcileProgression"))
        assertFalse(text("LegacyAutoPreview.kt").contains("reconcileProgression"))
        assertFalse(text("LegacyAutoPreview.kt").contains("GeneratedProgramSkeleton"))
        val persistence = text("data/ProgramPlanService.kt").substringAfter("suspend fun saveLegacyAutoProgram")
            .substringBefore("suspend fun saveGeneratedProgram")
        assertTrue(persistence.indexOf("insertProgramItemSets") < persistence.indexOf("progression.author"))
        assertTrue(persistence.contains("progression.author(programId, generated, restored)"))
        assertTrue(persistence.contains("binding.logicalItemId, binding.sessionKey, binding.linkMode, binding.signature"))
        assertFalse(persistence.contains("reconcileProgression"))
        listOf("ProgramAutoBuilder", "ProgramRuleTables", "ProgramSlotAllocator", "ProgramIntensityResolver",
            "ProgramExerciseSpec", "ProgramCandidateAuthority", "ProgramGenerationService").forEach {
            assertFalse("Old planning entrypoint returned: $it", File(source, "data/$it.kt").exists())
        }
    }

    @Test fun sessionOverlayOnlyBeginsAfterFrozenOutputAndReusesGenericExecutionAuthority() {
        val screen = text("PlanScreen.kt")
        val generation = screen.substringAfter("viewModel.generateLegacyAutoSkeleton(request)").substringBefore("fun runPreparedPersonalized")
        assertTrue(generation.indexOf("legacyAutoDraft = generated") < generation.indexOf("LegacyProgressionDraft().reconcile(generated"))
        assertTrue(text("LegacyAutoPreview.kt").contains("ProgressionDraftControl(executionItem, execution"))
        assertTrue(screen.contains("legacyProgressionDraft.reconcile(finalized, requireNotNull(progressionContext))"))
        assertFalse(text("data/LegacyProgressionDraft.kt").contains("GeneratedProgramSkeleton"))
        assertFalse(text("data/ProgramExecutionDraft.kt").contains("Legacy"))
        assertTrue(text("data/ProgramProgressionDraft.kt").contains("ProgramExecutionDraft.reconcileProgression"))
        assertTrue(text("data/ProgramProgressionDraft.kt").contains("ProgramExecutionDraft.configureProgressionSession"))
    }

    @Test fun temporalPresentationBypassesGenericTokenTranslation() {
        listOf("PlanGeneratedPreview.kt", "LegacyAutoPreview.kt").forEach {
            val content = text(it)
            assertFalse(content.contains("dayName("))
            assertFalse(content.contains("horizontalScroll"))
            assertTrue(content.contains("ProgramTemporalSelector"))
            assertTrue(content.contains("MaterialText(programWeekdayLabel"))
        }
        val selectors = text("ProgramTemporalSelectors.kt")
        assertTrue(selectors.contains("localizedWeekday(DayOfWeek.of(day))"))
        assertTrue(selectors.contains("R.string.program_week_number"))
        assertFalse(selectors.contains("localizedUiText"))
        assertFalse(selectors.contains("horizontalScroll"))
        val temporalRow = selectors.substringBefore("internal fun programWeekLabel")
        assertFalse(temporalRow.contains("FlowRow("))
        assertFalse(temporalRow.contains("OutlinedButton("))
        assertTrue(temporalRow.contains("Modifier.weight(1f, fill = false).widthIn(max = 40.dp)"))
    }
}
