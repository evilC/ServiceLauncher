package com.example.servicelauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLabelTest {
    @Test
    fun usesLabelWhenSet() {
        assertEquals("This is\nmy label", ServiceWidgetSettings.displayText("This is\nmy label", "WearWare UI"))
    }

    @Test
    fun fallsBackToServiceNameWhenLabelBlank() {
        assertEquals("WearWare UI", ServiceWidgetSettings.displayText("  ", "WearWare UI"))
        assertEquals("WearWare UI", ServiceWidgetSettings.displayText(null, "WearWare UI"))
    }

    @Test
    fun nullWhenNothingConfigured() {
        assertNull(ServiceWidgetSettings.displayText(null, null))
    }

    @Test
    fun unknownTextSizeParsesAsAuto() {
        assertEquals(WidgetTextSize.AUTO, WidgetTextSize.parse("bogus"))
        assertEquals(WidgetTextSize.LARGE, WidgetTextSize.parse("LARGE"))
    }
}
