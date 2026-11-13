package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.*
import com.training.trackplanner.data.v22.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProgramEditScopeTest {
    private val f=PostGenerationFixture
    private val all=ProgramEditScope.ALL_WEEKS
    private fun plan(weeks: Int)=f.plan(listOf(f.row("press",1,id="press"),f.row("row",1,id="row",order=2)),listOf(1,3,5),horizon=weeks)
        .let { p -> p.copy(items=p.items.map { item -> item.copy(weightKg=10.0,
            setPrescriptions=f.rx(item.setCount).sets.map { set -> set.copy(weightKg=10.0) }) }) }
    @Test fun explicitWeekOneCopyReallyUnifiesFourWeeksIncludingExerciseNamesAndSets() {
        val base=plan(4)
        val altered=base.copy(
            planningMetadataJson = """{"kind":"B_BALANCED_STANDARD"}""",
            items=base.items.map { row ->
                if(row.weekNumber==2 && row.exerciseStableKey=="press")
                    row.copy(exerciseStableKey="different_calf_raise", exerciseName="Calf",
                        reps=20,setCount=2) else row
            }
        )
        assertTrue(altered.items.any { it.exerciseStableKey=="different_calf_raise" })
        val next=ProgramScopedEditor.copyBWeekToAll(altered,1)
        assertEquals(8,next.items.size)
        assertEquals(8,next.items.map { it.localId }.distinct().size)
        assertFalse(next.items.any { it.exerciseStableKey=="different_calf_raise" })
        for(week in 2..4) {
            val expected=next.items.filter { it.weekNumber==1 }.sortedBy { it.orderIndex }
            val actual=next.items.filter { it.weekNumber==week }.sortedBy { it.orderIndex }
            assertEquals(expected.map { it.exerciseStableKey },actual.map { it.exerciseStableKey })
            assertEquals(expected.map { it.reps },actual.map { it.reps })
            assertEquals(expected.map { it.setCount },actual.map { it.setCount })
            assertEquals(expected.map { it.setPrescriptions },actual.map { it.setPrescriptions })
        }
        assertEquals(1,next.weekDaySchedule.values.distinct().size)
    }

    @Test fun editScopeSelectionNeverCopiesWeeksWithoutExplicitConfirmedAction() {
        val base=plan(4).copy(
            planningMetadataJson = """{"kind":"B_BALANCED_STANDARD"}"""
        )
        val altered=base.copy(items=base.items.map {
            if(it.weekNumber==3) it.copy(reps=18) else it
        })
        assertNotEquals(altered.items.filter { it.weekNumber==1 }.map { it.reps },
            altered.items.filter { it.weekNumber==3 }.map { it.reps })
        // User selecting edit scope does not mutate the skeleton.
        assertTrue(ProgramScopedEditor.supportsAll(altered))
        assertEquals(18,altered.items.first { it.weekNumber==3 }.reps)
        val synchronized=ProgramScopedEditor.copyBWeekToAll(altered,1)
        assertEquals(synchronized.items.filter { it.weekNumber==1 }.map { it.reps },
            synchronized.items.filter { it.weekNumber==3 }.map { it.reps })
    }

    @Test fun nonBPlansCannotUseDestructiveFullWeekCopy() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgramScopedEditor.copyBWeekToAll(plan(4),1)
        }
    }

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
        assertTrue(ProgramScopedEditor.supportsAll(moved))
        assertFalse(ProgramScopedEditor.canEditAll(moved,"w1_press"))
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
        assertTrue(ProgramScopedEditor.supportsAll(duplicate))
        assertFalse(ProgramScopedEditor.canEditAll(duplicate,"w1_press"))
    }
    @Test fun exactPrescriptionDifferencesAndMissingWeekRejectOnlyThatExercise() {
        val base=plan(4)
        fun changed(week: Int, key: String="press", change: (ProgramSkeletonItem)->ProgramSkeletonItem) =
            base.copy(items=base.items.map { if(it.weekNumber==week && it.exerciseStableKey==key) change(it) else it })
        val cases=listOf(
            changed(3) { item -> item.copy(weightKg=12.5,setPrescriptions=item.setPrescriptions.map { it.copy(weightKg=12.5) }) },
            changed(2) { item -> item.copy(reps=9,setPrescriptions=item.setPrescriptions.map { it.copy(reps=9) }) },
            changed(4) { item -> item.copy(setCount=1,setPrescriptions=item.setPrescriptions.take(1)) },
            base.copy(items=base.items.filterNot { it.weekNumber==3 && it.exerciseStableKey=="press" }),
            changed(3) { item -> item.copy(setPrescriptions=item.setPrescriptions.mapIndexed { index,set -> if(index==0) set.copy(weightKg=11.0) else set }) },
            changed(2) { item -> item.copy(stableKey="different-logical-id") },
            changed(2) { item -> item.copy(setPrescriptions=item.setPrescriptions.map { it.copy(weightKg=0.0,loadState=ProgramLoadState.USER_CALIBRATION_REQUIRED) }) }
        )
        cases.forEach { draft ->
            assertFalse(ProgramScopedEditor.canEditAll(draft,"w1_press"))
            assertTrue(ProgramScopedEditor.canEditAll(draft,"w1_row"))
            assertThrows(IllegalArgumentException::class.java) { ProgramScopedEditor.delete(draft,"w1_press",all) }
        }
        val partial=cases.first()
        val row=partial.items.single { it.localId=="w1_row" }
        val rowSets=row.setPrescriptions.map { it.copy(reps=12) }
        val updated=ProgramScopedEditor.update(partial,row.localId,row.copy(reps=12,setPrescriptions=rowSets),all)
        assertTrue(updated.items.filter { it.exerciseStableKey=="row" }.all { it.setPrescriptions==rowSets })
    }
    @Test fun unsetLoadAndExplicitZeroAreDifferent() {
        val base=plan(4)
        val zero=base.copy(items=base.items.map { item ->
            if(item.weekNumber!=2 || item.exerciseStableKey!="press") item else item.copy(
                weightKg=0.0,setPrescriptions=item.setPrescriptions.map {
                    it.copy(weightKg=0.0,loadState=ProgramLoadState.REAL_ZERO_LOAD)
                })
        })
        val unset=zero.copy(items=zero.items.map { item ->
            if(item.exerciseStableKey!="press" || item.weekNumber==2) item else item.copy(
                weightKg=0.0,setPrescriptions=item.setPrescriptions.map {
                    it.copy(weightKg=0.0,loadState=ProgramLoadState.USER_CALIBRATION_REQUIRED)
                })
        })
        assertFalse(ProgramScopedEditor.canEditAll(unset,"w1_press"))
    }
    @Test fun numericEditCopiesSetsRepsWeightsAndTimeToEveryWeek() {
        val base=plan(4)
        val source=base.items.single { it.localId=="w1_press" }
        val sets=listOf(
            ProgramSetPrescription(1,6,20.0,30,loadState=ProgramLoadState.EXPLICIT_LOAD),
            ProgramSetPrescription(2,7,22.5,35,loadState=ProgramLoadState.EXPLICIT_LOAD),
            ProgramSetPrescription(3,8,25.0,40,loadState=ProgramLoadState.EXPLICIT_LOAD)
        )
        val edited=source.copy(setCount=3,reps=0,weightKg=0.0,seconds=0,restSeconds=90,setPrescriptions=sets)
        val result=ProgramScopedEditor.update(base,source.localId,edited,all)
        val rows=result.items.filter { it.exerciseStableKey=="press" }
        assertEquals(4,rows.size)
        assertTrue(rows.all { it.setCount==3 && it.restSeconds==90 && it.setPrescriptions==sets })
        assertEquals(base.items.filter { it.exerciseStableKey=="row" },result.items.filter { it.exerciseStableKey=="row" })
    }
    @Test fun nonSteadyBlocksForceIndividualWeekWhileSteadyOffersAllWeeks() {
        fun withPattern(pattern: BVolumePattern)=plan(4).copy(planningMetadataJson=JSONObject()
            .put("schema",BBalanced12WeekEngine.BLOCK_SCHEMA)
            .put("blockSpecs",JSONArray().put(JSONObject().put("pattern",pattern.name))).toString())
        assertTrue(ProgramScopedEditor.supportsAll(withPattern(BVolumePattern.STEADY)))
        for(pattern in listOf(BVolumePattern.PROGRESSIVE,BVolumePattern.WAVE,BVolumePattern.ACCUMULATE_RECOVER)) {
            val draft=withPattern(pattern)
            assertFalse(ProgramScopedEditor.supportsAll(draft))
            assertFalse(ProgramScopedEditor.canEditAll(draft,"w1_press"))
        }
    }
}
