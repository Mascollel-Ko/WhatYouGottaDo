package com.training.trackplanner.analysis.strengthperformance.curve

import android.content.Context
import java.security.MessageDigest
import kotlin.math.exp

@JvmInline
value class RepetitionCurveProfileId(val value: String)

data class RepetitionCurveKnot(
    val repetitions: Double,
    val relativeLoad: Double
)

data class RepetitionCurveProvenance(
    val curveProfileId: RepetitionCurveProfileId,
    val curveVersion: String,
    val sourceCitation: String,
    val sourceArtifactHash: String,
    val sourceTableChecksum: String,
    val generatorVersion: String,
    val reviewedAt: String,
    val supportedRepRange: IntRange,
    val sourceExerciseScope: String,
    val sourceModelDescription: String,
    val runtimeAssetChecksum: String
)

enum class CurveMatchLevel {
    EXACT_EXERCISE,
    VALIDATED_VARIATION_FAMILY,
    BORROWED_WITH_UNCERTAINTY,
    GENERAL_FALLBACK,
    UNSUPPORTED
}

enum class RepetitionCurveEvaluationStatus {
    SUPPORTED,
    UNSUPPORTED_REPETITIONS,
    UNSUPPORTED_RELATIVE_LOAD
}

data class RepetitionCurveEvaluation(
    val status: RepetitionCurveEvaluationStatus,
    val relativeLoad: Double? = null,
    val repetitions: Double? = null
)

data class RepetitionCurveProfile(
    val id: RepetitionCurveProfileId,
    val knots: List<RepetitionCurveKnot>,
    val provenance: RepetitionCurveProvenance
) {
    private val interpolation = MonotonePchip(
        knots.map(RepetitionCurveKnot::repetitions).toDoubleArray(),
        knots.map(RepetitionCurveKnot::relativeLoad).toDoubleArray()
    )

    init {
        require(knots.size >= 2)
        require(knots.first().repetitions == 1.0 && knots.first().relativeLoad == 1.0)
        require(knots.zipWithNext().all { (left, right) ->
            right.repetitions > left.repetitions && right.relativeLoad <= left.relativeLoad
        })
        require(knots.all { knot -> knot.relativeLoad.isFinite() && knot.relativeLoad in 0.0..1.0 })
    }

    fun evaluate(repetitions: Double): RepetitionCurveEvaluation {
        if (!repetitions.isFinite() || repetitions !in knots.first().repetitions..knots.last().repetitions) {
            return RepetitionCurveEvaluation(RepetitionCurveEvaluationStatus.UNSUPPORTED_REPETITIONS)
        }
        val value = if (repetitions == 1.0) 1.0 else interpolation.evaluate(repetitions)
        return RepetitionCurveEvaluation(
            status = RepetitionCurveEvaluationStatus.SUPPORTED,
            relativeLoad = value.coerceIn(knots.last().relativeLoad, 1.0),
            repetitions = repetitions
        )
    }

    fun invert(relativeLoad: Double): RepetitionCurveEvaluation {
        if (!relativeLoad.isFinite() || relativeLoad !in knots.last().relativeLoad..1.0) {
            return RepetitionCurveEvaluation(RepetitionCurveEvaluationStatus.UNSUPPORTED_RELATIVE_LOAD)
        }
        if (relativeLoad == 1.0) {
            return RepetitionCurveEvaluation(RepetitionCurveEvaluationStatus.SUPPORTED, 1.0, 1.0)
        }
        var low = knots.first().repetitions
        var high = knots.last().repetitions
        repeat(64) {
            val middle = (low + high) / 2.0
            if (checkNotNull(evaluate(middle).relativeLoad) > relativeLoad) low = middle else high = middle
        }
        val repetitions = (low + high) / 2.0
        return RepetitionCurveEvaluation(
            status = RepetitionCurveEvaluationStatus.SUPPORTED,
            relativeLoad = relativeLoad,
            repetitions = repetitions
        )
    }
}

data class RepetitionCurveAssignment(
    val exerciseStableKey: String,
    val curveProfileId: RepetitionCurveProfileId,
    val matchLevel: CurveMatchLevel,
    val varianceMultiplier: Double,
    val assignmentVersion: String,
    val rationale: String,
    val sourceScope: String,
    val reviewedStatus: String
)

data class ResolvedRepetitionCurve(
    val profile: RepetitionCurveProfile,
    val matchLevel: CurveMatchLevel,
    val varianceMultiplier: Double,
    val assignmentVersion: String,
    val curveSubjectKey: String,
    val personalTheta: Double = 0.0
) {
    fun evaluate(repetitions: Double): RepetitionCurveEvaluation {
        val adjustedRepetitions = 1.0 + exp(-personalTheta) * (repetitions - 1.0)
        return profile.evaluate(adjustedRepetitions).copy(repetitions = repetitions)
    }
}

class RepetitionCurveRegistry private constructor(
    private val profiles: Map<RepetitionCurveProfileId, RepetitionCurveProfile>,
    private val assignments: Map<String, RepetitionCurveAssignment>
) {
    fun profiles(): List<RepetitionCurveProfile> = profiles.values.sortedBy { profile -> profile.id.value }

    fun profile(id: RepetitionCurveProfileId): RepetitionCurveProfile? = profiles[id]

    fun assignment(stableKey: String): RepetitionCurveAssignment? = assignments[stableKey]

    fun resolve(stableKey: String, isCustom: Boolean = false, personalTheta: Double = 0.0): ResolvedRepetitionCurve {
        val assignment = assignments[stableKey]
        if (assignment != null) {
            return ResolvedRepetitionCurve(
                profile = checkNotNull(profiles[assignment.curveProfileId]),
                matchLevel = assignment.matchLevel,
                varianceMultiplier = assignment.varianceMultiplier,
                assignmentVersion = assignment.assignmentVersion,
                curveSubjectKey = "exercise:$stableKey",
                personalTheta = personalTheta
            )
        }
        val general = checkNotNull(profiles[GENERAL_PROFILE_ID])
        return ResolvedRepetitionCurve(
            profile = general,
            matchLevel = CurveMatchLevel.GENERAL_FALLBACK,
            varianceMultiplier = if (isCustom) 1.80 else 1.55,
            assignmentVersion = "runtime-general-fallback-1.0.0",
            curveSubjectKey = if (isCustom) "global:user-strength-endurance" else "exercise:$stableKey",
            personalTheta = personalTheta
        )
    }

    companion object {
        val GENERAL_PROFILE_ID = RepetitionCurveProfileId("reps_curve.general_resistance.v1")
        const val CURVE_VERSION = "repetition-curve-assets-1.0.0"

        fun fromContext(context: Context): RepetitionCurveRegistry {
            fun bytes(name: String): ByteArray = context.assets.open("$ASSET_DIRECTORY/$name").use { it.readBytes() }
            return fromAssets(
                profileBytes = bytes(PROFILE_FILE),
                manifestBytes = bytes(MANIFEST_FILE),
                sourceBytes = bytes(SOURCE_FILE),
                assignmentBytes = bytes(ASSIGNMENT_FILE)
            )
        }

        fun fromAssets(
            profileBytes: ByteArray,
            manifestBytes: ByteArray,
            sourceBytes: ByteArray,
            assignmentBytes: ByteArray
        ): RepetitionCurveRegistry {
            val profileChecksum = sha256(canonicalTextBytes(profileBytes))
            val sourceChecksum = sha256(canonicalTextBytes(sourceBytes))
            val manifestRows = csvRows(manifestBytes.decodeToString())
            require(manifestRows.isNotEmpty()) { "Repetition-curve manifest is empty." }
            val provenance = manifestRows.associate { row ->
                val id = RepetitionCurveProfileId(row.required("curveProfileId"))
                require(row.required("runtimeAssetChecksum").equals(profileChecksum, ignoreCase = true)) {
                    "Repetition-curve runtime asset checksum mismatch."
                }
                require(row.required("sourceTableChecksum").equals(sourceChecksum, ignoreCase = true)) {
                    "Repetition-curve source table checksum mismatch."
                }
                val range = row.required("supportedRepRange").split("..")
                id to RepetitionCurveProvenance(
                    curveProfileId = id,
                    curveVersion = row.required("curveVersion"),
                    sourceCitation = row.required("sourceCitation"),
                    sourceArtifactHash = row.required("sourceArtifactHash"),
                    sourceTableChecksum = row.required("sourceTableChecksum"),
                    generatorVersion = row.required("generatorVersion"),
                    reviewedAt = row.required("reviewedAt"),
                    supportedRepRange = range[0].toInt()..range[1].toInt(),
                    sourceExerciseScope = row.required("sourceExerciseScope"),
                    sourceModelDescription = row.required("sourceModelDescription"),
                    runtimeAssetChecksum = row.required("runtimeAssetChecksum")
                )
            }
            val knots = csvRows(profileBytes.decodeToString()).groupBy { row ->
                RepetitionCurveProfileId(row.required("curveProfileId"))
            }.mapValues { (id, rows) ->
                RepetitionCurveProfile(
                    id = id,
                    knots = rows.map { row ->
                        RepetitionCurveKnot(
                            repetitions = row.required("repetitions").toDouble(),
                            relativeLoad = row.required("relativeLoad").toDouble()
                        )
                    }.sortedBy(RepetitionCurveKnot::repetitions),
                    provenance = checkNotNull(provenance[id])
                )
            }
            require(knots.keys == provenance.keys) { "Repetition-curve profile and manifest sets differ." }
            val assignments = csvRows(assignmentBytes.decodeToString()).map { row ->
                RepetitionCurveAssignment(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    curveProfileId = RepetitionCurveProfileId(row.required("curveProfileId")),
                    matchLevel = enumValueOf(row.required("matchLevel")),
                    varianceMultiplier = row.required("varianceMultiplier").toDouble(),
                    assignmentVersion = row.required("assignmentVersion"),
                    rationale = row.required("rationale"),
                    sourceScope = row.required("sourceScope"),
                    reviewedStatus = row.required("reviewedStatus")
                ).also { assignment ->
                    require(assignment.curveProfileId in knots)
                    require(assignment.varianceMultiplier >= 1.0)
                }
            }.associateBy(RepetitionCurveAssignment::exerciseStableKey)
            return RepetitionCurveRegistry(knots, assignments)
        }

        private fun csvRows(csv: String): List<Map<String, String>> {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.isNotEmpty()) { "CSV asset is empty." }
            val header = lines.first().removePrefix("\uFEFF").split(',')
            return lines.drop(1).map { line ->
                val values = line.split(',')
                require(values.size == header.size) { "Malformed strength-performance CSV row." }
                header.zip(values).toMap()
            }
        }

        private fun Map<String, String>.required(name: String): String =
            get(name).orEmpty().trim().also { value -> require(value.isNotEmpty()) { "Missing $name" } }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

        private fun canonicalTextBytes(bytes: ByteArray): ByteArray = bytes.decodeToString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .encodeToByteArray()

        private const val ASSET_DIRECTORY = "strength_performance"
        private const val PROFILE_FILE = "repetition_curve_profiles_v1.csv"
        private const val MANIFEST_FILE = "repetition_curve_manifest_v1.csv"
        private const val SOURCE_FILE = "repetition_curve_source_v1.csv"
        private const val ASSIGNMENT_FILE = "repetition_curve_assignments_v1.csv"
    }
}

private class MonotonePchip(
    private val x: DoubleArray,
    private val y: DoubleArray
) {
    private val slopes = slopes(x, y)

    init {
        require(x.size == y.size && x.size >= 2)
        require((0 until x.lastIndex).all { index -> x[index + 1] > x[index] })
    }

    fun evaluate(value: Double): Double {
        require(value in x.first()..x.last())
        if (value == x.last()) return y.last()
        val index = (0 until x.lastIndex).first { i -> value <= x[i + 1] }
        val width = x[index + 1] - x[index]
        val t = (value - x[index]) / width
        val h00 = 2 * t * t * t - 3 * t * t + 1
        val h10 = t * t * t - 2 * t * t + t
        val h01 = -2 * t * t * t + 3 * t * t
        val h11 = t * t * t - t * t
        return h00 * y[index] + h10 * width * slopes[index] +
            h01 * y[index + 1] + h11 * width * slopes[index + 1]
    }

    private companion object {
        fun slopes(x: DoubleArray, y: DoubleArray): DoubleArray {
            val n = x.size
            val h = DoubleArray(n - 1) { index -> x[index + 1] - x[index] }
            val delta = DoubleArray(n - 1) { index -> (y[index + 1] - y[index]) / h[index] }
            if (n == 2) return doubleArrayOf(delta[0], delta[0])
            val result = DoubleArray(n)
            for (index in 1 until n - 1) {
                result[index] = if (delta[index - 1] == 0.0 || delta[index] == 0.0 ||
                    delta[index - 1] * delta[index] < 0.0
                ) {
                    0.0
                } else {
                    val firstWeight = 2.0 * h[index] + h[index - 1]
                    val secondWeight = h[index] + 2.0 * h[index - 1]
                    (firstWeight + secondWeight) /
                        (firstWeight / delta[index - 1] + secondWeight / delta[index])
                }
            }
            result[0] = endpoint(h[0], h[1], delta[0], delta[1])
            result[n - 1] = endpoint(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3])
            return result
        }

        fun endpoint(h0: Double, h1: Double, delta0: Double, delta1: Double): Double {
            var value = ((2.0 * h0 + h1) * delta0 - h0 * delta1) / (h0 + h1)
            if (value * delta0 <= 0.0) value = 0.0
            else if (delta0 * delta1 < 0.0 && kotlin.math.abs(value) > kotlin.math.abs(3.0 * delta0)) {
                value = 3.0 * delta0
            }
            return value
        }
    }
}
