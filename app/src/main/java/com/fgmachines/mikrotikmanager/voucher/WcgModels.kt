package com.fgmachines.mikrotikmanager.voucher

enum class VoucherAccessMode {
    HOTSPOT,
    USER_MANAGER,
    PPPOE
}

data class WcgCardSettings(
    val accessMode: VoucherAccessMode = VoucherAccessMode.HOTSPOT,
    val networkName: String = "",
    val supportPhone: String = "",
    val priceText: String = "",
    val profile: String,
    val server: String = "all",
    val quantity: Int = 10,
    val usernameLength: Int = 6,
    val passwordLength: Int = 6,
    val passwordMode: VoucherPasswordMode = VoucherPasswordMode.SAME_AS_USERNAME,
    val characterSet: VoucherCharacterSet = VoucherCharacterSet.NUMERIC,
    val prefix: String = "",
    val suffix: String = "",
    val limitUptime: String? = null,
    val dataLimitBytes: Long? = null,
    val comment: String = "",
    val hotspotLoginUrl: String = ""
) {
    fun toBatchRequest(): VoucherBatchRequest =
        VoucherBatchRequest(
            quantity = quantity,
            usernameLength = usernameLength,
            prefix = prefix,
            suffix = suffix,
            passwordMode = passwordMode,
            passwordLength = passwordLength,
            characterSet = characterSet,
            profile = profile,
            server = server,
            comment = comment,
            limitUptime = limitUptime,
            limitBytesTotal = dataLimitBytes
        )
}

data class VoucherExportBundle(
    val rsc: String,
    val csv: String,
    val html: String
)
