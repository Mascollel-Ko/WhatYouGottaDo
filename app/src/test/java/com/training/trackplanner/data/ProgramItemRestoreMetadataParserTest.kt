package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramItemRestoreMetadataParserTest {
    @Test
    fun validPrescriptionRestoresAllMetadata() {
        val result = ProgramItemRestoreMetadataParser.parse(
            "SLOT:LOWER_STRENGTH_HEAVY · DAY:HARD · DIRECT_HISTORY_HIGH"
        )

        assertTrue(result is ProgramItemRestoreMetadataParseResult.Success)
        assertEquals("LOWER_STRENGTH_HEAVY", result.metadata.trainingSlot)
        assertEquals("HARD", result.metadata.dayIntensity)
        assertEquals("DIRECT_HISTORY_HIGH", result.metadata.weightSource)
    }

    @Test
    fun missingSlotReportsFallback() {
        val result = ProgramItemRestoreMetadataParser.parse(
            "DAY:LIGHT · MANUAL_INPUT"
        )

        assertTrue(result is ProgramItemRestoreMetadataParseResult.PartialFallback)
        assertEquals("FULL_BODY_BADMINTON_SUPPORT", result.metadata.trainingSlot)
        assertEquals(
            setOf(ProgramItemRestoreMetadataField.TRAINING_SLOT),
            result.fallbackFields
        )
    }

    @Test
    fun missingDayReportsFallback() {
        val result = ProgramItemRestoreMetadataParser.parse(
            "SLOT:UPPER_STRENGTH · RULE_TABLE"
        )

        assertTrue(result is ProgramItemRestoreMetadataParseResult.PartialFallback)
        assertEquals("MODERATE", result.metadata.dayIntensity)
        assertEquals(
            setOf(ProgramItemRestoreMetadataField.DAY_INTENSITY),
            result.fallbackFields
        )
    }

    @Test
    fun missingWeightSourceDoesNotUseDisplayTextAsMetadata() {
        val result = ProgramItemRestoreMetadataParser.parse(
            "SLOT:BADMINTON_TRANSFER · DAY:MODERATE · 기존 구성 · 3세트 x 8회 · RPE 7"
        )

        assertTrue(result is ProgramItemRestoreMetadataParseResult.PartialFallback)
        assertEquals("MANUAL_OR_EXISTING", result.metadata.weightSource)
        assertEquals(
            setOf(ProgramItemRestoreMetadataField.WEIGHT_SOURCE),
            result.fallbackFields
        )
    }

    @Test
    fun malformedLegacyPrescriptionFailsExplicitlyWithoutCrashing() {
        val result = ProgramItemRestoreMetadataParser.parse(
            "SLOT:LOWER_STRENGTH | DAY:HARD | unknown"
        )

        assertTrue(result is ProgramItemRestoreMetadataParseResult.Failed)
        assertEquals(ProgramItemRestoreMetadataField.entries.toSet(), result.fallbackFields)
    }

    @Test
    fun structuredMetadataIsSourceOfTruthWhenPrescriptionChanges() {
        val result = ProgramItemRestoreMetadataParser.resolve(
            item(
                prescription = "display text without metadata",
                trainingSlot = "UPPER_STRENGTH",
                dayIntensity = "LIGHT",
                weightSource = "DIRECT_HISTORY_MEDIUM"
            )
        )

        assertEquals("UPPER_STRENGTH", result.metadata.trainingSlot)
        assertEquals("LIGHT", result.metadata.dayIntensity)
        assertEquals("DIRECT_HISTORY_MEDIUM", result.metadata.weightSource)
        assertTrue(result.legacyFields.isEmpty())
        assertTrue(result.unresolvedFields.isEmpty())
    }

    @Test
    fun legacyRowsUsePrescriptionOnlyWhenStructuredMetadataIsNull() {
        val result = ProgramItemRestoreMetadataParser.resolve(
            item(
                prescription = "SLOT:BADMINTON_TRANSFER · DAY:HARD · RULE_TABLE"
            )
        )

        assertEquals("BADMINTON_TRANSFER", result.metadata.trainingSlot)
        assertEquals("HARD", result.metadata.dayIntensity)
        assertEquals("RULE_TABLE", result.metadata.weightSource)
        assertEquals(ProgramItemRestoreMetadataField.entries.toSet(), result.legacyFields)
        assertTrue(result.unresolvedFields.isEmpty())
    }

    @Test
    fun malformedLegacyRowsRemainExplicitlyUnresolved() {
        val result = ProgramItemRestoreMetadataParser.resolve(
            item(prescription = "legacy display only")
        )

        assertTrue(result.legacyFields.isEmpty())
        assertEquals(ProgramItemRestoreMetadataField.entries.toSet(), result.unresolvedFields)
    }

    private fun item(
        prescription: String,
        trainingSlot: String? = null,
        dayIntensity: String? = null,
        weightSource: String? = null
    ): TrainingProgramItem =
        TrainingProgramItem(
            programId = 1,
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseId = 1,
            exerciseName = "Test",
            category = "Strength",
            prescription = prescription,
            trainingSlot = trainingSlot,
            dayIntensity = dayIntensity,
            weightSource = weightSource
        )
}
