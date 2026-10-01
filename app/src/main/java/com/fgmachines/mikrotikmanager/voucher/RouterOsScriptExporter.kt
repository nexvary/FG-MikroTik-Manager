package com.fgmachines.mikrotikmanager.voucher

class RouterOsScriptExporter {

    fun export(batch: VoucherBatch): String {
        val header = buildString {
            appendLine("# FG MikroTik Manager")
            appendLine("# Generated voucher batch")
            appendLine("# Mode: " + batch.request.mode.name)
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
    }

    private fun exportUserManager(batch: VoucherBatch): String = buildString {
        appendLine("# User Manager commands vary by RouterOS generation/package.")
        appendLine("# This batch is intentionally not emitted until router capability detection selects the correct adapter.")
        batch.vouchers.forEach {
            appendLine("# " + safeComment(it.username) + " / profile=" + safeComment(it.profile))
        }
    }

    private fun quote(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""

    private fun safeComment(value: String): String =
        value.replace("\r", " ").replace("\n", " ")
}
