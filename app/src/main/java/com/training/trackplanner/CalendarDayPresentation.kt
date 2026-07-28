package com.training.trackplanner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal enum class CalendarDayBorderStyle(val width: Dp?) {
    SEARCH_MATCH(3.dp),
    TODAY(1.dp),
    NONE(null)
}

internal fun calendarDayBorderStyle(
    exerciseSearchMatch: Boolean,
    today: Boolean
): CalendarDayBorderStyle = when {
    exerciseSearchMatch -> CalendarDayBorderStyle.SEARCH_MATCH
    today -> CalendarDayBorderStyle.TODAY
    else -> CalendarDayBorderStyle.NONE
}

internal fun calendarOfiContainerColor(
    baseColor: Color,
    errorContainerColor: Color,
    ofi: Int?
): Color {
    if (ofi == null) return baseColor
    return lerp(baseColor, errorContainerColor, ofi.coerceIn(0, 100) / 100f)
}

internal fun calendarReadableContentColor(
    backgroundColor: Color,
    baseContentColor: Color,
    onErrorContainerColor: Color
): Color {
    val themed = listOf(baseContentColor, onErrorContainerColor)
        .maxBy { calendarContrastRatio(it, backgroundColor) }
    if (calendarContrastRatio(themed, backgroundColor) >= MIN_CALENDAR_TEXT_CONTRAST) {
        return themed
    }
    return listOf(Color.Black, Color.White)
        .maxBy { calendarContrastRatio(it, backgroundColor) }
}

internal fun calendarContrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = min(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal const val MIN_CALENDAR_TEXT_CONTRAST = 4.5f
