package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.validatedTargetRpeMin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusEffortThresholdTest {
    private val effort = TrainableQuality.HYPERTROPHY.canonicalEffortTarget()

    private fun prescription(targets: List<Double?>) = PlannedPrescription(
        text = "hypertrophy threshold",
        sets = targets.mapIndexed { index, target -> ProgramSetPrescription(index + 1, 10, 60.0, 0, target) },
        restSeconds = 120,
        weightSource = "TEST"
    )

    private fun authorization(value: PlannedPrescription) = StimulusPrescriptionAuthorization(
        targetId = "QUALITY:HYPERTROPHY",
        quality = TrainableQuality.HYPERTROPHY,
        owner = StimulusPrescriptionOwner("threshold.exercise", "B5_ROLE"),
        source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
        inputPrescription = value,
        plannedCompatibility = null,
        authorizedPrescription = value,
        status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
    )

    @Test
    fun numericValidityRemainsSeparateFromQualitySpecificSufficiency() {
        assertEquals(5.0, 5.0.validatedTargetRpeMin()!!, 0.0)
        assertNull(0.0.validatedTargetRpeMin())
        assertNull(11.0.validatedTargetRpeMin())
        assertNull(Double.NaN.validatedTargetRpeMin())
        assertNull(Double.POSITIVE_INFINITY.validatedTargetRpeMin())
        assertTrue(!ProgramSetPrescription(1, 10, 60.0, 0, 5.0).satisfiesEffortTarget(effort))
    }

    @Test
    fun hypertrophyThresholdUsesInclusiveMinimumAndRejectsMissingOrLowerTargets() {
        assertEquals(
            StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT,
            authorization(prescription(listOf(null))).executionAuthority
        )
        assertEquals(
            StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT,
            authorization(prescription(listOf(6.9))).executionAuthority
        )
        assertEquals(
            StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorization(prescription(listOf(7.0))).executionAuthority
        )
        assertEquals(
            StimulusPrescriptionExecutionAuthority.FULLY_ENCODED,
            authorization(prescription(listOf(8.0))).executionAuthority
        )
    }

    @Test
    fun everyFundedSetMustMeetTheThreshold() {
        val value = prescription(listOf(7.0, 7.0, 6.0))
        assertEquals(
            StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT,
            authorization(value).executionAuthority
        )
        assertTrue(!value.fullyEncodesEffort(effort))
    }
}
