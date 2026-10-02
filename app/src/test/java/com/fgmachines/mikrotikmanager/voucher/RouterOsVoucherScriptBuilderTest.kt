package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterOsVoucherScriptBuilderTest {

    private val builder = RouterOsVoucherScriptBuilder()

    @Test
    fun hotspotCommandContainsLimitsAndEscapesUserInput() {
        val voucher = VoucherDraft(
            username = "FG-1234",
            password = "12\"34",
            profile = "5M",
            server = "hotspot1",
            comment = "batch $1",
            limitUptime = "2h",
            limitBytesTotal = 1_073_741_824
        )

        val command = builder.buildHotspotUser(voucher)

        assertTrue(command.startsWith("/ip hotspot user add"))
        assertTrue(command.contains("limit-uptime=\"2h\""))
        assertTrue(command.contains("limit-bytes-total=1073741824"))
        assertTrue(command.contains("comment=\"batch \\\$1\""))
        assertTrue(command.contains("password=\"12\\\"34\""))
    }

    @Test
    fun noPasswordModeDoesNotEmitPasswordProperty() {
        val voucher = VoucherDraft(
            username = "123456",
            password = "",
            profile = "default",
            server = "all",
            comment = "",
            limitUptime = null,
            limitBytesTotal = null
        )

        assertFalse(builder.buildHotspotUser(voucher).contains(" password="))
    }

    @Test
    fun userManagerNoProfileOnlyCreatesUser() {
        val voucher = VoucherDraft(
            username = "1234",
            password = "5678",
            profile = "No Profile",
            server = "all",
            comment = "",
            limitUptime = null,
            limitBytesTotal = null
        )

        val commands = builder.buildUserManagerCommands(voucher)

        assertTrue(commands.size == 1)
        assertTrue(commands.single().startsWith("/user-manager user add"))
    }

    @Test
    fun pppoeCommandUsesPppSecret() {
        val voucher = VoucherDraft(
            username = "ppp-user",
            password = "pass",
            profile = "PPPoE-5M",
            server = "all",
            comment = "generated",
            limitUptime = null,
            limitBytesTotal = null
        )

        val command = builder.buildPppoeSecret(voucher)

        assertTrue(command.startsWith("/ppp secret add"))
        assertTrue(command.contains("service=\"pppoe\""))
    }
}
