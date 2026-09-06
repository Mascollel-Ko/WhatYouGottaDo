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

internal data class LegacyAutoSlotCaps(
    val totalSlots: Int,
    val mainCap: Int,
    val accessoryCap: Int
)

internal data class LegacyAutoDayRule(
    val label: String,
    val mainArea: LegacyAutoMainArea?,
    val pairedPriorities: List<LegacyAutoMainArea> = emptyList(),
    val secondaryMainArea: LegacyAutoMainArea? = null,
    val transferFocused: Boolean = false
)

internal object LegacyAutoRuleTables {
    fun slotCaps(sessionMinutes: Int): LegacyAutoSlotCaps =
        when {
            sessionMinutes <= 30 -> LegacyAutoSlotCaps(totalSlots = 3, mainCap = 2, accessoryCap = 3)
            sessionMinutes <= 45 -> LegacyAutoSlotCaps(totalSlots = 4, mainCap = 2, accessoryCap = 4)
            else -> LegacyAutoSlotCaps(totalSlots = 5, mainCap = 2, accessoryCap = 5)
        }

    fun intensityTable(durationWeeks: Int): List<Map<LegacyAutoMainArea, LegacyAutoIntensityLabel>> =
        when (durationWeeks.coerceIn(3, 8)) {
            3 -> threeWeek
            4 -> threeWeek + deloadWeek
            5 -> fiveWeek
            6 -> sixWeek
            7 -> sixWeek + deloadWeek
            else -> threeWeek + deloadWeek + threeWeek + deloadWeek
        }

    fun dayRules(weeklyDays: Int, week: Int): List<LegacyAutoDayRule> =
        when (weeklyDays.coerceIn(3, 7)) {
            3 -> listOf(
                LegacyAutoDayRule("Lower anterior", LegacyAutoMainArea.LOWER_ANTERIOR, listOf(LegacyAutoMainArea.LOWER_ANTERIOR)),
                if (week % 2 == 1) {
                    LegacyAutoDayRule(
                        label = "Chest / shoulder A",
                        mainArea = LegacyAutoMainArea.CHEST,
                        secondaryMainArea = LegacyAutoMainArea.SHOULDER
                    )
                } else {
                    LegacyAutoDayRule(
                        label = "Chest / shoulder B",
                        mainArea = LegacyAutoMainArea.SHOULDER,
                        secondaryMainArea = LegacyAutoMainArea.CHEST
                    )
                },
                LegacyAutoDayRule(
                    "Lower posterior + back",
                    LegacyAutoMainArea.LOWER_POSTERIOR,
                    listOf(LegacyAutoMainArea.BACK, LegacyAutoMainArea.LOWER_POSTERIOR)
                )
            )
            4 -> listOf(
                LegacyAutoDayRule("Lower anterior", LegacyAutoMainArea.LOWER_ANTERIOR, listOf(LegacyAutoMainArea.LOWER_ANTERIOR)),
                LegacyAutoDayRule("Shoulder + chest", LegacyAutoMainArea.SHOULDER, listOf(LegacyAutoMainArea.CHEST)),
                LegacyAutoDayRule("Back", LegacyAutoMainArea.BACK, listOf(LegacyAutoMainArea.BACK)),
                LegacyAutoDayRule("Lower posterior", LegacyAutoMainArea.LOWER_POSTERIOR, listOf(LegacyAutoMainArea.LOWER_POSTERIOR))
            )
            else -> buildList {
                add(LegacyAutoDayRule("Lower anterior", LegacyAutoMainArea.LOWER_ANTERIOR, listOf(LegacyAutoMainArea.LOWER_ANTERIOR)))
                add(LegacyAutoDayRule("Chest", LegacyAutoMainArea.CHEST, listOf(LegacyAutoMainArea.CHEST)))
                add(LegacyAutoDayRule("Back", LegacyAutoMainArea.BACK, listOf(LegacyAutoMainArea.BACK)))
                add(LegacyAutoDayRule("Lower posterior", LegacyAutoMainArea.LOWER_POSTERIOR, listOf(LegacyAutoMainArea.LOWER_POSTERIOR)))
                add(LegacyAutoDayRule("Shoulder accessory / transfer", null, listOf(LegacyAutoMainArea.SHOULDER), transferFocused = true))
                repeat((weeklyDays.coerceIn(3, 7) - 5).coerceAtLeast(0)) {
                    add(LegacyAutoDayRule("Accessory / transfer", null, transferFocused = true))
                }
            }
        }

    fun badmintonTargetCount(ratio: Double, sessionMinutes: Int, globalDayIndex: Int): Int =
        when {
            ratio <= 0.0 -> 0
            ratio <= 0.30 -> 1
            ratio <= 0.50 -> if (globalDayIndex % 2 == 0) 2 else 1
            sessionMinutes >= 60 -> 3
            else -> 2
        }

    val mainExercises: Map<LegacyAutoMainArea, List<LegacyAutoExerciseSpec>> = mapOf(
        LegacyAutoMainArea.LOWER_ANTERIOR to listOf(
            main("barbell_back_squat", "스쿼트", LegacyAutoMainArea.LOWER_ANTERIOR, "main-squat")
        ),
        LegacyAutoMainArea.LOWER_POSTERIOR to listOf(
            main("barbell_deadlift", "데드리프트", LegacyAutoMainArea.LOWER_POSTERIOR, "main-hinge")
        ),
        LegacyAutoMainArea.CHEST to listOf(
            main("barbell_bench_press", "벤치프레스", LegacyAutoMainArea.CHEST, "main-chest"),
            main("ex_3a7d3eda", "덤벨 벤치프레스", LegacyAutoMainArea.CHEST, "main-chest"),
            main("ex_a61f1e96", "인클라인 덤벨 프레스", LegacyAutoMainArea.CHEST, "main-chest")
        ),
        LegacyAutoMainArea.SHOULDER to listOf(
            main("ex_79f3bdbe", "덤벨 숄더프레스", LegacyAutoMainArea.SHOULDER, "main-shoulder"),
            main("ex_32219f7a", "오버헤드 프레스", LegacyAutoMainArea.SHOULDER, "main-shoulder"),
            main("ex_bb4b4276", "등받이 없는 덤벨 시티드 숄더프레스", LegacyAutoMainArea.SHOULDER, "main-shoulder"),
            main(
                "half_kneeling_single_arm_dumbbell_press",
                "하프 닐링 원암 덤벨 프레스",
                LegacyAutoMainArea.SHOULDER,
                "main-shoulder"
            ),
            main(
                "half_kneeling_single_arm_kettlebell_press",
                "하프 닐링 원암 케틀벨 프레스",
                LegacyAutoMainArea.SHOULDER,
                "main-shoulder"
            )
        ),
        LegacyAutoMainArea.BACK to listOf(
            main("ex_e41f4c2b", "중량 풀업", LegacyAutoMainArea.BACK, "main-back")
        )
    )

    val pairedAccessories: Map<LegacyAutoMainArea, List<LegacyAutoExerciseSpec>> = mapOf(
        LegacyAutoMainArea.LOWER_ANTERIOR to paired(
            LegacyAutoMainArea.LOWER_ANTERIOR,
            "ex_f2a79d37" to "스플릿 스쿼트",
            "ex_e9e97659" to "핵 스쿼트",
            "ex_b78a8f95" to "레그 익스텐션",
            "ex_c5043892" to "프론트 스쿼트"
        ),
        LegacyAutoMainArea.LOWER_POSTERIOR to paired(
            LegacyAutoMainArea.LOWER_POSTERIOR,
            "barbell_romanian_deadlift" to "루마니안 바벨 데드리프트",
            "dumbbell_romanian_deadlift" to "루마니안 덤벨 데드리프트",
            "dumbbell_single_leg_rdl" to "덤벨 원레그 루마니안 데드리프트",
            "kettlebell_single_leg_rdl" to "케틀벨 원레그 루마니안 데드리프트",
            "ex_721f7b5b" to "힙 쓰러스트",
            "ex_2822ec2e" to "레그 컬"
        ),
        LegacyAutoMainArea.CHEST to paired(
            LegacyAutoMainArea.CHEST,
            "ex_28902b13" to "푸시업",
            "ex_1dbee10e" to "머신 체스트프레스",
            "ex_4c779df2" to "케이블 플라이",
            "ex_6463edad" to "딥스"
        ),
        LegacyAutoMainArea.SHOULDER to paired(
            LegacyAutoMainArea.SHOULDER,
            "ex_93538692" to "덤벨 래터럴 레이즈",
            "kettlebell_halo" to "케틀벨 헤일로",
            "face_pull" to "페이스풀"
        ),
        LegacyAutoMainArea.BACK to paired(
            LegacyAutoMainArea.BACK,
            "ex_dc9e5953" to "랫풀다운",
            "ex_fa31f7a6" to "케이블 로우",
            "ex_ca5cce66" to "스트레이트암 풀다운",
            "ex_30a0e9aa" to "원암 덤벨 로우"
        )
    )

    val smallPartAccessories: Map<LegacyAutoSmallPart, List<LegacyAutoExerciseSpec>> = mapOf(
        LegacyAutoSmallPart.BICEPS to small(
            LegacyAutoSmallPart.BICEPS,
            "ex_281347da" to "덤벨 컬",
            "ex_2892da5a" to "해머 컬",
            "barbell_reverse_curl" to "바벨 리버스 컬",
            "ez_bar_reverse_curl" to "EZ바 리버스 컬"
        ),
        LegacyAutoSmallPart.TRICEPS to small(
            LegacyAutoSmallPart.TRICEPS,
            "cable_overhead_triceps_extension" to "케이블 오버헤드 트라이셉스 익스텐션",
            "dumbbell_overhead_triceps_extension" to "덤벨 오버헤드 트라이셉스 익스텐션"
        ),
        LegacyAutoSmallPart.FOREARM to small(
            LegacyAutoSmallPart.FOREARM,
            "ex_f6703b06" to "덤벨 프로네이션/수피네이션",
            "barbell_reverse_curl" to "바벨 리버스 컬",
            "ez_bar_reverse_curl" to "EZ바 리버스 컬"
        ),
        LegacyAutoSmallPart.CALF to small(
            LegacyAutoSmallPart.CALF,
            "ex_5ca7133f" to "원레그 카프 레이즈",
            "standing_bodyweight_calf_raise" to "맨몸 스탠딩 카프 레이즈",
            "standing_calf_raise_machine" to "스탠딩 카프 레이즈 머신",
            "standing_dumbbell_calf_raise" to "덤벨 스탠딩 카프 레이즈"
        )
    )

    val badmintonAccessories: Map<LegacyAutoBadmintonCategory, List<LegacyAutoExerciseSpec>> = mapOf(
        LegacyAutoBadmintonCategory.STEP to badminton(
            LegacyAutoBadmintonCategory.STEP,
            "ex_33841b88" to "6코너 풋워크"
        ),
        LegacyAutoBadmintonCategory.ACCELERATION to badminton(
            LegacyAutoBadmintonCategory.ACCELERATION,
            "medicine_ball_three_step_acceleration_throw" to "메디신볼 3스텝 가속 던지기"
        ),
        LegacyAutoBadmintonCategory.DECELERATION to badminton(
            LegacyAutoBadmintonCategory.DECELERATION,
            "lateral_bound_continuous" to "래터럴 바운드",
            "ex_314df428" to "원레그 홉 투 스틱",
            "medicine_ball_three_step_deceleration_throw" to "메디신볼 3스텝 감속 던지기",
            "ex_421ba24b" to "좌우 랜덤 콕줍기",
            "ex_bc84eb7f" to "6방향 랜덤 콕줍기"
        ),
        LegacyAutoBadmintonCategory.REACTION to badminton(
            LegacyAutoBadmintonCategory.REACTION,
            "ex_c5f4c242" to "랜덤 비프 풋워크",
            "ex_8e69fc74" to "스플릿 스텝 리액션"
        ),
        LegacyAutoBadmintonCategory.ANTI_ROTATION to badminton(
            LegacyAutoBadmintonCategory.ANTI_ROTATION,
            "ex_d5bdffe1" to "데드버그",
            "landmine_anti_rotation" to "랜드마인 안티로테이션",
            "band_pallof_press" to "밴드 팔로프 프레스",
            "cable_pallof_press" to "케이블 팔로프 프레스",
            "vipr_rotational_lift" to "바이퍼 회전 위로",
            "kettlebell_halo" to "케틀벨 헤일로"
        ),
        LegacyAutoBadmintonCategory.ROTATION_GENERATION to badminton(
            LegacyAutoBadmintonCategory.ROTATION_GENERATION,
            "vipr_chop" to "바이퍼 회전 아래로"
        )
    )

    private val threeWeek = listOf(
        week(
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.LOW_HIGH,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_LOW
        ),
        week(
            LegacyAutoIntensityLabel.MEDIUM_LOW,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_LOW,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.HIGH_LOW
        ),
        week(
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.LOW_HIGH,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM
        )
    )

    private val deloadWeek = week(
        LegacyAutoIntensityLabel.DELOAD,
        LegacyAutoIntensityLabel.DELOAD,
        LegacyAutoIntensityLabel.DELOAD,
        LegacyAutoIntensityLabel.DELOAD,
        LegacyAutoIntensityLabel.DELOAD
    )

    private val fiveWeek = listOf(
        threeWeek[0].toMutableMap().apply { this[LegacyAutoMainArea.SHOULDER] = LegacyAutoIntensityLabel.LOW_HIGH }.toMap(),
        threeWeek[1].toMutableMap().apply { this[LegacyAutoMainArea.SHOULDER] = LegacyAutoIntensityLabel.LOW_HIGH }.toMap(),
        deloadWeek,
        swapped(threeWeek[0]),
        swapped(threeWeek[1])
    )

    private val sixWeek = threeWeek + listOf(
        week(
            LegacyAutoIntensityLabel.MEDIUM_LOW,
            LegacyAutoIntensityLabel.LOW_HIGH,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_LOW
        ),
        week(
            LegacyAutoIntensityLabel.MEDIUM_LOW,
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.MEDIUM_LOW,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.MEDIUM_LOW
        ),
        week(
            LegacyAutoIntensityLabel.MEDIUM_MEDIUM,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.HIGH_LOW,
            LegacyAutoIntensityLabel.LOW_HIGH,
            LegacyAutoIntensityLabel.MEDIUM_LOW
        )
    )

    private fun main(
        stableKey: String,
        name: String,
        area: LegacyAutoMainArea,
        group: String
    ) = LegacyAutoExerciseSpec(
        displayName = name,
        slotType = LegacyAutoAutoSlotType.MAIN,
        stableKey = stableKey,
        mainArea = area,
        substitutionGroup = group
    )

    private fun paired(
        area: LegacyAutoMainArea,
        vararg exercises: Pair<String, String>
    ): List<LegacyAutoExerciseSpec> =
        exercises.map { (stableKey, name) ->
            LegacyAutoExerciseSpec(
                displayName = name,
                slotType = LegacyAutoAutoSlotType.STRENGTH_ACCESSORY,
                stableKey = stableKey,
                strengthAccessoryClass = LegacyAutoStrengthAccessoryClass.PAIRED_MAIN_ACCESSORY,
                pairedMainArea = area,
                substitutionGroup = "paired-${area.name.lowercase()}"
            )
        }

    private fun small(
        part: LegacyAutoSmallPart,
        vararg exercises: Pair<String, String>
    ): List<LegacyAutoExerciseSpec> =
        exercises.map { (stableKey, name) ->
            LegacyAutoExerciseSpec(
                displayName = name,
                slotType = LegacyAutoAutoSlotType.STRENGTH_ACCESSORY,
                stableKey = stableKey,
                strengthAccessoryClass = LegacyAutoStrengthAccessoryClass.SMALL_PART_ACCESSORY,
                strengthBodyPart = part,
                substitutionGroup = "small-${part.name.lowercase()}"
            )
        }

    private fun badminton(
        category: LegacyAutoBadmintonCategory,
        vararg exercises: Pair<String, String>
    ): List<LegacyAutoExerciseSpec> =
        exercises.map { (stableKey, name) ->
            LegacyAutoExerciseSpec(
                displayName = name,
                slotType = LegacyAutoAutoSlotType.BADMINTON_ACCESSORY,
                stableKey = stableKey,
                badmintonCategory = category,
                substitutionGroup = "badminton-${category.name.lowercase()}"
            )
        }

    private fun week(
        lowerAnterior: LegacyAutoIntensityLabel,
        shoulder: LegacyAutoIntensityLabel,
        chest: LegacyAutoIntensityLabel,
        back: LegacyAutoIntensityLabel,
        lowerPosterior: LegacyAutoIntensityLabel
    ): Map<LegacyAutoMainArea, LegacyAutoIntensityLabel> = mapOf(
        LegacyAutoMainArea.LOWER_ANTERIOR to lowerAnterior,
        LegacyAutoMainArea.SHOULDER to shoulder,
        LegacyAutoMainArea.CHEST to chest,
        LegacyAutoMainArea.BACK to back,
        LegacyAutoMainArea.LOWER_POSTERIOR to lowerPosterior
    )

    private fun swapped(source: Map<LegacyAutoMainArea, LegacyAutoIntensityLabel>): Map<LegacyAutoMainArea, LegacyAutoIntensityLabel> =
        mapOf(
            LegacyAutoMainArea.LOWER_ANTERIOR to source.getValue(LegacyAutoMainArea.SHOULDER),
            LegacyAutoMainArea.SHOULDER to source.getValue(LegacyAutoMainArea.LOWER_ANTERIOR),
            LegacyAutoMainArea.CHEST to source.getValue(LegacyAutoMainArea.LOWER_POSTERIOR),
            LegacyAutoMainArea.LOWER_POSTERIOR to source.getValue(LegacyAutoMainArea.CHEST),
            LegacyAutoMainArea.BACK to source.getValue(LegacyAutoMainArea.BACK)
        )
}
