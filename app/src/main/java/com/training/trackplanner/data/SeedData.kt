package com.training.trackplanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ProgramSeed(
    val key: String,
    val name: String,
    val durationDays: Int,
    val items: List<ProgramItemSeed>
)

data class ProgramItemSeed(
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val prescription: String,
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int
)

object SeedData {
    private const val SETTINGS_SEED_ASSET = "training_settings_seed.csv"
    private const val EXERCISE_SEED_ASSET = "exercises_seed.json"
    private const val EXERCISE_IMAGE_MAPPING_ASSET = "exercise_image_mapping.csv"

    fun exercises(context: Context): List<Exercise> {
        val imageMappings = exerciseImageMappings(context)
        val rows = csvRows(context)

        return exercisesFromParsedRows(rows, imageMappings)
    }

    internal fun exercisesFromParsedRows(rows: List<Map<String, String>>): List<Exercise> =
        exercisesFromParsedRows(rows, emptyList())

    internal fun exactExerciseMetadataByStableKey(context: Context): Map<String, Exercise> {
        val imageMappings = exerciseImageMappings(context)
        val rows = csvRows(context)
        return exactExerciseMetadataFromParsedRows(rows, imageMappings)
    }

    internal fun exactExerciseMetadataFromParsedRows(rows: List<Map<String, String>>): Map<String, Exercise> =
        exactExerciseMetadataFromParsedRows(rows, emptyList())

    private fun exactExerciseMetadataFromParsedRows(
        rows: List<Map<String, String>>,
        imageMappings: List<ExerciseImageMapping>
    ): Map<String, Exercise> {
        val generatedByStableKey = exercisesFromParsedRows(rows, imageMappings)
            .associateBy { exercise -> exercise.stableKey.normalizedSeedKey() }
        return rows
            .filter { row -> row["row_type"] == "exercise" }
            .mapNotNull { row ->
                val name = normalizedExerciseName(row.value("exercise_name"))
                val stableKey = row.value("stable_key").ifBlank { stableKeyFor(name) }
                val generated = generatedByStableKey[stableKey.normalizedSeedKey()] ?: return@mapNotNull null
                stableKey.normalizedSeedKey() to generated.copy(
                    primaryMuscles = row.value("primary_muscles"),
                    secondaryMuscles = row.value("secondary_muscles"),
                    equipment = row.value("equipment_tags"),
                    equipmentTags = row.value("equipment_tags"),
                    movementPattern = row.value("movement_pattern"),
                    movementCategory = row.value("movement_category"),
                    forceType = row.value("force_type"),
                    bodyRegion = row.value("body_region"),
                    laterality = row.value("laterality"),
                    plane = row.value("plane")
                )
            }
            .toMap()
    }

    private fun exercisesFromParsedRows(
        rows: List<Map<String, String>>,
        imageMappings: List<ExerciseImageMapping>
    ): List<Exercise> =
        rows
            .filter { it["row_type"] == "exercise" }
            .map { row -> exerciseFromCsv(row, imageMappings) }
            .also(::validateCatalog)

    fun programs(context: Context): List<ProgramSeed> {
        val rows = csvRows(context)
        val itemRows = rows.filter { it["row_type"] == "program_item" }
        return rows
            .filter { it["row_type"] == "program" }
            .map { row ->
                val key = row.value("program_key")
                ProgramSeed(
                    key = key,
                    name = row.value("program_name"),
                    durationDays = row.value("duration_days").toIntOrNull() ?: 28,
                    items = itemRows
                        .filter { it.value("program_key") == key }
                        .map(::programItemFromCsv)
                )
            }
            .filter { it.name.isNotBlank() && it.items.isNotEmpty() }
    }

    private fun csvRows(context: Context): List<Map<String, String>> =
        context.assets.open(SETTINGS_SEED_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            val parsedRows = reader.lineSequence()
                .filter { it.isNotBlank() }
                .map(::parseCsvLine)
                .toList()
            val header = parsedRows.first()
            parsedRows.drop(1).map { values ->
                header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
            }
        }

    private fun exercisesFromJson(
        context: Context,
        imageMappings: List<ExerciseImageMapping>
    ): List<Exercise> =
        context.assets.open(EXERCISE_SEED_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            val root = JSONObject(reader.readText())
            val exercises = root.optJSONArray("exercises") ?: JSONArray()
            (0 until exercises.length())
                .map { index -> exerciseFromJson(exercises.getJSONObject(index), imageMappings) }
                .also(::validateCatalog)
        }

    private fun exerciseFromJson(
        item: JSONObject,
        imageMappings: List<ExerciseImageMapping>
    ): Exercise {
        val name = item.optString("name").ifBlank { item.optString("exerciseName") }
        val category = item.optString("category")
        val patternTokens = item.stringList("movementPattern").toSet()
        val stableKey = item.optString("stableKey").ifBlank { stableKeyFor(name) }
        val base = Exercise(
            name = name,
            category = category,
            detail1 = item.optString("detail1"),
            detail2 = item.optString("detail2"),
            mode = item.optString("mode"),
            description = item.optString("description"),
            defaultRestSeconds = item.optInt("defaultRestSeconds", 60),
            familyId = item.optString("familyId").ifBlank { familyIdFor(name, category) },
            familyName = item.optString("familyName").ifBlank { familyIdFor(name, category).replace('_', ' ') },
            familyRole = canonicalFamilyRole(item.optString("familyRole")),
            familyE1rmMultiplier = item.optDoubleOrNull("familyE1rmMultiplier") ?: 1.0,
            stableKey = stableKey,
            movementPattern = ExerciseTaxonomy.list(patternTokens.joinToString(","), ExerciseTaxonomy.movementPatterns, "movementPattern"),
            movementCategory = ExerciseTaxonomy.single(item.firstString("movementCategory"), ExerciseTaxonomy.movementCategories, "movementCategory"),
            primaryMuscles = ExerciseTaxonomy.list(item.stringList("primaryMuscles").joinToString(","), ExerciseTaxonomy.muscles, "primaryMuscles"),
            secondaryMuscles = ExerciseTaxonomy.list(item.stringList("secondaryMuscles").joinToString(","), ExerciseTaxonomy.muscles, "secondaryMuscles"),
            equipment = ExerciseTaxonomy.list(item.stringList("equipment").joinToString(","), ExerciseTaxonomy.equipment, "equipment"),
            equipmentTags = ExerciseTaxonomy.list(item.stringList("equipmentTags").joinToString(","), ExerciseTaxonomy.equipment, "equipmentTags"),
            forceType = ExerciseTaxonomy.single(item.firstString("forceType"), ExerciseTaxonomy.forceTypes, "forceType"),
            bodyRegion = ExerciseTaxonomy.single(item.firstString("bodyRegion"), ExerciseTaxonomy.bodyRegions, "bodyRegion"),
            plane = item.firstString("plane"),
            laterality = item.firstString("laterality"),
            trainingRole = ExerciseTaxonomy.single(item.firstString("trainingRole"), ExerciseTaxonomy.trainingRoles, "trainingRole"),
            stabilityRoles = ExerciseTaxonomy.list(item.stringList("stabilityRoles").joinToString(","), ExerciseTaxonomy.stabilityRoles, "stabilityRoles"),
            sportTransferDirect = ExerciseTaxonomy.list(item.stringList("sportTransferDirect").joinToString(","), ExerciseTaxonomy.sportTransferDirect, "sportTransferDirect"),
            sportTransferSupportive = ExerciseTaxonomy.list(item.stringList("sportTransferSupportive").joinToString(","), ExerciseTaxonomy.sportTransferSupportive, "sportTransferSupportive"),
            accessoryRoles = ExerciseTaxonomy.list(item.stringList("accessoryRoles").joinToString(","), ExerciseTaxonomy.accessoryRoles, "accessoryRoles"),
            loadProfile = ExerciseTaxonomy.single(item.firstString("loadProfile"), ExerciseTaxonomy.loadProfiles, "loadProfile"),
            metadataConfidence = item.optString("metadataConfidence").ifBlank { MetadataConfidence.LOW.name }
        )
        return ExerciseMetadataMapper.applySeedMetadata(
            exercise = base,
            source = SeedMetadataSource(
                movementPatternTokens = patternTokens,
                movementCategoryToken = item.firstString("movementCategory"),
                forceTypeToken = item.firstString("forceType"),
                planeToken = item.firstString("plane"),
                isUnilateral = item.optBooleanOrNull("isUnilateral")
            )
        ).withRecoveredImage(imageMappings)
    }

    private fun programItemFromCsv(row: Map<String, String>): ProgramItemSeed =
        ProgramItemSeed(
            weekNumber = row.value("week_number").toIntOrNull() ?: 1,
            dayOfWeek = row.value("day_of_week").toIntOrNull() ?: 1,
            orderIndex = row.value("order_index").toIntOrNull() ?: 1,
            exerciseName = row.value("exercise_name"),
            category = row.value("category"),
            restSeconds = row.value("rest_seconds").toIntOrNull()
                ?: row.value("default_rest_seconds").toIntOrNull()
                ?: 60,
            prescription = row.value("prescription"),
            setCount = row.value("set_count").toIntOrNull() ?: 1,
            reps = row.value("reps").toIntOrNull() ?: 0,
            weightKg = row.value("weight_kg").toDoubleOrNull() ?: 0.0,
            seconds = row.value("seconds").toIntOrNull() ?: 0
        )

    private fun exerciseFromCsv(
        row: Map<String, String>,
        imageMappings: List<ExerciseImageMapping>
    ): Exercise {
        val originalName = row.value("exercise_name")
        val name = normalizedExerciseName(originalName)
        val category = row.value("category")
        val detail1 = row.value("detail1")
        val detail2 = row.value("detail2")
        val mode = row.value("mode")
        val description = row.value("description").ifBlank {
            "$name: $mode 방식으로 수행한다. 주요 타깃은 ${detail1}이며 보조 요소는 ${detail2}이다."
        }
        val rest = row.value("default_rest_seconds").toIntOrNull() ?: 60
        val sourceText = listOf(name, category, detail1, detail2, mode, description).joinToString(" ")
        val familyId = row.value("family_id").ifBlank { familyIdFor(name, category) }
        val muscles = musclesFor(detail1, category, sourceText)
        val secondaryMuscles = musclesFor(detail2, category, sourceText)
            .filterNot { it in muscles }
            .ifEmpty { fallbackSecondaryMuscles(category, muscles) }
        val equipment = equipmentFor(sourceText)
        val movementPattern = movementPatternFor(name, category, sourceText)
        val forceType = forceTypeFor(name, category, sourceText, movementPattern)
        val bodyRegion = bodyRegionFor(category, muscles, secondaryMuscles, sourceText)
        val laterality = lateralityFor(sourceText)
        val trainingRole = trainingRoleFor(category, sourceText, movementPattern)
        val stabilityRoles = stabilityRolesFor(sourceText, movementPattern)
        val sportTransferDirect = sportTransferDirectFor(name, category, sourceText, movementPattern)
        val sportTransferSupportive = sportTransferSupportiveFor(category, sourceText, movementPattern)
        val accessoryRoles = accessoryRolesFor(sourceText, movementPattern)
        val loadProfile = loadProfileFor(sourceText, forceType, trainingRole)
        val seedMovementTokens = row.value("movement_pattern").splitSeedTokens()
        val activityKind = activityKindFor(category, seedMovementTokens)
        val planningEligibility = planningEligibilityFor(activityKind)

        val legacyExercise = Exercise(
            name = name,
            category = category,
            detail1 = detail1,
            detail2 = detail2,
            mode = mode,
            description = description,
            defaultRestSeconds = rest,
            familyId = familyId,
            familyName = row.value("family_name").ifBlank { familyId.replace('_', ' ') },
            familyRole = canonicalFamilyRole(row.value("family_role")),
            familyE1rmMultiplier = row.value("family_e1rm_multiplier").toDoubleOrNull() ?: 1.0,
            stableKey = row.value("stable_key").ifBlank { stableKeyFor(name) },
            movementPattern = ExerciseTaxonomy.single(movementPattern, ExerciseTaxonomy.movementPatterns, "movementPattern"),
            movementCategory = ExerciseTaxonomy.single(movementCategoryFor(category), ExerciseTaxonomy.movementCategories, "movementCategory"),
            primaryMuscles = ExerciseTaxonomy.list(muscles.joinToString(","), ExerciseTaxonomy.muscles, "primaryMuscles"),
            secondaryMuscles = ExerciseTaxonomy.list(secondaryMuscles.joinToString(","), ExerciseTaxonomy.muscles, "secondaryMuscles"),
            equipmentTags = ExerciseTaxonomy.list(equipment.joinToString(","), ExerciseTaxonomy.equipment, "equipmentTags"),
            forceType = ExerciseTaxonomy.single(forceType, ExerciseTaxonomy.forceTypes, "forceType"),
            bodyRegion = ExerciseTaxonomy.single(bodyRegion, ExerciseTaxonomy.bodyRegions, "bodyRegion"),
            laterality = ExerciseTaxonomy.single(laterality, ExerciseTaxonomy.lateralities, "laterality"),
            trainingRole = ExerciseTaxonomy.single(trainingRole, ExerciseTaxonomy.trainingRoles, "trainingRole"),
            stabilityRoles = ExerciseTaxonomy.list(stabilityRoles.joinToString(","), ExerciseTaxonomy.stabilityRoles, "stabilityRoles"),
            sportTransferDirect = ExerciseTaxonomy.list(sportTransferDirect.joinToString(","), ExerciseTaxonomy.sportTransferDirect, "sportTransferDirect"),
            sportTransferSupportive = ExerciseTaxonomy.list(sportTransferSupportive.joinToString(","), ExerciseTaxonomy.sportTransferSupportive, "sportTransferSupportive"),
            accessoryRoles = ExerciseTaxonomy.list(accessoryRoles.joinToString(","), ExerciseTaxonomy.accessoryRoles, "accessoryRoles"),
            loadProfile = ExerciseTaxonomy.single(loadProfile, ExerciseTaxonomy.loadProfiles, "loadProfile"),
            metadataConfidence = ExerciseTaxonomy.single(
                metadataConfidenceFor(name, sportTransferDirect),
                ExerciseTaxonomy.metadataConfidence,
                "metadataConfidence"
            ),
            activityKind = activityKind.name,
            planningEligibility = planningEligibility.name
        )
        return ExerciseMetadataMapper.applySeedMetadata(
            exercise = legacyExercise,
            source = SeedMetadataSource(
                movementPatternTokens = seedMovementTokens,
                movementCategoryToken = row.value("movement_category"),
                forceTypeToken = row.value("force_type"),
                planeToken = row.value("plane"),
                isUnilateral = row.value("is_unilateral").toBooleanFlagOrNull()
            )
        ).withRecoveredImage(imageMappings)
    }

    private fun activityKindFor(
        category: String,
        movementPatternTokens: Set<String>
    ): ActivityKind =
        when {
            ActivityKind.MATCH_RECORD.name in movementPatternTokens -> ActivityKind.MATCH_RECORD
            ActivityKind.SPORT_SESSION.name in movementPatternTokens -> ActivityKind.SPORT_SESSION
            category == "스포츠" -> ActivityKind.SPORT_SESSION
            else -> ActivityKind.TRAINING_EXERCISE
        }

    private fun planningEligibilityFor(activityKind: ActivityKind): PlanningEligibility =
        when (activityKind) {
            ActivityKind.TRAINING_EXERCISE -> PlanningEligibility.PROGRAM_SELECTABLE
            ActivityKind.SPORT_SESSION,
            ActivityKind.MATCH_RECORD -> PlanningEligibility.FATIGUE_ONLY
            ActivityKind.DAILY_METRIC_ONLY -> PlanningEligibility.ANALYSIS_ONLY
        }

    private fun normalizedExerciseName(name: String): String = when (name) {
        "배드민턴" -> "배드민턴 경기 기록"
        "래터럴 바운드" -> "래터럴 바운드 투 스틱"
        "싱글 레그 홉 앤 스틱" -> "홉 투 스틱"
        "6코너 섀도우 풋워크" -> "6코너 풋워크"
        "싱글 레그 RDL" -> "원레그 RDL"
        else -> name
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }

    private fun Map<String, String>.value(key: String): String = this[key]?.trim().orEmpty()

    private data class ExerciseImageMapping(
        val stableKey: String,
        val exerciseName: String,
        val imageAssetName: String,
        val needsReview: Boolean
    )

    private fun exerciseImageMappings(context: Context): List<ExerciseImageMapping> {
        if (!context.assetExists(EXERCISE_IMAGE_MAPPING_ASSET)) return emptyList()
        return context.assets.open(EXERCISE_IMAGE_MAPPING_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            val rows = reader.lineSequence()
                .filter { it.isNotBlank() }
                .map(::parseCsvLine)
                .toList()
            if (rows.isEmpty()) return emptyList()
            val header = rows.first().map { value -> value.removePrefix("\uFEFF") }
            rows.drop(1).mapNotNull { values ->
                val row = header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
                val imageAssetName = row.value("image_asset_name")
                if (imageAssetName.isBlank()) {
                    null
                } else {
                    ExerciseImageMapping(
                        stableKey = row.value("stable_key"),
                        exerciseName = row.value("exercise_name"),
                        imageAssetName = imageAssetName,
                        needsReview = row.value("needs_review") == "1"
                    )
                }
            }
        }
    }

    private fun Exercise.withRecoveredImage(imageMappings: List<ExerciseImageMapping>): Exercise {
        val mapping = imageMappings.firstOrNull { mapping ->
            mapping.stableKey.isNotBlank() && mapping.stableKey == stableKey
        } ?: imageMappings.firstOrNull { mapping ->
            mapping.exerciseName.normalizedForMapping() == name.normalizedForMapping()
        }
        return if (mapping == null) {
            this
        } else {
            copy(
                imageAssetName = mapping.imageAssetName,
                needsReview = needsReview || mapping.needsReview
            )
        }
    }

    private fun Context.assetExists(name: String): Boolean =
        runCatching {
            assets.open(name).close()
            true
        }.getOrDefault(false)

    private fun JSONObject.firstString(key: String): String =
        stringList(key).firstOrNull().orEmpty()

    private fun JSONObject.stringList(key: String): List<String> {
        val value = opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                value.optString(index).trim().takeIf { item -> item.isNotBlank() }
            }
            is String -> value.split('|', ',', '/', ';')
                .map { item -> item.trim() }
                .filter { item -> item.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun String.normalizedForMapping(): String =
        trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

    private fun String.normalizedSeedKey(): String =
        trim().lowercase(Locale.ROOT)

    private fun canonicalFamilyRole(value: String): String {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return when {
            normalized.isBlank() -> "PRIMARY"
            normalized in setOf("PRIMARY", "VARIATION", "ACCESSORY", "TEST", "SKILL") -> normalized
            normalized.matches(Regex("[A-Z0-9_]+")) -> normalized
            else -> "PRIMARY"
        }
    }

    private fun movementCategoryFor(category: String): String = when (category) {
        "근력운동" -> "STRENGTH"
        "기능성운동" -> "FUNCTIONAL"
        "유산소운동" -> "CONDITIONING"
        "스포츠" -> "SPORT"
        else -> "GENERAL"
    }

    private fun movementPatternFor(name: String, category: String, text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            category == "유산소운동" -> "CARDIO"
            category == "스포츠" -> "SPORT_SKILL"
            hasAny(text, "풋워크", "콕줍기", "스플릿 스텝", "래더", "셔틀런", "코트") -> "SPORT_SKILL"
            hasAny(text, "홉", "바운드", "스틱", "착지", "드롭 점프", "라인 홉") -> "SINGLE_LEG_DECEL"
            hasAny(text, "플랭크", "팔로프", "데드버그", "버드독", "코어", "몸통") -> "ANTI_ROTATION_CORE"
            hasAny(text, "로테이션", "회전", "트위스트") -> "ROTATION_CORE"
            hasAny(text, "캐리") -> "CARRY"
            hasAny(text, "외회전", "회전근개") -> "SHOULDER_EXTERNAL_ROTATION"
            hasAny(text, "래터럴 레이즈", "사이드 레이즈", "측면삼각") -> "SHOULDER_ABDUCTION"
            hasAny(text, "리어", "후면삼각", "페이스풀", "리버스 플라이") -> "SHOULDER_HORIZONTAL_ABDUCTION"
            hasAny(text, "트라이셉스", "삼두", "딥스") -> "ELBOW_EXTENSION"
            hasAny(text, "컬", "이두", "상완") -> "ELBOW_FLEXION"
            hasAny(text, "손목", "전완", "그립", "프로네이션", "수피네이션") -> "WRIST_GRIP"
            hasAny(text, "로우") -> "HORIZONTAL_PULL"
            hasAny(text, "풀업", "친업", "풀다운") -> "VERTICAL_PULL"
            hasAny(text, "오버헤드", "숄더프레스", "프레스", "월 슬라이드") && hasAny(text, "어깨", "숄더", "오버헤드") -> "VERTICAL_PUSH"
            hasAny(text, "벤치", "체스트", "푸시업", "푸쉬업", "플라이") -> "HORIZONTAL_PUSH"
            hasAny(text, "데드리프트", "RDL", "힙 쓰러스트", "글루트 브릿지", "레그컬", "노르딕", "스윙", "힌지") ||
                lower.contains("rdl") -> "HINGE_LOWER"
            hasAny(text, "런지", "스플릿", "스텝업", "원레그", "싱글 레그", "피스톨", "스케이터", "코사크") -> "SINGLE_LEG_STRENGTH"
            hasAny(text, "스쿼트", "레그 프레스", "레그 익스텐션", "핵") -> "KNEE_DOMINANT_LOWER"
            hasAny(text, "모빌리티", "스트레칭", "가동성") -> "MOBILITY"
            name.contains("프레스") -> "HORIZONTAL_PUSH"
            else -> "KNEE_DOMINANT_LOWER"
        }
    }

    private fun musclesFor(detail: String, category: String, text: String): List<String> {
        if (category == "유산소운동") return listOf("CALF", "QUADRICEPS", "HAMSTRING")
        if (category == "스포츠") return listOf("QUADRICEPS", "GLUTE", "CALF", "SHOULDER", "CORE")

        val source = "$detail $text"
        val muscles = linkedSetOf<String>()
        if (hasAny(source, "상부대흉")) muscles += "UPPER_CHEST"
        if (hasAny(source, "대흉", "가슴", "체스트")) muscles += "CHEST"
        if (hasAny(source, "광배")) muscles += "LAT"
        if (hasAny(source, "등", "백")) muscles += "BACK"
        if (hasAny(source, "능형")) muscles += "RHOMBOID"
        if (hasAny(source, "하부승모")) muscles += "LOWER_TRAP"
        if (hasAny(source, "승모")) muscles += "TRAPEZIUS"
        if (hasAny(source, "전면삼각")) muscles += "ANTERIOR_DELTOID"
        if (hasAny(source, "측면삼각")) muscles += "LATERAL_DELTOID"
        if (hasAny(source, "후면삼각")) muscles += "REAR_DELT"
        if (hasAny(source, "어깨", "숄더")) muscles += "SHOULDER"
        if (hasAny(source, "회전근개", "외회전")) muscles += "ROTATOR_CUFF"
        if (hasAny(source, "견갑", "전거근")) muscles += "SCAPULAR_STABILIZERS"
        if (hasAny(source, "이두")) muscles += "BICEPS"
        if (hasAny(source, "삼두")) muscles += "TRICEPS"
        if (hasAny(source, "전완", "상완요골", "손목")) muscles += "FOREARM"
        if (hasAny(source, "그립")) muscles += "GRIP"
        if (hasAny(source, "대퇴사두")) muscles += "QUADRICEPS"
        if (hasAny(source, "햄스트링", "후면사슬")) muscles += "HAMSTRING"
        if (hasAny(source, "중둔", "골반안정")) muscles += "GLUTE_MEDIUS"
        if (hasAny(source, "둔근", "엉덩")) muscles += "GLUTE"
        if (hasAny(source, "내전근")) muscles += "HIP_ADDUCTOR"
        if (hasAny(source, "종아리", "비복", "가자미", "카프", "발목")) muscles += "CALF"
        if (hasAny(source, "정강", "전경골")) muscles += "TIBIALIS"
        if (hasAny(source, "복직", "복부", "요추", "코어", "몸통")) muscles += "CORE"
        if (hasAny(source, "심부", "안정성")) muscles += "DEEP_CORE"
        if (hasAny(source, "측면코어", "사선", "회전")) muscles += "OBLIQUE"
        if (hasAny(source, "척추기립")) muscles += "ERECTOR_SPINAE"

        return muscles.toList().ifEmpty { listOf("CORE") }
    }

    private fun fallbackSecondaryMuscles(category: String, primary: List<String>): List<String> = when {
        category == "유산소운동" || category == "스포츠" -> listOf("CORE")
        "CORE" !in primary -> listOf("CORE")
        else -> listOf("DEEP_CORE")
    }

    private fun equipmentFor(text: String): List<String> {
        val equipment = linkedSetOf<String>()
        if (hasAny(text, "스미스")) equipment += "SMITH_MACHINE"
        if (hasAny(text, "레그프레스")) equipment += "LEG_PRESS_MACHINE"
        if (hasAny(text, "핵 스쿼트", "핵")) equipment += "HACK_SQUAT_MACHINE"
        if (hasAny(text, "레그컬")) equipment += "LEG_CURL_MACHINE"
        if (hasAny(text, "레그 익스텐션", "레그익스텐션")) equipment += "LEG_EXTENSION_MACHINE"
        if (hasAny(text, "힙 어브덕션")) equipment += "HIP_ABDUCTION_MACHINE"
        if (hasAny(text, "러닝머신", "트레드밀")) equipment += "TREADMILL"
        if (hasAny(text, "자전거", "바이크")) equipment += "BIKE"
        if (hasAny(text, "로잉머신")) equipment += "ROWER"
        if (hasAny(text, "바벨", "EZ바")) equipment += "BARBELL"
        if (hasAny(text, "덤벨")) equipment += "DUMBBELL"
        if (hasAny(text, "케이블")) equipment += "CABLE"
        if (hasAny(text, "머신")) equipment += "MACHINE"
        if (hasAny(text, "케틀벨")) equipment += "KETTLEBELL"
        if (hasAny(text, "랜드마인")) equipment += "LANDMINE"
        if (hasAny(text, "플레이트", "원판")) equipment += "WEIGHT_PLATE"
        if (hasAny(text, "밴드", "미니밴드")) equipment += "BAND"
        if (hasAny(text, "벤치")) equipment += "BENCH"
        if (hasAny(text, "박스", "스텝박스", "발판")) equipment += "BOX"
        if (hasAny(text, "철봉", "풀업바")) equipment += "PULLUP_BAR"
        if (hasAny(text, "콘", "마커", "래더", "코트", "셔틀콕", "트랙", "신호")) equipment += "CONE_MARKER"
        if (hasAny(text, "메디신볼")) equipment += "MEDICINE_BALL"
        if (hasAny(text, "바이퍼")) equipment += "VIPR"
        if (hasAny(text, "TRX", "링")) equipment += "TRX_RING"
        if (hasAny(text, "맨몸") || equipment.isEmpty()) equipment += "BODYWEIGHT"
        return equipment.toList()
    }

    private fun forceTypeFor(name: String, category: String, text: String, pattern: String): String = when {
        hasAny(text, "스틱", "감속", "풋워크") -> "DECELERATION_DIRECT"
        pattern == "CARDIO" || category == "스포츠" -> "MIXED"
        hasAny(text, "점프", "홉", "바운드", "탄성", "포고") -> "PLYOMETRIC"
        pattern in setOf("ANTI_ROTATION_CORE", "ROTATION_CORE") -> "MOTOR_CONTROL"
        pattern in setOf("HORIZONTAL_PUSH", "VERTICAL_PUSH", "ELBOW_EXTENSION") -> "PUSH"
        pattern in setOf("HORIZONTAL_PULL", "VERTICAL_PULL", "ELBOW_FLEXION", "WRIST_GRIP") -> "PULL"
        pattern in setOf("KNEE_DOMINANT_LOWER", "HINGE_LOWER", "SINGLE_LEG_STRENGTH", "SINGLE_LEG_DECEL") -> "LOWER_BODY"
        hasAny(name, "레이즈", "컬", "익스텐션") -> "HYPERTROPHY"
        else -> "MIXED"
    }

    private fun bodyRegionFor(
        category: String,
        primary: List<String>,
        secondary: List<String>,
        text: String
    ): String {
        if (category == "유산소운동" || category == "스포츠" || hasAny(text, "전신")) return "WHOLE_BODY"
        val all = primary + secondary
        val upper = all.any { it in setOf("CHEST", "UPPER_CHEST", "BACK", "LAT", "RHOMBOID", "TRAPEZIUS", "LOWER_TRAP", "SHOULDER", "ANTERIOR_DELTOID", "LATERAL_DELTOID", "REAR_DELT", "ROTATOR_CUFF", "SCAPULAR_STABILIZERS", "BICEPS", "TRICEPS", "FOREARM", "GRIP") }
        val lower = all.any { it in setOf("QUADRICEPS", "RECTUS_FEMORIS", "HAMSTRING", "GLUTE", "GLUTE_MEDIUS", "HIP_ADDUCTOR", "CALF", "TIBIALIS") }
        val trunk = all.any { it in setOf("CORE", "DEEP_CORE", "OBLIQUE", "ERECTOR_SPINAE") }
        return when {
            upper && lower -> "WHOLE_BODY"
            lower -> "LOWER"
            upper -> "UPPER"
            trunk -> "TRUNK"
            else -> "WHOLE_BODY"
        }
    }

    private fun lateralityFor(text: String): String = when {
        hasAny(text, "원암", "한팔", "한 팔") -> "UNILATERAL_UPPER"
        hasAny(text, "원레그", "싱글 레그", "한발", "한쪽 다리") -> "UNILATERAL_LOWER"
        hasAny(text, "런지", "스플릿", "스텝업", "걷", "좌우", "앞뒤", "풋워크", "콕줍기") -> "UNILATERAL_ALTERNATING"
        hasAny(text, "편측", "한쪽") -> "UNILATERAL"
        else -> "BILATERAL"
    }

    private fun trainingRoleFor(category: String, text: String, pattern: String): String = when {
        category == "유산소운동" -> "CONDITIONING"
        category == "스포츠" -> "SKILL_DRILL"
        pattern == "MOBILITY" || hasAny(text, "모빌리티", "가동성") -> "MOBILITY"
        hasAny(text, "외회전", "월 슬라이드", "스캡", "보호") -> "PREHAB"
        hasAny(text, "풋워크", "콕줍기", "스플릿 스텝", "래더") -> "SKILL_DRILL"
        hasAny(text, "점프", "홉", "바운드", "포고", "탄성") -> "PLYOMETRIC"
        hasAny(text, "플랭크", "팔로프", "데드버그", "버드독", "안정") -> "STABILITY"
        hasAny(text, "컬", "레이즈", "트라이셉스", "익스텐션", "플라이") -> "HYPERTROPHY"
        else -> "STRENGTH"
    }

    private fun stabilityRolesFor(text: String, pattern: String): List<String> {
        val roles = linkedSetOf<String>()
        if (hasAny(text, "코어", "플랭크", "팔로프")) roles += "CORE_STABILITY"
        if (pattern == "ANTI_ROTATION_CORE" || hasAny(text, "회전", "팔로프")) roles += "ANTI_ROTATION"
        if (hasAny(text, "요추", "몸통")) roles += "TRUNK_CONTROL"
        if (hasAny(text, "골반", "둔근", "힙")) roles += "HIP_STABILITY"
        if (hasAny(text, "골반")) roles += "PELVIC_STABILITY"
        if (hasAny(text, "무릎")) roles += "KNEE_CONTROL"
        if (hasAny(text, "발목", "카프", "종아리")) roles += "ANKLE_STABILITY"
        if (hasAny(text, "견갑")) roles += "SCAPULAR_STABILITY"
        if (hasAny(text, "회전근개", "외회전")) roles += "ROTATOR_CUFF_CONTROL"
        if (hasAny(text, "착지", "스틱", "점프", "홉", "바운드")) roles += "LANDING_STABILITY"
        if (hasAny(text, "원레그", "싱글 레그", "한발", "편측")) roles += "SINGLE_LEG_STABILITY"
        return roles.toList()
    }

    private fun sportTransferDirectFor(
        name: String,
        category: String,
        text: String,
        pattern: String
    ): List<String> {
        val direct = linkedSetOf<String>()

        when (name) {
            "래터럴 바운드 투 스틱" -> {
                direct += "DECELERATION"
                direct += "LANDING_CONTROL"
                direct += "LATERAL_MOVEMENT"
                direct += "CHANGE_OF_DIRECTION"
            }
            "홉 투 스틱" -> {
                direct += "DECELERATION"
                direct += "LANDING_CONTROL"
            }
            "6코너 풋워크",
            "6코너 풋워크 최대반복 테스트" -> {
                direct += "BADMINTON_FOOTWORK"
                direct += "DECELERATION"
                direct += "CHANGE_OF_DIRECTION"
                direct += "COURT_CONDITIONING"
            }
            "배드민턴 경기 기록" -> {
                direct += "BADMINTON_FOOTWORK"
                direct += "COURT_CONDITIONING"
                direct += "REACTION"
            }
            "스플릿 스텝 리액션" -> {
                direct += "BADMINTON_FOOTWORK"
                direct += "REACTION"
            }
        }

        if (name.contains("콕줍기") || name == "멀티셔틀 풋워크") {
            direct += "BADMINTON_FOOTWORK"
            direct += "CHANGE_OF_DIRECTION"
            direct += "COURT_CONDITIONING"
        }

        return direct.toList()
    }

    private fun sportTransferSupportiveFor(
        category: String,
        text: String,
        pattern: String
    ): List<String> {
        if (category == "스포츠") return emptyList()
        val supportive = linkedSetOf<String>()
        if (hasAny(text, "감속", "런지", "스플릿", "스텝다운")) supportive += "DECELERATION_SUPPORT"
        if (hasAny(text, "착지", "점프", "홉")) supportive += "LANDING_SUPPORT"
        if (hasAny(text, "방향전환", "코사크", "측면", "사이드")) supportive += "COD_SUPPORT"
        if (hasAny(text, "배드민턴", "풋워크", "코트")) supportive += "BADMINTON_SUPPORT_LIGHT"
        if (hasAny(text, "골반", "힙", "중둔", "둔근")) supportive += "HIP_STABILITY_SUPPORT"
        if (hasAny(text, "코어", "몸통", "요추")) supportive += "TRUNK_CONTROL_SUPPORT"
        if (hasAny(text, "어깨", "견갑")) supportive += "SHOULDER_STABILITY_SUPPORT"
        if (hasAny(text, "회전근개", "외회전")) supportive += "ROTATOR_CUFF_SUPPORT"
        if (hasAny(text, "그립", "전완", "손목")) supportive += "GRIP_FOREARM_SUPPORT"
        if (hasAny(text, "발목", "카프", "종아리", "포고")) supportive += "ANKLE_SSC_SUPPORT"
        if (pattern == "HINGE_LOWER") supportive += "POSTERIOR_CHAIN_CAPACITY"
        if (pattern == "SINGLE_LEG_STRENGTH") supportive += "SINGLE_LEG_STRENGTH_SUPPORT"
        return supportive.toList()
    }

    private fun accessoryRolesFor(text: String, pattern: String): List<String> {
        val roles = linkedSetOf<String>()
        if (hasAny(text, "어깨", "숄더", "견갑", "레이즈")) roles += "SHOULDER_ACCESSORY"
        if (hasAny(text, "래터럴 레이즈", "측면삼각")) roles += "SHOULDER_ISOLATION"
        if (hasAny(text, "팔", "이두", "삼두", "전완", "컬")) roles += "ARM_ACCESSORY"
        if (pattern == "ELBOW_FLEXION") roles += "BICEPS_ACCESSORY"
        if (pattern == "ELBOW_EXTENSION") roles += "TRICEPS_ACCESSORY"
        if (hasAny(text, "트라이셉스", "삼두")) roles += "TRICEPS_ISOLATION"
        if (hasAny(text, "가슴", "대흉", "체스트", "플라이")) roles += "CHEST_ACCESSORY"
        if (hasAny(text, "등", "광배", "로우", "풀다운")) roles += "BACK_ACCESSORY"
        if (hasAny(text, "레그", "스쿼트", "런지", "대퇴", "종아리", "카프")) roles += "LEG_ACCESSORY"
        if (hasAny(text, "햄스트링", "둔근", "후면사슬", "RDL")) roles += "POSTERIOR_CHAIN_ACCESSORY"
        if (roles.isNotEmpty()) roles += "HYPERTROPHY_ACCESSORY"
        return roles.toList()
    }

    private fun loadProfileFor(text: String, forceType: String, trainingRole: String): String = when {
        hasAny(text, "데드리프트", "스쿼트", "바벨") -> "HIGH_AXIAL_LOAD"
        hasAny(text, "RDL", "굿모닝", "허리", "척추") -> "LUMBAR_STRESS_HIGH"
        forceType == "PLYOMETRIC" || trainingRole == "PLYOMETRIC" -> "PLYOMETRIC_JUMP"
        trainingRole in setOf("PREHAB", "MOBILITY", "RECOVERY") -> "LOW_LOAD"
        hasAny(text, "어깨", "숄더", "오버헤드") -> "SHOULDER_STRESS_LOW"
        hasAny(text, "원레그", "싱글 레그", "한발", "균형") -> "SINGLE_LEG_BALANCE_DEMAND"
        else -> "MODERATE_LOAD"
    }

    private fun metadataConfidenceFor(name: String, direct: List<String>): String {
        val reviewedDirectRows = setOf(
            "래터럴 바운드 투 스틱",
            "홉 투 스틱",
            "6코너 풋워크",
            "6코너 풋워크 최대반복 테스트",
            "배드민턴 경기 기록",
            "스플릿 스텝 리액션"
        )
        return if (name in reviewedDirectRows && direct.isNotEmpty()) "SEED_REVIEWED" else "PROFILE_INFERRED"
    }

    private fun familyIdFor(name: String, category: String): String {
        val text = name.lowercase(Locale.ROOT)
        return when {
            name.contains("스쿼트") -> "squat"
            name.contains("데드리프트") || text.contains("rdl") -> "deadlift"
            name.contains("벤치") || name.contains("체스트") -> "bench_press"
            name.contains("풀업") || name.contains("친업") || name.contains("풀다운") -> "vertical_pull"
            name.contains("로우") -> "row"
            name.contains("런지") || name.contains("스플릿") -> "lunge"
            name.contains("풋워크") || name.contains("콕줍기") -> "badminton_footwork"
            category == "유산소운동" -> "cardio"
            category == "스포츠" -> "sport"
            else -> stableKeyFor(name)
        }
    }

    private fun stableKeyFor(name: String): String =
        name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9가-힣]+"), "_")
            .trim('_')

    private fun validateCatalog(exercises: List<Exercise>) {
        val requiredNames = setOf(
            "스쿼트",
            "벤치프레스",
            "풀업",
            "데드리프트",
            "런지",
            "원레그 RDL",
            "래터럴 바운드 투 스틱",
            "홉 투 스틱",
            "6코너 풋워크",
            "배드민턴 경기 기록"
        )
        val requiredCategories = setOf("근력운동", "기능성운동", "유산소운동", "스포츠")
        val engineFields = exercises.flatMap { exercise ->
            listOf(
                exercise.familyRole,
                exercise.movementPattern,
                exercise.movementCategory,
                exercise.primaryMuscles,
                exercise.secondaryMuscles,
                exercise.equipmentTags,
                exercise.equipment,
                exercise.compoundType,
                exercise.forceType,
                exercise.bodyRegion,
                exercise.plane,
                exercise.laterality,
                exercise.axialLoadLevel,
                exercise.trainingRole,
                exercise.stabilityRoles,
                exercise.sportTransferDirect,
                exercise.sportTransferSupportive,
                exercise.badmintonTransferRoles,
                exercise.fatigueCategories,
                exercise.adaptiveBaselineGroups,
                exercise.accessoryRoles,
                exercise.loadProfile,
                exercise.recoveryDecayProfile,
                exercise.progressMetricType,
                exercise.strengthProgressionGroup,
                exercise.hypertrophyVolumeGroup,
                exercise.mainLiftGroup,
                exercise.accessoryContributionGroup,
                exercise.badmintonTransferStrength,
                exercise.courtMovementTypes,
                exercise.badmintonSkillTargets,
                exercise.jointStressTags,
                exercise.stabilityDemandLevel,
                exercise.mobilityDemandLevel,
                exercise.balanceContributionTags,
                exercise.analysisEligibility,
                exercise.metadataConfidence
            )
        }

        require(exercises.size >= 200) { "Seed catalog must include the full 200+ exercise catalog." }
        require(exercises.map { it.stableKey }.distinct().size == exercises.size) {
            "Seed catalog contains duplicate stable keys."
        }
        require(exercises.map { it.name }.containsAll(requiredNames)) {
            "Seed catalog is missing required Phase 1 exercises."
        }
        require(exercises.map { it.category }.toSet().containsAll(requiredCategories)) {
            "Seed catalog must include strength, functional, cardio, and sport categories."
        }
        require(engineFields.none { it.contains(Regex("[가-힣]")) }) {
            "Engine-facing metadata must use canonical English taxonomy tokens only."
        }
    }

    private fun hasAny(text: String, vararg parts: String): Boolean =
        parts.any { text.contains(it, ignoreCase = true) }

    private fun String.splitSeedTokens(): Set<String> =
        split('|', ',', '/', ';')
            .map { value -> value.trim().uppercase(Locale.ROOT) }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()

    private fun String.toBooleanFlagOrNull(): Boolean? = when (trim()) {
        "1", "true", "TRUE" -> true
        "0", "false", "FALSE" -> false
        else -> null
    }
}
