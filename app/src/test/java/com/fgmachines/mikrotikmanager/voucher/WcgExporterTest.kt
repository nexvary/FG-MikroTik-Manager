package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WcgExporterTest {

    private val voucher = VoucherDraft(
        username = "FG-1234",
        password = "9876",
        profile = "5M-1D",
        server = "all",
        comment = "batch-1",
        limitUptime = "1d",
        limitBytesTotal = 5_000_000_000
    )

    private val batch = VoucherBatch(
        request = VoucherBatchRequest(
            quantity = 1,
            usernameLength = 4,
            profile = "5M-1D"
        ),
        vouchers = listOf(voucher)
    )

    @Test
    fun exportsHotspotRsc() {
        val rsc = WcgExporter.toRsc(
            WcgCardSettings(
                accessMode = VoucherAccessMode.HOTSPOT,
                profile = "5M-1D"
            ),
            batch
        )

        assertTrue(rsc.contains("/ip hotspot user add"))
        assertTrue(rsc.contains("name=\"FG-1234\""))
        assertTrue(rsc.contains("limit-uptime=\"1d\""))
        assertTrue(rsc.contains("limit-bytes-total=5000000000"))
    }

    @Test
    fun exportsUserManagerCommands() {
        val rsc = WcgExporter.toRsc(
            WcgCardSettings(
                accessMode = VoucherAccessMode.USER_MANAGER,
                profile = "UM-1D"
            ),
            batch
        )

        assertTrue(rsc.contains("/user-manager user add"))
        assertTrue(rsc.contains("/user-manager user-profile add"))
    }

    @Test
    fun exportsPppoeSecret() {
        val rsc = WcgExporter.toRsc(
            WcgCardSettings(
                accessMode = VoucherAccessMode.PPPOE,
                profile = "PPP-5M"
            ),
            batch
        )

        assertTrue(rsc.contains("/ppp secret add"))
        assertTrue(rsc.contains("service=pppoe"))
    }

    @Test
    fun htmlEscapesUserControlledText() {
        val html = WcgExporter.toHtml(
            WcgCardSettings(
                networkName = "<FG & WiFi>",
                supportPhone = "0100",
                priceText = "10 جنيه",
                profile = "5M-1D"
            ),
            batch
        )

        assertTrue(html.contains("&lt;FG &amp; WiFi&gt;"))
        assertFalse(html.contains("<FG & WiFi>"))
    }

    @Test
    fun buildsHotspotQrLoginPayload() {
        val payload = WcgExporter.hotspotQrPayload(
            "http://login.example/login",
            voucher
        )

        assertTrue(payload.startsWith("http://login.example/login?"))
        assertTrue(payload.contains("username=FG-1234"))
        assertTrue(payload.contains("password=9876"))
    }
}
