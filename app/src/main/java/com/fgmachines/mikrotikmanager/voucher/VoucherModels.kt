package com.fgmachines.mikrotikmanager.voucher

enum class VoucherCharacterSet {
    NUMERIC,
    LETTERS,
    ALPHANUMERIC
}

enum class VoucherPasswordMode {
    SAME_AS_USERNAME,
    RANDOM,
    NONE
}

data class VoucherBatchRequest(
    val quantity: Int,
    val usernameLength: Int,
    val prefix: String = "",
    val suffix: String = "",
    val passwordMode: VoucherPasswordMode = VoucherPasswordMode.SAME_AS_USERNAME,
    val passwordLength: Int = 6,
    val characterSet: VoucherCharacterSet = VoucherCharacterSet.NUMERIC,
    val profile: String,
    val server: String = "all",
    val comment: String = "",
    val limitUptime: String? = null,
    val limitBytesTotal: Long? = null
) {
    init {
        require(quantity in 1..5000) { "Quantity must be between 1 and 5000" }
        require(usernameLength in 4..16) { "Username length must be between 4 and 16" }
        require(passwordLength in 4..16) { "Password length must be between 4 and 16" }
        require(profile.isNotBlank()) { "HotSpot profile is required" }
        require(server.isNotBlank()) { "HotSpot server is required" }
        require(limitBytesTotal == null || limitBytesTotal > 0) {
            "Traffic limit must be greater than zero"
        }
    }
}

data class VoucherDraft(
    val username: String,
    val password: String,
    val profile: String,
    val server: String,
    val comment: String,
    val limitUptime: String?,
    val limitBytesTotal: Long?
)

data class VoucherBatch(
    val request: VoucherBatchRequest,
    val vouchers: List<VoucherDraft>
)
