package com.training.trackplanner

internal object NumericInputTextPolicy {
    fun onFocus(value: String): String =
        if (value.toDoubleOrNull() == 0.0) "" else value

    fun onBlur(value: String): String = value.ifBlank { "0" }
}
