package com.seance.app.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdGeneratorTest {

    private val head = "HEAD".toByteArray()
    private val tail = "TAIL".toByteArray()

    @Test
    fun sameInputsProduceSameId() {
        val a = StableIdGenerator.forFile(1L, 1000L, head, tail)
        val b = StableIdGenerator.forFile(1L, 1000L, head, tail)
        assertEquals(a, b)
    }

    @Test
    fun differentSourceIdChangesId() {
        val a = StableIdGenerator.forFile(1L, 1000L, head, tail)
        val b = StableIdGenerator.forFile(2L, 1000L, head, tail)
        assertNotEquals(a, b)
    }

    @Test
    fun differentSizeChangesId() {
        val a = StableIdGenerator.forFile(1L, 1000L, head, tail)
        val b = StableIdGenerator.forFile(1L, 2000L, head, tail)
        assertNotEquals(a, b)
    }

    @Test
    fun differentContentChangesId() {
        val a = StableIdGenerator.forFile(1L, 1000L, head, tail)
        val b = StableIdGenerator.forFile(1L, 1000L, "OTHER".toByteArray(), tail)
        assertNotEquals(a, b)
    }

    @Test
    fun idSurvivesRename() {
        // The whole point of the generator: a rename doesn't affect the id because the filename
        // never enters the hash - only sourceId/size/content sample do.
        val beforeRename = StableIdGenerator.forFile(1L, 1000L, head, tail)
        val afterRename = StableIdGenerator.forFile(1L, 1000L, head, tail)
        assertEquals(beforeRename, afterRename)
    }

    @Test
    fun idIsLowercaseHex() {
        val id = StableIdGenerator.forFile(1L, 1000L, head, tail)
        assertEquals(64, id.length)
        assertEquals(id, id.lowercase())
        assertEquals(true, id.all { it in "0123456789abcdef" })
    }
}
