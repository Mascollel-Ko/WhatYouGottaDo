package com.training.trackplanner.analysis.trends

object BadmintonTrainingMethodLabels {
    fun label(key: String): String = when (key.uppercase()) {
        "FOOTWORK" -> "풋워크"
        "REACTION", "REACTION_RANDOM" -> "리액션"
        "JUMP_LANDING" -> "점프/착지"
        "LUNGE_REACH", "FRONT_LUNGE" -> "런지/리치"
        "ACCELERATION", "FIRST_STEP" -> "가속/첫 스텝"
        "DECELERATION" -> "감속"
        "ROTATION", "ROTATION_POWER" -> "회전 생성"
        "ANTI_ROTATION", "ANTI_ROTATION_STABILITY" -> "항회전/몸통제어"
        "CONDITIONING", "GENERAL_CONDITIONING" -> "컨디셔닝"
        else -> "기타"
    }

    fun description(key: String): String = when (key.uppercase()) {
        "FOOTWORK" -> "코트 이동, 스텝, 위치 회복을 직접 보강하는 전이 목적입니다."
        "REACTION" -> "신호나 상황 변화에 맞춰 빠르게 판단하고 반응하는 전이 목적입니다."
        "DECELERATION" -> "멈춤, 착지, 방향전환 직전의 감속 능력을 보강하는 훈련입니다."
        "ACCELERATION", "FIRST_STEP" -> "첫 스텝과 짧은 거리 가속을 보강하는 훈련입니다."
        "JUMP_LANDING" -> "점프와 착지 제어를 보강하는 훈련입니다."
        "LUNGE_REACH" -> "런지, 리치, 넓은 보폭에서 자세를 유지하는 전이 목적입니다."
        "ROTATION_POWER" -> "스매시, 클리어, 드라이브처럼 하체-몸통-어깨로 힘을 전달하는 전이 목적입니다."
        "ANTI_ROTATION" -> "리치, 런지, 방향전환 중 몸통이 무너지지 않게 버티는 전이 목적입니다."
        "CONDITIONING" -> "배드민턴 훈련을 버틸 수 있는 반복 능력과 체력 기반을 보강하는 전이 목적입니다."
        else -> "기존 메타데이터에서 확인된 배드민턴 전이 목적입니다."
    }

    fun keysFrom(
        courtMovementTypes: Set<String>,
        transferRoles: Set<String>,
        skillTargets: Set<String>,
        includeAntiRotation: Boolean = true
    ): Set<String> =
        (courtMovementTypes + transferRoles + skillTargets)
            .map { it.uppercase() }
            .flatMap(::objectivesFor)
            .toSet()
            .let { keys -> if (includeAntiRotation) keys else keys - "ANTI_ROTATION" }

    private fun objectivesFor(key: String): Set<String> = when (key) {
        "FIRST_STEP", "ACCELERATION" -> setOf("ACCELERATION")
        "FIRST_STEP_REACTION" -> setOf("ACCELERATION", "REACTION")
        "DECELERATION", "DECELERATION_CONTROL" -> setOf("DECELERATION")
        "FOOTWORK", "FOOTWORK_SPEED", "SPLIT_STEP", "LATERAL_MOVE", "CROSSOVER", "REAR_COURT", "RECOVERY_STEP", "MULTI_DIRECTION" -> setOf("FOOTWORK")
        "JUMP_LANDING", "JUMP_LANDING_CONTROL" -> setOf("JUMP_LANDING")
        "LUNGE_REACH", "FRONT_LUNGE" -> setOf("LUNGE_REACH")
        "REACTION", "REACTION_RANDOM" -> setOf("REACTION")
        "CONDITIONING" -> setOf("CONDITIONING")
        "ANTI_ROTATION", "ANTI_ROTATION_STABILITY" -> setOf("ANTI_ROTATION")
        "ROTATION", "ROTATION_POWER", "ROTATION_SEQUENCING", "ROTATIONAL_POWER" -> setOf("ROTATION_POWER")
        else -> emptySet()
    }
}
