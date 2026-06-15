package com.training.trackplanner.analysis.methods

data class AnalysisMethodResult(
    val methodId: String,
    val status: AnalysisStatus,
    val confidence: AnalysisConfidence,
    val keySignals: List<String> = emptyList(),
    val mainMessage: String? = null,
    val subMessage: String? = null
)

enum class AnalysisStatus {
    NOT_AVAILABLE,
    INSUFFICIENT_DATA,
    NORMAL,
    WATCH,
    ELEVATED,
    HIGH
}

enum class AnalysisConfidence {
    LOW,
    MEDIUM,
    HIGH
}
