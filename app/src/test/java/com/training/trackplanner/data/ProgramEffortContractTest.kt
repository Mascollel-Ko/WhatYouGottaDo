package com.training.trackplanner.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramEffortContractTest {
    @Test fun commonTargetIsCollapsedAndDisplayedOnce() {
        val display = programEffortDisplay(listOf(
            ProgramSetPrescription(2, 10, 70.0, 0, 7.0),
            ProgramSetPrescription(1, 10, 70.0, 0, 7.0)
        ))
        assertEquals(7.0, display.commonTargetRpeMin)
        assertEquals(listOf(null, null), display.perSetTargetRpeMin)
        assertEquals("RPE 7+", plannedRpeLabel(display.commonTargetRpeMin))
    }

    @Test fun mixedTargetsRemainPerSetAndNullTargetsRemainHidden() {
        val mixed = programEffortDisplay(listOf(
            ProgramSetPrescription(1, 8, 60.0, 0, 7.0),
            ProgramSetPrescription(2, 8, 55.0, 0, 8.0)
        ))
        assertNull(mixed.commonTargetRpeMin)
        assertEquals(listOf(7.0, 8.0), mixed.perSetTargetRpeMin)
        assertNull(plannedRpeLabel(null))
        assertNull(ProgramSetPrescription(1, 8, 60.0, 0, 11.0).validated().targetRpeMin)
    }

    @Test fun progressionPrescriptionWireRoundTripsTargetAndOldPayloadDefaultsNull() {
        val current = ProgramPrescriptionSet(
            entryId = 11L,
            setIndex = 2,
            originalReps = 8,
            originalKg = 60.0,
            originalSeconds = 0,
            plannedReps = 9,
            plannedKg = 62.5,
            plannedSeconds = 0,
            originalTargetRpeMin = 7.0,
            plannedTargetRpeMin = 8.0
        )
        val restored = ProgramProgressionWireCodec.programPrescriptionSet(ProgramProgressionWireCodec.encode(current))
        assertEquals(current, restored)
        val old = ProgramProgressionWireCodec.programPrescriptionSet(JSONObject()
            .put("plannedSetIndex", 2)
            .put("originalExists", true)
            .put("entryId", 11L)
            .put("setIndex", 2)
            .put("originalReps", 8)
            .put("originalKg", 60.0)
            .put("originalSeconds", 0)
            .put("plannedReps", 8)
            .put("plannedKg", 60.0)
            .put("plannedSeconds", 0))
        assertNull(old.originalTargetRpeMin)
        assertNull(old.plannedTargetRpeMin)
    }

    @Test fun prescriptionEqualityIncludesCanonicalTarget() {
        assertTrue(ProgramSetPrescription(1, 8, 60.0, 0, 7.0) != ProgramSetPrescription(1, 8, 60.0, 0, 8.0))
    }
}
