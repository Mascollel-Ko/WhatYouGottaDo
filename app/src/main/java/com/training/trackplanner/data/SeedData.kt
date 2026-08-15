package com.training.trackplanner.data

import android.content.Context
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
    val exerciseStableKey: String,
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

    fun exercises(context: Context): List<Exercise> =
        CanonicalExerciseMetadataRepositoryProvider.get(context).exercises()

    internal fun exactExerciseMetadataByStableKey(context: Context): Map<String, Exercise> =
        CanonicalExerciseMetadataRepositoryProvider.get(context).exercises(includeHistory = true)
            .associateBy { exercise -> exercise.stableKey.normalizedSeedKey() }

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

    private fun programItemFromCsv(row: Map<String, String>): ProgramItemSeed =
        ProgramItemSeed(
            weekNumber = row.value("week_number").toIntOrNull() ?: 1,
            dayOfWeek = row.value("day_of_week").toIntOrNull() ?: 1,
            orderIndex = row.value("order_index").toIntOrNull() ?: 1,
            exerciseStableKey = row.value("stable_key"),
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

    internal fun parseCsvLine(line: String): List<String> {
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

    private fun String.normalizedSeedKey(): String = trim().lowercase(Locale.ROOT)
}
