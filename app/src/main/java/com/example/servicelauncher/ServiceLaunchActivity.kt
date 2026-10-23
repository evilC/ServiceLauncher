package com.example.servicelauncher

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Inet6Address

internal object ServiceEndpoint {
    fun url(address: InetAddress, port: Int): String {
        val addressText = requireNotNull(address.hostAddress) {
            "Resolved address has no textual representation"
        }
        val host = if (address is Inet6Address) {
            "[${addressText.replace("%", "%25")}]"
        } else {
            addressText
        }
        return "http://$host:$port/"
    }
}

class ServiceLaunchActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var nsdManager: NsdManager
    private lateinit var serviceName: String
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var timeout: Runnable? = null
    private var finished = false

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                discoverService()
            } else {
                fail(getString(R.string.local_network_permission_required))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            setPadding(48, 48, 48, 48)
            setText(R.string.resolving_service)
            textSize = 18f
        })

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        serviceName = ServiceWidgetProvider.getServiceName(this, widgetId)
            ?: run {
                fail(getString(R.string.widget_service_not_configured))
                return
            }
        nsdManager = getSystemService(NsdManager::class.java)

        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_API &&
            ContextCompat.checkSelfPermission(
                this,
                LOCAL_NETWORK_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            discoverService()
        }
    }

    private fun discoverService() {
        if (finished) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                fail(getString(R.string.service_scan_failed))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Could not stop HTTP service discovery: $errorCode")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!finished && serviceInfo.serviceName == serviceName) {
                    stopDiscovery()
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices(
                ServiceWidgetSettings.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
            setTimeout(SERVICE_LOOKUP_TIMEOUT_MS) {
                fail(getString(R.string.service_not_found, serviceName))
            }
        } catch (exception: IllegalArgumentException) {
            fail(getString(R.string.service_scan_failed))
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(service: NsdServiceInfo) {
        clearTimeout()
        setTimeout(SERVICE_LOOKUP_TIMEOUT_MS) {
            fail(getString(R.string.service_resolution_failed, serviceName))
        }
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    fail(getString(R.string.service_resolution_failed, serviceName))
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val addresses = resolvedAddresses(serviceInfo)
                    val address = addresses.firstOrNull { it is Inet4Address }
                        ?: addresses.firstOrNull()
                    if (address == null || serviceInfo.port !in 1..65535) {
                        fail(getString(R.string.service_resolution_failed, serviceName))
                        return
                    }
                    openBrowser(ServiceEndpoint.url(address, serviceInfo.port))
                }
            })
        } catch (exception: IllegalArgumentException) {
            fail(getString(R.string.service_resolution_failed, serviceName))
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvedAddresses(serviceInfo: NsdServiceInfo): List<InetAddress> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return serviceInfo.hostAddresses.ifEmpty { listOfNotNull(serviceInfo.host) }
        }
        return listOfNotNull(serviceInfo.host)
    }

    private fun openBrowser(url: String) {
        if (finished) return
        finished = true
        clearTimeout()
        try {
            startActivity(android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                Uri.parse(url)
            ))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun fail(message: String) {
        if (finished) return
        finished = true
        clearTimeout()
        stopDiscovery()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun setTimeout(delayMs: Long, action: () -> Unit) {
        clearTimeout()
        timeout = Runnable(action).also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun clearTimeout() {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
    }

    private fun stopDiscovery() {
        discoveryListener?.let { listener ->
            discoveryListener = null
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not stop HTTP service discovery", exception)
            }
        }
    }

    override fun onDestroy() {
        clearTimeout()
        stopDiscovery()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ServiceLaunch"
        private const val SERVICE_LOOKUP_TIMEOUT_MS = 8_000L
        private const val LOCAL_NETWORK_PERMISSION_API = 37
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
