package com.example.servicelauncher

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceEndpointTest {
    @Test
    fun formatsIpv4AsBrowserUrl() {
        val address = InetAddress.getByName("192.168.43.25")

        assertEquals("http://192.168.43.25:5000/", ServiceEndpoint.url(address, 5000))
    }

    @Test
    fun bracketsIpv6AndEncodesItsScopeId() {
        val address = Inet6Address.getByAddress(
            null,
            byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            3
        )
        val url = ServiceEndpoint.url(address, 5000)

        assertTrue(url.startsWith("http://["))
        assertTrue(url.contains("%253"))
        assertTrue(url.endsWith("]:5000/"))
    }
}
