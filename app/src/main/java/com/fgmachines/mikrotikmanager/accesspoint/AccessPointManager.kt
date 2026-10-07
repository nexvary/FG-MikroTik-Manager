package com.fgmachines.mikrotikmanager.accesspoint

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class ApSnapshot(val at:Long,val devices:List<ApRow>,val sessions:List<ApRow>,val warnings:List<String>,val data:Map<String,List<ApRow>>)
class AccessPointManager(private val transport:RouterOsTransport,val routerKey:String) {
    private val mutex=Mutex()
    private var cached:ApSnapshot?=null
    suspend fun load(mappings:Map<String,ApRow>):ApSnapshot=mutex.withLock {
        val now=System.currentTimeMillis()
        cached?.takeIf{now-it.at in 0..29999}?.let {
            val devices=AccessPointEngine.discover(it.data,mappings)
            return@withLock it.copy(devices=devices,sessions=AccessPointEngine.correlate(it.data,devices))
        }
        val data=linkedMapOf<String,List<ApRow>>();val warnings=mutableListOf<String>()
        for(menu in AccessPointEngine.menus) {
            try { data[menu]=withTimeout(12000){transport.read(menu)} }
            catch(_:kotlinx.coroutines.TimeoutCancellationException){warnings+=menu}
            catch(c:CancellationException){throw c}
            catch(_:Exception){warnings+=menu}
        }
        check(AccessPointEngine.menus.take(4).any{it in data}){"Access point discovery unavailable"}
        val devices=AccessPointEngine.discover(data,mappings)
        ApSnapshot(System.currentTimeMillis(),devices,AccessPointEngine.correlate(data,devices),warnings,data).also{cached=it}
    }
}
