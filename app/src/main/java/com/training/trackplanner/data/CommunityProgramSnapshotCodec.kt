package com.training.trackplanner.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Community snapshot boundary.  Only public program semantics cross it; Room
 * ids, workout history and Cloud Backup lineage are deliberately absent.
 */
internal object CommunityProgramSnapshotCodec {
    suspend fun export(db: TrainingDatabase, programId: Long): JSONObject = db.withTransaction {
        val program = db.programDao().findProgram(programId) ?: error("PROGRAM_NOT_FOUND")
        val items = db.programDao().itemsForProgram(programId)
        val setsByItem = db.programDao().programItemSetsForProgram(programId).groupBy { it.programItemId }
        val itemIndexById = items.withIndex().associate { it.value.id to it.index }
        val itemArray = JSONArray()
        items.forEach { item ->
            itemArray.put(JSONObject()
                .put("weekNumber", item.weekNumber)
                .put("dayOfWeek", item.dayOfWeek)
                .put("orderIndex", item.orderIndex)
                .put("exerciseStableKey", item.exerciseStableKey)
                .put("exerciseName", item.exerciseName)
                .put("category", item.category)
                .put("restSeconds", item.restSeconds)
                .put("prescription", item.prescription)
                .put("setCount", item.setCount)
                .put("reps", item.reps)
                .put("weightKg", item.weightKg)
                .put("seconds", item.seconds)
                .putNullable("trainingSlot", item.trainingSlot)
                .putNullable("dayIntensity", item.dayIntensity)
                .putNullable("weightSource", item.weightSource)
                .put("sets", JSONArray().also { array ->
                    setsByItem[item.id].orEmpty().sortedBy(TrainingProgramItemSet::setIndex).forEach { set ->
                        array.put(JSONObject()
                            .put("setIndex", set.setIndex)
                            .put("reps", set.reps)
                            .put("weightKg", set.weightKg)
                            .put("seconds", set.seconds))
                    }
                }))
        }
        val tracks = db.programProgressionDao().tracks()
            .filter { it.programStableKey == program.stableKey }
            .sortedBy { it.id }
        val trackIndex = tracks.withIndex().associate { it.value.id to it.index }
        val trackArray = JSONArray()
        tracks.forEach { track ->
            trackArray.put(JSONObject()
                .put("exerciseStableKey", track.exerciseStableKey)
                .put("label", track.label)
                .put("role", track.role.name)
                .put("roleOverride", track.roleOverride.name)
                .put("mode", track.mode.name)
                .put("basePolicy", track.basePolicy.name)
                .putNullable("anchorSetIndex", track.anchorSetIndex)
                .put("needsReview", track.needsReview)
                .put("rule", ProgramProgressionWireCodec.encode(track.rule)))
        }
        val bindingArray = JSONArray()
        db.programProgressionDao().items()
            .filter { it.programItemId in itemIndexById && it.trackId in trackIndex }
            .sortedBy { it.programItemId }
            .forEach { binding ->
                bindingArray.put(JSONObject()
                    .put("itemIndex", itemIndexById.getValue(binding.programItemId))
                    .put("trackIndex", trackIndex.getValue(binding.trackId))
                    .put("linkMode", binding.linkMode.name)
                    .put("signature", ProgramProgressionWireCodec.encode(binding.signature)))
            }
        JSONObject()
            .put("schemaVersion", 1)
            .put("program", JSONObject()
                .put("name", program.name)
                .put("durationDays", program.durationDays)
                .put("goal", program.goal)
                .put("weeklyTrainingDays", program.weeklyTrainingDays)
                .put("sessionMinutes", program.sessionMinutes)
                .put("availableEquipment", program.availableEquipment)
                .put("periodizationType", program.periodizationType))
            .put("items", itemArray)
            .put("progression", JSONObject().put("tracks", trackArray).put("bindings", bindingArray))
    }

    suspend fun import(db: TrainingDatabase, snapshot: JSONObject, allowDuplicate: Boolean = false): Long {
        val normalized = validateSnapshot(snapshot)
        return db.withTransaction {
            val programObject = normalized.getJSONObject("program")
            val name = programObject.optString("name").trim().ifBlank { throw CommunityImportException("INVALID_SNAPSHOT") }
            val items = normalized.getJSONArray("items")
            if (!allowDuplicate) {
                val duplicate = db.programDao().allPrograms().firstOrNull { it.name == name }
                    ?.let { db.programDao().itemsForProgram(it.id).size == items.length() } == true
                if (duplicate) throw CommunityImportException("DUPLICATE_IMPORT_REVIEW_REQUIRED")
            }
            val stableKey = ProgramStableKeyPolicy.newUserKey()
            val programId = db.programDao().insertProgram(
                TrainingProgram(
                    stableKey = stableKey,
                    name = name,
                    durationDays = programObject.optInt("durationDays", 1).coerceAtLeast(1),
                    goal = programObject.optString("goal"),
                    weeklyTrainingDays = programObject.optInt("weeklyTrainingDays").coerceAtLeast(0),
                    sessionMinutes = programObject.optInt("sessionMinutes").coerceAtLeast(0),
                    availableEquipment = programObject.optString("availableEquipment"),
                    periodizationType = programObject.optString("periodizationType")
                )
            )
            val itemIds = mutableListOf<Long>()
            for (index in 0 until items.length()) {
                val row = items.getJSONObject(index)
                val stableExerciseKey = row.getString("exerciseStableKey").trim()
                require(stableExerciseKey.isNotBlank()) { "INVALID_SNAPSHOT" }
                if (db.exerciseDao().findByStableKey(stableExerciseKey) == null) {
                    db.exerciseDao().insertExercise(
                        Exercise(
                            stableKey = stableExerciseKey,
                            name = row.optString("exerciseName").ifBlank { stableExerciseKey },
                            category = row.optString("category"),
                            defaultRestSeconds = row.optInt("restSeconds", 60),
                            isCustom = true,
                            needsReview = true
                        )
                    )
                }
                val itemId = db.programDao().insertProgramItem(
                    TrainingProgramItem(
                        programId = programId,
                        weekNumber = row.optInt("weekNumber", 1),
                        dayOfWeek = row.optInt("dayOfWeek", 1),
                        orderIndex = row.optInt("orderIndex", index + 1),
                        exerciseStableKey = stableExerciseKey,
                        exerciseName = row.optString("exerciseName").ifBlank { stableExerciseKey },
                        category = row.optString("category"),
                        restSeconds = row.optInt("restSeconds", 60),
                        prescription = row.optString("prescription"),
                        setCount = row.optInt("setCount", 1).coerceAtLeast(1),
                        reps = row.optInt("reps"),
                        weightKg = row.optDouble("weightKg"),
                        seconds = row.optInt("seconds"),
                        trainingSlot = row.optString("trainingSlot").takeIf(String::isNotBlank),
                        dayIntensity = row.optString("dayIntensity").takeIf(String::isNotBlank),
                        weightSource = row.optString("weightSource").takeIf(String::isNotBlank)
                    )
                )
                itemIds += itemId
                val sets = row.optJSONArray("sets") ?: JSONArray()
                val rows = (0 until sets.length()).map { setIndex ->
                    val set = sets.getJSONObject(setIndex)
                    TrainingProgramItemSet(
                        programItemId = itemId,
                        setIndex = set.optInt("setIndex", setIndex + 1),
                        reps = set.optInt("reps"),
                        weightKg = set.optDouble("weightKg"),
                        seconds = set.optInt("seconds")
                    )
                }
                if (rows.isNotEmpty()) db.programDao().insertProgramItemSets(rows)
            }
            val progression = normalized.optJSONObject("progression") ?: JSONObject()
            val tracks = progression.optJSONArray("tracks") ?: JSONArray()
            val newTrackIds = mutableListOf<String>()
            for (index in 0 until tracks.length()) {
                val row = tracks.getJSONObject(index)
                val id = UUID.randomUUID().toString()
                newTrackIds += id
                db.programProgressionDao().putTrack(
                    ProgramProgressionTrack(
                        id = id,
                        programStableKey = stableKey,
                        exerciseStableKey = row.getString("exerciseStableKey"),
                        label = row.optString("label"),
                        role = enumValue(row.optString("role"), ProgressionRole.AUTO),
                        roleOverride = enumValue(row.optString("roleOverride"), ProgressionRole.AUTO),
                        mode = enumValue(row.optString("mode"), ProgressionMode.APP),
                        basePolicy = enumValue(row.optString("basePolicy"), ProgressionBase.REVIEW),
                        anchorSetIndex = row.optInt("anchorSetIndex").takeIf { row.has("anchorSetIndex") && !row.isNull("anchorSetIndex") },
                        needsReview = row.optBoolean("needsReview"),
                        rule = runCatching { ProgramProgressionWireCodec.progressionRule(row.getJSONObject("rule")) }.getOrDefault(ProgressionRule())
                    )
                )
            }
            val bindings = progression.optJSONArray("bindings") ?: JSONArray()
            for (index in 0 until bindings.length()) {
                val row = bindings.getJSONObject(index)
                val itemIndex = row.optInt("itemIndex", -1)
                val trackIndex = row.optInt("trackIndex", -1)
                if (itemIndex !in itemIds.indices || trackIndex !in newTrackIds.indices) continue
                val signature = runCatching { ProgramProgressionWireCodec.progressionSignature(row.getJSONObject("signature")) }.getOrNull() ?: continue
                db.programProgressionDao().putItem(
                    ProgramProgressionItem(
                        programItemId = itemIds[itemIndex],
                        logicalItemId = UUID.randomUUID().toString(),
                        trackId = newTrackIds[trackIndex],
                        linkMode = enumValue(row.optString("linkMode"), ProgressionLinkMode.AUTO),
                        signature = signature
                    )
                )
            }
            programId
        }
    }

    private fun validateSnapshot(snapshot: JSONObject): JSONObject {
        require(snapshot.optInt("schemaVersion", -1) == 1) { "UNSUPPORTED_SNAPSHOT_VERSION" }
        require(snapshot.has("program") && snapshot.optJSONArray("items") != null) { "INVALID_SNAPSHOT" }
        val items = snapshot.getJSONArray("items")
        require(items.length() in 1..1000) { "INVALID_SNAPSHOT" }
        for (index in 0 until items.length()) {
            val row = items.getJSONObject(index)
            require(row.optString("exerciseStableKey").isNotBlank()) { "INVALID_SNAPSHOT" }
            require(!row.has("id") && !row.has("programId")) { "PRIVATE_SNAPSHOT_FIELD" }
        }
        val progression = snapshot.optJSONObject("progression")
        progression?.optJSONArray("tracks")?.let { tracks ->
            for (index in 0 until tracks.length()) {
                val row = tracks.getJSONObject(index)
                require(!row.has("id") && !row.has("programStableKey")) { "PRIVATE_SNAPSHOT_FIELD" }
            }
        }
        progression?.optJSONArray("bindings")?.let { bindings ->
            for (index in 0 until bindings.length()) {
                val row = bindings.getJSONObject(index)
                require(!row.has("programItemId") && !row.has("trackId")) { "PRIVATE_SNAPSHOT_FIELD" }
            }
        }
        return snapshot
    }

    private fun <T : Enum<T>> enumValue(value: String, fallback: T): T =
        fallback.javaClass.enumConstants.firstOrNull { it.name == value } ?: fallback

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
}

internal class CommunityImportException(message: String) : IllegalArgumentException(message)
