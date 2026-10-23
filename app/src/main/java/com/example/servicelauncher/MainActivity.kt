package com.example.servicelauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<View>(R.id.add_widget_button).setOnClickListener { requestPinWidget() }
    }

    private fun requestPinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, ServiceWidgetProvider::class.java)
        val configureIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ServiceWidgetConfigureActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        if (!manager.isRequestPinAppWidgetSupported ||
            !manager.requestPinAppWidget(provider, null, configureIntent)
        ) {
            Toast.makeText(this, R.string.pin_widget_unsupported, Toast.LENGTH_LONG).show()
        } else {
            finish()
        }
    }
}