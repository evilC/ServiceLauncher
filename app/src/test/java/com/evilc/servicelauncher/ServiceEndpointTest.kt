package com.evilc.servicelauncher

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
    fun insertsUsernameForNonHttpSchemes() {
        val address = InetAddress.getByName("192.168.43.25")

        assertEquals(
            "ssh://pi@192.168.43.25:22",
            ServiceEndpoint.url("ssh://{host}:{port}", address, 22, "pi")
        )
        assertEquals(
            "ssh://a%20b%40c@192.168.43.25:22",
            ServiceEndpoint.url("ssh://{host}:{port}", address, 22, "a b@c")
        )
    }

    @Test
    fun ignoresUsernameForHttp() {
        val address = InetAddress.getByName("192.168.43.25")

        assertEquals(
            "http://192.168.43.25:5000/",
            ServiceEndpoint.url("http://{host}:{port}/", address, 5000, "pi")
        )
        assertEquals(
            "https://192.168.43.25:443/",
            ServiceEndpoint.url("https://{host}:{port}/", address, 443, "pi")
        )
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
