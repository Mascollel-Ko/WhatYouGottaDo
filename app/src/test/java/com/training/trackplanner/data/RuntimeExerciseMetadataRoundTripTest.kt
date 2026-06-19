package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeExerciseMetadataRoundTripTest {
    @Test
    fun everyRuntimeFieldSurvivesEntityRoundTrip() {
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity("user_ex_round_trip", "Custom row").copy(
            movementFamily = "ROW_VARIANTS",
            movementSubtype = "ONE_ARM_CABLE_ROW",
            programSlot = "HORIZONTAL_PULL_ACCESSORY",
            redundancyGroup = "HORIZONTAL_PULL_COMPOUND",
            analysisEligibility = MetadataTokenField.parse("FATIGUE | STRENGTH_PROGRESS"),
            secondaryStressTags = MetadataTokenField.parse("LOCAL_MUSCLE | GRIP_FOREARM"),
            badmintonPhysicalQualities = MetadataTokenField.parse("GRIP_STRENGTH | SHOULDER_STABILITY")
        )

        assertEquals(metadata, metadata.toEntity().toRuntimeMetadata())
    }

    @Test
    fun tokenConverterPreservesRawTextAndOrderedValues() {
        val converter = RuntimeMetadataTypeConverters()
        val tokenField = MetadataTokenField(
            raw = " FATIGUE | FUTURE_TOKEN | FATIGUE ",
            values = listOf("FATIGUE", "FUTURE_TOKEN")
        )

        assertEquals(tokenField, converter.stringToTokenField(converter.tokenFieldToString(tokenField)))
    }
}
