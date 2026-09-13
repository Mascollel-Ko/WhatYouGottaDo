package com.training.trackplanner.data.personalized

/** Transient execution milestones, not elapsed-time estimates or planner decision inputs. */
enum class PersonalizedPlannerStage(val percent: Int, val message: String) {
    INPUT(5, "입력 조건을 확인하는 중입니다."),
    HISTORY(10, "완료 기록과 운동 정보를 불러오는 중입니다."),
    PATTERNS(15, "최근 훈련 패턴을 분석하는 중입니다."),
    ADAPTATION(22, "현재 적응 상태와 보완 대상을 판단하는 중입니다."),
    DEMAND(30, "기본 주간 운동 수요를 구성하는 중입니다."),
    PLACEMENT(38, "기본 프로그램을 배치하는 중입니다."),
    BASE_REVIEW(44, "기본 프로그램의 배치와 회복 조건을 확인하는 중입니다."),
    EXPANSION(49, "선택한 운동 일수에 맞춰 추가 수요를 검토하는 중입니다."),
    DISTRIBUTION(55, "운동량을 여러 훈련일에 배치하는 중입니다."),
    FEASIBILITY(60, "일별 피로도와 연결조직 회복을 확인하는 중입니다."),
    EXPANSION_RECHECK(62, "추가 운동의 배치와 회복 조건을 다시 확인하는 중입니다."),
    RESIDUAL(63, "남은 처방과 배치 가능성을 확인하는 중입니다."),
    BALANCE(64, "훈련일 간 균형을 최종 조정하는 중입니다."),
    POST_SPLIT_REFLOW(65, "분할된 운동을 기준으로 주간 배치를 다시 조정하는 중입니다."),
    FINAL(98, "프로그램을 최종 검증하는 중입니다."),
    COMPLETE(100, "프로그램 구성을 완료했습니다.")
}

fun interface PersonalizedPlannerProgressReporter {
    fun report(stage: PersonalizedPlannerStage)
    fun report(update: PersonalizedPlannerProgress) { report(update.stage) }
    companion object { val NONE = PersonalizedPlannerProgressReporter { } }
}

/** Transient substage update. The stage overload keeps existing callers and no-op reporters compatible. */
data class PersonalizedPlannerProgress(val stage: PersonalizedPlannerStage, val percent: Int, val message: String) {
    init {
        require(percent in 0..100)
        require(stage != PersonalizedPlannerStage.POST_SPLIT_REFLOW || percent in 65..96)
    }
}

/** Observes existing search work only; never used by candidate selection, caches or hard gates.
 * Later rounds have unknown length: their percentages saturate at 94, with real phase messages.
 * Only finalization can reach 95, and only a returned reflow result can reach 96.
 */
internal class ReflowProgress(private val reporter: PersonalizedPlannerProgressReporter) {
    private var percent = 65
    private var previous: PersonalizedPlannerProgress? = null
    private fun emit(value: Int, message: String) {
        percent = maxOf(percent, value)
        val update = PersonalizedPlannerProgress(PersonalizedPlannerStage.POST_SPLIT_REFLOW, percent, message)
        if (update != previous) { reporter.report(update); previous = update }
    }
    fun prepared() = emit(68, "주간 배치 후보를 준비하는 중입니다.")
    fun baselineValidation() = emit(70, "기준 배치의 피로도와 연결조직 조건을 확인하는 중입니다.")
    fun roundStarted(round: Int) = emit(if (round == 0) 72 else 90,
        if (round == 0) "주간 배치 후보를 비교하는 중입니다." else "조정된 배치에서 후보를 다시 비교하는 중입니다.")
    fun comparedRows(round: Int, completed: Int, total: Int) {
        require(completed in 0..total && total > 0)
        emit(if (round == 0) 72 + 12 * completed / total else 90 + 2 * completed / total,
            if (round == 0) "주간 배치 후보를 비교하는 중입니다." else "조정된 배치에서 후보를 다시 비교하는 중입니다.")
    }
    fun validating(round: Int, completed: Int, total: Int) {
        require(completed in 0..total && total > 0)
        emit(if (round == 0) 84 + 5 * completed / total else 93 + completed / total,
            "배치 후보의 피로도와 연결조직 조건을 검증하는 중입니다.")
    }
    fun moved() = emit(90, "조정된 배치에서 다시 확인하는 중입니다.")
    fun finalizing() = emit(95, "주간 배치 결과를 최종 확인하는 중입니다.")
    fun complete() = emit(96, "주간 배치 조정을 완료했습니다.")
}
