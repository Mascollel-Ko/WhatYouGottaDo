package com.training.trackplanner.analysis.trends

object BadmintonTrainingMethodLabels {
    fun label(key: String): String = when (key.uppercase()) {
        "FOOTWORK" -> "풋워크"
        "REACTION", "REACTION_RANDOM" -> "리액션"
        "JUMP_LANDING" -> "점프/착지"
        "LUNGE_REACH", "FRONT_LUNGE" -> "런지/리치"
        "ACCELERATION", "FIRST_STEP" -> "가속/첫 스텝"
        "DECELERATION" -> "감속"
        "LATERAL_MOVE", "LATERAL_MOVEMENT" -> "측면 이동"
        "ROTATION", "ROTATION_POWER" -> "회전"
        "OVERHEAD_POWER", "OVERHEAD_REPETITION" -> "오버헤드"
        "GRIP_FOREARM" -> "전완/그립"
        "CONDITIONING", "GENERAL_CONDITIONING" -> "컨디셔닝"
        "REACTIVE" -> "반응/민첩"
        "POWER" -> "파워"
        "PLYOMETRIC" -> "플라이오메트릭"
        "STABILITY" -> "안정성"
        "STRENGTH" -> "근력"
        "ACCESSORY" -> "보조운동"
        "SUPPORT", "BADMINTON_SUPPORT" -> "기초지원"
        "BADMINTON_DIRECT_TRANSFER" -> "직접 전이"
        "BADMINTON_FOOTWORK" -> "풋워크"
        "BADMINTON_MULTI_SHUTTLE" -> "멀티셔틀"
        else -> key.lowercase().split('_').joinToString(" ") { token ->
            token.replaceFirstChar(Char::uppercase)
        }
    }

    fun description(key: String): String = when (key.uppercase()) {
        "FOOTWORK", "BADMINTON_FOOTWORK" -> "코트 이동, 스텝, 위치 회복을 직접 보강하는 훈련입니다."
        "REACTION", "REACTION_RANDOM", "REACTIVE" -> "신호나 상황 변화에 맞춰 빠르게 판단하고 반응하는 훈련입니다."
        "DECELERATION" -> "멈춤, 착지, 방향전환 직전의 감속 능력을 보강하는 훈련입니다."
        "ACCELERATION", "FIRST_STEP" -> "첫 스텝과 짧은 거리 가속을 보강하는 훈련입니다."
        "JUMP_LANDING" -> "점프와 착지 제어를 보강하는 훈련입니다."
        "ROTATION", "ROTATION_POWER" -> "스윙에 필요한 몸통 회전과 힘 전달을 보조하는 훈련입니다."
        "OVERHEAD_POWER", "OVERHEAD_REPETITION" -> "오버헤드 동작에 필요한 어깨·몸통 힘 전달을 보조하는 훈련입니다."
        "GRIP_FOREARM" -> "라켓 컨트롤에 필요한 전완과 그립 지구력을 보조하는 훈련입니다."
        "ACCESSORY" -> "주 훈련은 아니지만 배드민턴에 필요한 힘과 안정성을 보조하는 운동입니다."
        "SUPPORT", "BADMINTON_SUPPORT", "STABILITY", "STRENGTH" -> "직접 동작보다는 기초 체력, 안정성, 회복 기반을 받쳐주는 운동입니다."
        "BADMINTON_DIRECT_TRANSFER" -> "배드민턴 동작과 가까운 직접 전이 훈련입니다."
        else -> "기존 메타데이터에 따라 묶은 배드민턴 관련 훈련 유형입니다."
    }

    fun keysFrom(
        courtMovementTypes: Set<String>,
        transferRoles: Set<String>,
        sportContextTags: Set<String>,
        movementCategory: String
    ): Set<String> =
        (courtMovementTypes + transferRoles + sportContextTags + setOf(movementCategory))
            .map { it.uppercase() }
            .filter { it.isNotBlank() && it != "NONE" && it != "SKILL_DRILL" }
            .toSet()
}
