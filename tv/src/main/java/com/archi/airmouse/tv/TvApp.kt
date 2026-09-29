package com.archi.airmouse.tv

import android.app.Application
import android.content.Context
import com.archi.airmouse.receiver.LanTransport
import com.archi.airmouse.receiver.Transport
import com.archi.airmouse.receiver.TransportProvider

/** TV receiver: watches find it on the LAN and must be approved before they can drive it. */
class TvApp : Application(), TransportProvider {
    override fun createTransports(context: Context): List<Transport> = listOf(LanTransport(context))

    override val usesLan = true
}
