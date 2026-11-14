package com.training.trackplanner.data

/** Keeps the concrete, causal backup reason visible rather than hiding it in logcat. */
internal object ProgramDeleteFailureMessage {
    fun describe(error: Exception): String {
        val failure = error as? ProgramDeleteFailure
        val chain = generateSequence(error as Throwable) { it.cause }.take(12).toList()
        val diagnostic = chain.filterIsInstance<DataTransferFormatException>().firstOrNull()
        val cause = diagnostic?.let { "${it.diagnosticCode}: ${it.message}" }
            ?: chain.asReversed().firstNotNullOfOrNull { item ->
                item.message?.takeIf { it.isNotBlank() && it != "Failed requirement." }
            }
            ?: "세부 원인 불명 (로그 확인 필요)"
        val detail = cause.replace(Regex("\\s+"), " ").trim().take(350)

        return when (failure?.phase) {
            ProgramDeleteFailurePhase.BACKUP ->
                "삭제 전 복구 백업 검사 실패 (PD-BACKUP). 원인: $detail\n프로그램은 삭제되지 않았습니다."
            ProgramDeleteFailurePhase.DATABASE ->
                "프로그램 데이터베이스 삭제 실패 (PD-DB). 원인: $detail\n삭제 트랜잭션은 롤백되었습니다."
            ProgramDeleteFailurePhase.POST_COMMIT ->
                "프로그램 삭제 후 후속 처리 실패 (PD-POST). 원인: $detail\n프로그램이 실제로 삭제되었는지 목록을 확인해 주세요."
            else -> "프로그램 삭제 처리 실패 (PD-UNKNOWN). 원인: $detail"
        }
    }
}
