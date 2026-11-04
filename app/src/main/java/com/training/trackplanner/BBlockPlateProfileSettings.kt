package com.training.trackplanner

import android.content.Context

/** Device-local user profile preference; changing it never rewrites saved workout loads. */
internal object BBlockPlateProfileSettings {
    private const val FILE = "training_profile"
    private const val KEY = "smallest_plate_kg"
    const val DEFAULT_KG = 1.25
    fun isValid(value: Double?): Boolean = value != null && value.isFinite() && value in 0.1..25.0
    fun load(context: Context): Double {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getFloat(KEY, DEFAULT_KG.toFloat()).toDouble()
        return if (isValid(stored)) stored else DEFAULT_KG
    }
    fun save(context: Context, value: Double) {
        require(isValid(value)) { "Invalid smallest plate" }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY, value.toFloat()).apply()
    }
}
