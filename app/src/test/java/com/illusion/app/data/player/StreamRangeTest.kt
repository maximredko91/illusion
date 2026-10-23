package com.illusion.app.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Разбор заголовка Range у локального HTTP-моста - см. [resolveStreamRange]. */
class StreamRangeTest {

    @Test
    fun `no range header serves the whole file`() {
        assertEquals(Triple(0L, 999L, false), resolveStreamRange(null, 1000))
    }

    @Test
    fun `open ended range starts where asked`() {
        assertEquals(Triple(400L, 999L, true), resolveStreamRange("bytes=400-", 1000))
    }

    @Test
    fun `closed range is clamped to the file`() {
        assertEquals(Triple(400L, 999L, true), resolveStreamRange("bytes=400-5000", 1000))
        assertEquals(Triple(400L, 500L, true), resolveStreamRange("bytes=400-500", 1000))
    }

    @Test
    fun `suffix range serves the last bytes, not the first`() {
        assertEquals(Triple(900L, 999L, true), resolveStreamRange("bytes=-100", 1000))
        // Запрошено больше, чем есть - отдаём файл целиком, но как частичный ответ.
        assertEquals(Triple(0L, 999L, true), resolveStreamRange("bytes=-5000", 1000))
    }

    @Test
    fun `unsatisfiable ranges are rejected`() {
        assertNull(resolveStreamRange("bytes=1000-", 1000))
        assertNull(resolveStreamRange("bytes=-0", 1000))
        assertNull(resolveStreamRange("bytes=abc", 1000))
        assertNull(resolveStreamRange("bytes=10-abc", 1000))
    }

    @Test
    fun `multiple ranges fall back to the first one`() {
        assertEquals(Triple(0L, 99L, true), resolveStreamRange("bytes=0-99,200-299", 1000))
    }

    @Test
    fun `unknown length ignores the range`() {
        assertEquals(Triple(0L, 0L, false), resolveStreamRange("bytes=400-", -1))
    }
}
