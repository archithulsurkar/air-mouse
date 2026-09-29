package com.archi.airmouse

import android.content.Context
import android.content.SharedPreferences

class MousePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("mouse", Context.MODE_PRIVATE)

    /** Cursor pixels per radian of wrist rotation. */
    var sensitivity: Int
        get() = prefs.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putInt(KEY_SENSITIVITY, value).apply()

    /** Extra horizontal gain in percent; the wrist turns less side to side than up and down. */
    var horizontalBoost: Int
        get() = prefs.getInt(KEY_HORIZONTAL_BOOST, DEFAULT_HORIZONTAL_BOOST)
        set(value) = prefs.edit().putInt(KEY_HORIZONTAL_BOOST, value).apply()

    var invertX: Boolean
        get() = prefs.getBoolean(KEY_INVERT_X, false)
        set(value) = prefs.edit().putBoolean(KEY_INVERT_X, value).apply()

    var invertY: Boolean
        get() = prefs.getBoolean(KEY_INVERT_Y, false)
        set(value) = prefs.edit().putBoolean(KEY_INVERT_Y, value).apply()

    /** Caller must keep a strong reference to [listener]; SharedPreferences holds it weakly. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val MIN_SENSITIVITY = 500
        const val MAX_SENSITIVITY = 8000
        const val DEFAULT_SENSITIVITY = 2500
        const val MIN_HORIZONTAL_BOOST = 100
        const val MAX_HORIZONTAL_BOOST = 300
        const val DEFAULT_HORIZONTAL_BOOST = 150

        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_HORIZONTAL_BOOST = "horizontal_boost"
        private const val KEY_INVERT_X = "invert_x"
        private const val KEY_INVERT_Y = "invert_y"
    }
}
