package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.business.BusinessBackupCipher
import com.fgmachines.mikrotikmanager.voucher.VoucherHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoucherArchiveModel(app:Application):AndroidViewModel(app) {
    var busy by mutableStateOf(false);private set
    var count by mutableIntStateOf(0);private set
    var message by mutableStateOf<String?>(null);private set
    private val archive=VoucherHistoryStore(app)
    fun refresh(){viewModelScope.launch{count=archive.count()}}
    fun transfer(uri:Uri,password:CharArray,restore:Boolean) {
        if(busy){password.fill('\u0000');return};busy=true;message=null
        viewModelScope.launch {
            try {
                val added=withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    if(restore){val bytes=resolver.openInputStream(uri)!!.use { BusinessBackupCipher.readBounded(it) };archive.importPortable(bytes,password)}
                    else { val bytes=archive.exportPortable(password);resolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)};0 }
                }
                count=archive.count();message=if(restore)"IMPORTED:$added" else "EXPORTED"
            }catch(_:Exception){message="FAILED"}finally{password.fill('\u0000');busy=false}
        }
    }
}
@Composable
fun VoucherArchiveScreen(arabic:Boolean,onBack:()->Unit,model:VoucherArchiveModel=viewModel()) {
    fun tr(a:String,e:String)=if(arabic)a else e
    var uri by remember { mutableStateOf<Uri?>(null) };var restore by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") };var confirmation by remember { mutableStateOf("") }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { if(it!=null){uri=it;restore=false;password="";confirmation=""} }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if(it!=null){uri=it;restore=true;password="";confirmation=""} }
    LaunchedEffect(Unit){model.refresh()}
    BackHandler { if(!model.busy){if(uri!=null){uri=null;password="";confirmation=""}else onBack()} }
    Surface(color=FgBlack,contentColor=FgWhite,modifier=Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack,enabled=!model.busy){Text(tr("رجوع","Back"))}
        Text(tr("حماية ونقل أرشيف الكروت","Protect & transfer voucher archive"),style=MaterialTheme.typography.titleLarge)
        Text(tr("عدد الدفعات: ","Batch count: ")+model.count)
        Text(tr("صدّر من الجهاز الذي يفتح الأرشيف القديم، ثم استورد على الجهاز الجديد. يحتوي الملف كلمات مرور الكروت، ويُشفّر بكلمة مرور تختارها. لا تحذف التطبيق القديم قبل التحقق من الاستعادة.","Export from the device that can read the old archive, then import on the new device. The file includes voucher passwords encrypted with your chosen password. Verify restoration before removing the old app."))
        Text(tr("الاستيراد يضيف الدفعات ويحافظ على الحالية. التكرار المطابق يُتخطى والتعارض يُرفض بالكامل. لا ينشئ حسابات على الراوتر، ولا يستعيد مفتاح Android المفقود. الحد 20 ميجابايت و10,000 دفعة.","Import merges batches, skips identical duplicates and rejects conflicts atomically. It does not provision router accounts or recover a lost Android key. Limit: 20 MiB and 10,000 batches."),color=FgSilver)
        if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick={export.launch("FG-MTM-voucher-archive.fgv")},enabled=!model.busy){Text(tr("تصدير مشفر","Encrypted export"))}
        OutlinedButton(onClick={import.launch(arrayOf("*/*"))},enabled=!model.busy){Text(tr("استيراد أرشيف مشفر","Import encrypted archive"))}
        model.message?.let { Text(when { it=="EXPORTED"->tr("تم حفظ الأرشيف المشفر.","Encrypted archive saved.");it.startsWith("IMPORTED:")->tr("تم الاستيراد. الدفعات الجديدة: ","Imported. New batches: ")+it.substringAfter(':');else->tr("تعذرت العملية. راجع كلمة المرور والملف ومفتاح الجهاز القديم. لم يُؤكد نجاح العملية. احذف ملف التصدير غير المكتمل إن وُجد.","Operation failed. Check the password, file and original device key. Success is not confirmed; remove any incomplete export file.") },color=if(it=="FAILED")FgAmber else FgMint) }
    }
    }
    if(uri!=null)AlertDialog(onDismissRequest={uri=null;password="";confirmation=""},title={Text(tr("كلمة مرور الأرشيف","Archive password"))},text={Column {
        Text(tr("12 حرفًا على الأقل. احتفظ بها؛ لا يمكن استعادتها من التطبيق.","At least 12 characters. Keep it safe; the app cannot recover it."))
        OutlinedTextField(password,{password=it},label={Text(tr("كلمة المرور","Password"))},visualTransformation=PasswordVisualTransformation())
        if(!restore)OutlinedTextField(confirmation,{confirmation=it},label={Text(tr("تأكيد كلمة المرور","Confirm password"))},visualTransformation=PasswordVisualTransformation())
    }},confirmButton={TextButton(onClick={val selected=uri!!;uri=null;val chars=password.toCharArray();password="";confirmation="";model.transfer(selected,chars,restore)},enabled=password.length>=12 && (restore || password==confirmation)){Text(tr("تأكيد","Confirm"))}},dismissButton={TextButton(onClick={uri=null;password="";confirmation=""}){Text(tr("إلغاء","Cancel"))}})
}
