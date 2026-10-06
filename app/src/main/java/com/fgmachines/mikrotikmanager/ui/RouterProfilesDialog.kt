package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fgmachines.mikrotikmanager.monitor.BackgroundMonitor
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.fgmachines.mikrotikmanager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun RouterProfilesDialog(arabic:Boolean,current:RouterConnectionSettings?,onSelect:(RouterProfile)->Unit,onDismiss:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    val context=LocalContext.current;val store=remember { RouterProfiles(context) };val scope=rememberCoroutineScope()
    val monitored by BackgroundMonitor.routers.collectAsState()
    val alerts by BackgroundMonitor.alerts.collectAsState()
    var notificationDenied by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ notificationDenied=!it }
    var monitorTarget by remember { mutableStateOf<RouterProfile?>(null) }
    var monitorPassword by remember { mutableStateOf("") }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP){monitorPassword="";monitorTarget=null} }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);monitorPassword="" }
    }
    var profiles by remember { mutableStateOf(emptyList<RouterProfile>()) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") };var edit by rememberSaveable { mutableStateOf<String?>(null) };var delete by remember { mutableStateOf<RouterProfile?>(null) }
    var name by rememberSaveable { mutableStateOf("") };var branch by rememberSaveable { mutableStateOf("") }
    fun load(work:()->Unit={}) { if(busy)return;busy=true;error=false;scope.launch { try { profiles=withContext(Dispatchers.IO){work();store.list()};edit=null;delete=null }catch(_:Exception){error=true}finally{busy=false} } }
    LaunchedEffect(Unit){load()}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(tr("مركز الراوترات","Router center"))},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("ملفات اتصال محلية بلا كلمات مرور. اختر راوترًا ثم أدخل كلمة مروره واضغط اتصال. اتصال إدارة واحد؛ المراقبة منفصلة.","Local profiles without passwords. Select a router, enter its password and connect. One editing connection at a time. Monitoring is separate."))
        Text(tr("لراوترين بهوتسبوت مستقل على نفس الإنترنت: احفظ ملف اتصال لكل راوتر واختر المطلوب قبل إدارة كروته ومشتركيه. اجعل عناوين إدارة الراوترين مختلفة، وشبكتي HotSpot منفصلتين؛ لا تجمع منفذي LAN في شبكة واحدة مع خادمي DHCP.","For two independent hotspots sharing an Internet uplink: save a profile for each router and select it before managing its users and vouchers. Use different management addresses and separate HotSpot LANs; do not join two DHCP-serving LANs."),style=MaterialTheme.typography.bodySmall)
        Text(tr("راقب حتى 4 راوترات بفحص كل 30 ثانية، حتى في الخلفية، مع إشعار دائم وزر إيقاف. كلمات المرور في الذاكرة فقط؛ إذا أغلق النظام التطبيق ستحتاج بدء المراقبة مجددًا. حالة الراوتر لا تثبت اتصال الإنترنت أو الأكسس.","Monitor up to 4 routers every 30 seconds, including in the background, with a persistent notification and Stop action. Passwords remain in memory; restart monitoring if Android kills the process. Router reachability does not prove Internet or AP connectivity."),style=MaterialTheme.typography.bodySmall)
        if(notificationDenied)Text(tr("فعّل الإشعارات من إعدادات التطبيق ثم أعد المحاولة.","Enable notifications in app settings and try again."),color=MaterialTheme.colorScheme.error)
        if(monitored.isNotEmpty())OutlinedButton(onClick={BackgroundMonitor.stopAll(context)}){Text(tr("إيقاف كل المراقبة","Stop all monitoring"))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(tr("تعذرت العملية. راجع البيانات أو الاسم المكرر.","Operation failed. Check fields or duplicate names."),color=MaterialTheme.colorScheme.error)
        OutlinedTextField(search,{search=it.take(120)},label={Text(tr("بحث بالاسم أو الفرع أو العنوان","Search name, branch or address"))},modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={edit=UUID.randomUUID().toString();name="";branch=""},enabled=current!=null && !busy){Text(tr("حفظ بيانات الاتصال الحالية","Save current connection fields"))}
        LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(alerts.take(10)) { a->Text(a.router+" • "+when(a.kind){"UNREACHABLE"->tr("تعذر الاتصال مرتين","Unreachable twice");"HIGH_CPU"->tr("استخدام معالج مرتفع","High CPU");"AUTH_REQUIRED"->tr("سجل الدخول لاستئناف المراقبة","Sign in to resume monitoring");else->tr("انتهى التنبيه","Alert resolved")},style=MaterialTheme.typography.bodySmall) }
            items(profiles.filter { (it.name+" "+it.branch+" "+it.host).contains(search,true) },key={it.id}) { p->Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
                Text(p.name+if(p.branch.isBlank())"" else " • "+p.branch);Text(p.host+":"+p.port+" • "+p.protocol.name)
                monitored[p.id]?.let { m ->
                    val status=when { m.checking->tr("جاري الفحص","Checking");m.health.failures>0->tr("تعذر آخر فحص؛ البيانات السابقة قديمة","Last check failed; previous data is stale");m.snapshot!=null->tr("استجاب لآخر فحص","Responded to last check");else->tr("غير معروف","Unknown") }
                    Text(status)
                    m.snapshot?.let { Text("CPU: ${it.cpuLoadPercent ?: "?"}% • ${it.uptime}") }
                    TextButton(onClick={BackgroundMonitor.stop(context,p.id)}){Text(tr("إيقاف المراقبة","Stop monitoring"))}
                } ?: TextButton(onClick={if(!BackgroundMonitor.permitted(context) && Build.VERSION.SDK_INT>=33){permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else{monitorTarget=p;monitorPassword=""}},enabled=monitored.size<4){Text(tr("مراقبة","Monitor"))}
                Row { TextButton(onClick={onSelect(p)},enabled=!busy){Text(tr("اختيار","Select"))};TextButton(onClick={delete=p},enabled=!busy && p.id !in monitored){Text(tr("حذف الملف","Delete profile"))} }
            } } }
        }
    }},confirmButton={TextButton(onClick=onDismiss,enabled=!busy){Text(tr("رجوع","Back"))}})
    monitorTarget?.let { p->AlertDialog(onDismissRequest={monitorTarget=null;monitorPassword=""},title={Text(p.name)},text={OutlinedTextField(monitorPassword,{monitorPassword=it.take(128)},label={Text(tr("كلمة مرور المراقبة (مؤقتة)","Monitoring password (temporary)"))},visualTransformation=PasswordVisualTransformation())},confirmButton={TextButton(onClick={try{BackgroundMonitor.start(context,p,monitorPassword,arabic);notificationDenied=false}catch(_:Exception){notificationDenied=true};monitorTarget=null;monitorPassword=""},enabled=monitored.size<4){Text(tr("بدء","Start"))}},dismissButton={TextButton(onClick={monitorTarget=null;monitorPassword=""}){Text(tr("إلغاء","Cancel"))}}) }
    edit?.let { id->AlertDialog(onDismissRequest={if(!busy)edit=null},title={Text(tr("حفظ الراوتر","Save router"))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it.take(80)},label={Text(tr("اسم الراوتر","Router name"))});OutlinedTextField(branch,{branch=it.take(80)},label={Text(tr("اسم الفرع / المجموعة","Branch / group label"))})
        if(error)Text(tr("راجع الاسم والعنوان؛ الأسماء المكررة في المجموعة غير مسموحة.","Check name and address; duplicate names in a group are not allowed."),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={current?.let { c->load { store.save(RouterProfile(id,name,branch,c.normalizedHost(),c.port,c.username,c.protocol)) } }},enabled=!busy && name.isNotBlank()){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick={edit=null},enabled=!busy){Text(tr("إلغاء","Cancel"))}}) }
    delete?.let { p->AlertDialog(onDismissRequest={if(!busy)delete=null},title={Text(tr("حذف ملف الاتصال؟","Delete connection profile?"))},text={Text(p.name+"\n"+tr("لن تُحذف إعدادات الراوتر أو حساباته.","Router settings and accounts are retained."))},confirmButton={TextButton(onClick={load{store.delete(p.id)}},enabled=!busy){Text(tr("حذف","Delete"))}},dismissButton={TextButton(onClick={delete=null},enabled=!busy){Text(tr("إلغاء","Cancel"))}}) }
}
