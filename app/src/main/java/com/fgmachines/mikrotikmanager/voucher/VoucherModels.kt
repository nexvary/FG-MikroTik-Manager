package com.fgmachines.mikrotikmanager.voucher

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
enum class VoucherTimeUnit {
    MINUTES,
    HOURS,
    DAYS
}

@Serializable
data class VoucherBranding(
    val networkName: String = "",
    val supportPhone: String = "",
    val priceText: String = "",
    val priceEgp: Double? = null,
    val portalLoginUrl: String = ""
) {
    fun formattedPrice(): String =
        when {
            priceEgp != null -> DecimalFormat("0.##").format(priceEgp) + " EGP"
            priceText.isNotBlank() -> priceText
            else -> ""
        }
}

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
    val durationValue: Int = 60,
    val durationUnit: VoucherTimeUnit = VoucherTimeUnit.MINUTES,
    val limitUptime: String? = null,
    val absoluteExpiryEpochMs: Long? = null,
    val limitBytesTotal: Long? = null,
    val branding: VoucherBranding = VoucherBranding()
) {
    init {
        require(quantity in 1..5000) { "Quantity must be between 1 and 5000" }
        require(usernameLength in 4..16) { "Username length must be between 4 and 16" }
        require(passwordLength in 4..16) { "Password length must be between 4 and 16" }
        require(durationValue in 1..100000) { "Duration must be greater than zero" }
        require(profile.isNotBlank() || mode == VoucherMode.OFFLINE) {
            "A profile is required for online provisioning"
        }
        require(server.isNotBlank()) { "Server is required" }
        require(limitBytesTotal == null || limitBytesTotal > 0) {
            "Traffic limit must be greater than zero"
        }
    }

    fun routerOsDuration(): String =
        limitUptime?.takeIf { it.isNotBlank() }
            ?: when (durationUnit) {
                VoucherTimeUnit.MINUTES -> durationValue.toString() + "m"
                VoucherTimeUnit.HOURS -> durationValue.toString() + "h"
                VoucherTimeUnit.DAYS -> durationValue.toString() + "d"
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
    val branding: VoucherBranding = VoucherBranding(),
    val durationValue: Int = 60,
    val durationUnit: VoucherTimeUnit = VoucherTimeUnit.MINUTES,
    val absoluteExpiryEpochMs: Long? = null
) {
    fun displayDuration(arabic: Boolean): String =
        when (durationUnit) {
            VoucherTimeUnit.MINUTES ->
                if (arabic) "$durationValue دقيقة" else "$durationValue min"
            VoucherTimeUnit.HOURS ->
                if (arabic) "$durationValue ساعة" else "$durationValue h"
            VoucherTimeUnit.DAYS ->
                if (arabic) "$durationValue يوم" else "$durationValue day"
        }

    fun displayExpiry(arabic: Boolean): String? =
        absoluteExpiryEpochMs?.let { epoch ->
            val locale = if (arabic) Locale("ar") else Locale.ENGLISH
            SimpleDateFormat("dd/MM/yyyy  HH:mm", locale).format(Date(epoch))
        }
}

@Serializable
data class VoucherBatch(
    val request: VoucherBatchRequest,
    val vouchers: List<VoucherDraft>
)
