package com.archi.airmouse

import android.content.Context

/** Persists [MotionMapper] calibration on the watch. */
class CalibrationStore(context: Context) {

    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun load(mapper: MotionMapper) {
        mapper.reset()
        // Calibrations from before axis learning only stored signs; ignore them.
        if (!prefs.contains(KEY_HORIZONTAL + 0)) return
        for (i in 0..2) {
            mapper.bias[i] = prefs.getFloat(KEY_BIAS + i, 0f)
            mapper.horizontal[i] = prefs.getFloat(KEY_HORIZONTAL + i, mapper.horizontal[i])
            mapper.vertical[i] = prefs.getFloat(KEY_VERTICAL + i, mapper.vertical[i])
        }
    }

    fun save(mapper: MotionMapper) {
        prefs.edit().apply {
            clear()
            for (i in 0..2) {
                putFloat(KEY_BIAS + i, mapper.bias[i])
                putFloat(KEY_HORIZONTAL + i, mapper.horizontal[i])
                putFloat(KEY_VERTICAL + i, mapper.vertical[i])
            }
        }.apply()
    }

    private companion object {
        const val KEY_BIAS = "bias_"
        const val KEY_HORIZONTAL = "horizontal_"
        const val KEY_VERTICAL = "vertical_"
    }
}
