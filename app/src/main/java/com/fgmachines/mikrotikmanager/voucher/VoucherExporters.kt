package com.fgmachines.mikrotikmanager.voucher

import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvVoucherExporter {
    fun export(batch: VoucherBatch): String = buildString {
        appendLine(
            "username,password,profile,mode,duration_value,duration_unit," +
                "expiry,bytes_limit,price_egp,network,support_phone"
        )
        batch.vouchers.forEach { voucher ->
            appendLine(
                listOf(
                    voucher.username,
                    voucher.password,
                    voucher.profile,
                    voucher.mode.name,
                    voucher.durationValue.toString(),
                    voucher.durationUnit.name,
                    voucher.absoluteExpiryEpochMs?.let(::formatExpiry).orEmpty(),
                    voucher.limitBytesTotal?.toString().orEmpty(),
                    voucher.branding.priceEgp?.toString().orEmpty(),
                    voucher.branding.networkName,
                    voucher.branding.supportPhone
                ).joinToString(",") { csvCell(it) }
            )
        }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace(""", """")
        return if (
            escaped.contains(',') ||
            escaped.contains('"') ||
            escaped.contains('\n') ||
            escaped.contains('\r')
        ) {
            """ + escaped + """
        } else {
            escaped
        }
    }
}

class HtmlVoucherExporter {
    fun export(batch: VoucherBatch): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html lang="ar" dir="rtl"><head><meta charset="utf-8">")
        appendLine("<meta name="viewport" content="width=device-width,initial-scale=1">")
        appendLine("<title>FG MTM Vouchers</title>")
        appendLine("<style>")
        appendLine("@page{size:A4;margin:8mm}*{box-sizing:border-box}")
        appendLine("body{margin:0;font-family:Tahoma,Arial,sans-serif;background:#fff;color:#111}")
        appendLine(".grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7mm}")
        appendLine(".voucher{min-height:58mm;border:1.5px solid #416074;border-radius:10px;padding:6mm;break-inside:avoid}")
        appendLine("header,.meta,footer{display:flex;justify-content:space-between;gap:10px;align-items:center}")
        appendLine("header{border-bottom:2px solid #159dff;padding-bottom:3mm;font-size:17px}")
        appendLine(".price{font-weight:700;color:#087a4f}")
        appendLine(".credentials{display:grid;grid-template-columns:1fr 1fr;gap:4mm;margin:4mm 0}")
        appendLine(".credentials div{border:1px solid #a7bac8;border-radius:7px;padding:3mm;text-align:center}")
        appendLine("small{display:block;color:#666;margin-bottom:2mm}b{font-size:17px;letter-spacing:.5px}")
        appendLine(".meta{font-size:12px;flex-wrap:wrap}.expiry{color:#9b3f00;font-weight:700}")
        appendLine("footer{color:#444;font-size:11px;margin-top:3mm;border-top:1px solid #ddd;padding-top:2mm}")
        appendLine(".qr{text-align:center;margin:3mm 0}.qr svg{width:24mm;height:24mm}")
        appendLine("@media print{body{print-color-adjust:exact}}")
        appendLine("</style></head><body><main class="grid">")

        batch.vouchers.forEach { voucher ->
            appendLine("<article class="voucher">")
            append("<header><strong>")
            append(html(voucher.branding.networkName.ifBlank { "WiFi" }))
            append("</strong><span class="price">")
            append(html(priceArabic(voucher)))
            appendLine("</span></header>")

            appendLine("<div class="credentials">")
            append("<div><small>اسم المستخدم</small><b>")
            append(html(voucher.username))
            appendLine("</b></div>")
            append("<div><small>كلمة المرور</small><b>")
            append(html(voucher.password))
            appendLine("</b></div></div>")

            append("<div class="qr">")
            append(VoucherQrSvgFactory.create(VoucherQrPayloadBuilder.build(voucher)))
            appendLine("</div>")

            append("<div class="meta"><span>المدة: ")
            append(html(voucher.displayDuration(arabic = true)))
            append("</span><span>الباقة: ")
            append(html(voucher.profile))
            appendLine("</span></div>")

            voucher.absoluteExpiryEpochMs?.let { expiry ->
                append("<div class="meta expiry"><span>ينتهي: ")
                append(html(formatExpiry(expiry)))
                appendLine("</span></div>")
            }

            append("<footer><span>")
            append(html(voucher.branding.supportPhone))
            append("</span><span>FG MTM</span></footer>")
            appendLine("</article>")
        }

        appendLine("</main></body></html>")
    }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace(""", "&quot;")
            .replace("'", "&#39;")
}

object VoucherQrPayloadBuilder {
    fun build(voucher: VoucherDraft): String =
        buildString {
            appendLine("FG MTM Voucher")
            appendLine("Network: " + voucher.branding.networkName)
            appendLine("Username: " + voucher.username)
            appendLine("Password: " + voucher.password)
            appendLine("Duration: " + voucher.displayDuration(arabic = false))
            voucher.absoluteExpiryEpochMs?.let {
                appendLine("Expires: " + formatExpiry(it))
            }
            voucher.branding.priceEgp?.let {
                appendLine("Price: " + DecimalFormat("0.##").format(it) + " EGP")
            }
            if (voucher.profile.isNotBlank()) appendLine("Profile: " + voucher.profile)
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

private fun priceArabic(voucher: VoucherDraft): String =
    voucher.branding.priceEgp?.let {
        DecimalFormat("0.##").format(it) + " جنيه"
    } ?: voucher.branding.priceText

private fun formatExpiry(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(Date(epochMs))
