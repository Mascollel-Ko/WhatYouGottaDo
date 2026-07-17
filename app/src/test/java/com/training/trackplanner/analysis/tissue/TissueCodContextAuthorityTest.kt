package com.training.trackplanner.analysis.tissue

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueCodContextAuthorityTest {
    private val catalog by lazy { repository().catalog }
    private val resolver by lazy { TissueContextModifierResolver(catalog) }

    @Test
    fun approvedAuthorityHasExactTierAndLoadUnitCoverage() {
        assertEquals(22, catalog.codContextExerciseTiers.size)
        assertEquals(9, catalog.codContextExerciseTiers.count { it.factor == 1.09 })
        assertEquals(6, catalog.codContextExerciseTiers.count { it.factor == 1.06 })
        assertEquals(7, catalog.codContextExerciseTiers.count { it.factor == 1.04 })
        assertEquals(77, catalog.codContextLoadUnitEligibility.size)
        assertEquals(35, catalog.codContextLoadUnitEligibility.count { it.eligible })
        assertEquals(22 * 35, catalog.codContextModifierRules.size)
        assertFalse(catalog.codContextModifierRules.any { it.factor == 1.08 || it.factor == 1.10 })
        assertFalse(catalog.codContextLoadUnitEligibility.any {
            it.loadUnitStableKey.contains("LEFT", ignoreCase = true) ||
                it.loadUnitStableKey.contains("RIGHT", ignoreCase = true)
        })
    }

    @Test
    fun approvedDisplayNamesResolveToOneExactProductionStableKey() {
        catalog.codContextExerciseTiers.forEach { tier ->
            assertEquals(tier.displayNameKo, catalog.exerciseNamesByStableKey[tier.exerciseStableKey])
            assertEquals(
                1,
                catalog.exerciseNamesByStableKey.values.count { it == tier.displayNameKo }
            )
        }
    }

    @Test
    fun resolverAppliesTierOnlyToEligibleLowerLimbLoadUnits() {
        val eligibleKnee = "lu_ff22c06225"

        assertEquals(1.09, resolver.resolve("ex_bc84eb7f", eligibleKnee).factor, 0.0)
        assertEquals(1.06, resolver.resolve("ex_ae9ecdbc", eligibleKnee).factor, 0.0)
        assertEquals(1.04, resolver.resolve("ex_1a4858d7", eligibleKnee).factor, 0.0)
        assertEquals(
            TissueContextModifierStatus.APPLIED_APPROVED_COD_CONTEXT,
            resolver.resolve("ex_ae9ecdbc", eligibleKnee).status
        )

        listOf("lu_211533cb7a", "lu_d967354bce").forEach { ineligibleKey ->
            val resolution = resolver.resolve("ex_ae9ecdbc", ineligibleKey)
            assertEquals(1.0, resolution.factor, 0.0)
            assertEquals(
                TissueContextModifierStatus.DEFAULT_LOAD_UNIT_NOT_ELIGIBLE,
                resolution.status
            )
        }
    }

    @Test
    fun explicitNegativeExercisesRemainNeutral() {
        val negativeStableKeys = listOf(
            "ex_2055b629",
            "ex_c6d9efef",
            "ex_216351a1",
            "ex_34e7d21",
            "ex_314df428",
            "ex_d6726746",
            "ex_d9e141e4",
            "ex_df966b45",
            "ex_64644b5e",
            "ex_e3715c0b",
            "ex_faae2044",
            "ex_681d9ed2",
            "medicine_ball_three_step_acceleration_throw",
            "medicine_ball_three_step_deceleration_throw",
            "barbell_back_squat",
            "barbell_deadlift"
        )

        negativeStableKeys.forEach { stableKey ->
            val resolution = resolver.resolve(stableKey, "lu_ff22c06225")
            assertEquals(stableKey, 1.0, resolution.factor, 0.0)
            assertEquals(
                stableKey,
                TissueContextModifierStatus.DEFAULT_EXERCISE_NOT_WHITELISTED,
                resolution.status
            )
        }
    }

    @Test
    fun upperBodyAndSpinalLoadUnitsAreNeverEligible() {
        catalog.codContextLoadUnitEligibility.filter {
            it.bodyRegion in setOf(
                "SPINE",
                "SHOULDER",
                "SHOULDER_GIRDLE",
                "ELBOW",
                "FOREARM",
                "WRIST",
                "HAND"
            )
        }.forEach { row ->
            assertFalse(row.loadUnitStableKey, row.eligible)
        }
    }

    @Test
    fun invalidDuplicateUnknownAndBlankAuthorityFailsValidation() {
        val firstRule = catalog.codContextModifierRules.first()
        assertThrows(IllegalArgumentException::class.java) {
            TissueCodContextValidator.requireValid(
                catalog.copy(
                    codContextModifierRules = catalog.codContextModifierRules +
                        firstRule.copy(modifierRuleId = "${firstRule.modifierRuleId}_duplicate")
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TissueCodContextValidator.requireValid(
                catalog.copy(
                    codContextModifierRules = catalog.codContextModifierRules.mapIndexed { index, rule ->
                        if (index == 0) rule.copy(exerciseStableKey = "unknown_exercise") else rule
                    }
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TissueCodContextValidator.requireValid(
                catalog.copy(
                    codContextExerciseTiers = catalog.codContextExerciseTiers.mapIndexed { index, tier ->
                        if (index == 0) tier.copy(rationale = "") else tier
                    }
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TissueCodContextValidator.requireValid(
                catalog.copy(
                    codContextExerciseTiers = catalog.codContextExerciseTiers.mapIndexed { index, tier ->
                        if (index == 0) tier.copy(policyVersion = "") else tier
                    }
                )
            )
        }
    }

    @Test
    fun unknownResolverKeysFailClosedToNeutral() {
        val resolution = resolver.resolve("unknown_exercise", "unknown_load_unit")

        assertEquals(1.0, resolution.factor, 0.0)
        assertEquals(TissueContextModifierStatus.INVALID_UNKNOWN_KEY, resolution.status)
        assertTrue(resolution.diagnosticReason.isNotBlank())
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset)
        )

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
