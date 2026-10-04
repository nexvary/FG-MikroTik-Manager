package com.fgmachines.mikrotikmanager.monitor

import android.content.Context
import android.os.SystemClock
import com.fgmachines.mikrotikmanager.data.*
import com.fgmachines.mikrotikmanager.business.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MonitoredRouter(val profile:RouterProfile,val health:MonitorHealth=MonitorHealth(),val snapshot:DashboardSnapshot?=null,val checking:Boolean=true)
data class MonitorAlert(val router:String,val kind:String,val at:Long)
/** Four opted-in routers, two concurrent polls. The owning service/screen controls lifetime. */
class RouterMonitor(private val context:Context,private val scope:CoroutineScope) : AutoCloseable {
    private val jobs=mutableMapOf<String,Job>()
    private val active=java.util.concurrent.ConcurrentHashMap<String,RouterRepository>()
    private val permits=Semaphore(2)
    private val mutable=MutableStateFlow<Map<String,MonitoredRouter>>(emptyMap())
    val routers=mutable.asStateFlow()
    private val events=MutableStateFlow<List<MonitorAlert>>(emptyList())
    val alerts=events.asStateFlow()
    fun start(profile:RouterProfile,password:String) {
        check(jobs.size<4 || jobs.containsKey(profile.id)){"MONITOR_LIMIT"}
        stop(profile.id)
        mutable.value=mutable.value+(profile.id to MonitoredRouter(profile))
        jobs[profile.id]=scope.launch {
            val settings=RouterConnectionSettings(profile.host,profile.port,profile.username,password,profile.protocol)
            while(isActive) {
                try {
                    permits.withPermit {
                        val repository=RouterRepository.create(settings){BusinessAuthorizedTransport(it,BusinessStore(BusinessDatabase(context)))}
                        active[profile.id]=repository
                        try {
                            val result=withTimeoutOrNull(60000){repository.loadDashboard()}
                            ensureActive();publish(profile,result!=null,result)
                        } finally {active.remove(profile.id,repository);withContext(Dispatchers.IO){repository.close()}}
                    }
                } catch(c:CancellationException){throw c}
                catch(e:Exception){
                    if(e.message in setOf("LOGIN_REQUIRED","ACCESS_DENIED","SESSION_CLOSED")) {
                        events.value=(listOf(MonitorAlert(profile.name,"AUTH_REQUIRED",System.currentTimeMillis()))+events.value).take(100)
                        jobs.remove(profile.id);mutable.value=mutable.value-profile.id
                        return@launch
                    }
                    if(isActive)publish(profile,false,null)
                }
                delay(30000)
                mutable.value[profile.id]?.let{mutable.value=mutable.value+(profile.id to it.copy(checking=true))}
            }
        }
    }
    private fun publish(p:RouterProfile,success:Boolean,snapshot:DashboardSnapshot?) {
        val previous=mutable.value[p.id] ?: return
        val (health,event)=previous.health.sample(SystemClock.elapsedRealtime(),snapshot?.cpuLoadPercent,success)
        mutable.value=mutable.value+(p.id to previous.copy(health=health,snapshot=snapshot ?: previous.snapshot,checking=false))
        if(event!=null)events.value=(listOf(MonitorAlert(p.name,event,System.currentTimeMillis()))+events.value).take(100)
    }
    fun stop(id:String){jobs.remove(id)?.cancel();active.remove(id)?.close();mutable.value=mutable.value-id}
    override fun close(){jobs.keys.toList().forEach(::stop);events.value=emptyList()}
}
