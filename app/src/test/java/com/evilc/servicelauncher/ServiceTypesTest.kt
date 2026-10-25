package com.evilc.servicelauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTypesTest {
    @Test
    fun acceptsTemplatesWithSchemeHostAndPort() {
        assertTrue(ServiceTypes.validTemplate("ssh://pi@{host}:{port}"))
        assertTrue(ServiceTypes.validTemplate("https://{host}:{port}/ui"))
    }

    @Test
    fun buildsTypeFromProtocolAndTransport() {
        assertEquals("_mqtt._tcp.", ServiceTypes.buildType("mqtt", "TCP"))
        assertEquals("_mqtt._udp.", ServiceTypes.buildType(" _MQTT ", "udp"))
        assertNull(ServiceTypes.buildType("bad name", "tcp"))
        assertNull(ServiceTypes.buildType("", "tcp"))
    }

    @Test
    fun rejectsTemplatesMissingAddressPlaceholdersOrScheme() {
        assertFalse(ServiceTypes.validTemplate("ssh://{host}:22"))
        assertFalse(ServiceTypes.validTemplate("ssh://{port}"))
        assertFalse(ServiceTypes.validTemplate("{host}:{port}"))
    }
}
