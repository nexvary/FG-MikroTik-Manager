package com.fgmachines.mikrotikmanager.business

import org.junit.Assert.*
import org.junit.Test
class BusinessMoneyTest {
    @Test fun exactMinorUnits() { assertEquals(10010L,BusinessMoney.parse("100.10")); assertEquals(1L,BusinessMoney.parse("0.01")) }
    @Test fun arabicAndPersianInput() { assertEquals(12345L,BusinessMoney.parse("١٢٣٫٤٥")); assertEquals(12345L,BusinessMoney.parse("۱۲۳.۴۵")) }
    @Test fun rejectsAmbiguousOrUnsafeAmounts() {
        listOf("", "0", "-1", "+1", "1.001", "1e3", "1,000", "NaN", "99999999999999", "1 2", "١٬٠٠٠").forEach {
            try { BusinessMoney.parse(it); fail("Accepted invalid amount: $it") } catch(_: IllegalArgumentException) { }
        }
    }
    @Test fun formatsCreditWithoutRounding() { assertEquals("-10.01 EGP",BusinessMoney.format(-1001,"EGP")) }
}
