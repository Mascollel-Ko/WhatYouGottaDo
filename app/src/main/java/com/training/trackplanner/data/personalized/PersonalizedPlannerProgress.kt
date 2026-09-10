package com.training.trackplanner.data.personalized

/** Transient execution milestones, not elapsed-time estimates or planner decision inputs. */
enum class PersonalizedPlannerStage(val percent: Int, val message: String) {
    INPUT(5, "입력 조건을 확인하는 중입니다."),
    HISTORY(12, "완료 기록과 운동 정보를 불러오는 중입니다."),
    PATTERNS(20, "최근 훈련 패턴을 분석하는 중입니다."),
    ADAPTATION(30, "현재 적응 상태와 보완 대상을 판단하는 중입니다."),
    DEMAND(40, "기본 주간 운동 수요를 구성하는 중입니다."),
    PLACEMENT(50, "기본 프로그램을 배치하는 중입니다."),
    BASE_REVIEW(55, "기본 프로그램의 배치와 회복 조건을 확인하는 중입니다."),
    EXPANSION(60, "선택한 운동 일수에 맞춰 추가 수요를 검토하는 중입니다."),
    DISTRIBUTION(70, "운동량을 여러 훈련일에 배치하는 중입니다."),
    FEASIBILITY(80, "일별 피로도와 연결조직 회복을 확인하는 중입니다."),
    EXPANSION_RECHECK(80, "추가 운동의 배치와 회복 조건을 다시 확인하는 중입니다."),
    RESIDUAL(88, "남은 처방과 배치 가능성을 확인하는 중입니다."),
    BALANCE(94, "훈련일 간 균형을 최종 조정하는 중입니다."),
    POST_SPLIT_REFLOW(96, "분할된 운동을 기준으로 주간 배치를 다시 조정하는 중입니다."),
    FINAL(98, "프로그램을 최종 검증하는 중입니다."),
    COMPLETE(100, "프로그램 구성을 완료했습니다.")
}

fun interface PersonalizedPlannerProgressReporter {
    fun report(stage: PersonalizedPlannerStage)
    companion object { val NONE = PersonalizedPlannerProgressReporter { } }
}
