package com.archi.airmouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the cursor overlay and turns watch messages into taps, scrolls and global actions.
 * Receives messages directly from the Data Layer while the service is enabled.
 */
class CursorService : AccessibilityService(), MessageClient.OnMessageReceivedListener {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: MousePrefs
    private lateinit var cursor: View
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val hideCursor = Runnable { cursor.visibility = View.GONE }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadPrefs() }

    // Cursor pixels per radian, sign included; cached so move() doesn't hit prefs at 50 Hz.
    private var gainX = 0f
    private var gainY = 0f

    private var x = 0f
    private var y = 0f
    private var hotspot = 0
    private var pendingScroll = 0f
    private var scrolling = false

    override fun onServiceConnected() {
        windowManager = getSystemService(WindowManager::class.java)
        prefs = MousePrefs(this)
        loadPrefs()
        prefs.registerListener(prefsListener)
        hotspot = resources.getDimensionPixelOffset(R.dimen.cursor_hotspot)

        cursor = ImageView(this).apply {
            setImageResource(R.drawable.cursor)
            visibility = View.GONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not touchable: injected taps must pass through to the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(cursor, params)
        centerCursor()

        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Wearable.getMessageClient(this).removeListener(this)
        if (::prefs.isInitialized) prefs.unregisterListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        if (::cursor.isInitialized) windowManager.removeView(cursor)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_START -> centerCursor()
            Protocol.PATH_STOP -> {
                handler.removeCallbacks(hideCursor)
                cursor.visibility = View.GONE
            }
            Protocol.PATH_MOVE -> Protocol.readFloats(event.data, 2)?.let { move(it[0], it[1]) }
            Protocol.PATH_CLICK -> tap(TAP_MS)
            Protocol.PATH_LONG_PRESS -> tap(LONG_PRESS_MS)
            Protocol.PATH_SCROLL -> Protocol.readFloats(event.data, 1)?.let { scroll(it[0]) }
            Protocol.PATH_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Protocol.PATH_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            Protocol.PATH_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun loadPrefs() {
        gainX = prefs.sensitivity * prefs.horizontalBoost / 100f * if (prefs.invertX) -1f else 1f
        gainY = prefs.sensitivity * if (prefs.invertY) -1f else 1f
    }

    private fun move(yaw: Float, pitch: Float) {
        val size = screenSize()
        x = (x + yaw * gainX).coerceIn(0f, size.x - 1f)
        y = (y + pitch * gainY).coerceIn(0f, size.y - 1f)
        placeCursor()
    }

    private fun centerCursor() {
        val size = screenSize()
        x = size.x / 2f
        y = size.y / 2f
        placeCursor()
    }

    private fun placeCursor() {
        params.x = x.toInt() - hotspot
        params.y = y.toInt() - hotspot
        windowManager.updateViewLayout(cursor, params)
        showCursor()
    }

    private fun showCursor() {
        cursor.visibility = View.VISIBLE
        handler.removeCallbacks(hideCursor)
        handler.postDelayed(hideCursor, HIDE_AFTER_MS)
    }

    private fun tap(durationMs: Long) {
        // A hidden cursor gives no idea where the tap lands: reveal it and drop this tap.
        val wasVisible = cursor.visibility == View.VISIBLE
        showCursor()
        if (!wasVisible) return
        // Dispatching cancels a running swipe, whose callback would start the next swipe and
        // cancel this tap in turn. Drop the queued scroll so the tap survives.
        pendingScroll = 0f
        scrolling = false
        val path = Path().apply { moveTo(x, y) }
        dispatch(path, durationMs, null)
    }

    /** Queues scroll distance; one swipe runs at a time so bezel clicks don't cancel each other. */
    private fun scroll(watchPixels: Float) {
        pendingScroll += watchPixels * SCROLL_SCALE
        if (!scrolling) flushScroll()
    }

    private fun flushScroll() {
        val size = screenSize()
        val distance = pendingScroll.coerceIn(-size.y * MAX_SWIPE, size.y * MAX_SWIPE)
        pendingScroll -= distance
        if (abs(distance) < MIN_SWIPE_PX) {
            scrolling = false
            pendingScroll = 0f
            return
        }
        // Keep the swipe off the screen edges so it can't trigger system back/notification gestures.
        val margin = size.y * EDGE_MARGIN
        val swipeX = x.coerceIn(size.x * EDGE_MARGIN, size.x * (1 - EDGE_MARGIN))
        // Content moves down the page when the finger moves up.
        val startY = y.coerceIn(margin + max(distance, 0f), size.y - margin + min(distance, 0f))
        val path = Path().apply {
            moveTo(swipeX, startY)
            lineTo(swipeX, startY - distance)
        }
        scrolling = true
        val next = object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) = flushScroll()
            override fun onCancelled(gesture: GestureDescription?) = flushScroll()
        }
        if (!dispatch(path, SCROLL_MS, next)) {
            scrolling = false
            pendingScroll = 0f
        }
    }

    private fun dispatch(path: Path, durationMs: Long, callback: GestureResultCallback?): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, handler)
    }

    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also { windowManager.defaultDisplay.getRealSize(it) }
        }

    private companion object {
        const val TAP_MS = 50L
        const val LONG_PRESS_MS = 800L
        const val SCROLL_MS = 250L
        const val HIDE_AFTER_MS = 10_000L
        /** Phone pixels per watch pixel of swipe / bezel scroll. */
        const val SCROLL_SCALE = 3f
        const val MAX_SWIPE = 0.5f
        const val MIN_SWIPE_PX = 30f
        const val EDGE_MARGIN = 0.15f

        init {
            // Otherwise the swipe start range in flushScroll() is empty and coerceIn throws.
            require(MAX_SWIPE <= 1 - 2 * EDGE_MARGIN)
        }
    }
}
