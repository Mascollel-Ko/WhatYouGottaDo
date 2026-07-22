package com.training.trackplanner.analysis.strengthperformance

import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.StrengthCurvePosteriorEntity
import com.training.trackplanner.data.StrengthPosteriorEvidenceEntity
import com.training.trackplanner.data.StrengthPosteriorHistoryEntity
import com.training.trackplanner.data.StrengthPosteriorModelStateEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.apache.commons.math3.distribution.NormalDistribution

data class StrengthPosteriorState(
    val orderedFactorSchema: List<StrengthFactorKey>,
    val mean: DoubleArray,
    val covariance: Array<DoubleArray>,
    val lastProcessedEventUuid: String? = null,
    val lastProcessedDate: LocalDate? = null
) {
    init {
        require(mean.size == orderedFactorSchema.size)
        require(covariance.size == mean.size && covariance.all { row -> row.size == mean.size })
        require(mean.all(Double::isFinite))
        require(covariance.all { row -> row.all(Double::isFinite) })
    }

    fun copyDeep(
        mean: DoubleArray = this.mean.copyOf(),
        covariance: Array<DoubleArray> = Array(this.covariance.size) { index -> this.covariance[index].copyOf() },
        lastProcessedEventUuid: String? = this.lastProcessedEventUuid,
        lastProcessedDate: LocalDate? = this.lastProcessedDate
    ): StrengthPosteriorState = StrengthPosteriorState(
        orderedFactorSchema,
        mean,
        covariance,
        lastProcessedEventUuid,
        lastProcessedDate
    )
}

data class StrengthTargetDistribution(
    val median: Double,
    val low50: Double,
    val high50: Double,
    val low80: Double,
    val high80: Double,
    val low95: Double,
    val high95: Double,
    val logMean: Double,
    val logVariance: Double
)

data class StrengthPosteriorComputation(
    val state: StrengthPosteriorState,
    val history: List<StrengthPosteriorHistoryEntity>,
    val evidence: List<StrengthPosteriorEvidenceEntity>,
    val curvePosteriors: List<PersonalCurvePosterior>,
    val evidenceFingerprint: String,
    val diagnostics: List<String>
)

object VersionedDoubleArrayCodec {
    fun encode(values: DoubleArray): String {
        require(values.all(Double::isFinite))
        val payload = ByteBuffer.allocate(values.size * Double.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putDouble)
        }.array()
        val checksum = sha256(payload)
        return "v1|dim=${values.size}|order=LE|payload=${Base64.getEncoder().encodeToString(payload)}|sha256=$checksum"
    }

    fun decode(encoded: String): DoubleArray {
        val fields = encoded.split('|').associate { token ->
            val pair = token.split('=', limit = 2)
            if (pair.size == 1) "version" to pair[0] else pair[0] to pair[1]
        }
        require(fields["version"] == "v1" && fields["order"] == "LE")
        val dimension = checkNotNull(fields["dim"]).toInt()
        val payload = Base64.getDecoder().decode(checkNotNull(fields["payload"]))
        require(payload.size == dimension * Double.SIZE_BYTES)
        require(sha256(payload) == fields["sha256"])
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return DoubleArray(dimension) { buffer.double }.also { values -> require(values.all(Double::isFinite)) }
    }

    fun packLowerTriangle(matrix: Array<DoubleArray>): DoubleArray {
        require(matrix.all { row -> row.size == matrix.size })
        return DoubleArray(matrix.size * (matrix.size + 1) / 2).also { packed ->
            var cursor = 0
            for (row in matrix.indices) for (column in 0..row) packed[cursor++] = matrix[row][column]
        }
    }

    fun unpackLowerTriangle(packed: DoubleArray, dimension: Int): Array<DoubleArray> {
        require(packed.size == dimension * (dimension + 1) / 2)
        val matrix = Array(dimension) { DoubleArray(dimension) }
        var cursor = 0
        for (row in 0 until dimension) for (column in 0..row) {
            val value = packed[cursor++]
            matrix[row][column] = value
            matrix[column][row] = value
        }
        return matrix
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
}

object StrengthPosteriorModel {
    const val MODEL_VERSION = "strength-performance-model-2.0.0"
    const val MODEL_INSTANCE_KEY = "strength-performance-current"

    fun initialState(
        registry: StrengthPerformanceRegistry,
        initialProfile: InitialUserProfile?
    ): StrengthPosteriorState {
        val schema = registry.orderedFactorSchema()
        val mean = DoubleArray(schema.size)
        val covariance = Array(schema.size) { DoubleArray(schema.size) }
        val initialByFactor = registry.targets().associate { target ->
            target.targetSpecificFactorKey to ln(initialCapacity(target.targetKey, initialProfile))
        }
        schema.forEachIndexed { index, factor ->
            val targetMean = initialByFactor[factor]
            mean[index] = targetMean ?: 0.0
            covariance[index][index] = if (targetMean != null) TARGET_INITIAL_VARIANCE else SHARED_INITIAL_VARIANCE
        }
        return StrengthPosteriorState(schema, mean, covariance)
    }

    fun compute(
        eventUuid: String,
        date: LocalDate,
        currentState: StrengthPosteriorState,
        observations: List<StrengthExerciseSessionObservation>,
        registry: StrengthPerformanceRegistry,
        curves: RepetitionCurveRegistry,
        curvePosteriorBySubject: Map<String, PersonalCurvePosterior>,
        now: Long
    ): StrengthPosteriorComputation {
        var state = predict(currentState, date)
        val priorState = state.copyDeep()
        val diagnostics = mutableListOf<String>()
        val affected = observations.flatMap(StrengthExerciseSessionObservation::targetLoadings)
            .map(StrengthProxyLoadingSpec::targetKey).distinct().sortedBy(StrengthPerformanceTargetKey::value)
        val representative = affected.associateWith { targetKey ->
            observations.filter { observation -> observation.targetLoadings.any { loading -> loading.targetKey == targetKey } }
                .maxWithOrNull(
                    compareBy<StrengthExerciseSessionObservation> { observation ->
                        when {
                            observation.directTargetKey == targetKey && observation.observationType == StrengthObservationType.DIRECT_1RM -> 3
                            observation.directTargetKey == targetKey -> 2
                            else -> 1
                        }
                    }.thenBy { observation ->
                        observation.targetLoadings.first { loading -> loading.targetKey == targetKey }.loadingWeight
                    }
                )
        }
        observations.sortedWith(
            compareBy<StrengthExerciseSessionObservation>(StrengthExerciseSessionObservation::exerciseStableKey)
                .thenBy(StrengthExerciseSessionObservation::evidenceFingerprint)
        ).forEach { observation ->
            diagnostics += observation.diagnostics
            observation.targetLoadings.sortedBy { loading -> loading.targetKey.value }.forEach { loading ->
                val target = registry.target(loading.targetKey) ?: return@forEach
                val vector = observationVector(state, target, loading)
                val observationLog = ln(observation.capacityMedianKg)
                val variance = observation.logVariance /
                    (loading.loadingWeight * loading.loadingWeight).coerceAtLeast(MIN_LOADING_SQUARED) +
                    if (loading.isDirectAnchor) 0.0 else PROXY_VARIANCE_FLOOR
                state = if (observation.lowerBoundOnly) {
                    val predicted = dot(vector, state.mean)
                    if (predicted >= observationLog) state else update(state, observationLog, vector, variance + LOWER_BOUND_VARIANCE)
                } else {
                    update(state, observationLog, vector, variance)
                }
            }
        }

        val updatedCurves = mutableListOf<PersonalCurvePosterior>()
        observations.filter { observation -> observation.directTargetKey != null }.forEach { observation ->
            val profile = checkNotNull(curves.profile(com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveProfileId(observation.curveProfileId)))
            val current = curvePosteriorBySubject[observation.curveSubjectKey]
                ?: PersonalCurveCalibrator.initial(observation.curveSubjectKey, profile, now)
            val reference = observation.directObservedLoadKg
                ?: distribution(priorState, checkNotNull(registry.target(checkNotNull(observation.directTargetKey)))).median
            updatedCurves += PersonalCurveCalibrator.update(current, profile, observation.setEvidence, reference, now)
        }
        val curveBySubject = (curvePosteriorBySubject + updatedCurves.associateBy(PersonalCurvePosterior::curveSubjectKey))
        val allEvidenceFingerprint = fingerprint(*observations.map(StrengthExerciseSessionObservation::evidenceFingerprint).toTypedArray())
        val histories = affected.map { targetKey ->
            val target = checkNotNull(registry.target(targetKey))
            val before = distribution(priorState, target)
            val after = distribution(state, target)
            val observation = representative[targetKey]
            val targetLoading = observation?.targetLoadings?.firstOrNull { loading -> loading.targetKey == targetKey }
            val predictiveVariance = before.logVariance + (observation?.logVariance ?: 0.0)
            val surprise = observation?.capacityMedianKg?.takeIf { it > 0.0 }?.let { observed ->
                (ln(observed) - before.logMean) / sqrt(predictiveVariance.coerceAtLeast(MIN_VARIANCE))
            }
            val percentile = surprise?.let { value -> NormalDistribution().cumulativeProbability(value) }
            val curvePosterior = observation?.let { curveBySubject[it.curveSubjectKey] }
            StrengthPosteriorHistoryEntity(
                eventUuid = eventUuid,
                targetKey = targetKey.value,
                sessionDate = date.toString(),
                priorMedian = before.median,
                priorLow50 = before.low50,
                priorHigh50 = before.high50,
                priorLow80 = before.low80,
                priorHigh80 = before.high80,
                priorLow95 = before.low95,
                priorHigh95 = before.high95,
                posteriorMedian = after.median,
                posteriorLow50 = after.low50,
                posteriorHigh50 = after.high50,
                posteriorLow80 = after.low80,
                posteriorHigh80 = after.high80,
                posteriorLow95 = after.low95,
                posteriorHigh95 = after.high95,
                directObservedLoad = observation?.takeIf { it.directTargetKey == targetKey }?.directObservedLoadKg,
                directObservationType = observation?.takeIf { it.directTargetKey == targetKey }?.observationType?.name ?: "NONE",
                sessionObservationMedian = observation?.capacityMedianKg,
                sessionObservationLow80 = observation?.capacityLow80Kg,
                sessionObservationHigh80 = observation?.capacityHigh80Kg,
                posteriorMeanChange = after.median - before.median,
                posteriorVarianceBefore = before.logVariance,
                posteriorVarianceAfter = after.logVariance,
                intervalWidthChange80 = (after.high80 - after.low80) - (before.high80 - before.low80),
                predictivePercentile = percentile,
                standardizedSurprise = surprise,
                modelVersion = MODEL_VERSION,
                factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
                curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
                targetConfigVersion = target.configVersion,
                evidenceFingerprint = observation?.evidenceFingerprint ?: allEvidenceFingerprint,
                sourceEvidenceStatus = "AVAILABLE",
                sourceSetCountAtProcessing = observation?.sourceSetIds?.size ?: 0,
                bodyWeightKgAtProcessing = observation?.bodyWeightKg,
                rawAddedWeightKgAtProcessing = observation?.rawAddedWeightKg,
                bodyWeightSource = observation?.bodyWeightSource?.name,
                curveProfileId = observation?.curveProfileId,
                curveMatchLevel = observation?.curveMatchLevel,
                curveCalibrationStatus = curvePosterior?.calibrationStatus?.name ?: PersonalCurveStatus.CANONICAL_ONLY.name,
                createdAt = now
            )
        }
        val evidenceEntities = observations.map { observation -> observation.toEntity(eventUuid, now) }
        return StrengthPosteriorComputation(
            state = state.copyDeep(lastProcessedEventUuid = eventUuid, lastProcessedDate = date),
            history = histories,
            evidence = evidenceEntities,
            curvePosteriors = updatedCurves.distinctBy(PersonalCurvePosterior::curveSubjectKey),
            evidenceFingerprint = allEvidenceFingerprint,
            diagnostics = diagnostics.distinct()
        )
    }

    fun distribution(state: StrengthPosteriorState, target: StrengthPerformanceTargetSpec): StrengthTargetDistribution {
        val h = targetVector(state, target)
        val mean = dot(h, state.mean)
        val variance = quadratic(h, state.covariance).coerceAtLeast(MIN_VARIANCE)
        val sd = sqrt(variance)
        return StrengthTargetDistribution(
            median = exp(mean),
            low50 = exp(mean - Z_50 * sd),
            high50 = exp(mean + Z_50 * sd),
            low80 = exp(mean - Z_80 * sd),
            high80 = exp(mean + Z_80 * sd),
            low95 = exp(mean - Z_95 * sd),
            high95 = exp(mean + Z_95 * sd),
            logMean = mean,
            logVariance = variance
        )
    }

    fun toEntity(state: StrengthPosteriorState, now: Long): StrengthPosteriorModelStateEntity {
        val packed = VersionedDoubleArrayCodec.packLowerTriangle(state.covariance)
        val meanEncoded = VersionedDoubleArrayCodec.encode(state.mean)
        val covarianceEncoded = VersionedDoubleArrayCodec.encode(packed)
        val stateFingerprint = fingerprint(
            state.orderedFactorSchema.joinToString("|") { key -> key.value },
            meanEncoded,
            covarianceEncoded,
            state.lastProcessedEventUuid.orEmpty(),
            state.lastProcessedDate?.toString().orEmpty(),
            MODEL_VERSION,
            StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION
        )
        return StrengthPosteriorModelStateEntity(
            modelInstanceKey = MODEL_INSTANCE_KEY,
            orderedFactorSchema = state.orderedFactorSchema.joinToString("|") { key -> key.value },
            stateMeanEncoded = meanEncoded,
            packedCovarianceEncoded = covarianceEncoded,
            stateDimension = state.mean.size,
            lastProcessedEventUuid = state.lastProcessedEventUuid,
            lastProcessedDate = state.lastProcessedDate?.toString(),
            modelVersion = MODEL_VERSION,
            curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
            factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
            stateFingerprint = stateFingerprint,
            updatedAt = now
        )
    }

    fun fromEntity(entity: StrengthPosteriorModelStateEntity): StrengthPosteriorState {
        require(entity.modelVersion == MODEL_VERSION)
        require(entity.factorSchemaVersion == StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION)
        val schema = entity.orderedFactorSchema.split('|').filter(String::isNotEmpty).map(::StrengthFactorKey)
        require(schema.size == entity.stateDimension)
        val mean = VersionedDoubleArrayCodec.decode(entity.stateMeanEncoded)
        val covariance = VersionedDoubleArrayCodec.unpackLowerTriangle(
            VersionedDoubleArrayCodec.decode(entity.packedCovarianceEncoded),
            entity.stateDimension
        )
        val reconstructed = StrengthPosteriorState(
            orderedFactorSchema = schema,
            mean = mean,
            covariance = covariance,
            lastProcessedEventUuid = entity.lastProcessedEventUuid,
            lastProcessedDate = entity.lastProcessedDate?.let(LocalDate::parse)
        )
        require(toEntity(reconstructed, entity.updatedAt).stateFingerprint == entity.stateFingerprint)
        return reconstructed
    }

    private fun predict(state: StrengthPosteriorState, date: LocalDate): StrengthPosteriorState {
        val days = state.lastProcessedDate?.let { previous -> ChronoUnit.DAYS.between(previous, date).coerceAtLeast(0) } ?: 0
        if (days == 0L) return state.copyDeep()
        val covariance = Array(state.covariance.size) { index -> state.covariance[index].copyOf() }
        state.orderedFactorSchema.forEachIndexed { index, factor ->
            val daily = if (factor.value.startsWith("strength.factor.target.")) TARGET_DAILY_PROCESS_VARIANCE else SHARED_DAILY_PROCESS_VARIANCE
            covariance[index][index] += daily * days.coerceAtMost(MAX_PROCESS_DAYS)
        }
        return state.copyDeep(covariance = covariance)
    }

    private fun update(
        state: StrengthPosteriorState,
        observation: Double,
        h: DoubleArray,
        observationVariance: Double
    ): StrengthPosteriorState {
        if (!observation.isFinite() || !observationVariance.isFinite() || observationVariance <= 0.0) return state
        val ph = multiply(state.covariance, h)
        val innovationVariance = dot(h, ph) + observationVariance
        if (!innovationVariance.isFinite() || innovationVariance <= MIN_VARIANCE) return state
        val innovation = observation - dot(h, state.mean)
        val gain = DoubleArray(h.size) { index -> ph[index] / innovationVariance }
        val mean = DoubleArray(h.size) { index -> state.mean[index] + gain[index] * innovation }
        val identityMinusKh = Array(h.size) { row ->
            DoubleArray(h.size) { column -> (if (row == column) 1.0 else 0.0) - gain[row] * h[column] }
        }
        val joseph = add(
            multiply(multiply(identityMinusKh, state.covariance), transpose(identityMinusKh)),
            outer(gain, gain).map { row -> row.map { value -> value * observationVariance }.toDoubleArray() }.toTypedArray()
        )
        val symmetric = Array(h.size) { row ->
            DoubleArray(h.size) { column -> (joseph[row][column] + joseph[column][row]) / 2.0 }
        }
        for (index in symmetric.indices) symmetric[index][index] = symmetric[index][index].coerceAtLeast(MIN_VARIANCE)
        if (!mean.all(Double::isFinite) || !symmetric.all { row -> row.all(Double::isFinite) }) return state
        return state.copyDeep(mean = mean, covariance = symmetric)
    }

    private fun targetVector(state: StrengthPosteriorState, target: StrengthPerformanceTargetSpec): DoubleArray =
        DoubleArray(state.mean.size) { index ->
            val factor = state.orderedFactorSchema[index]
            when {
                factor == target.targetSpecificFactorKey -> 1.0
                factor in target.sharedFactorLoadings -> target.sharedFactorLoadings.getValue(factor) * SHARED_LOADING_SCALE
                else -> 0.0
            }
        }

    private fun observationVector(
        state: StrengthPosteriorState,
        target: StrengthPerformanceTargetSpec,
        loading: StrengthProxyLoadingSpec
    ): DoubleArray = DoubleArray(state.mean.size) { index ->
        val factor = state.orderedFactorSchema[index]
        when {
            factor == target.targetSpecificFactorKey -> 1.0
            factor in loading.factorLoadings -> loading.factorLoadings.getValue(factor) * SHARED_LOADING_SCALE
            else -> 0.0
        }
    }

    private fun initialCapacity(key: StrengthPerformanceTargetKey, profile: InitialUserProfile?): Double = when (key) {
        StrengthPerformanceRegistry.BENCH_PRESS -> profile?.benchPressKg
        StrengthPerformanceRegistry.BACK_SQUAT -> profile?.squatKg
        StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT -> profile?.deadliftKg
        StrengthPerformanceRegistry.WEIGHTED_PULL_UP -> {
            val bodyWeight = profile?.bodyWeightKg
            val added = profile?.pullUpAddedWeightKg
            if (bodyWeight != null) bodyWeight + (added ?: 0.0) else null
        }
        else -> null
    }?.takeIf { value -> value.isFinite() && value > 0.0 } ?: when (key) {
        StrengthPerformanceRegistry.BENCH_PRESS -> 60.0
        StrengthPerformanceRegistry.BACK_SQUAT -> 80.0
        StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT -> 100.0
        StrengthPerformanceRegistry.WEIGHTED_PULL_UP -> 70.0
        else -> 60.0
    }

    private fun StrengthExerciseSessionObservation.toEntity(eventUuid: String, now: Long) =
        StrengthPosteriorEvidenceEntity(
            evidenceFingerprint = evidenceFingerprint,
            eventUuid = eventUuid,
            sessionKey = sessionKey,
            sessionDate = date.toString(),
            exerciseStableKey = exerciseStableKey,
            exerciseNameAtProcessing = exerciseName,
            directTargetKey = directTargetKey?.value,
            observationType = observationType.name,
            capacityMedianKg = capacityMedianKg,
            capacityLow80Kg = capacityLow80Kg,
            capacityHigh80Kg = capacityHigh80Kg,
            lowerBoundOnly = if (lowerBoundOnly) 1 else 0,
            logVariance = logVariance,
            directObservedLoadKg = directObservedLoadKg,
            bodyWeightKg = bodyWeightKg,
            rawAddedWeightKg = rawAddedWeightKg,
            bodyWeightSource = bodyWeightSource.name,
            curveProfileId = curveProfileId,
            curveMatchLevel = curveMatchLevel,
            curveVarianceMultiplier = curveVarianceMultiplier,
            curveSubjectKey = curveSubjectKey,
            sourceSetIdsEncoded = sourceSetIds.sorted().joinToString("|"),
            strongObservationCount = strongObservationCount,
            diagnosticsEncoded = diagnostics.sorted().joinToString("|"),
            createdAt = now
        )

    private fun dot(left: DoubleArray, right: DoubleArray): Double = left.indices.sumOf { index -> left[index] * right[index] }
    private fun quadratic(vector: DoubleArray, matrix: Array<DoubleArray>): Double = dot(vector, multiply(matrix, vector))
    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(matrix.size) { row -> matrix[row].indices.sumOf { column -> matrix[row][column] * vector[column] } }
    private fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(right[0].size) { column ->
            right.indices.sumOf { inner -> left[row][inner] * right[inner][column] }
        } }
    private fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> =
        Array(matrix[0].size) { row -> DoubleArray(matrix.size) { column -> matrix[column][row] } }
    private fun add(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(left[row].size) { column -> left[row][column] + right[row][column] } }
    private fun outer(left: DoubleArray, right: DoubleArray): Array<DoubleArray> =
        Array(left.size) { row -> DoubleArray(right.size) { column -> left[row] * right[column] } }

    private const val TARGET_INITIAL_VARIANCE = 0.16
    private const val SHARED_INITIAL_VARIANCE = 0.09
    private const val TARGET_DAILY_PROCESS_VARIANCE = 0.00008
    private const val SHARED_DAILY_PROCESS_VARIANCE = 0.00004
    private const val MAX_PROCESS_DAYS = 3650L
    private const val SHARED_LOADING_SCALE = 0.10
    private const val MIN_LOADING_SQUARED = 0.04
    private const val PROXY_VARIANCE_FLOOR = 0.08
    private const val LOWER_BOUND_VARIANCE = 0.16
    private const val MIN_VARIANCE = 1e-8
    private const val Z_50 = 0.6744897501960817
    private const val Z_80 = 1.2815515655446004
    private const val Z_95 = 1.959963984540054
}

fun PersonalCurvePosterior.toEntity(): StrengthCurvePosteriorEntity = StrengthCurvePosteriorEntity(
    curveSubjectKey = curveSubjectKey,
    canonicalProfileId = canonicalProfileId,
    thetaGridEncoded = VersionedDoubleArrayCodec.encode(thetaGrid.toDoubleArray()),
    posteriorWeightsEncoded = VersionedDoubleArrayCodec.encode(posteriorWeights.toDoubleArray()),
    totalObservationCount = totalObservationCount,
    strongObservationCount = strongObservationCount,
    distinctRepRangeCount = distinctRepRangeCount,
    minObservedReps = minObservedReps,
    maxObservedReps = maxObservedReps,
    calibrationStatus = calibrationStatus.name,
    curveVersion = curveVersion,
    posteriorFingerprint = posteriorFingerprint,
    updatedAt = updatedAt
)

fun StrengthCurvePosteriorEntity.toPosterior(): PersonalCurvePosterior = PersonalCurvePosterior(
    curveSubjectKey = curveSubjectKey,
    canonicalProfileId = canonicalProfileId,
    thetaGrid = VersionedDoubleArrayCodec.decode(thetaGridEncoded).toList(),
    posteriorWeights = VersionedDoubleArrayCodec.decode(posteriorWeightsEncoded).toList(),
    totalObservationCount = totalObservationCount,
    strongObservationCount = strongObservationCount,
    distinctRepRangeCount = distinctRepRangeCount,
    minObservedReps = minObservedReps,
    maxObservedReps = maxObservedReps,
    calibrationStatus = enumValueOf(calibrationStatus),
    curveVersion = curveVersion,
    posteriorFingerprint = posteriorFingerprint,
    updatedAt = updatedAt
)
