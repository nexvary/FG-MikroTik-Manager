package com.fgmachines.mikrotikmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RouterProfile(val id:String,val name:String,val branch:String,val host:String,val port:Int,val username:String,val protocol:RouterProtocol)
/** Connection metadata only: never persist a password, token or a claim that a router is online. */
class RouterProfiles(context:Context,name:String="fg_router_profiles") {
    private val appContext=context.applicationContext
    private fun authorize() {
        com.fgmachines.mikrotikmanager.business.BusinessStore(com.fgmachines.mikrotikmanager.business.BusinessDatabase(appContext)).use { it.authorize(null,com.fgmachines.mikrotikmanager.business.BusinessPermission.ROUTER) }
    }
    private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    fun list():List<RouterProfile> = synchronized(lock) {
        authorize()
        val rows=JSONArray(prefs.getString("profiles","[]"))
        buildList { for(i in 0 until rows.length()) { val r=rows.getJSONObject(i);add(RouterProfile(r.getString("id"),r.getString("name"),r.getString("branch"),r.getString("host"),r.getInt("port"),r.getString("username"),RouterProtocol.valueOf(r.getString("protocol")))) } }.sortedWith(compareBy({it.branch},{it.name}))
    }
    fun save(profile:RouterProfile) = synchronized(lock) {
        authorize()
        require(profile.id.isNotBlank() && profile.id.length<=80)
        require(profile.name.trim().length in 1..80 && profile.branch.length<=80)
        require(profile.host.length in 1..253 && profile.host.matches(Regex("[A-Za-z0-9._:\\[\\]-]+"))) { "INVALID_HOST" }
        RouterConnectionSettings(profile.host,profile.port,profile.username,"",profile.protocol)
        require(profile.username.length<=120)
        val records=list().toMutableList();val i=records.indexOfFirst { it.id==profile.id }
        require(records.none { it.id!=profile.id && it.name.equals(profile.name.trim(),true) && it.branch.equals(profile.branch.trim(),true) }) { "DUPLICATE_PROFILE" }
        val clean=profile.copy(name=profile.name.trim(),branch=profile.branch.trim(),username=profile.username.trim())
        if(i<0){require(records.size<500){"PROFILE_LIMIT"};records+=clean}else records[i]=clean
        write(records)
    }
    fun delete(id:String) = synchronized(lock) { authorize();write(list().filterNot{it.id==id}) }
    private fun write(rows:List<RouterProfile>) {
        val out=JSONArray();rows.forEach { r->out.put(JSONObject().put("id",r.id).put("name",r.name).put("branch",r.branch).put("host",r.host).put("port",r.port).put("username",r.username).put("protocol",r.protocol.name)) }
        check(prefs.edit().putString("profiles",out.toString()).commit()) { "PROFILE_WRITE_FAILED" }
    }
    private companion object { val lock=Any() }
}
