package com.fgmachines.mikrotikmanager.accesspoint

import android.graphics.pdf.PdfDocument
import android.graphics.Color
import android.text.TextPaint
import android.text.StaticLayout
import android.text.Layout
import android.text.TextDirectionHeuristics
import java.io.OutputStream

object ApPdf {
    fun write(summary:List<ApRow>,period:String,arabic:Boolean,output:OutputStream) {
        val doc=PdfDocument();var number=1;var page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());var y=36f
        val paint=TextPaint().apply{color=Color.rgb(18,52,74);textSize=13f;isAntiAlias=true}
        fun line(value:String) {
            val layout=StaticLayout.Builder.obtain(value,0,value.length,paint,523).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(if(arabic)TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
            if(y+layout.height>800){doc.finishPage(page);page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,++number).create());y=36f}
            page.canvas.save();page.canvas.translate(36f,y);layout.draw(page.canvas);page.canvas.restore();y+=layout.height+12
        }
        try {
            paint.textSize=23f;line("FG MTM · FG Machines");paint.textSize=16f;line(if(arabic)"تقرير رصد نقاط الوصول" else "Access Point Observations");paint.textSize=13f;line(period);line(java.time.ZonedDateTime.now().toString())
            line(if(arabic)"عينات محلية وليست سجل جلسات كاملًا. ربط العملاء استنتاجي." else "Local samples, not a complete session ledger. Client association is inferred.")
            for(row in summary){line("#"+row["rankByObservedAccounts"]+" · "+row["shop"].orEmpty().ifBlank{row["name"].orEmpty()});line(row["mac"].orEmpty()+" · "+row["ip"].orEmpty());line((if(arabic)"عملاء: " else "Clients: ")+row["observedClients"]+" | "+(if(arabic)"حسابات: " else "Accounts: ")+row["observedAccounts"]+" | "+(if(arabic)"جلسات: " else "Sessions: ")+row["observedSessions"]);line((if(arabic)"ترافيك: " else "Traffic: ")+row["totalTrafficBytes"]+" bytes | "+(if(arabic)"ذروة: " else "Peak: ")+row["peakObservedHour"])}
            line(if(arabic)"الحسابات ليست كروتًا مباعة مؤكدة. المبيعات والإيراد غير منسوبة لعدم وجود علاقة موثقة." else "Accounts are not confirmed sold vouchers. Sales and revenue are not attributed without verified evidence.")
            doc.finishPage(page);doc.writeTo(output)
        } finally {doc.close()}
    }
}
