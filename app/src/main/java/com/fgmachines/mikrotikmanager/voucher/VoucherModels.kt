package com.fgmachines.mikrotikmanager.voucher

import kotlinx.serialization.Serializable

@Serializable
enum class VoucherMode {
    HOTSPOT,
    USER_MANAGER,
    PPPOE,
    OFFLINE
}

@Serializable
enum class VoucherCharacterSet {
    NUMERIC,
    ALPHANUMERIC
}

@Serializable
enum class VoucherPasswordMode {
    SAME_AS_USERNAME,
    RANDOM
}

@Serializable
data class VoucherBranding(
    val networkName: String = "",
    val supportPhone: String = "",
    val priceText: String = ""
)

@Serializable
data class VoucherBatchRequest(
    val quantity: Int,
    val usernameLength: Int,
    val prefix: String = "",
    val suffix: String = "",
    val passwordMode: VoucherPasswordMode = VoucherPasswordMode.SAME_AS_USERNAME,
    val passwordLength: Int = 6,
    val characterSet: VoucherCharacterSet = VoucherCharacterSet.NUMERIC,
    val mode: VoucherMode = VoucherMode.HOTSPOT,
    val profile: String,
    val server: String = "all",
    val comment: String = "",
    val limitUptime: String? = null,
    val limitBytesTotal: Long? = null,
    val branding: VoucherBranding = VoucherBranding()
) {
    init {
        require(quantity in 1..5000) { "Quantity must be between 1 and 5000" }
        require(usernameLength in 4..16) { "Username length must be between 4 and 16" }
        require(passwordLength in 4..16) { "Password length must be between 4 and 16" }
        require(profile.isNotBlank() || mode == VoucherMode.OFFLINE) {
            "A profile is required for online provisioning"
        }
        require(server.isNotBlank()) { "Server is required" }
        require(limitBytesTotal == null || limitBytesTotal > 0) {
            "Traffic limit must be greater than zero"
        }
    }
}

@Serializable
data class VoucherDraft(
    val username: String,
    val password: String,
    val profile: String,
    val server: String,
    val comment: String,
    val limitUptime: String?,
    val limitBytesTotal: Long?,
    val mode: VoucherMode = VoucherMode.HOTSPOT,
    val branding: VoucherBranding = VoucherBranding()
)

@Serializable
data class VoucherBatch(
    val request: VoucherBatchRequest,
    val vouchers: List<VoucherDraft>
)
