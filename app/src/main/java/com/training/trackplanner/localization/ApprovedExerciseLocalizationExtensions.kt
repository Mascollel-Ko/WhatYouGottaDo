package com.training.trackplanner.localization

import com.training.trackplanner.R

/**
 * Product-approved exercise copy added after the pinned 2026-08 localization baseline.
 * Kept separate from generated locale assets so a future workbook export cannot silently
 * drop the new exercise, while custom exercise user text remains untouched.
 */
internal object ApprovedExerciseLocalizationExtensions {
    private const val ROW_PRESS = "split_stance_contralateral_cable_row_dumbbell_press"

    val exerciseNameIds: Map<String, Int> =
        mapOf(ROW_PRESS to R.string.split_stance_row_press_name)

    val exerciseDescriptionIds: Map<String, Int> =
        mapOf(ROW_PRESS to R.string.split_stance_row_press_description)
}
