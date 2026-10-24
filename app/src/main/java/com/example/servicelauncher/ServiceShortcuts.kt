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
    private const val SAFE_FRACTION = 0.56f
    private const val MIN_TEXT_PX = 20
    private const val MAX_TEXT_PX = 200

    fun initials(label: String): String =
        label.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    fun isPinSupported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, serviceName: String, label: String, iconText: String): Boolean =
        ShortcutManagerCompat.requestPinShortcut(
            context,
            build(context, "pin_${UUID.randomUUID()}", serviceName, label, iconText),
            null
        )

    private fun build(
        context: Context,
        id: String,
        serviceName: String,
        label: String,
        iconText: String
    ) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(createIcon(iconText.ifBlank { initials(label) })))
            .setIntent(
                Intent(context, ServiceLaunchActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(ServiceWidgetSettings.EXTRA_SERVICE_NAME, serviceName)
            )
            .build()

    private fun createIcon(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(TILE_COLOR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val safe = ICON_SIZE * SAFE_FRACTION
        val size = TextFit.largestSize(
            text,
            MIN_TEXT_PX,
            MAX_TEXT_PX,
            safe,
            safe,
            lineHeight = { paint.textSize = it.toFloat(); paint.fontSpacing },
            measure = { px, word -> paint.textSize = px.toFloat(); paint.measureText(word) }
        )
        paint.textSize = size.toFloat()
        val lines = wrap(text, safe, paint)
        val top = (ICON_SIZE - lines.size * paint.fontSpacing) / 2f - paint.ascent()
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, ICON_SIZE / 2f, top + index * paint.fontSpacing, paint)
        }
        return bitmap
    }

    private fun wrap(text: String, width: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        for (line in text.split('\n')) {
            var current = ""
            for (word in line.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && paint.measureText(candidate) > width) {
                    lines += current
                    current = word
                } else {
                    current = candidate
                }
            }
            lines += current
        }
        return lines
    }
}
