package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BCoachProgressionPolicyTest {
    private fun route(
        key: String,
        planner: ProgressionRole = ProgressionRole.AUTO,
        override: ProgressionRole = ProgressionRole.AUTO,
        link: ProgressionLinkMode = ProgressionLinkMode.AUTO
    ) = BCoachProgressionPolicy.route(key, planner, override, link)

    @Test fun canonicalFourUseTheirExistingModelRegardlessOfInferredRole() {
        for (key in listOf("barbell_back_squat", "barbell_deadlift", "barbell_bench_press", "ex_e41f4c2b")) {
            assertEquals(BCoachProgressionPolicy.Route.CANONICAL_FOUR, route(key))
        }
    }

    @Test fun autoLinkedAccessoryDoesNotBecomeMain() {
        assertEquals(BCoachProgressionPolicy.Route.FIXED_LOAD, route("dumbbell_curl"))
    }

    @Test fun plannerAndUserMainSelectGeneralRoute() {
        assertEquals(BCoachProgressionPolicy.Route.GENERAL_MAIN,
            route("ex_ab468462", planner = ProgressionRole.MAIN))
        assertEquals(BCoachProgressionPolicy.Route.GENERAL_MAIN,
            route("ex_ab468462", override = ProgressionRole.MAIN))
    }

    @Test fun userExistingLinkRequestsProgressionButSeparateDoesNot() {
        assertEquals(BCoachProgressionPolicy.Route.GENERAL_MAIN,
            route("ex_ab468462", link = ProgressionLinkMode.EXISTING))
        assertEquals(BCoachProgressionPolicy.Route.FIXED_LOAD,
            route("ex_ab468462", link = ProgressionLinkMode.SEPARATE))
    }

    @Test fun explicitAssistanceOverridesPlannerMain() {
        assertEquals(BCoachProgressionPolicy.Route.FIXED_LOAD,
            route("ex_ab468462", planner = ProgressionRole.MAIN, override = ProgressionRole.ASSISTANCE))
    }

    @Test fun sameExerciseNameDoesNotAuthorizeCanonicalModel() {
        assertEquals(BCoachProgressionPolicy.Route.FIXED_LOAD, route("squat"))
    }
}
