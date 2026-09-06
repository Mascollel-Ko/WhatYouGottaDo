package com.training.trackplanner.data.program.legacy

// Mechanically isolated from f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9.
// Frozen product rules: do not generalize or route through another planner.
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramSetPrescription

internal data class LegacyAutoPrescriptionGuide(
    val setCount: Int,
    val reps: Int,
    val seconds: Int = 0,
    val restSeconds: Int,
    val text: String,
    val weightSource: String = "RULE_TABLE"
)

internal object LegacyAutoIntensityResolver {
    fun main(
        label: LegacyAutoIntensityLabel,
        area: LegacyAutoMainArea,
        weekNumber: Int
    ): LegacyAutoPrescriptionGuide =
        when (label) {
            LegacyAutoIntensityLabel.HIGH_LOW -> LegacyAutoPrescriptionGuide(
                setCount = 3,
                reps = 5,
                restSeconds = 150,
                text = "3세트 x 3-5회 · RPE 7.5-8.5"
            )
            LegacyAutoIntensityLabel.MEDIUM_LOW -> {
                val lower = area == LegacyAutoMainArea.LOWER_ANTERIOR || area == LegacyAutoMainArea.LOWER_POSTERIOR
                LegacyAutoPrescriptionGuide(
                    setCount = 3,
                    reps = if (lower) 5 else 10,
                    restSeconds = if (lower) 150 else 120,
                    text = if (lower) {
                        "2-3세트 x 3-5회 · 보수적 중량 · RPE 6.5-7.5"
                    } else {
                        "2-3세트 x 8-12회 · RPE 6.5-7.5"
                    }
                )
            }
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM -> {
                val lowerWeek3 = weekNumber == 3 &&
                    (area == LegacyAutoMainArea.LOWER_ANTERIOR || area == LegacyAutoMainArea.LOWER_POSTERIOR)
                LegacyAutoPrescriptionGuide(
                    setCount = 3,
                    reps = 10,
                    restSeconds = 120,
                    text = if (lowerWeek3) {
                        "3세트 x 8-12회 · 보수적 중량 · RPE 6.5-8"
                    } else {
                        "3세트 x 8-12회 · RPE 6.5-8"
                    }
                )
            }
            LegacyAutoIntensityLabel.LOW_HIGH -> LegacyAutoPrescriptionGuide(
                setCount = 3,
                reps = 15,
                restSeconds = 90,
                text = "3-4세트 x 12-20회 · RPE 6-7.5"
            )
            LegacyAutoIntensityLabel.DELOAD -> LegacyAutoPrescriptionGuide(
                setCount = 2,
                reps = 6,
                restSeconds = 120,
                text = "2세트 x 3-8회 · RPE 6-7.5 · 디로딩"
            )
        }

    fun strengthAccessory(highIntensityMain: Boolean): LegacyAutoPrescriptionGuide =
        LegacyAutoPrescriptionGuide(
            setCount = if (highIntensityMain) 3 else 4,
            reps = 12,
            restSeconds = 75,
            text = if (highIntensityMain) {
                "3세트 x 8-15회"
            } else {
                "3-4세트 x 8-15회"
            }
        )

    fun badminton(category: LegacyAutoBadmintonCategory): LegacyAutoPrescriptionGuide =
        when (category) {
            LegacyAutoBadmintonCategory.STEP,
            LegacyAutoBadmintonCategory.REACTION -> LegacyAutoPrescriptionGuide(
                setCount = 3,
                reps = 0,
                seconds = 20,
                restSeconds = 60,
                text = "3라운드 x 10-20초"
            )
            LegacyAutoBadmintonCategory.ACCELERATION,
            LegacyAutoBadmintonCategory.DECELERATION -> LegacyAutoPrescriptionGuide(
                setCount = 3,
                reps = 5,
                restSeconds = 75,
                text = "3세트 x 5회/side"
            )
            LegacyAutoBadmintonCategory.ANTI_ROTATION,
            LegacyAutoBadmintonCategory.ROTATION_GENERATION -> LegacyAutoPrescriptionGuide(
                setCount = 3,
                reps = 10,
                restSeconds = 60,
                text = "3세트 x 8-12회"
            )
        }
}
