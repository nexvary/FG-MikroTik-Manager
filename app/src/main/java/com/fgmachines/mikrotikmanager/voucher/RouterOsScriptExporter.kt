package com.fgmachines.mikrotikmanager.voucher

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RouterOsScriptExporter {

    fun export(batch: VoucherBatch): String {
        val header = buildString {
            appendLine("# FG MTM - FG MikroTik Manager")
            appendLine("# Generated voucher batch")
            appendLine("# Mode: " + batch.request.mode.name)
            appendLine("# Duration: " + batch.request.routerOsDuration())
            batch.request.branding.priceEgp?.let {
                appendLine("# Price: " + it + " EGP")
            }
            appendLine()
        }

        return header + when (batch.request.mode) {
            VoucherMode.HOTSPOT -> exportHotspot(batch)
            VoucherMode.PPPOE -> exportPppoe(batch)
            VoucherMode.USER_MANAGER -> exportUserManager(batch)
            VoucherMode.OFFLINE -> "# Offline batch: no RouterOS commands generated.\n"
        }
    }

    private fun exportHotspot(batch: VoucherBatch): String = buildString {
        appendLine("/ip hotspot user")
        batch.vouchers.forEach { voucher ->
            append("add name=" + quote(voucher.username))
            append(" password=" + quote(voucher.password))
            append(" profile=" + quote(voucher.profile))
            append(" server=" + quote(voucher.server))
            if (voucher.comment.isNotBlank()) append(" comment=" + quote(voucher.comment))
            voucher.limitUptime?.takeIf { it.isNotBlank() }?.let {
                append(" limit-uptime=" + quote(it))
            }
            voucher.limitBytesTotal?.let {
                append(" limit-bytes-total=" + it)
            }
            appendLine()
        }
        appendExpirySchedulers(batch, VoucherMode.HOTSPOT)
    }

    private fun exportPppoe(batch: VoucherBatch): String = buildString {
        appendLine("/ppp secret")
        batch.vouchers.forEach { voucher ->
            append("add name=" + quote(voucher.username))
            append(" password=" + quote(voucher.password))
            append(" profile=" + quote(voucher.profile))
            append(" service=pppoe")
            if (voucher.comment.isNotBlank()) append(" comment=" + quote(voucher.comment))
            appendLine()
        }
        appendExpirySchedulers(batch, VoucherMode.PPPOE)
    }

    private fun exportUserManager(batch: VoucherBatch): String = buildString {
        appendLine("/user-manager user")
        batch.vouchers.forEach { voucher ->
            append("add name=" + quote(voucher.username))
            append(" password=" + quote(voucher.password))
            if (voucher.comment.isNotBlank()) append(" comment=" + quote(voucher.comment))
            appendLine()
        }
        appendLine()
        batch.vouchers.forEach { voucher ->
            if (voucher.profile.isNotBlank() &&
                !voucher.profile.equals("No Profile", ignoreCase = true)
            ) {
                appendLine(
                    "/user-manager user-profile add user=" +
                        quote(voucher.username) +
                        " profile=" +
                        quote(voucher.profile)
                )
            }
        }
        appendExpirySchedulers(batch, VoucherMode.USER_MANAGER)
    }

    private fun StringBuilder.appendExpirySchedulers(
        batch: VoucherBatch,
        mode: VoucherMode
    ) {
        val expiring = batch.vouchers.filter { it.absoluteExpiryEpochMs != null }
        if (expiring.isEmpty()) return

        appendLine()
        appendLine("# Automatic voucher expiration")
        expiring.forEach { voucher ->
            val expiry = voucher.absoluteExpiryEpochMs ?: return@forEach
            val dateTime = Instant.ofEpochMilli(expiry)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()

            val schedulerName = ("fg-exp-" + voucher.username)
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .take(48)

            val disable = when (mode) {
                VoucherMode.HOTSPOT ->
                    "/ip hotspot user disable [find where name=" + quote(voucher.username) + "]"
                VoucherMode.PPPOE ->
                    "/ppp secret disable [find where name=" + quote(voucher.username) + "]"
                VoucherMode.USER_MANAGER ->
                    "/user-manager user disable [find where name=" + quote(voucher.username) + "]"
                VoucherMode.OFFLINE -> return@forEach
            }

            val onEvent = disable +
                "; /system scheduler remove [find where name=" +
                quote(schedulerName) +
                "]"

            append("/system scheduler add")
            append(" name=" + quote(schedulerName))
            append(
                " start-date=" +
                    dateTime.format(
                        DateTimeFormatter.ofPattern("MMM/dd/yyyy", Locale.ENGLISH)
                    ).lowercase(Locale.ENGLISH)
            )
            append(
                " start-time=" +
                    dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            )
            append(" interval=0s")
            append(" on-event=" + quote(onEvent))
            append(" comment=" + quote("FG MTM voucher expiry"))
            appendLine()
        }
    }

    private fun quote(value: String): String {
        val quote = 34.toChar()
        val slash = 92.toChar()
        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    slash -> {
                        append(slash)
                        append(slash)
                    }
                    quote -> {
                        append(slash)
                        append(quote)
                    }
                    else -> append(char)
                }
            }
        }
        return quote + escaped + quote
    }
}
