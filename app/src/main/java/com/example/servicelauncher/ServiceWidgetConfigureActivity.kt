package com.example.servicelauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ServiceWidgetConfigureActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val services = mutableListOf<NsdServiceInfo>()
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var nsdManager: NsdManager
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var scanTimeout: Runnable? = null

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScan()
            } else {
                status.setText(R.string.local_network_permission_required)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        nsdManager = getSystemService(NsdManager::class.java)
        setContentView(createContentView())
        requestPermissionOrScan()
    }

    private fun createContentView(): LinearLayout {
        val padding = (20 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            addView(TextView(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.widget_config_instructions)
                textSize = 20f
            })
            status = TextView(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.searching_for_services)
                setPadding(0, padding / 2, 0, padding / 2)
            }
            addView(status)

            adapter = ArrayAdapter(
                this@ServiceWidgetConfigureActivity,
                android.R.layout.simple_list_item_1,
                mutableListOf<String>()
            )
            val list = ListView(this@ServiceWidgetConfigureActivity).apply {
                adapter = this@ServiceWidgetConfigureActivity.adapter
                setOnItemClickListener { _, _, position, _ ->
                    completeConfiguration(services[position])
                }
            }
            addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            addView(Button(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.scan_again)
                setOnClickListener { requestPermissionOrScan() }
            })
        }
    }

    private fun requestPermissionOrScan() {
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_API &&
            ContextCompat.checkSelfPermission(
                this,
                LOCAL_NETWORK_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            startScan()
        }
    }

    private fun startScan() {
        stopScan()
        services.clear()
        adapter.clear()
        status.setText(R.string.searching_for_services)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopScan()
                status.setText(R.string.service_scan_failed)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                status.setText(R.string.service_scan_failed)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (services.none { it.serviceName == serviceInfo.serviceName }) {
                    services += serviceInfo
                    adapter.add(serviceInfo.serviceName)
                    status.setText(R.string.select_a_service)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val index = services.indexOfFirst { it.serviceName == serviceInfo.serviceName }
                if (index >= 0) {
                    services.removeAt(index)
                    adapter.remove(serviceInfo.serviceName)
                    if (services.isEmpty()) {
                        status.setText(R.string.searching_for_services)
                    }
                }
            }
        }

        val mainThreadListener = listener.onHandler(mainHandler)
        discoveryListener = mainThreadListener
        try {
            nsdManager.discoverServices(
                ServiceWidgetSettings.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                mainThreadListener
            )
            scanTimeout = Runnable {
                stopScan()
                if (services.isEmpty()) {
                    status.setText(R.string.no_services_found)
                }
            }.also { mainHandler.postDelayed(it, SCAN_TIMEOUT_MS) }
        } catch (exception: IllegalArgumentException) {
            discoveryListener = null
            status.setText(R.string.service_scan_failed)
        }
    }

    private fun stopScan() {
        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = null
        discoveryListener?.let { listener ->
            discoveryListener = null
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not stop HTTP service discovery", exception)
                status.setText(R.string.service_scan_failed)
            }
        }
    }

    private fun completeConfiguration(service: NsdServiceInfo) {
        stopScan()
        ServiceWidgetProvider.saveServiceName(this, widgetId, service.serviceName)
        val manager = AppWidgetManager.getInstance(this)
        ServiceWidgetProvider.updateWidget(this, manager, widgetId)

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ServiceWidgetConfig"
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val LOCAL_NETWORK_PERMISSION_API = 37
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
