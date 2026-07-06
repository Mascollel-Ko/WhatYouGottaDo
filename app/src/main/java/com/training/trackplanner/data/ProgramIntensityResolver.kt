package com.training.trackplanner.data

internal data class ProgramPrescriptionGuide(
    val setCount: Int,
    val reps: Int,
    val seconds: Int = 0,
    val restSeconds: Int,
    val text: String,
    val weightSource: String = "RULE_TABLE"
)

internal object ProgramIntensityResolver {
    fun main(
        label: ProgramIntensityLabel,
        area: ProgramMainArea,
        weekNumber: Int
    ): ProgramPrescriptionGuide =
        when (label) {
            ProgramIntensityLabel.HIGH_LOW -> ProgramPrescriptionGuide(
                setCount = 3,
                reps = 5,
                restSeconds = 150,
                text = "3세트 x 3-5회 · RPE 7.5-8.5"
            )
            ProgramIntensityLabel.MEDIUM_LOW -> {
                val lower = area == ProgramMainArea.LOWER_ANTERIOR || area == ProgramMainArea.LOWER_POSTERIOR
                ProgramPrescriptionGuide(
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
            ProgramIntensityLabel.MEDIUM_MEDIUM -> {
                val lowerWeek3 = weekNumber == 3 &&
                    (area == ProgramMainArea.LOWER_ANTERIOR || area == ProgramMainArea.LOWER_POSTERIOR)
                ProgramPrescriptionGuide(
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
            ProgramIntensityLabel.LOW_HIGH -> ProgramPrescriptionGuide(
                setCount = 3,
                reps = 15,
                restSeconds = 90,
                text = "3-4세트 x 12-20회 · RPE 6-7.5"
            )
            ProgramIntensityLabel.DELOAD -> ProgramPrescriptionGuide(
                setCount = 2,
                reps = 6,
                restSeconds = 120,
                text = "2세트 x 3-8회 · RPE 6-7.5 · 디로딩"
            )
        }

    fun strengthAccessory(highIntensityMain: Boolean): ProgramPrescriptionGuide =
        ProgramPrescriptionGuide(
            setCount = if (highIntensityMain) 3 else 4,
            reps = 12,
            restSeconds = 75,
            text = if (highIntensityMain) {
                "3세트 x 8-15회"
            } else {
                "3-4세트 x 8-15회"
            }
        )

    fun badminton(category: ProgramBadmintonCategory): ProgramPrescriptionGuide =
        when (category) {
            ProgramBadmintonCategory.STEP,
            ProgramBadmintonCategory.REACTION -> ProgramPrescriptionGuide(
                setCount = 3,
                reps = 0,
                seconds = 20,
                restSeconds = 60,
                text = "3라운드 x 10-20초"
            )
            ProgramBadmintonCategory.ACCELERATION,
            ProgramBadmintonCategory.DECELERATION -> ProgramPrescriptionGuide(
                setCount = 3,
                reps = 5,
                restSeconds = 75,
                text = "3세트 x 5회/side"
            )
            ProgramBadmintonCategory.ANTI_ROTATION,
            ProgramBadmintonCategory.ROTATION_GENERATION -> ProgramPrescriptionGuide(
                setCount = 3,
                reps = 10,
                restSeconds = 60,
                text = "3세트 x 8-12회"
            )
        }
}
