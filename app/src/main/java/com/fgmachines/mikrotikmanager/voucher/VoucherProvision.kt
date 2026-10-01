package com.fgmachines.mikrotikmanager.voucher

data class RouterVoucherProfile(
    val name: String,
    val rateLimit: String = "",
    val sharedUsers: Int? = null,
    val sessionTimeout: String = ""
)

enum class VoucherProvisionStatus {
    CREATED,
    DUPLICATE,
    FAILED,
    SKIPPED
}

data class VoucherProvisionItem(
    val username: String,
    val status: VoucherProvisionStatus,
    val message: String = "",
    val routerId: String? = null
)

data class VoucherProvisionSummary(
    val mode: VoucherMode,
    val total: Int,
    val created: Int,
    val duplicates: Int,
    val failed: Int,
    val skipped: Int,
    val items: List<VoucherProvisionItem>
) {
    val successful: Boolean
        get() = failed == 0
}
