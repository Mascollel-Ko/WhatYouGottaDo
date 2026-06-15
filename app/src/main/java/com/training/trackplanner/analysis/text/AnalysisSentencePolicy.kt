package com.training.trackplanner.analysis.text

object AnalysisSentencePolicy {
    val prohibitedExpressions = listOf(
        "부상 위험이 높습니다",
        "반드시 쉬어야 합니다",
        "이 운동은 효과가 없습니다",
        "무조건",
        "절대"
    )

    val recommendedExpressions = listOf(
        "기록 기준으로는",
        "최근 운동량이 평소보다 빠르게 늘었습니다",
        "오늘은 강도를 조금 낮추는 편이 안전합니다",
        "내일부터 계획은 현재 상태와 크게 충돌하지 않습니다",
        "계획에 보완할 여지가 있습니다"
    )

    fun containsProhibitedExpression(message: String): Boolean =
        prohibitedExpressions.any { expression -> message.contains(expression) }
}
