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
                    ProgramDayRule("Chest / shoulder A", ProgramMainArea.CHEST, listOf(ProgramMainArea.SHOULDER))
                } else {
                    ProgramDayRule("Chest / shoulder B", ProgramMainArea.SHOULDER, listOf(ProgramMainArea.CHEST))
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
        ProgramMainArea.LOWER_ANTERIOR to listOf(main("스쿼트", ProgramMainArea.LOWER_ANTERIOR, "main-squat")),
        ProgramMainArea.LOWER_POSTERIOR to listOf(main("데드리프트", ProgramMainArea.LOWER_POSTERIOR, "main-hinge")),
        ProgramMainArea.CHEST to listOf(
            main("벤치프레스", ProgramMainArea.CHEST, "main-chest"),
            main("인클라인 벤치프레스", ProgramMainArea.CHEST, "main-chest"),
            main("덤벨 벤치프레스", ProgramMainArea.CHEST, "main-chest"),
            main("인클라인 덤벨 벤치프레스", ProgramMainArea.CHEST, "main-chest")
        ),
        ProgramMainArea.SHOULDER to listOf(
            main("덤벨 프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("케틀벨 프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("앉아서 하는 오버헤드 프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("하프닐링 원암 프레스", ProgramMainArea.SHOULDER, "main-shoulder"),
            main("랜드마인 프레스", ProgramMainArea.SHOULDER, "main-shoulder")
        ),
        ProgramMainArea.BACK to listOf(
            main("풀업", ProgramMainArea.BACK, "main-back"),
            main("중량 풀업", ProgramMainArea.BACK, "main-back")
        )
    )

    val pairedAccessories: Map<ProgramMainArea, List<ProgramExerciseSpec>> = mapOf(
        ProgramMainArea.LOWER_ANTERIOR to paired(
            ProgramMainArea.LOWER_ANTERIOR,
            "스플릿스쿼트",
            "체어스쿼트",
            "핵스쿼트"
        ),
        ProgramMainArea.LOWER_POSTERIOR to paired(
            ProgramMainArea.LOWER_POSTERIOR,
            "루마니안 데드리프트",
            "원레그 RDL",
            "힙쓰러스트",
            "레그컬 머신"
        ),
        ProgramMainArea.CHEST to paired(
            ProgramMainArea.CHEST,
            "푸시업",
            "체스트프레스 머신",
            "플라이",
            "딥스"
        ),
        ProgramMainArea.SHOULDER to paired(
            ProgramMainArea.SHOULDER,
            "덤벨 래터럴 레이즈",
            "케틀벨 헤일로",
            "랜드마인 프레스",
            "Y레이즈"
        ),
        ProgramMainArea.BACK to paired(
            ProgramMainArea.BACK,
            "랫풀다운",
            "롱풀 / 케이블로우",
            "케이블풀다운",
            "덤벨로우"
        )
    )

    val smallPartAccessories: Map<ProgramSmallPart, List<ProgramExerciseSpec>> = mapOf(
        ProgramSmallPart.BICEPS to small(ProgramSmallPart.BICEPS, "덤벨컬", "해머컬", "리버스컬"),
        ProgramSmallPart.TRICEPS to small(ProgramSmallPart.TRICEPS, "오버헤드 케이블 익스텐션"),
        ProgramSmallPart.FOREARM to small(ProgramSmallPart.FOREARM, "리버스 프로네이션&수피네이션", "리버스컬"),
        ProgramSmallPart.CALF to small(ProgramSmallPart.CALF, "원레그 카프레이즈", "투레그 카프레이즈")
    )

    val badmintonAccessories: Map<ProgramBadmintonCategory, List<ProgramExerciseSpec>> = mapOf(
        ProgramBadmintonCategory.STEP to badminton(ProgramBadmintonCategory.STEP, "6방향 섀도우 풋워크"),
        ProgramBadmintonCategory.ACCELERATION to badminton(ProgramBadmintonCategory.ACCELERATION, "불가리안백 스타트"),
        ProgramBadmintonCategory.DECELERATION to badminton(
            ProgramBadmintonCategory.DECELERATION,
            "래터럴 바운드",
            "싱글 버니홉",
            "사이드가속후감속",
            "좌우 콕줍기",
            "6방향 콕줍기"
        ),
        ProgramBadmintonCategory.REACTION to badminton(
            ProgramBadmintonCategory.REACTION,
            "2방향 랜덤 비프 풋워크 좌우",
            "2방향 랜덤 비프 풋워크 앞뒤",
            "스플릿스텝 리액션"
        ),
        ProgramBadmintonCategory.ANTI_ROTATION to badminton(
            ProgramBadmintonCategory.ANTI_ROTATION,
            "데드버그",
            "랜드마인 레인보우",
            "팔로프 프레스",
            "바이퍼 로테이션 하→상",
            "케틀벨 헤일로"
        ),
        ProgramBadmintonCategory.ROTATION_GENERATION to badminton(
            ProgramBadmintonCategory.ROTATION_GENERATION,
            "바이퍼 촙 상→하"
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

    private fun main(name: String, area: ProgramMainArea, group: String) = ProgramExerciseSpec(
        displayName = name,
        slotType = ProgramAutoSlotType.MAIN,
        mainArea = area,
        substitutionGroup = group
    )

    private fun paired(area: ProgramMainArea, vararg names: String): List<ProgramExerciseSpec> =
        names.map {
            ProgramExerciseSpec(
                displayName = it,
                slotType = ProgramAutoSlotType.STRENGTH_ACCESSORY,
                strengthAccessoryClass = ProgramStrengthAccessoryClass.PAIRED_MAIN_ACCESSORY,
                pairedMainArea = area,
                substitutionGroup = "paired-${area.name.lowercase()}"
            )
        }

    private fun small(part: ProgramSmallPart, vararg names: String): List<ProgramExerciseSpec> =
        names.map {
            ProgramExerciseSpec(
                displayName = it,
                slotType = ProgramAutoSlotType.STRENGTH_ACCESSORY,
                strengthAccessoryClass = ProgramStrengthAccessoryClass.SMALL_PART_ACCESSORY,
                strengthBodyPart = part,
                substitutionGroup = "small-${part.name.lowercase()}"
            )
        }

    private fun badminton(category: ProgramBadmintonCategory, vararg names: String): List<ProgramExerciseSpec> =
        names.map {
            ProgramExerciseSpec(
                displayName = it,
                slotType = ProgramAutoSlotType.BADMINTON_ACCESSORY,
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
