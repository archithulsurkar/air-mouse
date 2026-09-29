package com.archi.airmouse

import android.content.Context

/**
 * Everything the watch can drive: TVs found on Wi-Fi first (the main use), then the paired phone.
 * The user's pick is remembered; without one the first TV wins. Main-thread only.
 */
class Targets(context: Context, private val onChanged: () -> Unit) {

    class Target(val key: String, val name: String, internal val tv: TvLink.Tv?) {
        val isTv: Boolean get() = tv != null
        /** A TV only listens once it has approved this watch; the phone always does. */
        val isReady: Boolean get() = tv?.approved ?: true
    }

    private val prefs = context.getSharedPreferences("targets", Context.MODE_PRIVATE)
    private val phone = PhoneLink(context, onChanged)
    private val tvs = TvLink(context, onChanged)
    private val phoneName = context.getString(R.string.target_phone)
    private var chosen: String? = prefs.getString(KEY_CHOSEN, null)
    private var locked: String? = null

    val all: List<Target>
        get() = tvs.tvs.map { Target(TV_PREFIX + it.name, it.name, it) } +
            if (phone.isConnected) listOf(Target(PHONE, phoneName, null)) else emptyList()

    /** While [lock]ed, only the locked target, or null once it goes away. */
    val current: Target?
        get() {
            val list = all
            locked?.let { key -> return list.firstOrNull { it.key == key } }
            return list.firstOrNull { it.key == chosen } ?: list.firstOrNull()
        }

    /** Pins the current target for a session so a TV appearing later can't take over. */
    fun lock() {
        locked = current?.key
    }

    fun unlock() {
        locked = null
    }

    fun connect() {
        phone.connect()
        tvs.connect()
    }

    fun disconnect() {
        phone.disconnect()
        tvs.disconnect()
    }

    /** Makes the next target current; remembered for next time. */
    fun next() {
        val list = all
        if (list.size < 2) return
        val index = list.indexOfFirst { it.key == current?.key }
        chosen = list[(index + 1) % list.size].key
        prefs.edit().putString(KEY_CHOSEN, chosen).apply()
        onChanged()
    }

    fun send(path: String, data: ByteArray = ByteArray(0)) {
        val target = current ?: return
        val tv = target.tv
        if (tv != null) tvs.send(tv, path, data) else phone.send(path, data)
    }

    private companion object {
        const val KEY_CHOSEN = "chosen"
        const val PHONE = "phone"
        const val TV_PREFIX = "tv:"
    }
}
