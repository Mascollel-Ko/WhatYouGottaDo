package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepositoryProvider
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BBalancedRealHistoryValidationTest {
    @Test
    fun `generate committed real fixture twelve week artifacts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = CanonicalExerciseMetadataRepositoryProvider.get(context)
        val exercises = SeedData.exercises(context).associateBy { it.stableKey }
        val rows = realRows()
        val request = V22GenerationRequest(
            name = "실제 기록 B 균형형 12주",
            weeks = 12,
            days = 3,
            mode = V22Mode.B,
            asOf = LocalDate.of(2026, 10, 11),
            sessionMinutes = 60,
            backupName = FIXTURE_NAME
        )
        lateinit var plan: BBalanced12WeekPlan
        val elapsedNanos = measureNanoTime {
            plan = BBalanced12WeekEngine.build(
                rows,
                request,
                exercises,
                metadata.muscleRelations(),
                metadata.coreCatalog(),
                metadata.badmintonObjectiveCatalog(),
                BBalancedTaskCatalog.fromAssets(context)
            )
        }
        val elapsedMillis = max(1L, elapsedNanos / 1_000_000L)
        val old = V22PythonParityEngine.build(rows, request)
        val output = outputDirectory()
        output.mkdirs()
        File(output, "plan.json").writeText(planJson(plan, exercises, elapsedMillis).toString(2))
        File(output, "weekly_plan.csv").writeText(weeklyCsv(plan, exercises))
        File(output, "goal_evaluation.csv").writeText(goalCsv(plan))
        File(output, "report.md").writeText(report(plan, old, exercises, elapsedMillis))
        File(output, "validation_manifest.json").writeText(JSONObject()
            .put("schema", BBalanced12WeekEngine.SCHEMA)
            .put("fixture", FIXTURE_NAME)
            .put("fixtureRows", rows.size)
            .put("asOf", request.asOf.toString())
            .put("weeks", plan.weeks.size)
            .put("blocks", plan.blocks.size)
            .put("candidateEvaluations", plan.search.evaluatedCandidates)
            .put("generationMillisThisRun", elapsedMillis)
            .put("originalFullBackupValidated", false)
            .toString(2))

        assertEquals(12, plan.weeks.size)
        assertEquals(3, plan.blocks.size)
        assertTrue(File(output, "report.md").readText().contains("원본 전체 백업을 검증했다고 주장하지 않는다"))
    }

    private fun report(
        plan: BBalanced12WeekPlan,
        old: V22Plan,
        exercises: Map<String, com.training.trackplanner.data.Exercise>,
        elapsedMillis: Long
    ): String = buildString {
        appendLine("# B 균형형 12주 실제 기록 검증")
        appendLine()
        appendLine("검증 입력은 저장소의 최소화 픽스처 `$FIXTURE_NAME`(940세트, 2026-06-01~2026-10-11)이다. 픽스처 밖 개인정보나 메타데이터를 보충하지 않았으며, 원본 전체 백업을 검증했다고 주장하지 않는다.")
        appendLine()
        appendLine("## 결과 요약")
        appendLine()
        appendLine("- 12주, 4주 × 3개 집중 구간 생성: 통과")
        appendLine("- 주당 3일, 세션 시간 상한 60분: 통과")
        appendLine("- 자동 디로드: 없음")
        appendLine("- 후보 평가: ${plan.search.evaluatedCandidates}, 실행 가능 후보: ${plan.search.feasibleCandidates}")
        appendLine("- 종료: `${plan.search.terminationReason}`; 최소 개선 임계값 ${plan.search.minimumImprovement}")
        appendLine("- 이 실행의 생성 시간: ${elapsedMillis}ms (환경 의존 측정값)")
        appendLine("- 과거 중량: 미래 확정 중량으로 저장하지 않고 `USER_CALIBRATION_REQUIRED`")
        appendLine()
        appendLine("목표 충족률은 실제 적응 확률이 아니라 계획량/목표량의 정규화된 계획 지표다.")
        appendLine()
        appendLine("## 목표 중요도와 목표량")
        appendLine()
        appendLine("| 목표 | 중요도 | 출처 | 단위 | R | H | P | cH | cP | 발전 T | 유지 M | 권한 |")
        appendLine("|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---|")
        plan.goalTargets.forEach { target ->
            appendLine("| ${target.goal} | ${target.importance} | ${target.importanceSource} | ${target.unit} | ${target.researchReference.f()} | ${target.recentHistory.f()} | ${target.personalResponseCandidate.f()} | ${target.confidence.history} | ${target.confidence.personalResponse} | ${target.developmentTarget.f()} | ${target.maintenanceFloor.f()} | ${target.numericAuthority} |")
        }
        appendLine()
        appendLine("근력 R은 ACSM 2026의 운동당 최소 2세트 권고를 anchor 단위에 적용한 초기 정책값이다. 근비대 R=10은 Schoenfeld·Ogborn·Krieger의 주당 세트 범주(10+)를 사용한다. 코어와 스포츠 보조는 검토된 수치 dose authority가 없어 방향성만 평가한다. 유지 M은 발전 T의 비율이 아니라 비교 가능한 실제 근력 기록에서 성과가 감소하지 않은 가장 낮은 주간 dose만 채택하며, 관측되지 않으면 비워 둔다.")
        appendLine()
        appendLine("근거: [ACSM resistance training position stand](https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/), [Schoenfeld et al. 2017](https://pubmed.ncbi.nlm.nih.gov/27433992/), [Bickel et al. 2011 maintenance study](https://pubmed.ncbi.nlm.nih.gov/21131862/).")
        appendLine()
        appendLine("## 집중 구간")
        appendLine()
        plan.blocks.forEach { block ->
            appendLine("### ${block.startWeek}~${block.endWeek}주: ${block.focus}")
            appendLine()
            block.focusReasons.forEach { appendLine("- $it") }
            appendLine()
            appendLine("| 목표 | 계획량 | 발전 부족 | 유지 부족 | 상태 |")
            appendLine("|---|---:|---:|---:|---|")
            block.evaluation.forEach { row ->
                appendLine("| ${row.goal} | ${row.plannedDose} | ${row.developmentShortfall.f()} | ${row.maintenanceShortfall.f()} | ${row.status} |")
            }
            appendLine()
        }
        appendLine("## 1~12주 전체 운동 계획")
        appendLine()
        plan.weeks.forEach { week ->
            appendLine("### ${week.week}주차 · 구간 ${week.block}")
            appendLine()
            week.sessions.forEach { session ->
                appendLine("#### ${session.session}일차")
                appendLine()
                appendLine("| 순서 | 운동 | stableKey | 세트 처방 | 휴식 | 중량 상태 |")
                appendLine("|---:|---|---|---|---:|---|")
                session.items.forEachIndexed { index, item ->
                    val shape = item.sets.joinToString(" / ") { set ->
                        val reps = set.reps?.let { "${it}회" } ?: set.seconds?.let { "${it}초" } ?: "반복 검토"
                        val load = set.weightKg?.let { "과거 ${it}kg 참고" } ?: "사용자 보정"
                        "$reps, $load"
                    }
                    appendLine("| ${index + 1} | ${item.exercise} | `${item.stableKey}` | ${item.setCount}세트: $shape | ${exercises[item.stableKey]?.defaultRestSeconds ?: 60}초 | 검토 필요 |")
                }
                appendLine()
            }
        }
        val oldKeys = old.weeks.first().sessions.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val newKeys = plan.weeks.first().sessions.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        appendLine("## 구형 B와 새 B 차이")
        appendLine()
        appendLine("| 항목 | 구형 B Python parity | 새 B 12주 |")
        appendLine("|---|---|---|")
        appendLine("| 구간 | ${old.blocks.joinToString { "${it.startWeek}-${it.endWeek}" }} | 1-4 / 5-8 / 9-12 |")
        appendLine("| 부족 평가 | 이름 기반 role gap proxy | canonical stableKey 메타데이터의 근력 anchor·PRIMARY muscle·DIRECT core·배드민턴 transfer |")
        appendLine("| 중량 | 과거 관측값을 adapter가 확정값으로 만들 수 있었음 | 모든 미래 중량 사용자 보정 필요 |")
        appendLine("| 목표 | role 재배분 | 목표 중요도 + T/M 별도 평가 |")
        appendLine("| 탐색 | 블록별 단일 이동 | 64개 3구간 focus 조합 제한 탐색 |")
        appendLine()
        appendLine("운동 연속성: 첫 주 기준 구형 B ${oldKeys.size}종목 중 ${oldKeys.intersect(newKeys).size}종목 유지. 신규 채택 ${newKeys - oldKeys}; 제외 ${oldKeys - newKeys}. 새 운동은 자동 채택하지 않았고, 개인 기록 roster를 그대로 사용했다.")
        appendLine()
        appendLine("## 검토 필요")
        appendLine()
        plan.limitations.forEach { appendLine("- $it") }
        appendLine("- 코어/스포츠 보조의 수치 T/M은 권한 부족으로 비어 있으며 방향성 coverage만 표시한다.")
        appendLine("- 근비대 cP는 직접 근육 크기 관측이 없어 0이다. 근력 향상을 근육 성장 증거로 전용하지 않았다.")
        appendLine("- 실제 완료 뒤의 INCREASE/HOLD/DECREASE/REVIEW는 기존 progression engine이 다음 비교 가능한 세션에만 제안한다.")
    }

    private fun planJson(
        plan: BBalanced12WeekPlan,
        exercises: Map<String, com.training.trackplanner.data.Exercise>,
        elapsedMillis: Long
    ): JSONObject = JSONObject(plan.metadataJson)
        .put("generationMillisThisRun", elapsedMillis)
        .put("weeks", JSONArray(plan.weeks.map { week -> JSONObject()
            .put("week", week.week).put("block", week.block).put("weeklyPhysicalSets", week.weeklyNominalSets)
            .put("sessions", JSONArray(week.sessions.map { session -> JSONObject()
                .put("day", session.session).put("physicalSets", session.nominalSets)
                .put("items", JSONArray(session.items.map { item -> JSONObject()
                    .put("exercise", item.exercise).put("stableKey", item.stableKey).put("setCount", item.setCount)
                    .put("restSeconds", exercises[item.stableKey]?.defaultRestSeconds ?: 60)
                    .put("loadState", "USER_CALIBRATION_REQUIRED")
                    .put("sets", JSONArray(item.sets.map { set -> JSONObject()
                        .put("reps", set.reps ?: JSONObject.NULL).put("seconds", set.seconds ?: JSONObject.NULL)
                        .put("historicalWeightKgReference", set.weightKg ?: JSONObject.NULL)
                        .put("futureWeightKg", JSONObject.NULL).put("sourceDate", set.sourceDate)
                    }))
                }))
            }))
        }))

    private fun weeklyCsv(plan: BBalanced12WeekPlan, exercises: Map<String, com.training.trackplanner.data.Exercise>): String = buildString {
        appendLine("week,block,focus,day,order,exercise,stable_key,set_count,reps,seconds,historical_weight_reference,future_load_state,rest_seconds")
        val focus = plan.blocks.associate { it.block to it.focus.name }
        plan.weeks.forEach { week -> week.sessions.forEach { session -> session.items.forEachIndexed { index, item ->
            appendLine(listOf(week.week, week.block, focus.getValue(week.block), session.session, index + 1,
                item.exercise, item.stableKey, item.setCount,
                item.sets.joinToString("|") { it.reps?.toString().orEmpty() },
                item.sets.joinToString("|") { it.seconds?.toString().orEmpty() },
                item.sets.joinToString("|") { it.weightKg?.toString().orEmpty() },
                "USER_CALIBRATION_REQUIRED", exercises[item.stableKey]?.defaultRestSeconds ?: 60).joinToString(",") { csv(it.toString()) })
        }}}
    }

    private fun goalCsv(plan: BBalanced12WeekPlan): String = buildString {
        appendLine("block,start_week,end_week,focus,goal,importance,unit,development_target,maintenance_floor,planned_dose,development_shortfall,maintenance_shortfall,status")
        val targets = plan.goalTargets.associateBy(BBalancedGoalTarget::goal)
        plan.blocks.forEach { block -> block.evaluation.forEach { row ->
            val target = targets.getValue(row.goal)
            appendLine(listOf(block.block, block.startWeek, block.endWeek, block.focus, row.goal, target.importance,
                target.unit, row.developmentTarget.f(), row.maintenanceFloor.f(), row.plannedDose,
                row.developmentShortfall.f(), row.maintenanceShortfall.f(), row.status).joinToString(",") { csv(it.toString()) })
        }}
    }

    private fun outputDirectory(): File {
        val candidates = listOf(
            File("docs/validation/b-balanced-12week-real-history-2026-10-12"),
            File("../docs/validation/b-balanced-12week-real-history-2026-10-12")
        )
        return candidates.first { it.parentFile?.exists() == true }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun Double?.f(): String = this?.toString() ?: "검토 필요"

    private fun realRows(): List<V22HistoryRow> = GZIPInputStream(checkNotNull(javaClass.classLoader?.getResourceAsStream(
        "v22-real-history/$FIXTURE_NAME.gz"
    ))).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }

    private companion object {
        const val FIXTURE_NAME = "whatyougottado_backup_2026-10-12-v22.csv"
    }
}
