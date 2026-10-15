package com.training.trackplanner.data.v22

import com.training.trackplanner.data.WorkoutEntryWithSets
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
