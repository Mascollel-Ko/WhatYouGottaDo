package com.training.trackplanner.analysis.text

import com.training.trackplanner.analysis.methods.AnalysisMethodResult

interface AnalysisSentenceBuilder {
    fun build(result: AnalysisMethodResult): AnalysisSentenceBlock
}

data class AnalysisSentenceBlock(
    val mainMessage: String?,
    val subMessage: String?
)
