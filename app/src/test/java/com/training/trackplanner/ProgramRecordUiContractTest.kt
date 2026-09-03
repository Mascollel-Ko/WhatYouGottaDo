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
    fun `record based planner is a separate path that retains editor on failure`() {
        val screen = source("PlanScreen.kt")
        val viewModel = source("TrainingViewModel.kt")

        assertTrue(screen.contains("기록 기반 알고리듬 프로그램 만들기"))
        assertTrue(screen.indexOf("기록 기반 알고리듬 프로그램 만들기") > screen.indexOf("자동 골자 만들기"))
        assertTrue(screen.contains("viewModel.generatePersonalizedProgram"))
        assertTrue(viewModel.contains("repository.generatePersonalizedProgram"))
        assertFalse(viewModel.contains("generatePersonalizedProgram") && viewModel.contains("자동 생성으로 전환"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}
