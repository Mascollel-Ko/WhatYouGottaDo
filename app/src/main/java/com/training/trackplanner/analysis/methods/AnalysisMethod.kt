package com.training.trackplanner.analysis.methods

interface AnalysisMethod<I, R> {
    val methodId: String
    val versionIntroduced: String
    val enabled: Boolean
        get() = false

    fun analyze(input: I): R
}
