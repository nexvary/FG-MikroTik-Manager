package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Wcg76PresetsTest {

    @Test
    fun reproducesReferenceUsernameAndRechargeLengths() {
        assertEquals((4..10).toList(), Wcg76Presets.standardUsernameLengths)
        assertEquals(listOf(11, 12), Wcg76Presets.rechargeCardLengths)
    }

    @Test
    fun containsReferenceDataLimitsAndCurrencies() {
        val labels = Wcg76Presets.dataLimits.map { it?.label ?: "No Data Limit" }
        assertTrue("500 MB" in labels)
        assertTrue("10 GB" in labels)

        assertTrue("EGP" in Wcg76Presets.currencies)
        assertTrue("USD" in Wcg76Presets.currencies)
        assertTrue("GBP" in Wcg76Presets.currencies)
    }

    @Test
    fun defaultStyleMatchesExtractedV76Settings() {
        val style = VoucherCardStyle()

        assertEquals(14, style.fontSizePx)
        assertEquals("#006400", style.priceColor)
        assertEquals("#00008B", style.phoneColor)
        assertEquals("#8B0000", style.timeColor)
        assertEquals("#4B0082", style.quotaColor)
        assertEquals(1, style.marginPx)
    }
}
