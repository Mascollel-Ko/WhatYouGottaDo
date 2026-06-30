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
        "BADMINTON_DIRECT_TRANSFER" -> "직접 전이"
        "BADMINTON_FOOTWORK" -> "풋워크"
        "BADMINTON_MULTI_SHUTTLE" -> "멀티셔틀"
        else -> key.lowercase().split('_').joinToString(" ") { token ->
            token.replaceFirstChar(Char::uppercase)
        }
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
