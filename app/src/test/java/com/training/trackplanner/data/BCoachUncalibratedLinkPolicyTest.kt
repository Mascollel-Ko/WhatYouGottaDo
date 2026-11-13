package com.training.trackplanner.data

import org.junit.Assert.*
import org.junit.Test

class BCoachUncalibratedLinkPolicyTest {
    private fun signature(
        key: String = "barbell_back_squat",
        reps: String = "6|6|6",
        intensity: String = "MODERATE",
        slot: String = "FULL_BODY_BADMINTON_SUPPORT",
        policy: ProgressionBase = ProgressionBase.REVIEW,
        base: Double? = null,
        role: ProgressionRole = ProgressionRole.AUTO
    ) = ProgressionSignature(
        exerciseStableKey=key, setCount=3, repsPattern=reps, baseKg=base,
        oneRmSnapshotKg=null, relativeIntensity=null,
        style="",variant="",trainingSlot=slot,dayIntensity=intensity,
        basePolicy=policy,anchorSetIndex=null,plannerRole=role
    )

    @Test fun `only explicit B generation metadata activates uncalibrated linking`() {
        assertFalse(BCoachUncalibratedLinkPolicy.isBProgram(null))
        assertFalse(BCoachUncalibratedLinkPolicy.isBProgram("{}"))
        assertFalse(BCoachUncalibratedLinkPolicy.isBProgram("{\"kind\":\"legacy\"}"))
        assertTrue(BCoachUncalibratedLinkPolicy.isBProgram(
            "{\"bCoachProgressionEquipmentSchema\":\"linked-g-1\",\"bCoachPlateOneSideKg\":0.75}"
        ))
    }

    @Test fun `matching uncalibrated B squat can share one session`() {
        assertTrue(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(
            signature(),signature()
        ))
    }

    @Test fun `another exercise or training purpose cannot silently join`() {
        val initial=signature()
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(initial,
            signature(key="barbell_deadlift")))
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(initial,
            signature(reps="8|8|8")))
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(initial,
            signature(intensity="HEAVY")))
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(initial,
            signature(slot="LOWER_STRENGTH")))
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(initial,
            signature(role=ProgressionRole.ASSISTANCE)))
    }

    @Test fun BRepeatedExerciseLinksDifferentWeekRepsButNeverCrossesWeekdayOrRole() {
        val one = signature(key="dumbbell_calf_raise",reps="6|6|6")
        val two = signature(key="dumbbell_calf_raise",reps="10|10|10")
        assertTrue(BCoachUncalibratedLinkPolicy.sameBRepeatedExercise(one,two,1,1))
        assertFalse(BCoachUncalibratedLinkPolicy.sameBRepeatedExercise(one,two,1,3))
        assertFalse(BCoachUncalibratedLinkPolicy.sameBRepeatedExercise(one,
            signature(key="dumbbell_calf_raise",reps="10|10|10",
                role=ProgressionRole.MAIN),1,1))
        assertFalse(BCoachUncalibratedLinkPolicy.sameBRepeatedExercise(one,
            signature(key="dumbbell_other_calf_raise",reps="10|10|10"),1,1))
    }

    @Test fun `weighted and unweighted tracks never auto-collapse`() {
        assertFalse(BCoachUncalibratedLinkPolicy.sameUncalibratedIntent(signature(),
            signature(policy=ProgressionBase.UNIFORM,base=135.0)))
    }
}
