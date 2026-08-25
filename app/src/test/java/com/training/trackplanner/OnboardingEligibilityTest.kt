package com.training.trackplanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingEligibilityTest {
    @Test
    fun `genuinely fresh install is eligible`() {
        assertTrue(OnboardingEligibility.shouldAutoStart(100L, 100L, OnboardingDecision.NONE))
    }

    @Test
    fun `existing install upgraded to onboarding version is not eligible`() {
        assertFalse(OnboardingEligibility.shouldAutoStart(100L, 200L, OnboardingDecision.NONE))
    }

    @Test
    fun `completed or skipped onboarding is not eligible again`() {
        assertFalse(OnboardingEligibility.shouldAutoStart(100L, 100L, OnboardingDecision.COMPLETED))
        assertFalse(OnboardingEligibility.shouldAutoStart(100L, 100L, OnboardingDecision.SKIPPED))
    }
}
