package com.training.trackplanner.data

import android.content.Context
import java.security.MessageDigest

class RuntimeExerciseMetadataAssetLoader(
    private val context: Context
) {
    fun load(): RuntimeExerciseMetadataCatalog =
        CanonicalExerciseMetadataRepositoryProvider.get(context).runtimeMetadataCatalog()

    companion object {
        const val EXPECTED_ROW_COUNT = CanonicalExerciseMetadataRepository.EXPECTED_IDENTITY_ROWS
        const val ASSET_DIRECTORY = CanonicalExerciseMetadataRepository.ASSET_DIRECTORY
        const val CANONICAL_ASSET_PATH =
            "$ASSET_DIRECTORY/runtime_metadata.csv"
        const val MANIFEST_ASSET_PATH =
            "$ASSET_DIRECTORY/${CanonicalExerciseMetadataRepository.MANIFEST_FILE}"
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
            cached ?: RuntimeExerciseMetadataAssetLoader(context.applicationContext).load().also { loaded ->
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
