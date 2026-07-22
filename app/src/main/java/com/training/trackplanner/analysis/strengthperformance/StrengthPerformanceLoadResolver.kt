package com.training.trackplanner.analysis.strengthperformance

import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BodyWeightSource {
    EXACT_DATE,
    MOST_RECENT_PRIOR,
    INITIAL_PROFILE,
    UNRESOLVED
}

enum class LoadResolutionConfidence {
    HIGH,
    MODERATE,
    LOW,
    UNRESOLVED
}

data class ResolvedStrengthLoad(
    val totalLoadKg: Double?,
    val rawAddedWeightKg: Double,
    val resolvedBodyWeightKg: Double?,
    val bodyWeightSource: BodyWeightSource,
    val bodyWeightAgeDays: Long?,
    val loadVarianceContribution: Double,
    val confidence: LoadResolutionConfidence,
    val semantics: StrengthLoadSemantics
) {
    val isResolved: Boolean get() = totalLoadKg?.let { value -> value.isFinite() && value > 0.0 } == true
}

class StrengthPerformanceLoadResolver(
    dailyMetrics: List<DailyMetric>,
    dailyCheckIns: List<DailyCheckIn>,
    private val initialProfile: InitialUserProfile?
) {
    private val bodyWeightsByDate: Map<LocalDate, Double> = buildMap {
        dailyMetrics.sortedBy(DailyMetric::updatedAt).forEach { metric ->
            metric.validBodyWeight()?.let { weight -> parseDate(metric.date)?.let { date -> put(date, weight) } }
        }
        dailyCheckIns.sortedBy(DailyCheckIn::updatedAt).forEach { checkIn ->
            checkIn.validBodyWeight()?.let { weight -> parseDate(checkIn.date)?.let { date -> put(date, weight) } }
        }
    }

    fun resolve(date: LocalDate, set: WorkoutSet, semantics: StrengthLoadSemantics): ResolvedStrengthLoad {
        val rawLoad = set.weightKg.takeIf { value -> value.isFinite() && value >= 0.0 }
            ?: return unresolved(set.weightKg, semantics)
        if (semantics in EXTERNAL_SEMANTICS) {
            return ResolvedStrengthLoad(
                totalLoadKg = rawLoad.takeIf { value -> value > 0.0 },
                rawAddedWeightKg = rawLoad,
                resolvedBodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNRESOLVED,
                bodyWeightAgeDays = null,
                loadVarianceContribution = EXTERNAL_LOAD_LOG_VARIANCE,
                confidence = if (rawLoad > 0.0) LoadResolutionConfidence.HIGH else LoadResolutionConfidence.UNRESOLVED,
                semantics = semantics
            )
        }
        val bodyWeight = resolveBodyWeight(date) ?: return unresolved(rawLoad, semantics)
        val total = when (semantics) {
            StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD -> bodyWeight.kg + rawLoad
            StrengthLoadSemantics.BODYWEIGHT_MINUS_ASSISTANCE -> bodyWeight.kg - rawLoad
            StrengthLoadSemantics.BODYWEIGHT_FRACTION_PLUS_ADDED_LOAD -> bodyWeight.kg + rawLoad
            else -> error("Unexpected bodyweight load semantics: $semantics")
        }
        return ResolvedStrengthLoad(
            totalLoadKg = total.takeIf { value -> value.isFinite() && value > 0.0 },
            rawAddedWeightKg = rawLoad,
            resolvedBodyWeightKg = bodyWeight.kg,
            bodyWeightSource = bodyWeight.source,
            bodyWeightAgeDays = bodyWeight.ageDays,
            loadVarianceContribution = bodyWeight.variance,
            confidence = bodyWeight.confidence,
            semantics = semantics
        )
    }

    fun resolveBodyWeight(date: LocalDate): ResolvedBodyWeight? {
        bodyWeightsByDate[date]?.let { value ->
            return ResolvedBodyWeight(
                kg = value,
                source = BodyWeightSource.EXACT_DATE,
                ageDays = 0,
                variance = EXACT_BODYWEIGHT_LOG_VARIANCE,
                confidence = LoadResolutionConfidence.HIGH
            )
        }
        val prior = bodyWeightsByDate.entries.filter { (recordedDate, _) -> !recordedDate.isAfter(date) }
            .maxByOrNull { (recordedDate, _) -> recordedDate }
        if (prior != null) {
            val ageDays = ChronoUnit.DAYS.between(prior.key, date).coerceAtLeast(0)
            return ResolvedBodyWeight(
                kg = prior.value,
                source = BodyWeightSource.MOST_RECENT_PRIOR,
                ageDays = ageDays,
                variance = (PRIOR_BODYWEIGHT_BASE_SD + ageDays.coerceAtMost(365) * PRIOR_BODYWEIGHT_DAILY_SD).let { it * it },
                confidence = if (ageDays <= 30) LoadResolutionConfidence.MODERATE else LoadResolutionConfidence.LOW
            )
        }
        return initialProfile?.bodyWeightKg?.takeIf { value -> value.isFinite() && value > 0.0 }?.let { value ->
            ResolvedBodyWeight(
                kg = value,
                source = BodyWeightSource.INITIAL_PROFILE,
                ageDays = null,
                variance = PROFILE_BODYWEIGHT_LOG_VARIANCE,
                confidence = LoadResolutionConfidence.LOW
            )
        }
    }

    private fun unresolved(rawLoad: Double, semantics: StrengthLoadSemantics) = ResolvedStrengthLoad(
        totalLoadKg = null,
        rawAddedWeightKg = rawLoad,
        resolvedBodyWeightKg = null,
        bodyWeightSource = BodyWeightSource.UNRESOLVED,
        bodyWeightAgeDays = null,
        loadVarianceContribution = 1.0,
        confidence = LoadResolutionConfidence.UNRESOLVED,
        semantics = semantics
    )

    data class ResolvedBodyWeight(
        val kg: Double,
        val source: BodyWeightSource,
        val ageDays: Long?,
        val variance: Double,
        val confidence: LoadResolutionConfidence
    )

    private companion object {
        val EXTERNAL_SEMANTICS = setOf(
            StrengthLoadSemantics.EXTERNAL_LOAD,
            StrengthLoadSemantics.MACHINE_STACK_LOAD,
            StrengthLoadSemantics.IMPLEMENT_TOTAL_LOAD
        )
        const val EXTERNAL_LOAD_LOG_VARIANCE = 0.0001
        const val EXACT_BODYWEIGHT_LOG_VARIANCE = 0.0004
        const val PRIOR_BODYWEIGHT_BASE_SD = 0.03
        const val PRIOR_BODYWEIGHT_DAILY_SD = 0.0005
        const val PROFILE_BODYWEIGHT_LOG_VARIANCE = 0.0064

        fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
        fun DailyMetric.validBodyWeight(): Double? = bodyWeightKg?.takeIf { it.isFinite() && it > 0.0 }
        fun DailyCheckIn.validBodyWeight(): Double? = bodyWeightKg?.takeIf { it.isFinite() && it > 0.0 }
    }
}
