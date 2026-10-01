package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterOsScriptExporterTest {

    @Test
    fun hotspotExportContainsQuotaAndProfile() {
        val batch = VoucherBatch(
            request = VoucherBatchRequest(
                quantity = 1,
                usernameLength = 6,
                profile = "5M-1D",
                limitUptime = "1d",
                limitBytesTotal = 1_073_741_824
            ),
            vouchers = listOf(
                VoucherDraft(
                    username = "FG1234",
                    password = "FG1234",
                    profile = "5M-1D",
                    server = "all",
                    comment = "batch-1",
                    limitUptime = "1d",
                    limitBytesTotal = 1_073_741_824,
                    mode = VoucherMode.HOTSPOT,
                    branding = VoucherBranding()
                )
            )
        )

        val script = RouterOsScriptExporter().export(batch)

        assertTrue(script.contains("/ip hotspot user"))
        assertTrue(script.contains("profile=\"5M-1D\""))
        assertTrue(script.contains("limit-uptime=\"1d\""))
        assertTrue(script.contains("limit-bytes-total=1073741824"))
    }

    @Test
    fun offlineExportDoesNotEmitRouterCommands() {
        val request = VoucherBatchRequest(
            quantity = 1,
            usernameLength = 6,
            mode = VoucherMode.OFFLINE,
            profile = ""
        )
        val batch = VoucherGenerator(RandomSource { 1 }).generate(request)
        val script = RouterOsScriptExporter().export(batch)

        assertTrue(script.contains("Offline batch"))
        assertFalse(script.contains("/ip hotspot user"))
        assertFalse(script.contains("/ppp secret"))
    }
}
