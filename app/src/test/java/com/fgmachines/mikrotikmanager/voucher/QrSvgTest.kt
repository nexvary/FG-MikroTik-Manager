package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrSvgTest {

    @Test
    fun encodesQrFullyOffline() {
        val svg = QrSvg.encode("http://10.10.10.1/login?username=123456&password=123456")

        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("<path"))
        assertTrue(svg.contains("fill=\"#000\""))
        assertFalse(svg.contains("qrserver.com"))
    }

    @Test
    fun htmlEmbedsLocalQrAndNoExternalQrProvider() {
        val voucher = VoucherDraft(
            username = "123456",
            password = "123456",
            profile = "default",
            server = "all",
            comment = "",
            limitUptime = null,
            limitBytesTotal = null
        )
        val request = VoucherBatchRequest(
            quantity = 1,
            usernameLength = 6,
            profile = "default"
        )
        val html = WcgExporter.toHtml(
            WcgCardSettings(
                networkName = "FG WiFi",
                profile = "default",
                hotspotLoginUrl = "http://10.10.10.1/login"
            ),
            VoucherBatch(request, listOf(voucher))
        )

        assertTrue(html.contains("<svg"))
        assertFalse(html.contains("api.qrserver.com"))
    }
}
