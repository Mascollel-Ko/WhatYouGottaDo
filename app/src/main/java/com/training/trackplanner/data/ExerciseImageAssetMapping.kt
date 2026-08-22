package com.training.trackplanner.data

import android.content.Context

internal object ExerciseImageAssetMapping {
    @Volatile
    private var cached: Map<String, String>? = null

    fun resolve(context: Context, stableKey: String, fallback: String): String =
        mappings(context)[stableKey] ?: fallback

    internal fun mappings(context: Context): Map<String, String> = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    private fun load(context: Context): Map<String, String> {
        val rows = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .filter(String::isNotBlank)
                .map(SeedData::parseCsvLine)
                .toList()
        }
        require(rows.isNotEmpty()) { "Empty exercise image mapping asset." }
        val header = rows.first().map { it.trim().trimStart('\uFEFF') }
        val stableKeyIndex = header.indexOf("stable_key")
        val imageIndex = header.indexOf("image_asset_name")
        require(stableKeyIndex >= 0 && imageIndex >= 0) { "Invalid exercise image mapping header." }

        return rows.drop(1).associate { values ->
            require(values.size == header.size) { "Malformed exercise image mapping row." }
            val stableKey = values[stableKeyIndex].trim()
            val imageAssetName = values[imageIndex].trim()
            require(stableKey.isNotBlank()) { "Blank exercise image stableKey." }
            require(imageAssetName.startsWith("exercise_images/")) {
                "Exercise image must remain inside the exercise_images asset directory: $imageAssetName"
            }
            stableKey to imageAssetName
        }.also { mappings ->
            require(mappings.size == rows.size - 1) { "Duplicate exercise image stableKey mapping." }
        }
    }

    private const val ASSET_NAME = "exercise_image_mapping.csv"
}
