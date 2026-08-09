package com.training.trackplanner.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo
import com.training.trackplanner.analysis.coach.CourtDurationRecoveryMessageCode
import com.training.trackplanner.analysis.coach.CourtDurationRecoverySignal
import com.training.trackplanner.analysis.coach.JointTendonWarningMessageCode
import com.training.trackplanner.analysis.coach.JointTendonWarningSignal
import com.training.trackplanner.analysis.coach.RpeAutoregulationSignal
import com.training.trackplanner.analysis.coach.SleepRecoveryMessageCode
import com.training.trackplanner.analysis.coach.SleepRecoverySignal
import com.training.trackplanner.analysis.coach.formatOneDecimal
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.TrainingProgram
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal object LocalizedPresentation {
    fun uiText(context: Context, source: String): String =
        GeneratedLocalizationCatalogue.exactUiTextIds[source]
            ?.let(context::getString)
            ?: localizedDateText(context, source)
            ?: GeneratedLocalizationCatalogue.uiTextPatterns.firstNotNullOfOrNull { pattern ->
                pattern.regex.matchEntire(source)?.let { match ->
                    context.getString(pattern.text, *match.groupValues.drop(1).toTypedArray())
                }
            }
            ?: source

    fun exerciseName(context: Context, stableKey: String, storedName: String): String =
        GeneratedLocalizationCatalogue.exerciseNameIds[stableKey]
            ?.let(context::getString)
            ?: storedName

    fun exerciseName(context: Context, exercise: Exercise): String =
        if (exercise.isCustom) exercise.name
        else exerciseName(context, exercise.stableKey, exercise.name)

    fun exerciseDescription(context: Context, exercise: Exercise): String =
        if (exercise.isCustom) exercise.description
        else GeneratedLocalizationCatalogue.exerciseDescriptionIds[exercise.stableKey]
            ?.let(context::getString)
            ?: exercise.description

    fun programName(context: Context, program: TrainingProgram): String =
        GeneratedLocalizationCatalogue.programNameIds[program.stableKey]
            ?.let(context::getString)
            ?: program.name

    fun yearMonth(context: Context, month: YearMonth): String {
        val locale = context.resources.configuration.locales[0]
        val pattern = if (locale.language == Locale.KOREAN.language) "uuuu년 M월" else "MMM uuuu"
        return month.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun weekday(context: Context, dayOfWeek: DayOfWeek): String {
        val locale = context.resources.configuration.locales[0]
        return dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }

    fun sleepRecoverySignal(context: Context, signal: SleepRecoverySignal): LocalizedSignalText {
        if (signal.messageCode == SleepRecoveryMessageCode.INSUFFICIENT_DATA) {
            return LocalizedSignalText(
                context.getString(com.training.trackplanner.R.string.sleep_signal_insufficient_headline),
                context.getString(com.training.trackplanner.R.string.sleep_signal_insufficient_detail)
            )
        }
        val recent = signal.recentAverageHours?.formatOneDecimal().orEmpty()
        val headline = context.getString(
            when (signal.messageCode) {
                SleepRecoveryMessageCode.CAUTION -> com.training.trackplanner.R.string.sleep_signal_caution_headline
                SleepRecoveryMessageCode.WATCH -> com.training.trackplanner.R.string.sleep_signal_watch_headline
                SleepRecoveryMessageCode.INFO -> com.training.trackplanner.R.string.sleep_signal_info_headline
                SleepRecoveryMessageCode.INSUFFICIENT_DATA -> error("handled above")
            }
        )
        val detail = when {
            signal.baselineAverageHours != null && (signal.sleepDeficitHours ?: 0.0) > 0.0 ->
                context.getString(
                    com.training.trackplanner.R.string.sleep_signal_baseline_detail,
                    recent,
                    signal.baselineAverageHours.formatOneDecimal()
                )
            signal.messageCode == SleepRecoveryMessageCode.CAUTION ->
                context.getString(com.training.trackplanner.R.string.sleep_signal_caution_detail, recent)
            signal.messageCode == SleepRecoveryMessageCode.WATCH ->
                context.getString(com.training.trackplanner.R.string.sleep_signal_watch_detail, recent)
            else -> context.getString(com.training.trackplanner.R.string.sleep_signal_info_detail, recent)
        }
        return LocalizedSignalText(headline, detail)
    }

    fun rpeAutoregulationSignal(context: Context, signal: RpeAutoregulationSignal): LocalizedSignalText {
        val exerciseName = uiText(context, signal.exerciseName.orEmpty())
        return LocalizedSignalText(
            headline = context.getString(com.training.trackplanner.R.string.rpe_signal_headline),
            detail = context.getString(
                com.training.trackplanner.R.string.rpe_signal_detail,
                exerciseName,
                signal.recentAverageRpe?.formatOneDecimal().orEmpty(),
                signal.baselineAverageRpe?.formatOneDecimal().orEmpty()
            ),
            context = signal.sleepContext?.let {
                context.getString(com.training.trackplanner.R.string.rpe_signal_sleep_context)
            }
        )
    }

    fun jointTendonSignal(context: Context, signal: JointTendonWarningSignal): LocalizedSignalText =
        LocalizedSignalText(
            headline = context.getString(com.training.trackplanner.R.string.joint_tendon_signal_headline),
            detail = context.getString(
                when (signal.messageCode) {
                    JointTendonWarningMessageCode.RELATED_EXERCISE_STRESS ->
                        com.training.trackplanner.R.string.joint_tendon_signal_related_detail
                    JointTendonWarningMessageCode.DISCOMFORT_ONLY ->
                        com.training.trackplanner.R.string.joint_tendon_signal_discomfort_detail
                }
            ),
            context = signal.sleepContext?.let {
                context.getString(com.training.trackplanner.R.string.joint_tendon_signal_sleep_context)
            }
        )

    fun courtDurationSignal(context: Context, signal: CourtDurationRecoverySignal): LocalizedSignalText {
        val headline = context.getString(
            when (signal.messageCode) {
                CourtDurationRecoveryMessageCode.INSUFFICIENT_DATA ->
                    com.training.trackplanner.R.string.court_signal_insufficient_headline
                CourtDurationRecoveryMessageCode.LONG_DURATION_CAUTION ->
                    com.training.trackplanner.R.string.court_signal_caution_headline
                CourtDurationRecoveryMessageCode.LONG_DURATION_WATCH ->
                    com.training.trackplanner.R.string.court_signal_watch_headline
                CourtDurationRecoveryMessageCode.REFERENCE ->
                    com.training.trackplanner.R.string.court_signal_reference_headline
            }
        )
        val detail = if (signal.messageCode == CourtDurationRecoveryMessageCode.INSUFFICIENT_DATA) {
            context.getString(com.training.trackplanner.R.string.court_signal_insufficient_detail)
        } else if (signal.observedThresholdMinutes != null) {
            context.getString(
                com.training.trackplanner.R.string.court_signal_threshold_detail,
                signal.observedThresholdMinutes
            )
        } else {
            context.getString(com.training.trackplanner.R.string.court_signal_reference_detail)
        }
        return LocalizedSignalText(
            headline,
            detail,
            signal.sleepContext?.let {
                context.getString(com.training.trackplanner.R.string.court_signal_sleep_context)
            }
        )
    }

    fun tissueEducation(context: Context, source: TissueEducationalInfo): LocalizedTissueEducation {
        val ids = GeneratedLocalizationCatalogue.tissueEducationIds[source.stableKey]
            ?: return LocalizedTissueEducation(
                source.displayNameKo,
                source.anatomicalLocationKo,
                source.primaryFunctionsKo.joinToString(" "),
                source.commonLoadContextsKo.joinToString(" ")
            )
        return LocalizedTissueEducation(
            name = context.getString(ids.name),
            location = context.getString(ids.location),
            functions = context.getString(ids.functions),
            contexts = context.getString(ids.contexts)
        )
    }

    private fun localizedDateText(context: Context, source: String): String? {
        val locale = context.resources.configuration.locales[0]
        if (locale.language != Locale.ENGLISH.language) return null
        return try {
            detailedWeekPattern.matchEntire(source)?.let { match ->
                val year = match.groupValues[1].toIntOrNull()
                val month = match.groupValues[2].toInt()
                val week = match.groupValues[3]
                val monthLabel = monthLabel(year, month, locale)
                val range = dateRange(match.groupValues[4], match.groupValues[5], locale)
                    ?: return@let null
                "$monthLabel · Week $week · $range"
            } ?: monthWeekPattern.matchEntire(source)?.let { match ->
                val year = match.groupValues[1].toIntOrNull()
                val month = match.groupValues[2].toInt()
                "${monthLabel(year, month, locale)} · Week ${match.groupValues[3]}"
            } ?: source.split('~').takeIf { it.size == 2 }?.let { parts ->
                dateRange(parts[0], parts[1], locale)
            } ?: formatDate(source, locale)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun monthLabel(year: Int?, month: Int, locale: Locale): String {
        val value = YearMonth.of(year ?: 2000, month)
        val label = value.month.getDisplayName(TextStyle.FULL, locale)
        return if (year == null) label else "$label $year"
    }

    private fun dateRange(left: String, right: String, locale: Locale): String? {
        val start = formatDate(left.trim(), locale) ?: return null
        val end = formatDate(right.trim(), locale) ?: return null
        return "$start – $end"
    }

    private fun formatDate(source: String, locale: Locale): String? {
        fullDatePattern.matchEntire(source)?.let { match ->
            val date = LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
            val pattern = if (match.groupValues[4].isBlank()) "MMM d, uuuu" else "EEEE, MMM d, uuuu"
            return date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
        monthDatePattern.matchEntire(source)?.let { match ->
            val date = LocalDate.of(2000, match.groupValues[1].toInt(), match.groupValues[2].toInt())
            val pattern = if (match.groupValues[3].isBlank()) "MMM d" else "EEEE, MMM d"
            return date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
        slashDatePattern.matchEntire(source)?.let { match ->
            val date = LocalDate.of(2000, match.groupValues[1].toInt(), match.groupValues[2].toInt())
            val dateLabel = date.format(DateTimeFormatter.ofPattern("MMM d", locale))
            val weekday = match.groupValues[3]
            return if (weekday.isBlank()) dateLabel else "$dateLabel\n${englishWeekdays.getValue(weekday)}"
        }
        return null
    }

    private val fullDatePattern = Regex("""^(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일(?:\s*([월화수목금토일])요일)?$""")
    private val monthDatePattern = Regex("""^(\d{1,2})월\s*(\d{1,2})일(?:\s*([월화수목금토일])요일)?$""")
    private val slashDatePattern = Regex("""^(\d{1,2})/(\d{1,2})(?:\n([월화수목금토일]))?$""")
    private val monthWeekPattern = Regex("""^(?:(\d{4})년\s*)?(\d{1,2})월\s*(\d+)주$""")
    private val detailedWeekPattern = Regex("""^(?:(\d{4})년\s*)?(\d{1,2})월\s*(\d+)주\s*·\s*(.+)~(.+)$""")
    private val englishWeekdays = mapOf(
        "월" to "Mon", "화" to "Tue", "수" to "Wed", "목" to "Thu",
        "금" to "Fri", "토" to "Sat", "일" to "Sun"
    )
}

internal data class LocalizedTissueEducation(
    val name: String,
    val location: String,
    val functions: String,
    val contexts: String
)

internal data class LocalizedSignalText(
    val headline: String,
    val detail: String,
    val context: String? = null
) {
    fun combined(): String = listOfNotNull(headline, detail, context).joinToString(" ")
}

@Composable
internal fun localizedUiText(source: String): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(source, locale) { LocalizedPresentation.uiText(context, source) }
}

@Composable
internal fun localizedExerciseName(stableKey: String, storedName: String): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(stableKey, storedName, locale) {
        LocalizedPresentation.exerciseName(context, stableKey, storedName)
    }
}

@Composable
internal fun localizedExerciseName(exercise: Exercise): String =
    localizedExerciseName(exercise.stableKey, exercise.name).takeUnless { exercise.isCustom }
        ?: exercise.name

@Composable
internal fun localizedExerciseDescription(exercise: Exercise): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(exercise, locale) { LocalizedPresentation.exerciseDescription(context, exercise) }
}

@Composable
internal fun localizedProgramName(program: TrainingProgram): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(program.stableKey, program.name, locale) {
        LocalizedPresentation.programName(context, program)
    }
}

@Composable
internal fun localizedYearMonth(month: YearMonth): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(month, locale) { LocalizedPresentation.yearMonth(context, month) }
}

@Composable
internal fun localizedWeekday(dayOfWeek: DayOfWeek): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(dayOfWeek, locale) { LocalizedPresentation.weekday(context, dayOfWeek) }
}

@Composable
internal fun localizedTissueEducation(source: TissueEducationalInfo): LocalizedTissueEducation {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(source, locale) { LocalizedPresentation.tissueEducation(context, source) }
}
