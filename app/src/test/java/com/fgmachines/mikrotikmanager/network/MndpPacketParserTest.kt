package com.fgmachines.mikrotikmanager.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MndpPacketParserTest {

    @Test
    fun parsesIdentityBoardVersionMacAndIpv4() {
        val packet = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 1))
            tlv(0x0001, byteArrayOf(0x74, 0x4D, 0x28, 0x83.toByte(), 0xFE.toByte(), 0x32))
            tlv(0x0005, "MikroTik".toByteArray())
            tlv(0x0007, "7.16.2".toByteArray())
            tlv(0x000C, "hAP ac2".toByteArray())
            tlv(0x0010, "ether1".toByteArray())
            tlv(0x0011, byteArrayOf(192.toByte(), 168.toByte(), 1, 110))
        }.toByteArray()

        val router = MndpPacketParser.parse(packet)

        requireNotNull(router)
        assertEquals("MikroTik", router.identity)
        assertEquals("192.168.1.110", router.ipAddress)
        assertEquals("74:4D:28:83:FE:32", router.macAddress)
        assertEquals("hAP ac2", router.boardName)
        assertEquals("7.16.2", router.version)
        assertEquals("ether1", router.interfaceName)
    }

    private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
        write((type ushr 8) and 0xFF)
        write(type and 0xFF)
        write((value.size ushr 8) and 0xFF)
        write(value.size and 0xFF)
        write(value)
    }
}
