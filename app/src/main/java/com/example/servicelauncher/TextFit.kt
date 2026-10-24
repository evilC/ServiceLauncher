package com.example.servicelauncher

internal object TextFit {
    // Returns Int.MAX_VALUE if any single word is wider than the line.
    fun lineCount(text: String, width: Float, measure: (String) -> Float): Int {
        var lines = 0
        for (line in text.split('\n')) {
            var current = 0f
            var count = 1
            for (word in line.split(' ').filter { it.isNotEmpty() }) {
                val wordWidth = measure(word)
                if (wordWidth > width) return Int.MAX_VALUE
                val next = if (current == 0f) wordWidth else current + measure(" $word")
                if (next > width) {
                    count++
                    current = wordWidth
                } else {
                    current = next
                }
            }
            lines += count
        }
        return lines
    }

    // Largest size from max down to min where all words fit and all lines fit the height.
    fun largestSize(
        text: String,
        min: Int,
        max: Int,
        width: Float,
        height: Float,
        lineHeight: (Int) -> Float,
        measure: (Int, String) -> Float
    ): Int {
        for (size in max downTo min) {
            val lines = lineCount(text, width) { measure(size, it) }
            if (lines != Int.MAX_VALUE && lines * lineHeight(size) <= height) return size
        }
        return min
    }
}
