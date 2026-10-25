package com.evilc.servicelauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceMetadataTest {
    @Test
    fun readsLabelAndText() {
        val metadata = ServiceMetadata.from(
            mapOf("sl-slabel" to "Wear Ware".toByteArray(), "sl-stext" to "WW".toByteArray())
        )
        assertEquals("Wear Ware", metadata.iconLabel)
        assertEquals("WW", metadata.iconText)
    }

    @Test
    fun missingOrBlankValuesAreNull() {
        val metadata = ServiceMetadata.from(mapOf("sl-slabel" to "  ".toByteArray(), "sl-stext" to null))
        assertNull(metadata.iconLabel)
        assertNull(metadata.iconText)
    }

    @Test
    fun literalBackslashNBecomesNewline() {
        val metadata = ServiceMetadata.from(mapOf("sl-stext" to "Wear\\nWare".toByteArray()))
        assertEquals("Wear\nWare", metadata.iconText)
    }

    @Test
    fun readsUsername() {
        val metadata = ServiceMetadata.from(mapOf("sl-username" to "pi".toByteArray()))
        assertEquals("pi", metadata.username)
    }
}
