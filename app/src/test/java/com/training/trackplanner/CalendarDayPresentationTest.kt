package com.training.trackplanner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalendarDayPresentationTest {
    private val base = Color(0xFFE8DEF8)
    private val error = Color(0xFFFFDAD6)

    @Test
    fun ofiTintClampsAndInterpolatesContinuously() {
        val zero = calendarOfiContainerColor(base, error, 0)
        val half = calendarOfiContainerColor(base, error, 50)
        val full = calendarOfiContainerColor(base, error, 100)

        assertEquals(base, zero)
        assertEquals(error, full)
        assertTrue(half != base && half != error)
        assertEquals(base, calendarOfiContainerColor(base, error, -1))
        assertEquals(error, calendarOfiContainerColor(base, error, 101))
        assertEquals(base, calendarOfiContainerColor(base, error, null))
        assertTrue(colorDistance(half, error) < colorDistance(zero, error))
        assertTrue(colorDistance(full, error) <= colorDistance(half, error))
    }

    @Test
    fun contentColorMaintainsSmallTextContrastInLightAndDarkThemes() {
        val lightBackground = Color(0xFFFFDAD6)
        val darkBackground = Color(0xFF8C1D18)
        val lightContent = calendarReadableContentColor(
            lightBackground,
            baseContentColor = Color(0xFF1D192B),
            onErrorContainerColor = Color(0xFF410002)
        )
        val darkContent = calendarReadableContentColor(
            darkBackground,
            baseContentColor = Color(0xFFE8DEF8),
            onErrorContainerColor = Color(0xFFFFDAD6)
        )

        assertTrue(calendarContrastRatio(lightContent, lightBackground) >= MIN_CALENDAR_TEXT_CONTRAST)
        assertTrue(calendarContrastRatio(darkContent, darkBackground) >= MIN_CALENDAR_TEXT_CONTRAST)
    }

    @Test
    fun searchBorderPrecedesTodayWithoutChangingOfiTint() {
        assertEquals(
            CalendarDayBorderStyle.SEARCH_MATCH,
            calendarDayBorderStyle(exerciseSearchMatch = true, today = true)
        )
        assertEquals(
            CalendarDayBorderStyle.TODAY,
            calendarDayBorderStyle(exerciseSearchMatch = false, today = true)
        )
        assertEquals(
            CalendarDayBorderStyle.NONE,
            calendarDayBorderStyle(exerciseSearchMatch = false, today = false)
        )
        assertEquals(3.dp, CalendarDayBorderStyle.SEARCH_MATCH.width)
        assertEquals(1.dp, CalendarDayBorderStyle.TODAY.width)
        assertEquals(null, CalendarDayBorderStyle.NONE.width)

        val tintWithoutSearch = calendarOfiContainerColor(base, error, 72)
        val tintWithSearch = calendarOfiContainerColor(base, error, 72)
        assertEquals(tintWithoutSearch, tintWithSearch)
    }

    private fun colorDistance(left: Color, right: Color): Float =
        abs(left.red - right.red) +
            abs(left.green - right.green) +
            abs(left.blue - right.blue) +
            abs(left.alpha - right.alpha)
}
