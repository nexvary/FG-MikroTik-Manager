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
        appendLine("# RouterOS v7 User Manager / userman-5")
        appendLine("/user-manager user")
        batch.vouchers.forEach { voucher ->
            append("add name=" + quote(voucher.username))
            append(" password=" + quote(voucher.password))
            if (voucher.comment.isNotBlank()) append(" comment=" + quote(voucher.comment))
            appendLine()
        }
        appendLine()
        appendLine("# Profile assignment is handled by the User Manager adapter after")
        appendLine("# capability/profile lookup because profiles and limitations are separate objects.")
    }

    private fun quote(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""

    private fun safeComment(value: String): String =
        value.replace("\r", " ").replace("\n", " ")
}
