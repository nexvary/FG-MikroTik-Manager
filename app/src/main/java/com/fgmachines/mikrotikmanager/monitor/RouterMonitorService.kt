package com.fgmachines.mikrotikmanager.monitor

import android.annotation.SuppressLint
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fgmachines.mikrotikmanager.MainActivity
import com.fgmachines.mikrotikmanager.R
import com.fgmachines.mikrotikmanager.data.RouterProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicit, visible foreground monitoring. No credentials in intents, preferences or restart state. */
object BackgroundMonitor {
    internal data class Pending(val profile:RouterProfile,val password:String,val arabic:Boolean)
    internal val pending=ConcurrentHashMap<String,Pending>()
    internal val state=MutableStateFlow<Map<String,MonitoredRouter>>(emptyMap())
    internal val events=MutableStateFlow<List<MonitorAlert>>(emptyList())
    val routers=state.asStateFlow();val alerts=events.asStateFlow()
    fun permitted(context:Context)=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun start(context:Context,profile:RouterProfile,password:String,arabic:Boolean) {
        check(permitted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()){ "NOTIFICATIONS_REQUIRED" }
        check(state.value.size+pending.size<4 || profile.id in state.value){"MONITOR_LIMIT"}
        val id=UUID.randomUUID().toString();pending[id]=Pending(profile,password,arabic)
        try { ContextCompat.startForegroundService(context,Intent(context,RouterMonitorService::class.java).setAction("START").putExtra("request",id)) }
        catch(t:Exception){pending.remove(id);throw t}
    }
    fun stop(context:Context,id:String){context.startService(Intent(context,RouterMonitorService::class.java).setAction("STOP_ONE").putExtra("id",id))}
    fun stopAll(context:Context){pending.clear();context.stopService(Intent(context,RouterMonitorService::class.java))}
}
class RouterMonitorService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var monitor:RouterMonitor
    private var started=false
    private var arabic=false
    private var lastAlert:MonitorAlert?=null
    override fun onBind(intent:Intent?):IBinder?=null
    @SuppressLint("MissingPermission") // Start is gated on notification permission; foreground operation stays visible.
    override fun onCreate() {
        super.onCreate();monitor=RouterMonitor(applicationContext,scope)
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("fg-monitor","FG MTM monitoring / المراقبة",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("fg-router-alerts","FG MTM router alerts / تنبيهات الراوتر",NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch { monitor.routers.collect { BackgroundMonitor.state.value=it; if(it.isNotEmpty() && BackgroundMonitor.permitted(this@RouterMonitorService))manager.notify(700,notification(it.size)) else if(started && BackgroundMonitor.pending.isEmpty())scope.launch { yield();if(monitor.routers.value.isEmpty() && BackgroundMonitor.pending.isEmpty())stopSelf() } } }
        scope.launch { monitor.alerts.collect { alerts ->
            BackgroundMonitor.events.value=alerts
            // StateFlow may coalesce simultaneous router failures; deliver every unseen alert in its retained list.
            val unseen=if(lastAlert==null)alerts else alerts.takeWhile{it!=lastAlert}
            unseen.asReversed().forEach(::postAlert)
            lastAlert=alerts.firstOrNull()
        } }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Promote immediately, including a STOP intent racing a new instance.
        ServiceCompat.startForeground(this,700,notification(BackgroundMonitor.state.value.size),if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        when(intent?.action) {
            "START" -> {
                started=true
                val p=intent.getStringExtra("request")?.let { BackgroundMonitor.pending.remove(it) }
                if(p!=null) { arabic=p.arabic;try { monitor.start(p.profile,p.password) }catch(_:Exception){if(monitor.routers.value.isEmpty())stopSelf()} }
                else if(monitor.routers.value.isEmpty())stopSelf()
            }
            "STOP_ONE" -> { intent.getStringExtra("id")?.let(monitor::stop);if(monitor.routers.value.isEmpty())stopSelf() }
            else -> { monitor.close();stopSelf() }
        }
        return START_NOT_STICKY // Process death requires a new explicit login/start, never persisted passwords.
    }
    private fun notification(count:Int):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,RouterMonitorService::class.java).setAction("STOP_ALL"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"fg-monitor").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if(arabic)"مراقبة الراوترات" else "Router monitoring")
            .setContentText(if(arabic)"$count راوتر • اتصال الإنترنت غير متحقق منه" else "$count routers • Internet unverified")
            .setContentIntent(open).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0,if(arabic)"إيقاف الكل" else "Stop all",stop).build()
    }
    @SuppressLint("MissingPermission") // Checked immediately below.
    private fun postAlert(alert:MonitorAlert) {
        if(!BackgroundMonitor.permitted(this))return
        val text=when(alert.kind){
            "AUTH_REQUIRED"->if(arabic)"توقفت المراقبة: سجل الدخول وراجع الصلاحيات" else "Monitoring stopped: sign in and check permissions"
            "UNREACHABLE"->if(arabic)"انقطع اتصال إدارة الراوتر" else "Router management connection lost"
            "HIGH_CPU"->if(arabic)"استخدام معالج مرتفع" else "High router CPU"
            "RESOLVED:UNREACHABLE"->if(arabic)"عاد اتصال إدارة الراوتر" else "Router management connection restored"
            else->if(arabic)"انتهى تنبيه المعالج" else "CPU alert resolved"
        }
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(NotificationManager::class.java).notify(1000+alert.router.hashCode().and(0xFFFF),NotificationCompat.Builder(this,"fg-router-alerts")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(alert.router).setContentText(text).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setStyle(NotificationCompat.BigTextStyle().bigText(text)).build())
    }
    override fun onDestroy(){monitor.close();scope.cancel();BackgroundMonitor.pending.clear();BackgroundMonitor.state.value=emptyMap();BackgroundMonitor.events.value=emptyList();super.onDestroy()}
}
