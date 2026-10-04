package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.business.*
import kotlinx.coroutines.*

@Composable
fun BusinessCloudScreen(arabic:Boolean,onBack:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    val context=LocalContext.current.applicationContext;val coroutine=rememberCoroutineScope()
    var url by remember { mutableStateOf("") };var username by remember { mutableStateOf("") };var password by remember { mutableStateOf("") }
    var joinEmpty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) };var result by remember { mutableStateOf("") };var ids by remember { mutableStateOf("") }
    LaunchedEffect(Unit){ids=withContext(Dispatchers.IO){runCatching{BusinessStore(BusinessDatabase(context)).use{it.authorize(null,BusinessPermission.BRANCHES);val s=it.defaultScope();s.organizationId+"\n"+s.branchId}}.getOrDefault("")}}
    BackHandler {if(!busy)onBack()}
    Surface(color=FgBlack,contentColor=FgWhite) { Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick=onBack,enabled=!busy){Text(tr("رجوع","Back"))}
        Text(tr("مزامنة بيانات الأعمال","Business synchronization"),style=MaterialTheme.typography.titleLarge)
        Text(tr("مزامنة في الاتجاهين للمشتركين والباقات والفواتير والمدفوعات والمصروفات والمبيعات والموظفين وأرصدة الموزعين وسجل التدقيق للفرع الحالي. السجلات المالية لا تُستبدل، والتعارض يتوقف للمراجعة. كلمات مرور الموظفين والراوتر وأرشيف الكروت المشفر تظل محلية. تنفيذ أو تأكيد أمر شبكة يحتاج مراجعة على الجهاز نفسه.","Two-way synchronization of subscribers, plans, invoices, payments, expenses, sales, staff, reseller balances and audit history for the current branch. Financial records are never overwritten; conflicts require review. Employee/router credentials and encrypted voucher archives stay local. Network actions require device-local review and confirmation."))
        Text(tr("معرف المؤسسة ثم الفرع:","Tenant then branch ID:"));androidx.compose.foundation.text.selection.SelectionContainer{Text(ids,style=MaterialTheme.typography.bodySmall)}
        OutlinedTextField(url,{url=it.take(300)},label={Text("HTTPS URL")},enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(username,{username=it.take(120)},label={Text(tr("حساب الخادم","Server username"))},enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(password,{password=it.take(128)},label={Text(tr("كلمة المرور","Password"))},visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row { Checkbox(joinEmpty,{joinEmpty=it},enabled=!busy);Text(tr("الانضمام لفرع الخادم على هاتف بسجل فارغ فقط، قبل إنشاء حساب المالك.","Join the server branch only on an unused store, before owner enrollment."),modifier=Modifier.weight(1f)) }
        Button(onClick={busy=true;result="";val secret=password;password="";coroutine.launch { try {
            val sent=withContext(Dispatchers.IO){BusinessStore(BusinessDatabase(context)).use{BusinessCloud(it).sync(url,username,secret,joinEmpty)}}
            result=tr("تمت المزامنة: رفع ","Synchronized: uploaded ")+sent.sent+tr(" • تنزيل "," • downloaded ")+sent.imported+tr(" • الإصدار "," • revision ")+sent.revision
        }catch(c:CancellationException){throw c}catch(e:Exception){result=when(e.message){"CLOUD_REVISION_CONFLICT","CLOUD_LOCAL_CHANGED"->tr("تغيرت البيانات أثناء المزامنة؛ أعد المحاولة. لم تُستبدل سجلات الهاتف.","Data changed during synchronization. Retry; phone records were not overwritten.");"CLOUD_RECORD_CONFLICT","CLOUD_TEAM_CONFLICT"->tr("يوجد تعارض بين سجلين. راجع البيانات؛ لن نستبدل سجلًا ماليًا أو صلاحية بصمت.","Records conflict. Review the data; financial records or permissions are never silently replaced.");else->tr("لم يتم تأكيد النقل. راجع HTTPS والحساب ومعرفي المؤسسة والفرع. لا تغيّر السجل لتجاوز تعارض؛ تكرار نفس القيود آمن.","Transfer not confirmed. Check HTTPS, credentials and tenant/branch IDs. Do not alter records to bypass conflicts; replaying identical entries is safe.")}}finally{busy=false} }},enabled=!busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()){Text(tr("مزامنة الآن","Synchronize now"))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(result.isNotBlank())Text(result)
    } }
}
