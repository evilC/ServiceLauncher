package com.evilc.servicelauncher

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

@RequiresApi(34)
internal class NsdServiceInfoResolver(
    private val manager: NsdManager,
    private val executor: Executor,
    private val service: NsdServiceInfo,
    private val onUpdated: (NsdServiceInfo) -> Unit,
    private val onLost: () -> Unit,
    private val onRegistrationFailed: (Int) -> Unit
) {
    private val callback = object : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            onRegistrationFailed(errorCode)
        }

        override fun onServiceInfoCallbackUnregistered() = Unit

        override fun onServiceLost() {
            onLost()
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            onUpdated(serviceInfo)
        }
    }

    fun register() {
        manager.registerServiceInfoCallback(service, executor, callback)
    }

    fun unregister() {
        manager.unregisterServiceInfoCallback(callback)
    }
}
