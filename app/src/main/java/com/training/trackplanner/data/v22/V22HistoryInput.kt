package com.training.trackplanner.data.v22

import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import java.io.PushbackReader
import java.io.Reader
import java.time.LocalDate

object V22HistoryInput {
    private val categories = setOf("근력운동", "기능성운동")

    fun fromWorkoutHistory(entries: List<WorkoutEntryWithSets>): List<V22HistoryRow> =
        entries.flatMap { row ->
            val entry = row.entry
            if (entry.category !in categories || row.sets.none { it.confirmed }) return@flatMap emptyList()
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@flatMap emptyList()
            row.sets
                .filter { it.confirmed }
                .map { set ->
                    V22HistoryRow(
                        date = date,
                        exerciseName = entry.exerciseName,
                        stableKey = entry.exerciseStableKey.ifBlank { entry.exerciseName },
                        category = entry.category,
                        setIndex = set.setIndex,
                        reps = set.reps.toDouble().takeIf { it > 0.0 },
                        weightKg = set.weightKg.takeIf { it.isFinite() },
                        seconds = set.seconds.toDouble().takeIf { it > 0.0 },
                        rpe = (set.rpe ?: entry.rpe)?.takeIf(Double::isFinite)
                    )
                }
        }

    fun badmintonFromWorkoutHistory(
        entries: List<WorkoutEntryWithSets>,
        exercises: Map<String, Exercise>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        runtimeMetadata: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
    ): List<V22BadmintonHistorySession> {
        val calculator = BadmintonPracticeLoadCalculator(runtimeMetadata)
        val generic = calculator.entryContributions(entries, exercises).associateBy { it.entryId }
        val directKeys = badmintonCatalog.allRelations().filter {
            it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT
        }.mapTo(linkedSetOf()) { it.exerciseStableKey }
        val components = entries.mapNotNull { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
                ?: return@mapNotNull null
            val confirmed = record.sets.filter { it.confirmed }
            val duration = confirmed.sumOf { it.seconds.coerceAtLeast(0) } / 60.0
            if (duration <= 0.0) return@mapNotNull null
            val key = record.entry.exerciseStableKey
            val exercise = exercises[key] ?: return@mapNotNull null
            val activityKind = runtimeMetadata.resolve(exercise)?.activityKind
                ?.takeIf(String::isNotBlank) ?: exercise.activityKind
            val isGeneric = BadmintonPracticeCatalog.admits(key, activityKind)
            if (!isGeneric && (key !in directKeys || record.entry.category == "근력운동")) {
                return@mapNotNull null
            }
            val rpe = confirmed.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()
                ?: record.entry.rpe
            Triple(date, record.entry.sessionStableKey, V22BadmintonHistoryComponent(
                entryId = record.entry.id,
                stableKey = key,
                durationMinutes = duration,
                practiceLoad = generic[record.entry.id]?.practiceLoad
                    ?: calculator.practiceLoad(duration, rpe),
                rpe = rpe
            ))
        }
        return components.groupBy { it.first to it.second }.map { (identity, rows) ->
            val values = rows.map { it.third }
            val genericValues = values.filter { it.stableKey in BadmintonPracticeCatalog.stableKeys }
            val totalLoad = if (genericValues.isNotEmpty()) genericValues.maxOf { it.practiceLoad }
                else values.sumOf { it.practiceLoad }
            val totalDuration = if (genericValues.isNotEmpty()) genericValues.maxOf { it.durationMinutes }
                else values.sumOf { it.durationMinutes }
            V22BadmintonHistorySession(
                date = identity.first,
                sessionStableKey = identity.second,
                practiceLoad = totalLoad,
                durationMinutes = totalDuration,
                components = values.sortedBy(V22BadmintonHistoryComponent::entryId)
            )
        }.sortedWith(compareBy(V22BadmintonHistorySession::date, V22BadmintonHistorySession::sessionStableKey))
    }

    /**
     * Preserve actual completed sport-drill set exposures for B-Coach SPORT Need.
     * Repetitions are not converted into fictional minutes or recovery load.
     * Functional-category drill sets remain on the existing general history path.
     */
    fun sportDrillHistory(
        entries: List<WorkoutEntryWithSets>,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog
    ): List<V22HistoryRow> {
        val eligibleKeys = badmintonCatalog.allRelations()
            .filter { it.transferLevel.coefficient > 0.0 }
            .mapTo(linkedSetOf()) { it.exerciseStableKey } -
            BadmintonPracticeCatalog.stableKeys
        return entries.flatMap { record ->
            val entry = record.entry
            if (entry.category != "스포츠" || entry.exerciseStableKey !in eligibleKeys) {
                return@flatMap emptyList()
            }
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                ?: return@flatMap emptyList()
            record.sets.filter { set ->
                set.confirmed && (set.reps > 0 || set.seconds > 0)
            }.map { set ->
                V22HistoryRow(
                    date = date,
                    exerciseName = entry.exerciseName,
                    stableKey = entry.exerciseStableKey,
                    category = entry.category,
                    setIndex = set.setIndex,
                    reps = set.reps.takeIf { it > 0 }?.toDouble(),
                    weightKg = set.weightKg.takeIf { it.isFinite() && it > 0.0 },
                    seconds = set.seconds.takeIf { it > 0 }?.toDouble(),
                    rpe = (set.rpe ?: entry.rpe)?.takeIf(Double::isFinite)
                )
            }
        }
    }

    /** Reads the exact backup shape used by the Python reference, including its leading manifest record. */
    fun fromBackupCsv(reader: Reader): List<V22HistoryRow> {
        val records = CsvRecords(reader).iterator()
        require(records.hasNext()) { "Backup CSV is empty." }
        records.next() // #WGTD_BACKUP_MANIFEST
        require(records.hasNext()) { "Backup CSV header is missing." }
        val header = records.next().mapIndexed { index, value -> value.removePrefix("\uFEFF") to index }.toMap()
        fun index(name: String): Int = requireNotNull(header[name]) { "Backup CSV column is missing: $name" }
        val rowType = index("row_type")
        val date = index("date")
        val name = index("exercise_name")
        val category = index("category")
        val confirmed = index("confirmed")
        val setIndex = index("set_index")
        val setConfirmed = index("set_confirmed")
        val reps = index("reps")
        val weight = index("weight_kg")
        val seconds = index("seconds")
        val stableKey = index("stable_key")
        val rpe = index("rpe")
        val result = mutableListOf<V22HistoryRow>()
        while (records.hasNext()) {
            val fields = records.next()
            fun value(i: Int): String = fields.getOrElse(i) { "" }
            if (value(rowType) != "set" || value(confirmed) != "1" || value(setConfirmed) != "1" || value(category) !in categories) continue
            val parsedDate = runCatching { LocalDate.parse(value(date)) }.getOrNull() ?: continue
            val exerciseName = value(name)
            result += V22HistoryRow(
                date = parsedDate,
                exerciseName = exerciseName,
                stableKey = value(stableKey).ifBlank { exerciseName },
                category = value(category),
                setIndex = value(setIndex).ifBlank { "0" }.toInt(),
                reps = numeric(value(reps)),
                weightKg = numeric(value(weight)),
                seconds = numeric(value(seconds)),
                rpe = numeric(value(rpe))
            )
        }
        return result
    }

    private fun numeric(value: String): Double? = value.toDoubleOrNull()?.takeIf(Double::isFinite)

    private class CsvRecords(reader: Reader) : Iterable<List<String>> {
        private val input = PushbackReader(reader, 1)
        private var consumed = false

        override fun iterator(): Iterator<List<String>> {
            check(!consumed) { "CSV records can only be consumed once." }
            consumed = true
            return object : Iterator<List<String>> {
                private var nextRecord: List<String>? = readRecord()
                override fun hasNext(): Boolean = nextRecord != null
                override fun next(): List<String> = nextRecord?.also { nextRecord = readRecord() }
                    ?: throw NoSuchElementException()

                private fun readRecord(): List<String>? {
                    val fields = mutableListOf<String>()
                    val field = StringBuilder()
                    var quoted = false
                    var sawAny = false
                    while (true) {
                        val code = input.read()
                        if (code == -1) {
                            if (!sawAny && fields.isEmpty() && field.isEmpty()) return null
                            fields += field.toString()
                            return fields
                        }
                        sawAny = true
                        val ch = code.toChar()
                        if (quoted) {
                            if (ch == '"') {
                                val peek = input.read()
                                if (peek == '"'.code) field.append('"')
                                else {
                                    quoted = false
                                    if (peek != -1) input.unread(peek)
                                }
                            } else field.append(ch)
                        } else when (ch) {
                            '"' -> quoted = true
                            ',' -> {
                                fields += field.toString()
                                field.setLength(0)
                            }
                            '\n' -> {
                                fields += field.toString()
                                return fields
                            }
                            '\r' -> {
                                val peek = input.read()
                                if (peek != '\n'.code && peek != -1) input.unread(peek)
                                fields += field.toString()
                                return fields
                            }
                            else -> field.append(ch)
                        }
                    }
                }
            }
        }
    }
}
