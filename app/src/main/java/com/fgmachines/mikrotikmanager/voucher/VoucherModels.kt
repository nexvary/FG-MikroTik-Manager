package com.fgmachines.mikrotikmanager.voucher

enum class VoucherBackend {
    HOTSPOT,
    USER_MANAGER_V7,
    PPPOE
}

enum class VoucherUsernameMode {
    GENERATED,
    RECHARGE_CARD,
    CUSTOM
}

enum class VoucherCharacterSet {
    NUMERIC,
    LETTERS,
    ALPHANUMERIC
}

enum class VoucherPasswordMode {
    NONE,
    SAME_AS_USERNAME,
    RANDOM
}

enum class VoucherValidityUnit {
    HOURS,
    DAYS
}

data class VoucherValidity(
    val value: Int,
    val unit: VoucherValidityUnit
) {
    init {
        require(value > 0) { "Validity value must be greater than zero" }
    }
}

data class VoucherCardMetadata(
    val wifiName: String = "",
    val supportNumber: String = "",
    val serverUrl: String = "",
    val priceText: String = "",
    val currency: String = "EGP"
)

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
    val limitBytesTotal: Long? = null,
    val backend: VoucherBackend = VoucherBackend.HOTSPOT,
    val usernameMode: VoucherUsernameMode = VoucherUsernameMode.GENERATED,
    val pppService: String = "pppoe",
    val validity: VoucherValidity? = null,
    val card: VoucherCardMetadata = VoucherCardMetadata()
) {
    init {
        require(quantity in 1..5000) { "Quantity must be between 1 and 5000" }
        require(usernameLength in 4..16) { "Username length must be between 4 and 16" }
        if (passwordMode == VoucherPasswordMode.RANDOM) {
            require(passwordLength in 4..16) {
                "Password length must be between 4 and 16 for random passwords"
            }
        }
        if (backend != VoucherBackend.USER_MANAGER_V7) {
            require(profile.isNotBlank()) { "RouterOS profile is required" }
        }
        if (backend == VoucherBackend.HOTSPOT) {
            require(server.isNotBlank()) { "HotSpot server is required" }
        }
        if (backend == VoucherBackend.PPPOE) {
            require(pppService.isNotBlank()) { "PPP service is required" }
        }
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
