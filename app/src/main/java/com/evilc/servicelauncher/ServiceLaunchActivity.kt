package com.evilc.servicelauncher

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Inet6Address
import java.net.URLEncoder

internal object ServiceEndpoint {
    fun url(address: InetAddress, port: Int): String =
        url("http://{host}:{port}/", address, port)

    fun url(template: String, address: InetAddress, port: Int, username: String? = null): String {
        val url = template.replace("{host}", formattedHost(address)).replace("{port}", port.toString())
        val scheme = url.substringBefore("://").lowercase()
        if (username.isNullOrBlank() || scheme == "http" || scheme == "https") return url
        val encoded = URLEncoder.encode(username, "UTF-8").replace("+", "%20")
        return url.replaceFirst("://", "://$encoded@")
    }

    fun endpoint(address: InetAddress, port: Int): String =
        "${formattedHost(address)}:$port"

    private fun formattedHost(address: InetAddress): String {
        val addressText = requireNotNull(address.hostAddress) {
            "Resolved address has no textual representation"
        }
        return if (address is Inet6Address) {
            "[${addressText.replace("%", "%25")}]"
        } else {
            addressText
        }
    }
}

class ServiceLaunchActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var nsdManager: NsdManager
    private lateinit var serviceName: String
    private lateinit var serviceType: String
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var unregisterServiceInfoCallback: (() -> Unit)? = null
    private var timeout: Runnable? = null
    private var webView: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }
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
        setAppifiedTaskDescription()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentWebView = webView
                if (currentWebView?.canGoBack() == true) {
                    currentWebView.goBack()
                } else {
                    finish()
                }
            }
        })
        setContentView(TextView(this).apply {
            setPadding(48, 48, 48, 48)
            setText(R.string.resolving_service)
            textSize = 18f
        })

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        serviceName = intent.getStringExtra(ServiceWidgetSettings.EXTRA_SERVICE_NAME)
            ?: ServiceWidgetProvider.getServiceName(this, widgetId)
            ?: run {
                fail(getString(R.string.widget_service_not_configured))
                return
            }
        serviceType = intent.getStringExtra(ServiceWidgetSettings.EXTRA_SERVICE_TYPE)
            ?: if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ServiceWidgetProvider.getServiceType(this, widgetId)
            } else {
                ServiceWidgetSettings.SERVICE_TYPE
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
                Log.w(TAG, "Could not stop $serviceType discovery: $errorCode")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!finished &&
                    serviceInfo.serviceName == serviceName &&
                    serviceInfo.serviceType == serviceType
                ) {
                    stopDiscovery()
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }

        val mainThreadListener = listener.onHandler(mainHandler)
        discoveryListener = mainThreadListener
        try {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                mainThreadListener
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val resolver = NsdServiceInfoResolver(
                nsdManager,
                ContextCompat.getMainExecutor(this),
                service,
                onUpdated = { serviceInfo ->
                    if (!finished) {
                        stopServiceInfoCallback()
                        openResolvedService(serviceInfo)
                    }
                },
                onLost = { fail(getString(R.string.service_not_found, serviceName)) },
                onRegistrationFailed = {
                    fail(getString(R.string.service_resolution_failed, serviceName))
                }
            )
            unregisterServiceInfoCallback = resolver::unregister
            try {
                resolver.register()
            } catch (exception: IllegalArgumentException) {
                fail(getString(R.string.service_resolution_failed, serviceName))
            }
            return
        }

        try {
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        mainHandler.post {
                            fail(getString(R.string.service_resolution_failed, serviceName))
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        mainHandler.post { openResolvedService(serviceInfo) }
                    }
                }
            )
        } catch (exception: IllegalArgumentException) {
            fail(getString(R.string.service_resolution_failed, serviceName))
        }
    }

    private fun openResolvedService(serviceInfo: NsdServiceInfo) {
        val addresses = resolvedAddresses(serviceInfo)
        val address = addresses.firstOrNull { it is Inet4Address }
            ?: addresses.firstOrNull()
        if (address == null || serviceInfo.port !in 1..65535) {
            fail(getString(R.string.service_resolution_failed, serviceName))
            return
        }
        val template = ServiceTypes.find(this, serviceType)?.urlTemplate
        if (template == null) {
            copyEndpointToClipboard(ServiceEndpoint.endpoint(address, serviceInfo.port))
            return
        }
        val username = ServiceMetadata.from(serviceInfo.attributes).username
        openService(
            ServiceEndpoint.url(template, address, serviceInfo.port, username),
            ServiceEndpoint.endpoint(address, serviceInfo.port)
        )
    }

    @Suppress("DEPRECATION")
    private fun resolvedAddresses(serviceInfo: NsdServiceInfo): List<InetAddress> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return serviceInfo.hostAddresses.ifEmpty { listOfNotNull(serviceInfo.host) }
        }
        return listOfNotNull(serviceInfo.host)
    }

    private fun openService(url: String, endpoint: String) {
        if (finished) return
        if (intent.getBooleanExtra(ServiceWidgetSettings.EXTRA_APPIFY, false)) {
            finished = true
            clearTimeout()
            val serviceHost = url.toUri().host
            val view = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView,
                        callback: ValueCallback<Array<Uri>>,
                        params: FileChooserParams
                    ): Boolean {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = callback
                        return try {
                            fileChooser.launch(params.createIntent())
                            true
                        } catch (_: ActivityNotFoundException) {
                            fileCallback = null
                            false
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (target.host.equals(serviceHost, ignoreCase = true)) return false
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, target))
                        } catch (_: ActivityNotFoundException) {
                        }
                        return true
                    }
                }
            }
            webView = view
            val container = FrameLayout(this).apply { addView(view) }
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
            setContentView(container)
            val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            WindowInsetsControllerCompat(window, container).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
            container.setBackgroundColor(if (night) Color.BLACK else Color.WHITE)
            view.loadUrl(url)
            return
        }
        finished = true
        clearTimeout()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            copyEndpointToClipboard(endpoint)
        }
        finish()
    }

    private fun copyEndpointToClipboard(endpoint: String) {
        if (finished) return
        finished = true
        clearTimeout()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), endpoint))
        Toast.makeText(this, R.string.endpoint_copied_to_clipboard, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun fail(message: String) {
        if (finished) return
        finished = true
        clearTimeout()
        stopDiscovery()
        stopServiceInfoCallback()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    @Suppress("DEPRECATION")
    private fun setAppifiedTaskDescription() {
        if (!intent.getBooleanExtra(ServiceWidgetSettings.EXTRA_APPIFY, false)) return
        val label = intent.getStringExtra(ServiceWidgetSettings.EXTRA_SHORTCUT_LABEL)
            ?: getString(R.string.app_name)
        val iconText = intent.getStringExtra(ServiceWidgetSettings.EXTRA_SHORTCUT_ICON_TEXT)
            ?.ifBlank { ServiceShortcuts.initials(label) }
            ?: ServiceShortcuts.initials(label)
        val icon = ServiceShortcuts.createIcon(iconText)
        setTaskDescription(ActivityManager.TaskDescription(label, icon))
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
                Log.w(TAG, "Could not stop $serviceType discovery", exception)
            }
        }
    }

    private fun stopServiceInfoCallback() {
        unregisterServiceInfoCallback?.let { unregister ->
            unregisterServiceInfoCallback = null
            try {
                unregister()
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not unregister $serviceType service info callback", exception)
            }
        }
    }

    override fun onDestroy() {
        clearTimeout()
        stopDiscovery()
        stopServiceInfoCallback()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ServiceLaunch"
        private const val SERVICE_LOOKUP_TIMEOUT_MS = 8_000L
        private const val LOCAL_NETWORK_PERMISSION_API = 37
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
