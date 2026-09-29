package com.archi.airmouse

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable

/** Finds the phone running the receiver app and sends messages to it. */
class PhoneLink(context: Context, private val onChanged: () -> Unit) {

    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val listener = CapabilityClient.OnCapabilityChangedListener { pick(it.nodes) }

    private var nodeId: String? = null

    val isConnected: Boolean get() = nodeId != null

    fun connect() {
        capabilityClient.addListener(listener, Protocol.CAPABILITY)
        capabilityClient.getCapability(Protocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { pick(it.nodes) }
            .addOnFailureListener { Log.w(TAG, "Capability lookup failed", it) }
    }

    fun disconnect() {
        capabilityClient.removeListener(listener, Protocol.CAPABILITY)
    }

    fun send(path: String, data: ByteArray = ByteArray(0)) {
        val id = nodeId ?: return
        messageClient.sendMessage(id, path, data)
    }

    private fun pick(nodes: Set<Node>) {
        nodeId = (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
        onChanged()
    }

    private companion object {
        const val TAG = "PhoneLink"
    }
}
