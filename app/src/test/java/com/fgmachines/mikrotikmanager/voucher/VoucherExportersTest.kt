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
    @Test
    fun loginQrNeverIncludesSeparatePasswordOrQuery() {
        val voucher = batch.vouchers.single().copy(password = "private-secret", branding = branding.copy(portalLoginUrl = "http://wifi.local/login"))
        val payload = VoucherQrPayloadBuilder.build(voucher)
        assertTrue(payload.startsWith("http://wifi.local/login#"))
        assertTrue(payload.contains("same=0"))
        assertTrue(!payload.contains("private-secret") && !payload.contains("password=") && !payload.contains("?"))
    }
    @Test
    fun pppoeAndOfflineNeverBecomeLoginLinks() {
        val voucher = batch.vouchers.single().copy(branding = branding.copy(portalLoginUrl="http://wifi.local/login"))
        assertTrue(VoucherQrPayloadBuilder.build(voucher.copy(mode=VoucherMode.PPPOE)).contains("PPPoE setup"))
        assertTrue(VoucherQrPayloadBuilder.build(voucher.copy(mode=VoucherMode.OFFLINE)).contains("NOT activated"))
    }
}
