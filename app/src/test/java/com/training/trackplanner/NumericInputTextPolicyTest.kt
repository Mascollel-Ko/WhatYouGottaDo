package com.training.trackplanner

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericInputTextPolicyTest {
    @Test
    fun zeroClearsOnFocusAndTypedDigitDoesNotAppendToZero() {
        val focused = NumericInputTextPolicy.onFocus("0")
        val typed = focused + "4"

        assertEquals("", focused)
        assertEquals("4", typed)
    }

    @Test
    fun blankRestoresZeroOnlyOnBlurAndDecimalIsPreserved() {
        assertEquals("0", NumericInputTextPolicy.onBlur(""))
        assertEquals("7.5", NumericInputTextPolicy.onFocus("7.5"))
        assertEquals("7.5", NumericInputTextPolicy.onBlur("7.5"))
    }
}
