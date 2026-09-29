package com.archi.airmouse.receiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.archi.airmouse.Packet
import com.archi.airmouse.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException

/**
 * Receives watch packets over UDP and advertises this device with NSD (mDNS) so the watch can
 * find it on the Wi-Fi network without typing an address.
 */
class LanTransport(private val context: Context) : Transport {

    private val store = DeviceStore(context)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var socket: DatagramSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    override fun start(host: Transport.Host) {
        val socket = try {
            DatagramSocket(0)
        } catch (e: SocketException) {
            Log.w(TAG, "Could not open UDP socket", e)
            return
        }
        this.socket = socket
        Thread({ receive(socket, host) }, "airmouse-lan").apply {
            isDaemon = true
            start()
        }
        register(socket.localPort)
    }

    override fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        // Closing unblocks receive() in the reader thread, which then exits.
        socket?.close()
        socket = null
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = deviceName()
            serviceType = Protocol.SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "NSD registration failed: $error")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun receive(socket: DatagramSocket, host: Transport.Host) {
        val buffer = ByteArray(Packet.MAX_SIZE)
        val datagram = DatagramPacket(buffer, buffer.size)
        while (!socket.isClosed) {
            try {
                datagram.setLength(buffer.size)
                socket.receive(datagram)
            } catch (e: Exception) {
                return // closed by stop()
            }
            val packet = Packet.decode(buffer, datagram.length) ?: continue
            if (packet.path == Protocol.PATH_HELLO) {
                val approved = store.isApproved(packet.sender)
                if (!approved) {
                    val name = String(packet.data, Charsets.UTF_8).ifBlank { "Watch" }
                    if (store.addPending(packet.sender, name)) {
                        main.post { host.showNotice(context.getString(R.string.lan_pending_notice, name)) }
                    }
                }
                val status = if (approved) Protocol.STATUS_APPROVED else Protocol.STATUS_PENDING
                val reply = Packet(packet.sender, Protocol.PATH_STATUS, byteArrayOf(status)).encode()
                runCatching { socket.send(DatagramPacket(reply, reply.size, datagram.socketAddress)) }
            } else if (store.isApproved(packet.sender)) {
                main.post { host.onMessage(packet.path, packet.data) }
            }
        }
    }

    private fun deviceName(): String {
        val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        return name?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }

    private companion object {
        const val TAG = "LanTransport"
    }
}
