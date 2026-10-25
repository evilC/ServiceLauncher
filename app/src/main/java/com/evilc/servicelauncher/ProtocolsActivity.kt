package com.evilc.servicelauncher

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ProtocolsActivity : AppCompatActivity() {
    private lateinit var rows: LinearLayout
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        title = getString(R.string.protocols_title)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val padding = (16 * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply { setText(R.string.protocols_description) })
            addView(rows)
            addView(Button(context).apply {
                setText(R.string.add_service_type)
                setOnClickListener { showAddDialog() }
            })
        }
        setContentView(ScrollView(this).apply { addView(content) })
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(
                padding + bars.left,
                padding + bars.top,
                padding + bars.right,
                padding + bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
        refresh()
    }

    private fun refresh() {
        rows.removeAllViews()
        ServiceTypes.all(this).forEach { config -> rows.addView(row(config)) }
    }

    private fun row(config: ServiceTypeConfig) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(CheckBox(context).apply {
            text = "${config.label}\n${config.type.removeSuffix(".")}"
            isChecked = config.enabled
            setOnCheckedChangeListener { _, checked ->
                ServiceTypes.setEnabled(this@ProtocolsActivity, config.type, checked)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply {
            setText(R.string.edit)
            setOnClickListener { showEditDialog(config) }
        })
        if (!config.builtIn) {
            addView(Button(context).apply {
                setText(R.string.delete)
                setOnClickListener { confirmDelete(config) }
            })
        }
    }

    private fun confirmDelete(config: ServiceTypeConfig) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_protocol_title)
            .setMessage(getString(R.string.delete_protocol_message, config.label))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                ServiceTypes.deleteCustom(this, config.type)
                refresh()
            }
            .show()
    }

    private fun showEditDialog(config: ServiceTypeConfig) {
        val schemeInput = EditText(this).apply {
            setText(config.scheme)
            setSingleLine()
        }
        val fields = dialogFields().apply {
            addSection(R.string.service_type_title, null, TextView(context).apply {
                text = config.type.removeSuffix(".")
            })
            addSection(R.string.launch_url_template_title, R.string.launch_url_template_instructions, schemeInput)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_protocol_title, config.label))
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (config.builtIn) {
            builder.setNeutralButton(R.string.reset_to_defaults) { _, _ ->
                ServiceTypes.reset(this, config.type)
                refresh()
            }
        }
        builder.create().also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (ServiceTypes.setScheme(this, config.type, schemeInput.text.toString())) {
                        dialog.dismiss()
                        refresh()
                    } else {
                        Toast.makeText(this, R.string.invalid_scheme, Toast.LENGTH_LONG).show()
                    }
                }
            }
            dialog.show()
        }
    }

    private fun showAddDialog() {
        val typeInput = EditText(this).apply {
            hint = getString(R.string.service_type_hint)
            setSingleLine()
        }
        val transports = listOf("TCP", "UDP")
        val transportSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ProtocolsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                transports
            )
        }
        val schemeInput = EditText(this).apply {
            hint = getString(R.string.launch_url_template_hint)
            setSingleLine()
        }
        val fields = dialogFields().apply {
            addSection(R.string.service_type_title, R.string.service_type_instructions, typeInput)
            addSection(R.string.transport_title, R.string.transport_instructions, transportSpinner)
            addSection(R.string.launch_url_template_title, R.string.launch_url_template_instructions, schemeInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_service_type)
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val config = ServiceTypes.addCustom(
                            this,
                            typeInput.text.toString(),
                            transports[transportSpinner.selectedItemPosition],
                            schemeInput.text.toString()
                        )
                        if (config == null) {
                            Toast.makeText(
                                this,
                                R.string.invalid_custom_service_type,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            dialog.dismiss()
                            refresh()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun dialogFields() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val horizontalPadding = (24 * density).toInt()
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
    }

    private fun LinearLayout.addSection(titleRes: Int, instructionsRes: Int?, field: android.view.View) {
        addView(TextView(context).apply {
            setText(titleRes)
            setTypeface(typeface, Typeface.BOLD)
            if (childCount > 0) setPadding(0, (16 * density).toInt(), 0, 0)
        })
        if (instructionsRes != null) addView(TextView(context).apply { setText(instructionsRes) })
        addView(field)
    }
}
