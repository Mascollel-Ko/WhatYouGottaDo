package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC3MultidimensionalResearchTest {
    @Test
    fun packageReextractsEveryExistingSourceAndDispositionsEveryRevisedCandidate() {
        assertEquals(49, extractions.size)
        assertEquals(existingSources.map { it.sourceId }.toSet(), extractions.map { it.sourceId }.toSet().intersect(existingSources.map { it.sourceId }.toSet()))
        assertEquals(24, dispositions.size)
        assertEquals(revised.map { it.getValue("revisedClaimCandidateId") }.toSet(), dispositions.map { it.candidateId }.toSet())
        assertEquals(30, candidates.size)
        assertEquals(2, rubrics.size)
        assertEquals(48, decisions.size)
        assertEquals(49, correspondences.size)
    }

    @Test
    fun newSourcesAreDualVerifiedMatchedAndFreeOfIntegrityBlockers() {
        assertEquals(5, sourceVerifications.size)
        assertTrue(sourceVerifications.all { it.identifierVerificationStatus == TissueIdentifierVerificationStatus.PMID_AND_DOI_VERIFIED })
        assertTrue(sourceVerifications.all { it.bibliographicMatchStatus == TissueBibliographicMatchStatus.MATCHED })
        assertEquals(5, integrity.size)
        assertTrue(integrity.all { it.integrityCheckStatus == TissuePublicationIntegrityCheckStatus.NO_ADVERSE_NOTICE_FOUND })
    }

    @Test
    fun multidimensionalPackageIsCompatibleAndComplete() {
        val report = TissueC3ResearchValidator.packageData(
            extractions = extractions,
            dispositions = dispositions,
            candidates = candidates,
            rubrics = rubrics,
            decisions = decisions,
            correspondences = correspondences,
            dimensions = dimensions,
            catalog = catalog,
            sourceIds = existingSources.map { it.sourceId }.toSet() + newSourceRows.map { it.getValue("sourceId") },
            expectedOldCandidateIds = revised.map { it.getValue("revisedClaimCandidateId") }.toSet(),
            requiredResearchTargetIds = requiredResearchTargets
        )

        assertTrue(report.errors.joinToString("\n"), report.isValid)
    }

    @Test
    fun peakImpulseRateStrainAndSourceCompositeStaySeparate() {
        val pfj = candidates.filter { it.tissueId == "KNEE_PATELLOFEMORAL" && it.sourceId == "SRC_PMID_37272685" }
        assertEquals(
            setOf(TissueTemporalMetric.PEAK, TissueTemporalMetric.IMPULSE_PER_EVENT, TissueTemporalMetric.LOADING_RATE),
            pfj.map { it.temporalMetric }.toSet()
        )
        assertEquals(
            setOf(
                TissueMeasurementMetric.MODELED_JOINT_CONTACT_FORCE,
                TissueMeasurementMetric.JOINT_CONTACT_FORCE_TIME_INTEGRAL,
                TissueMeasurementMetric.MODELED_JOINT_CONTACT_FORCE_LOADING_RATE
            ),
            pfj.map { it.measurementMetric }.toSet()
        )
        assertTrue(extractions.count { it.measurementMetric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX } == 2)
        assertFalse(candidates.any { it.measurementMetric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX })
        assertFalse(rubrics.any { it.measurementMetric == TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX })

        val achillesStrain = candidates.single { it.claimCandidateId == "C3_ACH_WEIGHTED_HEEL_STRAIN" }
        assertEquals(TissueMeasurementMetric.MEASURED_TENDON_STRAIN, achillesStrain.measurementMetric)
        assertEquals(TissueNormalizationBasis.MEASURED_TENDON_STRAIN_PERCENT, achillesStrain.normalizationBasis)
        val aclStrain = candidates.single { it.claimCandidateId == "C3_ACL_JUMP_LANDING_STRAIN" }
        assertEquals(TissueNormalizationBasis.MEASURED_LIGAMENT_STRAIN_PERCENT, aclStrain.normalizationBasis)
    }

    @Test
    fun priorScientificBoundariesRemainNarrow() {
        assertNull(candidates.single { it.claimCandidateId == "C3_R1C_01_ACH_SEATED_PEAK" }.maximumDefensibleBand)
        assertNull(candidates.single { it.claimCandidateId == "C3_R1C_04_ACH_SINGLE_CALF_RATE" }.maximumDefensibleBand)
        val sixtyDegree = candidates.filter { it.claimCandidateId.startsWith("C3_R1C_07") }
        assertTrue(sixtyDegree.all { it.maximumDefensibleBand == null })
        assertTrue(sixtyDegree.all { it.exerciseCorrespondence == TissueC3ExerciseCorrespondence.EXACT_EXERCISE_DIFFERENT_LOAD })

        val variants = correspondences.associateBy { it.stableKey }
        assertEquals(3, listOf("ex_e2efd0fe", "ex_bb728af2", "ex_f2a79d37").map { variants.getValue(it).movementVariant }.distinct().size)
        assertTrue(variants.values.all { it.transferVariables.containsAll(requiredTransferVariables) })
    }

    @Test
    fun cumulativeExposureKeepsMissingCountMissingAndUsesDeclaredEventCount() {
        assertNull(TissueC3ResearchValidator.cumulativeSessionImpulse(2.6, null))
        assertNull(TissueC3ResearchValidator.cumulativeSessionImpulse(null, 10))
        assertEquals(26.0, TissueC3ResearchValidator.cumulativeSessionImpulse(2.6, 10)!!, 0.0001)
        runCatching { TissueC3ResearchValidator.cumulativeSessionImpulse(2.6, -1) }
            .onSuccess { error("Negative event count must fail closed.") }
    }

    @Test
    fun anomalyChecksFlagSyntheticInconsistenciesWithoutRewritingCandidates() {
        val squatRate = candidates.single { it.claimCandidateId == "C3_R1C_08C_PFJ_FULL_SQUAT_RATE" }
        val dropRate = candidates.single { it.claimCandidateId == "C3_R1C_11C_PFJ_DROP_JUMP_RATE" }
            .copy(claimValue = 1.0)
        val shallow = candidates.single { it.claimCandidateId == "C3_R1C_07A_PFJ_60_SQUAT_PEAK" }
        val deep = candidates.single { it.claimCandidateId == "C3_R1C_08A_PFJ_FULL_SQUAT_PEAK" }
            .copy(claimValue = 1.0)
        val equalLoaded = shallow.copy(
            claimCandidateId = "SYNTHETIC_LOADED_EQUAL",
            externalLoadCondition = "35 percent bodyweight external load"
        )
        val copiedImpulseBand = candidates.single { it.claimCandidateId == "C3_R1C_08B_PFJ_FULL_SQUAT_IMPULSE" }
            .copy(maximumDefensibleBand = TissueLoadBand.HIGH)
        val copiedPeakBand = deep.copy(maximumDefensibleBand = TissueLoadBand.HIGH)

        val anomalies = TissueC3ResearchValidator.anomalies(
            listOf(squatRate, dropRate, shallow, deep, equalLoaded, copiedPeakBand, copiedImpulseBand)
        )
        val types = anomalies.map { it.anomalyType }.toSet()
        assertTrue(TissueBiomechanicalAnomalyType.HIGH_IMPACT_LOADING_RATE_INVERSION in types)
        assertTrue(TissueBiomechanicalAnomalyType.LOADED_UNLOADED_EQUALITY in types)
        assertTrue(TissueBiomechanicalAnomalyType.DEEP_SHALLOW_SQUAT_INVERSION in types)
        assertTrue(TissueBiomechanicalAnomalyType.COPIED_PEAK_IMPULSE_BAND in types)
        assertEquals(75.4, candidates.single { it.claimCandidateId == "C3_R1C_11C_PFJ_DROP_JUMP_RATE" }.claimValue!!, 0.0001)
    }

    @Test
    fun actualPackageHasNoUnexplainedBiomechanicalAnomaly() {
        assertTrue(TissueC3ResearchValidator.anomalies(candidates).isEmpty())
    }

    @Test
    fun productionBoundaryRemainsEmpty() {
        assertTrue(table("tissue_review_batch_approval_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_claims_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_blind_review_v1.csv").isEmpty())
        listOf(
            "exercise_joint_load_profiles_v1.csv",
            "exercise_tendon_load_profiles_v1.csv",
            "exercise_ligament_load_profiles_v1.csv",
            "exercise_fascia_load_profiles_v1.csv"
        ).forEach { assertTrue(it, table(it).isEmpty()) }
    }

    private val extractions by lazy { TissueC3ResearchParser.metricExtractions(asset("tissue_source_metric_extraction_v1.csv")) }
    private val dispositions by lazy { TissueC3ResearchParser.candidateDispositions(asset("tissue_revised_candidate_disposition_c3_v1.csv")) }
    private val candidates by lazy { TissueC3ResearchParser.claimCandidates(asset("tissue_evidence_claim_candidates_multidimensional_v1.csv")) }
    private val rubrics by lazy { TissueC3ResearchParser.rubrics(asset("tissue_load_rubric_v2.csv")) }
    private val decisions by lazy { TissueC3ResearchParser.researchDecisions(asset("tissue_research_decision_c3_v1.csv")) }
    private val correspondences by lazy { TissueC3ResearchParser.correspondences(asset("tissue_exercise_variant_correspondence_c3_v1.csv")) }
    private val dimensions by lazy { TissueMultidimensionalParser.dimensions(asset("tissue_load_dimension_registry_v2.csv")) }
    private val catalog by lazy { TissueMetadataParser.catalog(asset("canonical_tissue_catalog_v1.csv")) }
    private val existingSources by lazy { TissueEvidenceParser.sources(asset("tissue_load_evidence_registry_v1.csv")) }
    private val newSourceRows by lazy { table("tissue_load_evidence_registry_c3_v1.csv") }
    private val sourceVerifications by lazy { TissueEvidenceParser.sourceVerifications(asset("tissue_source_verification_c3_v1.csv")) }
    private val integrity by lazy { TissueEvidenceParser.publicationIntegrityVerifications(asset("tissue_publication_integrity_verification_c3_v1.csv")) }
    private val revised by lazy { table("tissue_evidence_claim_candidates_revised_v1.csv") }
    private fun table(name: String) = TissueMetadataParser.table(asset(name)).rows
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)

    private val requiredTransferVariables = setOf(
        "EXTERNAL_LOAD", "RELATIVE_LOAD", "ROM", "MOVEMENT_SPEED", "LATERALITY", "REAR_FOOT_ELEVATION",
        "STEP_LENGTH", "TRUNK_ANGLE", "KNEE_TRAVEL", "JUMP_DIRECTION", "CONTACT_DURATION", "DROP_HEIGHT",
        "REBOUND_VERSUS_STICK", "REPEATED_VERSUS_SINGLE_EVENT", "SURFACE"
    )
    private val requiredResearchTargets = setOf(
        "ACHILLES_TENDON:TENSION:PEAK:MODELED_TENDON_FORCE",
        "ACHILLES_TENDON:TENSION:IMPULSE_PER_EVENT:TENDON_FORCE_TIME_INTEGRAL",
        "ACHILLES_TENDON:TENSION:LOADING_RATE:MODELED_TENDON_FORCE_LOADING_RATE",
        "ACHILLES_TENDON:TENSION:CYCLIC_EXPOSURE:SOURCE_DEFINED_CYCLIC_EXPOSURE",
        "ACHILLES_TENDON:ENERGY_STORAGE_RELEASE:PEAK:MEASURED_TENDON_ENERGY_STORAGE",
        "PATELLAR_TENDON:TENSION:PEAK:MODELED_TENDON_FORCE",
        "PATELLAR_TENDON:TENSION:IMPULSE_PER_EVENT:TENDON_FORCE_TIME_INTEGRAL",
        "PATELLAR_TENDON:TENSION:LOADING_RATE:MODELED_TENDON_FORCE_LOADING_RATE",
        "PATELLAR_TENDON:TENSION:CYCLIC_EXPOSURE:SOURCE_DEFINED_CYCLIC_EXPOSURE",
        "PATELLAR_TENDON:TENSION:PEAK:MEASURED_TENDON_STRAIN",
        "PATELLAR_TENDON:TENSION:ECCENTRIC_PHASE_PEAK:MODELED_TENDON_FORCE",
        "KNEE_PATELLOFEMORAL:COMPRESSION:PEAK:MODELED_JOINT_CONTACT_FORCE",
        "KNEE_PATELLOFEMORAL:COMPRESSION:IMPULSE_PER_EVENT:JOINT_CONTACT_FORCE_TIME_INTEGRAL",
        "KNEE_PATELLOFEMORAL:COMPRESSION:LOADING_RATE:MODELED_JOINT_CONTACT_FORCE_LOADING_RATE",
        "KNEE_PATELLOFEMORAL:COMPRESSION:CUMULATIVE_SESSION_IMPULSE:JOINT_CONTACT_FORCE_TIME_INTEGRAL",
        "KNEE_TIBIOFEMORAL:COMPRESSION:PEAK:MODELED_JOINT_CONTACT_FORCE",
        "KNEE_TIBIOFEMORAL:ANTERIOR_POSTERIOR_SHEAR:PEAK:MODELED_JOINT_CONTACT_FORCE",
        "KNEE_ACL:ANTERIOR_TRANSLATION_STRESS:PEAK:MODELED_LIGAMENT_FORCE",
        "KNEE_ACL:VALGUS_STRESS:PEAK:EXTERNAL_JOINT_MOMENT",
        "KNEE_ACL:INTERNAL_ROTATION_STRESS:PEAK:EXTERNAL_JOINT_MOMENT",
        "KNEE_ACL:IMPACT_STABILIZATION:LOADING_RATE:GROUND_REACTION_FORCE_LOADING_RATE",
        "KNEE_PCL:POSTERIOR_TRANSLATION_STRESS:PEAK:MODELED_LIGAMENT_FORCE",
        "KNEE_MCL:VALGUS_STRESS:PEAK:MODELED_LIGAMENT_FORCE",
        "ANKLE_TALOCRURAL:COMPRESSION:PEAK:MODELED_JOINT_CONTACT_FORCE",
        "ANKLE_TALOCRURAL:IMPACT_STABILIZATION:LOADING_RATE:GROUND_REACTION_FORCE_LOADING_RATE",
        "ANKLE_SUBTALAR:ROTATIONAL_STRESS:PEAK:SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY",
        "ANKLE_SUBTALAR:INVERSION_STRESS:PEAK:SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY",
        "ANKLE_LATERAL_LIGAMENT_COMPLEX:INVERSION_STRESS:PEAK:MODELED_LIGAMENT_FORCE",
        "ANKLE_LATERAL_LIGAMENT_COMPLEX:IMPACT_STABILIZATION:LOADING_RATE:GROUND_REACTION_FORCE_LOADING_RATE"
    )
}
