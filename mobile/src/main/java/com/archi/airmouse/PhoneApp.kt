package com.archi.airmouse

import android.app.Application
import android.content.Context
import com.archi.airmouse.receiver.Transport
import com.archi.airmouse.receiver.TransportProvider

/** Phone receiver: the paired watch reaches it over the Wearable Data Layer. */
class PhoneApp : Application(), TransportProvider {
    override fun createTransports(context: Context): List<Transport> = listOf(WearableTransport(context))
}
