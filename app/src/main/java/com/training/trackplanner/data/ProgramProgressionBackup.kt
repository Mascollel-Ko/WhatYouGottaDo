package com.training.trackplanner.data

import org.json.JSONObject

data class ProgressionBackupRow(val type: String, val payload: String)

internal object ProgramProgressionBackup {
    const val CAPABILITY = "PROGRAM_EXECUTION_V1"
    val types = setOf("execution_track", "execution_item", "execution_application", "execution_link", "execution_prescription", "execution_suggestion")

    suspend fun export(db: TrainingDatabase): List<ProgressionBackupRow> {
        val dao = db.programProgressionDao()
        val entries = db.workoutDao().allEntriesWithSets().associateBy { it.entry.id }
        val programs = db.programDao().allPrograms().associateBy { it.id }
        val items = db.programDao().allProgramItems().associateBy { it.id }
        fun source(id: Long): String? = entries[id]?.entry?.backupSourceId
        return buildList {
            fun row(type: String, json: JSONObject) { add(ProgressionBackupRow(type, json.put("wireVersion", 1).toString())) }
            dao.tracks().sortedBy { it.id }.forEach { row("execution_track", ProgramProgressionWireCodec.encode(it)) }
            dao.items().sortedBy { it.logicalItemId }.forEach { binding ->
                val item = items[binding.programItemId] ?: error("Dangling progression item")
                row("execution_item", ProgramProgressionWireCodec.encode(binding)
                    .put("ownerProgramStableKey", programs.getValue(item.programId).stableKey)
                    .put("week", item.weekNumber).put("day", item.dayOfWeek).put("order", item.orderIndex))
            }
            dao.applications().sortedBy { it.id }.forEach { row("execution_application", ProgramProgressionWireCodec.encode(it)) }
            dao.links().sortedBy { it.entryId }.forEach {
                row("execution_link", ProgramProgressionWireCodec.encode(it).put("entrySourceId", requireNotNull(source(it.entryId))))
            }
            dao.prescriptions().sortedWith(compareBy({ it.entryId }, { it.setIndex })).forEach {
                row("execution_prescription", ProgramProgressionWireCodec.encode(it).put("entrySourceId", requireNotNull(source(it.entryId))))
            }
            dao.suggestions().sortedBy { it.id }.forEach {
                row("execution_suggestion", ProgramProgressionWireCodec.encode(it)
                    .put("sourceSourceId", source(it.sourceEntryId) ?: JSONObject.NULL)
                    .put("targetSourceId", source(it.targetEntryId) ?: JSONObject.NULL))
            }
        }
    }

    fun validate(rows: List<ProgressionBackupRow>, exercisesBySource: Map<String, String>? = null) {
        val tracks = mutableMapOf<String, ProgramProgressionTrack>()
        val applications = mutableSetOf<String>()
        val identities = mutableSetOf<String>()
        val links = mutableMapOf<String, ProgramWorkoutLink>()
        rows.forEach { row ->
            require(row.type in types)
            val json = JSONObject(row.payload)
            require(json.getInt("wireVersion") == 1)
            val identity = when (row.type) {
                "execution_track" -> ProgramProgressionWireCodec.programProgressionTrack(json).also { it.rule.validate(); tracks[it.id] = it }.id
                "execution_item" -> ProgramProgressionWireCodec.programProgressionItem(json).logicalItemId
                "execution_application" -> ProgramProgressionWireCodec.programApplication(json).also { applications += it.id }.id
                "execution_link" -> ProgramProgressionWireCodec.programWorkoutLink(json).let {
                    it.rule.validate()
                    val source = json.getString("entrySourceId")
                    require(source.isNotBlank()); links[source] = it
                    source
                }
                "execution_prescription" -> ProgramProgressionWireCodec.programPrescriptionSet(json).let {
                    require(it.setIndex > 0 && listOf(it.originalKg, it.plannedKg).all { kg -> kg.isFinite() && kg >= 0 })
                    require(it.originalReps >= 0 && it.plannedReps >= 0 && it.originalSeconds >= 0 && it.plannedSeconds >= 0)
                    require(it.plannedSetIndex == null || it.plannedSetIndex > 0)
                    require(json.getString("entrySourceId").isNotBlank()); "${json.getString("entrySourceId")}:${it.setIndex}"
                }
                else -> ProgramProgressionWireCodec.progressionSuggestion(json).also {
                    it.rule.validate()
                    require(listOfNotNull(it.previousActualKg, it.currentPlanKg, it.suggestedKg, it.resolvedKg).all { kg -> kg.isFinite() && kg > 0 })
                    require(it.judgmentRpe == null || it.judgmentRpe.isFinite() && it.judgmentRpe in 1.0..10.0)
                }.id
            }
            require(identities.add("${row.type}:$identity")) { "Duplicate execution graph identity" }
        }
        val sequences = mutableSetOf<String>()
        rows.forEach { row ->
            val json = JSONObject(row.payload)
            when (row.type) {
                "execution_item" -> ProgramProgressionWireCodec.programProgressionItem(json).let {
                    require(tracks[it.trackId]?.exerciseStableKey == it.signature.exerciseStableKey)
                    require(tracks[it.trackId]?.programStableKey == json.getString("ownerProgramStableKey"))
                }
                "execution_link" -> ProgramProgressionWireCodec.programWorkoutLink(json).let {
                    require(it.applicationId in applications && (it.trackId == "untracked" || it.trackId in tracks))
                    if (exercisesBySource != null) {
                        val key = requireNotNull(exercisesBySource[json.getString("entrySourceId")]) { "Unresolved execution workout source" }
                        require(it.trackId == "untracked" || tracks[it.trackId]?.exerciseStableKey == key)
                    }
                    require(sequences.add("${it.applicationId}|${it.trackId}|${it.sequence}"))
                }
                "execution_prescription" -> require(json.getString("entrySourceId") in links) { "Prescription without program link" }
                "execution_suggestion" -> ProgramProgressionWireCodec.progressionSuggestion(json).let {
                    require(it.applicationId in applications && it.trackId in tracks)
                }
            }
        }
    }

    /** Caller owns the restore transaction. Existing explicit decisions win on append. */
    suspend fun restore(db: TrainingDatabase, rows: List<ProgressionBackupRow>, entriesBySource: Map<String, Long>) {
        validate(rows)
        val dao = db.programProgressionDao()
        val programs = db.programDao().allPrograms().associateBy { it.stableKey }
        val items = db.programDao().allProgramItems()
        val existingApplications = dao.applications().mapTo(mutableSetOf()) { it.id }
        val existingSuggestions = dao.suggestions().associateBy { it.id }
        val existingLinks = dao.links().associateBy { it.entryId }
        fun entry(json: JSONObject, key: String): Long? = if (json.isNull(key)) null else entriesBySource[json.getString(key)]
        for (type in listOf("execution_track", "execution_application", "execution_item", "execution_link", "execution_prescription", "execution_suggestion")) {
            for (row in rows.filter { it.type == type }) {
                val json = JSONObject(row.payload)
                when (type) {
                    "execution_track" -> dao.putTrack(ProgramProgressionWireCodec.programProgressionTrack(json))
                    "execution_application" -> ProgramProgressionWireCodec.programApplication(json).let { if (it.id !in existingApplications) dao.putApplication(it) }
                    "execution_item" -> {
                        val binding = ProgramProgressionWireCodec.programProgressionItem(json)
                        val program = programs[json.getString("ownerProgramStableKey")]
                        val item = items.singleOrNull { it.programId == program?.id && it.weekNumber == json.getInt("week") && it.dayOfWeek == json.getInt("day") && it.orderIndex == json.getInt("order") && it.exerciseStableKey == binding.signature.exerciseStableKey }
                        if (item != null) dao.putItem(binding.copy(programItemId = item.id))
                    }
                    "execution_link" -> {
                        val id = requireNotNull(entry(json, "entrySourceId")) { "Unresolved program workout source" }
                        if (id !in existingLinks) dao.putLink(ProgramProgressionWireCodec.programWorkoutLink(json).copy(entryId = id))
                    }
                    "execution_prescription" -> {
                        val id = requireNotNull(entry(json, "entrySourceId"))
                        if (id !in existingLinks) dao.putPrescription(ProgramProgressionWireCodec.programPrescriptionSet(json).copy(entryId = id))
                    }
                    "execution_suggestion" -> {
                        val incoming = ProgramProgressionWireCodec.progressionSuggestion(json)
                        val suggestion = existingSuggestions[incoming.id] ?: incoming
                        val source = entry(json, "sourceSourceId")
                        val target = entry(json, "targetSourceId")
                        dao.putSuggestion(suggestion.copy(sourceEntryId = source ?: 0, targetEntryId = target ?: 0,
                            resolution = if ((source == null || target == null) && suggestion.resolution == ProgressionResolution.PENDING) ProgressionResolution.STALE else suggestion.resolution))
                    }
                }
            }
        }
    }
}
