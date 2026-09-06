package com.training.trackplanner

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ProgressionCalendarTest {
    @Test fun planOnlyIgnoresEvenAnAccidentallySuppliedFutureOfi() {
        for (dark in listOf(false, true)) {
            assertEquals(calendarProgramContainerColor(dark, true, null), calendarProgramContainerColor(dark, true, 100))
            assertEquals(calendarProgramContainerColor(dark, true, null), calendarProgramContainerColor(dark, false, 0))
        }
    }
    @Test fun programOfiRemainsBlueAndReadableAcrossBothThemes() {
        for (dark in listOf(false, true)) for (ofi in 0..100) {
            val color = calendarProgramContainerColor(dark, false, ofi)
            assertTrue(color.blue > color.red && color.blue > color.green)
            val text = calendarReadableContentColor(color, Color.Black, Color.White)
            assertTrue(calendarContrastRatio(text, color) >= 4.5f)
        }
        assertNotEquals(calendarProgramContainerColor(false, false, 0), calendarProgramContainerColor(false, false, 50))
        assertNotEquals(calendarProgramContainerColor(false, false, 50), calendarProgramContainerColor(false, false, 100))
    }
    @Test fun selectedUsesBorderAndNonProgramErrorMappingIsUnchanged() {
        assertEquals(CalendarDayBorderStyle.SELECTED, calendarDayBorderStyle(false, false, true))
        assertEquals(Color.Red, calendarOfiContainerColor(Color.White, Color.Red, 100))
        assertEquals(Color.White, calendarOfiContainerColor(Color.White, Color.Red, 0))
    }
}
