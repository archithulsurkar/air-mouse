package com.archi.airmouse

import java.nio.ByteBuffer

/** Message paths shared by the watch, phone and TV apps. */
object Protocol {
    /** Declared by the phone app in res/values/wear.xml so the watch can find it. */
    const val CAPABILITY = "airmouse_receiver"

    /** NSD (mDNS) service type the TV advertises on the local network. */
    const val SERVICE_TYPE = "_airmouse._udp"

    /** Watch -> TV, repeated while connected. Payload: the watch's display name, UTF-8. */
    const val PATH_HELLO = "/airmouse/hello"
    /** TV -> watch reply to [PATH_HELLO]. Payload: one byte, [STATUS_PENDING] or [STATUS_APPROVED]. */
    const val PATH_STATUS = "/airmouse/status"
    const val STATUS_PENDING: Byte = 0
    const val STATUS_APPROVED: Byte = 1

    const val PATH_START = "/airmouse/start"
    const val PATH_STOP = "/airmouse/stop"
    /** Payload: yaw, pitch rotation in radians since the previous message. */
    const val PATH_MOVE = "/airmouse/move"
    const val PATH_CLICK = "/airmouse/click"
    const val PATH_LONG_PRESS = "/airmouse/long_press"
    /** Payload: scroll distance in watch pixels; positive reveals content further down. */
    const val PATH_SCROLL = "/airmouse/scroll"
    const val PATH_BACK = "/airmouse/back"
    const val PATH_HOME = "/airmouse/home"
    const val PATH_RECENTS = "/airmouse/recents"

    fun floats(vararg values: Float): ByteArray =
        ByteBuffer.allocate(4 * values.size).apply { values.forEach { putFloat(it) } }.array()

    /** Returns exactly [count] floats, or null if the payload is too short. */
    fun readFloats(data: ByteArray, count: Int): FloatArray? {
        if (data.size < 4 * count) return null
        val buffer = ByteBuffer.wrap(data)
        return FloatArray(count) { buffer.float }
    }
}
