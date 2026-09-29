package com.archi.airmouse

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Finds Air Mouse TVs on the Wi-Fi network (NSD) and sends them packets over UDP.
 *
 * Wear OS normally routes traffic through the phone's Bluetooth proxy, where the LAN is out of
 * reach, so this holds a Wi-Fi network request while connected and binds its socket to it.
 * All public members are main-thread only.
 */
class TvLink(context: Context, private val onChanged: () -> Unit) {

    class Tv(val name: String, val address: InetSocketAddress) {
        var approved = false
            internal set
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val deviceId = loadDeviceId(context)
    private val deviceName = watchName(context)

    private val found = LinkedHashMap<String, Tv>()
    private var network: Network? = null
    private var socket: DatagramSocket? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var connected = false

    val tvs: List<Tv> get() = found.values.toList()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post { if (connected && this@TvLink.network == null) onNetwork(network) }
        }

        override fun onLost(network: Network) {
            main.post { if (this@TvLink.network == network) closeNetwork() }
        }
    }

    private val hello = object : Runnable {
        override fun run() {
            val name = deviceName.toByteArray(Charsets.UTF_8)
            found.values.forEach { send(it, Protocol.PATH_HELLO, name) }
            main.postDelayed(this, HELLO_INTERVAL_MS)
        }
    }

    fun connect() {
        if (connected) return
        connected = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivity.requestNetwork(request, networkCallback)
    }

    fun disconnect() {
        if (!connected) return
        connected = false
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        closeNetwork()
    }

    fun send(tv: Tv, path: String, data: ByteArray = ByteArray(0)) {
        val socket = socket ?: return
        val bytes = Packet(deviceId, path, data).encode()
        io.execute { runCatching { socket.send(DatagramPacket(bytes, bytes.size, tv.address)) } }
    }

    private fun onNetwork(network: Network) {
        this.network = network
        val socket = try {
            DatagramSocket().also { network.bindSocket(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open UDP socket on Wi-Fi", e)
            return
        }
        this.socket = socket
        Thread({ receive(socket) }, "airmouse-tv").apply {
            isDaemon = true
            start()
        }
        startDiscovery(network)
        main.post(hello)
    }

    private fun closeNetwork() {
        main.removeCallbacks(hello)
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        toResolve.clear()
        resolving = false
        socket?.close()
        socket = null
        network = null
        if (found.isNotEmpty()) {
            found.clear()
            onChanged()
        }
    }

    private fun startDiscovery(network: Network) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    toResolve.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (found.remove(info.serviceName) != null) onChanged() }
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, error: Int) {
                Log.w(TAG, "NSD discovery failed: $error")
            }
            override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
        }
        discovery = listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, network, { main.post(it) }, listener)
        } else {
            nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    /** NsdManager resolves one service at a time, so queue them. */
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving || discovery == null) return
        val info = toResolve.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                main.post {
                    resolving = false
                    val host = resolved.host
                    if (discovery != null && host != null) {
                        val tv = Tv(resolved.serviceName, InetSocketAddress(host, resolved.port))
                        found[tv.name] = tv
                        send(tv, Protocol.PATH_HELLO, deviceName.toByteArray(Charsets.UTF_8))
                        onChanged()
                    }
                    resolveNext()
                }
            }

            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                main.post {
                    resolving = false
                    resolveNext()
                }
            }
        })
    }

    /** Reads the TV's STATUS replies so the watch can say whether it still needs approving. */
    private fun receive(socket: DatagramSocket) {
        val buffer = ByteArray(Packet.MAX_SIZE)
        val datagram = DatagramPacket(buffer, buffer.size)
        while (!socket.isClosed) {
            try {
                datagram.setLength(buffer.size)
                socket.receive(datagram)
            } catch (e: Exception) {
                return // closed by closeNetwork()
            }
            val packet = Packet.decode(buffer, datagram.length) ?: continue
            if (packet.path != Protocol.PATH_STATUS || packet.data.isEmpty()) continue
            val approved = packet.data[0] == Protocol.STATUS_APPROVED
            val from = datagram.socketAddress
            main.post {
                val tv = found.values.firstOrNull { it.address == from } ?: return@post
                if (tv.approved != approved) {
                    tv.approved = approved
                    onChanged()
                }
            }
        }
    }

    private companion object {
        const val TAG = "TvLink"
        const val HELLO_INTERVAL_MS = 2000L

        /** A random id per install; the TV approves watches by it. */
        fun loadDeviceId(context: Context): UUID {
            val prefs = context.getSharedPreferences("link", Context.MODE_PRIVATE)
            prefs.getString("device_id", null)?.let { return UUID.fromString(it) }
            return UUID.randomUUID().also { prefs.edit().putString("device_id", it.toString()).apply() }
        }

        fun watchName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}
