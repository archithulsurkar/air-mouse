package com.archi.airmouse

/**
 * Turns gyroscope rates into cursor rates by projecting them onto two rotation axes in watch
 * coordinates. The defaults suit aiming with the watch face towards you: raising/lowering the
 * elbow spins the watch about its face normal (z) for left/right, and rolling the wrist spins
 * it about the forearm (x) for up/down. Calibration replaces both axes with the user's own moves.
 */
class MotionMapper {

    /** Gyro reading while held still, in rad/s. Subtracted to stop the cursor creeping. */
    val bias = FloatArray(3)
    /** Unit axis; rotation about it moves the cursor right. */
    val horizontal = floatArrayOf(0f, 0f, -1f)
    /** Unit axis; rotation about it moves the cursor down (screen y grows downwards). */
    val vertical = floatArrayOf(1f, 0f, 0f)

    /** Gyro rates minus [bias], written into [out]. */
    fun corrected(gyro: FloatArray, out: FloatArray) {
        for (i in 0..2) out[i] = gyro[i] - bias[i]
    }

    /** Writes cursor rates into [out]: +x is right, +y is down, in rad/s. */
    fun map(gyro: FloatArray, out: FloatArray) {
        var h = 0f
        var v = 0f
        for (i in 0..2) {
            val w = gyro[i] - bias[i]
            h += w * horizontal[i]
            v += w * vertical[i]
        }
        out[0] = h
        out[1] = v
    }

    fun reset() {
        bias.fill(0f)
        floatArrayOf(0f, 0f, -1f).copyInto(horizontal)
        floatArrayOf(1f, 0f, 0f).copyInto(vertical)
    }
}
