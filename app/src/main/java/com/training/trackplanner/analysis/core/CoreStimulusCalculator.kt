package com.training.trackplanner.analysis.core

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

data class DailyCoreStimulus(
    val date: LocalDate,
    val direct: Double,
    val indirect: Double,
    val directTargets: Map<CoreDirectTarget, Double>
) {
    val total: Double get() = direct + indirect
}

data class CoreStimulusSummary(
    val calculationVersion: String,
    val daily: List<DailyCoreStimulus>
) {
    companion object {
        const val VERSION = "CORE_STIMULUS_V1"
        val EMPTY = CoreStimulusSummary(VERSION, emptyList())
    }
}

class CoreStimulusCalculator(
    private val catalog: CanonicalCoreCatalog
) {
    fun calculate(
        entries: List<WorkoutEntryWithSets>,
        exerciseMap: Map<String, Exercise>
    ): CoreStimulusSummary {
        val daily = entries.groupBy { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
            .mapNotNull { (date, records) ->
                date ?: return@mapNotNull null
                var direct = 0.0
                var indirect = 0.0
                val targets = mutableMapOf<CoreDirectTarget, Double>()
                records.forEach { record ->
                    val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@forEach
                    if (exercise.activityKind == "SPORT_SESSION") return@forEach
                    val profile = catalog.resolve(record.entry.exerciseStableKey) ?: return@forEach
                    record.sets.filter { set -> set.confirmed }.forEach { set ->
                        val stimulus = profile.coreClass.coefficient *
                            AnalysisStimulusRpePolicy.modifier(set.rpe ?: record.entry.rpe)
                        when (profile.coreClass) {
                            CoreClass.DIRECT -> {
                                direct += stimulus
                                profile.directTarget?.let { target ->
                                    targets[target] = (targets[target] ?: 0.0) + stimulus
                                }
                            }
                            CoreClass.HIDDEN_HIGH,
                            CoreClass.HIDDEN_MODERATE,
                            CoreClass.HIDDEN_LOW -> indirect += stimulus
                            CoreClass.NONE -> Unit
                        }
                    }
                }
                DailyCoreStimulus(date, direct, indirect, targets.toMap())
            }
            .sortedBy(DailyCoreStimulus::date)
        return CoreStimulusSummary(
            calculationVersion = CoreStimulusSummary.VERSION,
            daily = daily
        )
    }
}
