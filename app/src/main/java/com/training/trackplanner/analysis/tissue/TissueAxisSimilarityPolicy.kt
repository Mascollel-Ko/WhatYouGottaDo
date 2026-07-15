package com.training.trackplanner.analysis.tissue

data class TissueAxisSimilarityBreakdown(
    val exactStableKeySimilarity: Double,
    val movementFamilySimilarity: Double,
    val eventTypeSimilarity: Double,
    val loadSimilarity: Double,
    val loadPlacementSimilarity: Double,
    val lateralitySimilarity: Double,
    val romSimilarity: Double,
    val jointAngleSimilarity: Double,
    val velocitySimilarity: Double,
    val phaseSimilarity: Double,
    val directionSimilarity: Double,
    val landingStrategySimilarity: Double,
    val surfaceSimilarity: Double,
    val populationSimilarity: Double,
    val methodCompatibility: Double
) {
    fun components() = linkedMapOf(
        "exactStableKey" to exactStableKeySimilarity,
        "movementFamily" to movementFamilySimilarity,
        "eventType" to eventTypeSimilarity,
        "load" to loadSimilarity,
        "loadPlacement" to loadPlacementSimilarity,
        "laterality" to lateralitySimilarity,
        "rom" to romSimilarity,
        "jointAngle" to jointAngleSimilarity,
        "velocity" to velocitySimilarity,
        "phase" to phaseSimilarity,
        "direction" to directionSimilarity,
        "landingStrategy" to landingStrategySimilarity,
        "surface" to surfaceSimilarity,
        "population" to populationSimilarity,
        "method" to methodCompatibility
    )
}

data class TissueAxisSimilarityPolicySpec(
    val policyId: String,
    val axis: TissueAxis,
    val componentWeights: Map<String, Double>,
    val minimumSimilarity: Double,
    val dominantSourceThreshold: Double,
    val dominantSourceMargin: Double,
    val version: String
)

object TissueAxisSimilarityPolicy {
    fun score(spec: TissueAxisSimilarityPolicySpec, breakdown: TissueAxisSimilarityBreakdown): Double {
        val components = breakdown.components()
        require(components.values.all { it in 0.0..1.0 }) { "Similarity components must be within 0..1." }
        require(spec.componentWeights.keys == components.keys) { "Similarity policy components are incomplete." }
        require(kotlin.math.abs(spec.componentWeights.values.sum() - 1.0) < 1e-9) { "Similarity weights must sum to one." }
        return components.entries.sumOf { (name, value) -> value * spec.componentWeights.getValue(name) }
    }
}

object TissueAxisContractParser {
    private val componentNames = TissueAxisSimilarityBreakdown(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        .components().keys

    fun scoringPolicies(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueAxisScoringPolicySpec(
            row.required("policyId"), row.enum("axis"), row.enum("profile"),
            row.required("minimumScore").toBigDecimal(), row.required("maximumScore").toBigDecimal(),
            row.required("persistedDecimalPlaces").toInt(), row.boolean("unknownIsNull"),
            row.boolean("crossAxisAggregationAllowed"), row.boolean("confidenceAltersScore"), row.required("version")
        )
    }

    fun similarityPolicies(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueAxisSimilarityPolicySpec(
            row.required("policyId"), row.enum("axis"),
            componentNames.associateWith { name -> row.required("${name}Weight").toDouble() },
            row.required("minimumSimilarity").toDouble(), row.required("dominantSourceThreshold").toDouble(),
            row.required("dominantSourceMargin").toDouble(), row.required("version")
        )
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String) = value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String) = required(name).toBooleanStrict()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T = enumValueOf(required(name).uppercase())
}
