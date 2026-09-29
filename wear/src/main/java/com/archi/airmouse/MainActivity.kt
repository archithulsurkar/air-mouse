package com.archi.airmouse

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var sensorsOn = false
    private val mapper = MotionMapper()
    private lateinit var calibrationStore: CalibrationStore
    private var calibrator: Calibrator? = null
    private val rates = FloatArray(2)
    private lateinit var targets: Targets
    private lateinit var pad: View
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var calibrate: Button
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var lastSampleNanos = 0L
    // Rotation in radians accumulated since the last move message.
    private var pendingYaw = 0f
    private var pendingPitch = 0f
    private var motionFrozenUntil = 0L

    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressFired = false
    private var touchSlop = 0
    private var rotaryScrollFactor = 0f

    private val flushMotion = object : Runnable {
        override fun run() {
            if (pendingYaw != 0f || pendingPitch != 0f) {
                targets.send(Protocol.PATH_MOVE, Protocol.floats(pendingYaw, pendingPitch))
                pendingYaw = 0f
                pendingPitch = 0f
            }
            handler.postDelayed(this, SEND_INTERVAL_MS)
        }
    }

    private val longPress = Runnable {
        longPressFired = true
        targets.send(Protocol.PATH_LONG_PRESS)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(SensorManager::class.java)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        calibrationStore = CalibrationStore(this).also { it.load(mapper) }
        val viewConfig = ViewConfiguration.get(this)
        touchSlop = viewConfig.scaledTouchSlop
        rotaryScrollFactor = viewConfig.scaledVerticalScrollFactor

        pad = findViewById(R.id.pad)
        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        calibrate = findViewById(R.id.calibrate)
        targets = Targets(this) { onLinkChanged() }

        toggle.setOnClickListener { if (active) stop() else start() }
        calibrate.setOnClickListener { if (calibrator != null) cancelCalibration() else startCalibration() }
        // While paused, tapping the status line switches between TVs and the phone.
        status.setOnClickListener { if (!active && calibrator == null) targets.next() }
        findViewById<Button>(R.id.back).setOnClickListener { targets.send(Protocol.PATH_BACK) }
        findViewById<Button>(R.id.home).setOnClickListener { targets.send(Protocol.PATH_HOME) }
        findViewById<Button>(R.id.recents).setOnClickListener { targets.send(Protocol.PATH_RECENTS) }
        pad.setOnTouchListener { _, event -> onPadTouch(event); true }
        pad.setOnGenericMotionListener { _, event -> onRotary(event) }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        targets.connect()
        // Rotary (bezel) events go to the focused view.
        pad.requestFocus()
    }

    override fun onPause() {
        cancelCalibration()
        stop()
        targets.disconnect()
        super.onPause()
    }

    private fun start() {
        if (gyroscope == null) {
            status.setText(R.string.no_gyro)
            return
        }
        // Without a ready target every message is dropped; stay paused rather than look active.
        if (targets.current?.isReady != true || calibrator != null) return
        targets.lock()
        active = true
        pendingYaw = 0f
        pendingPitch = 0f
        updateSensors()
        handler.post(flushMotion)
        targets.send(Protocol.PATH_START)
        updateStatus()
    }

    private fun stop() {
        if (!active) return
        active = false
        // A finger may still be down; its UP/CANCEL is ignored while inactive and would
        // otherwise leave touching stuck true, freezing the cursor after the next start().
        touching = false
        updateSensors()
        handler.removeCallbacks(flushMotion)
        handler.removeCallbacks(longPress)
        targets.send(Protocol.PATH_STOP)
        targets.unlock()
        updateStatus()
    }

    /** Sensors run while the cursor is live or a calibration is in progress. */
    private fun updateSensors() {
        val want = active || calibrator != null
        if (want == sensorsOn) return
        sensorsOn = want
        if (want) {
            lastSampleNanos = 0L
            gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    private fun startCalibration() {
        if (gyroscope == null) {
            status.setText(R.string.no_gyro)
            return
        }
        if (active || calibrator != null) return
        calibrator = Calibrator(mapper, object : Calibrator.Listener {
            override fun onStep(step: Calibrator.Step) {
                status.setText(
                    when (step) {
                        Calibrator.Step.HOLD_STILL -> R.string.cal_hold_still
                        Calibrator.Step.MOVE_RIGHT -> R.string.cal_move_right
                        Calibrator.Step.MOVE_UP -> R.string.cal_move_up
                    }
                )
                pad.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }

            override fun onFinished(result: Calibrator.Result) = finishCalibration(result)
        })
        updateCalibrateButton()
        toggle.isEnabled = false
        updateSensors()
        calibrator?.start()
    }

    private fun finishCalibration(result: Calibrator.Result) {
        calibrator = null
        val success = result == Calibrator.Result.OK
        if (success) calibrationStore.save(mapper)
        updateSensors()
        pad.performHapticFeedback(
            if (success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        )
        toggle.isEnabled = true
        updateStatus()
        // Show the result briefly; updateStatus() restores the normal text afterwards.
        status.setText(
            when (result) {
                Calibrator.Result.OK -> R.string.cal_done
                Calibrator.Result.NO_MOVEMENT -> R.string.cal_no_movement
                Calibrator.Result.MOVES_TOO_SIMILAR -> R.string.cal_too_similar
            }
        )
        handler.removeCallbacks(restoreStatus)
        handler.postDelayed(restoreStatus, RESULT_SHOWN_MS)
    }

    private fun cancelCalibration() {
        // The mapper is only written on success, so dropping the calibrator is enough.
        if (calibrator == null) return
        calibrator = null
        updateSensors()
        toggle.isEnabled = true
        updateStatus()
    }

    private val restoreStatus = Runnable { if (calibrator == null) updateStatus() }

    private fun updateCalibrateButton() {
        calibrate.visibility = if (active) View.GONE else View.VISIBLE
        calibrate.setText(if (calibrator != null) R.string.cancel else R.string.calibrate)
    }

    private fun onLinkChanged() {
        if (active && targets.current?.isReady != true) stop() else updateStatus()
    }

    private fun updateStatus() {
        updateCalibrateButton()
        // Clickable only while paused: while streaming, taps over the text must reach the pad.
        status.isClickable = !active
        // Calibration owns the status line until it finishes.
        if (calibrator != null) return
        handler.removeCallbacks(restoreStatus)
        val target = targets.current
        status.text = when {
            target == null -> getString(R.string.status_no_target)
            !target.isReady -> getString(R.string.status_allow_on_tv, target.name)
            active -> getString(R.string.status_active)
            targets.all.size > 1 -> getString(R.string.status_paused_switch, target.name)
            else -> getString(R.string.status_paused_target, target.name)
        }
        toggle.setText(if (active) R.string.pause else R.string.start)
        // Filled Start is the call to action; Pause drops to tonal so it doesn't shout mid-use.
        toggle.setBackgroundResource(if (active) R.drawable.bg_button_tonal else R.drawable.bg_button_filled)
        toggle.setTextColor(getColor(if (active) R.color.on_secondary_container else R.color.on_primary))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val dt = if (lastSampleNanos == 0L) 0f else (event.timestamp - lastSampleNanos) / 1e9f
        lastSampleNanos = event.timestamp
        calibrator?.let {
            it.onGyro(event.values, event.timestamp, dt)
            return
        }
        // Hold the cursor still while a finger is on the screen so taps land where aimed.
        if (touching || SystemClock.uptimeMillis() < motionFrozenUntil) return

        mapper.map(event.values, rates)
        if (abs(rates[0]) > DEAD_ZONE) pendingYaw += rates[0] * dt
        if (abs(rates[1]) > DEAD_ZONE) pendingPitch += rates[1] * dt
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onPadTouch(event: MotionEvent) {
        if (!active) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                moved = false
                longPressFired = false
                downX = event.x
                downY = event.y
                // Drop the wrist jolt from the finger landing.
                pendingYaw = 0f
                pendingPitch = 0f
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPress)
                }
            }
            MotionEvent.ACTION_UP -> {
                endTouch()
                when {
                    longPressFired -> Unit
                    moved -> targets.send(Protocol.PATH_SCROLL, Protocol.floats(downY - event.y))
                    else -> targets.send(Protocol.PATH_CLICK)
                }
            }
            MotionEvent.ACTION_CANCEL -> endTouch()
        }
    }

    private fun endTouch() {
        touching = false
        handler.removeCallbacks(longPress)
        motionFrozenUntil = SystemClock.uptimeMillis() + FREEZE_AFTER_TOUCH_MS
    }

    private fun onRotary(event: MotionEvent): Boolean {
        if (!active || event.action != MotionEvent.ACTION_SCROLL ||
            !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) return false
        val amount = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * rotaryScrollFactor
        targets.send(Protocol.PATH_SCROLL, Protocol.floats(amount))
        return true
    }

    private companion object {
        const val SEND_INTERVAL_MS = 20L
        /** Gyro rates below this (rad/s) are treated as hand tremor / sensor noise. */
        const val DEAD_ZONE = 0.02f
        const val LONG_PRESS_MS = 500L
        const val FREEZE_AFTER_TOUCH_MS = 150L
        const val RESULT_SHOWN_MS = 2000L
    }
}
