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
        require(csv.matchesSha256(expectedSha256)) {
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
        require(metadata.all { it.appCueProfile in APP_CUE_PROFILES }) {
            "Canonical metadata contains an unsupported appCueProfile."
        }
        return RuntimeExerciseMetadataCatalog.of(metadata)
    }

    companion object {
        const val EXPECTED_ROW_COUNT = 235
        const val ASSET_DIRECTORY = "metadata"
        const val CANONICAL_ASSET_PATH =
            "$ASSET_DIRECTORY/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
        const val MANIFEST_ASSET_PATH =
            "$ASSET_DIRECTORY/canonical_exercise_metadata_manifest.json"
        val APP_CUE_PROFILES = setOf("NONE", "RANDOM_BEEP_CUE")

        internal fun parseCanonicalCsv(csv: String): List<RuntimeExerciseMetadata> =
            ExerciseMetadataAdapter.fromCsv(csv)

        internal fun assetHashMatches(csv: String, expectedSha256: String): Boolean =
            csv.matchesSha256(expectedSha256)
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

private fun String.matchesSha256(expectedSha256: String): Boolean {
    val lf = replace("\r\n", "\n").replace('\r', '\n')
    return sequenceOf(this, lf, lf.replace("\n", "\r\n"))
        .map(String::sha256)
        .any { it == expectedSha256 }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
