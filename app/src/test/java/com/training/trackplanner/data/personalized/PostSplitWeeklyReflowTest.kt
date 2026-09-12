package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class PostSplitWeeklyReflowTest {
    @Test fun stressMultiplePrimariesAndSupportiveRowsPreserveExhaustiveSearch() {
        fun key(value: String)=when(value) { "row" -> "ex_a61f1e96"; "other" -> "ex_e41f4c2b"; else -> value }
        val base=plan()
        val extras=listOf(f.row("other",1,3,id="extra_primary",order=3),f.row("support",1,2,id="extra_support",order=4))
            .map { it.copy(progressionRole=ProgressionRole.MAIN) }
        val added=(1..base.request.durationWeeks).flatMap { week -> extras.map { it.copy(localId="w${week}_${it.localId}",weekNumber=week) } }
        val authority=base.personalizedDecision!!.authorizedScheduling!!
        val original=base.copy(items=(base.items+added).map { it.copy(exerciseStableKey=key(it.exerciseStableKey)) },
            personalizedDecision=base.personalizedDecision!!.copy(authorizedScheduling=authority.copy(
                authorized=(authority.authorized+extras.map { AuthorizedSchedulingDemand(it.exerciseStableKey,f.source(it.exerciseStableKey,it.setCount),f.rx(it.setCount),true) })
                    .map { it.copy(item=it.item.copy(stableKey=key(it.item.stableKey))) },
                localOrigins=authority.localOrigins+added.associate { it.localId to AuthorizedAtomOrigin(it.exerciseStableKey) })))
        val source=snapshot().let { old -> old.copy(exercises=old.exercises.mapKeys { key(it.key) }.mapValues { (id,e) -> e.copy(stableKey=id) },
            metadata=old.metadata.mapKeys { key(it.key) }) }
        val eager=ReflowEvaluationCounts(); val lazy=ReflowEvaluationCounts()
        val reference=ExhaustivePostSplitReflowReference(eager).review(original,source,f.state(source))
        val optimized=PostSplitWeeklyReflow().review(original,source,f.state(source),counts=lazy)
        assertEquals(reference.skeleton,optimized.skeleton)
        assertEquals(reference.trace.moves,optimized.trace.moves)
        assertEquals(reference.trace.finalObjective,optimized.trace.finalObjective)
        assertEquals(reference.trace.qcrAfterJson,optimized.trace.qcrAfterJson)
        assertEquals(reference.trace.fixedChunkIds,optimized.trace.fixedChunkIds)
        assertTrue(optimized.trace.moves.isNotEmpty())
        assertEquals(eager.candidates,lazy.candidates)
        assertEquals(eager.acceptedActions,lazy.acceptedActions)
        assertTrue(lazy.tissueProjections<eager.tissueProjections)
        assertTrue(lazy.objectives<eager.objectives)
        println("REFLOW_COUNTS stress before=$eager after=$lazy")
        val best=reference.trace.moves.first()
        val rejecting=source.copy(planWeekTissueProjection=PlanWeekTissueProjection { rows,rpe ->
            if(rows.any { it.localId==best.localId && it.dayOfWeek==best.to })
                PlannedTissueWeek(listOf(PlannedTissueDay(best.to,"",setOf("blocked"),emptySet(),null,null)))
            else source.planWeekTissueProjection!!.evaluate(rows,rpe)
        })
        val rejectedEager=ReflowEvaluationCounts(); val rejectedLazy=ReflowEvaluationCounts()
        val fallback=ExhaustivePostSplitReflowReference(rejectedEager).review(original,rejecting,f.state(rejecting))
        val actual=PostSplitWeeklyReflow().review(original,rejecting,f.state(rejecting),counts=rejectedLazy)
        assertEquals(fallback.skeleton,actual.skeleton)
        assertEquals(fallback.trace.moves,actual.trace.moves)
        assertTrue(actual.trace.moves.isNotEmpty())
        assertNotEquals(best,actual.trace.moves.first())
        assertTrue(actual.trace.rejections.getOrDefault("CHRONOLOGICAL_TISSUE",0)>0)
        assertTrue(rejectedLazy.tissueProjections<rejectedEager.tissueProjections)
        println("REFLOW_COUNTS rejectedBest before=$rejectedEager after=$rejectedLazy")
    }
    @Test fun lazyTissueMatchesExhaustiveWinnerIncludingRejectedBestAndNoMove() {
        for(mode in 0..2) {
            val original=plan()
            val base=snapshot()
            var firstBlocked: List<ProgramSkeletonItem>?=null
            // Block a deterministic target day, rather than invocation-order-dependent data.
            val source=base.copy(planWeekTissueProjection=PlanWeekTissueProjection { rows,_ ->
                val changed=rows.any { it.exerciseStableKey=="row" && it.dayOfWeek!=1 }
                val blocked=mode==2 && changed || mode==1 && rows.any { it.exerciseStableKey=="row" && it.dayOfWeek==7 }
                if(blocked) firstBlocked=rows
                PlannedTissueWeek(rows.map { it.dayOfWeek }.distinct().map {
                    PlannedTissueDay(it,"",if(blocked) setOf("blocked") else emptySet(),emptySet(),null,null)
                })
            })
            val eager=ReflowEvaluationCounts(); val lazy=ReflowEvaluationCounts()
            val reference=ExhaustivePostSplitReflowReference(eager).review(original,source,f.state(source))
            val optimized=PostSplitWeeklyReflow().review(original,source,f.state(source),counts=lazy)
            assertEquals(reference.skeleton,optimized.skeleton)
            assertEquals(reference.trace.moves,optimized.trace.moves)
            assertEquals(reference.trace.finalObjective,optimized.trace.finalObjective)
            assertEquals(reference.trace.qcrAfterJson,optimized.trace.qcrAfterJson)
            assertEquals(reference.trace.fixedChunkIds,optimized.trace.fixedChunkIds)
            assertEquals(eager.candidates,lazy.candidates)
            assertEquals(eager.acceptedActions,lazy.acceptedActions)
            assertTrue(lazy.objectives<eager.objectives)
            assertTrue(lazy.tissueProjections<=eager.tissueProjections)
            // This small fixture has only one improving destination; reduction is asserted
            // by the multi-primary stress fixture rather than inventing extra candidates.
            if(mode==1) assertNotNull(firstBlocked)
            if(mode==2) assertTrue(optimized.trace.moves.isEmpty())
            println("REFLOW_COUNTS mode=$mode before=$eager after=$lazy")
        }
    }
    @Test fun primaryObjectiveOutranksSoftBalanceButNotHardGates() {
        val separated=PostSplitObjective(BalanceObjective(5,2.0,4.0),3,2,StrengthPrimaryObjective(0,1))
        val overlapping=PostSplitObjective(BalanceObjective(0,0.0,0.0),2,1,StrengthPrimaryObjective(1,2))
        assertTrue(separated<overlapping)
        assertEquals(0,separated.toJson().getInt("strengthPrimaryOverlap"))
    }
    @Test fun exactPrimaryMovesAroundFixedChunksWithoutChangingParentsOrQcr() {
        fun key(value: String)=when(value) { "press" -> "barbell_bench_press"; "row" -> "ex_a61f1e96"; else -> value }
        val initial=plan().let { original -> original.copy(
            items=original.items.map { it.copy(exerciseStableKey=key(it.exerciseStableKey)) },
            personalizedDecision=original.personalizedDecision!!.copy(authorizedScheduling=original.personalizedDecision!!.authorizedScheduling!!.let { auth ->
                auth.copy(authorized=auth.authorized.map { it.copy(item=it.item.copy(stableKey=key(it.item.stableKey))) })
            })) }
        val source=snapshot().let { old -> old.copy(
            exercises=old.exercises.mapKeys { key(it.key) }.mapValues { (id,value) -> value.copy(stableKey=id) },
            metadata=old.metadata.mapKeys { key(it.key) }) }
        val result=review(initial,source)
        assertEquals(0,result.trace.finalObjective!!.strengthPrimary.overlap)
        assertTrue(result.trace.moves.any { it.stableKey=="ex_a61f1e96" })
        assertEquals(initial.items.filter { it.exerciseStableKey=="barbell_bench_press" },
            result.skeleton.items.filter { it.exerciseStableKey=="barbell_bench_press" })
        assertEquals(result.trace.qcrBeforeJson,result.trace.qcrAfterJson)
        assertEquals(initial.personalizedDecision,result.skeleton.personalizedDecision)
    }
    private val f=PostGenerationFixture
    private fun snapshot()=f.snapshot().copy(planDayProjection=f.safe,planWeekTissueProjection=PlanWeekTissueProjection { rows,_ ->
        PlannedTissueWeek(rows.map { it.dayOfWeek }.distinct().map { PlannedTissueDay(it,"",emptySet(),emptySet(),null,null) }) })
    private fun plan(): GeneratedProgramSkeleton {
        val template=SplitParentProgressionTest().plan(9,3)
        val auth=template.personalizedDecision!!.authorizedScheduling!!
        val extra=f.row("row",1,4,id="movable",order=2).copy(progressionRole=ProgressionRole.MAIN)
        val rows=listOf(f.row("press",1,3,id="chunk0"),f.row("press",3,3,id="chunk1"),f.row("press",5,3,id="chunk2"),extra)
        val plan=f.plan(rows,listOf(1,3,5,7))
        val parent=auth.authorized.single().copy(prescription=f.rx(9))
        val other=AuthorizedSchedulingDemand("other",f.source("row",4),f.rx(4),true)
        val origins=plan.items.associate { row -> row.localId to if(row.exerciseStableKey=="press")
            AuthorizedAtomOrigin(parent.id,parent.id,(row.dayOfWeek-1)/2) else AuthorizedAtomOrigin(other.id) }
        return plan.copy(personalizedDecision=template.personalizedDecision!!.copy(authorizedScheduling=
            AuthorizedSchedulingTrace(listOf(parent,other),emptyList(),localOrigins=origins)))
    }
    private fun review(plan: GeneratedProgramSkeleton=plan(), snapshot: PlanningHistorySnapshot=snapshot())=
        PostSplitWeeklyReflow().review(plan,snapshot,f.state(snapshot))

    @Test fun unstructuredMainMovesAsWholeWhileChunksBindingsAndQcrStayExact() {
        val initial=plan()
        val result=review(initial)
        assertEquals("APPLIED",result.trace.state)
        assertTrue(result.trace.moves.isNotEmpty())
        assertTrue(result.trace.moves.all { it.after<it.before && it.stableKey=="row" })
        assertEquals(initial.items.filter { it.exerciseStableKey=="press" },result.skeleton.items.filter { it.exerciseStableKey=="press" })
        assertEquals(initial.items.map { it.copy(dayOfWeek=1,orderIndex=0) },result.skeleton.items.map { it.copy(dayOfWeek=1,orderIndex=0) })
        assertEquals(initial.personalizedDecision,result.skeleton.personalizedDecision)
        assertEquals(result.trace.qcrBeforeJson,result.trace.qcrAfterJson)
        assertEquals(result,review(initial))
    }
    @Test fun noSplitReturnsExactOriginalAndDoesNotEmitStage() {
        val initial=plan().let { it.copy(personalizedDecision=it.personalizedDecision!!.copy(authorizedScheduling=null)) }
        val stages=mutableListOf<PersonalizedPlannerStage>()
        val result=PostSplitWeeklyReflow().review(initial,snapshot(),f.state(),PersonalizedPlannerProgressReporter { stages+=it })
        assertSame(initial,result.skeleton)
        assertEquals("NOT_APPLICABLE_NO_MANDATORY_SPLIT",result.trace.state)
        assertTrue(stages.isEmpty())
    }
    @Test fun structuredTemplateAndMissingProvenanceAreProtected() {
        for(mode in 0..3) {
            val original=plan()
            val changed=if(mode==3) original.copy(personalizedDecision=original.personalizedDecision!!.copy(authorizedScheduling=
                original.personalizedDecision!!.authorizedScheduling!!.let { it.copy(localOrigins=it.localOrigins.filterKeys { id -> !id.endsWith("movable") }) }))
            else original.copy(items=original.items.map { if(it.exerciseStableKey!="row") it else when(mode) {
                0 -> it.copy(requiredTemplateAnchor=true)
                1 -> it.copy(progressionStyle=StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM.name)
                else -> it.copy(progressionVariant="HEAVY")
            } })
            assertEquals(changed.items,review(changed).skeleton.items)
        }
    }
    @Test fun nonMainCoreIsProtectedButOrdinaryMainCoreCanMove() {
        val original=plan()
        val authority=original.personalizedDecision!!.authorizedScheduling!!
        val core=original.copy(personalizedDecision=original.personalizedDecision!!.copy(authorizedScheduling=authority.copy(
            authorized=authority.authorized.map { if(it.id=="other") it.copy(item=it.item.copy(priority=100,material=true)) else it })))
        assertTrue(review(core).trace.moves.isNotEmpty())
        val nonMain=core.copy(items=core.items.map { if(it.exerciseStableKey=="row") it.copy(progressionRole=ProgressionRole.ASSISTANCE) else it })
        assertEquals(nonMain.items,review(nonMain).skeleton.items)
        assertTrue(review(original).trace.moves.isNotEmpty())
    }
    @Test fun destinationOfiAndAxisAreHardGates() {
        for(load in listOf(StandaloneDayLoad(99,listOf(10)),StandaloneDayLoad(30,listOf(100)))) {
            val original=plan()
            val result=review(original,snapshot().copy(planDayProjection=PlanDayProjection { load }))
            assertEquals(original.items,result.skeleton.items)
            assertTrue(result.trace.rejections.getOrDefault("DESTINATION_OFI",0)>0)
        }
    }
    @Test fun chronologicalTissueBlocksMove() {
        val original=plan()
        val source=snapshot().copy(planWeekTissueProjection=PlanWeekTissueProjection { rows,_ ->
            PlannedTissueWeek(listOf(PlannedTissueDay(7,"",if(rows.any { it.exerciseStableKey=="row" && it.dayOfWeek!=1 }) setOf("unit") else emptySet(),emptySet(),null,null))) })
        val result=review(original,source)
        assertEquals(original.items,result.skeleton.items)
        assertTrue(result.trace.rejections.getOrDefault("CHRONOLOGICAL_TISSUE",0)>0)
    }
    @Test fun currentRestrictionAndEquipmentCannotBeBypassed() {
        val original=plan()
        val restricted=snapshot().copy(recoverySignals=PlanningRecoverySignals(tissueRestrictedStableKeys=setOf("row")))
        assertEquals(original.items,review(original,restricted).skeleton.items)
        val equipped=snapshot().let { it.copy(exercises=it.exercises + ("row" to it.exercises.getValue("row").copy(equipment="BARBELL"))) }
        val unavailable=original.copy(request=original.request.copy(availableEquipment=setOf("DUMBBELL")))
        assertEquals(unavailable.items,review(unavailable,equipped).skeleton.items)
    }
    @Test fun timeGateCannotBeBypassed() {
        val original=plan().let { it.copy(request=it.request.copy(sessionMinutes=2)) }
        val result=review(original)
        assertEquals(original.items,result.skeleton.items)
        assertTrue(result.trace.rejections.getOrDefault("SESSION_TIME",0)>0)
    }
    @Test fun absentProjectionFailsSafeAndPreservesEverything() {
        val original=plan()
        val result=review(original,snapshot().copy(planWeekTissueProjection=null))
        assertSame(original,result.skeleton)
        assertEquals("FAILED_SAFE_UNCHANGED",result.trace.state)
        assertEquals("MISSING_CANONICAL_TISSUE_PROJECTION",result.trace.diagnostic)
    }
    @Test fun stageIsOneActualMilestoneNotPerCandidate() {
        val stages=mutableListOf<PersonalizedPlannerStage>()
        PostSplitWeeklyReflow().review(plan(),snapshot(),f.state(),PersonalizedPlannerProgressReporter { stages+=it })
        assertEquals(listOf(PersonalizedPlannerStage.POST_SPLIT_REFLOW),stages)
        assertEquals(96,stages.single().percent)
    }
    @Test fun sameKeyAndCalendarSpacingRejectOtherwiseAttractiveDestinations() {
        val original=plan()
        val second=original.copy(items=original.items + original.items.filter { it.exerciseStableKey=="row" }.map {
            it.copy(localId=it.localId+"_second",dayOfWeek=7,orderIndex=1) },personalizedDecision=original.personalizedDecision!!.copy(
            authorizedScheduling=original.personalizedDecision!!.authorizedScheduling!!.let { auth -> auth.copy(
                authorized=auth.authorized.map { if(it.id=="other") it.copy(prescription=f.rx(8)) else it },
                localOrigins=auth.localOrigins + original.items.filter { it.exerciseStableKey=="row" }.associate {
                    it.localId+"_second" to AuthorizedAtomOrigin("other") }) }))
        val source=snapshot()
        // Initial Mon/Sun would violate spacing. Use Mon/Thu; Sun is then forbidden next to Mon.
        val legal=second.copy(items=second.items.map { if(it.localId.endsWith("_second")) it.copy(dayOfWeek=5) else it })
        val state=f.state(source).copy(anchors=listOf(UserAnchor("row","unrelated label",3,8,"","","",10.0)))
        val result=PostSplitWeeklyReflow().review(legal,source,state)
        assertTrue(result.trace.rejections.getOrDefault("SAME_KEY",0)>0)
        assertTrue(result.trace.rejections.getOrDefault("PRIMARY_ANCHOR_CALENDAR_SPACING",0)>0)
        assertTrue(PrimaryStrengthAnchorSpacingPolicy.allowedRows(result.skeleton.items,setOf("row")))
    }
    @Test fun maximumLowerImpactConcentrationCannotIncrease() {
        val original=plan()
        val source=snapshot().let { it.copy(metadata=it.metadata + ("row" to it.metadata.getValue("row").copy(jointTendonImpactStressLevel="HIGH"))) }
        val occupied=original.copy(items=original.items.map { if(it.exerciseStableKey=="row") it.copy(dayOfWeek=7) else it })
        val allLower=source.copy(metadata=source.metadata.mapValues { (_,m) -> m.copy(jointTendonImpactStressLevel="HIGH") })
        val result=review(occupied,allLower)
        assertEquals(occupied.items,result.skeleton.items)
        assertTrue(result.trace.rejections.getOrDefault("LOWER_IMPACT_CONCENTRATION",0)>0)
    }
    @Test fun uniformSupportivePerformanceCanMoveWholeButOrderedStructureCannot() {
        val initial=plan()
        val source=snapshot().let { it.copy(metadata=it.metadata + ("row" to it.metadata.getValue("row").copy(programSlot="CORE_STABILITY",
            analysisEligibility=MetadataTokenField.parse("FATIGUE|BADMINTON_SUPPORTIVE"),progressMetricType="QUALITY_BASED")),
            badmintonSupportiveObjectives=it.badmintonSupportiveObjectives + ("row" to setOf("REACTION"))) }
        assertEquals(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL,source.activityKind("row"))
        assertEquals("APPLIED",review(initial,source).trace.state)
        val structured=initial.copy(items=initial.items.map { row -> if(row.exerciseStableKey!="row") row else row.copy(
            setPrescriptions=row.setPrescriptions.mapIndexed { i,set -> set.copy(reps=5+i) }) })
        assertEquals(structured.items,review(structured,source).skeleton.items)
    }
}
