package com.training.trackplanner.data

internal data class ProgramSlotCaps(
    val totalSlots: Int,
    val mainCap: Int,
    val accessoryCap: Int
)

internal data class ProgramDayRule(
    val label: String,
    val mainArea: ProgramMainArea?,
    val pairedPriorities: List<ProgramMainArea> = emptyList(),
    val secondaryMainArea: ProgramMainArea? = null,
    val transferFocused: Boolean = false
)

internal object ProgramRuleTables {
    fun slotCaps(sessionMinutes: Int): ProgramSlotCaps =
        when {
            sessionMinutes <= 30 -> ProgramSlotCaps(totalSlots = 3, mainCap = 2, accessoryCap = 3)
            sessionMinutes <= 45 -> ProgramSlotCaps(totalSlots = 4, mainCap = 2, accessoryCap = 4)
            else -> ProgramSlotCaps(totalSlots = 5, mainCap = 2, accessoryCap = 5)
        }

    fun intensityTable(durationWeeks: Int): List<Map<ProgramMainArea, ProgramIntensityLabel>> =
        when (durationWeeks.coerceIn(3, 8)) {
            3 -> threeWeek
            4 -> threeWeek + deloadWeek
            5 -> fiveWeek
            6 -> sixWeek
            7 -> sixWeek + deloadWeek
            else -> threeWeek + deloadWeek + threeWeek + deloadWeek
        }

    fun dayRules(weeklyDays: Int, week: Int): List<ProgramDayRule> =
        when (weeklyDays.coerceIn(3, 7)) {
            3 -> listOf(
                ProgramDayRule("Lower anterior", ProgramMainArea.LOWER_ANTERIOR, listOf(ProgramMainArea.LOWER_ANTERIOR)),
                if (week % 2 == 1) {
                    ProgramDayRule(
                        label = "Chest / shoulder A",
                        mainArea = ProgramMainArea.CHEST,
                        secondaryMainArea = ProgramMainArea.SHOULDER
                    )
                } else {
                    ProgramDayRule(
                        label = "Chest / shoulder B",
                        mainArea = ProgramMainArea.SHOULDER,
                        secondaryMainArea = ProgramMainArea.CHEST
                    )
                },
                ProgramDayRule(
                    "Lower posterior + back",
                    ProgramMainArea.LOWER_POSTERIOR,
                    listOf(ProgramMainArea.BACK, ProgramMainArea.LOWER_POSTERIOR)
                )
            )
            4 -> listOf(
                ProgramDayRule("Lower anterior", ProgramMainArea.LOWER_ANTERIOR, listOf(ProgramMainArea.LOWER_ANTERIOR)),
                ProgramDayRule("Shoulder + chest", ProgramMainArea.SHOULDER, listOf(ProgramMainArea.CHEST)),
                ProgramDayRule("Back", ProgramMainArea.BACK, listOf(ProgramMainArea.BACK)),
                ProgramDayRule("Lower posterior", ProgramMainArea.LOWER_POSTERIOR, listOf(ProgramMainArea.LOWER_POSTERIOR))
            )
            else -> buildList {
                add(ProgramDayRule("Lower anterior", ProgramMainArea.LOWER_ANTERIOR, listOf(ProgramMainArea.LOWER_ANTERIOR)))
                add(ProgramDayRule("Chest", ProgramMainArea.CHEST, listOf(ProgramMainArea.CHEST)))
                add(ProgramDayRule("Back", ProgramMainArea.BACK, listOf(ProgramMainArea.BACK)))
                add(ProgramDayRule("Lower posterior", ProgramMainArea.LOWER_POSTERIOR, listOf(ProgramMainArea.LOWER_POSTERIOR)))
                add(ProgramDayRule("Shoulder accessory / transfer", null, listOf(ProgramMainArea.SHOULDER), transferFocused = true))
                repeat((weeklyDays.coerceIn(3, 7) - 5).coerceAtLeast(0)) {
                    add(ProgramDayRule("Accessory / transfer", null, transferFocused = true))
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

    val mainExercises: Map<ProgramMainArea, List<ProgramExerciseSpec>> = mapOf(
        ProgramMainArea.LOWER_ANTERIOR to listOf(
            main("barbell_back_squat", "스쿼트", ProgramMainArea.LOWER_ANTERIOR, "main-squat")
        ),
        ProgramMainArea.LOWER_POSTERIOR to listOf(
            main("barbell_deadlift", "데드리프트", ProgramMainArea.LOWER_POSTERIOR, "main-hinge")
        ),
        ProgramMainArea.CHEST to listOf(
            main("barbell_bench_press", "벤치프레스", ProgramMainArea.CHEST, "main-chest"),
            main("ex_3a7d3eda", "덤벨 벤치프레스", ProgramMainArea.CHEST, "main-chest"),
            main("ex_a61f1e96", "인클라인 덤벨 프레스", ProgramMainArea.CHEST, "main-chest")
        ),
        ProgramMainArea.SHOULDER to listOf(
            main("ex_79f3bdbe", "덤벨 숄더프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("ex_32219f7a", "오버헤드 프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("ex_bb4b4276", "등받이 없는 덤벨 시티드 숄더프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main(
                "half_kneeling_single_arm_dumbbell_press",
                "하프 닐링 원암 덤벨 프레스",
                ProgramMainArea.SHOULDER,
                "main-shoulder"
            ),
            main(
                "half_kneeling_single_arm_kettlebell_press",
                "하프 닐링 원암 케틀벨 프레스",
                ProgramMainArea.SHOULDER,
                "main-shoulder"
            )
        ),
        ProgramMainArea.BACK to listOf(
            main("ex_e41f4c2b", "중량 풀업", ProgramMainArea.BACK, "main-back")
        )
    )

    val pairedAccessories: Map<ProgramMainArea, List<ProgramExerciseSpec>> = mapOf(
        ProgramMainArea.LOWER_ANTERIOR to paired(
            ProgramMainArea.LOWER_ANTERIOR,
            "ex_f2a79d37" to "스플릿 스쿼트",
            "ex_e9e97659" to "핵 스쿼트",
            "ex_b78a8f95" to "레그 익스텐션",
            "ex_c5043892" to "프론트 스쿼트"
        ),
        ProgramMainArea.LOWER_POSTERIOR to paired(
            ProgramMainArea.LOWER_POSTERIOR,
            "barbell_romanian_deadlift" to "루마니안 바벨 데드리프트",
            "dumbbell_romanian_deadlift" to "루마니안 덤벨 데드리프트",
            "dumbbell_single_leg_rdl" to "덤벨 원레그 루마니안 데드리프트",
            "kettlebell_single_leg_rdl" to "케틀벨 원레그 루마니안 데드리프트",
            "ex_721f7b5b" to "힙 쓰러스트",
            "ex_2822ec2e" to "레그 컬"
        ),
        ProgramMainArea.CHEST to paired(
            ProgramMainArea.CHEST,
            "ex_28902b13" to "푸시업",
            "ex_1dbee10e" to "머신 체스트프레스",
            "ex_4c779df2" to "케이블 플라이",
            "ex_6463edad" to "딥스"
        ),
        ProgramMainArea.SHOULDER to paired(
            ProgramMainArea.SHOULDER,
            "ex_93538692" to "덤벨 래터럴 레이즈",
            "kettlebell_halo" to "케틀벨 헤일로",
            "face_pull" to "페이스풀"
        ),
        ProgramMainArea.BACK to paired(
            ProgramMainArea.BACK,
            "ex_dc9e5953" to "랫풀다운",
            "ex_fa31f7a6" to "케이블 로우",
            "ex_ca5cce66" to "스트레이트암 풀다운",
            "ex_30a0e9aa" to "원암 덤벨 로우"
        )
    )

    val smallPartAccessories: Map<ProgramSmallPart, List<ProgramExerciseSpec>> = mapOf(
        ProgramSmallPart.BICEPS to small(
            ProgramSmallPart.BICEPS,
            "ex_281347da" to "덤벨 컬",
            "ex_2892da5a" to "해머 컬",
            "barbell_reverse_curl" to "바벨 리버스 컬",
            "ez_bar_reverse_curl" to "EZ바 리버스 컬"
        ),
        ProgramSmallPart.TRICEPS to small(
            ProgramSmallPart.TRICEPS,
            "cable_overhead_triceps_extension" to "케이블 오버헤드 트라이셉스 익스텐션",
            "dumbbell_overhead_triceps_extension" to "덤벨 오버헤드 트라이셉스 익스텐션"
        ),
        ProgramSmallPart.FOREARM to small(
            ProgramSmallPart.FOREARM,
            "ex_f6703b06" to "덤벨 프로네이션/수피네이션",
            "barbell_reverse_curl" to "바벨 리버스 컬",
            "ez_bar_reverse_curl" to "EZ바 리버스 컬"
        ),
        ProgramSmallPart.CALF to small(
            ProgramSmallPart.CALF,
            "ex_5ca7133f" to "원레그 카프 레이즈",
            "standing_bodyweight_calf_raise" to "맨몸 스탠딩 카프 레이즈",
            "standing_calf_raise_machine" to "스탠딩 카프 레이즈 머신",
            "standing_dumbbell_calf_raise" to "덤벨 스탠딩 카프 레이즈"
        )
    )

    val badmintonAccessories: Map<ProgramBadmintonCategory, List<ProgramExerciseSpec>> = mapOf(
        ProgramBadmintonCategory.STEP to badminton(
            ProgramBadmintonCategory.STEP,
            "ex_33841b88" to "6코너 풋워크"
        ),
        ProgramBadmintonCategory.ACCELERATION to badminton(
            ProgramBadmintonCategory.ACCELERATION,
            "medicine_ball_three_step_acceleration_throw" to "메디신볼 3스텝 가속 던지기"
        ),
        ProgramBadmintonCategory.DECELERATION to badminton(
            ProgramBadmintonCategory.DECELERATION,
            "lateral_bound_continuous" to "래터럴 바운드",
            "ex_314df428" to "원레그 홉 투 스틱",
            "medicine_ball_three_step_deceleration_throw" to "메디신볼 3스텝 감속 던지기",
            "ex_421ba24b" to "좌우 랜덤 콕줍기",
            "ex_bc84eb7f" to "6방향 랜덤 콕줍기"
        ),
        ProgramBadmintonCategory.REACTION to badminton(
            ProgramBadmintonCategory.REACTION,
            "ex_c5f4c242" to "랜덤 비프 풋워크",
            "ex_8e69fc74" to "스플릿 스텝 리액션"
        ),
        ProgramBadmintonCategory.ANTI_ROTATION to badminton(
            ProgramBadmintonCategory.ANTI_ROTATION,
            "ex_d5bdffe1" to "데드버그",
            "landmine_anti_rotation" to "랜드마인 안티로테이션",
            "band_pallof_press" to "밴드 팔로프 프레스",
            "cable_pallof_press" to "케이블 팔로프 프레스",
            "vipr_rotational_lift" to "바이퍼 회전 위로",
            "kettlebell_halo" to "케틀벨 헤일로"
        ),
        ProgramBadmintonCategory.ROTATION_GENERATION to badminton(
            ProgramBadmintonCategory.ROTATION_GENERATION,
            "vipr_chop" to "바이퍼 회전 아래로"
        )
    )

    private val threeWeek = listOf(
        week(
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.LOW_HIGH,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_LOW
        ),
        week(
            ProgramIntensityLabel.MEDIUM_LOW,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_LOW,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.HIGH_LOW
        ),
        week(
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.LOW_HIGH,
            ProgramIntensityLabel.MEDIUM_MEDIUM
        )
    )

    private val deloadWeek = week(
        ProgramIntensityLabel.DELOAD,
        ProgramIntensityLabel.DELOAD,
        ProgramIntensityLabel.DELOAD,
        ProgramIntensityLabel.DELOAD,
        ProgramIntensityLabel.DELOAD
    )

    private val fiveWeek = listOf(
        threeWeek[0].toMutableMap().apply { this[ProgramMainArea.SHOULDER] = ProgramIntensityLabel.LOW_HIGH }.toMap(),
        threeWeek[1].toMutableMap().apply { this[ProgramMainArea.SHOULDER] = ProgramIntensityLabel.LOW_HIGH }.toMap(),
        deloadWeek,
        swapped(threeWeek[0]),
        swapped(threeWeek[1])
    )

    private val sixWeek = threeWeek + listOf(
        week(
            ProgramIntensityLabel.MEDIUM_LOW,
            ProgramIntensityLabel.LOW_HIGH,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_LOW
        ),
        week(
            ProgramIntensityLabel.MEDIUM_LOW,
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.MEDIUM_LOW,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.MEDIUM_LOW
        ),
        week(
            ProgramIntensityLabel.MEDIUM_MEDIUM,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.HIGH_LOW,
            ProgramIntensityLabel.LOW_HIGH,
            ProgramIntensityLabel.MEDIUM_LOW
        )
    )

    private fun main(
        stableKey: String,
        name: String,
        area: ProgramMainArea,
        group: String
    ) = ProgramExerciseSpec(
        displayName = name,
        slotType = ProgramAutoSlotType.MAIN,
        stableKey = stableKey,
        mainArea = area,
        substitutionGroup = group
    )

    private fun paired(
        area: ProgramMainArea,
        vararg exercises: Pair<String, String>
    ): List<ProgramExerciseSpec> =
        exercises.map { (stableKey, name) ->
            ProgramExerciseSpec(
                displayName = name,
                slotType = ProgramAutoSlotType.STRENGTH_ACCESSORY,
                stableKey = stableKey,
                strengthAccessoryClass = ProgramStrengthAccessoryClass.PAIRED_MAIN_ACCESSORY,
                pairedMainArea = area,
                substitutionGroup = "paired-${area.name.lowercase()}"
            )
        }

    private fun small(
        part: ProgramSmallPart,
        vararg exercises: Pair<String, String>
    ): List<ProgramExerciseSpec> =
        exercises.map { (stableKey, name) ->
            ProgramExerciseSpec(
                displayName = name,
                slotType = ProgramAutoSlotType.STRENGTH_ACCESSORY,
                stableKey = stableKey,
                strengthAccessoryClass = ProgramStrengthAccessoryClass.SMALL_PART_ACCESSORY,
                strengthBodyPart = part,
                substitutionGroup = "small-${part.name.lowercase()}"
            )
        }

    private fun badminton(
        category: ProgramBadmintonCategory,
        vararg exercises: Pair<String, String>
    ): List<ProgramExerciseSpec> =
        exercises.map { (stableKey, name) ->
            ProgramExerciseSpec(
                displayName = name,
                slotType = ProgramAutoSlotType.BADMINTON_ACCESSORY,
                stableKey = stableKey,
                badmintonCategory = category,
                substitutionGroup = "badminton-${category.name.lowercase()}"
            )
        }

    private fun week(
        lowerAnterior: ProgramIntensityLabel,
        shoulder: ProgramIntensityLabel,
        chest: ProgramIntensityLabel,
        back: ProgramIntensityLabel,
        lowerPosterior: ProgramIntensityLabel
    ): Map<ProgramMainArea, ProgramIntensityLabel> = mapOf(
        ProgramMainArea.LOWER_ANTERIOR to lowerAnterior,
        ProgramMainArea.SHOULDER to shoulder,
        ProgramMainArea.CHEST to chest,
        ProgramMainArea.BACK to back,
        ProgramMainArea.LOWER_POSTERIOR to lowerPosterior
    )

    private fun swapped(source: Map<ProgramMainArea, ProgramIntensityLabel>): Map<ProgramMainArea, ProgramIntensityLabel> =
        mapOf(
            ProgramMainArea.LOWER_ANTERIOR to source.getValue(ProgramMainArea.SHOULDER),
            ProgramMainArea.SHOULDER to source.getValue(ProgramMainArea.LOWER_ANTERIOR),
            ProgramMainArea.CHEST to source.getValue(ProgramMainArea.LOWER_POSTERIOR),
            ProgramMainArea.LOWER_POSTERIOR to source.getValue(ProgramMainArea.CHEST),
            ProgramMainArea.BACK to source.getValue(ProgramMainArea.BACK)
        )
}
