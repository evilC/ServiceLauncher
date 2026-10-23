package com.example.servicelauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit

internal object ServiceWidgetSettings {
    const val SERVICE_TYPE = "_http._tcp."
    const val EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID

    fun serviceNameKey(widgetId: Int) = "service_name_$widgetId"
}

class ServiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            appWidgetIds.forEach { remove(ServiceWidgetSettings.serviceNameKey(it)) }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "service_widgets"

        fun saveServiceName(context: Context, widgetId: Int, serviceName: String) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit { putString(ServiceWidgetSettings.serviceNameKey(widgetId), serviceName) }
        }

        fun getServiceName(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(ServiceWidgetSettings.serviceNameKey(widgetId), null)

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val serviceName = getServiceName(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_service)
            views.setTextViewText(
                R.id.widget_service_name,
                serviceName ?: context.getString(R.string.widget_unconfigured)
            )

            val target = if (serviceName == null) {
                ServiceWidgetConfigureActivity::class.java
            } else {
                ServiceLaunchActivity::class.java
            }
            val launchIntent = Intent(context, target)
                .putExtra(ServiceWidgetSettings.EXTRA_WIDGET_ID, widgetId)
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
