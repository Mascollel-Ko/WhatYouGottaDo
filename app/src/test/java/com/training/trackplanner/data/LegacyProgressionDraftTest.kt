package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.*
import org.junit.Assert.*
import org.junit.Test

internal fun legacySessionSkeleton() = LegacyAutoProgramBuilder().build(frozenLegacyRequest(4, 4, 45, .5), frozenLegacyExercises())
internal fun LegacyProgressionDraft.choose(skeleton: LegacyAutoSkeleton, localId: String, link: ProgressionLinkMode,
    target: String? = null, role: ProgressionRole = ProgressionRole.MAIN, mode: ProgressionMode = ProgressionMode.CUSTOM,
    rule: ProgressionRule = ProgressionRule(incrementKg = 2.5)) =
    LegacyProgressionDraft.fromExecutionDraft(executionDraft(skeleton).configureProgressionSession(localId, link, target, role, mode, rule))

class LegacyProgressionDraftTest {
    @Test fun frozenOutputIsUnchangedAcrossEveryChoiceAndConfiguration() {
        val skeleton = legacySessionSkeleton()
        val before = frozenFingerprint(skeleton)
        val group = skeleton.items.groupBy { it.exerciseStableKey }.values.first { it.size >= 4 }
        val context = ProgressionDraftContext(setOf(group.first().exerciseStableKey), mapOf(group.first().exerciseStableKey to 150.0))
        var overlay = LegacyProgressionDraft().reconcile(skeleton, context)
        assertEquals(group.map { it.localId }.toSet(), overlay.bindings.keys)
        val first = group[0].localId; val second = group[1].localId
        val original = overlay.bindings.getValue(first).sessionKey
        overlay = overlay.choose(skeleton, first, ProgressionLinkMode.SEPARATE)
        val separate = overlay.bindings.getValue(first).sessionKey
        assertNotEquals(original, separate)
        overlay = overlay.choose(skeleton, second, ProgressionLinkMode.EXISTING, separate)
        assertEquals(separate, overlay.bindings.getValue(second).sessionKey)
        overlay = overlay.choose(skeleton, first, ProgressionLinkMode.OFF)
        assertEquals(ProgressionLinkMode.OFF, overlay.bindings.getValue(first).linkMode)
        assertEquals(separate, overlay.bindings.getValue(second).sessionKey)
        overlay = overlay.choose(skeleton, first, ProgressionLinkMode.AUTO, role = ProgressionRole.ASSISTANCE, mode = ProgressionMode.DIRECT)
        assertEquals(ProgressionLinkMode.AUTO, overlay.bindings.getValue(first).linkMode)
        assertEquals(overlay, overlay.reconcile(skeleton, context))
        assertEquals(before, frozenFingerprint(skeleton))
        assertTrue(overlay.bindings.values.all { it.signature.oneRmSnapshotKg == 150.0 })
    }

    @Test fun editsDeletionAndRegenerationDoNotSilentlyResetExplicitMembership() {
        val skeleton = legacySessionSkeleton()
        val group = skeleton.items.groupBy { it.exerciseStableKey }.values.first { it.size >= 3 }
        val context = ProgressionDraftContext(setOf(group.first().exerciseStableKey), emptyMap())
        val first = group[0]; val second = group[1]
        var overlay = LegacyProgressionDraft().reconcile(skeleton, context).choose(skeleton, first.localId, ProgressionLinkMode.SEPARATE)
        val key = overlay.bindings.getValue(first.localId).sessionKey
        overlay = overlay.choose(skeleton, second.localId, ProgressionLinkMode.EXISTING, key)
        val edited = skeleton.upsertDraftItem(second.copy(restSeconds = second.restSeconds + 10, prescription = "user note"))
            .copy(suggestedName = "renamed")
        assertEquals(overlay, overlay.reconcile(edited, context))
        val deleted = skeleton.deleteDraftItem(first.localId)
        val retained = overlay.reconcile(deleted, context)
        assertFalse(first.localId in retained.bindings)
        assertEquals(key, retained.bindings.getValue(second.localId).sessionKey)
        assertEquals(2.5, retained.sessions.single { it.key == key }.track.rule.incrementKg!!, 0.0)
        val regenerated = legacySessionSkeleton()
        assertEquals(frozenFingerprint(skeleton), frozenFingerprint(regenerated))
        val fresh = LegacyProgressionDraft().reconcile(regenerated, context)
        assertTrue(fresh.bindings.values.all { it.linkMode == ProgressionLinkMode.AUTO })
        assertTrue(fresh.sessions.none { it.key == key })
    }

    @Test fun exactIdentityAndEligibilityRemainRequired() {
        val skeleton = legacySessionSkeleton()
        val keys = skeleton.items.map { it.exerciseStableKey }.distinct().take(2).toSet()
        val overlay = LegacyProgressionDraft().reconcile(skeleton, ProgressionDraftContext(keys, emptyMap()))
        val a = skeleton.items.first { it.exerciseStableKey == keys.first() }
        val b = skeleton.items.first { it.exerciseStableKey != a.exerciseStableKey && it.exerciseStableKey in keys }
        assertTrue(runCatching { overlay.choose(skeleton, b.localId, ProgressionLinkMode.EXISTING, overlay.bindings.getValue(a.localId).sessionKey) }.isFailure)
        val replacement = skeleton.upsertDraftItem(a.copy(exerciseStableKey = "ineligible-exact-key"))
        val changed = overlay.reconcile(replacement, ProgressionDraftContext(keys, emptyMap()))
        assertFalse(a.localId in changed.bindings)
        assertTrue(LegacyProgressionDraft().reconcile(skeleton, ProgressionDraftContext(emptySet(), emptyMap())).bindings.isEmpty())
    }
}
