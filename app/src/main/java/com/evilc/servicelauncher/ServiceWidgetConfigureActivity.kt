package com.evilc.servicelauncher

import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ServiceWidgetConfigureActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val services = mutableListOf<NsdServiceInfo>()
    private lateinit var status: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var list: ListView
    private lateinit var configForm: LinearLayout
    private lateinit var labelInput: EditText
    private lateinit var iconTextInput: EditText
    private var iconTextEdited = false
    private var updatingIconText = false
    private lateinit var sizeSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var nsdManager: NsdManager
    private var selectedName: String? = null
    private var metadata = ServiceMetadata(null, null, null)
    private var metadataFor: String? = null
    private var stopMetadataLookup: (() -> Unit)? = null
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var shortcutMode = false
    private lateinit var sizeTitle: TextView
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var scanTimeout: Runnable? = null
    private var selectedType = ServiceWidgetSettings.SERVICE_TYPE
    private var scanFailureCount = 0

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startScan()
            } else {
                showStatus(R.string.local_network_permission_required)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        shortcutMode = intent.getBooleanExtra(ServiceWidgetSettings.EXTRA_SHORTCUT_MODE, false)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (!shortcutMode && widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        nsdManager = getSystemService(NsdManager::class.java)
        setContentView(createContentView())
        if (!shortcutMode) loadExistingSettings()
        requestPermissionOrScan()
    }

    private fun loadExistingSettings() {
        val name = ServiceWidgetProvider.getServiceName(this, widgetId) ?: return
        selectedType = ServiceWidgetProvider.getServiceType(this, widgetId)
        selectService(name, selectedType)
        labelInput.setText(ServiceWidgetProvider.getLabel(this, widgetId) ?: name)
        sizeSpinner.setSelection(ServiceWidgetProvider.getTextSize(this, widgetId).ordinal)
    }

    private fun selectService(name: String, type: String) {
        val previousDefault = defaultLabel()
        if (name != selectedName || type != selectedType) {
            stopMetadataLookup?.invoke()
            stopMetadataLookup = null
            metadataFor = null
            metadata = ServiceMetadata(null, null, null)
        }
        selectedName = name
        selectedType = type
        setFormEnabled(true)
        val current = labelInput.text.toString()
        if (current.isBlank() || current == previousDefault) {
            labelInput.setText(defaultLabel())
        }
        syncChecked()
        lookUpMetadata()
    }

    private fun defaultLabel() =
        (if (shortcutMode) metadata.iconLabel else metadata.widgetText) ?: selectedName.orEmpty()

    private fun defaultIconText() =
        metadata.iconText ?: ServiceShortcuts.initials(labelInput.text.toString())

    private fun resetIconText() {
        iconTextEdited = false
        updatingIconText = true
        iconTextInput.setText(defaultIconText())
        updatingIconText = false
    }

    @Suppress("DEPRECATION")
    private fun lookUpMetadata() {
        val name = selectedName ?: return
        val service = services.firstOrNull {
            it.serviceName == name && it.serviceType == selectedType
        } ?: return
        val key = "$selectedType|$name"
        if (metadataFor == key) return
        metadataFor = key

        fun deliver(info: NsdServiceInfo) {
            if (selectedName != name || selectedType != service.serviceType) return
            val previousLabel = defaultLabel()
            metadata = ServiceMetadata.from(info.attributes)
            if (labelInput.text.isBlank() || labelInput.text.toString() == previousLabel) {
                labelInput.setText(defaultLabel())
            }
            if (shortcutMode && !iconTextEdited) resetIconText()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lateinit var resolver: NsdServiceInfoResolver
            resolver = NsdServiceInfoResolver(
                nsdManager,
                ContextCompat.getMainExecutor(this),
                service,
                onUpdated = { info ->
                    stopMetadataLookup?.invoke()
                    stopMetadataLookup = null
                    deliver(info)
                },
                onLost = {},
                onRegistrationFailed = {}
            )
            try {
                resolver.register()
                stopMetadataLookup = resolver::unregister
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not read service attributes", exception)
            }
            return
        }

        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    mainHandler.post { deliver(serviceInfo) }
                }
            })
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Could not read service attributes", exception)
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
        configForm.alpha = if (enabled) 1f else 0.4f
        configForm.setEnabledRecursively(enabled)
    }

    private fun View.setEnabledRecursively(enabled: Boolean) {
        isEnabled = enabled
        if (this is ViewGroup) children.forEach { it.setEnabledRecursively(enabled) }
    }

    private fun showStatus(textRes: Int) {
        status.setText(textRes)
        status.visibility = View.VISIBLE
    }

    private fun syncChecked() {
        val index = services.indexOfFirst {
            it.serviceName == selectedName && it.serviceType == selectedType
        }
        if (index >= 0) list.setItemChecked(index, true) else list.clearChoices()
    }

    private fun createContentView(): LinearLayout {
        val padding = (20 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                )
                view.setPadding(
                    padding + bars.left,
                    padding + bars.top,
                    padding + bars.right,
                    padding + bars.bottom
                )
                WindowInsetsCompat.CONSUMED
            }

            val servicePanel = panel(R.string.service_panel_title, fill = true)
            addView(servicePanel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            status = TextView(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.searching_for_services)
                setPadding(0, padding / 2, 0, padding / 2)
            }
            servicePanel.body().addView(status)

            adapter = ArrayAdapter(
                this@ServiceWidgetConfigureActivity,
                android.R.layout.simple_list_item_single_choice,
                mutableListOf<String>()
            )
            list = ListView(this@ServiceWidgetConfigureActivity).apply {
                choiceMode = ListView.CHOICE_MODE_SINGLE
                adapter = this@ServiceWidgetConfigureActivity.adapter
                setOnItemClickListener { _, _, position, _ ->
                    selectService(services[position].serviceName, services[position].serviceType)
                }
            }
            servicePanel.body().addView(list, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            servicePanel.body().addView(Button(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.scan_again)
                setOnClickListener { requestPermissionOrScan() }
            })

            configForm = panel(R.string.settings_panel_title)
            labelInput = EditText(this@ServiceWidgetConfigureActivity).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 2
                maxLines = 4
                gravity = Gravity.TOP
            }
            configForm.body().addView(fieldRow(R.string.label_title, labelInput))
            configForm.body().addView(Button(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.reset)
                setOnClickListener { labelInput.setText(defaultLabel()) }
            })
            if (shortcutMode) {
                iconTextInput = EditText(this@ServiceWidgetConfigureActivity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 2
                    maxLines = 4
                    gravity = Gravity.TOP
                    addTextChangedListener {
                        if (!updatingIconText) iconTextEdited = true
                    }
                }
                labelInput.addTextChangedListener {
                    if (!iconTextEdited) {
                        updatingIconText = true
                        iconTextInput.setText(ServiceShortcuts.initials(labelInput.text.toString()))
                        updatingIconText = false
                    }
                }
                configForm.body().addView(fieldRow(R.string.icon_text_title, iconTextInput))
                configForm.body().addView(Button(this@ServiceWidgetConfigureActivity).apply {
                    setText(R.string.reset)
                    setOnClickListener { resetIconText() }

                })
            }
            sizeTitle = TextView(this@ServiceWidgetConfigureActivity).apply {
                setText(R.string.text_size_title)
                setPadding(0, padding / 2, 0, 0)
            }
            configForm.body().addView(sizeTitle)
            sizeSpinner = Spinner(this@ServiceWidgetConfigureActivity).apply {
                adapter = ArrayAdapter(
                    this@ServiceWidgetConfigureActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    WidgetTextSize.entries.map { getString(it.labelRes) }
                )
            }
            configForm.body().addView(sizeSpinner)
            if (shortcutMode) {
                sizeTitle.visibility = View.GONE
                sizeSpinner.visibility = View.GONE
            }
            addView(configForm, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = padding / 2; bottomMargin = padding / 2 })
            saveButton = Button(this@ServiceWidgetConfigureActivity).apply {
                setText(if (shortcutMode) R.string.create_icon else R.string.save_widget)
                isEnabled = false
                setOnClickListener {
                    if (shortcutMode) createPinnedShortcut() else completeConfiguration()
                }
            }
            addView(LinearLayout(this@ServiceWidgetConfigureActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                val weight = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(Button(this@ServiceWidgetConfigureActivity).apply {
                    setText(R.string.cancel)
                    setOnClickListener {
                        stopScan()
                        finishAndRemoveTask()
                    }
                }, weight)
                addView(saveButton, weight)
            })
            setFormEnabled(false)
        }
    }

    private fun panel(titleRes: Int, fill: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val inner = (12 * resources.displayMetrics.density).toInt()
        setBackgroundResource(R.drawable.panel_background)
        addView(TextView(context).apply {
            setText(titleRes)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.panel_header_background)
            setPadding(inner, inner / 2, inner, inner / 2)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(inner, inner / 2, inner, inner)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (fill) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            if (fill) 1f else 0f
        ))
    }

    private fun LinearLayout.body() = getChildAt(1) as LinearLayout
    private fun fieldRow(titleRes: Int, input: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val titleWidth = (90 * resources.displayMetrics.density).toInt()
        addView(TextView(context).apply {
            setText(titleRes)
            gravity = Gravity.TOP
            setPadding(0, input.paddingTop, 0, 0)
        }, LinearLayout.LayoutParams(titleWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        val types = ServiceTypes.enabled(this)
        scanFailureCount = 0
        if (types.isEmpty()) {
            showStatus(R.string.no_protocols_enabled)
            return
        }
        showStatus(R.string.searching_for_services)

        types.forEach { typeConfig ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    scanFailureCount++
                    if (scanFailureCount == types.size && services.isEmpty()) {
                        stopScan()
                        showStatus(R.string.service_scan_failed)
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    showStatus(R.string.service_scan_failed)
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (services.none {
                            it.serviceName == serviceInfo.serviceName &&
                                it.serviceType == typeConfig.type
                        }
                    ) {
                        services += serviceInfo
                        adapter.add(typeConfig.displayName(serviceInfo.serviceName))
                        status.visibility = View.GONE
                        syncChecked()
                        lookUpMetadata()
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val index = services.indexOfFirst {
                        it.serviceName == serviceInfo.serviceName &&
                            it.serviceType == typeConfig.type
                    }
                    if (index >= 0) {
                        services.removeAt(index)
                        adapter.remove(typeConfig.displayName(serviceInfo.serviceName))
                        syncChecked()
                        if (services.isEmpty()) {
                            showStatus(R.string.searching_for_services)
                        }
                    }
                }
            }.onHandler(mainHandler)
            discoveryListeners += listener
            try {
                nsdManager.discoverServices(
                    typeConfig.type,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
            } catch (exception: IllegalArgumentException) {
                discoveryListeners.remove(listener)
                scanFailureCount++
                Log.w(TAG, "Could not scan for ${typeConfig.type}", exception)
            }
        }

        if (types.isEmpty() || scanFailureCount == types.size) {
            stopScan()
            showStatus(R.string.service_scan_failed)
            return
        }
        scanTimeout = Runnable {
            stopScan()
            if (services.isEmpty()) showStatus(R.string.no_services_found)
        }.also { mainHandler.postDelayed(it, SCAN_TIMEOUT_MS) }
    }

    private fun stopScan() {
        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = null
        discoveryListeners.toList().forEach { listener ->
            discoveryListeners.remove(listener)
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not stop service discovery", exception)
            }
        }
    }

    private fun shortcutLabel(name: String) =
        labelInput.text.toString().trim().replace(Regex("\\s+"), " ").ifEmpty { name }

    private fun createPinnedShortcut() {
        val name = selectedName ?: return
        if (ServiceShortcuts.isPinSupported(this) &&
            ServiceShortcuts.requestPin(
                this,
                name,
                selectedType,
                shortcutLabel(name),
                iconTextInput.text.toString().trim()
            )
        ) {
            stopScan()
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, R.string.pin_shortcut_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun completeConfiguration() {
        val name = selectedName ?: return
        stopScan()
        val label = labelInput.text.toString().trim().takeIf { it.isNotEmpty() && it != name }
        val textSize = WidgetTextSize.entries[sizeSpinner.selectedItemPosition]
        ServiceWidgetProvider.saveSettings(this, widgetId, name, selectedType, label, textSize)
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
        stopMetadataLookup?.invoke()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ServiceWidgetConfig"
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val LOCAL_NETWORK_PERMISSION_API = 37
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
