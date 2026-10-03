package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.business.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TeamModel(app:Application):AndroidViewModel(app) {
    private val store=BusinessStore(BusinessDatabase(app));private val team=BusinessTeam(store)
    var members by mutableStateOf(emptyList<TeamMember>());private set
    var entries by mutableStateOf(emptyList<ResellerEntry>());private set
    var sales by mutableStateOf(emptyList<BusinessSale>());private set
    var selected by mutableStateOf<TeamMember?>(null);private set
    var balance by mutableLongStateOf(0);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var revision by mutableIntStateOf(0);private set
    private fun run(write:Boolean=false,work:(BusinessScope)->Unit={}) {
        if(busy)return;busy=true;error=null
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val s=store.defaultScope();work(s);val ms=team.members(s);val chosen=selected?.id?.let { id->ms.firstOrNull { it.id==id } }
                    Triple(ms,chosen,if(chosen==null) emptyList() else team.entries(s,chosen.id)) to
                        ((if(chosen==null) 0L else team.balance(s,chosen.id)) to BusinessSales(store).page(s).items)
                }
                members=result.first.first;selected=result.first.second;entries=result.first.third
                balance=result.second.first;sales=result.second.second
                if(write)revision++
            } catch(e:Exception) { error=e.message?.takeIf { it in listOf("INSUFFICIENT_WALLET","INVALID_COMMISSION","INVALID_AMOUNT","IDEMPOTENCY_CONFLICT","INACTIVE_RESELLER","TEAM_LIMIT") } ?: "OPERATION_FAILED" }
            finally { busy=false }
        }
    }
    fun refresh()=run()
    fun select(m:TeamMember?) { if(busy)return;selected=m;run() }
    fun add(id:String,n:String,p:String,r:String,c:String,b:Int)=run(true){team.add(it,id,n,p,r,c,b)}
    fun active(m:TeamMember)=run(true){team.activate(it,m.id,!m.active)}
    fun post(id:String,kind:String,amount:Long,note:String,sale:String?=null,reversal:String?=null) {
        val m=selected ?: return
        run(true){team.post(it,m.id,id,kind,amount,note,sale,reversal)}
    }
    override fun onCleared(){store.close()}
}

@Composable
fun TeamScreen(arabic:Boolean,onBack:()->Unit,model:TeamModel=viewModel()) {
    fun tr(a:String,e:String)=if(arabic)a else e
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var reference by rememberSaveable { mutableStateOf("") }
    var handled by rememberSaveable { mutableIntStateOf(model.revision) }
    LaunchedEffect(Unit){model.refresh()}
    LaunchedEffect(model.revision){if(handled!=model.revision){handled=model.revision;editor=null}}
    fun back(){if(!model.busy){if(model.selected!=null)model.select(null) else onBack()}}
    BackHandler { if(editor==null)back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) { TextButton(onClick={back()},enabled=!model.busy){Text(tr("رجوع","Back"))};Text(tr("الموظفون والموزعون","Staff & resellers"),Modifier.padding(12.dp)) }
        if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        model.error?.let { Text(tr("لم تُحفظ العملية: ","Operation not saved: ")+it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp)) }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text(tr("سجل محلي للفرع الحالي. مسميات الأدوار للتنظيم وليست صلاحيات دخول. المحفظة سجل مستقل عن حسابات العملاء.","Local directory for the current branch. Roles describe duties, not login permissions. Wallets are separate from customer accounts."),color=FgSilver) }
            val selected=model.selected
            if(selected==null) {
                item { Button(onClick={editor="member"},enabled=!model.busy){Text(tr("إضافة موظف أو موزع","Add staff or reseller"))} }
                items(model.members,key={it.id}) { m->Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(m.name+" • "+teamRole(m.role,arabic));Text(m.phone)
                    Text(if(m.active)tr("نشط","Active") else tr("موقوف","Disabled"))
                    Row { if(m.role=="RESELLER")TextButton(onClick={model.select(m)},enabled=!model.busy){Text(tr("المحفظة والعمولات","Wallet & commissions"))}
                        TextButton(onClick={reference=m.id;editor="active"},enabled=!model.busy){Text(if(m.active)tr("إيقاف","Disable") else tr("تفعيل","Enable"))} }
                } } }
            } else {
                item { Text(selected.name,color=FgMint);Text(tr("الرصيد: ","Balance: ")+BusinessMoney.format(model.balance,selected.currency));Text(tr("نسبة العمولة: ","Commission: ")+"${selected.commissionBps/100.0}%")
                    Row { listOf("DEPOSIT","WITHDRAWAL","COMMISSION").forEach { k->TextButton(onClick={editor=k},enabled=!model.busy && selected.active){Text(teamKind(k,arabic))} } }
                }
                item { Text(tr("آخر 100 حركة. العمولة تُحسب على المحصل وقت البيع، وتُسجل مرة واحدة لكل بيع.","Latest 100 entries. Commission uses the amount collected at sale creation, once per sale."),color=FgSilver) }
                items(model.entries,key={it.id}) { e->Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(teamKind(e.kind,arabic)+" • "+BusinessMoney.format(e.amount,selected.currency));Text(e.note)
                    if(e.reversed)Text(tr("معكوسة","Reversed")) else if(e.kind!="REVERSAL")TextButton(onClick={reference=e.id;editor="REVERSAL"},enabled=!model.busy){Text(tr("عكس الحركة","Reverse entry"))}
                } } }
            }
        }
    }
    if(editor=="active") AlertDialog(onDismissRequest={if(!model.busy)editor=null},title={Text(tr("تغيير حالة العضو؟","Change member status?"))},text={Text(tr("السجلات والأرصدة ستظل محفوظة.","History and balances are retained."))},confirmButton={TextButton(onClick={model.members.firstOrNull{it.id==reference}?.let(model::active)},enabled=!model.busy){Text(tr("تأكيد","Confirm"))}},dismissButton={TextButton(onClick={editor=null},enabled=!model.busy){Text(tr("إلغاء","Cancel"))}})
    else editor?.let { kind->key(kind,reference) {
        var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        var name by rememberSaveable { mutableStateOf("") };var phone by rememberSaveable { mutableStateOf("") }
        var role by rememberSaveable { mutableStateOf("RESELLER") };var currency by rememberSaveable { mutableStateOf("EGP") }
        var amount by rememberSaveable { mutableStateOf("") };var percent by rememberSaveable { mutableStateOf("0") }
        var sale by rememberSaveable { mutableStateOf("") };var invalid by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest={if(!model.busy)editor=null},title={Text(if(kind=="member")tr("عضو جديد","New member") else teamKind(kind,arabic))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it.take(120)},label={Text(if(kind=="member")tr("الاسم","Name") else tr("البيان / السبب","Note / reason"))})
            if(kind=="member") {
                OutlinedTextField(phone,{phone=it.take(40)},label={Text(tr("الهاتف","Phone"))})
                TeamChoice(role,listOf("ADMIN","MANAGER","TECHNICIAN","CASHIER","RESELLER","READ_ONLY"),{teamRole(it,arabic)}){role=it}
                TeamChoice(currency,BusinessMoney.currencies.toList(),{it}){currency=it}
                if(role=="RESELLER")OutlinedTextField(percent,{percent=it.take(6)},label={Text(tr("العمولة % (0–100)","Commission % (0–100)"))})
            } else if(kind=="DEPOSIT" || kind=="WITHDRAWAL") OutlinedTextField(amount,{amount=it.take(20)},label={Text(tr("المبلغ","Amount"))})
            else if(kind=="COMMISSION") {
                Text(tr("اختر بيعًا من آخر 30 عملية بالعملة نفسها.","Select a sale from the latest 30 in the same currency."))
                model.sales.filter { !it.voided && it.paid>0 && it.currency==model.selected?.currency }.forEach { v->FilterChip(sale==v.id,{sale=v.id},label={Text(v.customer+" • "+BusinessMoney.format(v.paid,v.currency))}) }
            }
            if(invalid)Text(tr("راجع البيانات المطلوبة.","Check required fields."),color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(enabled=!model.busy,onClick={
            try {
                require(name.isNotBlank())
                if(kind=="member") { val bp=if(role=="RESELLER")java.math.BigDecimal(percent).movePointRight(2).intValueExact() else 0;require(bp in 0..10000);model.add(id,name,phone,role,currency,bp) }
                else { val value=if(kind in listOf("DEPOSIT","WITHDRAWAL")) BusinessMoney.parse(amount) else 0L;require(kind!="COMMISSION" || sale.isNotBlank());model.post(id,kind,value,name,sale.takeIf{kind=="COMMISSION"},reference.takeIf{kind=="REVERSAL"}) }
            }catch(_:Exception){invalid=true}
        }){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick={editor=null},enabled=!model.busy){Text(tr("إلغاء","Cancel"))}})
    } }
}
@Composable private fun TeamChoice(value:String,values:List<String>,label:(String)->String,onChange:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true}){Text(label(value))};DropdownMenu(open,{open=false}){values.forEach { v->DropdownMenuItem(text={Text(label(v))},onClick={onChange(v);open=false}) }} }
}
private fun teamRole(k:String,a:Boolean)=if(!a)k else when(k){"ADMIN"->"مدير";"MANAGER"->"مدير فرع";"TECHNICIAN"->"فني";"CASHIER"->"كاشير";"RESELLER"->"موزع";else->"قراءة فقط"}
private fun teamKind(k:String,a:Boolean)=if(!a)k else when(k){"DEPOSIT"->"إيداع";"WITHDRAWAL"->"سحب";"COMMISSION"->"عمولة";else->"عكس حركة"}
