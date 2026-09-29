package com.archi.airmouse.receiver

import android.content.Context

/** Delivers watch messages to [CursorService]. The phone uses the Data Layer, the TV the LAN. */
interface Transport {
    /** Starts listening. [host] callbacks must be invoked on the main thread. */
    fun start(host: Host)

    fun stop()

    interface Host {
        fun onMessage(path: String, data: ByteArray)

        /** Shows a short message on screen, e.g. that a new watch is waiting for approval. */
        fun showNotice(text: CharSequence)
    }
}

/** Implemented by each app's Application class to plug its transports into the shared service. */
interface TransportProvider {
    fun createTransports(context: Context): List<Transport>

    /** True when watches connect over the LAN and must be approved in settings. */
    val usesLan: Boolean get() = false
}
