package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgramItemSet
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max
import org.json.JSONObject

internal data class BCoachV2ScheduleEvaluation(
    val recoveryScore: Double,
    val maximumDayMinutes: Double,
    val dayMinutes: Map<String, Double>,
    val daysOverLimit: Int,
    val unmappedAxisExerciseCount: Int,
    val planningStartDate: LocalDate
)

internal object BCoachV2ScheduleLoad {
    private val axisSelectors = linkedMapOf<String, (Exercise) -> Double>(
        "systemic" to Exercise::systemicLoadWeight,
        "neuralHeavy" to Exercise::neuralHeavyWeight,
        "neuralSpeed" to Exercise::neuralSpeedWeight,
        "localMuscular" to Exercise::localLoadWeight,
        "deceleration" to Exercise::decelerationWeight,
        "elasticSsc" to Exercise::elasticSscWeight
    )

    fun evaluate(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        scenario: BCoachV2Scenario,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList(),
        rirBySetId: Map<String, Double> = emptyMap(),
        intensityBySetId: Map<String, Double> = emptyMap()
    ): BCoachV2ScheduleEvaluation {
        val start = request.planningStartDate
            ?: request.asOf.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        fun plannedDate(item: ProgramSkeletonItem): LocalDate =
            start.plusDays((item.weekNumber - 1L) * 7L + (item.dayOfWeek - 1L))
        fun loadByDate(items: List<ProgramSkeletonItem>): Map<LocalDate, Map<String, Double>> =
            items.groupBy(::plannedDate).mapValues { (_, dayItems) ->
                axisSelectors.mapValues { (axis, selector) ->
                    dayItems.sumOf { item ->
                        val exercise = exercises[item.exerciseStableKey]
                        if (exercise == null) 0.0
                        else ProgramSetPrescriptionResolver.resolve(item).sumOf { set ->
                            val setId = BCoachV2LoadCandidateResolver.setId(item.localId, set.setIndex)
                            selector(exercise) * setAdjustment(
                                axis, set, rirBySetId[setId], intensityBySetId[setId]
                            )
                        }
                    }
                }
            }
        val planned = loadByDate(plan.items)
        val baselineLoads = loadByDate(baseline.items)
        // Timed sport drills already enter badmintonHistory. Do not count them
        // a second time as resistance, and do not invent load for reps-only drills.
        val resistanceHistory = history.filter { it.category != "스포츠" }
        val capacityByKey = resistanceHistory.groupBy(V22HistoryRow::stableKey).mapValues { (_, rows) ->
            rows.mapNotNull { row ->
                val weight = row.weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                val reps = row.reps?.takeIf { it.isFinite() && it in 1.0..20.0 } ?: return@mapNotNull null
                val rir = row.rpe?.takeIf { it.isFinite() && it in 1.0..10.0 }
                    ?.let { 10.0 - it } ?: 0.0
                BCoachV2LoadCandidateResolver.epleyEstimatedOneRepMax(weight, reps, rir)
            }.maxOrNull()
        }
        val resistanceObserved = resistanceHistory.filter { row ->
            row.date in request.asOf.minusDays(27)..request.asOf
        }
            .groupBy(V22HistoryRow::date).mapValues { (_, rows) ->
                axisSelectors.mapValues { (axis, selector) ->
                    rows.sumOf { row ->
                        val exercise = exercises[row.stableKey] ?: return@sumOf 0.0
                        val weight = row.weightKg?.takeIf { it.isFinite() && it > 0.0 }
                        val capacity = capacityByKey[row.stableKey]
                        val intensity = if (weight != null && capacity != null && capacity > 0.0) {
                            weight / capacity
                        } else null
                        val workingRir = row.rpe?.takeIf { it.isFinite() && it in 1.0..10.0 }
                            ?.let { 10.0 - it }
                        val set = ProgramSetPrescription(
                            setIndex = row.setIndex,
                            reps = row.reps?.toInt() ?: 0,
                            weightKg = weight ?: 0.0,
                            seconds = row.seconds?.toInt() ?: 0,
                            targetRpeMin = row.rpe,
                            loadState = if (weight != null) ProgramLoadState.EXPLICIT_LOAD
                                else ProgramLoadState.USER_CALIBRATION_REQUIRED,
                            estimatedLoadKg = weight,
                            expectedRir = workingRir,
                            relativeIntensity = intensity
                        )
                        selector(exercise) * setAdjustment(axis, set, workingRir, intensity)
                    }
                }
            }
        val badmintonObserved = badmintonHistory
            .filter { it.date in request.asOf.minusDays(27)..request.asOf }
            .groupBy(V22BadmintonHistorySession::date).mapValues { (_, sessions) ->
                axisSelectors.mapValues { (axis, selector) ->
                    sessions.sumOf { session -> badmintonAxisLoad(session, axis, selector, exercises) }
                }
            }
        val observed = (resistanceObserved.keys + badmintonObserved.keys).associateWith { date ->
            axisSelectors.keys.associateWith { axis ->
                (resistanceObserved[date]?.get(axis) ?: 0.0) +
                    (badmintonObserved[date]?.get(axis) ?: 0.0)
            }
        }
        val allPlanDates = planned.keys
        val periodStart = allPlanDates.minOrNull()?.minusDays(3) ?: start.minusDays(3)
        val periodEnd = allPlanDates.maxOrNull() ?: start
        val scores = axisSelectors.keys.map { axis ->
            val observedActivity = observed.values.mapNotNull { values -> values.getValue(axis).takeIf { it > 0.0 } }
            val baselineActivity = baselineLoads.values.mapNotNull { values -> values.getValue(axis).takeIf { it > 0.0 } }
            val scale = max(1.0, max(q75(observedActivity), q75(baselineActivity)))
            val transformed = generateSequence(periodStart) { day ->
                day.plusDays(1).takeIf { it <= periodEnd }
            }.map { day ->
                val residual = BCoachV2Policy.recoveryKernels.getValue(axis).mapIndexed { lag, kernel ->
                    val date = day.minusDays(lag.toLong())
                    val actual = if (date <= request.asOf) observed[date]?.get(axis) ?: 0.0 else 0.0
                    val simulated = if (date > request.asOf) planned[date]?.get(axis) ?: 0.0 else 0.0
                    (actual + simulated) * kernel * scenario.recoveryKernelFactor
                }.sum()
                val ratio = residual / scale
                (ratio / (1.0 + ratio)).unitInterval()
            }.toList()
            if (transformed.isEmpty()) 0.0
            else 0.40 * transformed.average() + 0.60 * (transformed.maxOrNull() ?: 0.0)
        }
        val cap = (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES).toDouble()
        val minutes = plan.items.groupBy { it.weekNumber to it.dayOfWeek }.map { (slot, items) ->
            "${slot.first}:${slot.second}" to timeUpper(items, exercises)
        }.toMap()
        return BCoachV2ScheduleEvaluation(
            recoveryScore = scores.averageOrZero().unitInterval(),
            maximumDayMinutes = minutes.values.maxOrNull() ?: 0.0,
            dayMinutes = minutes,
            daysOverLimit = minutes.values.count { it > cap + 1e-9 },
            unmappedAxisExerciseCount = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
                .count { exercises[it] == null },
            planningStartDate = start
        )
    }

    fun timeUpper(items: List<ProgramSkeletonItem>, exercises: Map<String, Exercise>): Double {
        val inner = BCoachV2Policy.ESTIMATED_WARMUP_MINUTES + items.sumOf { item ->
            val exercise = exercises[item.exerciseStableKey]
            val setup = if (exercise?.equipment.orEmpty().isNotBlank()) {
                BCoachV2Policy.ESTIMATED_SETUP_KNOWN_MINUTES
            } else BCoachV2Policy.ESTIMATED_SETUP_UNKNOWN_MINUTES
            val sets = ProgramSetPrescriptionResolver.resolve(item)
            val active = sets.sumOf { set ->
                (set.seconds.takeIf { it > 0 }?.toDouble()
                    ?: BCoachV2Policy.ESTIMATED_ACTIVE_SET_SECONDS) / 60.0
            }
            val rest = max(0, sets.size - 1) * item.restSeconds.coerceAtLeast(45) / 60.0
            setup + active + rest + 0.75
        }
        return BCoachV2Policy.TIME_UPPER_EXTRA_MINUTES + BCoachV2Policy.TIME_UPPER_FACTOR * inner
    }

    private fun q75(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(0.75 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    internal fun setAdjustment(
        axis: String,
        set: com.training.trackplanner.data.ProgramSetPrescription,
        scenarioRir: Double? = null,
        scenarioIntensity: Double? = null
    ): Double {
        val intensity = scenarioIntensity?.takeIf { it.isFinite() && it > 0.0 }
            ?: set.relativeIntensity?.takeIf { it.isFinite() && it > 0.0 }
        val workingRir = scenarioRir?.takeIf(Double::isFinite)
            ?: set.expectedRir?.takeIf(Double::isFinite)
        val repFactor = when {
            set.reps <= 0 -> 1.0
            else -> (set.reps / 8.0).coerceIn(0.65, 1.35)
        }
        val intensityFactor = intensity?.let { (0.65 + it.coerceIn(0.0, 1.2) * 0.55).coerceIn(0.65, 1.30) }
            ?: 1.0
        val effortFactor = workingRir?.let { (1.0 + (4.0 - it) * 0.08).coerceIn(0.70, 1.30) }
            ?: 1.0
        val exposure = when {
            set.seconds > 0 -> (set.seconds / 45.0).coerceIn(0.65, 1.35)
            set.reps > 0 -> (set.reps / 8.0).coerceIn(0.65, 1.35)
            else -> 1.0
        }
        val value = when (axis) {
            "systemic" -> 0.40 * intensityFactor + 0.35 * repFactor + 0.25 * effortFactor
            "neuralHeavy" -> 0.75 * intensityFactor + 0.25 * effortFactor
            "neuralSpeed" -> exposure
            "localMuscular" -> 0.55 * repFactor + 0.45 * effortFactor
            "deceleration", "elasticSsc" -> exposure
            else -> 1.0
        }
        return value.coerceIn(
            BCoachV2Policy.RECOVERY_SET_ADJUSTMENT_MIN,
            BCoachV2Policy.RECOVERY_SET_ADJUSTMENT_MAX
        )
    }

    private fun badmintonAxisLoad(
        session: V22BadmintonHistorySession,
        axis: String,
        selector: (Exercise) -> Double,
        exercises: Map<String, Exercise>
    ): Double {
        val detail = session.components.filterNot { component ->
            component.stableKey in com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog.stableKeys
        }.ifEmpty { session.components }
        val totalDuration = detail.sumOf(V22BadmintonHistoryComponent::durationMinutes).coerceAtLeast(1e-9)
        val metadataWeight = detail.sumOf { component ->
            val base = exercises[component.stableKey]?.let(selector) ?: 0.0
            base * component.durationMinutes / totalDuration
        }
        val conservativeFallback = when (axis) {
            "systemic", "localMuscular" -> 0.5
            else -> 0.25
        }
        return session.practiceLoad / 60.0 * metadataWeight.takeIf { it > 0.0 }.orDefault(conservativeFallback)
    }

    private fun Double?.orDefault(default: Double): Double = this ?: default

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}

/** Re-scores only the recovery axis against the calendar start chosen during application. */
internal object BCoachV2ApplicationRecoveryAudit {
    fun evidenceCutoff(metadataJson: String?): LocalDate? {
        val decision = runCatching { JSONObject(metadataJson ?: return null) }
            .getOrNull()?.optJSONObject("bCoachMultiobjectiveDecision") ?: return null
        if (!decision.optString("engineVersion").startsWith("B_COACH_V2")) return null
        return runCatching { LocalDate.parse(decision.getString("evidenceCutoff")) }.getOrNull()
    }

    fun refresh(
        program: TrainingProgram,
        items: List<TrainingProgramItem>,
        storedSets: List<TrainingProgramItemSet>,
        applicationStartDate: LocalDate,
        cutoff: LocalDate,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList()
    ): String? {
        val root = runCatching { JSONObject(program.planningMetadataJson ?: return null) }.getOrNull()
            ?: return null
        val decision = root.optJSONObject("bCoachMultiobjectiveDecision") ?: return null
        val weeks = ((program.durationDays.coerceAtLeast(1) + 6) / 7).coerceAtLeast(1)
        val skeletonRequest = ProgramSkeletonRequest(
            name = program.name,
            goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = program.weeklyTrainingDays.coerceIn(1, 7),
            sessionMinutes = program.sessionMinutes.coerceAtLeast(15),
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = program.badmintonTransferRatio,
            sportStrengthRatio = program.sportStrengthRatio,
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = weeks
        )
        val setsByItem = storedSets.groupBy(TrainingProgramItemSet::programItemId)
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = program.name,
            durationDays = program.durationDays,
            request = skeletonRequest,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = emptyList(),
            items = items.map { item ->
                ProgramSkeletonItem(
                    localId = "persisted-${item.id}",
                    weekNumber = item.weekNumber,
                    dayOfWeek = item.dayOfWeek,
                    orderIndex = item.orderIndex,
                    exerciseStableKey = item.exerciseStableKey,
                    exerciseName = item.exerciseName,
                    category = item.category,
                    restSeconds = item.restSeconds,
                    prescription = item.prescription,
                    setCount = item.setCount,
                    reps = item.reps,
                    weightKg = item.weightKg,
                    seconds = item.seconds,
                    selectionReason = "PERSISTED_B_COACH_APPLICATION",
                    weightSource = item.weightSource.orEmpty(),
                    selectionRole = item.selectionRole.orEmpty(),
                    setPrescriptions = ProgramSetPrescriptionResolver.resolve(
                        item, setsByItem[item.id].orEmpty()
                    )
                )
            },
            planningMetadataJson = program.planningMetadataJson
        )
        val request = V22GenerationRequest(
            name = program.name,
            weeks = weeks,
            days = program.weeklyTrainingDays.coerceIn(1, 7),
            mode = V22Mode.B,
            asOf = cutoff,
            sessionMinutes = program.sessionMinutes.takeIf { it > 0 },
            planningStartDate = applicationStartDate
        )
        val scores = BCoachV2Scenario.entries.associateWith { scenario ->
            BCoachV2ScheduleLoad.evaluate(
                skeleton, skeleton, request, history, exercises, scenario, badmintonHistory
            )
        }
        decision.put("applicationRecoveryAudit", JSONObject()
            .put("status", "RECALCULATED_FOR_APPLICATION_START")
            .put("planningStartDate", applicationStartDate.toString())
            .put("evidenceCutoff", cutoff.toString())
            .put("scoreScope", "RECOVERY_AXIS_ONLY_NOT_PARETO_RESELECTION")
            .put("badmintonSessionCount", badmintonHistory.size)
            .put("badmintonHistoryFingerprint",
                BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), badmintonHistory))
            .put("scoresByScenario", JSONObject().also { output ->
                scores.forEach { (scenario, evaluation) ->
                    output.put(scenario.name, evaluation.recoveryScore)
                }
            })
            .put("maximumDayMinutes", scores.getValue(BCoachV2Scenario.BASE).maximumDayMinutes))
        return root.toString()
    }
}
