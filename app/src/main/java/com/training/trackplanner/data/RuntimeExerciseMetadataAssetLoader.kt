package com.training.trackplanner.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

class RuntimeExerciseMetadataAssetLoader(
    private val context: Context
) {
    fun load(): RuntimeExerciseMetadataCatalog {
        val manifestText = context.assets.open(MANIFEST_ASSET_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val manifest = JSONObject(manifestText)
        val assetName = manifest.getString("asset")
        val expectedRows = manifest.getInt("rowCount")
        val expectedSha256 = manifest.getString("assetSha256")
        require(expectedRows == EXPECTED_ROW_COUNT) {
            "Unexpected canonical metadata manifest row count: $expectedRows"
        }

        val csv = context.assets.open("$ASSET_DIRECTORY/$assetName")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        require(csv.sha256() == expectedSha256) {
            "Canonical metadata asset hash does not match its manifest."
        }
        val metadata = parseCanonicalCsv(csv)
        require(metadata.size == expectedRows) {
            "Expected $expectedRows canonical metadata rows, found ${metadata.size}."
        }
        require(metadata.map { it.stableKey.lowercase(Locale.ROOT) }.distinct().size == metadata.size) {
            "Canonical metadata contains blank or duplicate stableKey values."
        }
        require(metadata.none { it.stableKey.isBlank() }) {
            "Canonical metadata contains a blank stableKey."
        }
        return RuntimeExerciseMetadataCatalog.of(metadata)
    }

    companion object {
        const val EXPECTED_ROW_COUNT = 215
        const val ASSET_DIRECTORY = "metadata"
        const val CANONICAL_ASSET_PATH =
            "$ASSET_DIRECTORY/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
        const val MANIFEST_ASSET_PATH =
            "$ASSET_DIRECTORY/canonical_exercise_metadata_manifest.json"

        internal fun parseCanonicalCsv(csv: String): List<RuntimeExerciseMetadata> =
            ExerciseMetadataAdapter.fromCsv(csv)
    }
}

object RuntimeExerciseMetadataCatalogProvider {
    @Volatile
    private var cached: RuntimeExerciseMetadataCatalog? = null

    fun get(context: Context): RuntimeExerciseMetadataCatalog =
        cached ?: synchronized(this) {
            cached ?: runCatching {
                RuntimeExerciseMetadataAssetLoader(context.applicationContext).load()
            }.onFailure { error ->
                Log.e("CanonicalMetadata", "Failed to load Pass 3.1 runtime metadata.", error)
            }.getOrDefault(RuntimeExerciseMetadataCatalog.EMPTY).also { loaded ->
                cached = loaded
            }
        }

    internal fun clearForTest() {
        cached = null
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
