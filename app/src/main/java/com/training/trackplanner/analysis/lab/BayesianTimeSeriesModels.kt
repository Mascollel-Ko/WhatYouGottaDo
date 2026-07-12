package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

internal enum class BayesianTimeSeriesModel {
    BAYESIAN_LOCAL_PROJECTION,
    BAYESIAN_VAR,
    BAYESIAN_VECM,
    UNAVAILABLE
}

internal enum class IntegrationOrder {
    I0,
    I1,
    I2_OR_HIGHER,
    INCONCLUSIVE
}

internal data class TimeSeriesAnalysisRequest(
    val xMetric: TrendMetricId,
    val yMetrics: List<TrendMetricId>,
    val controls: List<TrendMetricId>,
    val requestedHorizon: Int = 2
)

internal data class TimeSeriesAlignment(
    val weeks: List<LocalDate>,
    val valuesByMetric: Map<TrendMetricId, List<Double>>,
    val excludedMetrics: Map<TrendMetricId, String>,
    @Deprecated("Use qualitySummaries; this compatibility field only exposes raw missing cells.")
    val missingRates: Map<TrendMetricId, Double>,
    val qualitySummaries: Map<TrendMetricId, MetricDataQualitySummary> = emptyMap(),
    val preparedSeries: Map<TrendMetricId, PreparedMetricSeries> = emptyMap(),
    val grid: TimeSeriesCalendarGrid? = null,
    val rowExclusions: List<TimeSeriesRowExclusion> = emptyList()
)

internal class TimeSeriesCalendarGrid private constructor(
    val weeks: List<LocalDate>,
    val cellsByMetric: Map<TrendMetricId, List<TimeSeriesCell>>
) {
    fun cell(metric: TrendMetricId, index: Int): TimeSeriesCell? = cellsByMetric[metric]?.getOrNull(index)

    companion object {
        fun createValidated(
            weeks: List<LocalDate>,
            cellsByMetric: Map<TrendMetricId, List<TimeSeriesCell>>
        ): TimeSeriesCalendarGrid {
            require(weeks.isNotEmpty()) { "calendar grid cannot be empty" }
            require(weeks == weeks.sorted()) { "calendar weeks must be sorted" }
            require(weeks.distinct().size == weeks.size) { "calendar weeks must be unique" }
            require(weeks.all { it.dayOfWeek == DayOfWeek.MONDAY }) { "calendar weeks must use ISO Monday week starts" }
            weeks.zipWithNext().forEach { (left, right) ->
                require(left.plusWeeks(1) == right) { "calendar weeks must be exactly seven days apart" }
            }
            require(weeks.map { it.dayOfWeek }.distinct().size == 1) { "calendar week start day must be consistent" }
            cellsByMetric.forEach { (metric, cells) ->
                require(cells.size == weeks.size) { "cell count mismatch for $metric" }
                cells.forEachIndexed { index, cell ->
                    require(cell.weekStart == weeks[index]) { "cell week mismatch for $metric at $index" }
                    require(cell.metric == metric) { "cell metric mismatch for $metric at $index" }
                }
            }
            return TimeSeriesCalendarGrid(weeks, cellsByMetric)
        }
    }
}

internal data class TimeSeriesCell(
    val metric: TrendMetricId,
    val weekStart: LocalDate,
    val state: TimeSeriesCellState,
    val value: Double?,
    val missingReason: String? = null,
    val source: String? = null,
    val version: String? = null,
    val candidateCount: Int = 1,
    val sourceCells: List<TimeSeriesCellReference> = emptyList(),
    val transformation: String? = null,
    val exclusionReason: TimeSeriesRowExclusionReason? = null,
    val conflictProvenance: ObservationConflictProvenance? = null
) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "cell week must be an ISO Monday week start" }
        validateStateValue(state, value)
        if (state == TimeSeriesCellState.CONFLICT) require(conflictProvenance != null) { "conflict cell requires provenance" }
    }
}

internal enum class MetricActivationPolicy {
    EXPLICIT_METADATA_ONLY,
    FIRST_OBSERVATION_ALLOWED,
    REGISTRY_DEFINED
}

internal data class MetricLifecycleMetadata(
    val availableFromWeek: LocalDate? = null,
    val availableUntilWeek: LocalDate? = null,
    val structuralZeroAllowed: Boolean = false,
    val activationPolicy: MetricActivationPolicy = MetricActivationPolicy.EXPLICIT_METADATA_ONLY,
    val notApplicableWeeks: Set<LocalDate> = emptySet(),
    val versionDiscontinuityWeeks: Set<LocalDate> = emptySet(),
    val versionDiscontinuityRanges: List<TimeSeriesWeekRange> = emptyList(),
    val provenance: LifecycleMetadataProvenance = LifecycleMetadataProvenance()
) {
    init {
        availableFromWeek?.let { require(it.dayOfWeek == DayOfWeek.MONDAY) { "availableFromWeek must be ISO Monday" } }
        availableUntilWeek?.let { require(it.dayOfWeek == DayOfWeek.MONDAY) { "availableUntilWeek must be ISO Monday" } }
        if (availableFromWeek != null && availableUntilWeek != null) require(!availableFromWeek.isAfter(availableUntilWeek))
        require(notApplicableWeeks.all { it.dayOfWeek == DayOfWeek.MONDAY }) { "not-applicable weeks must be ISO Monday" }
        require(versionDiscontinuityWeeks.all { it.dayOfWeek == DayOfWeek.MONDAY }) { "version-discontinuity weeks must be ISO Monday" }
        versionDiscontinuityRanges.sortedWith(compareBy<TimeSeriesWeekRange> { it.startWeek }.thenBy { it.endWeek })
            .zipWithNext()
            .forEach { (left, right) ->
                require(left == right || left.endWeek.isBefore(right.startWeek)) { "version-discontinuity ranges must not overlap" }
            }
    }

    fun fingerprint(): String = stableFingerprint(
        listOf(
            availableFromWeek?.toString().orEmpty(),
            availableUntilWeek?.toString().orEmpty(),
            structuralZeroAllowed.toString(),
            activationPolicy.name,
            notApplicableWeeks.sorted().joinToString(","),
            versionDiscontinuityWeeks.sorted().joinToString(","),
            versionDiscontinuityRanges.distinct().sortedWith(compareBy<TimeSeriesWeekRange> { it.startWeek }.thenBy { it.endWeek })
                .joinToString(",") { "${it.startWeek}:${it.endWeek}" },
            provenance.source,
            provenance.sourceVersion,
            provenance.registryVersion,
            provenance.derivedFromMetric?.name.orEmpty(),
            provenance.inferencePolicy,
            provenance.metadataVersion.toString()
        )
    )
}

internal data class LifecycleMetadataProvenance(
    val source: String = "unspecified",
    val sourceVersion: String = "unspecified",
    val registryVersion: String = "unspecified",
    val derivedFromMetric: TrendMetricId? = null,
    val inferencePolicy: String = "explicit",
    val metadataVersion: Int = 1
)

internal data class TimeSeriesWeekRange(
    val startWeek: LocalDate,
    val endWeek: LocalDate
) {
    init {
        require(startWeek.dayOfWeek == DayOfWeek.MONDAY) { "range start must be ISO Monday" }
        require(endWeek.dayOfWeek == DayOfWeek.MONDAY) { "range end must be ISO Monday" }
        require(!startWeek.isAfter(endWeek)) { "range start must be before or equal to end" }
    }

    fun contains(week: LocalDate): Boolean = !week.isBefore(startWeek) && !week.isAfter(endWeek)
}

internal data class TimeSeriesObservation(
    val metric: TrendMetricId,
    val weekStart: LocalDate,
    val value: Double?,
    val state: TimeSeriesCellState? = null,
    val missingReason: String? = null,
    val source: String? = null,
    val version: String? = null,
    val revisionNumber: Long? = null,
    val observedAt: Instant? = null,
    val versionSequence: Long? = null,
    val authoritativeRevisionTime: Instant? = null,
    val revisionOrderingScheme: RevisionOrderingScheme? = null,
    val conflictProvenance: ObservationConflictProvenance? = null
) {
    init {
        if (weekStart.dayOfWeek != DayOfWeek.MONDAY) require(state == null) { "explicit observation state requires ISO Monday week start" }
        if (state != null) validateStateValue(state, value)
        if (state == null && value != null) require(value.isFinite()) { "observation value must be finite" }
        if (state == TimeSeriesCellState.CONFLICT) require(conflictProvenance != null) { "conflict observation requires provenance" }
    }

    fun revision(): ObservationRevision = ObservationRevision(
        scheme = revisionOrderingScheme ?: when {
            revisionNumber != null -> RevisionOrderingScheme.REVISION_NUMBER
            versionSequence != null -> RevisionOrderingScheme.VERSION_SEQUENCE
            authoritativeRevisionTime != null -> RevisionOrderingScheme.AUTHORITATIVE_REVISION_TIME
            else -> null
        },
        revisionNumber = revisionNumber,
        versionSequence = versionSequence,
        authoritativeRevisionTime = authoritativeRevisionTime
    )
}

internal enum class RevisionOrderingScheme {
    REVISION_NUMBER,
    VERSION_SEQUENCE,
    AUTHORITATIVE_REVISION_TIME
}

internal data class ObservationRevision(
    val scheme: RevisionOrderingScheme? = null,
    val revisionNumber: Long? = null,
    val versionSequence: Long? = null,
    val authoritativeRevisionTime: Instant? = null
) {
    init {
        when (scheme) {
            RevisionOrderingScheme.REVISION_NUMBER -> {
                require(revisionNumber != null) { "revision-number scheme requires revisionNumber" }
                require(versionSequence == null && authoritativeRevisionTime == null) { "revision-number scheme cannot mix ordering fields" }
            }
            RevisionOrderingScheme.VERSION_SEQUENCE -> {
                require(versionSequence != null) { "version-sequence scheme requires versionSequence" }
                require(revisionNumber == null && authoritativeRevisionTime == null) { "version-sequence scheme cannot mix ordering fields" }
            }
            RevisionOrderingScheme.AUTHORITATIVE_REVISION_TIME -> {
                require(authoritativeRevisionTime != null) { "authoritative-time scheme requires authoritativeRevisionTime" }
                require(revisionNumber == null && versionSequence == null) { "authoritative-time scheme cannot mix ordering fields" }
            }
            null -> require(revisionNumber == null && versionSequence == null && authoritativeRevisionTime == null) {
                "revision ordering fields require an explicit scheme"
            }
        }
    }

    fun isOrdered(): Boolean = scheme != null

    fun orderValue(): Long? = when (scheme) {
        RevisionOrderingScheme.REVISION_NUMBER -> revisionNumber
        RevisionOrderingScheme.VERSION_SEQUENCE -> versionSequence
        RevisionOrderingScheme.AUTHORITATIVE_REVISION_TIME -> authoritativeRevisionTime?.toEpochMilli()
        null -> null
    }
}

internal data class ObservationCandidateProvenance(
    val metric: TrendMetricId,
    val weekStart: LocalDate,
    val value: Double?,
    val state: TimeSeriesCellState?,
    val source: String?,
    val revision: ObservationRevision,
    val version: String?
)

internal data class ObservationConflictProvenance(
    val candidates: List<ObservationCandidateProvenance>,
    val selectedCandidate: ObservationCandidateProvenance?,
    val selectionRule: ObservationConflictSelectionRule,
    val identicalCandidates: Boolean,
    val unresolvedConflict: Boolean
) {
    init {
        require(candidates.isNotEmpty()) { "conflict provenance requires candidates" }
    }
}

internal enum class ObservationConflictSelectionRule {
    IDENTICAL_DUPLICATE_MERGE,
    TYPED_REVISION_ORDER,
    REVISION_NUMBER_ORDER,
    VERSION_SEQUENCE_ORDER,
    AUTHORITATIVE_REVISION_TIME_ORDER,
    HETEROGENEOUS_REVISION_SCHEME_CONFLICT,
    PARTIAL_REVISION_METADATA_CONFLICT,
    TIED_HIGHEST_REVISION_CONFLICT,
    UNRESOLVED_CONFLICT
}

internal enum class TimeSeriesCellState {
    OBSERVED_VALUE,
    STRUCTURAL_ZERO,
    MISSING,
    NOT_APPLICABLE,
    PRE_METRIC_CREATION,
    VERSION_DISCONTINUITY,
    CONFLICT
}

internal data class TimeSeriesRowExclusion(
    val targetWeek: LocalDate,
    val sourceWeek: LocalDate?,
    val lagWeeks: List<LocalDate>,
    val horizon: Int,
    val reason: TimeSeriesRowExclusionReason,
    val cellReferences: List<TimeSeriesCellReference>,
    val diagnostics: List<String> = emptyList()
)

internal data class TimeSeriesCellReference(
    val role: TimeSeriesCellRole,
    val lagOrder: Int?,
    val metric: TrendMetricId,
    val week: LocalDate?,
    val state: TimeSeriesCellState?,
    val valuePresent: Boolean
)

internal enum class TimeSeriesCellRole {
    SOURCE,
    TARGET,
    LAG
}

internal class MetricDataQualitySummary private constructor(
    val totalWeeks: Int,
    val observedCount: Int,
    val structuralZeroCount: Int,
    val missingCount: Int,
    val preMetricCreationCount: Int,
    val notApplicableCount: Int,
    val versionDiscontinuityCount: Int,
    val conflictCount: Int,
    val transformationFailureCount: Int = 0,
    val modelEligibleWeekCount: Int,
    val usableCount: Int,
    val unusableCount: Int,
    val rawMissingRate: Double,
    val unusableRate: Double,
    val coverageRate: Double,
    val longestContiguousUsableRun: Int,
    val contiguousSegmentCount: Int
) {
    override fun equals(other: Any?): Boolean =
        other is MetricDataQualitySummary &&
            totalWeeks == other.totalWeeks &&
            observedCount == other.observedCount &&
            structuralZeroCount == other.structuralZeroCount &&
            missingCount == other.missingCount &&
            preMetricCreationCount == other.preMetricCreationCount &&
            notApplicableCount == other.notApplicableCount &&
            versionDiscontinuityCount == other.versionDiscontinuityCount &&
            conflictCount == other.conflictCount &&
            transformationFailureCount == other.transformationFailureCount &&
            modelEligibleWeekCount == other.modelEligibleWeekCount &&
            usableCount == other.usableCount &&
            unusableCount == other.unusableCount &&
            rawMissingRate == other.rawMissingRate &&
            unusableRate == other.unusableRate &&
            coverageRate == other.coverageRate &&
            longestContiguousUsableRun == other.longestContiguousUsableRun &&
            contiguousSegmentCount == other.contiguousSegmentCount

    override fun hashCode(): Int {
        var result = totalWeeks
        result = 31 * result + observedCount
        result = 31 * result + structuralZeroCount
        result = 31 * result + missingCount
        result = 31 * result + preMetricCreationCount
        result = 31 * result + notApplicableCount
        result = 31 * result + versionDiscontinuityCount
        result = 31 * result + conflictCount
        result = 31 * result + transformationFailureCount
        result = 31 * result + modelEligibleWeekCount
        result = 31 * result + usableCount
        result = 31 * result + unusableCount
        result = 31 * result + rawMissingRate.hashCode()
        result = 31 * result + unusableRate.hashCode()
        result = 31 * result + coverageRate.hashCode()
        result = 31 * result + longestContiguousUsableRun
        result = 31 * result + contiguousSegmentCount
        return result
    }

    companion object {
        fun fromCells(cells: List<TimeSeriesCell>): MetricDataQualitySummary {
            val observed = cells.count { it.state == TimeSeriesCellState.OBSERVED_VALUE }
            val structuralZero = cells.count { it.state == TimeSeriesCellState.STRUCTURAL_ZERO }
            val missing = cells.count { it.state == TimeSeriesCellState.MISSING }
            val preCreation = cells.count { it.state == TimeSeriesCellState.PRE_METRIC_CREATION }
            val notApplicable = cells.count { it.state == TimeSeriesCellState.NOT_APPLICABLE }
            val versionDiscontinuity = cells.count { it.state == TimeSeriesCellState.VERSION_DISCONTINUITY }
            val conflict = cells.count { it.state == TimeSeriesCellState.CONFLICT }
            val transformationFailure = cells.count { it.hasTransformationFailure() }
            val eligibleCells = cells.filter(TimeSeriesCell::isModelEligible)
            val modelEligible = eligibleCells.size
            val usable = eligibleCells.count(TimeSeriesCell::isModelUsable)
            val unusable = modelEligible - usable
            val segments = contiguousUsableSegments(cells)
            val denominator = modelEligible.coerceAtLeast(1)
            return MetricDataQualitySummary(
                totalWeeks = cells.size,
                observedCount = observed,
                structuralZeroCount = structuralZero,
                missingCount = missing,
                preMetricCreationCount = preCreation,
                notApplicableCount = notApplicable,
                versionDiscontinuityCount = versionDiscontinuity,
                conflictCount = conflict,
                transformationFailureCount = transformationFailure,
                modelEligibleWeekCount = modelEligible,
                usableCount = usable,
                unusableCount = unusable,
                rawMissingRate = missing.toDouble() / denominator,
                unusableRate = unusable.toDouble() / denominator,
                coverageRate = usable.toDouble() / denominator,
                longestContiguousUsableRun = segments.maxOfOrNull { it.length } ?: 0,
                contiguousSegmentCount = segments.size
            )
        }
    }
}

internal data class ContiguousUsableSegment(
    val startWeek: LocalDate,
    val endWeek: LocalDate,
    val length: Int
)

internal class PreparedMetricSeries private constructor(
    val metric: TrendMetricId,
    val weeks: List<LocalDate>,
    val cells: List<TimeSeriesCell>,
    val transformation: String,
    val qualitySummary: MetricDataQualitySummary,
    val lifecycleMetadata: MetricLifecycleMetadata,
    val contiguousSegments: List<ContiguousUsableSegment>,
    val provenance: List<String>,
    val preparationVersion: Int = 1
) {
    companion object {
        fun createValidated(
            metric: TrendMetricId,
            weeks: List<LocalDate>,
            cells: List<TimeSeriesCell>,
            transformation: String,
            lifecycleMetadata: MetricLifecycleMetadata,
            provenance: List<String>,
            preparationVersion: Int = 1
        ): PreparedMetricSeries {
            require(weeks.isNotEmpty()) { "prepared series weeks cannot be empty" }
            require(weeks.all { it.dayOfWeek == DayOfWeek.MONDAY }) { "prepared weeks must be ISO Mondays" }
            weeks.zipWithNext().forEach { (left, right) -> require(left.plusWeeks(1) == right) { "prepared weeks must be continuous" } }
            require(cells.size == weeks.size) { "prepared cells must match weeks" }
            cells.forEachIndexed { index, cell ->
                require(cell.metric == metric) { "prepared cell metric mismatch" }
                require(cell.weekStart == weeks[index]) { "prepared cell week mismatch" }
                if (cell.state == TimeSeriesCellState.CONFLICT) require(cell.conflictProvenance != null) { "conflict cell requires provenance" }
                if (cell.transformation != null) require(cell.sourceCells.isNotEmpty()) { "transformed cell requires source-cell provenance" }
            }
            validateLifecycleSemantics(cells, lifecycleMetadata)
            val summary = MetricDataQualitySummary.fromCells(cells)
            val segments = contiguousUsableSegments(cells)
            return PreparedMetricSeries(
                metric = metric,
                weeks = weeks.toList(),
                cells = cells.toList(),
                transformation = transformation,
                qualitySummary = summary,
                lifecycleMetadata = lifecycleMetadata,
                contiguousSegments = segments,
                provenance = provenance + listOf(
                    "quality summary derived from cells",
                    "contiguous segments derived from cells",
                    "lifecycleFingerprint=${lifecycleMetadata.fingerprint()}"
                ),
                preparationVersion = preparationVersion
            )
        }

        private fun validateLifecycleSemantics(
            cells: List<TimeSeriesCell>,
            lifecycleMetadata: MetricLifecycleMetadata
        ) {
            val activationWeek = lifecycleMetadata.availableFromWeek
                ?: cells.firstOrNull { cell ->
                    lifecycleMetadata.activationPolicy == MetricActivationPolicy.FIRST_OBSERVATION_ALLOWED &&
                        cell.state in setOf(TimeSeriesCellState.OBSERVED_VALUE, TimeSeriesCellState.STRUCTURAL_ZERO)
                }?.weekStart
            cells.forEach { cell ->
                val afterAvailability = lifecycleMetadata.availableUntilWeek?.let { cell.weekStart.isAfter(it) } == true
                val notApplicable = cell.weekStart in lifecycleMetadata.notApplicableWeeks || afterAvailability
                val versionDiscontinuous = cell.weekStart in lifecycleMetadata.versionDiscontinuityWeeks ||
                    lifecycleMetadata.versionDiscontinuityRanges.any { it.contains(cell.weekStart) }
                val derivedFromSameLifecycleState = cell.sourceCells.any { it.state == cell.state }
                when (cell.state) {
                    TimeSeriesCellState.STRUCTURAL_ZERO -> {
                        require(lifecycleMetadata.structuralZeroAllowed) { "LIFECYCLE_CELL_INCONSISTENCY: structural zero is not allowed" }
                        require(activationWeek != null && !cell.weekStart.isBefore(activationWeek)) { "LIFECYCLE_CELL_INCONSISTENCY: structural zero before activation" }
                        require(!notApplicable && !versionDiscontinuous) { "LIFECYCLE_CELL_INCONSISTENCY: structural zero in inactive range" }
                    }
                    TimeSeriesCellState.PRE_METRIC_CREATION -> {
                        require((activationWeek != null && cell.weekStart.isBefore(activationWeek)) || derivedFromSameLifecycleState) {
                            "LIFECYCLE_CELL_INCONSISTENCY: pre-creation cell outside pre-activation range"
                        }
                    }
                    TimeSeriesCellState.NOT_APPLICABLE -> {
                        require(notApplicable || derivedFromSameLifecycleState) { "LIFECYCLE_CELL_INCONSISTENCY: not-applicable cell lacks metadata" }
                    }
                    TimeSeriesCellState.VERSION_DISCONTINUITY -> {
                        require(versionDiscontinuous || derivedFromSameLifecycleState) { "LIFECYCLE_CELL_INCONSISTENCY: discontinuity cell lacks metadata" }
                    }
                    TimeSeriesCellState.OBSERVED_VALUE -> {
                        require(activationWeek == null || !cell.weekStart.isBefore(activationWeek)) { "LIFECYCLE_CELL_INCONSISTENCY: observed value before activation" }
                        require(!notApplicable && !versionDiscontinuous) { "LIFECYCLE_CELL_INCONSISTENCY: observed value in inactive range" }
                    }
                    TimeSeriesCellState.CONFLICT -> {
                        require(cell.conflictProvenance != null) { "LIFECYCLE_CELL_INCONSISTENCY: conflict cell lacks provenance" }
                    }
                    TimeSeriesCellState.MISSING -> {
                        require(activationWeek == null || !cell.weekStart.isBefore(activationWeek)) { "LIFECYCLE_CELL_INCONSISTENCY: missing cell before activation" }
                        require(!notApplicable && !versionDiscontinuous) { "LIFECYCLE_CELL_INCONSISTENCY: missing cell in inactive range" }
                    }
                }
            }
        }
    }
}

internal enum class PreparedTimeSeriesRowPolicy {
    COMMON_USABLE_ROWS
}

internal enum class SeriesTransformation(val id: String) {
    LEVEL("level"),
    FIRST_DIFFERENCE("first difference"),
    EXCLUDED("excluded")
}

internal enum class IntegrationDiagnosticSource {
    AUTOMATIC_INTEGRATION_DIAGNOSTIC
}

internal enum class InconclusiveTransformationPolicy {
    EXCLUDE_CANDIDATE,
    USE_DOCUMENTED_FALLBACK
}

internal data class MetricTransformationPlan(
    val metric: TrendMetricId,
    val integrationOrder: IntegrationOrder,
    val transformation: SeriesTransformation,
    val diagnosticSource: IntegrationDiagnosticSource,
    val decisionReason: String,
    val planVersion: String = TRANSFORMATION_PLAN_VERSION
)

internal data class TimeSeriesTransformationPlan(
    val plansByMetric: Map<TrendMetricId, MetricTransformationPlan>,
    val diagnostics: Map<TrendMetricId, IntegrationDiagnostic>,
    val planFingerprint: String = stableFingerprint(
        plansByMetric.toSortedMap(compareBy { it.name }).map { (metric, plan) ->
            listOf(metric.name, plan.integrationOrder.name, plan.transformation.id, plan.decisionReason, plan.planVersion).joinToString(":")
        }
    ),
    val planVersion: String = TRANSFORMATION_PLAN_VERSION
)

internal data class PreparedCandidateCatalog(
    val weeks: List<LocalDate>,
    val preparedSeriesByMetric: Map<TrendMetricId, PreparedMetricSeries>,
    val transformationPlan: TimeSeriesTransformationPlan,
    val excludedCandidates: Map<TrendMetricId, CandidateExclusion>,
    val preparationFingerprint: String,
    val preparationVersion: String = PREPARED_CANDIDATE_CATALOG_VERSION
)

internal data class CandidateExclusion(
    val metric: TrendMetricId,
    val reason: String
)

internal enum class TimeSeriesVariableRole {
    SHOCK_SOURCE,
    ENDOGENOUS_STATE,
    RESPONSE,
    CONTEMPORANEOUS_CONTROL,
    LAGGED_CONTROL
}

internal data class VariableRowRequirement(
    val metric: TrendMetricId,
    val roles: Set<TimeSeriesVariableRole>,
    val requireSourceValue: Boolean,
    val requiredLagOffsets: Set<Int>,
    val requiredTargetOffsets: Set<Int>,
    val requireShockEstimationRows: Boolean = false
) {
    init {
        require(roles.isNotEmpty()) { "ROLE_REQUIREMENT_MISSING: variable role required" }
        require(requiredLagOffsets.all { it > 0 }) { "lag offsets must be positive" }
        require(requiredTargetOffsets.all { it >= 0 }) { "target offsets must be non-negative" }
    }

    fun fingerprintPart(): String = listOf(
        metric.name,
        roles.map(TimeSeriesVariableRole::name).sorted().joinToString("|"),
        requireSourceValue.toString(),
        requiredLagOffsets.sorted().joinToString("|"),
        requiredTargetOffsets.sorted().joinToString("|"),
        requireShockEstimationRows.toString()
    ).joinToString(":")
}

internal enum class HorizonSelectionPolicy {
    REFERENCE_HORIZON
}

internal enum class RowComparisonPolicy {
    COMMON_USABLE_ROWS
}

internal data class PreparedRowSpecification(
    val orderedRequirements: List<VariableRowRequirement>,
    val lag: Int,
    val requestedHorizons: Set<Int>,
    val horizonPolicy: HorizonSelectionPolicy,
    val rowComparisonPolicy: RowComparisonPolicy,
    val preparationVersion: String = PREPARED_ROW_SPECIFICATION_VERSION,
    val specificationFingerprint: String = stableFingerprint(
        listOf(
            orderedRequirements.sortedBy { it.metric.name }.joinToString(",") { it.fingerprintPart() },
            lag.toString(),
            requestedHorizons.sorted().joinToString(","),
            horizonPolicy.name,
            rowComparisonPolicy.name,
            preparationVersion
        )
    )
)

internal data class PreparedTimeSeriesRowIdentity(
    val sourceWeek: LocalDate,
    val targetWeek: LocalDate,
    val lagWeeks: List<LocalDate>,
    val metricSet: List<TrendMetricId>,
    val requirements: List<VariableRowRequirement> = emptyList(),
    val transformations: Map<TrendMetricId, String>,
    val lag: Int,
    val horizon: Int,
    val requestedHorizons: Set<Int> = setOf(horizon),
    val horizonPolicy: HorizonSelectionPolicy = HorizonSelectionPolicy.REFERENCE_HORIZON,
    val preparationVersion: Int,
    val rowPolicy: PreparedTimeSeriesRowPolicy,
    val rowComparisonPolicy: RowComparisonPolicy = RowComparisonPolicy.COMMON_USABLE_ROWS,
    val fingerprint: String
)

internal class PreparedTimeSeriesSystem private constructor(
    val weeks: List<LocalDate>,
    val seriesByMetric: Map<TrendMetricId, PreparedMetricSeries>,
    val orderedMetrics: List<TrendMetricId>,
    val commonUsableRows: List<PreparedTimeSeriesRowIdentity>,
    val rowPolicy: PreparedTimeSeriesRowPolicy,
    val rowSpecification: PreparedRowSpecification,
    val preparationVersion: Int,
    val preparationFingerprint: String,
    val diagnostics: List<String>
) {
    companion object {
        fun createValidated(
            orderedMetrics: List<TrendMetricId>,
            preparedSeries: Map<TrendMetricId, PreparedMetricSeries>,
            lag: Int,
            horizon: Int,
            rowPolicy: PreparedTimeSeriesRowPolicy = PreparedTimeSeriesRowPolicy.COMMON_USABLE_ROWS,
            rowRequirements: List<VariableRowRequirement>? = null,
            requestedHorizons: Set<Int> = setOf(horizon),
            horizonPolicy: HorizonSelectionPolicy = HorizonSelectionPolicy.REFERENCE_HORIZON,
            rowComparisonPolicy: RowComparisonPolicy = RowComparisonPolicy.COMMON_USABLE_ROWS
        ): PreparedTimeSeriesSystem {
            require(orderedMetrics.isNotEmpty()) { "prepared system requires metrics" }
            require(lag >= 0) { "lag must be non-negative" }
            require(horizon >= 0) { "horizon must be non-negative" }
            require(horizon in requestedHorizons) { "HORIZON_POLICY_MISMATCH: requested horizons must include the reference horizon" }
            val uniqueMetrics = orderedMetrics.distinct().sortedBy { it.name }
            val selected = uniqueMetrics.associateWith { metric ->
                preparedSeries[metric] ?: error("prepared series missing for $metric")
            }
            val weeks = selected.values.first().weeks
            val version = selected.values.first().preparationVersion
            selected.forEach { (metric, series) ->
                require(series.weeks == weeks) { "prepared system week vector mismatch for $metric" }
                require(series.preparationVersion == version) { "prepared system preparation version mismatch for $metric" }
            }
            val transformations = selected.mapValues { (_, series) -> series.transformation }
            val requirements = (rowRequirements ?: uniqueMetrics.map { metric ->
                VariableRowRequirement(
                    metric = metric,
                    roles = setOf(TimeSeriesVariableRole.ENDOGENOUS_STATE, TimeSeriesVariableRole.RESPONSE),
                    requireSourceValue = true,
                    requiredLagOffsets = (1..lag).toSet(),
                    requiredTargetOffsets = setOf(horizon)
                )
            }).sortedBy { it.metric.name }
            val requirementMetrics = requirements.map { it.metric }.toSet()
            require(uniqueMetrics.all { it in requirementMetrics }) { "ROLE_REQUIREMENT_MISSING: every metric needs a row requirement" }
            val rowSpecification = PreparedRowSpecification(
                orderedRequirements = requirements,
                lag = lag,
                requestedHorizons = requestedHorizons,
                horizonPolicy = horizonPolicy,
                rowComparisonPolicy = rowComparisonPolicy
            )
            val rows = weeks.indices.mapNotNull { index ->
                val targetIndex = index + horizon
                if (index < lag || targetIndex !in weeks.indices) return@mapNotNull null
                val lagIndices = (1..lag).map { index - it }
                val included = requirements.all { requirement -> selected.getValue(requirement.metric).satisfies(requirement, index) }
                if (!included) return@mapNotNull null
                val lagWeeks = lagIndices.map { weeks[it] }
                val keyParts = listOf(
                    weeks[index].toString(),
                    weeks[targetIndex].toString(),
                    lagWeeks.joinToString(","),
                    uniqueMetrics.joinToString(",") { it.name },
                    requirements.joinToString(",") { it.fingerprintPart() },
                    transformations.toSortedMap(compareBy { it.name }).entries.joinToString(",") { "${it.key.name}:${it.value}" },
                    lag.toString(),
                    requestedHorizons.sorted().joinToString(","),
                    horizonPolicy.name,
                    version.toString(),
                    rowPolicy.name,
                    rowComparisonPolicy.name,
                    rowSpecification.specificationFingerprint
                )
                PreparedTimeSeriesRowIdentity(
                    sourceWeek = weeks[index],
                    targetWeek = weeks[targetIndex],
                    lagWeeks = lagWeeks,
                    metricSet = uniqueMetrics,
                    requirements = requirements,
                    transformations = transformations,
                    lag = lag,
                    horizon = horizon,
                    requestedHorizons = requestedHorizons,
                    horizonPolicy = horizonPolicy,
                    preparationVersion = version,
                    rowPolicy = rowPolicy,
                    rowComparisonPolicy = rowComparisonPolicy,
                    fingerprint = stableFingerprint(keyParts)
                )
            }
            val systemFingerprint = stableFingerprint(
                listOf(
                    weeks.joinToString(","),
                    uniqueMetrics.joinToString(",") { it.name },
                    transformations.toSortedMap(compareBy { it.name }).entries.joinToString(",") { "${it.key.name}:${it.value}" },
                    rowSpecification.specificationFingerprint,
                    rows.joinToString(",") { it.fingerprint },
                    version.toString(),
                    rowPolicy.name
                )
            )
            return PreparedTimeSeriesSystem(
                weeks = weeks,
                seriesByMetric = selected,
                orderedMetrics = uniqueMetrics,
                commonUsableRows = rows,
                rowPolicy = rowPolicy,
                rowSpecification = rowSpecification,
                preparationVersion = version,
                preparationFingerprint = systemFingerprint,
                diagnostics = listOf("common usable rows derived from prepared cells: ${rows.size}")
            )
        }

        private fun PreparedMetricSeries.satisfies(requirement: VariableRowRequirement, sourceIndex: Int): Boolean {
            if (requirement.requireSourceValue && !cells[sourceIndex].isModelUsable()) return false
            if (requirement.requiredLagOffsets.any { offset -> cells.getOrNull(sourceIndex - offset)?.isModelUsable() != true }) return false
            if (requirement.requiredTargetOffsets.any { offset -> cells.getOrNull(sourceIndex + offset)?.isModelUsable() != true }) return false
            return true
        }
    }
}

internal data class TimeSeriesModelRow(
    val targetWeek: LocalDate,
    val sourceWeek: LocalDate,
    val lagWeeks: List<LocalDate>,
    val horizon: Int,
    val target: Double,
    val source: Double,
    val lags: List<Double>
)

internal enum class TimeSeriesRowExclusionReason {
    MISSING_TARGET,
    MISSING_SOURCE,
    MISSING_LAG,
    MISSING_HORIZON,
    SOURCE_BEFORE_REQUIRED_LAGS,
    TARGET_OUTSIDE_GRID,
    DISCONTINUOUS_LAG,
    DISCONTINUOUS_DIFFERENCE,
    DISCONTINUOUS_HORIZON,
    PRE_METRIC_CREATION,
    VERSION_DISCONTINUITY,
    NOT_APPLICABLE,
    CONFLICT,
    STRUCTURAL_ZERO_NOT_ALLOWED,
    INVALID_CELL_STATE
}

internal data class IntegrationDiagnostic(
    val metric: TrendMetricId,
    val levelOrder: IntegrationOrder,
    val adfLevelStatistic: Double,
    val kpssLevelStatistic: Double,
    val adfDifferenceStatistic: Double?,
    val kpssDifferenceStatistic: Double?,
    val message: String
)

internal const val TRANSFORMATION_PLAN_VERSION = "phase-a-transformation-plan-v1"
internal const val PREPARED_CANDIDATE_CATALOG_VERSION = "phase-a-prepared-candidate-catalog-v1"
internal const val PREPARED_ROW_SPECIFICATION_VERSION = "phase-a-row-spec-v1"

internal data class BayesianLagPosterior(
    val probabilities: Map<Int, Double>,
    val selectedLag: Int?,
    val modelAveraged: Boolean
)

internal data class CointegrationDiagnostic(
    val legacySuggestedRank: Int?,
    val legacyHeuristicScore: Double,
    val legacyRankOneStatistic: Double?,
    val isSupported: Boolean,
    val message: String,
    val cointegrationVector: List<Double>? = null,
    val diagnostics: List<String> = emptyList(),
    val diagnosticOnly: Boolean = true,
    val supportedForModelRouting: Boolean = false,
    val method: CointegrationDiagnosticMethod = CointegrationDiagnosticMethod.LEGACY_RANK_ONE_HEURISTIC
)

internal enum class CointegrationDiagnosticMethod {
    LEGACY_RANK_ONE_HEURISTIC
}

private fun validateStateValue(state: TimeSeriesCellState, value: Double?) {
    when (state) {
        TimeSeriesCellState.OBSERVED_VALUE -> require(value != null && value.isFinite()) { "observed value must be finite" }
        TimeSeriesCellState.STRUCTURAL_ZERO -> require(value == 0.0) { "structural zero must have value 0.0" }
        TimeSeriesCellState.MISSING,
        TimeSeriesCellState.NOT_APPLICABLE,
        TimeSeriesCellState.PRE_METRIC_CREATION,
        TimeSeriesCellState.VERSION_DISCONTINUITY,
        TimeSeriesCellState.CONFLICT -> require(value == null) { "$state cannot carry a value" }
    }
}

internal fun TimeSeriesCell.isModelEligible(): Boolean =
    state !in setOf(TimeSeriesCellState.PRE_METRIC_CREATION, TimeSeriesCellState.NOT_APPLICABLE)

internal fun TimeSeriesCell.isModelUsable(): Boolean =
    state == TimeSeriesCellState.OBSERVED_VALUE || state == TimeSeriesCellState.STRUCTURAL_ZERO

internal fun TimeSeriesCell.hasTransformationFailure(): Boolean =
    missingReason?.startsWith("transformation unavailable") == true

internal fun contiguousUsableSegments(cells: List<TimeSeriesCell>): List<ContiguousUsableSegment> {
    val segments = mutableListOf<ContiguousUsableSegment>()
    var start: LocalDate? = null
    var previous: LocalDate? = null
    var length = 0
    fun flush() {
        val s = start
        val p = previous
        if (s != null && p != null) segments += ContiguousUsableSegment(s, p, length)
        start = null
        previous = null
        length = 0
    }
    cells.forEach { cell ->
        if (cell.isModelUsable()) {
            if (start == null) start = cell.weekStart
            previous = cell.weekStart
            length++
        } else {
            flush()
        }
    }
    flush()
    return segments
}

internal fun stableFingerprint(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        digest.update(part.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class AutomaticEndogenousSelection(
    val metrics: List<TrendMetricId>,
    val diagnostics: List<String>
)

internal data class CholeskySensitivityDiagnostic(
    val isOrderSensitive: Boolean,
    val message: String
)

internal data class BayesianIrfPoint(
    val horizonWeeks: Int,
    val estimate: Double,
    val low80: Double,
    val high80: Double,
    val observations: Int
)

internal data class BayesianResponseIrf(
    val yMetric: TrendMetricId,
    val points: List<BayesianIrfPoint>
)

internal data class BayesianTimeSeriesResult(
    val request: TimeSeriesAnalysisRequest,
    val model: BayesianTimeSeriesModel,
    val responses: List<BayesianResponseIrf>,
    val usedHorizon: Int,
    val alignment: TimeSeriesAlignment?,
    val integrationDiagnostics: List<IntegrationDiagnostic>,
    val cointegration: CointegrationDiagnostic?,
    val lagPosterior: BayesianLagPosterior?,
    val automaticEndogenous: List<TrendMetricId>,
    val automaticSelectionDiagnostics: List<String>,
    val choleskyOrder: List<TrendMetricId>,
    val choleskySensitivity: CholeskySensitivityDiagnostic?,
    val transformations: Map<TrendMetricId, String>,
    val confidence: AnalysisConfidence,
    val warnings: List<String>,
    val summary: String
)
