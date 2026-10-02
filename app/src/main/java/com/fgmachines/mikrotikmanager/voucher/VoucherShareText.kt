package com.fgmachines.mikrotikmanager.voucher

object VoucherShareText {
    fun build(voucher: VoucherDraft, arabic: Boolean, activated: Boolean): String = buildString {
        fun line(ar: String, en: String, value: String) { appendLine((if(arabic) ar else en) + ": " + value) }
        appendLine(voucher.branding.networkName.ifBlank { "FG Machines WiFi" })
        if (!activated) appendLine(if(arabic) "الكارت غير مؤكد التفعيل على الراوتر" else "Router activation is not confirmed")
        line("كود الكارت", "Voucher code", voucher.username)
        line("كلمة المرور", "Password", voucher.password)
        line("مدة الاستخدام", "Usage allowance", voucher.limitUptime ?: voucher.displayDuration(arabic))
        voucher.limitBytesTotal?.let { line("البيانات", "Data", "%.2f MB".format(java.util.Locale.US, it / 1048576.0)) }
        voucher.displayExpiry(arabic)?.let { line("تاريخ الانتهاء", "Expires", it) }
        if(voucher.branding.portalLoginUrl.isNotBlank() && voucher.mode == VoucherMode.HOTSPOT) {
            line("صفحة الدخول", "Login page", voucher.branding.portalLoginUrl)
        }
        if(voucher.mode == VoucherMode.HOTSPOT) appendLine(if(arabic) "اتصل بواي فاي الشبكة، ثم أدخل الكود وكلمة المرور في صفحة الدخول." else "Connect to the network Wi-Fi, then enter the code and password on its login page.")
        if(voucher.mode == VoucherMode.PPPOE) appendLine(if(arabic) "هذه بيانات اتصال PPPoE." else "These are PPPoE connection credentials.")
        if(voucher.branding.supportPhone.isNotBlank()) line("الدعم", "Support", voucher.branding.supportPhone)
    }.trim()
}
