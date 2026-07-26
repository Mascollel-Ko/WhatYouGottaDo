package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import kotlin.math.abs

data class RirProbability(
    val rir: Int,
    val probability: Double
)

data class ResolvedRirDistribution(
    val reportedRpe: Double,
    val probabilities: List<RirProbability>,
    val interpolated: Boolean
) {
    init {
        require(probabilities.isNotEmpty())
        require(probabilities.all { it.rir >= 0 && it.probability.isFinite() && it.probability > 0.0 })
        require(abs(probabilities.sumOf(RirProbability::probability) - 1.0) <= 1e-9)
    }

    val expectedRir: Double
        get() = probabilities.sumOf { it.rir * it.probability }
}

class RpeRirPolicy private constructor(
    private val distributionsByRpe: Map<Double, List<RirProbability>>,
    val version: String
) {
    fun resolve(reportedRpe: Double): ResolvedRirDistribution? {
        if (!reportedRpe.isFinite() || reportedRpe !in supportedRpeRange) return null
        distributionsByRpe[reportedRpe]?.let { values ->
            return ResolvedRirDistribution(reportedRpe, values, interpolated = false)
        }
        val lower = distributionsByRpe.keys.filter { it < reportedRpe }.maxOrNull() ?: return null
        val upper = distributionsByRpe.keys.filter { it > reportedRpe }.minOrNull() ?: return null
        val upperWeight = (reportedRpe - lower) / (upper - lower)
        val probabilities = buildMap<Int, Double> {
            distributionsByRpe.getValue(lower).forEach { value ->
                this[value.rir] = getOrDefault(value.rir, 0.0) + value.probability * (1.0 - upperWeight)
            }
            distributionsByRpe.getValue(upper).forEach { value ->
                this[value.rir] = getOrDefault(value.rir, 0.0) + value.probability * upperWeight
            }
        }.map { (rir, probability) -> RirProbability(rir, probability) }
            .filter { it.probability > 0.0 }
            .sortedBy(RirProbability::rir)
        return ResolvedRirDistribution(reportedRpe, probabilities, interpolated = true)
    }

    fun supportedRpes(): List<Double> = distributionsByRpe.keys.sorted()

    val supportedRpeRange: ClosedFloatingPointRange<Double>
        get() = distributionsByRpe.keys.min()..distributionsByRpe.keys.max()

    companion object {
        const val POLICY_VERSION = "strength-rpe-rir-policy-1.0.0"
        const val MINIMUM_SUPPORTED_MIXTURE_MASS = 0.80

        fun fromContext(context: Context): RpeRirPolicy =
            context.assets.open("strength_performance/$ASSET_FILE")
                .bufferedReader(Charsets.UTF_8)
                .use { fromCsv(it.readText()) }

        fun fromCsv(csv: String): RpeRirPolicy {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.size > 1) { "RPE/RIR policy is empty." }
            val header = lines.first().removePrefix("\uFEFF").split(',')
            val rows = lines.drop(1).map { line ->
                val values = line.split(',')
                require(values.size == header.size) { "Malformed RPE/RIR policy row." }
                header.zip(values).toMap()
            }
            val versions = rows.map { it.required("policyVersion") }.distinct()
            require(versions == listOf(POLICY_VERSION)) { "Unexpected RPE/RIR policy version." }
            val distributions = rows.groupBy { it.required("rpe").toDouble() }.mapValues { (_, group) ->
                group.map { row ->
                    require(row.required("sourceClass") == "PRODUCT_POLICY")
                    require(row.required("reviewedStatus") == "REVIEWED")
                    RirProbability(
                        rir = row.required("rir").toInt(),
                        probability = row.required("probability").toDouble()
                    )
                }.sortedBy(RirProbability::rir).also { values ->
                    require(abs(values.sumOf(RirProbability::probability) - 1.0) <= 1e-9)
                    require(values.map(RirProbability::rir).distinct().size == values.size)
                }
            }
            require(distributions.getValue(10.0) == listOf(RirProbability(0, 1.0)))
            val expected = distributions.toSortedMap().mapValues { (_, values) ->
                values.sumOf { it.rir * it.probability }
            }.values.toList()
            require(expected.zipWithNext().all { (lowerRpe, higherRpe) -> lowerRpe >= higherRpe })
            return RpeRirPolicy(distributions, versions.single())
        }

        private fun Map<String, String>.required(name: String): String =
            get(name).orEmpty().trim().also { require(it.isNotEmpty()) { "Missing $name" } }

        private const val ASSET_FILE = "rpe_rir_distribution_v1.csv"
    }
}
