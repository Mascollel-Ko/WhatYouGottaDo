package com.training.trackplanner.analysis.tissue

import android.content.Context
import org.json.JSONObject

data class TissuePriorProfile(
    val priorProfileId: String,
    val boundariesByLocalHour: Map<Int, TissuePriorBoundaries>,
    val adjustment: TissuePriorProfileAdjustment
)

data class TissuePriorRegistry(
    val schemaVersion: String,
    val protocolVersion: String,
    val generatorVersion: String,
    val deterministicOutputChecksum: String,
    val recoveryEngineFingerprint: String,
    val profileIdByLoadUnitStableKey: Map<String, String>,
    val profiles: Map<String, TissuePriorProfile>
) {
    fun profileFor(loadUnitStableKey: String): TissuePriorProfile? =
        profileIdByLoadUnitStableKey[loadUnitStableKey]?.let(profiles::get)
}

object TissuePriorRegistryParser {
    const val SCHEMA_VERSION = "1.0.0"
    const val PROTOCOL_VERSION = "RCV-ALL-0.6|RCV-EXPOSURE-1.1"
    const val EXPECTED_LOAD_UNIT_COUNT = 77
    const val EXPECTED_PROFILE_COUNT = 13
    private val stableKeyPattern = Regex("lu_[0-9a-f]{10}")

    fun parse(json: String): TissuePriorRegistry {
        val root = JSONObject(json)
        require(root.getString("schemaVersion") == SCHEMA_VERSION)
        require(root.getString("protocolVersion") == PROTOCOL_VERSION)
        val declaredHours = root.getJSONArray("evaluationTimeBuckets").intValues()
        require(declaredHours == (0..23).toList())
        root.getJSONArray("coefficientSources").stringValues().forEach(TissuePriorCoefficientSource::valueOf)

        val adjustmentPolicy = root.getJSONObject("adjustmentPolicy")
        val normalClamp = adjustmentPolicy.getJSONArray("normalMultiplierClamp").doubleValues()
        val hardClamp = adjustmentPolicy.getJSONArray("hardMultiplierClamp").doubleValues()
        require(normalClamp.size == 2 && hardClamp.size == 2)

        val profiles = linkedMapOf<String, TissuePriorProfile>()
        root.getJSONArray("profiles").objects().forEach { value ->
            val id = value.getString("priorProfileId")
            require(id !in profiles)
            val boundaries = value.getJSONArray("evaluationBuckets").objects().associate { bucket ->
                val hour = bucket.getInt("localHour")
                hour to TissuePriorBoundaries(
                    meaningfulFloor = bucket.getDouble("meaningfulFloor"),
                    q30 = bucket.getDouble("q30"),
                    q80 = bucket.getDouble("q80"),
                    q95 = bucket.getDouble("q95")
                )
            }
            require(boundaries.keys.sorted() == declaredHours)
            val bodyMass = value.getJSONObject("bodyMass")
            val intensity = value.getJSONObject("habitualIntensity")
            val experience = value.getJSONObject("experience")
            profiles[id] = TissuePriorProfile(
                priorProfileId = id,
                boundariesByLocalHour = boundaries,
                adjustment = TissuePriorProfileAdjustment(
                    priorProfileId = id,
                    bodyMassBeta = bodyMass.getDouble("beta"),
                    bodyMassSource = TissuePriorCoefficientSource.valueOf(bodyMass.getString("source")),
                    lightIntensityLogOffset = intensity.getDouble("lightLogOffset"),
                    hardIntensityLogOffset = intensity.getDouble("hardLogOffset"),
                    habitualIntensitySource = TissuePriorCoefficientSource.valueOf(intensity.getString("source")),
                    strengthExperienceLogCoefficient = experience.getDouble("strengthExperienceLogCoefficient"),
                    strengthExperienceRelevance = experience.getDouble("strengthExperienceRelevance"),
                    strengthExperienceSource = TissuePriorCoefficientSource.valueOf(
                        experience.getString("strengthExperienceSource")
                    ),
                    racketExperienceLogCoefficient = experience.getDouble("racketExperienceLogCoefficient"),
                    racketExperienceRelevance = experience.getDouble("racketExperienceRelevance"),
                    racketExperienceSource = TissuePriorCoefficientSource.valueOf(
                        experience.getString("racketExperienceSource")
                    ),
                    normalClampMin = normalClamp[0],
                    normalClampMax = normalClamp[1],
                    hardClampMin = hardClamp[0],
                    hardClampMax = hardClamp[1]
                )
            )
        }

        val assignments = linkedMapOf<String, String>()
        root.getJSONArray("loadUnitAssignments").objects().forEach { value ->
            val stableKey = value.getString("loadUnitStableKey")
            val profileId = value.getString("priorProfileId")
            require(stableKeyPattern.matches(stableKey) && profileId in profiles && stableKey !in assignments)
            assignments[stableKey] = profileId
        }
        require(root.getInt("loadUnitCount") == EXPECTED_LOAD_UNIT_COUNT)
        require(root.getInt("priorProfileCount") == EXPECTED_PROFILE_COUNT)
        require(assignments.size == EXPECTED_LOAD_UNIT_COUNT)
        require(profiles.size == EXPECTED_PROFILE_COUNT)
        return TissuePriorRegistry(
            schemaVersion = root.getString("schemaVersion"),
            protocolVersion = root.getString("protocolVersion"),
            generatorVersion = root.getString("generatorVersion"),
            deterministicOutputChecksum = root.getString("deterministicOutputChecksum"),
            recoveryEngineFingerprint = root.getString("recoveryEngineFingerprint"),
            profileIdByLoadUnitStableKey = assignments,
            profiles = profiles
        )
    }

    private fun org.json.JSONArray.objects(): List<JSONObject> =
        (0 until length()).map(::getJSONObject)

    private fun org.json.JSONArray.stringValues(): List<String> =
        (0 until length()).map(::getString)

    private fun org.json.JSONArray.intValues(): List<Int> =
        (0 until length()).map(::getInt)

    private fun org.json.JSONArray.doubleValues(): List<Double> =
        (0 until length()).map(::getDouble)
}

object TissuePriorRegistryLoader {
    private const val ASSET_PATH = "metadata/tissue_load_v1/connective_tissue_prior_baselines_v1.json"

    fun fromAssets(context: Context): Result<TissuePriorRegistry> = runCatching {
        val json = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        TissuePriorRegistryParser.parse(json)
    }
}
