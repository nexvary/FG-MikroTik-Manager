package com.fgmachines.mikrotikmanager.voucher

/**
 * Builds RouterOS CLI commands for voucher batches.
 *
 * This builder is kept for command-by-command provisioning, while
 * RouterOsScriptExporter is used for downloadable .rsc output.
 */
class RouterOsVoucherScriptBuilder {

    fun build(batch: VoucherBatch): String = buildString {
        appendLine("/log info \"FG MikroTik Manager: starting voucher batch\"")
        batch.vouchers.forEach { voucher ->
            when (batch.request.mode) {
                VoucherMode.HOTSPOT ->
                    appendLine(buildHotspotUser(voucher))
                VoucherMode.USER_MANAGER ->
                    buildUserManagerCommands(voucher).forEach(::appendLine)
                VoucherMode.PPPOE ->
                    appendLine(buildPppoeSecret(voucher))
                VoucherMode.OFFLINE -> Unit
            }
        }
        appendLine("/log info \"FG MikroTik Manager: voucher batch completed\"")
    }.trimEnd()

    fun buildHotspotUser(voucher: VoucherDraft): String = buildString {
        append("/ip hotspot user add")
        append(" name=").append(routerOsString(voucher.username))
        if (voucher.password.isNotEmpty()) {
            append(" password=").append(routerOsString(voucher.password))
        }
        append(" profile=").append(routerOsString(voucher.profile))
        append(" server=").append(routerOsString(voucher.server))
        if (voucher.comment.isNotEmpty()) {
            append(" comment=").append(routerOsString(voucher.comment))
        }
        voucher.limitUptime?.takeIf { it.isNotBlank() }?.let {
            append(" limit-uptime=").append(routerOsString(it))
        }
        voucher.limitBytesTotal?.let {
            append(" limit-bytes-total=").append(it)
        }
    }

    fun buildUserManagerCommands(voucher: VoucherDraft): List<String> {
        val createUser = buildString {
            append("/user-manager user add")
            append(" name=").append(routerOsString(voucher.username))
            if (voucher.password.isNotEmpty()) {
                append(" password=").append(routerOsString(voucher.password))
            }
            if (voucher.comment.isNotEmpty()) {
                append(" comment=").append(routerOsString(voucher.comment))
            }
        }

        if (voucher.profile.isBlank() || voucher.profile == "No Profile") {
            return listOf(createUser)
        }

        val assignProfile = buildString {
            append("/user-manager user-profile add")
            append(" profile=").append(routerOsString(voucher.profile))
            append(" user=").append(routerOsString(voucher.username))
        }

        return listOf(createUser, assignProfile)
    }

    fun buildPppoeSecret(voucher: VoucherDraft): String = buildString {
        append("/ppp secret add")
        append(" name=").append(routerOsString(voucher.username))
        if (voucher.password.isNotEmpty()) {
            append(" password=").append(routerOsString(voucher.password))
        }
        append(" service=pppoe")
        append(" profile=").append(routerOsString(voucher.profile))
        if (voucher.comment.isNotEmpty()) {
            append(" comment=").append(routerOsString(voucher.comment))
        }
    }

    private fun routerOsString(value: String): String {
        val escaped = buildString(value.length + 2) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(char)
                }
            }
        }
        return "\"$escaped\""
    }
}
