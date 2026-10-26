package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRecordUiContractTest {
    @Test
    fun `calendar exposes an explicit confirmed range save flow`() {
        val source = source("RecordCalendarScreen.kt")

        assertTrue(source.contains("기간을 프로그램으로 저장"))
        assertTrue(source.contains("RecordRangeProgramDialog"))
        assertTrue(source.contains("완료 \${summary.confirmedSetCount}세트"))
        assertTrue(source.contains("계획 \${summary.unconfirmedSetCount}세트"))
        assertTrue(source.contains("viewModel.createProgramFromRecordRange"))
    }

    @Test
    fun `program open screen uses set cards and the existing exercise info dialog`() {
        val sections = source("PlanProgramSections.kt")
        val screen = source("PlanScreen.kt")

        assertTrue(sections.contains("ProgramSetPrescriptionResolver.resolve"))
        assertTrue(sections.contains("Icons.Default.Info"))
        assertTrue(sections.contains("R.string.exercise_info_content_description"))
        assertTrue(screen.contains("ExerciseInfoDialog("))
        assertTrue(screen.contains("viewModel.programItemSets(program.id)"))
        assertFalse(screen.contains("listOf(1 to 1)"))
    }

    @Test
    fun `program creation exposes only B 12 week and preserves existing editor`() {
        val screen = source("PlanScreen.kt")
        val viewModel = source("TrainingViewModel.kt")

        assertTrue(screen.contains("12주 균형형 프로그램 만들기"))
        assertTrue(screen.contains("viewModel.generateV22Program"))
        assertTrue(screen.contains("sessionMinutes = v22SessionMinutes"))
        assertTrue(screen.contains("days = v22Days"))
        assertTrue(screen.contains("allowFourthDay = bAllowFourthDay"))
        assertTrue(screen.contains("importance = BBalancedGoalImportance("))
        assertTrue(screen.contains("request = current.request.copy(name = name)"))
        assertFalse(screen.contains("기록 기반 알고리듬 프로그램 만들기"))
        assertFalse(screen.contains("빈 프로그램으로 시작"))
        assertFalse(screen.contains("viewModel.preparePersonalizedProgram"))
        assertFalse(screen.contains("viewModel.generatePreparedPersonalizedProgram"))
        assertFalse(screen.contains("generateLegacyAutoSkeleton"))
        assertFalse(screen.contains("saveLegacyAutoProgram"))
        assertFalse(screen.contains("showSkeletonOptions"))
        assertFalse(screen.contains("personalizedDurationOverride"))
        assertFalse(screen.contains("personalizedDaysOverride"))
        assertFalse(screen.contains("badmintonRatio"))
        assertFalse(screen.contains("durationWeeks by rememberSaveable"))
        assertFalse(screen.contains("weeklyDays by rememberSaveable"))
        assertTrue(viewModel.contains("repository.preparePersonalizedProgram"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}
