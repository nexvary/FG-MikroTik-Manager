package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import com.fgmachines.mikrotikmanager.advanced.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

@Composable
fun DnsProtectionScreen(arabic:Boolean,manager:AdvancedRouterManager?,onBack:()->Unit,modifier:Modifier=Modifier) {
    fun tr(a:String,e:String)=if(arabic)a else e
    fun modeLabel(mode:DnsProtectionMode)=if(mode==DnsProtectionMode.FAMILY)tr("الإباحية + الإعلانات + التتبع","Adult content + ads + trackers") else tr("الإعلانات والتتبع فقط","Ads and trackers only")
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val protection=remember(manager){manager?.dnsProtection()}
    val vault=remember(manager){RouterChangeVault(context,manager?.routerKey ?: "disconnected")}
    var receipt by remember(manager){mutableStateOf<DnsProtectionReceipt?>(null)}
    var journalReady by remember(manager){mutableStateOf(false)}
    var checkedReceipt by remember(manager){mutableStateOf(false)}
    var inspection by remember(manager){mutableStateOf<DnsProtectionInspection?>(null)}
    var selected by remember(manager){mutableStateOf(emptySet<String>())}
    var mode by remember{mutableStateOf(DnsProtectionMode.FAMILY)}
    var preview by remember{mutableStateOf<DnsProtectionPlan?>(null)}
    var confirmRestore by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    fun error(code:String)=when(code){
        "SELECT_CLIENT_NETWORK"->tr("اختر شبكة عملاء خاصة من القائمة.","Select a private client network from the list.")
        "DHCP_OPTIONS_REVIEW"->tr("الشبكة تستخدم DHCP Options مخصصة؛ راجعها يدويًا قبل الحظر.","Custom DHCP options require manual review before filtering.")
        "DNS_CONFIGURATION_CHANGED","DNS_PLAN_CHANGED"->tr("تغيرت إعدادات الراوتر؛ حدّث الفحص وراجع خطة جديدة.","Router settings changed. Refresh and review a new plan.")
        "DNS_RESTORE_CONFLICT","DNS_TARGET_CHANGED"->tr("تغيرت الإعدادات أو هوية الراوتر. لن نستبدل التعديلات الجديدة؛ راجع النسخة الاحتياطية من قسم الاستعادة.","Settings or router identity changed. New edits will not be overwritten; review the backup in Recovery.")
        "DNS_APPLY_FAILED_CHECK_RECOVERY"->tr("لم يكتمل التطبيق. راجع حالة الاستعادة؛ قد تكون بعض الأوامر نُفذت قبل انقطاع الاتصال.","Apply did not complete. Check recovery: some commands may have applied before connection loss.")
        else->tr("تعذرت العملية أو التحقق. أعد الاتصال وحدّث الفحص؛ لا يوجد نجاح مؤكد.","Operation or verification failed. Reconnect and refresh; success is not confirmed.")
    }
    fun run(work:suspend()->Unit) {
        if(busy)return
        busy=true;message="";checkedReceipt=false
        scope.launch{try{withTimeout(180000){work()}}catch(t:TimeoutCancellationException){message=error("TIMEOUT");runCatching{receipt=vault.dnsProtection()}}catch(c:CancellationException){throw c}catch(e:Exception){message=error(e.message.orEmpty());runCatching{receipt=vault.dnsProtection()}}finally{busy=false}}
    }
    fun refresh(){run{receipt=vault.dnsProtection();journalReady=true;inspection=protection!!.inspect();checkedReceipt=receipt?.let{protection.verified(it,inspection!!)} ?: false;selected=selected.intersect(inspection!!.networks.map{it.getValue(".id")}.toSet());message=tr("تمت قراءة الإعدادات الحالية.","Current settings read.")}}
    LaunchedEffect(manager){if(manager!=null)refresh()}
    BackHandler{if(!busy)onBack()}
    Surface(color=FgBlack,contentColor=FgWhite,modifier=modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            TextButton(onClick=onBack,enabled=!busy){Text(tr("رجوع","Back"))}
            Text(tr("حماية الأسرة والخصوصية","Family & privacy protection"),style=MaterialTheme.typography.titleLarge)
            Text(tr("حظر عبر DNS على MikroTik لشبكات العملاء المحددة. المزود: AdGuard DNS العام؛ لا يحتاج حسابًا أو اشتراكًا من التطبيق.","DNS filtering on MikroTik for selected client networks. Provider: public AdGuard DNS; no app account or subscription required."))
            if(manager==null)Text(tr("اتصل بالراوتر أولًا لإعداد الحماية.","Connect to a router to configure protection."),color=FgAmber)
            Text(tr("الحدود: VPN وPrivate DNS وDoH وDoT وDNS اليدوي قد يتجاوزون الحظر. توزيع DNS هنا عبر DHCP IPv4؛ DNS العملاء عبر IPv6 وإعدادات PPP المخصصة تحتاج ضبطًا منفصلًا. لا يحذف كل إعلانات YouTube أو الإعلانات من نفس نطاق المحتوى.","Limits: VPN, Private DNS, DoH, DoT and manual DNS can bypass filtering. DNS assignment here uses DHCP IPv4; client IPv6 DNS and custom PPP settings need separate configuration. It cannot remove every YouTube ad or ads served by the content domain."),style=MaterialTheme.typography.bodySmall,color=FgAmber)
            Text(tr("استعلامات DNS تُرسل للمزود الخارجي عبر DNS التقليدي. يتغير DNS الراوتر أيضًا، فيؤثر على مستخدمي محلله وHotSpot. يُعطّل DoH الحالي وDNS المستلم من WAN مع حفظ القيم القديمة. لا تُفتح خدمة DNS للإنترنت ولا تتغير قواعد الحماية. قد تتأثر أسماء الشبكة الداخلية؛ جدّد اتصال العملاء بعد التطبيق.","DNS queries go to the external provider using conventional DNS. Router DNS also changes, affecting its resolver users and HotSpot. Existing DoH and WAN peer DNS are disabled with previous values saved. No public DNS service or firewall changes. Internal names may be affected; reconnect clients after applying."),style=MaterialTheme.typography.bodySmall)
            DnsProtectionMode.entries.forEach{item->Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){RadioButton(mode==item,{mode=item;preview=null},enabled=!busy && receipt==null);Text(modeLabel(item),Modifier.weight(1f))}}
            Text(mode.servers,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall.copy(textDirection=TextDirection.Ltr,textAlign=if(arabic)TextAlign.Right else TextAlign.Left))
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message)
            receipt?.let{r->
                Text(if(r.state=="ACTIVE" && checkedReceipt)tr("تم التحقق من إعدادات الحظر على الراوتر. فعالية الحظر على أجهزة العملاء تحتاج اختبارًا فعليًا.","Filtering settings verified on the router. Effectiveness on client devices needs an actual test.") else tr("هناك عملية غير مكتملة أو تحتاج مراجعة. استعد إعداداتها قبل تطبيق وضع جديد.","An incomplete operation needs review. Recover it before applying a new mode."),color=FgAmber)
                Text(tr("نسخة الراوتر: ","Router backup: ")+r.backup)
                OutlinedButton(onClick={confirmRestore=true},enabled=manager!=null && journalReady && !busy){Text(tr("إيقاف الحظر واستعادة الإعدادات السابقة","Stop filtering & restore previous settings"))}
            }
            OutlinedButton(onClick={preview=null;refresh()},enabled=manager!=null && !busy){Text(tr("تحديث الفحص","Refresh inspection"))}
            Text(tr("شبكات العملاء IPv4","IPv4 client networks"),style=MaterialTheme.typography.titleMedium)
            inspection?.networks?.forEach{row->val id=row.getValue(".id");Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(id in selected,{on->selected=if(on)selected+id else selected-id;preview=null},enabled=!busy && receipt==null);Text(row["address"].orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr,textAlign=if(arabic)TextAlign.Right else TextAlign.Left))}}
            if(inspection!=null && inspection!!.networks.isEmpty())Text(tr("لا توجد شبكة DHCP خاصة مؤهلة. راجع شبكة العملاء؛ لا ننشئ شبكة أو نغير WAN تلقائيًا.","No eligible private DHCP network. Review client configuration; no network is created and WAN addresses are preserved."))
            Button(onClick={try{preview=protection!!.plan(inspection!!,selected,mode)}catch(e:Exception){message=error(e.message.orEmpty())}},enabled=manager!=null && journalReady && inspection!=null && selected.isNotEmpty() && receipt==null && !busy){Text(tr("معاينة التغييرات","Preview changes"))}
        }
    }
    preview?.let{plan->AlertDialog(onDismissRequest={preview=null},title={Text(tr("مراجعة الحظر","Review filtering"))},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text(modeLabel(plan.mode));Text(tr("شبكات مختارة: ","Selected networks: ")+plan.networks.size)
        Text(tr("مجموع إعدادات التغيير: ","Settings to change: ")+plan.changes.size)
        Text(tr("سنحفظ نسخة راوتر مشفرة وسجل استعادة على الهاتف قبل التنفيذ. لن تُمس كلمات المرور أو قواعد Firewall. تعطيل الحظر يعيد القيم السابقة ولا يستبدل تعديلات لاحقة.","An encrypted router backup and phone recovery journal are saved before execution. Passwords and firewall rules are preserved. Restore reverts previous values without overwriting later edits."))
        plan.changes.forEach{Text(it.menu+" • "+it.after.keys.joinToString())}
    }},confirmButton={TextButton(onClick={preview=null;run{
        receipt=protection!!.apply(plan,backup={val secret=UUID.randomUUID().toString();val file=manager!!.backup(secret);vault.rememberBackup(file,secret);file},save={r->vault.rememberDnsProtection(r);receipt=r})
        vault.record("إعداد حظر DNS","Configure DNS filtering",true,receipt?.backup.orEmpty())
        inspection=protection!!.inspect();checkedReceipt=receipt?.let{protection.verified(it,inspection!!)} ?: false;message=if(receipt==null)tr("الإعدادات مطابقة بالفعل؛ لم نغيرها ولا نملك سجلًا لإلغائها.","Settings already match; no changes or owned restore journal.") else tr("تم تطبيق الإعدادات والتحقق منها. جدّد اتصال العملاء واختبر الحظر.","Settings applied and verified. Reconnect clients and test filtering.")
    }}){Text(tr("حفظ نسخة وتطبيق","Back up & apply"))}},dismissButton={TextButton(onClick={preview=null}){Text(tr("إلغاء","Cancel"))}})}
    if(confirmRestore)AlertDialog(onDismissRequest={confirmRestore=false},title={Text(tr("إيقاف الحظر؟","Stop filtering?"))},text={Text(tr("استعادة قيم DNS السابقة فقط. إذا تغيرت القيم منذ التطبيق ستتوقف العملية للمراجعة.","Restore previous DNS values only. Later changes stop recovery for review."))},confirmButton={TextButton(onClick={confirmRestore=false;run{protection!!.restore(receipt!!){r->vault.rememberDnsProtection(r);receipt=r};vault.record("استعادة DNS السابق","Restore previous DNS",true);inspection=protection!!.inspect();message=tr("تمت استعادة الإعدادات السابقة. جدّد اتصال العملاء.","Previous settings restored. Reconnect clients.")}}){Text(tr("استعادة","Restore"))}},dismissButton={TextButton(onClick={confirmRestore=false}){Text(tr("إلغاء","Cancel"))}})
}
