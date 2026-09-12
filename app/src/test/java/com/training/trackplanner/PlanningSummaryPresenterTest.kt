package com.training.trackplanner

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.data.personalized.*
import org.junit.Assert.*
import org.junit.Test

internal object PlanningSummaryFixture {
    fun decision() = PersonalizedPlanningDecision(
        decisionId="summary", protocolVersion="test", generatedAtEpochMillis=0, historyCutoff="2026-09-02", historyWindowDays=56,
        planningHorizonWeeks=4, adaptationIntentMinWeeks=4, adaptationIntentMaxWeeks=6,
        observedTrainingBehavior="MIXED_STRENGTH_HYPERTROPHY", strengthIntent="MIXED", strengthIntentProvenance="USER",
        badmintonIntent="ENABLED", badmintonIntentProvenance="USER", primaryAdaptation="HYPERTROPHY_STRENGTH", secondaryTargets=emptyList(),
        strengthStyle="STRAIGHT_STRENGTH_SETS", strengthStyleProvenance="OBSERVED_HISTORY_ONLY", weeklyFrequency=4, confidence="HIGH",
        reasonCodes=emptyList(), reasons=emptyList(), constraints=emptyList(), metadataAuthorityVersion="canonical-v1"
    )
    fun movement() = MovementExposureRepresentation("VERTICAL_PULL",RepresentationPriority.HIGH,2.0,10.0,1,.02,.1,10.0,.2,.2,
        RepresentationState.UNDERREPRESENTATION_SIGNAL,PlanningConfidence.HIGH,emptyList())
    fun objective() = BadmintonObjectiveRepresentation("ACCELERATION",2.0,10.0,0.0,5.0,.02,.1,.2,10.0,.2,1,
        PlanningConfidence.HIGH,true,false,RepresentationState.UNDERREPRESENTATION_SIGNAL,emptyList())
    fun transition() = AnchorTransition("barbell_bench_press",StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS,PlanningConfidence.HIGH,
        StyleFeatures(),AdaptationState(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),StructureTreatment.PRESERVE,DoseTreatment.MAINTAIN,
        .8,1.0,.1,emptyList(),emptyList(),emptyList())
    fun populated() = decision().copy(movementRepresentations=listOf(movement()), anchorTransitions=listOf(transition()),
        planningBudget=PlanningBudget(31.5,32,30,3,2,1.0,2,1), constraints=listOf(
            "확인되지 않은 새 운동의 시작 중량은 RPE 기반으로 결정하며 기계 중량을 프리웨이트 중량으로 변환하지 않습니다."))
}

class PlanningSummaryPresenterTest {
    @Test fun threeMovementSourcesRenderOneOwnedNeed() {
        val d=PlanningSummaryFixture.decision().copy(secondaryTargets=listOf("VERTICAL_PULL"),
            adaptationGaps=listOf(AdaptationGap("VERTICAL_PULL","HIGH","raw",representationState=RepresentationState.UNDERREPRESENTATION_SIGNAL)),
            movementRepresentations=listOf(PlanningSummaryFixture.movement()))
        val model=PlanningSummaryPresenter.present(d)
        assertEquals(1,model.identifiedNeeds.size)
        assertEquals(SummaryNeedKey.Movement(MovementCoverage.VERTICAL_PULL),model.identifiedNeeds.single().key)
        assertEquals(3,model.identifiedNeeds.single().evidence.size)
    }
    @Test fun badmintonDropUnderrepresentationAndSecondaryTargetShareObjectiveOwner() {
        val d=PlanningSummaryFixture.decision().copy(secondaryTargets=listOf("BADMINTON_DEVELOP_ACCELERATION"),
            adaptationGaps=listOf(AdaptationGap("BADMINTON_DROP_ACCELERATION","HIGH","raw"),AdaptationGap("BADMINTON_UNDERREPRESENTED_ACCELERATION","HIGH","raw")),
            badmintonObjectiveRepresentations=listOf(PlanningSummaryFixture.objective()))
        val need=PlanningSummaryPresenter.present(d).identifiedNeeds.single()
        assertEquals(SummaryNeedKey.Badminton(BadmintonObjective.ACCELERATION),need.key)
        assertTrue(NeedSignal.DIRECT_DROP in need.signals)
        assertEquals(3,need.evidence.size)
    }
    @Test fun exerciseTransitionsDeduplicateByStableKeyWithoutRepeatingObservation() {
        val transition=PlanningSummaryFixture.transition()
        val d=PlanningSummaryFixture.decision().copy(anchorTransitions=listOf(transition,transition.copy(doseTreatment=DoseTreatment.REDUCE_SLIGHTLY)))
        val row=PlanningSummaryPresenter.present(d).planResponse.single() as SummaryResponse.Transition
        assertEquals(transition.stableKey,row.stableKey)
        assertEquals(setOf(DoseTreatment.MAINTAIN,DoseTreatment.REDUCE_SLIGHTLY),row.doses)
        assertEquals(setOf(StructureTreatment.PRESERVE),row.structures)
    }
    @Test fun structuredFactSuppressesFallbackButKnownUniqueFallbackSurvives() {
        val d=PlanningSummaryFixture.populated().copy(reasonCodes=listOf("GAP_VERTICAL_PULL","GAP_CALVES","INTERNAL_UNKNOWN"),
            reasons=listOf("조직 회복 상태가 높은 기여 운동의 증량을 제한합니다.","DO_NOT_DISPLAY_DEBUG"))
        val model=PlanningSummaryPresenter.present(d)
        assertEquals(2,model.identifiedNeeds.size)
        assertFalse(SummaryEvidence.FALLBACK in model.identifiedNeeds.first().evidence)
        assertEquals(setOf(SummaryEvidence.FALLBACK),model.identifiedNeeds.last().evidence)
        assertEquals(setOf(SummaryLimitationKind.STARTING_LOAD,SummaryLimitationKind.TISSUE),model.limitations.map { it.kind }.toSet())
    }
    @Test fun limitationsDeduplicateByCategoryAndNeverIncludePositiveObservations() {
        val d=PlanningSummaryFixture.decision().copy(strengthIntent="UNRESOLVED",constraints=listOf(
            "고중량 선호가 미해결이어서 새로운 고중량 특화를 추가하지 않은 임시 계획입니다.","회복 상태 좋음","현재 기록에서 뚜렷한 광범위 악화가 확인되지 않아 유지·관찰합니다."))
        assertEquals(listOf(SummaryLimitation(SummaryLimitationKind.UNRESOLVED_STRENGTH)),PlanningSummaryPresenter.present(d).limitations)
    }
    @Test fun fiveSectionsKeepClassificationActionsAndBudgetsSeparateAndInputUnchanged() {
        val d=PlanningSummaryFixture.populated()
        val before=d.copy()
        val model=PlanningSummaryPresenter.present(d)
        assertEquals(5,PlanningSummarySection.entries.size)
        assertEquals(ObservedTrainingBehavior.MIXED_STRENGTH_HYPERTROPHY,model.observedState.behavior)
        assertEquals(StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS,model.observedState.style)
        assertTrue(model.planResponse.single() is SummaryResponse.Transition)
        assertEquals(31.5,model.finalAllocation.baselineSets!!,0.0)
        assertEquals(30,model.finalAllocation.plannedSets)
        assertEquals(2,model.finalAllocation.badmintonPlanned)
        assertEquals(1,model.finalAllocation.athleticPlanned)
        assertEquals(before,d)
        assertEquals(model,PlanningSummaryPresenter.present(d))
    }
    @Test fun emptyAndUnrecognizedValuesDoNotInventClaims() {
        val model=PlanningSummaryPresenter.present(PlanningSummaryFixture.decision().copy(observedTrainingBehavior="NEW_UNKNOWN",confidence="NEW_UNKNOWN",strengthStyle="NEW_UNKNOWN"))
        assertEquals(ObservedTrainingBehavior.UNKNOWN,model.observedState.behavior)
        assertNull(model.observedState.confidence)
        assertNull(model.observedState.style)
        assertTrue(model.identifiedNeeds.isEmpty())
        assertTrue(model.planResponse.isEmpty())
        assertTrue(model.limitations.isEmpty())
        assertNull(model.finalAllocation.plannedSets)
    }
}
