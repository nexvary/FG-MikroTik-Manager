package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherExportersTest {
    private val branding = VoucherBranding(
        networkName = "FG WiFi",
        supportPhone = "01000000000",
        priceText = "10 EGP"
    )

    private val batch = VoucherBatch(
        request = VoucherBatchRequest(
            quantity = 1,
            usernameLength = 6,
            profile = "5M",
            branding = branding
        ),
        vouchers = listOf(
            VoucherDraft(
                username = "123456",
                password = "123456",
                profile = "5M",
                server = "all",
                comment = "",
                limitUptime = "1d",
                limitBytesTotal = 1024,
                branding = branding
            )
        )
    )

    @Test
    fun csvContainsVoucherCredentials() {
        val csv = CsvVoucherExporter().export(batch)
        assertTrue(csv.contains("username,password"))
        assertTrue(csv.contains("123456,123456"))
    }

    @Test
    fun htmlIsPrintableAndContainsVoucher() {
        val html = HtmlVoucherExporter().export(batch)
        assertTrue(html.contains("@page"))
        assertTrue(html.contains("FG WiFi"))
        assertTrue(html.contains("123456"))
        assertTrue(html.contains("<svg"))
    }

    @Test
    fun qrPayloadContainsCredentialFields() {
        val payload = VoucherQrPayloadBuilder.build(batch.vouchers.single())
        assertTrue(payload.contains("Username: 123456"))
        assertTrue(payload.contains("Password: 123456"))
    }
}
