package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.fgmachines.mikrotikmanager.accesspoint.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AccessPointScreen(manager:AccessPointManager?,arabic:Boolean,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val coroutine=rememberCoroutineScope()
    val store=remember(manager?.routerKey){AccessPointStore(context,manager?.routerKey.orEmpty())}
    DisposableEffect(store){onDispose{store.close()}}
    var snapshot by remember(manager){mutableStateOf<ApSnapshot?>(null)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<ApRow?>(null)}
    var showUnknown by remember{mutableStateOf(false)};var report by remember{mutableStateOf(false)}
    var from by remember{mutableStateOf(LocalDate.now().toString())};var until by remember{mutableStateOf(LocalDate.now().toString())}
    var summary by remember{mutableStateOf<List<ApRow>>(emptyList())}
    var exportRows by remember{mutableStateOf<List<ApRow>>(emptyList())}
    val tr={ar:String,en:String->if(arabic)ar else en}
    fun refresh() {if(busy || manager==null)return;busy=true;error="";coroutine.launch {
        try {val next=manager.load(withContext(Dispatchers.IO){store.mappings()});withContext(Dispatchers.IO){store.save(next)};snapshot=next}
        catch(c:kotlinx.coroutines.CancellationException){throw c}
        catch(e:Exception){error=e.message.orEmpty()}
        finally{busy=false}
    }}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)coroutine.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(ApExport.csv(exportRows).toByteArray(Charsets.UTF_8))} ?: error("Cannot open output")};error=tr("تم حفظ التقرير","Report saved")}catch(e:Exception){error=e.message.orEmpty()}}}
    val xlsx=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->if(uri!=null)coroutine.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{ApExport.xlsx(summary,exportRows,it)} ?: error("Cannot open output")};error=tr("تم حفظ التقرير","Report saved")}catch(e:Exception){error=e.message.orEmpty()}}}
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->if(uri!=null)coroutine.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{ApPdf.write(summary,"$from — $until",arabic,it)} ?: error("Cannot open output")};error=tr("تم حفظ التقرير","Report saved")}catch(e:Exception){error=e.message.orEmpty()}}}
    LaunchedEffect(manager){refresh()}
    LazyColumn(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(12.dp)) {
        item {Text(tr("نقاط الوصول المتصلة","Connected Access Points"),style=MaterialTheme.typography.headlineSmall)}
        item {Text(tr("مؤكدة: ","Confirmed: ")+(snapshot?.devices?.count{it["classification"]=="Confirmed AP"}?.toString()?:"—")+tr(" • مرجحة: "," • Likely: ")+(snapshot?.devices?.count{it["classification"]=="Likely AP"}?.toString()?:"—"))}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={refresh()},enabled=!busy&&manager!=null){Text(tr("تحديث","Refresh"))};OutlinedButton(onClick={report=!report}){Text(tr("التقارير","Reports"))}}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(error.isNotEmpty())item{Text(error)}
        item {Text(tr("المنفذ هو مسار المرور وقد يكون خلفه سويتش. ربط العملاء استنتاجي؛ NAT قد يخفيهم. التحديث اليدوي محدود بمرة كل 30 ثانية.","The port is an upstream path and may contain a switch. Client association is inferred; NAT can hide clients. Manual refresh is limited to every 30 seconds."),style=MaterialTheme.typography.bodySmall)}
        if(snapshot?.warnings?.isNotEmpty()==true)item{Text(tr("مصادر غير متاحة: ","Unavailable sources: ")+snapshot!!.warnings.joinToString(", "),color=MaterialTheme.colorScheme.error)}
        if(report) {
            item{Text(tr("تقارير الرصد المحلي — آخر 90 يومًا","Local observation reports — last 90 days"),style=MaterialTheme.typography.titleMedium)}
            item{OutlinedTextField(from,{from=it},label={Text(tr("من YYYY-MM-DD","From YYYY-MM-DD"))},modifier=Modifier.fillMaxWidth())}
            item{OutlinedTextField(until,{until=it},label={Text(tr("إلى YYYY-MM-DD شامل","Through YYYY-MM-DD inclusive"))},modifier=Modifier.fillMaxWidth())}
            item{Button(onClick={coroutine.launch{try{val start=LocalDate.parse(from);val end=LocalDate.parse(until);require(!end.isBefore(start));val zone=ZoneId.systemDefault();val records=withContext(Dispatchers.IO){store.records(start.atStartOfDay(zone).toInstant().toEpochMilli(),end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())};summary=AccessPointStore.summary(records,snapshot?.devices.orEmpty());exportRows=records;error=""}catch(e:Exception){error=tr("راجع الفترة الزمنية","Check the date range")}}}){Text(tr("عرض المقارنة","Compare"))}}
            items(summary){row->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("#"+row["rankByObservedAccounts"]+"  "+row["shop"].orEmpty().ifBlank{row["name"].orEmpty()});Text(tr("عملاء: ","Clients: ")+row["observedClients"]+tr(" • حسابات/مستخدمون: "," • Accounts/users: ")+row["observedAccounts"]+tr(" • جلسات: "," • Sessions: ")+row["observedSessions"]);Text(tr("ترافيك مرصود: ","Observed traffic: ")+row["totalTrafficBytes"]+tr(" بايت • متوسط الجلسة: "," bytes • Avg session: ")+row["averageSessionSeconds"]+tr(" ث"," s"));Text(tr("ذروة الرصد: ","Observation peak: ")+row["peakObservedHour"]+tr(" • الباقة: "," • Plan: ")+row["topObservedPlan"]);Text(tr("المبيعات/الإيراد: غير منسوبة بلا دليل.","Sales/revenue: not attributed without evidence."),color=FgSilver)}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={pdf.launch("FG-MTM-AP-$from-$until.pdf")},enabled=exportRows.isNotEmpty()){Text("PDF")};OutlinedButton(onClick={xlsx.launch("FG-MTM-AP-$from-$until.xlsx")},enabled=exportRows.isNotEmpty()){Text("Excel XLSX")}}}
            item{OutlinedButton(onClick={csv.launch("FG-MTM-AP-$from-$until.csv")},enabled=exportRows.isNotEmpty()){Text(tr("تصدير الرصد CSV","Export observations CSV"))}}
            item{Text(tr("المؤشرات مستنتجة من عينات HotSpot، مع استخدام أقصى بايتات ومدة مرصودة لكل Session لتقليل التكرار. الحسابات لا تعني كروتًا مباعة، ولا ننسب مبيعات أو Revenue لأي AP بلا علاقة موثقة.","Metrics are inferred from sampled HotSpot sessions, using each session's maximum observed bytes/duration to reduce double counting. Accounts are not confirmed sold vouchers, and sales/revenue are never attributed without verified evidence."))}
        } else {
            item{Row{Checkbox(showUnknown,{showUnknown=it});Text(tr("عرض الأجهزة غير المصنفة أيضًا","Also show unclassified devices"))}}
            items(snapshot?.devices.orEmpty().filter{showUnknown || it["classification"] in listOf("Confirmed AP","Likely AP")},key={it["mac"].orEmpty()}) {row->
                Card(onClick={selected=row},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(row["shop"].orEmpty().ifBlank{row["name"].orEmpty()},style=MaterialTheme.typography.titleMedium)
                    Text(row["classification"].orEmpty()+" • "+row["reason"].orEmpty())
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr){Text(listOf(row["ip"],row["mac"],row["port"].orEmpty().ifBlank{"Unknown port"}).joinToString(" • "))}
                    Text(tr("جلسات نشطة منسوبة استنتاجيًا: ","Inferred active sessions: ")+(if("ip/hotspot/active" in snapshot!!.warnings)"N/A" else snapshot!!.sessions.count{it["ap"]==row["mac"]}.toString()))
                    if(row["mode"]=="NAT")Text(tr("هذا الجهاز يعمل بطريقة قد تقلل دقة إحصائيات العملاء.","This device operates in a mode that can reduce client-statistics accuracy."),color=MaterialTheme.colorScheme.error)
                }}
            }
            if(snapshot!=null && snapshot!!.devices.none{it["classification"] in listOf("Confirmed AP","Likely AP")})item{Text(tr("لم يظهر دليل كافٍ لتحديد نقطة وصول. اعرض الأجهزة غير المصنفة وحدد جهازك بعد التحقق منه.","No sufficient AP evidence. Show unclassified devices and identify your device after verifying it."))}
        }
    }
    selected?.let{row->
        var shop by remember(row){mutableStateOf(row["shop"].orEmpty())};var confirm by remember(row){mutableStateOf(row["reason"]=="User verified")};var mode by remember(row){mutableStateOf(row["mode"].orEmpty().ifBlank{"Unknown"})}
        AlertDialog(onDismissRequest={selected=null},title={Text(tr("تفاصيل نقطة الوصول","Access point details"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(row["name"].orEmpty());Text(row["mac"].orEmpty());Text(tr("المصادر: ","Sources: ")+row["sources"])
            OutlinedTextField(shop,{shop=it.take(120)},label={Text(tr("اسم المحل","Shop name"))})
            Row{Checkbox(confirm,{confirm=it});Text(tr("تحققت بنفسي أنه Access Point","I verified this device is an AP"))}
            Row{listOf("Unknown","Bridge","NAT").forEach{value->FilterChip(mode==value,{mode=value},label={Text(value)})}}
        }},confirmButton={TextButton(onClick={coroutine.launch{try{withContext(Dispatchers.IO){store.map(row["mac"].orEmpty(),shop,confirm,mode)};selected=null;refresh()}catch(e:Exception){error=e.message.orEmpty()}}}){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick={selected=null}){Text(tr("رجوع","Back"))}})
    }
}
