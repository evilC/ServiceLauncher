package com.evilc.servicelauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal data class ServiceTypeConfig(
    val type: String,
    val label: String,
    val urlTemplate: String,
    val builtIn: Boolean = false,
    val enabled: Boolean = true
) {
    val scheme get() = urlTemplate.substringBefore("://")

    fun displayName(serviceName: String) = "$serviceName ($label)"
}

internal object ServiceTypes {
    private const val PREFERENCES_NAME = "service_types"
    private const val CUSTOM_TYPES_KEY = "custom_types"
    private const val DEFAULT_ENABLED_TYPE = "_http._tcp."
    private val schemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*$")

    val builtIns = listOf(
        ServiceTypeConfig("_http._tcp.", "HTTP", "http://{host}:{port}/", builtIn = true),
        ServiceTypeConfig("_https._tcp.", "HTTPS", "https://{host}:{port}/", builtIn = true),
        ServiceTypeConfig("_ssh._tcp.", "SSH", "ssh://{host}:{port}", builtIn = true),
        ServiceTypeConfig("_sftp-ssh._tcp.", "SFTP", "sftp://{host}:{port}", builtIn = true),
        ServiceTypeConfig("_ftp._tcp.", "FTP", "ftp://{host}:{port}", builtIn = true),
        ServiceTypeConfig("_smb._tcp.", "SMB", "smb://{host}:{port}", builtIn = true),
        ServiceTypeConfig("_rfb._tcp.", "VNC", "vnc://{host}:{port}", builtIn = true)
    )

    fun all(context: Context): List<ServiceTypeConfig> {
        val preferences = preferences(context)
        val customTypes = preferences.getStringSet(CUSTOM_TYPES_KEY, emptySet()).orEmpty()
        val builtInNames = builtIns.mapTo(mutableSetOf()) { it.type.lowercase() }
        val builtInConfigs = builtIns.map { config ->
            config.copy(
                urlTemplate = preferences.getString(templateKey(config.type), null) ?: config.urlTemplate,
                enabled = preferences.getBoolean(enabledKey(config.type), config.type == DEFAULT_ENABLED_TYPE)
            )
        }
        return builtInConfigs + customTypes.mapNotNull { type ->
            if (type.lowercase() in builtInNames) return@mapNotNull null
            val template = preferences.getString(templateKey(type), null) ?: return@mapNotNull null
            customConfig(type, template, preferences.getBoolean(enabledKey(type), true))
        }.sortedBy { it.type }
    }

    fun enabled(context: Context) = all(context).filter { it.enabled }

    fun addCustom(context: Context, protocol: String, transport: String, schemeValue: String): ServiceTypeConfig? {
        val type = buildType(protocol, transport) ?: return null
        val scheme = schemeValue.trim().removeSuffix("://")
        if (!schemePattern.matches(scheme)) return null
        if (builtIns.any { it.type.equals(type, ignoreCase = true) }) return null

        val preferences = preferences(context)
        val types = preferences.getStringSet(CUSTOM_TYPES_KEY, emptySet()).orEmpty().toMutableSet()
        if (types.any { it.equals(type, ignoreCase = true) }) return null
        types += type
        val template = "$scheme://{host}:{port}"
        preferences.edit {
            putStringSet(CUSTOM_TYPES_KEY, types)
            putString(templateKey(type), template)
        }
        return customConfig(type, template, true)
    }

    fun setEnabled(context: Context, type: String, enabled: Boolean) {
        preferences(context).edit { putBoolean(enabledKey(type), enabled) }
    }

    fun setScheme(context: Context, type: String, schemeValue: String): Boolean {
        val scheme = schemeValue.trim().removeSuffix("://")
        if (!schemePattern.matches(scheme)) return false
        val config = all(context).firstOrNull { it.type == type } ?: return false
        val template = "$scheme://" + config.urlTemplate.substringAfter("://")
        preferences(context).edit { putString(templateKey(type), template) }
        return true
    }

    fun reset(context: Context, type: String) {
        preferences(context).edit {
            remove(templateKey(type))
            remove(enabledKey(type))
        }
    }

    fun deleteCustom(context: Context, type: String) {
        val preferences = preferences(context)
        val types = preferences.getStringSet(CUSTOM_TYPES_KEY, emptySet()).orEmpty() - type
        preferences.edit {
            putStringSet(CUSTOM_TYPES_KEY, types)
            remove(templateKey(type))
            remove(enabledKey(type))
        }
    }

    fun find(context: Context, type: String): ServiceTypeConfig? =
        all(context).firstOrNull { it.type.equals(type, ignoreCase = true) }

    fun validTemplate(template: String): Boolean =
        template.contains("{host}") &&
            template.contains("{port}") &&
            template.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*://.+$"))

    fun buildType(protocol: String, transport: String): String? {
        val name = protocol.trim().lowercase().removePrefix("_")
        val type = "_$name._${transport.lowercase()}."
        return type.takeIf { it.matches(Regex("^_[a-z0-9-]+\\._(tcp|udp)\\.$")) }
    }

    private fun customConfig(type: String, template: String, enabled: Boolean) = ServiceTypeConfig(
        type,
        type.substringBefore("._").removePrefix("_").uppercase() +
            if (type.endsWith("._udp.")) " UDP" else "",
        template,
        enabled = enabled
    )

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun templateKey(type: String) = "launch_$type"

    private fun enabledKey(type: String) = "enabled_$type"
}
