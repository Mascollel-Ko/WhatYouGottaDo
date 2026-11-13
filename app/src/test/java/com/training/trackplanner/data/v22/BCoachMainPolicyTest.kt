package com.training.trackplanner.data.v22

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class BCoachMainPolicyTest {
    private fun track(role: ProgressionRole = ProgressionRole.AUTO) =
        ProgramProgressionTrack(programStableKey = "b", exerciseStableKey = "dumbbell_curl",
            label = "Curl", role = ProgressionRole.MAIN, roleOverride = role)
    private fun item(id: Long, mode: ProgressionLinkMode = ProgressionLinkMode.AUTO,
                     planner: ProgressionRole = ProgressionRole.AUTO) =
        ProgramProgressionItem(id, trackId = "track", linkMode = mode,
            signature = ProgressionSignature("dumbbell_curl", 3, "8|8|8",
                null, null, null, plannerRole = planner))

    @Test fun autoOnlyAccessoryMustNotBecomeMainJustBecauseLegacyTrackRoleWasInferredMain() {
        assertEquals(ProgressionRole.ASSISTANCE, BCoachMainPolicy.appliedRole(
            "dumbbell_curl", track(), item(1), listOf(item(1), item(2))))
    }

    @Test fun manuallyConnectedOrExplicitMainCanUseGenericCurve() {
        assertEquals(ProgressionRole.MAIN, BCoachMainPolicy.appliedRole(
            "dumbbell_curl", track(), item(1), listOf(item(1),
                item(2, ProgressionLinkMode.EXISTING))))
        assertEquals(ProgressionRole.MAIN, BCoachMainPolicy.appliedRole(
            "dumbbell_curl", track(ProgressionRole.MAIN), item(1), listOf(item(1))))
        assertEquals(ProgressionRole.MAIN, BCoachMainPolicy.appliedRole(
            "dumbbell_curl", track(), item(1, planner = ProgressionRole.MAIN), listOf(item(1))))
    }

    @Test fun canonicalFourRetainMainAuthorityAndAssistanceOverrideIsHonored() {
        for (key in BCoachMainPolicy.canonicalFour) {
            assertEquals(ProgressionRole.MAIN,
                BCoachMainPolicy.appliedRole(key, track(), item(1), listOf(item(1))))
            assertEquals(ProgressionRole.ASSISTANCE,
                BCoachMainPolicy.appliedRole(key, track(ProgressionRole.ASSISTANCE),
                    item(1), listOf(item(1))))
        }
    }
}
