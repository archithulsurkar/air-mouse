package com.archi.airmouse

import kotlin.math.sqrt

/**
 * Guided calibration: hold still (measures gyro bias), then the user's own RIGHT move and UP
 * move. Each move's net rotation gives the axis the cursor should follow, so any gesture works.
 * Writes the result into [mapper] only on success.
 */
class Calibrator(private val mapper: MotionMapper, private val listener: Listener) {

    enum class Step { HOLD_STILL, MOVE_RIGHT, MOVE_UP }

    enum class Result { OK, NO_MOVEMENT, MOVES_TOO_SIMILAR }

    interface Listener {
        fun onStep(step: Step)
        fun onFinished(result: Result)
    }

    private var step = Step.HOLD_STILL
    private var stepStartNanos = 0L
    // Still step: summed raw rates. Move steps: net rotation vector in radians.
    private val sum = FloatArray(3)
    private var samples = 0
    private val rate = FloatArray(3)

    private val bias = FloatArray(3)
    private val right = FloatArray(3)

    fun start() = enter(Step.HOLD_STILL)

    fun onGyro(values: FloatArray, timestampNanos: Long, dt: Float) {
        if (stepStartNanos == 0L) stepStartNanos = timestampNanos
        val elapsedMs = (timestampNanos - stepStartNanos) / 1_000_000
        when (step) {
            Step.HOLD_STILL -> {
                if (length(values) > STILL_LIMIT) {
                    // Moved: start the still period over.
                    sum.fill(0f)
                    samples = 0
                    stepStartNanos = timestampNanos
                    return
                }
                for (i in 0..2) sum[i] += values[i]
                samples++
                if (elapsedMs >= HOLD_MS) {
                    for (i in 0..2) bias[i] = sum[i] / samples
                    enter(Step.MOVE_RIGHT)
                }
            }
            Step.MOVE_RIGHT, Step.MOVE_UP -> {
                // Give the user time to read the prompt and settle back to neutral.
                if (elapsedMs < GRACE_MS) return
                if (elapsedMs > TIMEOUT_MS) {
                    listener.onFinished(Result.NO_MOVEMENT)
                    return
                }
                for (i in 0..2) rate[i] = values[i] - bias[i]
                for (i in 0..2) sum[i] += rate[i] * dt
                if (length(sum) < MOVE_RAD) return
                if (step == Step.MOVE_RIGHT) {
                    normalize(sum).copyInto(right)
                    enter(Step.MOVE_UP)
                } else {
                    finish()
                }
            }
        }
    }

    private fun finish() {
        // Up must come out negative (screen y grows downwards), so follow the opposite axis,
        // minus whatever part of it overlaps the right axis, so the two never mix.
        val down = FloatArray(3) { -sum[it] }
        val overlap = dot(down, right)
        for (i in 0..2) down[i] -= overlap * right[i]
        if (length(down) < MIN_SEPARATION * length(sum)) {
            listener.onFinished(Result.MOVES_TOO_SIMILAR)
            return
        }
        bias.copyInto(mapper.bias)
        right.copyInto(mapper.horizontal)
        normalize(down).copyInto(mapper.vertical)
        listener.onFinished(Result.OK)
    }

    private fun enter(next: Step) {
        step = next
        stepStartNanos = 0L
        sum.fill(0f)
        samples = 0
        listener.onStep(next)
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun length(a: FloatArray) = sqrt(dot(a, a))

    private fun normalize(a: FloatArray): FloatArray {
        val len = length(a)
        return FloatArray(3) { a[it] / len }
    }

    private companion object {
        /** rad/s; anything faster during the still step counts as moving. */
        const val STILL_LIMIT = 0.2f
        const val HOLD_MS = 1500L
        const val GRACE_MS = 600L
        const val TIMEOUT_MS = 8000L
        /** About 15° of net rotation before a move counts. */
        const val MOVE_RAD = 0.25f
        /** Fraction of the UP move that must be independent of the RIGHT axis (~sin 30°). */
        const val MIN_SEPARATION = 0.5f
    }
}
