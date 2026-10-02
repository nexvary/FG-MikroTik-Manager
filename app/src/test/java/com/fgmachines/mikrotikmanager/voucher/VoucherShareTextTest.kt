package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.*
import org.junit.Test

class VoucherShareTextTest {
    private val voucher=VoucherDraft("123456","987654","plan","all","", "6h",524288000,
        branding=VoucherBranding(networkName="FG WiFi",portalLoginUrl="http://192.168.10.1/login"),durationValue=6,durationUnit=VoucherTimeUnit.HOURS)
    @Test fun sharesOnlySelectedCredentialsAndActualAllowance() {
        val text=VoucherShareText.build(voucher,true,true)
        assertTrue(text.contains("123456"));assertTrue(text.contains("987654"));assertTrue(text.contains("500.00 MB"));assertTrue(text.contains("6h"))
        assertTrue(text.contains("http://192.168.10.1/login"));assertFalse(text.contains("#u="));assertFalse(text.contains("غير مؤكد"))
    }
    @Test fun routerAllowanceOverridesOldPrintedDuration() {
        assertEquals("12h",VoucherShareText.allowance(voucher.copy(limitUptime="12h"),false))
        assertEquals("No time limit",VoucherShareText.allowance(voucher.copy(limitUptime="0s"),false))
        assertEquals("6 h",VoucherShareText.allowance(voucher.copy(limitUptime=null),false))
    }
    @Test fun unconfirmedActivationIsVisibleAndSeparatePasswordIsPreserved() {
        val text=VoucherShareText.build(voucher,false,false)
        assertTrue(text.contains("not confirmed"));assertTrue(text.contains("Password: 987654"));assertFalse(text.contains("Expires:"))
    }
}
