package com.fgmachines.mikrotikmanager.voucher

import java.net.URLEncoder

class CsvVoucherExporter {
    fun export(batch: VoucherBatch): String = buildString {
        appendLine("username,password,profile,mode,time_limit,bytes_limit,price,network,support_phone")
        batch.vouchers.forEach { voucher ->
            appendLine(
                listOf(
                    voucher.username,
                    voucher.password,
                    voucher.profile,
                    voucher.mode.name,
                    voucher.limitUptime.orEmpty(),
                    voucher.limitBytesTotal?.toString().orEmpty(),
                    voucher.branding.priceText,
                    voucher.branding.networkName,
                    voucher.branding.supportPhone
                ).joinToString(",") { csvCell(it) }
            )
        }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (
            escaped.contains(',') ||
            escaped.contains('\"') ||
            escaped.contains('\n') ||
            escaped.contains('\r')
        ) {
            "\"" + escaped + "\""
        } else {
            escaped
        }
    }
}

class HtmlVoucherExporter {
    fun export(batch: VoucherBatch): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        appendLine("<title>FG MikroTik Manager Vouchers</title>")
        appendLine("<style>")
        appendLine("@page{size:A4;margin:8mm}*{box-sizing:border-box}")
        appendLine("body{margin:0;font-family:Arial,Tahoma,sans-serif;background:#fff;color:#111}")
        appendLine(".grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7mm}")
        appendLine(".voucher{min-height:55mm;border:1.5px solid #666;border-radius:10px;padding:7mm;break-inside:avoid}")
        appendLine("header,.meta,footer{display:flex;justify-content:space-between;gap:10px}")
        appendLine("header{border-bottom:2px solid #222;padding-bottom:4mm;font-size:17px}")
        appendLine(".credentials{display:grid;grid-template-columns:1fr 1fr;gap:5mm;margin:6mm 0}")
        appendLine(".credentials div{border:1px solid #bbb;border-radius:7px;padding:4mm;text-align:center}")
        appendLine("small{display:block;color:#666;margin-bottom:2mm}b{font-size:18px;letter-spacing:.5px}")
        appendLine(".meta,footer{color:#444;font-size:12px}footer{margin-top:4mm;border-top:1px solid #ddd;padding-top:3mm}")
        appendLine("@media print{body{print-color-adjust:exact}}")
        appendLine("</style></head><body><main class=\"grid\">")

        batch.vouchers.forEach { voucher ->
            appendLine("<article class=\"voucher\">")
            append("<header><strong>")
            append(html(voucher.branding.networkName.ifBlank { "WiFi" }))
            append("</strong><span>")
            append(html(voucher.branding.priceText))
            appendLine("</span></header>")

            appendLine("<div class=\"credentials\">")
            append("<div><small>Username</small><b>")
            append(html(voucher.username))
            appendLine("</b></div>")
            append("<div><small>Password</small><b>")
            append(html(voucher.password))
            appendLine("</b></div></div>")

            append("<div class=\"meta\"><span>")
            append(html(voucher.profile))
            append("</span><span>")
            append(html(voucher.limitUptime.orEmpty()))
            appendLine("</span></div>")

            append("<footer>")
            append(html(voucher.branding.supportPhone))
            appendLine("</footer></article>")
        }

        appendLine("</main></body></html>")
    }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}

object VoucherQrPayloadBuilder {
    fun build(voucher: VoucherDraft): String =
        buildString {
            appendLine("FG MikroTik Voucher")
            appendLine("Network: " + voucher.branding.networkName)
            appendLine("Username: " + voucher.username)
            appendLine("Password: " + voucher.password)
            if (voucher.profile.isNotBlank()) appendLine("Profile: " + voucher.profile)
            voucher.limitUptime?.takeIf { it.isNotBlank() }?.let {
                appendLine("Time: " + it)
            }
            if (voucher.branding.supportPhone.isNotBlank()) {
                append("Support: " + voucher.branding.supportPhone)
            }
        }.trim()

    fun buildLoginUrl(baseUrl: String, voucher: VoucherDraft): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return baseUrl + separator +
            "username=" + encode(voucher.username) +
            "&password=" + encode(voucher.password)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
