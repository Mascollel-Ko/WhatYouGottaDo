package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EligibleIncumbentRankingTest {
    private fun registry(): StrengthPerformanceRegistry {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        val assets = File(root, "app/src/main/assets/strength_performance")
        return StrengthPerformanceRegistry.fromCsv(File(assets, "strength_target_registry_v1.csv").readText(),
            File(assets, "strength_proxy_loadings_v1.csv").readText())
    }
    private fun anchor(key: String, group: String = key, sessions: Int = 2) =
        UserAnchor(key, "not semantic authority", sessions, sessions * 3, group, "LOAD_REPS", "UNKNOWN", sessions * 2.0 + sessions * 3 * .15 + 1)

    @Test fun directBonusComesOnlyFromEnabledCanonicalTarget() {
        val registry = registry()
        val target = registry.targets().first { it.closeVariationStableKeys.isNotEmpty() }
        val direct = target.anchorStableKeys.first()
        val proxy = target.closeVariationStableKeys.first { registry.directTarget(it) == null }
        val directKeys = registry.targets().flatMap { it.anchorStableKeys }.toSet()
        val ranked = rankEligibleIncumbents(listOf(anchor(direct), anchor(proxy)), directKeys)
        assertTrue(registry.loading(direct, target.targetKey)!!.isDirectAnchor)
        assertEquals(2.0, ranked.first { it.anchor.stableKey == direct }.directAnchorBonus, 0.0)
        assertEquals(0.0, ranked.first { it.anchor.stableKey == proxy }.directAnchorBonus, 0.0)
        ranked.forEach { assertEquals(it.baseScore + it.directAnchorBonus, it.anchor.score, 0.0) }
    }
    @Test fun allTwelveRemainAndDirectAnchorIsNotForcedIntoBase() {
        val direct = registry().targets().first().anchorStableKeys.first()
        val ranked = rankEligibleIncumbents((1..11).map { anchor("fixture_$it", sessions = 20) } + anchor(direct), setOf(direct))
        assertEquals(12, ranked.size)
        assertEquals(9, ranked.count { it.baseSelected })
        assertEquals(12, ranked.last().globalPreCutRank)
        assertFalse(ranked.last().baseSelected)
        assertEquals(direct, ranked.last().anchor.stableKey)
        assertEquals(IncumbentCutReason.GLOBAL_ANCHOR_CUTOFF, ranked.last().cutReason)
    }
    @Test fun thirdMovementCandidateRemainsTraceableWithStableTieBreak() {
        val ranked = rankEligibleIncumbents(listOf("c", "b", "a").map { anchor(it, "same") }, emptySet())
        assertEquals(listOf("a", "b", "c"), ranked.map { it.anchor.stableKey })
        assertEquals(listOf(1, 2, 3), ranked.map { it.movementRank })
        assertEquals(IncumbentCutReason.MOVEMENT_ANCHOR_CUTOFF, ranked.last().cutReason)
    }
    @Test fun builderPreservesEligibilityAndAllPreCutCandidates() {
        val f = PostGenerationFixture.snapshot()
        val keys = (1..12).map { "candidate_$it" }
        val source = f.allConfirmedSets.first()
        val snapshot = f.copy(exercises = keys.associateWith { f.exercises.getValue("press").copy(stableKey = it) },
            metadata = keys.associateWith { f.metadata.getValue("press") },
            allConfirmedSets = keys.flatMap { key -> listOf(source.copy(stableKey = key), source.copy(stableKey = key, date = source.date.minusDays(1))) } +
                source.copy(stableKey = "single_session"))
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        assertEquals(12, state.fullEligibleIncumbentRanking.size)
        assertEquals(2, state.anchors.size)
        assertFalse(state.fullEligibleIncumbentRanking.any { it.anchor.stableKey == "single_session" })
    }
}
