package com.training.trackplanner.data.personalized

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalMovementCoverageResolverTest {
    private val repository = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext()
    )

    @Test
    fun allSelectableIdentitiesGetAnExplicitCanonicalMovementAndRegionalQualityDecision() {
        val identities = repository.selectableIdentities()
        val metadata = repository.runtimeMetadataCatalog().all().associateBy { it.stableKey }
        val movementRelations = repository.movementRelations()
        val physical = repository.physicalQualityCatalog()
        val identitiesByKey = repository.identities().associateBy { it.stableKey }
        val identityPatterns = identitiesByKey.mapValues { it.value.movementPattern }
        val identityReviewStatuses = identitiesByKey.mapValues { it.value.reviewStatus }
        val decisions = CanonicalMovementCoverageResolver.resolveAll(
            metadata, movementRelations, identityPatterns, identityReviewStatuses
        )

        assertTrue("canonical catalog contains selectable identities", identities.isNotEmpty())
        assertEquals(metadata.keys, decisions.keys)
        assertTrue(identities.all { it.stableKey in decisions })

        val reportRows = identities.map { identity ->
            val runtime = metadata.getValue(identity.stableKey)
            val decision = decisions.getValue(identity.stableKey)
            val movement = movementRelations.filter {
                it.exerciseStableKey == identity.stableKey && it.domain == CanonicalRelationDomain.MOVEMENT
            }
            val quality = physical.relations(identity.stableKey)
            org.json.JSONObject()
                .put("stableKey", identity.stableKey)
                .put("exerciseName", runtime.exerciseName)
                .put("identityMovementPattern", identity.movementPattern)
                .put("identityReviewStatus", identity.reviewStatus)
                .put("programSlot", runtime.programSlot)
                .put("movementFamily", runtime.movementFamily)
                .put("movementSubtype", runtime.movementSubtype)
                .put("canonicalPatterns", org.json.JSONArray(movement.filter { it.relationType == "MOVEMENT_PATTERN" }
                    .map { org.json.JSONObject().put("value", it.relationValue).put("status", it.status) }))
                .put("familyRelations", org.json.JSONArray(movement.filter { it.relationType == "MOVEMENT_FAMILY" }
                    .map { org.json.JSONObject().put("value", it.relationValue).put("status", it.status) }))
                .put("jointActions", org.json.JSONArray(movement.filter { it.relationType == "JOINT_ACTION" }
                    .map { it.relationValue }.distinct().sorted()))
                .put("qualityRelations", org.json.JSONArray(quality.map { relation ->
                    org.json.JSONObject()
                        .put("quality", relation.qualityId.name)
                        .put("level", relation.relationLevel.name)
                        .put("region", relation.regionQualifier.name)
                        .put("mode", relation.modeQualifier.name)
                }))
                .put("effectiveMovement", decision.coverage.name)
                .put("directCoverageAdmissible", decision.directCoverageAdmissible)
                .put("reason", decision.reason)
                .put("regionalHypertrophyLedgerKeys", org.json.JSONArray(
                    regionalQualityEvidenceTargets(decision.coverage, quality).map(MovementCoverage::name)
                ))
        }
        val selectableDecisions = identities.map { decisions.getValue(it.stableKey) }
        val report = org.json.JSONObject()
            .put("selectableCount", identities.size)
            .put("decisionCounts", org.json.JSONObject(selectableDecisions.groupingBy { it.reason }.eachCount()))
            .put("directCoverageAdmissibleCountsByEffectiveCoverage", org.json.JSONObject(selectableDecisions
                .filter(CanonicalMovementCoverageDecision::directCoverageAdmissible)
                .groupingBy { it.coverage.name }.eachCount()))
            .put("notDirectMovementCoverageCount", selectableDecisions.count { !it.directCoverageAdmissible })
            .put("canonicalPatternConflictCount", selectableDecisions.count {
                it.reason == "IDENTITY_MASTER_MOVEMENT_PATTERN_CONFLICT" ||
                    it.reason == "MULTIPLE_INCOMPATIBLE_DIRECT_MOVEMENT_PATTERNS" ||
                    it.reason == "MOVEMENT_PATTERN_SLOT_OR_FAMILY_CONFLICT"
            })
            .put("canonicalReviewRequiredCount", selectableDecisions.count {
                it.reason == "MOVEMENT_PATTERN_REQUIRES_CANONICAL_REVIEW"
            })
            .put("nonMovementAccessoryOrRegionalCount", selectableDecisions.count {
                it.reason == "CANONICAL_PATTERN_IS_NOT_A_DIRECT_RESISTANCE_COVERAGE_TARGET" ||
                    it.reason == "NO_CANONICAL_MOVEMENT_PATTERN_OR_EXACT_REGION_SLOT"
            })
            .put("identities", org.json.JSONArray(reportRows))
        File("build/reports/c38-2-selectable-movement-semantics.json").apply {
            parentFile?.mkdirs()
            writeText(report.toString(2))
        }

        assertMovement("cable_rear_delt_fly", MovementCoverage.OTHER, decisions)
        assertMovement("dumbbell_rear_delt_fly", MovementCoverage.OTHER, decisions)
        assertMovement("ex_7176cbee", MovementCoverage.OTHER, decisions)
        assertMovement("ex_7deabeba", MovementCoverage.OTHER, decisions)
        assertMovement("ex_a60496be", MovementCoverage.OTHER, decisions)
        assertMovement("ex_93538692", MovementCoverage.OTHER, decisions)
        assertMovement("ex_b076570e", MovementCoverage.OTHER, decisions)
        assertMovement("ex_708e64ce", MovementCoverage.OTHER, decisions)
        assertMovement("ex_c365e5e2", MovementCoverage.OTHER, decisions)
        assertMovement("machine_chest_fly", MovementCoverage.OTHER, decisions)
        assertMovement("ex_fa31f7a6", MovementCoverage.HORIZONTAL_PULL, decisions)
        assertMovement("chest_supported_row_machine", MovementCoverage.OTHER, decisions)
        assertMovement("ex_6232f4bc", MovementCoverage.LOWER_KNEE, decisions)
        assertMovement("ex_389e0556", MovementCoverage.HORIZONTAL_PUSH, decisions)
        assertMovement("ex_dc9e5953", MovementCoverage.VERTICAL_PULL, decisions)
        assertMovement("ex_e41f4c2b", MovementCoverage.VERTICAL_PULL, decisions)
        assertMovement("ex_32219f7a", MovementCoverage.VERTICAL_PUSH, decisions)
        assertMovement("barbell_bench_press", MovementCoverage.HORIZONTAL_PUSH, decisions)
        assertMovement("ex_3d5719de", MovementCoverage.ARMS_BICEPS, decisions)
        assertMovement("ex_5c8751d2", MovementCoverage.CALVES, decisions)
        listOf(
            "dumbbell_spider_curl", "ez_bar_spider_curl", "standing_bodyweight_calf_raise",
            "standing_calf_raise_machine", "standing_dumbbell_calf_raise"
        ).forEach { key ->
            assertMovement(key, MovementCoverage.OTHER, decisions)
            assertEquals("unreviewed family/identity cannot receive exact-region direct coverage",
                "IDENTITY_MASTER_MOVEMENT_REVIEW_REQUIRED", decisions.getValue(key).reason)
        }
        assertTrue("no selectable identity marked REVIEW_REQUIRED receives direct coverage",
            identitiesByKey.values.filter { it.reviewStatus == "REVIEW_REQUIRED" }
                .none { decisions.getValue(it.stableKey).directCoverageAdmissible })
        assertEquals(
            "a split equipment identity with only REVIEW_REQUIRED movement metadata stays unresolved",
            "IDENTITY_MASTER_MOVEMENT_REVIEW_REQUIRED",
            decisions.getValue("chest_supported_row_machine").reason
        )

        val rearShoulder = physical.relations("cable_rear_delt_fly").single {
            it.qualityId == TrainableQuality.HYPERTROPHY &&
                it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                it.regionQualifier == PhysicalQualityRegion.SHOULDERS
        }
        val rearDecision = decisions.getValue("cable_rear_delt_fly")
        assertEquals(1.0, regionalQualityRelationCredit(
            MovementCoverage.VERTICAL_PUSH, rearDecision.coverage, rearShoulder.qualityId,
            rearShoulder.relationLevel, rearShoulder.regionQualifier
        ), 0.0)
        assertEquals(0.0, regionalQualityRelationCredit(
            MovementCoverage.UPPER_PULL, rearDecision.coverage, rearShoulder.qualityId,
            rearShoulder.relationLevel, rearShoulder.regionQualifier
        ), 0.0)
        val rearTargets = regionalQualityEvidenceTargets(rearDecision.coverage, physical.relations("cable_rear_delt_fly"))
        assertTrue(MovementCoverage.VERTICAL_PUSH in rearTargets)
        assertFalse(MovementCoverage.UPPER_PULL in rearTargets)

        val lateral = physical.relations("ex_93538692").single {
            it.qualityId == TrainableQuality.HYPERTROPHY &&
                it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                it.regionQualifier == PhysicalQualityRegion.SHOULDERS
        }
        assertEquals(1.0, regionalQualityRelationCredit(
            MovementCoverage.VERTICAL_PUSH, decisions.getValue("ex_93538692").coverage, lateral.qualityId,
            lateral.relationLevel, lateral.regionQualifier
        ), 0.0)
        assertEquals(MovementCoverage.OTHER, decisions.getValue("ex_93538692").coverage)

        val chestFly = physical.relations("machine_chest_fly").single {
            it.qualityId == TrainableQuality.HYPERTROPHY &&
                it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                it.regionQualifier == PhysicalQualityRegion.CHEST
        }
        assertEquals(1.0, regionalQualityRelationCredit(
            MovementCoverage.HORIZONTAL_PUSH, decisions.getValue("machine_chest_fly").coverage,
            chestFly.qualityId, chestFly.relationLevel, chestFly.regionQualifier
        ), 0.0)
        assertEquals(MovementCoverage.OTHER, decisions.getValue("machine_chest_fly").coverage)

        val pullover = physical.relations("ex_708e64ce").firstOrNull {
            it.qualityId == TrainableQuality.HYPERTROPHY &&
                it.regionQualifier == PhysicalQualityRegion.UPPER_PULL
        }
        assertNotNull("pullover regional quality evidence remains inspectable", pullover)
        assertTrue(MovementCoverage.UPPER_PULL in regionalQualityEvidenceTargets(
            decisions.getValue("ex_708e64ce").coverage, physical.relations("ex_708e64ce")
        ))
        assertFalse(MovementCoverage.VERTICAL_PULL in regionalQualityEvidenceTargets(
            decisions.getValue("ex_708e64ce").coverage, physical.relations("ex_708e64ce")
        ))
    }

    @Test
    fun productionSnapshotUsesCanonicalDecisionInsteadOfLegacyAccessorySlotPromotion() {
        val metadata = repository.runtimeMetadataCatalog().all().associateBy { it.stableKey }
        val identities = repository.identities().associateBy { it.stableKey }
        val decisions = CanonicalMovementCoverageResolver.resolveAll(
            metadata,
            repository.movementRelations(),
            identities.mapValues { it.value.movementPattern },
            identities.mapValues { it.value.reviewStatus }
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 10, 11),
            allConfirmedSets = emptyList(),
            exercises = emptyMap(),
            metadata = metadata,
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "MIXED",
            strengthTrainingYears = 0.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            canonicalMovementCoverage = decisions
        )

        assertEquals(MovementCoverage.OTHER, snapshot.movementCoverage("cable_rear_delt_fly"))
        assertEquals(MovementCoverage.OTHER, snapshot.movementCoverage("ex_93538692"))
        assertEquals(MovementCoverage.OTHER, snapshot.movementCoverage("ex_708e64ce"))
        assertEquals(MovementCoverage.OTHER, snapshot.movementCoverage("machine_chest_fly"))
        assertEquals(MovementCoverage.HORIZONTAL_PULL, snapshot.movementCoverage("ex_fa31f7a6"))
        assertEquals(MovementCoverage.VERTICAL_PULL, snapshot.movementCoverage("ex_dc9e5953"))
    }

    @Test
    fun runtimeSlotFamilyWithoutCanonicalPatternCannotGrantDirectMovementCoverage() {
        val metadata = repository.runtimeMetadataCatalog().all().associateBy { it.stableKey }
            .getValue("ex_fa31f7a6")
        val decision = CanonicalMovementCoverageResolver.resolve(
            metadata,
            emptyList(),
            identityMovementPattern = "HORIZONTAL_PULL"
        )
        assertEquals(MovementCoverage.OTHER, decision.coverage)
        assertFalse(decision.directCoverageAdmissible)
        assertEquals("IDENTITY_MASTER_MOVEMENT_PATTERN_CONFLICT", decision.reason)
    }

    private fun assertMovement(
        stableKey: String,
        expected: MovementCoverage,
        decisions: Map<String, CanonicalMovementCoverageDecision>
    ) {
        val actual = decisions[stableKey]
        assertNotNull("missing selectable movement decision for $stableKey", actual)
        assertEquals(stableKey, expected, actual!!.coverage)
        assertEquals(expected != MovementCoverage.OTHER, actual.directCoverageAdmissible)
    }
}
