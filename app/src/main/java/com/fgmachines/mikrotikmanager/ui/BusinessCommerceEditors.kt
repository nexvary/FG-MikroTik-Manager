package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.business.*
import java.util.UUID

@Composable fun PaymentMethodPicker(arabic: Boolean,method: PaymentMethod,onChange: (PaymentMethod)->Unit,enabled: Boolean=true) {
    var expanded by remember { mutableStateOf(false) }
    fun label(m: PaymentMethod)=when(m){PaymentMethod.CASH->if(arabic)"كاش" else "Cash";PaymentMethod.VODAFONE_CASH->"Vodafone Cash";PaymentMethod.INSTAPAY->"InstaPay";PaymentMethod.BANK->if(arabic)"بنك" else "Bank";PaymentMethod.OTHER->if(arabic)"أخرى" else "Other"}
    Box { OutlinedButton(onClick={expanded=true},enabled=enabled){Text((if(arabic)"طريقة الدفع: " else "Payment method: ")+label(method))}
        DropdownMenu(expanded,onDismissRequest={expanded=false}) { PaymentMethod.entries.forEach { m->DropdownMenuItem(text={Text(label(m))},onClick={onChange(m);expanded=false}) } }
    }
}

@Composable fun SaleEditor(arabic: Boolean,currency: String,busy: Boolean,error: String?,onDismiss: ()->Unit,onSave:(String,List<SaleLine>,Long,PaymentMethod,String)->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    val id=rememberSaveable { UUID.randomUUID().toString() }
    var encoded by rememberSaveable { mutableStateOf("[]") };val lines=remember(encoded){BusinessSales.decode(encoded)}
    var name by rememberSaveable { mutableStateOf("") };var qty by rememberSaveable { mutableStateOf("1") };var price by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("") };var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) };var reference by rememberSaveable { mutableStateOf("") }
    val unit=runCatching { BusinessMoney.parse(price) }.getOrNull();val count=qty.toIntOrNull()
    val total=runCatching { lines.fold(0L){s,l->Math.addExact(s,Math.multiplyExact(l.unitMinor,l.quantity.toLong()))} }.getOrNull()
    val collected=if(paid.isBlank())0L else runCatching { BusinessMoney.parse(paid) }.getOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("بيع جديد","New sale"))},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(tr("بيع خدمات أو سلع لحساب العميل؛ لا يخصم من مخزون ولا يحسب ضرائب.","Sell services or goods to this account; no inventory deduction or tax calculation."))
        OutlinedTextField(name,{name=it.take(120)},label={Text(tr("الصنف / الخدمة","Item / service"))},enabled=!busy)
        OutlinedTextField(qty,{qty=it.take(5)},label={Text(tr("الكمية","Quantity"))},enabled=!busy)
        OutlinedTextField(price,{price=it.take(20)},label={Text(tr("سعر الوحدة ","Unit price ")+currency)},enabled=!busy)
        TextButton(onClick={encoded=BusinessSales.encode(lines+SaleLine(name.trim(),count!!,unit!!));name="";price="";qty="1"},enabled=!busy && name.isNotBlank() && count!=null && count in 1..10000 && unit!=null && lines.size<30){Text(tr("إضافة بند","Add line"))}
        lines.forEachIndexed { index,l->Text("${l.name} • ${l.quantity} × ${BusinessMoney.format(l.unitMinor,currency)}");TextButton(onClick={encoded=BusinessSales.encode(lines.filterIndexed { i,_->i!=index })},enabled=!busy){Text(tr("حذف البند ","Remove line ")+(index+1))} }
        Text(tr("الإجمالي: ","Total: ")+(total?.let { BusinessMoney.format(it,currency) } ?: tr("المبلغ زائد","Amount overflow")))
        OutlinedTextField(paid,{paid=it.take(20)},label={Text(tr("تحصيل الآن (اختياري)","Collect now (optional)"))},enabled=!busy)
        PaymentMethodPicker(arabic,method,{method=it},!busy)
        OutlinedTextField(reference,{reference=it.take(120)},label={Text(tr("مرجع الدفع (اختياري)","Payment reference (optional)"))},enabled=!busy)
        if(error!=null)Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={onSave(id,lines,collected!!,method,reference)},enabled=!busy && lines.isNotEmpty() && total!=null && total in 1..BusinessMoney.MAX_MINOR && collected!=null && collected in 0..total){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(tr("إلغاء","Cancel"))}})
}

@Composable fun NetworkInvoiceDialog(arabic: Boolean,invoice: String,state: BusinessToolsState,model: BusinessToolsViewModel,onDismiss:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    var profile by rememberSaveable(invoice) { mutableStateOf("") };var quota by rememberSaveable(invoice) { mutableStateOf("0") };var expanded by remember { mutableStateOf(false) }
    val job=state.networkJob?.takeIf { it.invoice==invoice }
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("تطبيق الاشتراك على الراوتر","Apply subscription to router"))},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(tr("استورد الحساب واربطه أولًا. التطبيق يتحقق من هوية الراوتر والساعة. الإجراء يفصل الجلسة الحالية لتطبيق البروفايل.","Import and bind the account first. Router identity and clock are checked. Current sessions are disconnected to apply the profile."))
        if(job==null) {
            OutlinedButton(onClick=model::scanRouter,enabled=!state.busy){Text(tr("قراءة البروفايلات","Read profiles"))}
            Box { OutlinedButton(onClick={expanded=true},enabled=!state.busy && state.catalog!=null){Text(profile.ifBlank { tr("اختيار بروفايل السرعة","Choose speed profile") })}
                DropdownMenu(expanded,onDismissRequest={expanded=false}){(state.catalog?.let { it.hotspotProfiles+it.pppProfiles } ?: emptyList()).distinct().forEach { p->DropdownMenuItem(text={Text(p)},onClick={profile=p;expanded=false}) }} }
            OutlinedTextField(quota,{quota=it.take(10)},label={Text(tr("حصة HotSpot بالميجابايت؛ 0 غير محدود","HotSpot allowance MiB; 0 unlimited"))},enabled=!state.busy)
            Text(tr("HotSpot: يُستبدل حد وقت الاستخدام بتاريخ الانتهاء، ويصبح حد البيانات الاستهلاك الحالي + الحصة. PPPoE: البروفايل والانتهاء فقط؛ اترك الحصة صفرًا.","HotSpot replaces uptime limits with calendar expiry and sets total bytes to current usage plus allowance. PPPoE uses profile and expiry only; leave allowance zero."))
            Button(onClick={model.prepareNetwork(invoice,profile,quota.toLong())},enabled=!state.busy && profile.isNotBlank() && quota.toLongOrNull()?.let { it>=0 }==true){Text(tr("تجهيز ومعاينة","Prepare preview"))}
        } else {
            Text(job.target.account+" • "+job.target.service);Text(tr("البروفايل: ","Profile: ")+job.target.profile)
            Text(tr("نهاية الاشتراك: ","Subscription end: ")+java.time.LocalDate.ofEpochDay(job.target.endDay))
            Text(tr("حد البيانات الكلي بالبايت: ","Absolute byte limit: ")+job.target.byteLimit)
            Text(tr("الحالة: ","State: ")+job.state)
            Text(tr("الإيقاف التلقائي دوري كل دقيقة ويحتاج ساعة راوتر صحيحة. عند فشل الاتصال لا يُكرر التحصيل؛ راجع الحالة وأعد التوفيق لنفس العملية.","Expiry checks run every minute and require a correct router clock. Connection failure never repeats collection; reconcile this same job."))
            Button(onClick={model.applyNetwork(false)},enabled=!state.busy){Text(tr("تأكيد التطبيق / التوفيق","Confirm apply / reconcile"))}
            OutlinedButton(onClick={model.applyNetwork(true)},enabled=!state.busy){Text(tr("إيقاف الخدمة قبل إلغاء الفاتورة","Suspend service before invoice cancellation"))}
        }
        state.error?.let {Text(tr("تعذرت العملية: ","Operation failed: ")+it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick=onDismiss,enabled=!state.busy){Text(tr("إغلاق","Close"))}})
}
