package com.example.servicelauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.content.edit

enum class WidgetTextSize(@StringRes val labelRes: Int, val sp: Float?) {
    AUTO(R.string.text_size_auto, null),
    SMALL(R.string.text_size_small, 11f),
    MEDIUM(R.string.text_size_medium, 14f),
    LARGE(R.string.text_size_large, 18f);

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

internal object ServiceWidgetSettings {
    const val SERVICE_TYPE = "_http._tcp."
    const val EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID
    const val EXTRA_SERVICE_NAME = "service_name"
    const val EXTRA_SHORTCUT_MODE = "shortcut_mode"

    fun serviceNameKey(widgetId: Int) = "service_name_$widgetId"
    fun labelKey(widgetId: Int) = "label_$widgetId"
    fun textSizeKey(widgetId: Int) = "text_size_$widgetId"

    fun displayText(label: String?, serviceName: String?): String? =
        label?.takeIf { it.isNotBlank() } ?: serviceName
}

class ServiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            appWidgetIds.forEach {
                remove(ServiceWidgetSettings.serviceNameKey(it))
                remove(ServiceWidgetSettings.labelKey(it))
                remove(ServiceWidgetSettings.textSizeKey(it))
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "service_widgets"
        private const val AUTO_MAX_SP = 18f
        private const val AUTO_MIN_SP = 8f
        private const val WIDGET_PADDING_DP = 6
        private const val CORNER_INSET_DP = 10

        fun saveSettings(
            context: Context,
            widgetId: Int,
            serviceName: String,
            label: String?,
            textSize: WidgetTextSize
        ) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
                putString(ServiceWidgetSettings.serviceNameKey(widgetId), serviceName)
                putString(ServiceWidgetSettings.labelKey(widgetId), label)
                putString(ServiceWidgetSettings.textSizeKey(widgetId), textSize.name)
            }
        }

        fun getServiceName(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(ServiceWidgetSettings.serviceNameKey(widgetId), null)

        fun getLabel(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(ServiceWidgetSettings.labelKey(widgetId), null)

        fun getTextSize(context: Context, widgetId: Int): WidgetTextSize =
            WidgetTextSize.parse(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getString(ServiceWidgetSettings.textSizeKey(widgetId), null)
            )

        // Largest size where the text fits without breaking inside a word.
        private fun fitTextSp(context: Context, text: String, options: Bundle): Float {
            val density = context.resources.displayMetrics.density
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                0
            )
            val heightDp = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                0
            )
            if (widthDp == 0 || heightDp == 0) return AUTO_MAX_SP
            val availableWidth = (widthDp - WIDGET_PADDING_DP - CORNER_INSET_DP) * density
            val availableHeight = (heightDp - WIDGET_PADDING_DP) * density
            val paint = Paint().apply { typeface = Typeface.DEFAULT_BOLD }

            for (sp in AUTO_MAX_SP.toInt() downTo AUTO_MIN_SP.toInt()) {
                paint.textSize = sp * context.resources.displayMetrics.scaledDensity
                if (wrappedLineCount(text, paint, availableWidth) * paint.fontSpacing <= availableHeight) {
                    return sp.toFloat()
                }
            }
            return AUTO_MIN_SP
        }

        // Returns Int.MAX_VALUE if any single word is wider than the line.
        private fun wrappedLineCount(text: String, paint: Paint, width: Float): Int {
            var lines = 0
            for (line in text.split('\n')) {
                var current = 0f
                var count = 1
                for (word in line.split(' ').filter { it.isNotEmpty() }) {
                    val wordWidth = paint.measureText(word)
                    if (wordWidth > width) return Int.MAX_VALUE
                    val next = if (current == 0f) wordWidth else current + paint.measureText(" $word")
                    if (next > width) {
                        count++
                        current = wordWidth
                    } else {
                        current = next
                    }
                }
                lines += count
            }
            return lines
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val serviceName = getServiceName(context, widgetId)
            val text = ServiceWidgetSettings.displayText(getLabel(context, widgetId), serviceName)
                ?: context.getString(R.string.widget_unconfigured)
            val textSize = getTextSize(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_service)
            val sp = textSize.sp ?: fitTextSp(context, text, appWidgetManager.getAppWidgetOptions(widgetId))
            views.setTextViewText(R.id.widget_service_name, text)
            views.setTextViewTextSize(R.id.widget_service_name, TypedValue.COMPLEX_UNIT_SP, sp)

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
