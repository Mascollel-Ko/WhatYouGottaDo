package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetadataAdapterTest {
    @Test
    fun pipeParserPreservesRawValueAndDeduplicatesTokens() {
        val parsed = MetadataTokenField.parse(" FATIGUE | CONTROL | FATIGUE || ")

        assertEquals(" FATIGUE | CONTROL | FATIGUE || ", parsed.raw)
        assertEquals(listOf("FATIGUE", "CONTROL"), parsed.values)
        assertTrue("fatigue" in parsed)
        assertTrue(MetadataTokenField.parse("NONE").values.isEmpty())
        assertTrue(MetadataTokenField.parse(" ").values.isEmpty())
    }

    @Test
    fun everyPass3ProgressMetricHasSafeRuntimeBehavior() {
        val pass3Values = setOf(
            "COUNT_ONLY",
            "DISTANCE_OR_TIME_LOAD",
            "ESTIMATED_1RM",
            "LOAD_REPS",
            "LOAD_REPS_OR_REPS",
            "LOAD_REPS_OR_TIME",
            "MACHINE_LOAD_REPS",
            "QUALITY_BASED",
            "QUALITY_LOAD_REPS",
            "REPS_AT_LOAD",
            "REPS_OR_TIME",
            "SESSION_DURATION",
            "TIME",
            "TIME_DISTANCE",
            "TIME_DISTANCE_PACE_OR_INTENSITY",
            "TIME_OR_COMPLETION",
            "TIME_OR_DISTANCE",
            "TIME_OR_REPS",
            "VOLUME_LOAD"
        )

        pass3Values.forEach { value ->
            assertNotEquals(
                value,
                ProgressMetricRuntimeBehavior.UNSUPPORTED_SAFE_NO_OP,
                ExerciseMetadataAdapter.progressMetricBehavior(value)
            )
        }
        assertEquals(
            ProgressMetricRuntimeBehavior.UNSUPPORTED_SAFE_NO_OP,
            ExerciseMetadataAdapter.progressMetricBehavior("FUTURE_METRIC")
        )
    }

    @Test
    fun everyPass3AnalysisTokenIsAcceptedAndUnknownTokensArePreserved() {
        val pass3Tokens = listOf(
            "ACCESSORY_VOLUME",
            "BADMINTON_SUPPORTIVE",
            "BADMINTON_TRANSFER",
            "BALANCE",
            "CONDITIONING",
            "CONTROL",
            "COORDINATION",
            "CORE_ACCESSORY",
            "CORE_STABILITY",
            "FATIGUE",
            "FATIGUE_LOW",
            "HYPERTROPHY_VOLUME",
            "MOBILITY",
            "PATTERN_QUALITY",
            "RECOVERY_CONTEXT",
            "RECOVERY_ONLY",
            "SESSION_LOAD",
            "SHOULDER_STABILITY",
            "SPORT_SESSION_LOAD",
            "STRENGTH_PROGRESS",
            "STRENGTH_VOLUME",
            "TEST_METRIC"
        )
        val raw = pass3Tokens.joinToString(" | ") + " | FUTURE_ANALYSIS_TOKEN"
        val parsed = ExerciseMetadataAdapter.fromFields(baseFields() + ("analysisEligibility" to raw))

        assertEquals(pass3Tokens + "FUTURE_ANALYSIS_TOKEN", parsed.analysisEligibility.values)
        assertEquals(
            AnalysisTokenRuntimeSupport.PRESERVED_SAFE_NO_OP,
            ExerciseMetadataAdapter.analysisTokenSupport("FUTURE_ANALYSIS_TOKEN")
        )
    }

    @Test
    fun stressTransferAndSourceFieldsRemainStringBacked() {
        val metadata = ExerciseMetadataAdapter.fromFields(baseFields())

        assertEquals("COURT_SPORT_MOVEMENT_STRESS", metadata.primaryStressProfile)
        assertEquals(listOf("DECELERATION", "ELASTIC_SSC"), metadata.secondaryStressTags.values)
        assertEquals(listOf("ACHILLES_TENDON", "PATELLAR_TENDON"), metadata.tendonStressTags.values)
        assertEquals(RuntimeBadmintonTransferLevel.DIRECT, metadata.transferLevel)
        assertEquals(listOf("FOOTWORK", "REACTION"), metadata.badmintonTransferType.values)
        assertEquals("VERIFIED_FAMILY", metadata.sourceConfidenceLevel)
        assertEquals("SOURCE_ACCEPTED", metadata.finalSourceStatus)
        assertFalse(metadata.safeForSeedMutation)
    }

    @Test
    fun catalogResolvesStableKeyBeforeRestrictedLegacyNameFallback() {
        val stableMatch = ExerciseMetadataAdapter.fromFields(baseFields())
        val nameMatch = ExerciseMetadataAdapter.fromFields(
            baseFields() + mapOf(
                "stableKey" to "other_key",
                "exerciseName" to "Fallback Name",
                "movementFamily" to "FALLBACK_FAMILY"
            )
        )
        val catalog = RuntimeExerciseMetadataCatalog.of(listOf(stableMatch, nameMatch))

        assertSame(stableMatch, catalog.resolve("court_test", "Fallback Name", allowNameFallback = false))
        assertEquals(null, catalog.resolve("missing", "Fallback Name", allowNameFallback = false))
        assertSame(nameMatch, catalog.resolve("imported_old", "Fallback Name", allowNameFallback = true))
    }

    @Test
    fun csvParserAcceptsCanonicalRowsAndQuotedValues() {
        val csv = """
            stableKey,exerciseName,movementFamily,analysisEligibility,safeForSeedMutation
            court_test,"Court, Test",BADMINTON_COURT_TEST,"TEST_METRIC | BADMINTON_TRANSFER",NO
        """.trimIndent()

        val metadata = ExerciseMetadataAdapter.fromCsv(csv).single()

        assertEquals("Court, Test", metadata.exerciseName)
        assertEquals("BADMINTON_COURT_TEST", metadata.movementFamily)
        assertEquals(listOf("TEST_METRIC", "BADMINTON_TRANSFER"), metadata.analysisEligibility.values)
        assertFalse(metadata.safeForSeedMutation)
    }

    @Test
    fun pass31StressAxesRemainAvailableToRuntimeConsumers() {
        val metadata = ExerciseMetadataAdapter.fromFields(
            baseFields() + mapOf(
                "currentActivityKind" to "SPORT_SESSION",
                "neuromuscularStressLevel" to "HIGH",
                "systemicMuscularStressLevel" to "MODERATE",
                "localMuscularStressLevel" to "HIGH",
                "jointTendonImpactStressLevel" to "HIGH",
                "movementFocusDemandLevel" to "VERY_HIGH",
                "recoveryDurationClass" to "LONG"
            )
        )

        assertEquals("SPORT_SESSION", metadata.activityKind)
        assertEquals("HIGH", metadata.neuromuscularStressLevel)
        assertEquals("MODERATE", metadata.systemicMuscularStressLevel)
        assertEquals("HIGH", metadata.localMuscularStressLevel)
        assertEquals("HIGH", metadata.jointTendonImpactStressLevel)
        assertEquals("VERY_HIGH", metadata.movementFocusDemandLevel)
        assertEquals("LONG", metadata.recoveryDurationClass)
    }

    private fun baseFields(): Map<String, String> = mapOf(
        "stableKey" to "court_test",
        "exerciseName" to "Court Test",
        "currentPlanningEligibility" to "ANALYSIS_ONLY",
        "movementFamily" to "BADMINTON_COURT_TEST",
        "movementSubtype" to "REPEATED_OVERHEAD_TEST",
        "programSlot" to "NOT_APPLICABLE",
        "redundancyGroup" to "BADMINTON_OVERHEAD_TEST",
        "progressMetricType" to "COUNT_ONLY",
        "strengthProgressionGroup" to "NOT_APPLICABLE",
        "analysisEligibility" to "TEST_METRIC | BADMINTON_TRANSFER",
        "primaryStressProfile" to "COURT_SPORT_MOVEMENT_STRESS",
        "secondaryStressTags" to "DECELERATION | ELASTIC_SSC",
        "tendonStressTags" to "ACHILLES_TENDON | PATELLAR_TENDON",
        "ligamentJointStabilityStressTags" to "ANKLE_STABILITY",
        "jointImpactStressTags" to "COURT_IMPACT",
        "cognitiveStressTags" to "REACTION_DECISION",
        "sportContextTags" to "BADMINTON_COURT",
        "recoveryDecayProfile" to "MEDIUM",
        "stressMagnitudeHint" to "HIGH",
        "badmintonTransferLevel" to "DIRECT",
        "badmintonTransferType" to "FOOTWORK | REACTION",
        "badmintonSkillTargets" to "SPLIT_STEP | OVERHEAD_REPETITION",
        "badmintonPhysicalQualities" to "DECELERATION | REACTION_SPEED",
        "transferConfidence" to "HIGH",
        "sourceConfidenceLevel" to "VERIFIED_FAMILY",
        "finalSourceStatus" to "SOURCE_ACCEPTED",
        "safeForSeedMutation" to "NO"
    )
}
