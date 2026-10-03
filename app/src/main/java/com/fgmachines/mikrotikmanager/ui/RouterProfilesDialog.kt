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
import com.fgmachines.mikrotikmanager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun RouterProfilesDialog(arabic:Boolean,current:RouterConnectionSettings?,onSelect:(RouterProfile)->Unit,onDismiss:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    val context=LocalContext.current;val store=remember { RouterProfiles(context) };val scope=rememberCoroutineScope()
    var profiles by remember { mutableStateOf(emptyList<RouterProfile>()) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") };var edit by rememberSaveable { mutableStateOf<String?>(null) };var delete by remember { mutableStateOf<RouterProfile?>(null) }
    var name by rememberSaveable { mutableStateOf("") };var branch by rememberSaveable { mutableStateOf("") }
    fun load(work:()->Unit={}) { if(busy)return;busy=true;error=false;scope.launch { try { profiles=withContext(Dispatchers.IO){work();store.list()};edit=null;delete=null }catch(_:Exception){error=true}finally{busy=false} } }
    LaunchedEffect(Unit){load()}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(tr("مركز الراوترات","Router center"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("ملفات اتصال محلية بلا كلمات مرور. اختر راوترًا ثم أدخل كلمة مروره واضغط اتصال. اتصال واحد نشط في كل مرة.","Local profiles without passwords. Select a router, enter its password and connect. One active connection at a time."))
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error)Text(tr("تعذرت العملية. راجع البيانات أو الاسم المكرر.","Operation failed. Check fields or duplicate names."),color=MaterialTheme.colorScheme.error)
        OutlinedTextField(search,{search=it.take(120)},label={Text(tr("بحث بالاسم أو الفرع أو العنوان","Search name, branch or address"))},modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={edit=UUID.randomUUID().toString();name="";branch=""},enabled=current!=null && !busy){Text(tr("حفظ بيانات الاتصال الحالية","Save current connection fields"))}
        LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(profiles.filter { (it.name+" "+it.branch+" "+it.host).contains(search,true) },key={it.id}) { p->Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
                Text(p.name+if(p.branch.isBlank())"" else " • "+p.branch);Text(p.host+":"+p.port+" • "+p.protocol.name)
                Row { TextButton(onClick={onSelect(p)},enabled=!busy){Text(tr("اختيار","Select"))};TextButton(onClick={delete=p},enabled=!busy){Text(tr("حذف الملف","Delete profile"))} }
            } } }
        }
    }},confirmButton={TextButton(onClick=onDismiss,enabled=!busy){Text(tr("رجوع","Back"))}})
    edit?.let { id->AlertDialog(onDismissRequest={if(!busy)edit=null},title={Text(tr("حفظ الراوتر","Save router"))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it.take(80)},label={Text(tr("اسم الراوتر","Router name"))});OutlinedTextField(branch,{branch=it.take(80)},label={Text(tr("اسم الفرع / المجموعة","Branch / group label"))})
        if(error)Text(tr("راجع الاسم والعنوان؛ الأسماء المكررة في المجموعة غير مسموحة.","Check name and address; duplicate names in a group are not allowed."),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={current?.let { c->load { store.save(RouterProfile(id,name,branch,c.normalizedHost(),c.port,c.username,c.protocol)) } }},enabled=!busy && name.isNotBlank()){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick={edit=null},enabled=!busy){Text(tr("إلغاء","Cancel"))}}) }
    delete?.let { p->AlertDialog(onDismissRequest={if(!busy)delete=null},title={Text(tr("حذف ملف الاتصال؟","Delete connection profile?"))},text={Text(p.name+"\n"+tr("لن تُحذف إعدادات الراوتر أو حساباته.","Router settings and accounts are retained."))},confirmButton={TextButton(onClick={load{store.delete(p.id)}},enabled=!busy){Text(tr("حذف","Delete"))}},dismissButton={TextButton(onClick={delete=null},enabled=!busy){Text(tr("إلغاء","Cancel"))}}) }
}
