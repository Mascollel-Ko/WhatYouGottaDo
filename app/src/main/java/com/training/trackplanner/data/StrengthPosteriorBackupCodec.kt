package com.training.trackplanner.data

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object StrengthPosteriorBackupCodec {
    private const val FORMAT = "strength-posterior-backup-v1"

    fun encodeManifest(bootstrapMarker: String?): String = encode(listOf(bootstrapMarker))
    fun decodeManifest(payload: String): String? = decode(payload, 1)[0]

    fun encode(entity: StrengthPosteriorEventEntity): String = encode(
        listOf(
            entity.eventUuid, entity.sessionKey, entity.sessionDate, entity.completionFingerprint,
            entity.status, entity.creationReason, entity.confirmedSetCount, entity.createdAt,
            entity.processedAt, entity.modelVersion, entity.curveVersion, entity.factorSchemaVersion,
            entity.evidenceFingerprint, entity.errorCode, entity.errorMessage
        )
    )

    fun decodeEvent(payload: String): StrengthPosteriorEventEntity = decode(payload, 15).let { value ->
        StrengthPosteriorEventEntity(
            eventUuid = value.string(0),
            sessionKey = value.string(1),
            sessionDate = value.string(2),
            completionFingerprint = value.string(3),
            status = value.string(4),
            creationReason = value.string(5),
            confirmedSetCount = value.int(6),
            createdAt = value.long(7),
            processedAt = value.optionalLong(8),
            modelVersion = value.string(9),
            curveVersion = value.string(10),
            factorSchemaVersion = value.string(11),
            evidenceFingerprint = value[12],
            errorCode = value[13],
            errorMessage = value[14]
        )
    }

    fun encode(entity: StrengthPosteriorHistoryEntity): String = encode(
        listOf(
            entity.eventUuid, entity.targetKey, entity.sessionDate,
            entity.priorMedian, entity.priorLow50, entity.priorHigh50, entity.priorLow80,
            entity.priorHigh80, entity.priorLow95, entity.priorHigh95,
            entity.posteriorMedian, entity.posteriorLow50, entity.posteriorHigh50,
            entity.posteriorLow80, entity.posteriorHigh80, entity.posteriorLow95,
            entity.posteriorHigh95, entity.directObservedLoad, entity.directObservationType,
            entity.sessionObservationMedian, entity.sessionObservationLow80,
            entity.sessionObservationHigh80, entity.posteriorMeanChange,
            entity.posteriorVarianceBefore, entity.posteriorVarianceAfter,
            entity.intervalWidthChange80, entity.predictivePercentile,
            entity.standardizedSurprise, entity.modelVersion, entity.factorSchemaVersion,
            entity.curveVersion, entity.targetConfigVersion, entity.evidenceFingerprint,
            entity.sourceEvidenceStatus, entity.sourceSetCountAtProcessing,
            entity.bodyWeightKgAtProcessing, entity.rawAddedWeightKgAtProcessing,
            entity.bodyWeightSource, entity.curveProfileId, entity.curveMatchLevel,
            entity.curveCalibrationStatus, entity.createdAt
        )
    )

    fun decodeHistory(payload: String): StrengthPosteriorHistoryEntity = decode(payload, 42).let { value ->
        StrengthPosteriorHistoryEntity(
            eventUuid = value.string(0),
            targetKey = value.string(1),
            sessionDate = value.string(2),
            priorMedian = value.optionalDouble(3),
            priorLow50 = value.optionalDouble(4),
            priorHigh50 = value.optionalDouble(5),
            priorLow80 = value.optionalDouble(6),
            priorHigh80 = value.optionalDouble(7),
            priorLow95 = value.optionalDouble(8),
            priorHigh95 = value.optionalDouble(9),
            posteriorMedian = value.optionalDouble(10),
            posteriorLow50 = value.optionalDouble(11),
            posteriorHigh50 = value.optionalDouble(12),
            posteriorLow80 = value.optionalDouble(13),
            posteriorHigh80 = value.optionalDouble(14),
            posteriorLow95 = value.optionalDouble(15),
            posteriorHigh95 = value.optionalDouble(16),
            directObservedLoad = value.optionalDouble(17),
            directObservationType = value.string(18),
            sessionObservationMedian = value.optionalDouble(19),
            sessionObservationLow80 = value.optionalDouble(20),
            sessionObservationHigh80 = value.optionalDouble(21),
            posteriorMeanChange = value.optionalDouble(22),
            posteriorVarianceBefore = value.optionalDouble(23),
            posteriorVarianceAfter = value.optionalDouble(24),
            intervalWidthChange80 = value.optionalDouble(25),
            predictivePercentile = value.optionalDouble(26),
            standardizedSurprise = value.optionalDouble(27),
            modelVersion = value.string(28),
            factorSchemaVersion = value.string(29),
            curveVersion = value.string(30),
            targetConfigVersion = value.string(31),
            evidenceFingerprint = value.string(32),
            sourceEvidenceStatus = value.string(33),
            sourceSetCountAtProcessing = value.int(34),
            bodyWeightKgAtProcessing = value.optionalDouble(35),
            rawAddedWeightKgAtProcessing = value.optionalDouble(36),
            bodyWeightSource = value[37],
            curveProfileId = value[38],
            curveMatchLevel = value[39],
            curveCalibrationStatus = value[40],
            createdAt = value.long(41)
        )
    }

    fun encode(entity: StrengthPosteriorModelStateEntity): String = encode(
        listOf(
            entity.modelInstanceKey, entity.orderedFactorSchema, entity.stateMeanEncoded,
            entity.packedCovarianceEncoded, entity.stateDimension, entity.lastProcessedEventUuid,
            entity.lastProcessedDate, entity.modelVersion, entity.curveVersion,
            entity.factorSchemaVersion, entity.stateFingerprint, entity.updatedAt
        )
    )

    fun decodeModelState(payload: String): StrengthPosteriorModelStateEntity = decode(payload, 12).let { value ->
        StrengthPosteriorModelStateEntity(
            modelInstanceKey = value.string(0),
            orderedFactorSchema = value.string(1),
            stateMeanEncoded = value.string(2),
            packedCovarianceEncoded = value.string(3),
            stateDimension = value.int(4),
            lastProcessedEventUuid = value[5],
            lastProcessedDate = value[6],
            modelVersion = value.string(7),
            curveVersion = value.string(8),
            factorSchemaVersion = value.string(9),
            stateFingerprint = value.string(10),
            updatedAt = value.long(11)
        )
    }

    fun encode(entity: StrengthCurvePosteriorEntity): String = encode(
        listOf(
            entity.curveSubjectKey, entity.canonicalProfileId, entity.thetaGridEncoded,
            entity.posteriorWeightsEncoded, entity.totalObservationCount,
            entity.strongObservationCount, entity.distinctRepRangeCount, entity.minObservedReps,
            entity.maxObservedReps, entity.calibrationStatus, entity.curveVersion,
            entity.posteriorFingerprint, entity.updatedAt
        )
    )

    fun decodeCurvePosterior(payload: String): StrengthCurvePosteriorEntity = decode(payload, 13).let { value ->
        StrengthCurvePosteriorEntity(
            curveSubjectKey = value.string(0),
            canonicalProfileId = value.string(1),
            thetaGridEncoded = value.string(2),
            posteriorWeightsEncoded = value.string(3),
            totalObservationCount = value.int(4),
            strongObservationCount = value.int(5),
            distinctRepRangeCount = value.int(6),
            minObservedReps = value.optionalInt(7),
            maxObservedReps = value.optionalInt(8),
            calibrationStatus = value.string(9),
            curveVersion = value.string(10),
            posteriorFingerprint = value.string(11),
            updatedAt = value.long(12)
        )
    }

    fun encode(entity: StrengthPosteriorEvidenceEntity): String = encode(
        listOf(
            entity.evidenceFingerprint, entity.eventUuid, entity.sessionKey, entity.sessionDate,
            entity.exerciseStableKey, entity.exerciseNameAtProcessing, entity.directTargetKey,
            entity.observationType, entity.capacityMedianKg, entity.capacityLow80Kg,
            entity.capacityHigh80Kg, entity.lowerBoundOnly, entity.logVariance,
            entity.directObservedLoadKg, entity.bodyWeightKg, entity.rawAddedWeightKg,
            entity.bodyWeightSource, entity.curveProfileId, entity.curveMatchLevel,
            entity.curveVarianceMultiplier, entity.curveSubjectKey, entity.sourceSetIdsEncoded,
            entity.strongObservationCount, entity.diagnosticsEncoded, entity.createdAt
        )
    )

    fun decodeEvidence(payload: String): StrengthPosteriorEvidenceEntity = decode(payload, 25).let { value ->
        StrengthPosteriorEvidenceEntity(
            evidenceFingerprint = value.string(0),
            eventUuid = value.string(1),
            sessionKey = value.string(2),
            sessionDate = value.string(3),
            exerciseStableKey = value.string(4),
            exerciseNameAtProcessing = value.string(5),
            directTargetKey = value[6],
            observationType = value.string(7),
            capacityMedianKg = value.double(8),
            capacityLow80Kg = value.double(9),
            capacityHigh80Kg = value.double(10),
            lowerBoundOnly = value.int(11),
            logVariance = value.double(12),
            directObservedLoadKg = value.optionalDouble(13),
            bodyWeightKg = value.optionalDouble(14),
            rawAddedWeightKg = value.optionalDouble(15),
            bodyWeightSource = value.string(16),
            curveProfileId = value.string(17),
            curveMatchLevel = value.string(18),
            curveVarianceMultiplier = value.double(19),
            curveSubjectKey = value.string(20),
            sourceSetIdsEncoded = value.string(21),
            strongObservationCount = value.int(22),
            diagnosticsEncoded = value.string(23),
            createdAt = value.long(24)
        )
    }

    private fun encode(values: List<Any?>): String =
        buildList {
            add(FORMAT)
            values.forEach { value ->
                add(
                    value?.toString()?.toByteArray(StandardCharsets.UTF_8)?.let { bytes ->
                        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                    } ?: "~"
                )
            }
        }.joinToString("|")

    private fun decode(payload: String, expectedSize: Int): List<String?> {
        val tokens = payload.split('|')
        require(tokens.firstOrNull() == FORMAT) { "Unsupported strength posterior backup payload." }
        require(tokens.size == expectedSize + 1) { "Invalid strength posterior backup field count." }
        return tokens.drop(1).map { token ->
            if (token == "~") null else String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
        }
    }

    private fun List<String?>.string(index: Int): String = requireNotNull(get(index))
    private fun List<String?>.int(index: Int): Int = string(index).toInt()
    private fun List<String?>.optionalInt(index: Int): Int? = get(index)?.toInt()
    private fun List<String?>.long(index: Int): Long = string(index).toLong()
    private fun List<String?>.optionalLong(index: Int): Long? = get(index)?.toLong()
    private fun List<String?>.double(index: Int): Double = string(index).toDouble().also { require(it.isFinite()) }
    private fun List<String?>.optionalDouble(index: Int): Double? = get(index)?.toDouble()?.also { require(it.isFinite()) }
}
