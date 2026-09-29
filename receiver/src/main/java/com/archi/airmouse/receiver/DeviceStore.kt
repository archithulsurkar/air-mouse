package com.archi.airmouse.receiver

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Watches allowed to drive this device over the LAN, plus those waiting for approval.
 * Anyone on the network can send packets, so nothing but a HELLO is honoured until the
 * user approves the watch in [SettingsActivity].
 */
class DeviceStore(context: Context) {

    data class Device(val id: UUID, val name: String)

    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun isApproved(id: UUID) = prefs.contains(APPROVED + id)

    /** Records a watch asking to connect. Returns true the first time it is seen. */
    fun addPending(id: UUID, name: String): Boolean {
        if (isApproved(id)) return false
        val isNew = !prefs.contains(PENDING + id)
        prefs.edit().putString(PENDING + id, name.take(MAX_NAME)).apply()
        return isNew
    }

    fun approve(id: UUID) {
        val name = prefs.getString(PENDING + id, null) ?: return
        prefs.edit().remove(PENDING + id).putString(APPROVED + id, name).apply()
    }

    fun remove(id: UUID) {
        prefs.edit().remove(PENDING + id).remove(APPROVED + id).apply()
    }

    fun approved(): List<Device> = list(APPROVED)

    fun pending(): List<Device> = list(PENDING)

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun list(prefix: String): List<Device> =
        prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is String) return@mapNotNull null
            runCatching { Device(UUID.fromString(key.removePrefix(prefix)), value) }.getOrNull()
        }.sortedBy { it.name }

    private companion object {
        const val APPROVED = "approved:"
        const val PENDING = "pending:"
        const val MAX_NAME = 40
    }
}
