package com.training.trackplanner.analysis.badminton

class BadmintonTransferInsightBuilder {
    fun build(snapshot: BadmintonTransferScoreSnapshot): String {
        if (snapshot.totalTransferStimulus7d <= BadmintonTransferConstants.EPSILON) {
            return "최근 7일 배드민턴 전이 자극 기록이 부족합니다."
        }
        val highest = snapshot.axisShare7d.maxByOrNull { (_, share) -> share }?.key
        val lowest = BadmintonTransferConstants.recommendationPriority
            .minByOrNull { axis -> snapshot.axisShare7d[axis] ?: 0.0 }

        return when {
            highest != null && lowest != null && highest != lowest ->
                "최근 7일 전이 비중은 ${highest.displayName}에 가장 높고, ${lowest.displayName} 자극은 낮습니다."
            highest != null ->
                "최근 7일 전이 비중은 ${highest.displayName} 중심으로 잡혀 있습니다."
            else ->
                "최근 7일 전이축 구성이 아직 뚜렷하지 않습니다."
        }
    }
}
