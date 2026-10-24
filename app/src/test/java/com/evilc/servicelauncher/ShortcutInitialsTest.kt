package com.evilc.servicelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutInitialsTest {
    @Test
    fun usesFirstLettersOfFirstTwoWords() {
        assertEquals("WU", ServiceShortcuts.initials("WearWare UI"))
        assertEquals("AB", ServiceShortcuts.initials("alpha beta gamma"))
    }

    @Test
    fun handlesSingleWordBlankAndNewlines() {
        assertEquals("F", ServiceShortcuts.initials("Foo"))
        assertEquals("?", ServiceShortcuts.initials("   "))
        assertEquals("TI", ServiceShortcuts.initials("This is\nmy label"))
    }
}
