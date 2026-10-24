package com.example.servicelauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.UUID

internal object ServiceShortcuts {
    private const val ICON_SIZE = 432
    private const val TILE_COLOR = 0xFF455A64.toInt()

    fun initials(label: String): String =
        label.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    fun isPinSupported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, serviceName: String, label: String): Boolean =
        ShortcutManagerCompat.requestPinShortcut(
            context,
            build(context, "pin_${UUID.randomUUID()}", serviceName, label),
            null
        )

    private fun build(context: Context, id: String, serviceName: String, label: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(createIcon(label)))
            .setIntent(
                Intent(context, ServiceLaunchActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(ServiceWidgetSettings.EXTRA_SERVICE_NAME, serviceName)
            )
            .build()

    private fun createIcon(label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(TILE_COLOR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = ICON_SIZE * 0.3f
            textAlign = Paint.Align.CENTER
        }
        val baseline = ICON_SIZE / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(initials(label), ICON_SIZE / 2f, baseline, paint)
        return bitmap
    }
}
