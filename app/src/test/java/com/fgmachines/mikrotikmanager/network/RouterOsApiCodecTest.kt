package com.fgmachines.mikrotikmanager.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RouterOsApiCodecTest {

    @Test
    fun roundTripsOfficialLengthBoundaries() {
        val lengths = listOf(
            0,
            1,
            0x7F,
            0x80,
            0x3FFF,
            0x4000,
            0x1FFFFF,
            0x200000,
            0x0FFFFFFF
        )

        lengths.forEach { length ->
            val output = ByteArrayOutputStream()
            RouterOsApiCodec.writeLength(output, length)
            val decoded = RouterOsApiCodec.readLength(
                ByteArrayInputStream(output.toByteArray())
            )
            assertEquals(length, decoded)
        }
    }

    @Test
    fun readsRouterOsSentence() {
        val output = ByteArrayOutputStream()
        RouterOsApiCodec.writeWord(output, "!re")
        RouterOsApiCodec.writeWord(output, "=name=ether1")
        RouterOsApiCodec.writeWord(output, "=running=true")
        RouterOsApiCodec.writeLength(output, 0)

        val sentence = RouterOsApiCodec.readSentence(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals("!re", sentence.first())
        val attributes = RouterOsApiCodec.attributes(sentence.drop(1))
        assertEquals("ether1", attributes["name"])
        assertEquals("true", attributes["running"])
    }

    @Test
    fun parsesValuesContainingEqualsSigns() {
        val attributes = RouterOsApiCodec.attributes(
            listOf("=comment=a=b=c", ".tag=44")
        )

        assertEquals("a=b=c", attributes["comment"])
        assertEquals("44", attributes[".tag"])
        assertTrue(attributes.size == 2)
    }
}
