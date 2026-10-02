package com.fgmachines.mikrotikmanager.business

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** All supported currencies here have two fractional digits. Never store Double money. */
object BusinessMoney {
    val currencies = listOf("EGP", "USD", "EUR", "SAR", "AED", "TRY")
    const val MAX_MINOR = 999_999_999_999L
    fun parse(text: String): Long {
        val normalized = text.trim().map {
            when (it) {
                in '٠'..'٩' -> '0' + (it - '٠')
                in '۰'..'۹' -> '0' + (it - '۰')
                '٫' -> '.'
                else -> it
            }
        }.joinToString("")
        require(Regex("[0-9]+(\\.[0-9]{1,2})?").matches(normalized)) { "INVALID_AMOUNT" }
        val amount = try { BigDecimal(normalized).movePointRight(2).longValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("INVALID_AMOUNT") }
        require(amount in 1..MAX_MINOR) { "INVALID_AMOUNT" }
        return amount
    }
    fun format(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString() + " " + currency
}

data class BusinessScope(val organizationId: String, val branchId: String)
data class Subscriber(val id: String, val name: String, val phone: String, val service: String, val account: String, val currency: String)
enum class LedgerKind { CHARGE, PAYMENT, REVERSAL }
data class LedgerEntry(val sequence: Long, val id: String, val subscriberId: String, val kind: LedgerKind,
    val amountMinor: Long, val currency: String, val note: String, val createdAt: Long, val reversalOf: String?, val reversed: Boolean)
data class BusinessPage<T>(val items: List<T>, val hasMore: Boolean)

internal fun businessText(value: String, max: Int, required: Boolean = false): String = value.trim().also {
    require(it.length <= max && (!required || it.isNotEmpty())) { "INVALID_TEXT" }
    require(it.none { c -> c.isISOControl() && c != '\n' }) { "INVALID_TEXT" }
}
