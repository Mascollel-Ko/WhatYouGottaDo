package com.training.trackplanner.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PlannerIsolationArchitectureTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
        .first { File(it, "settings.gradle.kts").isFile }
    private val source = File(root, "app/src/main/java/com/training/trackplanner")
    private fun text(path: String) = File(source, path).readText()

    @Test fun frozenPlannerImportsOnlyOwnTypesIdentityDaoAndNeutralRowNoticePrimitives() {
        val allowed = setOf("Exercise", "ExerciseDao", "ProgramSetPrescription", "ProgramOptimizationSummary",
            "ProgramUserNotice", "ProgramUserNoticeCode", "ProgramUserNoticeLevel")
        val forbidden = listOf("personalized.", "AthletePlanningState", "TrainingState", "AdaptationGap", "BlockIntent",
            "ExecutionAllocation", "PersonalizedProgramBuilder", "PersonalizedPlanningDecision", "reconcileProgression",
            "GeneratedProgramSkeleton", "ProgramSkeletonItem", "progressionStyle", "progressionVariant",
            "progressionAnchorSetIndex", "progressionBinding", "progressionSessions")
        File(source, "data/program/legacy").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            forbidden.forEach { assertFalse("${file.name}: $it", content.contains(it)) }
            Regex("(?m)^import com\\.training\\.trackplanner\\.([^\\n]+)").findAll(content).forEach {
                assertTrue("${file.name}: ${it.value}", it.groupValues[1].startsWith("data.program.legacy.") ||
                    it.groupValues[1].removePrefix("data.") in allowed)
            }
        }
    }

    @Test fun recordBasedTransitiveSourceDependenciesCannotReachFrozenPlanningInternals() {
        val files = source.walkTopDown().filter { it.extension == "kt" }.toList()
        val contents = files.associateWith(File::readText)
        val packages = contents.mapValues { Regex("(?m)^package ([\\w.]+)").find(it.value.removePrefix("\uFEFF"))!!.groupValues[1] }
        val declarations = mutableMapOf<String, File>()
        contents.forEach { (file, content) ->
            Regex("(?:class|object|interface|typealias)\\s+(\\w+)").findAll(content).forEach {
                declarations[packages.getValue(file) + "." + it.groupValues[1]] = file
            }
            // Follow imported/top-level bridge functions too, not only class constructors.
            Regex("(?m)^(?:(?:internal|public|private|suspend|inline|tailrec)\\s+)*fun\\s+(?:[\\w<>?]+\\.)?(\\w+)\\s*\\(")
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
        assertTrue(persistence.contains("progression.author(programId)"))
        assertFalse(persistence.contains("reconcileProgression"))
        listOf("ProgramAutoBuilder", "ProgramRuleTables", "ProgramSlotAllocator", "ProgramIntensityResolver",
            "ProgramExerciseSpec", "ProgramCandidateAuthority", "ProgramGenerationService").forEach {
            assertFalse("Old planning entrypoint returned: $it", File(source, "data/$it.kt").exists())
        }
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
    }
}
