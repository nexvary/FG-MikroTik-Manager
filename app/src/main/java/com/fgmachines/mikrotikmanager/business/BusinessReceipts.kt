package com.fgmachines.mikrotikmanager.business

import java.time.LocalDate

class BusinessReceipts(private val store: BusinessStore) {
    private fun esc(value: String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    fun html(scope: BusinessScope,id: String,sale: Boolean,arabic: Boolean): String {
        val rows=mutableListOf<Pair<String,String>>()
        val title=if(arabic) "FG MTM — إيصال داخلي" else "FG MTM — Internal receipt"
        rows+=(if(arabic) "المرجع" else "Reference") to id
        if(sale) {
            val s=BusinessSales(store).sale(scope,id) ?: error("SALE_NOT_FOUND")
            rows+=(if(arabic)"العميل" else "Customer") to s.customer
            s.lines.forEach { rows+=it.name to "${it.quantity} × ${BusinessMoney.format(it.unitMinor,s.currency)}" }
            rows+=(if(arabic)"الإجمالي" else "Total") to BusinessMoney.format(s.total,s.currency)
            rows+=(if(arabic)"المحصّل عند الإصدار" else "Collected at issue") to BusinessMoney.format(s.paid,s.currency)
            rows+=(if(arabic)"الحالة" else "Status") to if(s.voided) (if(arabic)"ملغى" else "Canceled") else (if(arabic)"مسجل" else "Recorded")
        } else {
            val i=BusinessOperations(store).invoice(scope,id) ?: error("INVOICE_NOT_FOUND")
            rows+=(if(arabic)"المشترك" else "Subscriber") to i.customer;rows+=(if(arabic)"الباقة" else "Plan") to i.plan
            rows+=(if(arabic)"الإجمالي" else "Total") to BusinessMoney.format(i.amount,i.currency)
            rows+=(if(arabic)"المحصّل عند الإصدار" else "Collected at issue") to BusinessMoney.format(i.paid,i.currency)
            rows+=(if(arabic)"البداية" else "Starts") to LocalDate.ofEpochDay(i.start).toString()
            rows+=(if(arabic)"النهاية غير شاملة" else "End exclusive") to LocalDate.ofEpochDay(i.end).toString()
            rows+=(if(arabic)"الحالة" else "Status") to if(i.voided) (if(arabic)"ملغاة" else "Canceled") else (if(arabic)"مسجلة" else "Recorded")
        }
        store.helper.readableDatabase.rawQuery("SELECT method,reference FROM payment_details WHERE ledger_id=?",arrayOf("$id:p")).use { c -> if(c.moveToFirst()) { rows+=(if(arabic)"طريقة الدفع" else "Payment method") to c.getString(0);rows+=(if(arabic)"مرجع الدفع" else "Payment reference") to c.getString(1) } }
        val table=rows.joinToString("") { "<tr><th>${esc(it.first)}</th><td>${esc(it.second)}</td></tr>" }
        return """<!doctype html><html lang="${if(arabic)"ar" else "en"}" dir="${if(arabic)"rtl" else "ltr"}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>
            body{font-family:sans-serif;padding:16px;color:#111}h1{font-size:22px}table{width:100%;border-collapse:collapse}td,th{border-bottom:1px solid #ccc;padding:9px;text-align:start;overflow-wrap:anywhere}td{unicode-bidi:plaintext}th{width:35%}@page{margin:12mm}</style></head><body>
            <h1>$title</h1><table>$table</table><p>${if(arabic)"مستند داخلي، وليس فاتورة ضريبية. التحصيل مسجل يدويًا ولا يُثبت تحويلًا إلكترونيًا." else "Internal document, not a tax invoice. Payment is manually recorded and does not verify an electronic transfer."}</p></body></html>"""
    }
}
