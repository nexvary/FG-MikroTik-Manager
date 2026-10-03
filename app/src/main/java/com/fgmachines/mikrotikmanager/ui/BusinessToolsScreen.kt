package com.fgmachines.mikrotikmanager.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.business.*
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessToolsScreen(arabic: Boolean,subscriber: Subscriber?,onBack: ()->Unit,onLanguageToggle: ()->Unit,model: BusinessToolsViewModel=viewModel(),router: BusinessRouter?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=androidx.compose.ui.platform.LocalContext.current
    SideEffect { model.router=router }
    LaunchedEffect(state.receipt) { state.receipt?.let { printBusinessReceipt(context,it);model.receiptConsumed() } }
    var networkSelection by remember { mutableStateOf(setOf<String>()) }
    var networkCurrency by rememberSaveable { mutableStateOf("EGP") }
    var networkSearch by rememberSaveable { mutableStateOf("") }
    var networkPage by rememberSaveable { mutableIntStateOf(0) }
    fun tr(ar: String,en: String)=if(arabic) ar else en
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf("") }
    var planName by rememberSaveable { mutableStateOf("") }
    var planCurrency by rememberSaveable { mutableStateOf("EGP") }
    var planPrice by rememberSaveable { mutableLongStateOf(0) }
    var handled by rememberSaveable { mutableIntStateOf(state.saved) }
    var from by rememberSaveable { mutableStateOf(state.from) };var to by rememberSaveable { mutableStateOf(state.to) }
    // Passwords are deliberately not written to saved state or preferences.
    var password by remember { mutableStateOf("") };var confirmation by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) };var restoring by rememberSaveable { mutableStateOf(false) }
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.exportCsv(uri) } }
    val template=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.exportCsv(uri,true) } }
    val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::readImport) }
    val backupFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri!=null) { backupUri=uri;restoring=false;editor="password" } }
    val restoreFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { backupUri=uri;restoring=true;editor="password" } }
    fun back() { if(!state.busy) { if(state.tab!="home") model.tab("home") else onBack() } }
    BackHandler { back() }
    LaunchedEffect(state.saved) { if(handled!=state.saved) { editor=null;password="";confirmation="";backupUri=null;handled=state.saved;if(state.message=="BRANCH_CHANGED") onBack() } }
    val error=state.error?.let { code -> when(code) {
        "NETWORK_INVOICE_LOCKED" -> tr("أوقف الخدمة من تطبيق على الراوتر قبل إلغاء الفاتورة.","Suspend service from Apply to router before canceling this invoice.")
        "NOT_BOUND" -> tr("استورد حساب الشبكة واربطه بهذا المشترك أولًا.","Import and bind the network account first.")
        "WRONG_ROUTER","BINDING_CONFLICT","ACCOUNT_CHANGED" -> tr("الراوتر أو الحساب لا يطابق الربط المحفوظ. لم يتم تأكيد التطبيق.","Router or account does not match the saved binding. Application is not confirmed.")
        "PLAN_MISMATCH" -> tr("نوع الخدمة أو عملة الباقة لا يطابق المشترك.","Plan service or currency does not match the subscriber.")
        "CANCEL_LATEST_FIRST" -> tr("ألغِ آخر تجديد أولًا للحفاظ على ترتيب فترات الاشتراك.","Cancel the latest renewal first to preserve the subscription timeline.")
        "RESTORE_NEEDS_EMPTY_STORE" -> tr("الاستعادة متاحة في سجل أعمال فارغ فقط؛ لن تُستبدل بياناتك الحالية.","Restore requires an unused business store; existing data will not be replaced.")
        "CSV_HEADER" -> tr("استخدم أعمدة ملف النموذج بنفس ترتيبها.","Use the template column names and order.")
        "IMPORT_CONFLICT","INVALID_CSV","IMPORT_LIMIT","EMPTY_IMPORT" -> tr("راجع تنسيق CSV والتكرارات. الحد لكل استيراد 1000 مشترك.","Check CSV format and duplicates. Each import supports up to 1000 subscribers.")
        "FILE_TOO_LARGE" -> tr("الملف أكبر من الحد المسموح للعملية.","File exceeds the per-operation size limit.")
        "INVALID_DATE" -> tr("اكتب تاريخًا صحيحًا بصيغة YYYY-MM-DD، والبداية قبل النهاية.","Use YYYY-MM-DD dates, with start no later than end.")
        "DUPLICATE_BRANCH" -> tr("اسم الفرع موجود بالفعل.","Branch name already exists.")
        "INVALID_AMOUNT","INVALID_TEXT","PASSWORD_SHORT" -> tr("راجع الحقول والمبالغ؛ كلمة النسخة الاحتياطية 12 حرفًا على الأقل.","Check fields and amounts; backup passwords need at least 12 characters.")
        "IDEMPOTENCY_CONFLICT" -> tr("معرف العملية مسجل ببيانات مختلفة. راجع السجل قبل التكرار.","Operation ID already has different data. Review records before retrying.")
        else -> tr("تعذرت العملية. لم يتم تأكيد نجاحها. في الاستعادة راجع كلمة المرور وصحة الملف؛ وفي التصدير احذف الملف غير المكتمل وأعد المحاولة.","Operation was not confirmed. For restore, check password and file; for export, delete any incomplete file and retry.")
    } }
    val tabs=listOf("network" to tr("استيراد من الراوتر","Router import"),"sales" to tr("المبيعات والإيصالات","Sales & receipts"),"plans" to tr("الباقات والتجديد","Plans & renewal"),"invoices" to tr("الفواتير","Invoices"),"expenses" to tr("المصروفات","Expenses"),"reports" to tr("التقارير والتصدير","Reports & export"),"branches" to tr("الفروع","Branches"),"import" to tr("استيراد المشتركين","Import subscribers"),"backup" to tr("نسخ واستعادة","Backup & restore"),"audit" to tr("سجل التدقيق","Audit trail"))
    Scaffold(containerColor=FgBlack,topBar={ TopAppBar(title={Text(tabs.firstOrNull { it.first==state.tab }?.second ?: tr("إدارة الأعمال","Business tools"))},navigationIcon={IconButton(onClick={back()},enabled=!state.busy){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("رجوع","Back"))}},actions={IconButton(onClick=onLanguageToggle){Icon(Icons.Outlined.Language,tr("اللغة","Language"))}},colors=TopAppBarDefaults.topAppBarColors(containerColor=FgPanel)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text(tr("الفرع الحالي: ","Current branch: ")+state.branch,color=FgMint) }
            if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(error!=null) item { Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick=model::refresh,enabled=!state.busy){Text(tr("إعادة التحميل","Reload"))} }
            state.message?.let { message -> item { Text(when(message) { "RENEWED"->tr("تم التجديد محليًا وتسجيل الفاتورة. لم يتغير الراوتر.","Renewed locally and invoice recorded. Router unchanged.");"BRANCH_CHANGED"->tr("تم تغيير الفرع الحالي.","Current branch changed.");"IMPORTED"->tr("تم استيراد المشتركين.","Subscribers imported.");"EXPORTED","BACKED_UP"->tr("تم حفظ الملف بنجاح.","File saved successfully.");"RESTORED"->tr("تمت استعادة بيانات الأعمال.","Business data restored.");else->tr("تم الحفظ.","Saved.") },color=FgMint) } }
            when(state.tab) {
                "home" -> {
                    item { Text(tr("باقات وحسابات محلية. الربط الآلي بالراوتر يأتي في مرحلة لاحقة.","Local plans and accounts. Automatic router integration is a later phase."),color=FgSilver) }
                    items(tabs.chunked(2)) { row ->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            row.forEach { (id,label) ->
                                OutlinedButton(onClick={model.tab(id)},enabled=!state.busy,
                                    modifier=Modifier.weight(1f).heightIn(min=68.dp)) { Text(label) }
                            }
                        }
                    }
                }
                "network" -> {
                    item { Text(tr("اقرأ الحسابات، اختر المطلوب، ثم أكّد الربط. الحساب المطابق في الفرع يُربط بدل إنشاء نسخة مكررة. لا تُقرأ كلمات المرور.","Read accounts, select rows, then confirm binding. Matching branch accounts are linked without duplicates. Passwords are not requested."),color=FgSilver)
                        if(router==null) Text(tr("ارجع واتصل بالراوتر أولًا.","Go back and connect to the router first."),color=FgAmber)
                        Button(onClick={networkSelection=emptySet();networkPage=0;model.scanRouter()},enabled=!state.busy && router!=null){Text(tr("قراءة الحسابات","Read accounts"))}
                        OutlinedTextField(networkSearch,{networkSearch=it.take(120);networkPage=0},label={Text(tr("بحث الحسابات","Search accounts"))},modifier=Modifier.fillMaxWidth())
                        BusinessMoney.currencies.chunked(3).forEach { row->Row { row.forEach { c->FilterChip(networkCurrency==c,{networkCurrency=c},label={Text(c)}) } } }
                    }
                    val accounts=state.catalog?.accounts?.filter { it.name.contains(networkSearch,true) }.orEmpty()
                    items(accounts.drop(networkPage*50).take(50),key={it.service+it.id}) { a ->
                        val key=a.service+":"+a.id
                        FilterChip(selected=key in networkSelection,onClick={networkSelection=if(key in networkSelection) networkSelection-key else networkSelection+key},enabled=!state.busy,label={Text(a.name+" • "+a.service+" • "+a.profile)})
                    }
                    item { Row { TextButton(onClick={networkPage--},enabled=networkPage>0 && !state.busy){Text(tr("السابق","Previous"))};TextButton(onClick={networkPage++},enabled=(networkPage+1)*50<accounts.size && !state.busy){Text(tr("التالي","Next"))} }
                        Button(onClick={model.importRouter(networkSelection,networkCurrency)},enabled=!state.busy && networkSelection.size in 1..1000){Text(tr("تأكيد ربط المحدد: ","Confirm selected bindings: ")+networkSelection.size)} }
                }
                "sales" -> {
                    item { Text(tr("مبيعات يدوية بدون إدارة مخزون أو ضرائب.","Manual sales without inventory or tax management."),color=FgSilver)
                        if(subscriber!=null)Button(onClick={editor="sale"},enabled=!state.busy){Text(tr("بيع جديد","New sale"))}
                        else Text(tr("افتح حساب العميل ثم إدارة الأعمال لإضافة بيع.","Open a customer account, then Business tools to add a sale."),color=FgSilver)
                    }
                    items(state.sales,key={it.id}) { sale -> BusinessToolCard {
                        Text(sale.customer,color=FgMint);Text(BusinessMoney.format(sale.total,sale.currency),color=FgAmber)
                        TextButton(onClick={model.receipt(sale.id,true,arabic)},enabled=!state.busy){Text(tr("طباعة / حفظ PDF","Print / save PDF"))}
                        if(sale.voided)Text(tr("ملغاة","Canceled"),color=FgSilver)
                        else TextButton(onClick={target=sale.id;editor="saleCancel"},enabled=!state.busy){Text(tr("إلغاء البيع","Cancel sale"))}
                    } }
                }
                "plans" -> {
                    item { Button(onClick={editor="plan"},enabled=!state.busy){Text(tr("باقة جديدة","Add plan"))} }
                    item { Text(subscriber?.let { tr("تجديد للمشترك: ","Renew for: ")+it.name } ?: tr("افتح حساب مشترك ثم إدارة الأعمال لتجديد اشتراكه.","Open a subscriber account, then Business tools to renew."),color=FgSilver) }
                    items(state.plans,key={it.id}) { p -> BusinessToolCard {
                        Text(p.name,color=FgMint,style=MaterialTheme.typography.titleMedium)
                        Text(tr("السعر: ","Price: ")+BusinessMoney.format(p.price,p.currency),color=FgWhite)
                        Text(tr("المدة: ","Duration: ")+p.days+tr(" يوم"," days"),color=FgSilver)
                        Text(p.service,color=FgSilver)
                        if(subscriber!=null) OutlinedButton(onClick={target=p.id;planName=p.name;planPrice=p.price;planCurrency=p.currency;editor="renew"},enabled=!state.busy && subscriber.currency==p.currency && subscriber.service==p.service){Text(tr("تجديد وإصدار فاتورة","Renew & invoice"))}
                    } }
                    if(state.plans.isEmpty() && !state.busy) item { Text(tr("لا توجد باقات في هذا الفرع.","No plans in this branch."),color=FgSilver) }
                }
                "invoices" -> {
                    item { Text(tr("فواتير اشتراك داخلية، وليست فواتير ضريبية. الإلغاء يعكس المستحق والتحصيل المرتبط ويُلغي فترة الاشتراك.","Internal subscription invoices, not tax invoices. Cancellation reverses linked charges and receipts and removes the period."),color=FgSilver) }
                    items(state.invoices,key={it.id}) { inv -> BusinessToolCard {
                        Text("#${inv.sequence} • ${inv.customer}",color=FgMint,style=MaterialTheme.typography.titleMedium)
                        Text(inv.plan,color=FgWhite);Text(BusinessMoney.format(inv.amount,inv.currency),color=FgAmber)
                        Text(tr("المحصّل عند الإصدار: ","Collected at issue: ")+BusinessMoney.format(inv.paid,inv.currency),color=FgSilver)
                        Text(tr("البداية: ","Starts: ")+businessLtr(LocalDate.ofEpochDay(inv.start).toString()),color=FgSilver)
                        Text(tr("النهاية (غير شاملة): ","End (exclusive): ")+businessLtr(LocalDate.ofEpochDay(inv.end).toString()),color=FgSilver)
                        Text(tr("مرجع: ","Reference: ")+inv.id,color=FgSilver,style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={model.receipt(inv.id,false,arabic)},enabled=!state.busy){Text(tr("طباعة / حفظ PDF","Print / save PDF"))}
                        if(!inv.voided) OutlinedButton(onClick={target=inv.id;editor="network"},enabled=!state.busy && router!=null){Text(tr("تطبيق على الراوتر","Apply to router"))}
                        if(inv.voided) Text(tr("ملغاة بقيد عكسي","Canceled with reversal"),color=FgAmber)
                        else OutlinedButton(onClick={target=inv.id;editor="cancel"},enabled=!state.busy){Text(tr("إلغاء الفاتورة","Cancel invoice"))}
                    } }
                    if(state.invoices.isEmpty() && !state.busy) item { Text(tr("لا توجد فواتير. أصدر فاتورة من تجديد باقة لمشترك.","No invoices. Renew a subscriber plan to issue one."),color=FgSilver) }
                }
                "expenses" -> {
                    item { Button(onClick={editor="expense"},enabled=!state.busy){Text(tr("مصروف جديد","Add expense"))} }
                    items(state.expenses,key={it.id}) { e -> BusinessToolCard {
                        Text(e.category,color=FgMint);Text(BusinessMoney.format(e.amount,e.currency),color=FgAmber);Text(e.note,color=FgWhite)
                        if(e.reversed) Text(tr("تم عكس المصروف","Expense reversed"),color=FgSilver)
                        else if(e.reversalOf==null) TextButton(onClick={target=e.id;editor="expenseReverse"},enabled=!state.busy){Text(tr("عكس المصروف","Reverse expense"))}
                        else Text(tr("قيد عكسي","Reversal"),color=FgSilver)
                    } }
                    if(state.expenses.isEmpty() && !state.busy) item { Text(tr("لا توجد مصروفات مسجلة.","No recorded expenses."),color=FgSilver) }
                }
                "reports" -> {
                    item { OutlinedTextField(from,{from=it.take(10)},label={Text(tr("من YYYY-MM-DD","From YYYY-MM-DD"))},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(to,{to=it.take(10)},label={Text(tr("إلى YYYY-MM-DD","To YYYY-MM-DD"))},singleLine=true,modifier=Modifier.fillMaxWidth()) }
                    item { Button(onClick={model.report(from,to)},enabled=!state.busy){Text(tr("عرض الفترة","Apply period"))};Text(tr("الفترة المعروضة: ","Displayed period: ")+businessLtr(state.from)+tr(" إلى "," to ")+businessLtr(state.to),color=FgSilver) }
                    item { Text(tr("الحركة حسب تاريخ التسجيل وتشمل القيود العكسية. الرصيد إجمالي كل الفترات. التحصيل ناقص المصروفات ليس صافي ربح.","Movements use posting dates and include reversals. Balance covers all dates. Receipts minus expenses is not net profit."),color=FgSilver) }
                    items(state.totals) { t -> BusinessToolCard {
                        Text(t.currency,color=FgMint,style=MaterialTheme.typography.titleMedium)
                        Text(tr("المستحقات: ","Charges: ")+BusinessMoney.format(t.charges,t.currency),color=FgWhite)
                        Text(tr("التحصيل: ","Receipts: ")+BusinessMoney.format(t.receipts,t.currency),color=FgWhite)
                        Text(tr("المصروفات: ","Expenses: ")+BusinessMoney.format(t.expenses,t.currency),color=FgWhite)
                        Text(tr("الرصيد الكلي: ","All-time balance: ")+BusinessMoney.format(t.balance,t.currency),color=FgAmber)
                    } }
                    item { OutlinedButton(onClick={csv.launch("FG-MTM-${state.from}-${state.to}.csv")},enabled=!state.busy && error==null){Text(tr("تصدير حركة الفترة CSV","Export period CSV"))};Text(tr("التصدير يحتوي بيانات العملاء والمبالغ بوحدات العملة الصغرى مثل القروش.","Export includes customer data and amounts in minor units such as cents."),color=FgSilver) }
                }
                "branches" -> {
                    item { Text(tr("فصل محلي للسجلات؛ لا توجد صلاحيات موظفين في هذه المرحلة.","Local record separation; employee authorization is not included yet."),color=FgSilver);Button(onClick={editor="branch"},enabled=!state.busy){Text(tr("فرع جديد","Add branch"))} }
                    items(state.branches,key={it.id}) { b -> OutlinedButton(onClick={model.switchBranch(b.id)},enabled=!state.busy && b.id!=state.branchId,modifier=Modifier.fillMaxWidth()){Text(b.name+if(b.id==state.branchId) tr(" • الحالي"," • current") else "")} }
                }
                "import" -> {
                    item { Text(tr("CSV بترميز UTF-8، حتى 1000 مشترك لكل عملية. راجع النموذج واحذف صف المثال. لا يتم إنشاء حسابات على الراوتر.","UTF-8 CSV, up to 1000 subscribers per operation. Use the template and remove its example row. No router accounts are created."),color=FgSilver) }
                    item { OutlinedButton(onClick={template.launch("FG-MTM-subscriber-template.csv")},enabled=!state.busy){Text(tr("حفظ نموذج CSV","Save CSV template"))};Button(onClick={importFile.launch(arrayOf("text/*","application/csv","application/octet-stream"))},enabled=!state.busy){Text(tr("اختيار ملف للمعاينة","Choose file to preview"))} }
                    state.importPreview?.let { p ->
                        item { Text(tr("صفوف صالحة: ","Valid rows: ")+p.rows.size,color=FgMint)
                            if(p.errors.isNotEmpty()) Text(tr("صفوف غير صالحة أو مكررة: ","Invalid or conflicting rows: ")+p.errors.take(40).joinToString(", "),color=FgAmber)
                            Text(tr("المعاينة تعرض أول 20 صفًا. الاستيراد كله يُرفض إذا وجد خطأ.","Preview shows the first 20 rows. Any error rejects the whole import."),color=FgSilver)
                            Button(onClick=model::importSubscribers,enabled=!state.busy && p.errors.isEmpty() && p.rows.isNotEmpty()){Text(tr("تأكيد الاستيراد","Confirm import"))} }
                        items(p.rows.take(20)) { r -> Text("${r.name} • ${r.service} • ${r.currency} • ${r.account}",color=FgWhite) }
                    }
                }
                "backup" -> {
                    item { Text(tr("نسخة مشفّرة لكل فروع بيانات الأعمال، حتى 20 ميجابايت قبل التشفير. لا تشمل أرشيف الكروت القديم أو كلمات مرور الراوتر. احفظ كلمة المرور؛ لا يمكن استرجاعها.","Encrypted backup of business data across all branches, up to 20 MiB before encryption. Excludes legacy vouchers and router passwords. Keep the password; it cannot be recovered."),color=FgSilver) }
                    item { Button(onClick={backupFile.launch("FG-MTM-business-${LocalDate.now()}.fgbackup")},enabled=!state.busy){Text(tr("إنشاء نسخة مشفّرة","Create encrypted backup"))} }
                    item { Text(tr("الاستعادة مسموحة فقط في سجل أعمال لم يُستخدم، ولا تستبدل بيانات موجودة. لا تحذف تطبيقك الحالي أو بياناته بغرض الاستعادة.","Restore is allowed only into an unused business store and never overwrites existing data. Do not uninstall or clear your current app to restore."),color=FgAmber) }
                    item { OutlinedButton(onClick={restoreFile.launch(arrayOf("application/octet-stream","*/*"))},enabled=!state.busy){Text(tr("اختيار نسخة للاستعادة","Choose backup to restore"))} }
                }
                "audit" -> {
                    item { Text(tr("سجل محلي آلي من إصدار 0.10.0. الفاعل: التطبيق المحلي؛ ليس نظام هوية موظفين أو سجلًا محصنًا ضد جهاز مخترق.","Automatic local trail from 0.10.0. Actor: local app; not employee identity or protection against a compromised device."),color=FgSilver) }
                    items(state.audit,key={it.sequence}) { a -> BusinessToolCard {
                        Text("#${a.sequence} • ${a.entity} • ${a.action}",color=FgMint)
                        Text(a.id,color=FgSilver,style=MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(a.at)),color=FgWhite)
                    } }
                    if(state.audit.isEmpty() && !state.busy) item { Text(tr("لا توجد أحداث بعد.","No events yet."),color=FgSilver) }
                }
            }
            if(state.tab in listOf("plans","invoices","expenses","audit","sales")) item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={model.page(false)},enabled=!state.busy && state.page>1){Text(tr("السابق","Previous"))};Text(state.page.toString(),color=FgSilver,modifier=Modifier.padding(12.dp));TextButton(onClick={model.page(true)},enabled=!state.busy && state.more){Text(tr("التالي","Next"))}
            } }
        }
    }
    if(editor=="network") NetworkInvoiceDialog(arabic,target,state,model){if(!state.busy)editor=null}
    else if(editor=="sale" && subscriber!=null) SaleEditor(arabic,subscriber.currency,state.busy,error,{if(!state.busy)editor=null}){id,lines,paid,method,reference->model.sell(id,subscriber.id,lines,paid,method,reference)}
    else if(editor=="password") AlertDialog(onDismissRequest={if(!state.busy){editor=null;password="";confirmation="";backupUri=null}},title={Text(if(restoring) tr("تأكيد الاستعادة","Confirm restore") else tr("حماية النسخة","Protect backup"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(restoring) tr("سيتم التحقق من الملف واستعادته فقط إذا كان سجل الأعمال فارغًا.","The file will be validated and restored only if the business store is unused.") else tr("كلمة مرور 12 حرفًا على الأقل. ستحتاجها عند الاستعادة.","At least 12 characters. You will need it to restore."))
        OutlinedTextField(password,{password=it.take(200)},label={Text(tr("كلمة مرور النسخة","Backup password"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!state.busy)
        if(!restoring) OutlinedTextField(confirmation,{confirmation=it.take(200)},label={Text(tr("تأكيد كلمة المرور","Confirm password"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!state.busy)
        if(error!=null) Text(error,color=MaterialTheme.colorScheme.error)
        if(backupUri==null) Text(tr("أعد اختيار الملف؛ لم نحتفظ بكلمة المرور بعد إعادة فتح الشاشة.","Select the file again; passwords are not retained after the screen is recreated."))
    }},confirmButton={TextButton(onClick={backupUri?.let { model.backup(it,password.toCharArray(),restoring) }},enabled=!state.busy && backupUri!=null && password.length>=12 && (restoring || password==confirmation)){Text(tr("تنفيذ","Proceed"))}},dismissButton={TextButton(onClick={editor=null;password="";confirmation="";backupUri=null},enabled=!state.busy){Text(tr("إلغاء","Cancel"))}})
    else editor?.let { mode -> key(mode,target) { BusinessToolEditor(mode,arabic,state.busy,error,planName,planCurrency,planPrice,onDismiss={if(!state.busy) editor=null},onSave={id,name,service,currency,amount,days,note,method,reference ->
        when(mode) { "plan"->model.addPlan(id,name,service,currency,amount,days);"renew"->subscriber?.let { model.renew(id,it.id,target,amount,method,reference) };"expense"->model.expense(id,name,amount,currency,note);"branch"->model.addBranch(id,name);"cancel"->model.cancelInvoice(target,id,note);"expenseReverse"->model.reverseExpense(target,id,note);"saleCancel"->model.cancelSale(target,id,note) }
    }) } }
}

@Composable private fun BusinessToolCard(content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=FgPanel)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp),content=content) } }

@Composable private fun BusinessToolEditor(mode: String,arabic: Boolean,busy: Boolean,error: String?,plan: String,planCurrency: String,price: Long,onDismiss: ()->Unit,onSave:(String,String,String,String,Long,Int,String,PaymentMethod,String)->Unit) {
    fun tr(ar: String,en: String)=if(arabic) ar else en
    val id=rememberSaveable { UUID.randomUUID().toString() }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) };var reference by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") };var service by rememberSaveable { mutableStateOf("HOTSPOT") };var currency by rememberSaveable { mutableStateOf(if(mode=="renew") planCurrency else "EGP") }
    var amount by rememberSaveable { mutableStateOf("") };var days by rememberSaveable { mutableStateOf("30") };var note by rememberSaveable { mutableStateOf("") }
    val minor=if(mode=="renew" && amount.isBlank()) 0L else runCatching { BusinessMoney.parse(amount) }.getOrNull()
    val valid=when(mode) { "branch"->name.isNotBlank();"plan"->name.isNotBlank() && minor!=null && days.toIntOrNull() in 1..3660;"renew"->minor!=null && minor<=price;"expense"->name.isNotBlank() && minor!=null && note.isNotBlank();else->note.isNotBlank() }
    val title=when(mode){"branch"->tr("فرع جديد","New branch");"plan"->tr("باقة جديدة","New plan");"renew"->tr("تجديد محلي وفاتورة","Local renewal & invoice");"expense"->tr("مصروف جديد","New expense");else->tr("تأكيد التصحيح","Confirm correction")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(mode in listOf("branch","plan","expense")) OutlinedTextField(name,{name=it.take(if(mode=="expense")80 else 120)},label={Text(when(mode){"branch"->tr("اسم الفرع","Branch name");"plan"->tr("اسم الباقة","Plan name");else->tr("فئة المصروف","Expense category")})},enabled=!busy,singleLine=true)
        if(mode=="plan") {
            OutlinedTextField(days,{days=it.take(4)},label={Text(tr("مدة الباقة بالأيام","Duration in days"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=!busy,singleLine=true)
            listOf("HOTSPOT","PPPOE","OTHER").forEach { v -> FilterChip(selected=service==v,onClick={service=v},enabled=!busy,label={Text(v)}) }
        }
        if(mode=="renew") { Text(plan+" • "+BusinessMoney.format(price,planCurrency));Text(tr("يبدأ من اليوم أو نهاية آخر اشتراك، أيهما أحدث. اترك التحصيل فارغًا إذا لم تستلم مبلغًا. التجديد محلي ولا يغير الإنترنت.","Starts today or after the latest period, whichever is later. Leave receipt blank if nothing was collected. Local renewal does not change Internet service.")) }
        if(mode in listOf("plan","expense","renew")) OutlinedTextField(amount,{amount=it.take(20)},label={Text((if(mode=="renew")tr("تحصيل الآن (اختياري) ","Collect now (optional) ") else tr("المبلغ ","Amount "))+currency)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy,singleLine=true,isError=amount.isNotBlank() && minor==null)
        if(mode in listOf("plan","expense")) BusinessMoney.currencies.chunked(3).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){row.forEach { v->FilterChip(selected=currency==v,onClick={currency=v},enabled=!busy,label={Text(v)}) }} }
        if(mode in listOf("cancel","expenseReverse","expense","saleCancel")) { if(mode=="cancel") Text(tr("سيُعكس المستحق والتحصيل المسجل عند إصدار الفاتورة. الإلغاء المحاسبي لا يعيد نقودًا للعميل فعليًا.","Reverses the charge and receipt recorded at invoice issue. Accounting cancellation does not physically refund cash."));OutlinedTextField(note,{note=it.take(500)},label={Text(tr("البيان / السبب","Description / reason"))},enabled=!busy,minLines=2,maxLines=4) }
        if(mode=="renew") { PaymentMethodPicker(arabic,method,{method=it},!busy);OutlinedTextField(reference,{reference=it.take(120)},label={Text(tr("مرجع الدفع (اختياري)","Payment reference (optional)"))},enabled=!busy) }
        if(error!=null) Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={onSave(id,name,service,currency,minor ?: 0,days.toIntOrNull() ?: 0,note,method,reference)},enabled=valid && !busy){Text(if(busy)tr("جاري الحفظ…","Saving…") else tr("حفظ","Save"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(tr("إلغاء","Cancel"))}})
}

// Isolate ISO dates from surrounding RTL labels; keep chronological meaning unambiguous.
private fun businessLtr(value: String)="\u2066$value\u2069"
