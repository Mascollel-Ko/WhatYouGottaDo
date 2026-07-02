package com.training.trackplanner

internal fun formatRpe(value: Double): String =
    formatDecimal(value.coerceIn(0.0, 10.0))
