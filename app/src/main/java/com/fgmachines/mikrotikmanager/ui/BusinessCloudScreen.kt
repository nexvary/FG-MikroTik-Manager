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
    var busy by remember { mutableStateOf(false) };var result by remember { mutableStateOf("") };var ids by remember { mutableStateOf("") }
    LaunchedEffect(Unit){ids=withContext(Dispatchers.IO){runCatching{BusinessStore(BusinessDatabase(context)).use{it.authorize(null,BusinessPermission.BRANCHES);val s=it.defaultScope();s.organizationId+"\n"+s.branchId}}.getOrDefault("")}}
    BackHandler {if(!busy)onBack()}
    Surface(color=FgBlack,contentColor=FgWhite) { Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick=onBack,enabled=!busy){Text(tr("رجوع","Back"))}
        Text(tr("نقل السجل إلى الخادم","Transfer ledger to server"),style=MaterialTheme.typography.titleLarge)
        Text(tr("اختياري وتجريبي: يرسل حتى 100 قيد مالي في كل مرة، ولا يستبدل بيانات الهاتف. يلزم خادم FG مهيأ وحساب بنفس معرفي المؤسسة والفرع. QUEUED يعني انتظار المعالجة؛ QUARANTINED يعني يحتاج مراجعة. ليس مزامنة لكل بيانات التطبيق.","Optional preview: sends up to 100 financial entries per batch and never replaces phone data. Requires a configured FG server account with matching tenant/branch IDs. QUEUED awaits processing; QUARANTINED needs review. This does not sync all app data."))
        Text(tr("معرف المؤسسة ثم الفرع:","Tenant then branch ID:"));androidx.compose.foundation.text.selection.SelectionContainer{Text(ids,style=MaterialTheme.typography.bodySmall)}
        OutlinedTextField(url,{url=it.take(300)},label={Text("HTTPS URL")},enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(username,{username=it.take(120)},label={Text(tr("حساب الخادم","Server username"))},enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(password,{password=it.take(128)},label={Text(tr("كلمة المرور","Password"))},visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(onClick={busy=true;result="";val secret=password;password="";coroutine.launch { try {
            val sent=withContext(Dispatchers.IO){BusinessStore(BusinessDatabase(context)).use{BusinessCloud(it).upload(url,username,secret)}}
            result=tr("تم إرسال: ","Sent: ")+sent.count+" • "+sent.statuses+if(sent.remaining)tr(" • توجد دفعات أخرى"," • More batches remain")else ""
        }catch(c:CancellationException){throw c}catch(_:Exception){result=tr("لم يتم تأكيد النقل. راجع HTTPS والحساب ومعرفي المؤسسة والفرع. لا تغيّر السجل لتجاوز تعارض؛ تكرار نفس القيود آمن.","Transfer not confirmed. Check HTTPS, credentials and tenant/branch IDs. Do not alter records to bypass conflicts; replaying identical entries is safe.")}finally{busy=false} }},enabled=!busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()){Text(tr("إرسال دفعة / مراجعة حالتها","Send batch / review status"))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(result.isNotBlank())Text(result)
    } }
}
