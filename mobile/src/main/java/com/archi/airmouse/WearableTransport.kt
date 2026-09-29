package com.archi.airmouse

import android.content.Context
import com.archi.airmouse.receiver.Transport
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

/**
 * Receives messages from the paired watch over the Data Layer. Only apps with the same package
 * name and signing key can reach it, so no approval step is needed.
 */
class WearableTransport(context: Context) : Transport {

    private val client = Wearable.getMessageClient(context)
    private var listener: MessageClient.OnMessageReceivedListener? = null

    override fun start(host: Transport.Host) {
        // Data Layer listeners are called on the main thread.
        val listener = MessageClient.OnMessageReceivedListener { host.onMessage(it.path, it.data) }
        this.listener = listener
        client.addListener(listener)
    }

    override fun stop() {
        listener?.let { client.removeListener(it) }
        listener = null
    }
}
