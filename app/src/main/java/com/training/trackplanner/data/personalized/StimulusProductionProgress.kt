package com.training.trackplanner.data.personalized

import kotlin.math.roundToInt

/**
 * Maps the two internal planner passes onto one user-facing generation operation.
 *
 * The builder keeps its existing stage semantics. This adapter is only used by the
 * production orchestration seam, so standalone builder progress remains unchanged.
 */
internal class ProductionGenerationProgressMapper(
    private val downstream: PersonalizedPlannerProgressReporter
) {
    private var lastPercent = Int.MIN_VALUE

    fun controlReporter(): PersonalizedPlannerProgressReporter = phaseReporter(5, 45)

    fun experimentalReporter(): PersonalizedPlannerProgressReporter = phaseReporter(46, 92)

    fun reportSelection() = emit(93, PersonalizedPlannerStage.FINAL.message)

    fun reportValidationComplete() = emit(98, PersonalizedPlannerStage.FINAL.message)

    /** The selected skeleton exists at this point; callers may now show completion. */
    fun reportComplete() = emit(100, PersonalizedPlannerStage.COMPLETE.message)

    private fun phaseReporter(start: Int, end: Int): PersonalizedPlannerProgressReporter =
        object : PersonalizedPlannerProgressReporter {
            override fun report(stage: PersonalizedPlannerStage) {
                report(PersonalizedPlannerProgress(stage, stage.percent, stage.message))
            }

            override fun report(update: PersonalizedPlannerProgress) {
                val normalized = ((update.percent - 5) / 93.0).coerceIn(0.0, 1.0)
                val mapped = (start + (end - start) * normalized).roundToInt()
                emit(mapped, update.message)
            }
        }

    private fun emit(percent: Int, message: String) {
        if (percent < lastPercent) return
        if (percent == lastPercent) return
        lastPercent = percent
        // FINAL is deliberately used for mapped updates: POST_SPLIT_REFLOW has a
        // narrower validation range because it is a standalone builder milestone.
        downstream.report(PersonalizedPlannerProgress(PersonalizedPlannerStage.FINAL, percent, message))
    }
}
