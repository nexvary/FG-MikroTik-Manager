package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.business.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessScreen(arabic: Boolean,onBack: ()->Unit,onLanguageToggle: ()->Unit,model: BusinessViewModel = viewModel(),router: BusinessRouter?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    if(toolsOpen) {
        BusinessToolsScreen(arabic,state.selected,{ toolsOpen=false;model.resetBranch() },onLanguageToggle,router=router)
        return
    }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var reverseId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var handledSave by rememberSaveable { mutableStateOf(state.saved) }
    fun tr(ar: String,en: String)=if(arabic) ar else en
    fun back() { if(!state.busy) { if(state.selected != null) model.back() else onBack() } }
    BackHandler { back() }
    LaunchedEffect(state.saved) { if(state.saved!=handledSave) { editor=null; reverseId=null; handledSave=state.saved } }
    val error=state.error?.let { code -> when(code) {
        "INVALID_AMOUNT" -> tr("اكتب مبلغًا صحيحًا أكبر من صفر، بحد أقصى منزلتين عشريتين.","Enter a positive amount with at most two decimal places.")
        "INVALID_TEXT" -> tr("راجع الحقول المطلوبة وطول النص.","Check required fields and text length.")
        "IDEMPOTENCY_CONFLICT" -> tr("رقم العملية مستخدم ببيانات مختلفة. راجع السجل قبل المحاولة من جديد.","This operation ID has different data. Review the ledger before retrying.")
        "CANCEL_INVOICE_FIRST" -> tr("هذه العملية مرتبطة بفاتورة. استخدم إلغاء الفاتورة من إدارة الأعمال.","This entry belongs to an invoice. Cancel it from Business tools.")
        "ALREADY_REVERSED" -> tr("العملية اتعكست قبل كده. ارجع للسجل وراجعه.","This entry has already been reversed. Review the ledger.")
        else -> tr("تعذر حفظ أو تحميل البيانات. راجع السجل ثم أعد المحاولة؛ لا تحذف بيانات التطبيق.","Could not save or load data. Review the ledger, then retry; do not clear app data.")
    } }
    Scaffold(containerColor=FgBlack,topBar={
        TopAppBar(title={ Text(tr("المشتركون والحسابات","Subscribers & accounts")) },
            navigationIcon={ IconButton(onClick={ back() },enabled=!state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("رجوع","Back")) } },
            actions={ IconButton(onClick=onLanguageToggle) { Icon(Icons.Outlined.Language,tr("اللغة","Language")) } },
            colors=TopAppBarDefaults.topAppBarColors(containerColor=FgPanel))
    }) { padding ->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text(tr("سجل أعمال محلي • لا يفعّل أو يوقف الإنترنت تلقائيًا.","Local business ledger • does not activate or suspend Internet automatically."),color=FgSilver,style=MaterialTheme.typography.bodySmall) }
            item { Text(tr("الفرع الحالي: ","Current branch: ")+state.branchName,color=FgMint) }
            item { OutlinedButton(onClick={toolsOpen=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(tr("إدارة الأعمال والباقات","Business tools & plans")) } }
            if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(error!=null) item { Text(error,color=MaterialTheme.colorScheme.error); TextButton(onClick=model::refresh,enabled=!state.busy) { Text(tr("إعادة التحميل","Reload")) } }
            val sub=state.selected
            if(sub==null) {
                item { OutlinedTextField(value=search,onValueChange={ search=it.take(120) },label={ Text(tr("الاسم أو الهاتف أو حساب الشبكة","Name, phone or network account")) },modifier=Modifier.fillMaxWidth(),singleLine=true) }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={ model.search(search) },enabled=!state.busy,modifier=Modifier.weight(1f)) { Text(tr("بحث","Search")) }
                    Button(onClick={ editor="subscriber" },enabled=!state.busy,modifier=Modifier.weight(1f)) { Icon(Icons.Outlined.Add,null); Text(tr("مشترك جديد","Add subscriber")) }
                } }
                if(state.subscribers.isEmpty() && !state.busy && state.error==null) item { Text(tr("لا يوجد مشتركون هنا. أضف مشتركًا أو غيّر البحث.","No subscribers here. Add one or change your search."),color=FgSilver) }
                items(state.subscribers,key={it.id}) { record ->
                    Card(modifier=Modifier.fillMaxWidth().clickable(enabled=!state.busy) { model.select(record.id) },colors=CardDefaults.cardColors(containerColor=FgPanel)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(record.name,color=FgMint,style=MaterialTheme.typography.titleMedium)
                            Text(listOf(record.phone,record.account).filter { it.isNotBlank() }.joinToString(" • "),color=FgSilver)
                            Text(record.service+" • "+record.currency,color=FgSilver)
                        }
                    }
                }
                item { BusinessPager(state.subscriberPage,state.moreSubscribers,state.busy,arabic,model::subscriberPage) }
            } else {
                item { Card(colors=CardDefaults.cardColors(containerColor=FgPanel),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        state.subscriptionEnd?.let { end -> Text(tr("نهاية الاشتراك المحلي (غير شاملة): ","Local subscription end (exclusive): ")+java.time.LocalDate.ofEpochDay(end),color=FgMint) }
                        Text(sub.name,color=FgMint,style=MaterialTheme.typography.titleLarge)
                        Text(listOf(sub.phone,sub.account,sub.service).filter { it.isNotBlank() }.joinToString(" • "),color=FgSilver)
                        Text(tr("الرصيد المستحق: ","Balance due: ")+(state.balance?.let { BusinessMoney.format(it,sub.currency) } ?: tr("غير متاح الآن","Unavailable")),color=FgWhite,style=MaterialTheme.typography.titleLarge)
                        Text(tr("الموجب: مطلوب من المشترك. السالب: رصيد لصالحه.","Positive: subscriber owes. Negative: subscriber credit."),color=FgSilver,style=MaterialTheme.typography.bodySmall)
                    }
                } }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ editor="payment" },enabled=!state.busy,modifier=Modifier.weight(1f)) { Text(tr("تسجيل دفعة","Record payment")) }
                    OutlinedButton(onClick={ editor="charge" },enabled=!state.busy,modifier=Modifier.weight(1f)) { Text(tr("إضافة مستحق","Add charge")) }
                } }
                if(state.entries.isEmpty() && !state.busy && state.error==null) item { Text(tr("لا توجد عمليات مسجلة لهذا المشترك.","No entries recorded for this subscriber."),color=FgSilver) }
                items(state.entries,key={it.id}) { entry ->
                    Card(colors=CardDefaults.cardColors(containerColor=FgPanel),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(when(entry.kind) { LedgerKind.CHARGE -> tr("مستحق","Charge"); LedgerKind.PAYMENT -> tr("دفعة مستلمة","Payment received"); LedgerKind.REVERSAL -> tr("قيد عكسي","Reversal") }+" • "+BusinessMoney.format(entry.amountMinor,entry.currency),color=if(entry.amountMinor>0) FgAmber else FgMint)
                            Text(entry.note,color=FgWhite)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.createdAt)),color=FgSilver)
                            Text(tr("مرجع: ","Reference: ")+entry.id.take(8),color=FgSilver,style=MaterialTheme.typography.bodySmall)
                            if(entry.reversed) Text(tr("تم عكس العملية","Entry reversed"),color=FgAmber)
                            else if(entry.kind!=LedgerKind.REVERSAL) TextButton(onClick={ reverseId=entry.id; editor="reverse" },enabled=!state.busy) { Text(tr("تصحيح بقيد عكسي","Correct with reversal")) }
                        }
                    }
                }
                item { BusinessPager(state.ledgerPage,state.moreEntries,state.busy,arabic,model::ledgerPage) }
            }
        }
    }
    editor?.let { mode -> key(mode,reverseId) {
        BusinessEditor(mode,arabic,state.busy,error,state.selected?.currency ?: "EGP",onDismiss={ if(!state.busy) editor=null },onSave={ id,name,phone,service,account,currency,amount,note,method,reference ->
            when(mode) {
                "subscriber" -> model.addSubscriber(id,name,phone,service,account,currency)
                "reverse" -> model.reverse(reverseId!!,id,note)
                else -> model.post(id,if(mode=="payment") LedgerKind.PAYMENT else LedgerKind.CHARGE,amount,note,method,reference)
            }
        })
    } }
}

@Composable
private fun BusinessPager(page: Int,more: Boolean,busy: Boolean,arabic: Boolean,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={change(false)},enabled=page>1 && !busy) { Text(if(arabic) "السابق" else "Previous") }
        Text(page.toString(),modifier=Modifier.padding(12.dp),color=FgSilver)
        TextButton(onClick={change(true)},enabled=more && !busy) { Text(if(arabic) "التالي" else "Next") }
    }
}

@Composable
private fun BusinessEditor(mode: String,arabic: Boolean,busy: Boolean,error: String?,initialCurrency: String,onDismiss: ()->Unit,
    onSave: (String,String,String,String,String,String,Long,String,PaymentMethod,String)->Unit) {
    fun tr(ar: String,en: String)=if(arabic) ar else en
    val operationId=rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }; var service by rememberSaveable { mutableStateOf("HOTSPOT") }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }; var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) };var reference by rememberSaveable { mutableStateOf("") }
    val minor=runCatching { BusinessMoney.parse(amount) }.getOrNull()
    val valid=if(mode=="subscriber") name.isNotBlank() else note.isNotBlank() && (mode=="reverse" || minor!=null)
    AlertDialog(onDismissRequest=onDismiss,title={ Text(when(mode) { "subscriber"->tr("مشترك جديد","New subscriber"); "payment"->tr("تسجيل دفعة","Record payment"); "charge"->tr("إضافة مستحق","Add charge"); else->tr("تأكيد القيد العكسي","Confirm reversal") }) },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(mode=="subscriber") {
                OutlinedTextField(name,{name=it.take(120)},label={Text(tr("اسم المشترك","Subscriber name"))},enabled=!busy,singleLine=true)
                OutlinedTextField(phone,{phone=it.take(40)},label={Text(tr("الهاتف (اختياري)","Phone (optional)"))},enabled=!busy,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
                OutlinedTextField(account,{account=it.take(120)},label={Text(tr("حساب الشبكة (اختياري)","Network account (optional)"))},enabled=!busy,singleLine=true)
                Text(tr("نوع الخدمة","Service"))
                listOf("HOTSPOT","PPPOE","OTHER").forEach { value -> FilterChip(selected=service==value,onClick={service=value},enabled=!busy,label={Text(if(value=="OTHER") tr("أخرى","Other") else value)}) }
                Text(tr("عملة الحساب — تثبت بعد الحفظ","Account currency — fixed after saving"))
                BusinessMoney.currencies.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { row.forEach { value -> FilterChip(selected=currency==value,onClick={currency=value},enabled=!busy,label={Text(value)}) } } }
            } else {
                if(mode=="reverse") Text(tr("سيتم إضافة عملية معاكسة بكامل المبلغ مع الاحتفاظ بالأصل. اكتب سبب التصحيح.","Adds a full opposite entry and preserves the original. Enter a reason."))
                else {
                    OutlinedTextField(amount,{amount=it.take(20)},label={Text(tr("المبلغ ","Amount ")+currency)},enabled=!busy,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=amount.isNotBlank() && minor==null)
                    if(amount.isNotBlank() && minor==null) Text(tr("مبلغ موجب، بحد أقصى منزلتين عشريتين.","Positive amount, at most two decimal places."),color=MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(note,{note=it.take(500)},label={Text(tr("البيان / السبب","Description / reason"))},enabled=!busy,minLines=2,maxLines=4)
            }
            if(mode=="payment") { PaymentMethodPicker(arabic,method,{method=it},!busy);OutlinedTextField(reference,{reference=it.take(120)},label={Text(tr("مرجع الدفع (اختياري)","Payment reference (optional)"))},enabled=!busy) }
            if(error!=null) Text(error,color=MaterialTheme.colorScheme.error)
        } },confirmButton={ TextButton(onClick={ onSave(operationId,name,phone,service,account,currency,minor ?: 0L,note,method,reference) },enabled=valid && !busy) { Text(if(busy) tr("جاري الحفظ…","Saving…") else tr("حفظ","Save")) } },
        dismissButton={ TextButton(onClick=onDismiss,enabled=!busy) { Text(tr("إلغاء","Cancel")) } })
}
