package com.training.trackplanner.analysis.core

import java.time.LocalDate

interface AnalysisDateProvider {
    fun today(): LocalDate
}

class SystemAnalysisDateProvider : AnalysisDateProvider {
    override fun today(): LocalDate = LocalDate.now()
}

class FixedAnalysisDateProvider(
    private val fixedDate: LocalDate
) : AnalysisDateProvider {
    override fun today(): LocalDate = fixedDate
}
