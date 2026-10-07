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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.business.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

val LocalAccessLanguage=staticCompositionLocalOf<Pair<Boolean,(Boolean)->Unit>?> { null }
val LocalManageAccess=staticCompositionLocalOf<()->Unit> { {} }
val LocalBusinessPermissions=staticCompositionLocalOf<Set<BusinessPermission>> { BusinessPermission.entries.toSet() }

class BusinessAccessModel(app:Application,private val saved:androidx.lifecycle.SavedStateHandle,name:String):AndroidViewModel(app) {
    constructor(app:Application,saved:androidx.lifecycle.SavedStateHandle):this(app,saved,"fg_business.db")
    private val store=BusinessStore(BusinessDatabase(app,name));private val identity=BusinessIdentity(store)
    var ready by mutableStateOf(false);private set
    var enabled by mutableStateOf(true);private set
    var principal by mutableStateOf<LocalPrincipal?>(null);private set
    var accounts by mutableStateOf(emptyList<LocalAccount>());private set
    var candidates by mutableStateOf(emptyList<TeamMember>());private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    private var workspace:BusinessWorkspaceOwner?=null
    private var workspaceId:String?=null
    private var reloadAccounts=false
    init { saved.setSavedStateProvider("workspace") { workspace?.save() ?: android.os.Bundle() };refresh() }
    internal fun workspace(id:String):BusinessWorkspaceOwner {
        if(workspaceId!=id){
            workspace?.close()
            val restored=if(saved.get<String>("workspaceId")==id)saved.get<android.os.Bundle>("workspace") else null
            workspace=BusinessWorkspaceOwner(getApplication(),restored);workspaceId=id;saved["workspaceId"]=id
            saved.setSavedStateProvider("workspace"){workspace?.save() ?: android.os.Bundle()}
        }
        return workspace!!
    }
    private fun run(quiet:Boolean=false,action:()->Unit={}) {
        if(busy)return;busy=true;if(!quiet)error=null
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO){
                    action();val active=identity.principal();val enabled=identity.enabled()
                    val lists=if(reloadAccounts && active?.role=="OWNER") {
                        val a=identity.accounts();a to BusinessTeam(store).members(store.defaultScope()).filter{m->m.active && a.none{it.member==m.id}}
                    }else null
                    Triple(enabled,active,lists)
                }
                enabled=result.first;principal=result.second;ready=true
                result.third?.let {accounts=it.first;candidates=it.second};reloadAccounts=false
                if(principal?.role!="OWNER"){accounts=emptyList();candidates=emptyList()}
                if(enabled && principal==null){workspace?.close();workspace=null;workspaceId=null;saved.remove<android.os.Bundle>("workspace")}
            }catch(e:Exception){error=e.message?.takeIf{it in setOf("LOGIN_REQUIRED","ACCESS_DENIED","INVALID_LOGIN","LOGIN_THROTTLED","PASSWORD_LENGTH","INVALID_USERNAME","OWNER_EXISTS","OWNER_NOT_CONFIGURED","INACTIVE_MEMBER")} ?: "ACCESS_FAILED"}
            finally{busy=false}
        }
    }
    fun refresh(quiet:Boolean=false)=run(quiet)
    fun lock(){identity.lock();principal=null;workspace?.close();workspace=null;workspaceId=null;saved.remove<android.os.Bundle>("workspace")}
    fun login(name:String,password:CharArray){if(busy){password.fill('\u0000');return};run { identity.login(name,password) }}
    fun bootstrap(password:CharArray){if(busy){password.fill('\u0000');return};run { identity.bootstrap(password) }}
    fun loadAccounts(){reloadAccounts=true;run()}
    fun create(member:String,name:String,password:CharArray){if(busy){password.fill('\u0000');return};reloadAccounts=true;run {identity.create(store.defaultScope(),member,name,password)}}
    fun reset(id:String,password:CharArray){if(busy){password.fill('\u0000');return};reloadAccounts=true;run {identity.reset(id,password)}}
    override fun onCleared(){workspace?.close();store.close();super.onCleared()}
}

/** Fail closed when local identity is enabled. Cashier/read-only/reseller never enter router UI. */
@Composable
fun BusinessAccessRoot(model:BusinessAccessModel=viewModel(),content:@Composable ()->Unit) {
    var arabic by rememberSaveable{mutableStateOf(Locale.getDefault().language=="ar")}
    var manage by rememberSaveable{mutableStateOf(false)}
    var business by rememberSaveable{mutableStateOf(false)}
    val holder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var previousIdentity by rememberSaveable{mutableStateOf<String?>(null)}
    fun tr(a:String,e:String)=if(arabic)a else e
    val identityKey=if(!model.enabled)"legacy" else model.principal?.id ?: "locked"
    val activity=androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle,identityKey){
        if(model.principal!=null) lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED){
            while(true){delay(2000);model.refresh(true)}
        }
    }
    LaunchedEffect(identityKey){
        previousIdentity?.takeIf{it!=identityKey}?.let{holder.removeState(it)}
        if(previousIdentity!=null && previousIdentity!=identityKey){manage=false;business=false}
        previousIdentity=identityKey
    }
    if(!model.ready){FgMikroTikTheme{Surface(Modifier.fillMaxSize(),color=FgBlack){Column(Modifier.safeDrawingPadding().padding(20.dp)){Text(tr("جارٍ التحقق من الدخول…","Checking access…"));model.error?.let{Text(it)};TextButton(onClick={model.refresh()}){Text(tr("إعادة المحاولة","Retry"))}}}};return}
    val user=model.principal
    val permissions=if(!model.enabled)BusinessPermission.entries.toSet() else user?.let{BusinessAccess.permissions(it.role)} ?: emptySet()
    androidx.compose.runtime.CompositionLocalProvider(LocalAccessLanguage provides (arabic to { next:Boolean -> arabic=next }),LocalManageAccess provides {if(BusinessPermission.AUTH in permissions){manage=true;model.loadAccounts()}},LocalBusinessPermissions provides permissions,
        androidx.compose.ui.platform.LocalLayoutDirection provides if(arabic)androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
        FgMikroTikTheme {
            when {
                model.enabled && user==null -> AccessLogin(arabic,model,{arabic=!arabic})
                manage && BusinessPermission.AUTH in permissions -> BusinessAccessScreen(arabic,model,{manage=false})
                !model.enabled -> holder.SaveableStateProvider(identityKey){BusinessWorkspace(model,identityKey,activity){content()}}
                else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(user!!.username+" • "+user.role,Modifier.weight(1f).padding(10.dp),color=FgSilver)
                        TextButton(onClick=model::lock){Text(tr("قفل","Lock"))}
                    }
                    Box(Modifier.weight(1f)) {
                        holder.SaveableStateProvider(identityKey) { BusinessWorkspace(model,identityKey,activity) {
                            when {
                                user!!.role=="RESELLER" -> TeamScreen(arabic,{model.lock()})
                                BusinessPermission.ROUTER in permissions -> content()
                                business -> BusinessScreen(arabic,{business=false},{arabic=!arabic})
                                else -> Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text(tr("مساحة الموظف","Employee workspace"),color=FgWhite)
                                    Button(onClick={business=true}){Text(tr("المشتركون والحسابات","Subscribers & accounts"))}
                                    TextButton(onClick={arabic=!arabic}){Text(tr("English","العربية"))}
                                }
                            }
                        } }
                    }
                }
            }
        }
    }
}

@Composable private fun BusinessWorkspace(model:BusinessAccessModel,id:String,activity:androidx.activity.ComponentActivity?,content:@Composable ()->Unit) {
    val owner=remember(id){model.workspace(id)}
    DisposableEffect(owner,activity){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            when(event){androidx.lifecycle.Lifecycle.Event.ON_RESUME->owner.state(androidx.lifecycle.Lifecycle.State.RESUMED)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE->owner.state(androidx.lifecycle.Lifecycle.State.STARTED)
                androidx.lifecycle.Lifecycle.Event.ON_STOP->owner.state(androidx.lifecycle.Lifecycle.State.CREATED)
                else->Unit}
        }
        activity?.lifecycle?.addObserver(observer)
        if(activity?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)==true)owner.state(androidx.lifecycle.Lifecycle.State.RESUMED)
        onDispose{activity?.lifecycle?.removeObserver(observer)}
    }
    CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner){key(id){content()}}
}

@Composable private fun AccessLogin(arabic:Boolean,model:BusinessAccessModel,language:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    var username by rememberSaveable{mutableStateOf("")};var password by remember{mutableStateOf("")}
    Surface(color=FgBlack,contentColor=FgWhite,modifier=Modifier.fillMaxSize()){
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("FG MTM",style=MaterialTheme.typography.headlineMedium)
            Text(tr("دخول الموظفين","Employee sign in"))
            OutlinedTextField(username,{username=it.take(40)},label={Text(tr("اسم الدخول","Login name"))},singleLine=true,enabled=!model.busy)
            OutlinedTextField(password,{password=it.take(128)},label={Text(tr("كلمة المرور","Password"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!model.busy)
            Button(onClick={val chars=password.toCharArray();password="";model.login(username,chars)},enabled=!model.busy && username.isNotBlank() && password.isNotEmpty()){Text(tr("دخول","Sign in"))}
            TextButton(onClick=language){Text(tr("English","العربية"))}
            AccessError(arabic,model.error)
            Text(tr("لا تُحذف البيانات عند القفل. الجلسة تنتهي بعد 15 دقيقة، بما فيها وقت الخلفية، أو عند القفل. اطلب من المالك إعادة ضبط كلمة المرور إذا نسيتها.","Locking preserves data. Sessions expire after 15 minutes including background time, or when locked. Ask the owner to reset a forgotten password."),color=FgSilver)
        }
    }
}

@Composable
fun BusinessAccessScreen(arabic:Boolean,model:BusinessAccessModel,onBack:()->Unit) {
    fun tr(a:String,e:String)=if(arabic)a else e
    BackHandler{if(!model.busy)onBack()}
    var editor by rememberSaveable{mutableStateOf<String?>(null)}
    var member by rememberSaveable{mutableStateOf("")};var reset by rememberSaveable{mutableStateOf("")}
    Surface(color=FgBlack,contentColor=FgWhite,modifier=Modifier.fillMaxSize()) {
        LazyColumn(Modifier.safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{TextButton(onClick=onBack,enabled=!model.busy){Text(tr("رجوع","Back"))};Text(tr("الدخول والصلاحيات","Sign in & permissions"),style=MaterialTheme.typography.titleLarge)}
            item{AccessError(arabic,model.error);if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(!model.enabled) {
                item{Text(tr("فعّل حساب المالك لحماية هذا الجهاز، ثم أنشئ بيانات دخول للموظفين الموجودين. لن تُحذف بيانات الأعمال. اسم المالك: owner. احفظ كلمة مروره؛ لا يوجد تجاوز تلقائي إذا فُقدت. النسخ المحمولة لا تتضمن كلمات دخول الموظفين.","Enable the owner account for this device, then assign logins to existing staff. Business data is retained. Owner login: owner. Keep its password; there is no automatic bypass if lost. Portable backups exclude employee credentials."),color=FgSilver)}
                item{Button(onClick={editor="owner"},enabled=!model.busy){Text(tr("تفعيل حساب المالك","Enable owner account"))}}
            }else{
                item{Text(tr("الموظف مقيد بفرعه، والمالك والمدير العام يمكنهما إدارة الفروع. الكاشير يسجل البيع والتحصيل ولا يعكس القيود؛ الفني يستورد ويعمل على الراوتر دون ترحيل مالي؛ الموزع يقرأ محفظته فقط. هذه صلاحيات داخل التطبيق؛ لا تغير حسابات RouterOS ولا تتحكم في عملاء خارجيين.","Staff are branch-bound; owner and admin can manage branches. Cashiers record sales/collections but cannot reverse entries; technicians import/manage routers without financial posting; resellers read only their own wallet. These are app permissions; RouterOS accounts and external clients are separate."),color=FgSilver)}
                items(model.accounts,key={it.id}){a->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(a.username+" • "+a.role);Text(if(a.active)tr("نشط","Active") else tr("موقوف من سجل الموظفين","Disabled in staff directory"));TextButton(onClick={reset=a.id;editor="reset"},enabled=!model.busy){Text(tr("إعادة ضبط كلمة المرور","Reset password"))}}}}
                item{Text(tr("موظفون نشطون في الفرع الحالي دون دخول:","Active staff in current branch without a login:"))}
                items(model.candidates,key={it.id}){m->OutlinedButton(onClick={member=m.id;editor="member"},enabled=!model.busy){Text(m.name+" • "+m.role)}}
                item{Text(tr("إيقاف الموظف من سجله يمنع الدخول ويلغي جلسته. لاستعادة نسخة أعمال على جهاز جديد، استعدها قبل تفعيل حساب المالك ثم أعد إنشاء الدخول.","Disabling a staff member blocks sign-in and revokes their session. On a new device, restore business data before enrolling an owner, then create logins again."),color=FgSilver)}
            }
        }
    }
    editor?.let { mode->
        var name by rememberSaveable(mode){mutableStateOf("")};var password by remember(mode){mutableStateOf("")};var confirm by remember(mode){mutableStateOf("")}
        AlertDialog(onDismissRequest={if(!model.busy)editor=null},title={Text(if(mode=="member")tr("دخول جديد","New login") else tr("كلمة مرور جديدة","New password"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(mode=="member")OutlinedTextField(name,{name=it.take(40)},label={Text(tr("اسم الدخول (حروف لاتينية وأرقام)","Login name (Latin letters/digits)"))},singleLine=true)
            Text(tr("12–128 حرفًا. لن تُحفظ الكلمة في حالة الشاشة.","12–128 characters. Passwords are not saved in screen state."))
            OutlinedTextField(password,{password=it.take(128)},label={Text(tr("كلمة المرور الجديدة","New password"))},visualTransformation=PasswordVisualTransformation())
            OutlinedTextField(confirm,{confirm=it.take(128)},label={Text(tr("تأكيد كلمة المرور","Confirm password"))},visualTransformation=PasswordVisualTransformation())
        }},confirmButton={TextButton(onClick={val chars=password.toCharArray();password="";confirm="";editor=null;when(mode){"owner"->model.bootstrap(chars);"member"->model.create(member,name,chars);else->model.reset(reset,chars)}},enabled=!model.busy && password.length>=12 && password==confirm && (mode!="member" || name.isNotBlank())){Text(tr("حفظ","Save"))}},dismissButton={TextButton(onClick={editor=null},enabled=!model.busy){Text(tr("إلغاء","Cancel"))}})
    }
}

@Composable private fun AccessError(arabic:Boolean,error:String?) {
    error?.let{Text(when(it){"INVALID_LOGIN"->if(arabic)"بيانات الدخول غير صحيحة أو الموظف موقوف." else "Invalid credentials or disabled staff.";"LOGIN_THROTTLED"->if(arabic)"انتظر 30 ثانية بعد المحاولات المتكررة." else "Wait 30 seconds after repeated attempts.";"PASSWORD_LENGTH"->if(arabic)"كلمة المرور من 12 إلى 128 حرفًا." else "Password needs 12–128 characters.";else->if(arabic)"تعذرت العملية؛ راجع الدخول والصلاحيات والبيانات." else "Operation failed; check access and input."},color=MaterialTheme.colorScheme.error)}
}
