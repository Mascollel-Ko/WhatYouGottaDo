package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import org.junit.Assert.*
import org.junit.Test

class ProgramEditScopeTest {
    private val f=PostGenerationFixture
    private val all=ProgramEditScope.ALL_WEEKS
    private fun plan(weeks: Int)=f.plan(listOf(f.row("press",1,id="press"),f.row("row",1,id="row",order=2)),listOf(1,3,5),horizon=weeks)
        .let { p -> p.copy(items=p.items.map { it.copy(weightKg=it.weekNumber*10.0,
            setPrescriptions=f.rx(it.weekNumber).sets.map { set -> set.copy(weightKg=it.weekNumber*10.0) }) }) }
    @Test fun fourAndFiveWeeksMoveKeepsEveryWeekPrescriptionIdentityAndDecision() {
        for(weeks in listOf(4,5)) {
            val original=plan(weeks)
            assertTrue(ProgramScopedEditor.supportsAll(original))
            val moved=ProgramScopedEditor.move(original,"w1_press",3,all)
            assertEquals(original.items.map { it.copy(dayOfWeek=0,orderIndex=0) },moved.items.map { it.copy(dayOfWeek=0,orderIndex=0) })
            assertTrue(moved.items.filter { it.exerciseStableKey=="press" }.all { it.dayOfWeek==3 })
            assertEquals(original.personalizedDecision,moved.personalizedDecision)
            assertEquals(original.progressionSessions,moved.progressionSessions)
            assertEquals(original.weekDaySchedule,moved.weekDaySchedule)
        }
    }
    @Test fun addDeleteReorderAndDayToggleAcrossFourAndFiveWeeks() {
        for(weeks in listOf(4,5)) {
            val original=plan(weeks)
            val added=ProgramScopedEditor.add(original,f.row("other",3,id="new"),all)
            assertEquals(weeks,added.items.count { it.exerciseStableKey=="other" })
            assertEquals(added.items.size,added.items.map { it.localId }.distinct().size)
            assertTrue(added.items.containsAll(original.items))
            val deleted=ProgramScopedEditor.delete(original,"w1_press",all)
            assertEquals(original.items.filter { it.exerciseStableKey=="row" },deleted.items)
            val reordered=ProgramScopedEditor.reorder(original,"w1_row",-1,all)
            assertTrue(reordered.items.filter { it.exerciseStableKey=="row" }.all { it.orderIndex==1 })
            assertEquals(original.items.map { it.copy(orderIndex=0) },reordered.items.map { it.copy(orderIndex=0) })
            val days=ProgramScopedEditor.days(original,1,setOf(1,3,5,6),all)
            assertTrue(days.weekDaySchedule.values.all { it==setOf(1,3,5,6) })
            assertEquals(original.items,days.items)
        }
    }
    @Test fun individualWeekChangesNothingElseAndDivergenceRejectsAllMode() {
        val original=plan(4)
        val moved=ProgramScopedEditor.move(original,"w2_press",3,ProgramEditScope.INDIVIDUAL_WEEK)
        assertEquals(original.items.filter { it.weekNumber!=2 },moved.items.filter { it.weekNumber!=2 })
        assertFalse(ProgramScopedEditor.supportsAll(moved))
        assertThrows(IllegalArgumentException::class.java) { ProgramScopedEditor.move(moved,"w1_press",3,all) }
        assertEquals(1,original.items.first().dayOfWeek)
    }
    @Test fun exactSplitOriginAndAllWeekLocalFieldsSurviveMoveOfOtherAtom() {
        val original=SplitParentProgressionTest().plan(9,3)
        val authority=original.personalizedDecision!!.authorizedScheduling!!
        assertTrue(ProgramScopedEditor.supportsAll(original))
        val row=original.items.first()
        val deleted=ProgramScopedEditor.delete(original,row.localId,all)
        assertEquals(authority,deleted.personalizedDecision!!.authorizedScheduling)
        assertEquals(original.items.filterNot { authority.localOrigins[it.localId]==authority.localOrigins[row.localId] },deleted.items)
    }
    @Test fun ambiguousRepeatedUnownedKeysNeverUseDisplayOrderAsIdentity() {
        val original=plan(4)
        val duplicate=original.copy(items=original.items+original.items.map { it.copy(localId=it.localId+"_second",orderIndex=it.orderIndex+2) })
        assertFalse(ProgramScopedEditor.supportsAll(duplicate))
    }
}
