package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterVoucherMapperTest {

    private val batch = VoucherBatch(
        request = VoucherBatchRequest(
            quantity = 1,
            usernameLength = 6,
            profile = "P1"
        ),
        vouchers = listOf(
            VoucherDraft(
                username = "123456",
                password = "654321",
                profile = "P1",
                server = "hs1",
                comment = "sale-1",
                limitUptime = "2h",
                limitBytesTotal = 1_073_741_824
            )
        )
    )

    @Test
    fun mapsHotspotVoucher() {
        val commands = RouterVoucherMapper.commands(
            WcgCardSettings(
                accessMode = VoucherAccessMode.HOTSPOT,
                profile = "P1"
            ),
            batch
        )

        assertEquals(1, commands.size)
        assertEquals("ip/hotspot/user", commands.single().path)
        assertEquals("123456", commands.single().attributes["name"])
        assertEquals("2h", commands.single().attributes["limit-uptime"])
        assertEquals("1073741824", commands.single().attributes["limit-bytes-total"])
    }

    @Test
    fun mapsUserManagerUserAndProfile() {
        val commands = RouterVoucherMapper.commands(
            WcgCardSettings(
                accessMode = VoucherAccessMode.USER_MANAGER,
                profile = "P1"
            ),
            batch
        )

        assertEquals(2, commands.size)
        assertEquals("user-manager/user", commands[0].path)
        assertEquals("user-manager/user-profile", commands[1].path)
        assertEquals("P1", commands[1].attributes["profile"])
    }

    @Test
    fun mapsPppoeSecret() {
        val command = RouterVoucherMapper.commands(
            WcgCardSettings(
                accessMode = VoucherAccessMode.PPPOE,
                profile = "P1"
            ),
            batch
        ).single()

        assertEquals("ppp/secret", command.path)
        assertEquals("pppoe", command.attributes["service"])
        assertTrue(command.attributes.containsKey("limit-bytes-out"))
    }
}
