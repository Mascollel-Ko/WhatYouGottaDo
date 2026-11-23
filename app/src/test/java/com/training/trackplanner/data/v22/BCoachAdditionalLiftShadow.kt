package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import org.json.JSONObject

/**
 * B-Coach explicit, short-horizon first-set Shadow, scoped to exact exercise history.
 * NO bench-to-dumbbell e1RM substitution; no four-week predictions or automatic writes.
 * The approximate curve/RPE comparison is experimental, never a success probability.
 */
object BCoachAdditionalLiftShadow {
    const val DEADLIFT = "barbell_deadlift"
    const val BENCH = "barbell_bench_press"
    const val DUMBBELL_BENCH = "ex_3a7d3eda"
    const val VERSION = "B_COACH_EXACT_LIFT_SHADOW_FIRST_SET_V1"
    private const val MAX_AGE = 56L
    private const val EPS = 1e-7

    private val names = linkedMapOf(
        DEADLIFT to "컨벤셔널 데드리프트",
        BENCH to "바벨 벤치프레스",
        DUMBBELL_BENCH to "덤벨 벤치프레스"
    )

    data class Evidence(
        val stableKey: String,
        val label: String,
        val status: String,
        val reason: String,
        val asOf: LocalDate,
        val anchorDate: LocalDate?,
        val sourceEntryId: Long?,
        val anchorReps: Int?,
        val anchorKg: Double?,
        val observedRpe: Double?,
        val stepKg: Double?,
        val curveMatchLevel: String?,
        val recommendedAction: String?,
        val proposedKg: Double?,
        val proposedReps: Int?,
        val evidenceHash: String
    )

    data class Review(
        val stableKey: String,
        val label: String,
        val localId: String,
        val week: Int,
        val day: Int,
        val action: String,
        val kg: Double,
        val reps: Int,
        val originalSetCount: Int,
        val evidenceHash: String
    ) {
        val display: String get() =
            "$label · ${week}주 ${day}요일 첫 세트: ${kg}kg × ${reps}회" +
                if (stableKey == DUMBBELL_BENCH) " (양손 덤벨 합산 중량)" else ""
    }

    fun examine(
        asOf: LocalDate,
        records: List<WorkoutEntryWithSets>,
        policy: RpeRirPolicy?,
        curves: RepetitionCurveRegistry?,
        plateOneSideKg: Double?,
        dumbbellDisplayedIncrementKg: Double?
    ): Map<String, Evidence> = names.mapValues { (key, label) ->
        val step = if (key == DUMBBELL_BENCH) dumbbellDisplayedIncrementKg
            else plateOneSideKg?.times(2.0)
        val source = records.asSequence()
            .filter { it.entry.exerciseStableKey == key }
            .mapNotNull { entry ->
                val date = runCatching { LocalDate.parse(entry.entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                if (date.isAfter(asOf) || entry.sets.isEmpty() ||
                    entry.sets.any { !it.confirmed ||
                        it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                        !it.weightKg.isFinite() || it.weightKg <= 0.0 ||
                        it.reps !in 1..20 ||
                        (it.rpe ?: entry.entry.rpe)?.let { r ->
                            !r.isFinite() || r !in 6.0..10.0
                        } != false
                    }
                ) return@mapNotNull null
                date to entry
            }
            .maxWithOrNull(compareBy<Pair<LocalDate, WorkoutEntryWithSets>>(
                { it.first }, { it.second.entry.id }
            ))
        val date = source?.first
        val newerUnfinished = source != null && records.any { row ->
            row.entry.exerciseStableKey == key &&
                runCatching { LocalDate.parse(row.entry.date) }.getOrNull()?.let { day ->
                    day <= asOf && (day > source.first ||
                        (day == source.first && row.entry.id > source.second.entry.id))
                } == true &&
                (row.sets.isEmpty() || row.sets.any { !it.confirmed })
        }
        val anchor = source?.second?.sets?.minByOrNull { it.setIndex }
        val observedRpe = source?.second?.sets?.maxOfOrNull {
            it.rpe ?: source?.second?.entry?.rpe ?: Double.NaN
        }
        val fingerprint = listOf(key, asOf, date, source?.second?.entry?.id,
            source?.second?.sets?.sortedBy { it.setIndex },
            policy?.version, curves?.assignment(key)?.assignmentVersion,
            step).joinToString("|").let(::hash)
        fun fail(reason: String) = Evidence(
            key, label, "REVIEW_ONLY", reason, asOf, date,
            source?.second?.entry?.id, anchor?.reps, anchor?.weightKg,
            observedRpe, step, null, null, null, null, fingerprint
        )
        when {
            date == null || anchor == null -> fail("동일 운동의 완전히 확인된 세트와 RPE 기록이 없습니다.")
            newerUnfinished -> fail("더 최근에 시작한 미완료 운동이 있어 오래된 기록으로 처방하지 않습니다.")
            ChronoUnit.DAYS.between(date, asOf) !in 0..MAX_AGE ->
                fail("최근 56일 이내 확인기록이 없습니다.")
            step == null || !step.isFinite() || step <= 0.0 ->
                fail("운동 기구에 맞는 최소 증량 단위가 필요합니다.")
            (key == DUMBBELL_BENCH && step !in 0.1..50.0) ||
                (key != DUMBBELL_BENCH && step !in 0.2..50.0) ->
                fail("입력된 증량 단위가 허용 범위를 벗어났습니다.")
            policy == null || curves == null || observedRpe == null ||
                !observedRpe.isFinite() -> fail("RPE/RIR 또는 반복곡선 정보가 없습니다.")
            else -> {
                val curve = runCatching { curves.resolve(key) }.getOrNull()
                if (curve == null || curve.matchLevel.name == "UNSUPPORTED") {
                    fail("이 운동에 사용할 반복곡선이 없습니다.")
                } else {
                    val recent = records.asSequence()
                        .filter { it.entry.exerciseStableKey == key }
                        .mapNotNull { row ->
                            val day = runCatching { LocalDate.parse(row.entry.date) }.getOrNull()
                                ?: return@mapNotNull null
                            if (day.isAfter(asOf) || day.isBefore(asOf.minusDays(MAX_AGE))) null
                            else row.sets.filter {
                                it.confirmed && it.loadState == ProgramLoadState.EXPLICIT_LOAD &&
                                    it.weightKg.isFinite() && it.weightKg > 0.0
                            }.map { it.weightKg }
                        }.flatten().distinct().sorted().toList()
                    val inconsistentGrid = recent.zipWithNext().any { (a, b) ->
                        val steps = (b - a) / step
                        abs(steps - kotlin.math.round(steps)) > 1e-5
                    }
                    if (inconsistentGrid) {
                        fail("최근 기록의 중량 간격과 입력된 기구 증량 단위가 충돌합니다.")
                    } else {
                        val mixture = policy.resolve(observedRpe)
                        val q = mixture?.probabilities?.mapNotNull { scenario ->
                            val evaluated = curve.evaluate(anchor.reps + scenario.rir.toDouble())
                            if (evaluated.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                                evaluated.relativeLoad == null
                            ) null else scenario.rir to (scenario.probability to evaluated.relativeLoad)
                        }.orEmpty()
                        data class Scored(val action: String, val kg: Double,
                                          val reps: Int, val cost: Double, val lowRir: Double)
                        val minStepFrac = step / anchor.weightKg
                        fun score(action: String, load: Double, reps: Int): Scored? {
                            if (reps !in 1..20 || !load.isFinite() || load <= 0.0 ||
                                q.size != mixture?.probabilities?.size || q.isEmpty()) return null
                            var cost = 0.0
                            var low = 0.0
                            for ((_, pair) in q) {
                                val probability = pair.first
                                val inverse = curve.profile.invert(
                                    pair.second * load / anchor.weightKg
                                )
                                if (inverse.status != RepetitionCurveEvaluationStatus.SUPPORTED ||
                                    inverse.repetitions == null) return null
                                val projectedRir =
                                    1.0 + exp(curve.personalTheta) * (inverse.repetitions - 1.0) - reps
                                if (!projectedRir.isFinite()) return null
                                cost += probability * (projectedRir - 2.0) *
                                    (projectedRir - 2.0)
                                if (projectedRir < 1.0) low += probability
                            }
                            return Scored(action, load, reps, cost, low)
                        }
                        val hold = score("HOLD", anchor.weightKg, anchor.reps)
                        val candidates = buildList {
                            hold?.let(::add)
                            // These are policy caps, not measured physiological limits.
                            if (minStepFrac <= if (key == DUMBBELL_BENCH) 0.07 else 0.05) {
                                score("LOAD_UP_ONE_STEP", anchor.weightKg + step,
                                    anchor.reps)?.let(::add)
                            }
                            score("REPS_UP_ONE", anchor.weightKg, anchor.reps + 1)
                                ?.let(::add)
                        }
                        if (hold == null || hold.lowRir > 0.10) {
                            fail("확인 기록 또는 RIR 분포가 안정적인 처방 선택을 지원하지 않습니다.")
                        } else {
                            val improvement = candidates.filter {
                                it.lowRir <= 0.10 && it.cost + 0.05 < hold.cost
                            }.minWithOrNull(compareBy<Scored> { it.cost }
                                .thenBy { if (it.action == "REPS_UP_ONE") 0 else 1 })
                            val chosen = improvement ?: hold
                            Evidence(
                                key, label, "READY_FOR_EXPLICIT_REVIEW",
                                "운동별 확인 기록과 RPE/RIR·반복곡선에 따른 실험적 1세트 선택입니다. 미래 성공확률이 아닙니다.",
                                asOf, date, source?.second?.entry?.id, anchor.reps,
                                anchor.weightKg, observedRpe, step,
                                curve.matchLevel.name, chosen.action, chosen.kg,
                                chosen.reps, fingerprint
                            )
                        }
                    }
                }
            }
        }
    }

    fun inspect(plan: GeneratedProgramSkeleton, evidence: Evidence?): Review? {
        val e = evidence ?: return null
        if (e.status != "READY_FOR_EXPLICIT_REVIEW" ||
            e.stableKey !in names || e.proposedKg == null || e.proposedReps == null ||
            e.anchorReps == null || e.anchorKg == null || e.anchorDate == null ||
            e.proposedKg <= 0.0 || e.proposedReps !in 1..20
        ) return null
        val item = plan.items.filter { it.exerciseStableKey == e.stableKey }
            .minWithOrNull(compareBy<ProgramSkeletonItem> { it.weekNumber }
                .thenBy { it.dayOfWeek }.thenBy { it.orderIndex }) ?: return null
        if (item.directSportSession || "PEAK" in item.selectionRole.uppercase() ||
            BCoachV2DoseEvaluator.isExplicitUserExerciseLock(item.selectionReason)) return null
        val sets = ProgramSetPrescriptionResolver.resolve(item)
        val first = sets.firstOrNull() ?: return null
        if (first.reps != e.anchorReps || first.seconds != 0 ||
            BCoachV2LoadCandidateResolver.isSetUserFixed(first, item.weightSource)
        ) return null
        val targetRir = first.targetRpeMin?.let { 10.0 - it }
        if (targetRir != null && targetRir !in 1.0..3.0) return null
        val plannedKg = first.weightKg.takeIf {
            first.loadState == ProgramLoadState.EXPLICIT_LOAD && it.isFinite() && it > 0.0
        } ?: first.estimatedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        if (plannedKg != null && abs(plannedKg - e.proposedKg) / plannedKg > 0.05 + EPS)
            return null
        return Review(e.stableKey, e.label, item.localId, item.weekNumber,
            item.dayOfWeek, e.recommendedAction ?: return null, e.proposedKg,
            e.proposedReps, sets.size, e.evidenceHash)
    }

    fun apply(
        plan: GeneratedProgramSkeleton,
        evidence: Evidence,
        reviewed: Review
    ): GeneratedProgramSkeleton? {
        if (inspect(plan, evidence) != reviewed) return null
        val match = plan.items.filter { it.localId == reviewed.localId }
        if (match.size != 1) return null
        val item = match.single()
        val sets = ProgramSetPrescriptionResolver.resolve(item)
        if (sets.size != reviewed.originalSetCount) return null
        val amended = sets.mapIndexed { i, set ->
            if (i != 0) set else set.copy(
                reps = reviewed.reps, weightKg = reviewed.kg,
                seconds = 0, estimatedLoadKg = reviewed.kg,
                loadState = ProgramLoadState.EXPLICIT_LOAD,
                loadSource = ProgramLoadSource.USER_FIXED,
                freshRir = null, expectedRir = null,
                relativeIntensity = null, evidenceVersion = VERSION
            )
        }
        val root = runCatching { JSONObject(plan.planningMetadataJson ?: "{}") }
            .getOrElse { return null }
        val audits = root.optJSONArray("bCoachOtherLiftShadowApprovedFirstSets")
            ?: org.json.JSONArray()
        audits.put(JSONObject()
            .put("authority", VERSION).put("exerciseStableKey", reviewed.stableKey)
            .put("localId", reviewed.localId).put("setIndex", 1)
            .put("week", reviewed.week).put("dayOfWeek", reviewed.day)
            .put("action", reviewed.action).put("kg", reviewed.kg)
            .put("reps", reviewed.reps).put("evidenceHash", reviewed.evidenceHash)
            .put("unit", if (reviewed.stableKey == DUMBBELL_BENCH)
                "TWO_DUMBBELLS_TOTAL_DISPLAYED_KG" else "BARBELL_TOTAL_KG")
            .put("laterSetsOrWeeksModified", false))
        root.put("bCoachOtherLiftShadowApprovedFirstSets", audits)
        val replacement = item.copy(
            setPrescriptions = amended,
            reps = amended.map { it.reps }.distinct().singleOrNull() ?: 0,
            weightKg = amended.map { it.weightKg }.distinct().singleOrNull() ?: 0.0,
            weightSource = VERSION
        )
        return plan.copy(
            items = plan.items.map {
                if (it.localId == reviewed.localId) replacement else it
            },
            planningMetadataJson = root.toString()
        )
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
