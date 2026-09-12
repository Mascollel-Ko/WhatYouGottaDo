package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StrengthPrimaryMainPolicyTest {
    private val f = PostGenerationFixture
    private val keys = listOf("barbell_back_squat", "barbell_bench_press", "ex_a61f1e96",
        "barbell_deadlift", "ex_e41f4c2b")
    private fun rows(keys: List<String>) = keys.map { TimedPlannedExercise(f.source(it, 1), f.rx(1)) }
    @Test fun completeCanonicalInventoryContainsExactlyTheApprovedFive() {
        val root=generateSequence(File(System.getProperty("user.dir")),File::getParentFile).first { File(it,"settings.gradle.kts").isFile }
        val inventory=File(root,"app/src/main/assets/metadata/canonical_v1/runtime_metadata.csv").readLines().drop(1).map { it.substringBefore(',') }
        assertEquals(keys.toSet(),inventory.filter(StrengthPrimaryMainPolicy::isPrimary).toSet())
    }
    private fun place(keys: List<String>, days: Int): Map<Int, List<TimedPlannedExercise>> {
        val source = rows(keys)
        val initial = (1..days).associateWith { if (it == 1) source else emptyList() }
        val result = InitialMainPlacement.review(initial, 90, null, true) { true }
        assertEquals(source.toSet(), result.values.flatten().toSet())
        assertEquals(initial.keys, result.keys)
        assertEquals(result, InitialMainPlacement.review(initial, 90, null, true) { true })
        return result
    }

    @Test fun exactFiveKeysNotProgressionRoleEquipmentProxyOrName() {
        keys.forEach { assertTrue(it, StrengthPrimaryMainPolicy.isPrimary(it)) }
        for (key in listOf("barbell_romanian_deadlift", "cable_pallof_press", "ex_5ca7133f",
            "ex_de46b7f6", "ex_c5043892", "ex_ab468462", "ex_e9e97659", "ex_32219f7a",
            "Squat", "Weighted Pull-up", "ex_e41f4c2b_variant", "BARBELL_BACK_SQUAT", "")) {
            assertFalse(key, StrengthPrimaryMainPolicy.isPrimary(key))
            assertEquals(ProgressionRole.MAIN, MainSchedulingPolicy.role(f.source(key), true))
        }
        assertTrue(StrengthPrimaryMainPolicy.isPrimary(f.row("squat", 1).copy(exerciseStableKey=keys.first(), exerciseName = "arbitrary renamed label").exerciseStableKey))
    }

    @Test fun fourPrimariesAcrossFourAndFiveDaysHaveZeroOverlap() {
        for (days in listOf(4, 5)) {
            val result = place(keys.take(4), days)
            assertEquals(0, StrengthPrimaryMainPolicy.counts(result.values.map { it.size }).overlap)
        }
    }

    @Test fun fourAcrossThreeAndTwoDaysKeepMinimumOverlapAndEveryPrescription() {
        assertEquals(listOf(1, 1, 2), place(keys.take(4), 3).values.map { it.size }.sorted())
        assertEquals(listOf(2, 2), place(keys.take(4), 2).values.map { it.size }.sorted())
    }

    @Test fun benchInclineSquatDeadliftAndPullupInclineSeparate() {
        for (pair in listOf(listOf(keys[1], keys[2]), listOf(keys[0], keys[3]), listOf(keys[4], keys[2]))) {
            assertEquals(listOf(1, 1), place(pair, 2).values.map { it.size })
        }
    }

    @Test fun calfMainDoesNotConsumePrimarySlotAndBroadOnlyLayoutIsUnchanged() {
        val calf = f.row("other", 1).copy(exerciseStableKey="ex_5ca7133f", progressionRole = ProgressionRole.MAIN)
        val squat = f.row("squat", 1).copy(exerciseStableKey=keys[0], progressionRole = ProgressionRole.MAIN)
        assertEquals(StrengthPrimaryObjective(0, 1), StrengthPrimaryMainPolicy.objective(listOf(calf, squat), listOf(1, 2)))
        val initial = mapOf(1 to rows(listOf("calf", "badminton_drill")), 2 to emptyList())
        assertSame(initial, InitialMainPlacement.review(initial, 90, null, true) { true })
    }

    @Test fun structuredPrimaryCannotBeMovedToManufactureSeparation() {
        for (style in listOf(StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING,StrengthProgrammingStyle.TOP_SET_BACKOFF,
            StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM,StrengthProgrammingStyle.DUP_LIKE_UNDULATING)) {
            val source = rows(keys.take(2)).map { it.copy(item = it.item.copy(style = style, styleVariant = "HEAVY")) }
            val initial = mapOf(1 to source, 2 to emptyList())
            assertSame(initial, InitialMainPlacement.review(initial, 90, null, true) { true })
        }
    }

    @Test fun initialPrimarySearchPreservesHardOfiAndChronologicalTissueFailures() {
        val snapshot=f.snapshot().let { old -> old.copy(
            exercises=old.exercises + keys.take(2).associateWith { old.exercises.getValue("press").copy(stableKey=it) },
            metadata=old.metadata + keys.take(2).associateWith { old.metadata.getValue("press") }) }
        val initial=mapOf(1 to rows(keys.take(2)),2 to emptyList())
        val ofi=snapshot.copy(planDayProjection=PlanDayProjection { StandaloneDayLoad(99,listOf(0)) })
        assertSame(initial,InitialMainPlacement.review(initial,90,ofi,true) { true })
        val tissue=snapshot.copy(planDayProjection=f.safe,planWeekTissueProjection=PlanWeekTissueProjection { _,_ ->
            PlannedTissueWeek(listOf(PlannedTissueDay(2,"",setOf("blocked"),emptySet(),null,null))) })
        assertSame(initial,InitialMainPlacement.review(initial,90,tissue,true) { true })
    }
}
