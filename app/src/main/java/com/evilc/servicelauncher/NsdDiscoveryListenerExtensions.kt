package com.evilc.servicelauncher

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler

internal fun NsdManager.DiscoveryListener.onHandler(handler: Handler) =
    object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            handler.post { this@onHandler.onDiscoveryStarted(serviceType) }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            handler.post { this@onHandler.onStartDiscoveryFailed(serviceType, errorCode) }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            handler.post { this@onHandler.onStopDiscoveryFailed(serviceType, errorCode) }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            handler.post { this@onHandler.onDiscoveryStopped(serviceType) }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            handler.post { this@onHandler.onServiceFound(serviceInfo) }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            handler.post { this@onHandler.onServiceLost(serviceInfo) }
        }
    }
