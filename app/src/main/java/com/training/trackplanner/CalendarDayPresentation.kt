package com.training.trackplanner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal enum class CalendarDayBorderStyle(val width: Dp?) {
    SELECTED(2.dp),
    SEARCH_MATCH(3.dp),
    TODAY(1.dp),
    NONE(null)
}

internal fun calendarDayBorderStyle(
    exerciseSearchMatch: Boolean,
    today: Boolean,
    selected: Boolean = false
): CalendarDayBorderStyle = when {
    exerciseSearchMatch -> CalendarDayBorderStyle.SEARCH_MATCH
    selected -> CalendarDayBorderStyle.SELECTED
    today -> CalendarDayBorderStyle.TODAY
    else -> CalendarDayBorderStyle.NONE
}

/** Central semantic palette: plan-only never consumes an OFI value. */
internal fun calendarProgramContainerColor(dark: Boolean, planOnly: Boolean, ofi: Int?): Color {
    val pale = if (dark) Color(0xFF243B53) else Color(0xFFEAF4FF)
    val deep = if (dark) Color(0xFF155BA6) else Color(0xFF174B8C)
    return if (planOnly) pale else lerp(pale, deep, (ofi ?: 0).coerceIn(0, 100) / 100f)
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
