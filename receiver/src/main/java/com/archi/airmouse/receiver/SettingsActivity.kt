package com.archi.airmouse.receiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: MousePrefs
    private lateinit var statusCard: MaterialCardView
    private lateinit var serviceStatus: TextView
    private var devices: DeviceStore? = null
    private val devicesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> showDevices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = MousePrefs(this)
        statusCard = findViewById(R.id.status_card)
        serviceStatus = findViewById(R.id.service_status)

        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val label = findViewById<TextView>(R.id.sensitivity_label)
        findViewById<Slider>(R.id.sensitivity).apply {
            valueFrom = MousePrefs.MIN_SENSITIVITY.toFloat()
            valueTo = MousePrefs.MAX_SENSITIVITY.toFloat()
            // Slider throws if the value is off its step grid, so snap whatever was stored.
            value = snapToStep(prefs.sensitivity.toFloat())
            label.text = getString(R.string.sensitivity, value.roundToInt())
            addOnChangeListener { _, value, _ ->
                prefs.sensitivity = value.roundToInt()
                label.text = getString(R.string.sensitivity, value.roundToInt())
            }
        }

        val boostLabel = findViewById<TextView>(R.id.horizontal_boost_label)
        findViewById<Slider>(R.id.horizontal_boost).apply {
            valueFrom = MousePrefs.MIN_HORIZONTAL_BOOST.toFloat()
            valueTo = MousePrefs.MAX_HORIZONTAL_BOOST.toFloat()
            value = snapToStep(prefs.horizontalBoost.toFloat())
            boostLabel.text = getString(R.string.horizontal_boost, value.roundToInt())
            addOnChangeListener { _, value, _ ->
                prefs.horizontalBoost = value.roundToInt()
                boostLabel.text = getString(R.string.horizontal_boost, value.roundToInt())
            }
        }

        findViewById<MaterialSwitch>(R.id.invert_x).apply {
            isChecked = prefs.invertX
            setOnCheckedChangeListener { _, checked -> prefs.invertX = checked }
        }
        findViewById<MaterialSwitch>(R.id.invert_y).apply {
            isChecked = prefs.invertY
            setOnCheckedChangeListener { _, checked -> prefs.invertY = checked }
        }

        if ((application as TransportProvider).usesLan) {
            devices = DeviceStore(this)
            findViewById<View>(R.id.devices_card).visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        devices?.registerListener(devicesListener)
        showDevices()
    }

    override fun onStop() {
        devices?.unregisterListener(devicesListener)
        super.onStop()
    }

    /** One button per watch: Allow for those waiting, Remove for those already allowed. */
    private fun showDevices() {
        val store = devices ?: return
        val list = findViewById<LinearLayout>(R.id.devices_list)
        list.removeAllViews()
        val pending = store.pending()
        val approved = store.approved()
        findViewById<View>(R.id.devices_empty).visibility =
            if (pending.isEmpty() && approved.isEmpty()) View.VISIBLE else View.GONE
        for (device in pending) {
            list.addView(deviceButton(getString(R.string.device_allow, device.name), filled = true) {
                store.approve(device.id)
            })
        }
        for (device in approved) {
            list.addView(deviceButton(getString(R.string.device_remove, device.name), filled = false) {
                store.remove(device.id)
            })
        }
    }

    private fun deviceButton(text: String, filled: Boolean, onClick: () -> Unit): Button {
        val style = if (filled) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle
        return com.google.android.material.button.MaterialButton(this, null, style).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * resources.displayMetrics.density).roundToInt() }
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        serviceStatus.setText(if (enabled) R.string.service_on else R.string.service_off)
        val container = if (enabled) com.google.android.material.R.attr.colorPrimaryContainer
            else com.google.android.material.R.attr.colorErrorContainer
        val onContainer = if (enabled) com.google.android.material.R.attr.colorOnPrimaryContainer
            else com.google.android.material.R.attr.colorOnErrorContainer
        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, container))
        serviceStatus.setTextColor(MaterialColors.getColor(serviceStatus, onContainer))
    }

    private fun Slider.snapToStep(raw: Float): Float {
        val steps = ((raw.coerceIn(valueFrom, valueTo) - valueFrom) / stepSize).roundToInt()
        return valueFrom + steps * stepSize
    }

    private fun isServiceEnabled(): Boolean =
        getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == packageName && info.name == CursorService::class.java.name
            }
}
