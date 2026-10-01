package com.fgmachines.mikrotikmanager.voucher

enum class VoucherValidityUnit {
    HOURS,
    DAYS,
    WEEKS,
    MONTHS
}

data class VoucherValidityPreset(
    val label: String,
    val amount: Int,
    val unit: VoucherValidityUnit
)

data class VoucherDataPreset(
    val label: String,
    val bytes: Long
)

data class VoucherPricePreset(
    val label: String,
    val amount: Int?
)

object Wcg76Presets {

    val standardUsernameLengths: List<Int> = (4..10).toList()

    val rechargeCardLengths: List<Int> = listOf(11, 12)

    val timeLimits: List<Pair<String, String?>> = listOf(
        "No Time Limit" to null,
        "1 Hour" to "1h",
        "2 Hours" to "2h",
        "3 Hours" to "3h",
        "4 Hours" to "4h"
    )

    val dataLimits: List<VoucherDataPreset?> = listOf(
        null,
        VoucherDataPreset("500 MB", 500L * 1024L * 1024L),
        VoucherDataPreset("1 GB", 1L * 1024L * 1024L * 1024L),
        VoucherDataPreset("2 GB", 2L * 1024L * 1024L * 1024L),
        VoucherDataPreset("5 GB", 5L * 1024L * 1024L * 1024L),
        VoucherDataPreset("10 GB", 10L * 1024L * 1024L * 1024L)
    )

    val validityPeriods: List<VoucherValidityPreset> = listOf(
        VoucherValidityPreset("5 Hours", 5, VoucherValidityUnit.HOURS),
        VoucherValidityPreset("1 Day", 1, VoucherValidityUnit.DAYS),
        VoucherValidityPreset("7 Days", 7, VoucherValidityUnit.DAYS),
        VoucherValidityPreset("15 Days", 15, VoucherValidityUnit.DAYS),
        VoucherValidityPreset("30 Days", 30, VoucherValidityUnit.DAYS)
    )

    val userManagerExtraValidity: List<VoucherValidityPreset> = listOf(
        VoucherValidityPreset("1 Week", 1, VoucherValidityUnit.WEEKS),
        VoucherValidityPreset("1 Month", 1, VoucherValidityUnit.MONTHS)
    )

    val pricePresets: List<VoucherPricePreset> = listOf(
        VoucherPricePreset("No Price", null),
        VoucherPricePreset("5 EGP", 5),
        VoucherPricePreset("10 EGP", 10),
        VoucherPricePreset("25 EGP", 25),
        VoucherPricePreset("50 EGP", 50),
        VoucherPricePreset("100 EGP", 100)
    )

    val currencies: List<String> = listOf(
        "EGP", "SAR", "AED", "KWD", "BHD", "QAR", "OMR", "JOD",
        "IQD", "LYD", "TND", "DZD", "MAD", "SDG", "YER", "LBP",
        "SYP", "MRU", "DJF", "SOS", "ILS", "USD", "EUR", "GBP"
    )

    val limitedProfiles: List<String> = listOf(
        "Limited 1MB",
        "Limited 2MB",
        "Limited 3MB",
        "Limited 5MB",
        "Limited 10MB"
    )
}

data class VoucherCardStyle(
    val fontSizePx: Int = 14,
    val fontColor: String = "#000000",
    val priceColor: String = "#006400",
    val phoneColor: String = "#00008B",
    val timeColor: String = "#8B0000",
    val quotaColor: String = "#4B0082",
    val cardAndWifiColor: String = "#000000",
    val marginPx: Int = 1,
    val backgroundImageUri: String? = null
) {
    init {
        require(fontSizePx in 8..48) { "Font size is out of range" }
        require(marginPx in 0..24) { "Margin is out of range" }

        listOf(
            fontColor,
            priceColor,
            phoneColor,
            timeColor,
            quotaColor,
            cardAndWifiColor
        ).forEach {
            require(HEX_COLOR.matches(it)) { "Invalid card color: $it" }
        }
    }

    private companion object {
        val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
