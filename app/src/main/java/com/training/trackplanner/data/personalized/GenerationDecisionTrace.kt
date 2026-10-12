package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/** Observation-only per-attempt decision log. Payloads are explicitly allow-listed by callers. */
object GenerationDecisionTraceStore {
    private const val MAX_TRACES = 8
    private val forbiddenExportKeys = setOf(
        "email", "emailaddress", "account", "accountid", "userid", "useraccountid", "uid",
        "accesstoken", "refreshtoken", "idtoken", "authorization", "auth", "rawbackup", "backupjson",
        "sourceentryid", "sourcesetid", "localid", "programid", "programname"
    )
    private val lock = Any()
    private val sequence = AtomicLong()
    private val traces = LinkedHashMap<String, MutableTrace>()
    @Volatile private var recordingEnabled = true

    internal fun setRecordingEnabledForTests(enabled: Boolean) {
        recordingEnabled = enabled
    }

    private data class Event(
        val index: Long,
        val stageId: String,
        val functionPolicy: String,
        val inputs: JSONObject,
        val criteria: JSONObject,
        val comparisons: JSONObject,
        val decision: String,
        val reasonCodes: JSONArray,
        val selected: JSONArray,
        val excluded: JSONArray,
        val deferred: JSONArray,
        val nextStageInputs: JSONObject,
        val elapsedRealtimeNanos: Long,
        val durationMillis: Double
    ) {
        fun json(): JSONObject = JSONObject()
            .put("index", index).put("stageId", stageId).put("functionPolicy", functionPolicy)
            .put("inputs", GenerationDecisionTraceStore.sanitize(inputs))
            .put("criteria", GenerationDecisionTraceStore.sanitize(criteria))
            .put("comparisons", GenerationDecisionTraceStore.sanitize(comparisons))
            .put("decision", decision).put("reasonCodes", reasonCodes).put("selected", selected)
            .put("excluded", excluded).put("deferred", deferred)
            .put("nextStageInputs", GenerationDecisionTraceStore.sanitize(nextStageInputs))
            .put("elapsedRealtimeNanos", elapsedRealtimeNanos).put("durationMillis", durationMillis)
    }

    private data class MutableTrace(
        val runId: String,
        val createdAtEpochMillis: Long,
        val attemptStartedNanos: Long,
        val request: JSONObject,
        val events: MutableList<Event> = mutableListOf(),
        var generationStartedNanos: Long? = null,
        var finishedAtNanos: Long? = null,
        var status: String = "PREPARING"
    )

    fun begin(runId: String, request: JSONObject, startedNanos: Long = System.nanoTime()) {
        if (!recordingEnabled) return
        synchronized(lock) {
            traces.remove(runId)
            traces[runId] = MutableTrace(runId, System.currentTimeMillis(), startedNanos, sanitize(request))
            while (traces.size > MAX_TRACES) traces.remove(traces.keys.first())
        }
    }

    fun setStatus(runId: String, status: String) {
        if (!recordingEnabled) return
        synchronized(lock) { traces[runId]?.status = status }
    }

    fun beginGeneration(runId: String) {
        if (!recordingEnabled) return
        synchronized(lock) {
            traces[runId]?.apply { generationStartedNanos = System.nanoTime(); status = "RUNNING" }
        }
    }

    fun record(
        runId: String?,
        stageId: String,
        functionPolicy: String,
        inputs: JSONObject = JSONObject(),
        criteria: JSONObject = JSONObject(),
        comparisons: JSONObject = JSONObject(),
        decision: String,
        reasonCodes: Collection<String> = emptyList(),
        selected: Collection<String> = emptyList(),
        excluded: Collection<String> = emptyList(),
        deferred: Collection<String> = emptyList(),
        nextStageInputs: JSONObject = JSONObject(),
        startedNanos: Long = System.nanoTime(),
        durationNanos: Long = (System.nanoTime() - startedNanos).coerceAtLeast(0L)
    ) {
        if (!recordingEnabled || runId == null) return
        synchronized(lock) {
            val trace = traces[runId] ?: return
            trace.events += Event(
                index = sequence.incrementAndGet(), stageId = stageId, functionPolicy = functionPolicy,
                // Callers pass fresh immutable-after-record JSON projections. Defer recursive
                // privacy filtering until export so per-event observation stays inexpensive.
                inputs = inputs, criteria = criteria,
                comparisons = comparisons, decision = decision,
                reasonCodes = JSONArray(reasonCodes.distinct()), selected = JSONArray(selected),
                excluded = JSONArray(excluded), deferred = JSONArray(deferred),
                nextStageInputs = nextStageInputs, elapsedRealtimeNanos = startedNanos,
                durationMillis = durationNanos / 1_000_000.0
            )
        }
    }

    fun finish(runId: String?, status: String, stageId: String, functionPolicy: String, details: JSONObject = JSONObject()) {
        if (!recordingEnabled || runId == null) return
        val now = System.nanoTime()
        synchronized(lock) {
            val trace = traces[runId] ?: return
            trace.status = status
            trace.finishedAtNanos = now
            val start = trace.generationStartedNanos ?: trace.attemptStartedNanos
            val detailReasonCodes = details.optJSONArray("reasonCodes")
            val reasonCodes = JSONArray()
            if (detailReasonCodes != null) {
                for (index in 0 until detailReasonCodes.length()) reasonCodes.put(detailReasonCodes.opt(index))
            }
            trace.events += Event(
                index = sequence.incrementAndGet(), stageId = stageId, functionPolicy = functionPolicy,
                inputs = JSONObject(), criteria = JSONObject(), comparisons = JSONObject(), decision = status,
                reasonCodes = reasonCodes,
                selected = JSONArray(), excluded = JSONArray(), deferred = JSONArray(),
                nextStageInputs = sanitize(details), elapsedRealtimeNanos = now,
                durationMillis = (now - start).coerceAtLeast(0L) / 1_000_000.0
            )
        }
    }

    fun contains(runId: String): Boolean = recordingEnabled && synchronized(lock) { traces.containsKey(runId) }

    fun hasStage(runId: String, stageId: String): Boolean = synchronized(lock) {
        traces[runId]?.events?.any { it.stageId == stageId } == true
    }

    private fun sanitize(source: JSONObject): JSONObject {
        val safe = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val normalized = key.lowercase().filter(Char::isLetterOrDigit)
            if (normalized !in forbiddenExportKeys) safe.put(key, sanitizeValue(source.opt(key)))
        }
        return safe
    }

    private fun sanitizeValue(value: Any?): Any? = when (value) {
        is JSONObject -> sanitize(value)
        is JSONArray -> JSONArray().also { result ->
            for (index in 0 until value.length()) result.put(sanitizeValue(value.opt(index)))
        }
        else -> value
    }

    fun json(runId: String): String? = synchronized(lock) {
        if (!recordingEnabled) return@synchronized null
        val trace = traces[runId] ?: return@synchronized null
        val now = System.nanoTime()
        val endedAt = trace.finishedAtNanos ?: now
        JSONObject()
            .put("schema", "generation-decision-trace-v1")
            .put("runId", trace.runId)
            .put("status", trace.status)
            .put("createdAtEpochMillis", trace.createdAtEpochMillis)
            .put("totalAttemptDurationMillis", (endedAt - trace.attemptStartedNanos).coerceAtLeast(0L) / 1_000_000.0)
            .put("generationDurationMillis", trace.generationStartedNanos?.let { start ->
                (endedAt - start).coerceAtLeast(0L) / 1_000_000.0
            } ?: JSONObject.NULL)
            .put("preparationDurationMillis", trace.generationStartedNanos?.let { start ->
                ((start - trace.attemptStartedNanos).coerceAtLeast(0L) / 1_000_000.0)
            } ?: ((endedAt - trace.attemptStartedNanos).coerceAtLeast(0L) / 1_000_000.0))
            .put("request", JSONObject(trace.request.toString()))
            .put("events", JSONArray(trace.events.map(Event::json)))
            .toString(2)
    }

    fun summary(runId: String): String? = synchronized(lock) {
        if (!recordingEnabled) return@synchronized null
        val trace = traces[runId] ?: return@synchronized null
        val byStage = trace.events.groupBy(Event::stageId)
        fun latest(stage: String): Event? = byStage[stage]?.lastOrNull()
        fun payload(stage: String): JSONObject? = latest(stage)?.nextStageInputs
        fun comparison(stage: String): JSONObject? = latest(stage)?.comparisons
        fun array(value: JSONObject?, key: String): List<JSONObject> {
            val rows = value?.optJSONArray(key) ?: return emptyList()
            return (0 until rows.length()).mapNotNull(rows::optJSONObject)
        }
        fun stringArray(value: JSONArray?): String = value?.let { rows ->
            (0 until rows.length()).map { rows.optString(it) }.filter(String::isNotBlank).joinToString(", ")
        }.orEmpty()
        fun targetText(value: Any?): String = when (value) {
            null, JSONObject.NULL -> "숫자 목표 없음"
            is JSONObject -> value.toString()
            else -> value.toString()
        }
        fun koreanDecision(code: String): String = when (code) {
            "DEVELOP" -> "향상 필요"
            "MAINTAIN" -> "유지"
            "REDUCE" -> "감량"
            "NO_EXTRA_NEED" -> "추가 자극 불필요"
            "UNKNOWN" -> "근거 부족"
            "NOT_EXECUTED" -> "실행하지 않음"
            else -> code
        }
        fun duration(event: Event?): String = event?.let { "${it.durationMillis} ms — ${it.functionPolicy}" } ?: "기록 없음"
        buildString {
            appendLine("운동 프로그램 생성 진단")
            appendLine("실행 ID: ${trace.runId}")
            appendLine("상태: ${trace.status}")
            appendLine()
            appendLine("1. 사용자 요청 요약")
            appendLine("목표: ${trace.request.optString("goal")} / 주당 요청 운동일: ${trace.request.optInt("requestedWeeklyTrainingDays")}일 / 세션: ${trace.request.optInt("requestedSessionMinutes")}분 / 기간: ${trace.request.optInt("durationWeeks")}주")
            appendLine("장비: ${stringArray(trace.request.optJSONArray("availableEquipment")).ifBlank { "별도 장비 지정 없음" }}")
            appendLine("사용자 선호 운동: ${stringArray(trace.request.optJSONArray("preferredExerciseStableKeys")).ifBlank { "없음" }}")
            appendLine("사용자 제외 운동: ${stringArray(trace.request.optJSONArray("excludedExerciseStableKeys")).ifBlank { "없음" }}")
            latest("TRAINING_STATE_ANALYSIS")?.let { appendLine("사전 분석: ${it.decision}; 보정 질문: ${stringArray(it.selected).ifBlank { "없음" }}") }
            appendLine()
            appendLine("2. 과거 평균 운동량 및 계산 근거")
            val baseline = payload("B2_PERSONAL_BASELINE")
            if (baseline == null) appendLine("개인 운동량 기준선이 계산되지 않았습니다.") else {
                appendLine("기준일 ${baseline.optString("cutoff")}, 최근 ${baseline.optInt("windowWeeks")}주(물리 세트 기록 기준)")
                appendLine("운동한 주의 물리 세트 평균/중앙값: ${baseline.opt("activeWeekPhysicalSetMean")} / ${baseline.opt("activeWeekPhysicalSetMedian")}; Q25–Q75 ${baseline.opt("activeWeekPhysicalSetQ25")}–${baseline.opt("activeWeekPhysicalSetQ75")}")
                appendLine("기록이 확인된 주의 물리 세트 중앙값: ${baseline.opt("confirmedRecordWeekPhysicalSetMedian")}; Q25–Q75 ${baseline.opt("confirmedRecordWeekPhysicalSetQ25")}–${baseline.opt("confirmedRecordWeekPhysicalSetQ75")}")
                appendLine("관측 주 ${baseline.optInt("confirmedRecordWeekCount")} / 미관측 주 ${baseline.optInt("unobservedWeekCount")}; 행이 없는 주는 미훈련과 미기록을 구별할 수 없습니다.")
                array(baseline, "weeks").forEach { week ->
                    appendLine("- ${week.optString("start")}~${week.optString("end")}: 확인된 물리 세트 ${week.optInt("physicalSetCount")}, 근력운동일 ${week.optInt("strengthTrainingDayCount")}, 관측 상태 ${week.optString("trainingWeekObservation")}")
                }
                val dose = baseline.optJSONObject("qualityDoseHistory")
                array(dose, "qualities").forEach { quality ->
                    appendLine("- ${quality.optString("quality")}: 기준 ${quality.optString("baselineSource")}, 적격 주 ${quality.optInt("eligibleWeekCount")}, 직접 자극 중앙값 ${quality.opt("directUnitsMedian")}, 보조 자극 중앙값 ${quality.opt("supportiveUnitsMedian")}, 제외·미관측 주 ${quality.optInt("emptyCompletedWeekCount")}")
                    array(quality, "weeklyEvidence").forEach { week ->
                        appendLine("  ${week.optString("start")}~${week.optString("end")}: 직접 ${week.optDouble("directUnits")}, 보조 ${week.optDouble("supportiveUnits")}, 제외=${week.optBoolean("excludedFromBaseline")}, 분류완료=${week.optBoolean("classificationComplete")}")
                    }
                }
            }
            appendLine()
            appendLine("3. B1~B4 판단 요약")
            val b1 = payload("B1_NEED")
            array(b1, "qualityNeedsDetailed").forEach { need ->
                appendLine("B1 품질 ${need.optString("quality")}: 목적 ${need.optString("relevance")}, 필요성 ${koreanDecision(need.optString("decision"))} (${need.optString("decision")}), 반응 ${need.optString("response")}, 신뢰도 ${need.optString("confidence")}; 최근 28일 직접/보조 ${need.optDouble("current28dDirect")}/${need.optDouble("current28dSupportive")}, 이전 28일 직접/보조 ${need.optDouble("prior28dDirect")}/${need.optDouble("prior28dSupportive")}; 사유 ${stringArray(need.optJSONArray("reasonCodes"))}")
            }
            array(b1, "taskNeedsDetailed").forEach { need ->
                appendLine("B1 과제 ${need.optString("task")}: 목적 ${need.optString("relevance")}, 필요성 ${koreanDecision(need.optString("decision"))} (${need.optString("decision")}), 최근/이전 직접 자극 ${need.optDouble("current28dDirect")}/${need.optDouble("prior28dDirect")}; 사유 ${stringArray(need.optJSONArray("reasonCodes"))}")
            }
            array(b1, "movementNeedEvidence").forEach { need ->
                appendLine("B1 움직임 ${need.optString("movementCoverage")}: 현재/이전 28일 ${need.opt("currentExposure28d")}/${need.opt("priorExposure28d")}, 우선순위 ${need.optString("gapPriority")}, 신뢰도 ${need.optString("evidenceConfidence")}, 제한됨=${need.optBoolean("globallyRestricted") || need.optBoolean("explicitlyRestricted")}; 사유 ${stringArray(need.optJSONArray("reasonCodes"))}")
            }
            val b3 = payload("B3_STRATEGY")
            array(b3, "qualityDecisions").forEach { row -> appendLine("B3 품질 ${row.optString("quality")}: 전략 ${row.optString("strategy")}, 기준선 ${row.opt("baselineDirectUnitsMedian")}, 사유 ${stringArray(row.optJSONArray("reasonCodes"))}") }
            array(b3, "taskDecisions").forEach { row -> appendLine("B3 과제 ${row.optString("task")}: 전략 ${row.optString("strategy")}, 수치 기준선 권한 ${row.opt("numericBaselineAuthority")}, 사유 ${stringArray(row.optJSONArray("reasonCodes"))}") }
            array(b3, "movementDecisions").forEach { row -> appendLine("B3 움직임 ${row.optString("movementCoverage")}: 결정 ${row.optString("disposition")}, 제한됨=${row.optBoolean("globallyRestricted") || row.optBoolean("explicitlyRestricted")}, 사유 ${stringArray(row.optJSONArray("reasonCodes"))}") }
            latest("B3_TRAINING_STATE_AND_REQUEST")?.let { event ->
                appendLine("훈련 상태·요청 확정: 적응 기간 ${event.comparisons.optInt("intentMinWeeks")}–${event.comparisons.optInt("intentMaxWeeks")}주; 추천·확정 운동일 ${event.nextStageInputs.optInt("frequencyRecommendation")}/${event.nextStageInputs.optInt("resolvedWeeklyDays")}일; 사유 ${stringArray(event.reasonCodes)}")
            }
            val b4 = payload("B4_TARGETS")
            (array(b4, "qualityTargets") + array(b4, "taskTargets") + array(b4, "movementTargets")).forEach { target ->
                appendLine("B4 목표 ${target.optString("targetId").ifBlank { target.optString("quality").ifBlank { target.optString("task").ifBlank { target.optString("movementCoverage") } } }}: 목적/전략 ${target.optString("strategy")}, 수치 권한 ${target.optString("numericAuthority")}, 주당 목표 ${targetText(target.opt("weeklyDirectUnitsTarget") ?: target.opt("weeklyUnitsTarget"))}, 사유 ${stringArray(target.optJSONArray("reasonCodes"))}")
            }
            if (b1 == null) appendLine("B1~B4 판단은 입력 보정 또는 사전 실패 때문에 실행되지 않았습니다.")
            appendLine()
            appendLine("4. B5 운동 선택 및 탈락 이유")
            val b5 = payload("B5_CANDIDATES")
            val dispositions = array(b5, "candidateDispositions")
            if (dispositions.isEmpty()) appendLine("B5 후보 평가 기록 없음")
            dispositions.forEach { row ->
                appendLine("- ${if (row.optString("status") == "SELECTED") "선택" else "미선택"}: ${row.optString("exerciseName")} (${row.optString("stableKey")}) / 목표 ${row.optString("targetId")} / 역할 ${row.optString("selectionRole")} / 직접 자격 ${row.opt("directTargetCandidate")} / 장비 ${row.opt("equipment")} / 사용자 필수·제외 ${row.optBoolean("preferredByUser")}/${row.optBoolean("excludedByUser")} / 개인 기록 세트 ${row.optInt("personalRecordedSetCount")} / 연부조직 제한 ${row.optBoolean("tissueRestricted")} / 순위 ${row.opt("ranking")} / 대체 선택 ${row.optJSONObject("selectedInstead")} / 사유 ${stringArray(row.optJSONArray("reasons"))}")
            }
            array(b5, "materialDemandCandidates").forEach { row -> appendLine("최초 요구량 ${row.optString("stableKey")}#${row.optString("selectionRole")}: ${row.optInt("targetSets")}세트 (${row.optString("reason")})") }
            array(b5, "candidatePoolNotDispositioned").forEach { row -> appendLine("평가 대상 아님 ${row.optString("stableKey")} / 목표 ${row.optString("targetId")}: disposition index에 없어서 탈락 처리하지 않음") }
            appendLine()
            appendLine("5. B6 처방 이유")
            array(payload("B6_AUTHORIZATION"), "authorizations").forEach { row ->
                val owner = row.optJSONObject("owner")
                val prescription = row.optJSONObject("authorizedPrescription")
                appendLine("- 목표 ${row.optString("targetId")}, 운동 ${owner?.optString("stableKey") ?: "미선택"}#${owner?.optString("selectionRole") ?: ""}: 상태 ${row.optString("status")}, 권한 ${row.optString("executionAuthority")}, 요청 ${row.optString("inputPrescription")}, 승인 처방 ${prescription?.optString("text") ?: "없음"}, 중량 근거 ${prescription?.optString("weightSource") ?: "없음"}, 휴식 ${prescription?.optInt("restSeconds") ?: 0}초, 사유 ${stringArray(row.optJSONArray("reasonCodes"))}, 보정 필요 ${row.opt("coldStartCalibration")}")
                array(prescription, "sets").forEach { set -> appendLine("  세트 ${set.optInt("setIndex")}: ${set.optInt("reps")}회, ${set.opt("weightKg")}kg (${set.optString("loadState")}), RPE ${set.opt("targetRpeMin")}") }
            }
            val realization = payload("B6_REALIZATION")
            appendLine("B6 실현/물리 세트 감사: ${realization?.optJSONArray("materializationAudits")?.toString() ?: "기록 없음"}")
            appendLine()
            appendLine("6. 주간 총량 제한 내역")
            val volume = payload("RESISTANCE_VOLUME_WEEKS")
            val volumeComparisons = comparison("RESISTANCE_VOLUME_WEEKS")
            appendLine("주당 물리 세트 기준선 ${volume?.opt("baseline")}, 주당 최종 목표 ${volume?.opt("targetSets")}; 기준 선택 주 ${stringArray(volumeComparisons?.optJSONArray("selectedBaselineWeeks"))}")
            array(volumeComparisons, "weeklyEvidence").forEach { week -> appendLine("- ${week.optString("weekStart")}~${week.optString("weekEnd")}: ${week.optInt("resistancePhysicalSetCount")} 물리 세트, 운동일 ${week.optInt("resistanceTrainingDayCount")}, 포함=${week.optBoolean("includedInSelectedBaseline")}, 제외 사유 ${week.optString("exclusionReason")}") }
            val limits = volume?.optJSONArray("sequentialLimits")
            if (limits != null) for (index in 0 until limits.length()) {
                val row = limits.optJSONObject(index) ?: continue
                appendLine("제한 ${row.optString("limit")}: ${row.opt("before")} → ${row.opt("after")} (감소 ${row.opt("reduction")})")
            }
            appendLine("기준선 근거: 평균 ${volumeComparisons?.opt("selectedBaselineMeanPhysicalSets")}, 중앙값 ${volumeComparisons?.opt("selectedBaselineMedianPhysicalSets")}, Q25 ${volumeComparisons?.opt("selectedBaselineQ25PhysicalSets")}, Q75 ${volumeComparisons?.opt("selectedBaselineQ75PhysicalSets")}, 운동일/주 ${volumeComparisons?.opt("normalTrainingDaysPerWeekMedian")}, 세트당 초 ${volumeComparisons?.opt("secondsPerSetMedian")}")
            val capacity = payload("EXECUTION_CAPACITY")?.optJSONObject("envelope")
            appendLine("요청일 ${capacity?.opt("resolvedWeeklyDays")}, 유용 수요 ${capacity?.opt("usefulDemandUnits")}, 과거 입증 용량 ${capacity?.opt("historicalControllableUnits")}, 밀도 한도 ${capacity?.opt("scheduleFeasibleUnits")}, schedule units ${capacity?.opt("scheduleFeasibleUnits")}, 최종 조절 가능 단위 ${capacity?.opt("finalControllableUnits")}; 회복 제한 ${capacity?.opt("recoveryConstraint")}")
            appendLine("최종 주간 용량 요약: ${comparison("WEEKLY_CAPACITY")?.optJSONObject("resistance")?.toString() ?: "기록 없음"}")
            val finite = payload("FINITE_EXECUTION_ALLOCATOR")?.optJSONObject("finite")
            appendLine("finite 배정: 연속 수요 ${finite?.opt("continuity")}, material 배정 ${finite?.optJSONArray("material")}; 미배정 물리 세트 ${stringArray(finite?.optJSONArray("deferred"))}")
            array(payload("FINITE_EXECUTION_ALLOCATOR"), "ownerAllocationProvenance").forEach { row -> appendLine("배정 소유자 ${row.optString("stableKey")}#${row.optString("selectionRole")}: ${row.optString("action")} ${row.opt("before")} → ${row.opt("after")}; 사유 ${row.optString("reason")}") }
            appendLine()
            appendLine("7. 세트 분할·요일 배치 내역")
            for (stage in listOf("SPLIT_AND_DAY_PLACEMENT", "SPLIT_AWARE_CONTINUITY_ALLOCATION", "FREQUENCY_EXPANSION", "RESIDUAL_COMPLETION", "BOUNDED_DAY_REBALANCER", "POST_SPLIT_REFLOW")) {
                val event = latest(stage)
                if (event == null) appendLine("$stage: 실행 이벤트 없음") else {
                    appendLine("$stage (${event.functionPolicy}, ${event.durationMillis} ms): ${event.decision}; 사유 ${stringArray(event.reasonCodes)}")
                    if (event.selected.length() > 0) appendLine("  배치·선택: ${stringArray(event.selected)}")
                    if (event.deferred.length() > 0) appendLine("  보류·미배정: ${stringArray(event.deferred)}")
                    if (event.excluded.length() > 0) appendLine("  배제·재배치 거부: ${stringArray(event.excluded)}")
                    if (event.nextStageInputs.length() > 0) appendLine("  다음 단계 전달값: ${sanitize(event.nextStageInputs)}")
                }
            }
            trace.events.filter { it.stageId == "TIMED_DAY_PLACEMENT_ATTEMPT" }.forEach { event ->
                val attempt = event.nextStageInputs
                val evaluations = attempt.optJSONArray("dayEvaluations")
                if (evaluations == null || evaluations.length() == 0) {
                    appendLine("실제 요일 배치: ${attempt.optString("stableKey")} / 선택 요일 ${attempt.opt("selectedDay")} / ${attempt.optString("decision")} (${event.durationMillis} ms)")
                } else for (index in 0 until evaluations.length()) {
                    val day = evaluations.optJSONObject(index) ?: continue
                    appendLine("실제 요일 후보 평가: ${attempt.optString("stableKey")}#${attempt.optString("selectionRole")} / 요일 ${day.optInt("day")} / 선택=${day.optBoolean("selected")} / 자격=${day.optBoolean("eligible")} / 시간 기존 ${day.optInt("currentSeconds")}초 + 신규 ${day.optInt("incomingSeconds")}초 ≤ ${day.optInt("availableSeconds")}초=${day.optBoolean("fitsTime")} / 중복=${day.optBoolean("sameExerciseConflict")} / 순위 ${day.optInt("scheduleTierRank")} / 사유 ${day.optString("reasonCode")} (${event.durationMillis} ms)")
                }
            }
            appendLine()
            appendLine("8. 빈 운동 요일 발생 원인")
            val empty = payload("EMPTY_TRAINING_DAYS")
            appendLine("요청 ${empty?.optInt("requestedDays")}일, 실제 운동일 ${empty?.optInt("actualTrainingDays")}일, 빈 예정 요일 ${stringArray(comparison("EMPTY_TRAINING_DAYS")?.optJSONArray("emptyScheduledDays"))}; 결정 ${latest("EMPTY_TRAINING_DAYS")?.decision ?: "기록 없음"}")
            appendLine("배정 단계에서 보류된 수요: ${stringArray(latest("SPLIT_AWARE_CONTINUITY_ALLOCATION")?.deferred)}")
            appendLine("빈도 확장 분기 사유: ${latest("FREQUENCY_EXPANSION")?.let { "${it.decision} / ${stringArray(it.reasonCodes)}" } ?: "확장 분기 이벤트가 실행되지 않았거나 해당 단계에 도달하지 않음"}")
            appendLine("총량 상한으로 보류된 세트: ${stringArray(latest("FINITE_EXECUTION_ALLOCATOR")?.deferred)}; 배정 불가 수요 코드: ${stringArray(latest("FINITE_EXECUTION_ALLOCATOR")?.reasonCodes)}")
            appendLine()
            appendLine("9. 최종 생성 프로그램")
            val program = payload("FINAL_PROGRAM")
            appendLine("출처 ${latest("B9_RETURN")?.selected?.let(::stringArray) ?: "미반환"}; 요청 운동일 ${program?.optInt("requestedWeeklyDays")}, 1주차 물리 세트 ${program?.optInt("weekOnePhysicalSets")}, 전체 기간 물리 세트 ${program?.optInt("totalPhysicalSets")}")
            appendLine("B7 비교 진단: ${latest("B7_DIAGNOSTIC")?.decision ?: "기록 없음"}; 사유 ${latest("B7_DIAGNOSTIC")?.let { stringArray(it.reasonCodes) }.orEmpty()}")
            appendLine("B8 cutover 진단: ${latest("B8_DIAGNOSTIC")?.decision ?: "기록 없음"}; 사유 ${latest("B8_DIAGNOSTIC")?.let { stringArray(it.reasonCodes) }.orEmpty()}")
            val weeks = program?.optJSONObject("itemsByWeekAndDay")
            if (weeks != null) {
                val weekKeys = weeks.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                weekKeys.forEach { weekKey ->
                    appendLine("${weekKey}주차")
                    val days = weeks.optJSONArray(weekKey) ?: JSONArray()
                    for (index in 0 until days.length()) {
                        val day = days.optJSONObject(index) ?: continue
                        val items = day.optJSONArray("items") ?: JSONArray()
                        if (items.length() == 0) continue
                        appendLine("- ${day.optInt("dayOfWeek")}요일")
                        for (itemIndex in 0 until items.length()) {
                            val item = items.optJSONObject(itemIndex) ?: continue
                            appendLine("  ${item.optString("name")} (${item.optString("stableKey")}, ${item.optString("selectionRole")}): ${item.optInt("setCount")}세트; 선택 이유 ${item.optString("selectionReason")}")
                            val sets = item.optJSONArray("sets") ?: JSONArray()
                            for (setIndex in 0 until sets.length()) {
                                val set = sets.optJSONObject(setIndex) ?: continue
                                appendLine("    ${set.optInt("setIndex")}번: ${set.optInt("reps")}회 / ${set.opt("weightKg")}kg (${set.optString("loadState")}) / RPE ${set.opt("targetRpeMin")} / 휴식 ${set.optInt("restSeconds")}초")
                            }
                        }
                    }
                }
            }
            val validation = payload("FINAL_VALIDATION")
            appendLine("독립 검증: ${latest("FINAL_VALIDATION")?.decision ?: "미실행"}; 사유 ${latest("FINAL_VALIDATION")?.let { stringArray(it.reasonCodes) }.orEmpty()}")
            appendLine("세션 시간·중복 검사: ${comparison("VALIDATION_SESSION_TIME")?.optJSONArray("checks") ?: "검사 결과 없음"}")
            appendLine("OFI 검사: ${comparison("VALIDATION_OFI")?.optJSONArray("checks") ?: "검사 결과 없음"}")
            appendLine("연부조직 회복 검사: ${comparison("VALIDATION_CONNECTIVE_TISSUE")?.optJSONArray("checks") ?: "검사 결과 없음"}")
            appendLine("스케줄 형식 검사: ${comparison("VALIDATION_SCHEDULE")?.optJSONArray("checks") ?: "검사 결과 없음"}")
            appendLine("운동 분류·장비·세트 모양 검사: ${comparison("VALIDATION_ROW_CLASSIFICATION_SUMMARY")?.optJSONArray("checks") ?: "검사 결과 없음"}")
            appendLine("B5/B6 대상·처방 권한 검사: ${comparison("VALIDATION_B5_B6_AUTHORITY")?.optJSONArray("ownerChecks") ?: "검사 결과 없음"}")
            appendLine("물리 세트 무결성 검사: ${comparison("VALIDATION_PHYSICAL_SET_INTEGRITY")?.optJSONArray("materializationAudits") ?: "검사 결과 없음"}")
            appendLine("독립 검증 요약: ${validation?.toString() ?: "검증 결과 없음"}")
            appendLine()
            appendLine("10. 분석·생성 시간")
            val now = System.nanoTime()
            val endedAt = trace.finishedAtNanos ?: now
            val attemptMillis = (endedAt - trace.attemptStartedNanos).coerceAtLeast(0L) / 1_000_000.0
            val generationMillis = trace.generationStartedNanos?.let { start ->
                (endedAt - start).coerceAtLeast(0L) / 1_000_000.0
            }
            appendLine("전체 실행 경과시간(ms, 입력 보정 대기 포함 가능): $attemptMillis${if (trace.finishedAtNanos == null) " (진행 중)" else ""}")
            appendLine("B1~B9 생성 실행시간(ms): ${generationMillis ?: "미실행"}")
            appendLine("사전 기록 로딩: ${duration(latest("RECORD_LOADING"))}; 생성 전 기록 로딩: ${duration(latest("RECORD_LOADING_FINAL"))}; 훈련상태 분석: ${duration(latest("TRAINING_STATE_ANALYSIS"))}")
            for (stage in listOf("B1_NEED", "B2_PERSONAL_BASELINE", "B3_STRATEGY", "B3_TRAINING_STATE_AND_REQUEST", "B4_TARGETS", "B5_CANDIDATES", "B6_AUTHORIZATION", "B6_REALIZATION", "RESISTANCE_VOLUME_WEEKS", "RESISTANCE_VOLUME_PLANNING", "EXECUTION_CAPACITY", "FINITE_EXECUTION_ALLOCATOR", "SPLIT_AWARE_CONTINUITY_ALLOCATION", "FREQUENCY_EXPANSION", "RESIDUAL_COMPLETION", "BOUNDED_DAY_REBALANCER", "POST_SPLIT_REFLOW", "VALIDATION_SCHEDULE", "VALIDATION_SESSION_TIME", "VALIDATION_OFI", "VALIDATION_CONNECTIVE_TISSUE", "VALIDATION_ROW_CLASSIFICATION_SUMMARY", "VALIDATION_B5_B6_AUTHORITY", "VALIDATION_PHYSICAL_SET_INTEGRITY", "FINAL_VALIDATION", "B9_RETURN")) {
                appendLine("${stage}: ${duration(latest(stage))}")
            }
            appendLine("총 trace 이벤트 ${trace.events.size}개; 각 이벤트의 상세 입력·기준·비교 수치·다음 단계 값은 동봉 JSON에 있습니다.")
        }
    }
}

/** Safe diagnostic projection; excludes program name, free text, account data, and source record IDs. */
internal fun diagnosticRequestJson(
    goal: String,
    weeklyDays: Int,
    sessionMinutes: Int,
    durationWeeks: Int,
    equipment: Set<String>,
    excludedStableKeys: Set<String>,
    preferredStableKeys: Set<String>,
    explicitGoal: String? = null,
    explicitDays: Int? = null,
    explicitMinutes: Int? = null
): JSONObject = JSONObject()
    .put("goal", goal).put("explicitGoal", explicitGoal)
    .put("requestedWeeklyTrainingDays", weeklyDays).put("explicitWeeklyTrainingDays", explicitDays)
    .put("requestedSessionMinutes", sessionMinutes).put("explicitSessionMinutes", explicitMinutes)
    .put("durationWeeks", durationWeeks).put("availableEquipment", JSONArray(equipment.sorted()))
    .put("excludedExerciseStableKeys", JSONArray(excludedStableKeys.sorted()))
    .put("preferredExerciseStableKeys", JSONArray(preferredStableKeys.sorted()))

/** B2 descriptive week table: physical sets remain distinct from equivalent stimulus units. */
internal fun PlanningHistorySnapshot.generationBaselineJson(): JSONObject {
    val newestEnd = cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val earliestHistory = allConfirmedSets.minOfOrNull { it.date }
    val weeks = (7 downTo 0).map { offset ->
        val end = newestEnd.minusDays(offset * 7L)
        val start = end.minusDays(6)
        val records = allConfirmedSets.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val rowsPresent = records.isNotEmpty()
        JSONObject().put("start", start.toString()).put("end", end.toString())
            .put("confirmedSetRowsPresent", rowsPresent).put("observedZeroSetWeek", JSONObject.NULL)
            .put("zeroConfirmedSetRows", records.isEmpty())
            .put("trainingWeekObservation", if (rowsPresent) "CONFIRMED_SET_ROWS_PRESENT"
                else "UNKNOWN_NO_ROWS_CANNOT_DISTINGUISH_NO_TRAINING_FROM_UNRECORDED_ACTIVITY")
            .put("unobservedWeek", !rowsPresent).put("physicalSetCount", records.size)
            .put("strengthTrainingDays", JSONArray(records.filter { it.strengthSetIntentEvidence.intent == StrengthSetIntent.PLANNED_STRENGTH }.map { it.date }.distinct().sorted().map(LocalDate::toString)))
            .put("strengthTrainingDayCount", records.filter { it.strengthSetIntentEvidence.intent == StrengthSetIntent.PLANNED_STRENGTH }.map { it.date }.distinct().size)
            .put("strengthHypertrophySetCount", records.count { it.strengthSetIntentEvidence.intent == StrengthSetIntent.PLANNED_STRENGTH })
            .put("stableKeySetCounts", JSONObject(records.groupingBy { it.stableKey }.eachCount().toSortedMap()))
    }
    val physical = weeks.map { it.optInt("physicalSetCount") }
    val observedPhysical = weeks.filter { it.optBoolean("confirmedSetRowsPresent") }.map { it.optInt("physicalSetCount") }
    val active = physical.filter { it > 0 }
    fun quantile(values: List<Int>, p: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val position = p * (sorted.size - 1)
        val lower = position.toInt()
        val upper = kotlin.math.ceil(position).toInt()
        val fraction = position - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }
    val sortedActive = active.sorted()
    val medianActive = sortedActive.takeIf { it.isNotEmpty() }?.let { values ->
        if (values.size % 2 == 1) values[values.size / 2].toDouble()
        else (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
    }
    val sortedObserved = observedPhysical.sorted()
    val medianObserved = sortedObserved.takeIf { it.isNotEmpty() }?.let { values ->
        if (values.size % 2 == 1) values[values.size / 2].toDouble()
        else (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
    }
    return JSONObject().put("cutoff", cutoff.toString()).put("windowWeeks", 8)
        .put("weeks", JSONArray(weeks)).put("activeWeekPhysicalSetMean", active.takeIf { it.isNotEmpty() }?.average())
        .put("activeWeekPhysicalSetMedian", medianActive).put("confirmedRecordWeekPhysicalSetMedian", medianObserved)
        .put("confirmedRecordWeekPhysicalSetQ25", quantile(observedPhysical, 0.25)).put("confirmedRecordWeekPhysicalSetQ75", quantile(observedPhysical, 0.75))
        .put("activeWeekPhysicalSetQ25", quantile(active, 0.25)).put("activeWeekPhysicalSetQ75", quantile(active, 0.75))
        .put("activeWeekCount", active.size).put("confirmedRecordWeekCount", observedPhysical.size)
        .put("observedZeroWeekCount", JSONObject.NULL)
        .put("zeroConfirmedSetRowWeekCount", weeks.count { it.optBoolean("zeroConfirmedSetRows") })
        .put("unobservedWeekCount", weeks.count { it.optBoolean("unobservedWeek") })
        .put("zeroWeekLimitation", "confirmed-set-only history cannot distinguish a no-training week from unrecorded activity")
        .put("earliestConfirmedSetDate", earliestHistory?.toString())
        .put("confirmedSetCountAcrossLoadedHistory", allConfirmedSets.size)
        .put("confirmedSetCountOutside56DayResistancePlannerWindow", allConfirmedSets.count {
            it.date.isBefore(cutoff.minusDays(55)) || it.date.isAfter(cutoff)
        })
        .put("quantileMethod", "linear interpolation, position=(n-1)*p")
        .put("physicalSetsAreEquivalentStimulusUnits", false)
}

internal fun GeneratedProgramSkeleton.generationProgramJson(): JSONObject {
    val rows = items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }, { it.exerciseStableKey }))
    val days = (1..7).associateWith { day -> rows.filter { it.weekNumber == 1 && it.dayOfWeek == day } }
    val schedule = JSONObject()
    for (week in 1..request.durationWeeks) {
        val dayRows = JSONArray()
        for (day in 1..7) {
            val itemRows = JSONArray()
            rows.filter { it.weekNumber == week && it.dayOfWeek == day }.forEach { item ->
                val sets = ProgramSetPrescriptionResolver.resolve(item)
                val setRows = JSONArray()
                sets.forEach { set ->
                    setRows.put(JSONObject().put("setIndex", set.setIndex).put("reps", set.reps)
                        .put("weightKg", set.weightKg).put("loadState", set.loadState.name)
                        .put("targetRpeMin", set.targetRpeMin).put("restSeconds", item.restSeconds)
                        .put("seconds", set.seconds))
                }
                itemRows.put(JSONObject().put("name", item.exerciseName).put("stableKey", item.exerciseStableKey)
                    .put("selectionRole", item.selectionRole).put("selectionReason", item.selectionReason)
                    .put("setCount", sets.size).put("sets", setRows))
            }
            dayRows.put(JSONObject().put("dayOfWeek", day).put("scheduled", weekDaySchedule[week].orEmpty().contains(day))
                .put("items", itemRows))
        }
        schedule.put(week.toString(), dayRows)
    }
    return JSONObject().put("requestedWeeklyDays", request.weeklyTrainingDays).put("durationWeeks", request.durationWeeks)
        .put("scheduledDaysByWeek", JSONObject(weekDaySchedule.mapKeys { it.key.toString() }.mapValues { JSONArray(it.value.sorted()) }))
        .put("itemsByWeekAndDay", schedule).put("weekOneActualTrainingDays", days.filterValues { it.isNotEmpty() }.keys.sorted())
        .put("weekOneEmptyScheduledDays", days.filter { (day, rowsForDay) -> weekDaySchedule[1].orEmpty().contains(day) && rowsForDay.isEmpty() }.keys.sorted())
        .put("weekOnePhysicalSets", rows.filter { it.weekNumber == 1 }.sumOf { ProgramSetPrescriptionResolver.resolve(it).size })
        .put("totalPhysicalSets", rows.sumOf { ProgramSetPrescriptionResolver.resolve(it).size })
}
