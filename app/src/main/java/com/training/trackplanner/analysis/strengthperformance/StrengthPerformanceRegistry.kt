package com.training.trackplanner.analysis.strengthperformance

import android.content.Context

@JvmInline
value class StrengthPerformanceTargetKey(val value: String)

@JvmInline
value class StrengthFactorKey(val value: String)

enum class StrengthLoadSemantics {
    EXTERNAL_LOAD,
    BODYWEIGHT_PLUS_ADDED_LOAD,
    BODYWEIGHT_MINUS_ASSISTANCE,
    BODYWEIGHT_FRACTION_PLUS_ADDED_LOAD,
    MACHINE_STACK_LOAD,
    IMPLEMENT_TOTAL_LOAD
}

enum class DirectObservationPolicy {
    RPE10_ONLY
}

data class StrengthPerformanceTargetSpec(
    val targetKey: StrengthPerformanceTargetKey,
    val displayNameKo: String,
    val anchorStableKeys: Set<String>,
    val closeVariationStableKeys: Set<String>,
    val loadSemantics: StrengthLoadSemantics,
    val curveSelectionPolicyKey: String,
    val sharedFactorLoadings: Map<StrengthFactorKey, Double>,
    val targetSpecificFactorKey: StrengthFactorKey,
    val supportedRepRange: IntRange,
    val directObservationPolicy: DirectObservationPolicy,
    val enabled: Boolean,
    val configVersion: String
)

data class StrengthProxyLoadingSpec(
    val exerciseStableKey: String,
    val targetKey: StrengthPerformanceTargetKey,
    val relationship: String,
    val loadingWeight: Double,
    val factorLoadings: Map<StrengthFactorKey, Double>,
    val loadSemantics: StrengthLoadSemantics,
    val configVersion: String
) {
    val isDirectAnchor: Boolean get() = relationship == "DIRECT_ANCHOR"
}

class StrengthPerformanceRegistry private constructor(
    private val targetsByKey: Map<StrengthPerformanceTargetKey, StrengthPerformanceTargetSpec>,
    private val proxyRowsByExercise: Map<String, List<StrengthProxyLoadingSpec>>
) {
    fun targets(): List<StrengthPerformanceTargetSpec> = targetsByKey.values
        .filter(StrengthPerformanceTargetSpec::enabled)
        .sortedBy { target -> target.targetKey.value }

    fun target(key: StrengthPerformanceTargetKey): StrengthPerformanceTargetSpec? = targetsByKey[key]

    fun directTarget(exerciseStableKey: String): StrengthPerformanceTargetSpec? =
        targets().firstOrNull { target -> exerciseStableKey in target.anchorStableKeys }

    fun proxyLoadings(exerciseStableKey: String): List<StrengthProxyLoadingSpec> =
        proxyRowsByExercise[exerciseStableKey].orEmpty()

    fun loading(exerciseStableKey: String, targetKey: StrengthPerformanceTargetKey): StrengthProxyLoadingSpec? =
        proxyLoadings(exerciseStableKey).firstOrNull { loading -> loading.targetKey == targetKey }

    fun orderedFactorSchema(): List<StrengthFactorKey> = targets().flatMap { target ->
        target.sharedFactorLoadings.keys + target.targetSpecificFactorKey
    }.distinct().sortedBy(StrengthFactorKey::value)

    companion object {
        const val TARGET_CONFIG_VERSION = "strength-target-registry-1.0.0"
        const val FACTOR_SCHEMA_VERSION = "strength-factor-schema-2.0.0"

        val BENCH_PRESS = StrengthPerformanceTargetKey("strength.bench_press")
        val BACK_SQUAT = StrengthPerformanceTargetKey("strength.back_squat")
        val CONVENTIONAL_DEADLIFT = StrengthPerformanceTargetKey("strength.conventional_deadlift")
        val WEIGHTED_PULL_UP = StrengthPerformanceTargetKey("strength.weighted_pull_up")

        fun fromContext(context: Context): StrengthPerformanceRegistry {
            fun text(name: String): String = context.assets.open("strength_performance/$name")
                .bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            return fromCsv(text(TARGET_FILE), text(PROXY_FILE))
        }

        fun fromCsv(targetCsv: String, proxyCsv: String): StrengthPerformanceRegistry {
            val targets = rows(targetCsv).map { row ->
                StrengthPerformanceTargetSpec(
                    targetKey = StrengthPerformanceTargetKey(row.required("targetKey")),
                    displayNameKo = row.required("displayNameKo"),
                    anchorStableKeys = row.tokens("anchorStableKeys").toSet(),
                    closeVariationStableKeys = row.tokens("closeVariationStableKeys").toSet(),
                    loadSemantics = enumValueOf(row.required("loadSemantics")),
                    curveSelectionPolicyKey = row.required("curveSelectionPolicyKey"),
                    sharedFactorLoadings = row.factorMap("sharedFactorLoadings"),
                    targetSpecificFactorKey = StrengthFactorKey(row.required("targetSpecificFactorKey")),
                    supportedRepRange = row.required("supportedRepMin").toInt()..row.required("supportedRepMax").toInt(),
                    directObservationPolicy = enumValueOf(row.required("directObservationPolicy")),
                    enabled = row.required("enabled").toBooleanStrict(),
                    configVersion = row.required("configVersion")
                ).also { target ->
                    require(target.anchorStableKeys.isNotEmpty())
                    require(target.sharedFactorLoadings.values.all { value -> value in 0.0..1.0 })
                }
            }.associateBy(StrengthPerformanceTargetSpec::targetKey)
            val proxyRows = rows(proxyCsv).map { row ->
                StrengthProxyLoadingSpec(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    targetKey = StrengthPerformanceTargetKey(row.required("targetKey")),
                    relationship = row.required("relationship"),
                    loadingWeight = row.required("loadingWeight").toDouble(),
                    factorLoadings = row.factorMap("factorLoadings"),
                    loadSemantics = enumValueOf(row.required("loadSemantics")),
                    configVersion = row.required("configVersion")
                ).also { loading ->
                    require(loading.targetKey in targets)
                    require(loading.loadingWeight in 0.0..1.0)
                    require(loading.factorLoadings.values.all { value -> value in 0.0..1.0 })
                }
            }.groupBy(StrengthProxyLoadingSpec::exerciseStableKey)
            return StrengthPerformanceRegistry(targets, proxyRows)
        }

        private fun rows(csv: String): List<Map<String, String>> {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.isNotEmpty())
            val header = lines.first().removePrefix("\uFEFF").split(',')
            return lines.drop(1).map { line ->
                val values = line.split(',')
                require(values.size == header.size) { "Malformed strength registry CSV row." }
                header.zip(values).toMap()
            }
        }

        private fun Map<String, String>.required(name: String): String =
            get(name).orEmpty().trim().also { value -> require(value.isNotEmpty()) { "Missing $name" } }

        private fun Map<String, String>.tokens(name: String): List<String> =
            get(name).orEmpty().split('|').map(String::trim).filter(String::isNotEmpty)

        private fun Map<String, String>.factorMap(name: String): Map<StrengthFactorKey, Double> =
            tokens(name).associate { token ->
                val parts = token.split(':', limit = 2)
                require(parts.size == 2)
                StrengthFactorKey(parts[0]) to parts[1].toDouble()
            }

        private const val TARGET_FILE = "strength_target_registry_v1.csv"
        private const val PROXY_FILE = "strength_proxy_loadings_v1.csv"
    }
}
