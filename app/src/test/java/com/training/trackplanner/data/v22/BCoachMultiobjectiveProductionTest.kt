package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BCoachMultiobjectiveProductionTest {
    private val exercise = Exercise(
        stableKey = "barbell_back_squat",
        name = "Back squat",
        category = "Strength",
        equipment = "BARBELL",
        metadataConfidence = "REVIEWED",
        systemicLoadWeight = 1.0,
        neuralHeavyWeight = 0.8,
        localLoadWeight = 0.7
    )

    @Test
    fun `changed whole-block candidate remains fallback until fingerprinted acceptance`() {
        val baseline = skeleton(sets = 2)
        val candidate = skeleton(sets = 3)
        val outcome = prepare(baseline, candidate)

        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
        assertEquals(2, outcome.skeleton.items.single().setCount)
        assertNotNull(outcome.proposal)
        assertTrue(outcome.decision.changes.isNotEmpty())
        val accepted = requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint),
            outcome.skeleton
        )
        assertTrue(accepted.accepted)
        assertEquals(3, requireNotNull(accepted.skeleton).items.single().setCount)
        assertTrue(accepted.skeleton.warnings.none { "승인 전에는" in it })
        val metadata = JSONObject(accepted.skeleton.planningMetadataJson!!)
            .getJSONObject("bCoachMultiobjectiveDecision")
        assertEquals("ACCEPTED_AFTER_EXPLICIT_USER_REVIEW", metadata.getString("proposalAcceptance"))
    }

    @Test
    fun `stale edited fallback rejects candidate without mutating either plan`() {
        val baseline = skeleton(sets = 2)
        val candidate = skeleton(sets = 3)
        val outcome = prepare(baseline, candidate)
        val stale = outcome.skeleton.copy(items = outcome.skeleton.items.map {
            it.copy(reps = 9, setPrescriptions = it.setPrescriptions.map { set -> set.copy(reps = 9) })
        })

        val rejected = requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint),
            stale
        )
        assertFalse(rejected.accepted)
        assertEquals("STALE_INPUT_REGENERATE_REQUIRED", rejected.reason)
        assertNull(rejected.skeleton)
        assertEquals(2, outcome.skeleton.items.single().setCount)
        assertEquals(3, candidate.items.single().setCount)
    }

    @Test
    fun `T34 approved Need is absent so extra sets never become an automatic reward`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val axes = outcome.decision.candidateObjectives

        assertEquals(BCoachObjectiveAxis.entries.toSet(), axes.map { it.axis }.toSet())
        assertTrue(axes.flatMap { it.values }.all { it.unit.isNotBlank() && it.provenance.isNotBlank() })
        val deficit = axes.single { it.axis == BCoachObjectiveAxis.GOAL_DEFICIT }
        assertTrue(deficit.values.all { it.value == null && it.missingReason != null })
        val recovery = axes.single { it.axis == BCoachObjectiveAxis.RECOVERY_LOAD }
        assertTrue(recovery.values.any { it.name == "systemicPlannedMaximum" && it.value == 3.0 })
        assertTrue(recovery.values.any { it.name == "daysOverApprovedRecoveryCap" && it.value == null })
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
    }

    @Test
    fun `partial importance stays missing and no incomplete vector is normalized`() {
        val request = request(BBalancedGoalImportance(strength = 5, sportSupport = 1))
        val outcome = BCoachMultiobjectiveProduction.prepare(
            request, emptyList(), mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        )
        val weights = outcome.decision.goalWeights.associateBy { it.goal }

        assertNull(weights.getValue(BBalancedGoal.STRENGTH).normalizedImportance)
        assertNull(weights.getValue(BBalancedGoal.SPORT_SUPPORT).normalizedImportance)
        assertNull(weights.getValue(BBalancedGoal.CORE).normalizedImportance)
        assertEquals("MISSING_NOT_ZERO", weights.getValue(BBalancedGoal.CORE).source)
        assertEquals("EXPLICIT_USER_PARTIAL_VECTOR_NOT_NORMALIZED",
            weights.getValue(BBalancedGoal.STRENGTH).source)
    }

    @Test
    fun `complete explicit importance vector normalizes to one`() {
        val outcome = BCoachMultiobjectiveProduction.prepare(
            request(BBalancedGoalImportance(5, 3, 1, 1)), emptyList(),
            mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        )
        val normalized = outcome.decision.goalWeights.map { requireNotNull(it.normalizedImportance) }
        assertEquals(1.0, normalized.sum(), 1e-12)
        assertEquals(0.5, normalized.first(), 1e-12)
    }

    @Test
    fun `T06 zero importance is invalid rather than unset`() {
        assertThrows(IllegalArgumentException::class.java) {
            BBalancedGoalImportance(strength = 0)
        }
    }

    @Test
    fun `T44 fixed inputs equipment and unavailable bodyweight are traceable`() {
        val request = request(BBalancedGoalImportance(strength = 5)).copy(
            fixedWeekdays = setOf(1),
            allowedWeekdays = setOf(1, 3),
            bShadowPlateOneSideKg = 1.25,
            bShadowDumbbellIncrementKg = 2.0,
            bShadowMachineIncrementKg = 5.0
        )
        val outcome = BCoachMultiobjectiveProduction.prepare(
            request, emptyList(), mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        )

        assertEquals("[1]", outcome.decision.inputAudit["fixedWeekdays"])
        assertEquals("[AUTO]", outcome.decision.inputAudit["blockGoals"])
        assertEquals("1.25", outcome.decision.inputAudit["plateOneSideKg"])
        assertEquals("UNAVAILABLE_NOT_FABRICATED", outcome.decision.inputAudit["futureBodyweight"])
    }

    @Test
    fun `T04 a candidate over the per-session limit is held rather than offered`() {
        fun withLastWeek(plan: GeneratedProgramSkeleton, lastWeekRest: Int) = plan.copy(
            items = plan.items + plan.items.single().copy(
                localId = "w2-d1-squat", weekNumber = 2, restSeconds = lastWeekRest
            )
        )
        val baseline = withLastWeek(skeleton(2), 120)
        val candidate = withLastWeek(skeleton(3), 3_600)
        val outcome = prepare(baseline, candidate)

        assertEquals(BCoachProductionStatus.HOLD_CALIBRATE, outcome.decision.status)
        assertNull(outcome.proposal)
        assertNull(outcome.decision.candidateFingerprint)
        assertTrue(requireNotNull(outcome.decision.fallbackReason).contains("SESSION_TIME_LIMIT"))
        val execution = outcome.decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.EXECUTION
        }.values.associateBy { it.name }
        assertTrue(requireNotNull(execution.getValue("lastWeekMaximumDayMinutes").value) > 60.0)
    }

    @Test
    fun `T43 changing only user goal changes mapped input fingerprint deterministically`() {
        val first = BCoachMultiobjectiveProduction.prepare(
            request(BBalancedGoalImportance(strength = 5)), emptyList(),
            mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        ).decision
        val replay = BCoachMultiobjectiveProduction.prepare(
            request(BBalancedGoalImportance(strength = 5)), emptyList(),
            mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        ).decision
        val changed = BCoachMultiobjectiveProduction.prepare(
            request(BBalancedGoalImportance(hypertrophy = 5)), emptyList(),
            mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        ).decision

        assertEquals(first.inputFingerprint, replay.inputFingerprint)
        assertNotEquals(first.inputFingerprint, changed.inputFingerprint)
        assertEquals(first.candidateFingerprint, replay.candidateFingerprint)
    }

    @Test
    fun `unchanged candidate holds and exposes no acceptance object`() {
        val plan = skeleton(2)
        val outcome = prepare(plan, plan)

        assertEquals(BCoachProductionStatus.HOLD_CALIBRATE, outcome.decision.status)
        assertNull(outcome.proposal)
        assertNull(outcome.decision.candidateFingerprint)
        assertTrue(outcome.decision.changes.isEmpty())
    }

    @Test
    fun `wrong candidate fingerprint is rejected before stale-input check`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val rejected = requireNotNull(outcome.proposal).accept("wrong", outcome.skeleton)
        assertFalse(rejected.accepted)
        assertEquals("PROPOSAL_FINGERPRINT_MISMATCH", rejected.reason)
    }

    @Test
    fun `T35 raw recent six-axis load is retained while residual decay remains missing`() {
        val decision = prepare(skeleton(2), skeleton(3)).decision
        val recovery = decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.RECOVERY_LOAD
        }.values.associateBy { it.name }

        assertEquals(1.0, recovery.getValue("systemicRecentObservedRaw").value!!, 0.0)
        assertNull(recovery.getValue("daysOverApprovedRecoveryCap").value)
        assertEquals("CAP_AND_DECAY_POLICY_UNAVAILABLE",
            recovery.getValue("daysOverApprovedRecoveryCap").missingReason)
    }

    @Test
    fun `T36 recovery maximum and mean remain distinct raw values`() {
        val baseline = skeleton(3)
        val secondDay = baseline.items.single().copy(
            localId = "w1-d2-squat",
            dayOfWeek = 2,
            setCount = 1,
            setPrescriptions = baseline.items.single().setPrescriptions.take(1)
        )
        val candidate = baseline.copy(items = baseline.items + secondDay)
        val recovery = prepare(baseline, candidate).decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.RECOVERY_LOAD
        }.values.associateBy { it.name }

        assertEquals(3.0, recovery.getValue("systemicPlannedMaximum").value!!, 0.0)
        assertEquals(2.0, recovery.getValue("systemicPlannedMean").value!!, 0.0)
    }

    @Test
    fun `T37 no candidate claims an approved risk-cap pass when no cap exists`() {
        val decision = prepare(skeleton(2), skeleton(3)).decision
        val cap = decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.RECOVERY_LOAD
        }.values.single { it.name == "daysOverApprovedRecoveryCap" }

        assertNull(cap.value)
        assertNotEquals(BCoachProductionStatus.BOUNDED_AUTO_APPLIED, decision.status)
    }

    @Test
    fun `T38 an unapproved fatigue cap does not hard-reject a reviewable candidate`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
        assertNotNull(outcome.proposal)
        assertTrue(outcome.decision.unknowns.contains(
            "APPROVED_SIX_AXIS_RECOVERY_CAPS_AND_DECAY_UNAVAILABLE"))
    }

    @Test
    fun `T39 execution axis reports engineering time without inventing a time-pressure cause`() {
        val execution = prepare(skeleton(2), skeleton(3)).decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.EXECUTION
        }
        assertTrue(execution.values.all { "LAST_EXERCISE" !in it.provenance })
        assertTrue(execution.values.single { it.name == "maximumDayMinutes" }
            .provenance.contains("NOT_MEASURED"))
    }

    @Test
    fun `T40 missing lambda epsilon and Need keep the candidate in review`() {
        val decision = prepare(skeleton(2), skeleton(3)).decision
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, decision.status)
        assertTrue(decision.unknowns.contains(
            "APPROVED_CROSS_AXIS_NORMALIZERS_WEIGHTS_AND_EPSILON_UNAVAILABLE"))
    }

    @Test
    fun `T41 rest-only mutation is not an active production candidate`() {
        val baseline = skeleton(2)
        val restOnly = baseline.copy(items = baseline.items.map { it.copy(restSeconds = 300) })
        val outcome = prepare(baseline, restOnly)
        assertEquals(BCoachProductionStatus.HOLD_CALIBRATE, outcome.decision.status)
        assertNull(outcome.proposal)

        val orderOnly = baseline.copy(items = baseline.items.map { it.copy(orderIndex = 2) })
        assertEquals(BCoachProductionStatus.HOLD_CALIBRATE,
            prepare(baseline, orderOnly).decision.status)
    }

    @Test
    fun `acceptance fingerprint preserves a recent user rest edit`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val edited = outcome.skeleton.copy(items = outcome.skeleton.items.map { it.copy(restSeconds = 180) })
        val acceptance = requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), edited)
        assertFalse(acceptance.accepted)
        assertEquals("STALE_INPUT_REGENERATE_REQUIRED", acceptance.reason)
    }

    @Test
    fun `T42 set action changes both validity route and evaluated ramp value`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val ramp = outcome.decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.RAMP_CHANGE
        }.values.single { it.name == "workSetDeltaFromFallback" }
        assertEquals(1.0, ramp.value!!, 0.0)
        assertTrue(outcome.decision.changes.any { it.startsWith("PRESCRIPTION:") })
    }

    @Test
    fun `accepted structure permits manual kg calibration but rejects stale evidence and edits`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val accepted = requireNotNull(requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton
        ).skeleton)
        val calibrated = accepted.copy(items = accepted.items.map { item ->
            item.copy(
                weightKg = 100.0,
                setPrescriptions = item.setPrescriptions.map { set ->
                    set.copy(weightKg = 100.0, loadState = ProgramLoadState.EXPLICIT_LOAD)
                }
            )
        })
        val history = history()
        val linked = BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
        BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
            calibrated, history, mapOf(exercise.stableKey to exercise), linked
        )

        val staleHistory = assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                calibrated,
                history + history.single().copy(date = LocalDate.of(2026, 11, 11), setIndex = 2),
                mapOf(exercise.stableKey to exercise),
                linked
            )
        }
        assertEquals("B_COACH_ACCEPTANCE_HISTORY_STALE", staleHistory.message)

        val staleMetadata = assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                calibrated, history, mapOf(exercise.stableKey to exercise.copy(equipment = "MACHINE")), linked
            )
        }
        assertEquals("B_COACH_ACCEPTANCE_EXERCISE_METADATA_STALE", staleMetadata.message)

        val editedReps = calibrated.copy(items = calibrated.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.map { it.copy(reps = 6) })
        })
        val staleStructure = assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                editedReps, history, mapOf(exercise.stableKey to exercise), linked
            )
        }
        assertEquals("B_COACH_ACCEPTANCE_STRUCTURE_STALE", staleStructure.message)
    }

    @Test
    fun `value and constraint states distinguish unknown from zero and pass`() {
        val noTime = request().copy(sessionMinutes = null)
        val outcome = BCoachMultiobjectiveProduction.prepare(
            noTime, history(), mapOf(exercise.stableKey to exercise), skeleton(2), skeleton(3)
        )
        val daysOver = outcome.decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.EXECUTION
        }.values.single { it.name == "daysOverUserLimit" }
        assertNull(daysOver.value)
        assertEquals(BCoachValueStatus.NOT_APPLICABLE, daysOver.status)
        assertEquals(BCoachConstraintStatus.UNKNOWN, outcome.decision.hardConstraints.single {
            it.id == "SESSION_TIME_LIMIT"
        }.status)
        assertTrue(outcome.decision.needEnvelopes.all {
            it.status == BCoachValueStatus.UNKNOWN && it.lower == null && it.upper == null
        })
    }

    @Test
    fun `unapproved explicit automatic kg candidate is suppressed even with a known device`() {
        val candidate = skeleton(3).copy(items = skeleton(3).items.map { item ->
            item.copy(weightKg = 100.0, setPrescriptions = item.setPrescriptions.map {
                it.copy(weightKg = 100.0, loadState = ProgramLoadState.EXPLICIT_LOAD)
            })
        })
        val outcome = prepare(skeleton(2), candidate)
        assertEquals(BCoachProductionStatus.HOLD_CALIBRATE, outcome.decision.status)
        assertNull(outcome.proposal)
        assertEquals(BCoachConstraintStatus.FAIL, outcome.decision.hardConstraints.single {
            it.id == "AUTOMATIC_LOAD_AUTHORITY"
        }.status)
    }

    @Test
    fun `canonical fingerprint covers schedule request and non-load item content`() {
        val base = skeleton(2)
        assertNotEquals(
            BCoachMultiobjectiveProduction.fingerprint(base),
            BCoachMultiobjectiveProduction.fingerprint(base.copy(weekDaySchedule = mapOf(1 to setOf(1))))
        )
        assertNotEquals(
            BCoachMultiobjectiveProduction.fingerprint(base),
            BCoachMultiobjectiveProduction.fingerprint(base.copy(items = base.items.map {
                it.copy(selectionReason = "changed")
            }))
        )
    }

    @Test
    fun `legacy persisted acceptance without replay schema remains editable`() {
        val outcome = prepare(skeleton(2), skeleton(3))
        val accepted = requireNotNull(requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton
        ).skeleton)
        val root = JSONObject(accepted.planningMetadataJson!!)
        root.getJSONObject("bCoachMultiobjectiveDecision").remove("acceptanceSchemaVersion")
        val legacy = accepted.copy(planningMetadataJson = root.toString())
        assertNull(BCoachMultiobjectiveProduction.acceptedPersistenceSnapshot(legacy))
    }

    private fun prepare(
        baseline: GeneratedProgramSkeleton,
        candidate: GeneratedProgramSkeleton
    ): BCoachMultiobjectiveProduction.Outcome = BCoachMultiobjectiveProduction.prepare(
        request(),
        history(),
        mapOf(exercise.stableKey to exercise),
        baseline,
        candidate
    )

    private fun history() = listOf(V22HistoryRow(
        LocalDate.of(2026, 11, 10), "Back squat", exercise.stableKey,
        "Strength", 1, 5.0, 100.0, null, 8.0
    ))

    private fun request(importance: BBalancedGoalImportance = BBalancedGoalImportance()) =
        V22GenerationRequest(
            name = "B review",
            weeks = 2,
            days = 1,
            mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14),
            sessionMinutes = 60,
            bGoalImportance = importance,
            blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 2)))
        )

    private fun skeleton(sets: Int): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "B review",
            goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = 1,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 2
        )
        val prescriptions = List(sets) { index ->
            ProgramSetPrescription(index + 1, 5, 0.0, 0, null,
                ProgramLoadState.USER_CALIBRATION_REQUIRED)
        }
        val item = ProgramSkeletonItem(
            localId = "w1-d1-squat",
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            restSeconds = 120,
            prescription = "review",
            setCount = sets,
            reps = 5,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "test",
            weightSource = "USER_CALIBRATION_REQUIRED",
            setPrescriptions = prescriptions
        )
        return GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = 14,
            request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = emptyList(),
            items = listOf(item),
            planningMetadataJson = JSONObject().put("schema", BBalanced12WeekEngine.BLOCK_SCHEMA).toString()
        )
    }
}
