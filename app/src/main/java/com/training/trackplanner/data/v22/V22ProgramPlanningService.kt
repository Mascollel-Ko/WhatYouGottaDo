package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.ExerciseMetadataUserOverrideDao
import com.training.trackplanner.data.UserMuscleRoleCodec
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramDayIntensity
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramTrainingSlot
import com.training.trackplanner.data.WorkoutDao
import com.training.trackplanner.data.ProgramProgressionDao
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog

internal class V22ProgramPlanningService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val canonicalMetadataRepository: CanonicalExerciseMetadataRepository,
    private val bBalancedTaskCatalog: BBalancedTaskCatalog,
    private val userOverrideDao: ExerciseMetadataUserOverrideDao? = null,
    private val progressionDao: ProgramProgressionDao? = null,
    private val squatShadowReader: BCoachSquatShadowEvidenceReader? = null,
    private val rirPolicy: RpeRirPolicy? = null,
    private val strengthRegistry: StrengthPerformanceRegistry? = null
) {
    suspend fun generate(request: V22GenerationRequest): V22GeneratedProgram {
        val effectiveRequest = if (request.mode == V22Mode.B && request.blockProgram == null) {
            request.copy(weeks = 12) // preserve historical B12 callers
        } else request
        val recordedWorkouts = workoutDao.entriesWithSetsUntil(effectiveRequest.asOf.toString())
        val history = V22HistoryInput.fromWorkoutHistory(recordedWorkouts)
        val linkedEvidence = effectiveRequest.sourceProgramStableKey?.let { source ->
            progressionDao?.let { dao ->
                BCoachLinkedExecutionReader.read(
                    sourceProgramStableKey = source,
                    asOf = effectiveRequest.asOf,
                    workouts = recordedWorkouts,
                    applications = dao.applications(),
                    links = dao.links(),
                    prescriptions = dao.prescriptions()
                )
            }
        }
        val coachRequest = if (linkedEvidence == null) effectiveRequest
            else effectiveRequest.copy(
                linkedDuePlannedSets = linkedEvidence.duePlannedSets,
                linkedConfirmedSets = linkedEvidence.confirmedSets,
                linkedEvidenceApplicationId = linkedEvidence.applicationId
            )
        val exercises = exerciseDao.allExercises().associateBy(Exercise::stableKey)
        if (effectiveRequest.mode != V22Mode.B) {
            val plan = V22PythonParityEngine.build(history, effectiveRequest)
            return V22GeneratedProgram(plan, plan.toSkeleton(effectiveRequest, exercises))
        }
        val balanced = BBalanced12WeekEngine.build(
            rows = history,
            request = coachRequest,
            exercises = exercises,
            muscleRelations = UserMuscleRoleCodec.effectiveRelations(
                canonicalMetadataRepository.muscleRelations(), userOverrideDao?.all().orEmpty(), exercises.values.toList()
            ),
            coreCatalog = canonicalMetadataRepository.coreCatalog(),
            badmintonCatalog = canonicalMetadataRepository.badmintonObjectiveCatalog(),
            taskCatalog = bBalancedTaskCatalog,
            mainExerciseKeys = canonicalMetadataRepository.programSlotCapabilityRelations()
                .filter { it.capabilityCode == "MAIN_STRENGTH_SLOT" }
                .mapTo(linkedSetOf()) { it.exerciseStableKey }
        )
        val comparator = runCatching { V22PythonParityEngine.build(history, effectiveRequest) }.getOrNull()
            ?.takeIf { plan -> plan.weeks.firstOrNull()?.sessions?.any { it.items.isNotEmpty() } == true }
        val baseSkeleton = balanced.toSkeleton(effectiveRequest, exercises,
            mainCapabilityKeys = canonicalMetadataRepository.programSlotCapabilityRelations()
                .filter { it.capabilityCode == "MAIN_STRENGTH_SLOT" }
                .mapTo(linkedSetOf()) { it.exerciseStableKey }
        )
        // Preserve the actual plate size the user entered at generation time. The profile
        // can change later; an applied program must never silently pick up a different grid.
        val skeleton = effectiveRequest.bShadowPlateOneSideKg
            ?.takeIf { it.isFinite() && it in 0.1..25.0 }
            ?.let { plate ->
                val metadata = org.json.JSONObject(baseSkeleton.planningMetadataJson ?: "{}")
                metadata.put("bCoachProgressionEquipmentSchema", "linked-g-1")
                metadata.put("bCoachPlateOneSideKg", plate)
                metadata.put("bCoachRolePolicySchema", "main-role-snapshot-1")
                effectiveRequest.bShadowDumbbellIncrementKg?.takeIf { it.isFinite() && it in 0.1..50.0 }
                    ?.let { metadata.put("bCoachDumbbellDisplayedKg", it) }
                effectiveRequest.bShadowMachineIncrementKg?.takeIf { it.isFinite() && it in 0.1..100.0 }
                    ?.let { metadata.put("bCoachMachineDisplayedKg", it) }
                baseSkeleton.copy(planningMetadataJson = metadata.toString())
            } ?: baseSkeleton
        // Gate D is strictly a side-channel. No exception, missing posterior, or score may
        // modify or prevent the existing B program from being generated and saved as before.
        val shadow = effectiveRequest.bShadowPlateOneSideKg
            ?.takeIf { it.isFinite() && it in 0.1..25.0 }
            ?.let { plate ->
                runCatching {
                    val reader = squatShadowReader ?: return@runCatching null
                    val policy = rirPolicy ?: return@runCatching null
                    val observed = reader.read(effectiveRequest.asOf)
                    val pipeline = BCoachSquatShadowPipeline.evaluate(
                        input = observed,
                        rirPolicy = policy,
                        smallestPlateOneSideKg = plate,
                        // Experimental RIR band only. Neither user preference nor approved default.
                        targetRirMin = 1.0,
                        targetRirMax = 3.0,
                        policies = listOf(
                            BCoachSquatShadowSelector.Weights(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                            BCoachSquatShadowSelector.Weights(1.0, 0.5, 0.25, 1.0, 0.5, 0.1),
                            BCoachSquatShadowSelector.Weights(0.25, 0.25, 0.1, 3.0, 2.0, 0.5)
                        ),
                        omegaBands = listOf(
                            BCoachSquatShadowSelector.OmegaBand.CENTRAL_80,
                            BCoachSquatShadowSelector.OmegaBand.CENTRAL_90
                        )
                    )
                    BCoachSquatShadowProgramComparator.compare(
                        asOf = effectiveRequest.asOf,
                        plannedItems = skeleton.items,
                        pipeline = pipeline,
                        userConfirmedEquipmentStep = true
                    )
                }.getOrElse {
                    BCoachSquatShadowProgramComparison(
                        status = "SHADOW_COMPARISON_ERROR",
                        snapshotDate = effectiveRequest.asOf,
                        observedReference = null,
                        plannedSquatRows = emptyList(),
                        experimentCandidates = emptyList(),
                        experiments = emptyList(),
                        notices = listOf("Shadow 비교를 계산하지 못했습니다. 기존 프로그램 생성 결과에는 영향이 없습니다.")
                    )
                }
            }
        // Gate E is informational coverage, not a second B generator or automatic load selector.
        val otherCoverage = runCatching {
            val registry = strengthRegistry ?: return@runCatching emptyList()
            val increments = runCatching {
                BBlockEquipmentIncrements(
                    effectiveRequest.bShadowPlateOneSideKg ?: Double.NaN,
                    effectiveRequest.bShadowDumbbellIncrementKg ?: Double.NaN,
                    effectiveRequest.bShadowMachineIncrementKg ?: Double.NaN
                )
            }.getOrNull()
            BCoachExerciseShadowCoverage.inspect(
                asOf = effectiveRequest.asOf,
                plannedItems = skeleton.items,
                confirmedHistory = history,
                registry = registry,
                exercises = exercises,
                increments = increments
            )
        }.getOrDefault(emptyList())
        return V22GeneratedProgram(comparator, skeleton, balanced, shadow, otherCoverage)
    }
}

internal fun BBalanced12WeekPlan.toSkeleton(
    request: V22GenerationRequest,
    exercises: Map<String, Exercise>,
    mainCapabilityKeys: Set<String> = emptySet()
): GeneratedProgramSkeleton {
    val blockCalendar = request.blockProgram?.let(::BBlockCalendar)
    val calendarWeeks = blockCalendar?.calendarWeeks ?: input.weeks
    val skeletonRequest = ProgramSkeletonRequest(
        name = request.name,
        goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
        weeklyTrainingDays = input.days,
        sessionMinutes = request.sessionMinutes ?: 0,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.0,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = calendarWeeks
    )
    val focusByBlock = blocks.associate { it.block to it.focus }
    val selectedMains = blocks.associate { block ->
        block.block to BCoachJointBlockOptimizer.selectedMainKeys(block.sessions, mainCapabilityKeys)
    }
    val items = weeks.flatMap { week ->
        week.sessions.flatMap { session ->
            session.items.mapIndexed { index, item ->
                val sets = item.sets.mapIndexed { setIndex, set ->
                    ProgramSetPrescription(
                        setIndex = setIndex + 1,
                        reps = set.reps ?: 0,
                        weightKg = 0.0,
                        seconds = set.seconds ?: 0,
                        targetRpeMin = null,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                    )
                }
                val reps = sets.map(ProgramSetPrescription::reps).distinct().singleOrNull() ?: 0
                val seconds = sets.map(ProgramSetPrescription::seconds).distinct().singleOrNull() ?: 0
                val focus = focusByBlock.getValue(week.block)
                val calendar = blockCalendar?.slot(
                    week.block - 1,
                    week.week - blocks[week.block - 1].startWeek,
                    session.session
                )
                val calendarWeek = calendar?.week ?: week.week
                val calendarDay = calendar?.day ?: session.session
                ProgramSkeletonItem(
                    localId = "b-w${calendarWeek}-d${calendarDay}-o${index + 1}-${item.stableKey}",
                    weekNumber = calendarWeek,
                    dayOfWeek = calendarDay,
                    orderIndex = index + 1,
                    exerciseStableKey = item.stableKey,
                    exerciseName = item.exercise,
                    category = item.category,
                    restSeconds = exercises[item.stableKey]?.defaultRestSeconds ?: 60,
                    prescription = item.periodizationPreviewNote(),
                    setCount = item.setCount,
                    reps = reps,
                    weightKg = 0.0,
                    seconds = seconds,
                    selectionReason = "B12_BLOCK_${week.block}_FOCUS_${focus.name}",
                    weightSource = "B12_HISTORY_REFERENCE_NOT_APPROVED_LOAD",
                    trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                    dayIntensity = ProgramDayIntensity.MODERATE.name,
                    stableKey = item.stableKey,
                    selectionRole = "B12_FOCUS_${focus.name}",
                    progressionRole = if (item.stableKey in selectedMains[week.block].orEmpty())
                        ProgressionRole.MAIN else ProgressionRole.AUTO,
                    setPrescriptions = sets
                )
            }
        }
    }
    return GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = blockCalendar?.durationDays ?: input.weeks * 7,
        request = skeletonRequest,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = (1..calendarWeeks).map { calendarWeek ->
            val active = weeks.lastOrNull { week ->
                val block = blocks[week.block - 1]
                val mapped = blockCalendar?.slot(week.block - 1, week.week - block.startWeek, 1)
                if (blockCalendar == null) week.week == calendarWeek
                else (1..7).any { day ->
                    blockCalendar.slot(week.block - 1, week.week - block.startWeek, day).week == calendarWeek
                }
            }
            val focus = active?.let { focusByBlock.getValue(it.block) }
            com.training.trackplanner.data.ProgramWeekPlan(
                weekIndex = calendarWeek,
                weekType = if (active == null) "B_BLOCK_RECOVERY"
                    else "B_BALANCED_BLOCK_${active.block}_${focus!!.name}",
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false,
                targetRpeMin = 6.0,
                targetRpeMax = 8.5
            )
        },
        items = items,
        weekDaySchedule = (1..calendarWeeks).associateWith { week ->
            items.asSequence().filter { it.weekNumber == week }.mapTo(linkedSetOf()) { it.dayOfWeek }
        },
        warnings = listOf(
            schema,
            "Historical kilograms are references only; every future load remains USER_CALIBRATION_REQUIRED.",
            "Steady blocks may intentionally keep their weekly prescription unchanged.",
            "Initial kilograms must be calibrated by the user, not fabricated from history."
        ) + limitations,
        templateId = if (schema == BBalanced12WeekEngine.SCHEMA) BBalanced12WeekEngine.TEMPLATE_ID
            else "V2_2_B_BALANCED_BLOCK_PROGRAM_V1",
        representativeTemplate = false,
        planningMetadataJson = if (schema == BBalanced12WeekEngine.BLOCK_SCHEMA) {
            org.json.JSONObject(metadataJson).apply {
                val kinds = org.json.JSONObject()
                weeks.flatMap(V22Week::sessions).flatMap(V22Session::items)
                    .map(V22ExercisePlan::stableKey).distinct().forEach { key ->
                        kinds.put(key, BBlockEquipmentIncrements.kind(key, exercises[key]).name)
                    }
                put("equipmentIncrementKinds", kinds)
            }.toString()
        } else metadataJson
    )
}

private fun V22ExercisePlan.periodizationPreviewNote(): String {
    val plan = plannedProgression.orEmpty()
    val stage = Regex("B_BLOCK_VOLUME_STEP=(-?[0-9]+)").find(plan)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: return "HISTORICAL_REFERENCE_ONLY · USER_CALIBRATION_REQUIRED"
    val applied = Regex("APPLIED_STEPS=([0-9]+)").find(plan)
        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val loadSteps = Regex("LOAD_STEPS=(-?[0-9]+)").find(plan)
        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    return when {
        stage == 0 -> "주기화 기준 주차 · 현재 처방 유지"
        applied == 0 -> "주기화 변화 미적용 · 반복·세트·중량 조정 수단을 적용할 수 없음"
        loadSteps != 0 -> "주기화 중량 ${if (loadSteps > 0) "+" else ""}${loadSteps}단계 · 시작 중량 입력 후 실제 중량에 반영"
        else -> "주기화 ${if (stage > 0) "+" else ""}${stage}단계 · 실제 세트·반복 처방에 반영"
    }
}

internal fun V22Plan.toSkeleton(
    request: V22GenerationRequest,
    exercises: Map<String, Exercise>
): GeneratedProgramSkeleton {
    val skeletonRequest = ProgramSkeletonRequest(
        name = request.name,
        goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
        weeklyTrainingDays = request.days,
        sessionMinutes = request.sessionMinutes ?: 0,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.0,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = request.weeks
    )
    val items = weeks.flatMap { week ->
        week.sessions.flatMap { session ->
            session.items.mapIndexed { index, item ->
                val sets = item.sets.mapIndexed { setIndex, set ->
                    ProgramSetPrescription(
                        setIndex = setIndex + 1,
                        reps = set.reps ?: 0,
                        weightKg = set.weightKg ?: 0.0,
                        seconds = set.seconds ?: 0,
                        targetRpeMin = null,
                        loadState = if (set.weightKg != null) {
                            ProgramLoadState.EXPLICIT_LOAD
                        } else {
                            ProgramLoadState.USER_CALIBRATION_REQUIRED
                        }
                    )
                }
                val reps = sets.map(ProgramSetPrescription::reps).distinct().singleOrNull() ?: 0
                val weight = sets.map(ProgramSetPrescription::weightKg).distinct().singleOrNull() ?: 0.0
                val seconds = sets.map(ProgramSetPrescription::seconds).distinct().singleOrNull() ?: 0
                ProgramSkeletonItem(
                    localId = "v22-w${week.week}-d${session.session}-o${index + 1}-${item.stableKey}",
                    weekNumber = week.week,
                    dayOfWeek = session.session,
                    orderIndex = index + 1,
                    exerciseStableKey = item.stableKey,
                    exerciseName = item.exercise,
                    category = item.category,
                    restSeconds = exercises[item.stableKey]?.defaultRestSeconds ?: 60,
                    prescription = "HISTORICAL_REFERENCE_ONLY · REVIEW_REQUIRED_NOT_AUTO_PRESCRIBED",
                    setCount = item.setCount,
                    reps = reps,
                    weightKg = weight,
                    seconds = seconds,
                    selectionReason = "${item.role} · NAME_BASED_ROLE_PROXY_NOT_CANONICAL",
                    weightSource = "V2_2_HISTORICAL_REFERENCE_ONLY",
                    trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name,
                    dayIntensity = ProgramDayIntensity.MODERATE.name,
                    stableKey = item.stableKey,
                    setPrescriptions = sets
                )
            }
        }
    }
    return GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = request.weeks * 7,
        request = skeletonRequest,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = emptyList(),
        items = items,
        weekDaySchedule = weeks.associate { week -> week.week to week.sessions.mapTo(linkedSetOf(), V22Session::session) },
        warnings = listOf(
            "V2_2_PYTHON_V1_0_PARITY_PORT",
            "Historical RPE remains observation only and is not mapped to target RPE.",
            "Missing historical load is stored as USER_CALIBRATION_REQUIRED."
        ) + limitations,
        templateId = "V2_2_PYTHON_V1_0_${request.mode.name}",
        representativeTemplate = false
    )
}
