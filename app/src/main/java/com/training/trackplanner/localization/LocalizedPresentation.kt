package com.training.trackplanner.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo
import com.training.trackplanner.data.Exercise

internal object LocalizedPresentation {
    fun uiText(context: Context, source: String): String =
        GeneratedLocalizationCatalogue.exactUiTextIds[source]
            ?.let(context::getString)
            ?: source

    fun exerciseName(context: Context, stableKey: String, storedName: String): String =
        GeneratedLocalizationCatalogue.exerciseNameIds[stableKey]
            ?.let(context::getString)
            ?: storedName

    fun exerciseName(context: Context, exercise: Exercise): String =
        if (exercise.isCustom) exercise.name
        else exerciseName(context, exercise.stableKey, exercise.name)

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
}

internal data class LocalizedTissueEducation(
    val name: String,
    val location: String,
    val functions: String,
    val contexts: String
)

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
internal fun localizedTissueEducation(source: TissueEducationalInfo): LocalizedTissueEducation {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(source, locale) { LocalizedPresentation.tissueEducation(context, source) }
}
