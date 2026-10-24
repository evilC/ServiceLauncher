package com.evilc.servicelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFitTest {
    private val charWidth: (String) -> Float = { it.length * 10f }

    @Test
    fun wrapsWordsAtLineWidth() {
        assertEquals(2, TextFit.lineCount("aaaa bbbb", 50f, charWidth))
        assertEquals(1, TextFit.lineCount("aa bb", 50f, charWidth))
    }

    @Test
    fun explicitNewlinesStartNewLines() {
        assertEquals(2, TextFit.lineCount("a\nb", 100f, charWidth))
    }

    @Test
    fun wordWiderThanLineFails() {
        assertEquals(Int.MAX_VALUE, TextFit.lineCount("abcdefgh", 50f, charWidth))
    }

    @Test
    fun largestSizeRespectsWidthAndHeight() {
        val measure: (Int, String) -> Float = { size, word -> word.length * size.toFloat() }
        val lineHeight: (Int) -> Float = { it.toFloat() }
        assertEquals(10, TextFit.largestSize("abcde", 5, 30, 50f, 100f, lineHeight, measure))
        assertEquals(20, TextFit.largestSize("ab cd", 5, 20, 100f, 100f, lineHeight, measure))
        assertEquals(5, TextFit.largestSize("abcdefghij", 5, 30, 10f, 100f, lineHeight, measure))
    }
}
