package com.example.servicelauncher

/** Default texts advertised by a service in its DNS-SD TXT record. */
internal data class ServiceMetadata(val widgetText: String?, val iconText: String?, val iconLabel: String?) {
    companion object {
        const val KEY_WIDGET_TEXT = "sl-wtext"
        const val KEY_ICON_TEXT = "sl-stext"
        const val KEY_ICON_LABEL = "sl-slabel"

        fun from(attributes: Map<String, ByteArray?>) = ServiceMetadata(
            widgetText = attributes[KEY_WIDGET_TEXT].decode(),
            iconText = attributes[KEY_ICON_TEXT].decode(),
            iconLabel = attributes[KEY_ICON_LABEL].decode()
        )

        private fun ByteArray?.decode() = this
            ?.toString(Charsets.UTF_8)
            ?.replace("\\n", "\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
