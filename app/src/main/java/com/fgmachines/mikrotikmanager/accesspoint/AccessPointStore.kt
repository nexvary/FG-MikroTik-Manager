package com.fgmachines.mikrotikmanager.accesspoint

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Independent append-only observations; no credentials, full API rows, or voucher passwords. */
class AccessPointStore(context:Context,private val routerKey:String):SQLiteOpenHelper(context,"fg_access_points.db",null,1) {
    private val business=com.fgmachines.mikrotikmanager.business.BusinessStore(com.fgmachines.mikrotikmanager.business.BusinessDatabase(context))
    private val scope:String get() {
        business.authorize(null,com.fgmachines.mikrotikmanager.business.BusinessPermission.ROUTER)
        val selected=business.defaultScope()
        return selected.organizationId+"|"+selected.branchId+"|"+routerKey
    }
    override fun close(){super.close();business.close()}
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE mappings(scope TEXT,mac TEXT,body TEXT NOT NULL,PRIMARY KEY(scope,mac))")
        db.execSQL("CREATE TABLE observations(scope TEXT,at INTEGER,kind TEXT,identity TEXT,body TEXT NOT NULL,PRIMARY KEY(scope,at,kind,identity))")
        db.execSQL("CREATE INDEX observation_period ON observations(scope,at)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    fun mappings():Map<String,ApRow> {
        val result=linkedMapOf<String,ApRow>()
        readableDatabase.rawQuery("SELECT mac,body FROM mappings WHERE scope=?",arrayOf(scope)).use{c->while(c.moveToNext())result[c.getString(0)]=decode(c.getString(1))}
        return result
    }
    fun map(mac:String,shop:String,confirmed:Boolean,mode:String) {
        require(AccessPointEngine.mac(mac)==mac && shop.length<=120 && mode in listOf("Unknown","Bridge","NAT"))
        val body=mapOf("shop" to shop.trim(),"confirmed" to confirmed.toString(),"mode" to mode)
        writableDatabase.execSQL("INSERT OR REPLACE INTO mappings VALUES (?,?,?)",arrayOf(scope,mac,JSONObject(body).toString()))
    }
    fun save(snapshot:ApSnapshot) {
        val db=writableDatabase;db.beginTransaction()
        try {
            snapshot.devices.forEach {insert(db,snapshot.at,"device",it["mac"].orEmpty(),it)}
            snapshot.sessions.forEachIndexed{i,row->insert(db,snapshot.at,"session",row["id"].orEmpty().ifBlank{"row:$i"},row)}
            insert(db,snapshot.at,"coverage","poll",mapOf("warnings" to snapshot.warnings.joinToString(", ")))
            db.execSQL("DELETE FROM observations WHERE scope=? AND at<?",arrayOf(scope,snapshot.at-90L*86400000))
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    private fun insert(db:SQLiteDatabase,at:Long,kind:String,id:String,row:ApRow) {
        db.execSQL("INSERT OR IGNORE INTO observations VALUES (?,?,?,?,?)",arrayOf(scope,at,kind,id,JSONObject(row).toString()))
    }
    fun records(from:Long,until:Long):List<ApRow> {
        val result=mutableListOf<ApRow>()
        readableDatabase.rawQuery("SELECT at,kind,body FROM observations WHERE scope=? AND at>=? AND at<? ORDER BY at",arrayOf(scope,from.toString(),until.toString())).use{c->while(c.moveToNext()) result+=decode(c.getString(2))+mapOf("at" to c.getLong(0).toString(),"kind" to c.getString(1))}
        return result
    }
    companion object {
        fun decode(value:String):ApRow=JSONObject(value).let{json->json.keys().asSequence().associateWith{json.getString(it)}}
        private fun uptimeSeconds(value:String):Long {
            val source=value.trim().lowercase()
            val matches=Regex("(\\d+)([wdhms])").findAll(source).toList()
            if(matches.isEmpty() || matches.sumOf{it.value.length}!=source.length)return 0
            return matches.fold(0L){total,m->
                val n=m.groupValues[1].toLongOrNull()?:return 0
                val factor=when(m.groupValues[2]){"w"->604800L;"d"->86400L;"h"->3600L;"m"->60L;else->1L}
                if(n>Long.MAX_VALUE/factor || total>Long.MAX_VALUE-n*factor)return 0
                total+n*factor
            }
        }
        fun summary(records:List<ApRow>,devices:List<ApRow>):List<ApRow> {
            val zone=java.time.ZoneId.systemDefault()
            val rows=devices.filter{it["classification"] in listOf("Confirmed AP","Likely AP")}.map{device->
                val samples=records.filter{it["kind"]=="session" && it["ap"]==device["mac"]}
                val clients=samples.mapNotNull{it["client"]?.takeIf(String::isNotBlank)}.toSet()
                val accounts=samples.mapNotNull{it["account"]?.takeIf(String::isNotBlank)}.toSet()
                val sessions=samples.groupBy{it["id"].orEmpty().ifBlank{listOf(it["account"],it["client"],it["server"]).joinToString("|")}}
                var upload=java.math.BigInteger.ZERO
                var download=java.math.BigInteger.ZERO
                var duration=0L
                val plans=mutableMapOf<String,Int>()
                sessions.values.forEach{group->
                    upload+=group.mapNotNull{it["upload"]?.toBigIntegerOrNull()}.maxOrNull()?:java.math.BigInteger.ZERO
                    download+=group.mapNotNull{it["download"]?.toBigIntegerOrNull()}.maxOrNull()?:java.math.BigInteger.ZERO
                    val maxDuration=group.maxOfOrNull{uptimeSeconds(it["uptime"].orEmpty())}?:0L
                    if(duration<=Long.MAX_VALUE-maxDuration)duration+=maxDuration
                    group.lastOrNull{!it["profile"].isNullOrBlank()}?.get("profile")?.let{plans[it]=(plans[it]?:0)+1}
                }
                val hours=mutableMapOf<String,Int>()
                val days=mutableMapOf<String,Int>()
                samples.forEach{sample->sample["at"]?.toLongOrNull()?.let{at->
                    val dt=java.time.Instant.ofEpochMilli(at).atZone(zone)
                    val hour="%02d:00".format(java.util.Locale.ROOT,dt.hour)
                    hours[hour]=(hours[hour]?:0)+1
                    val day=dt.toLocalDate().toString()
                    days[day]=(days[day]?:0)+1
                }}
                fun peak(values:Map<String,Int>)=values.maxByOrNull{it.value}?.key.orEmpty()
                val total=upload+download
                device+mapOf(
                    "observedClients" to clients.size.toString(),"observedAccounts" to accounts.size.toString(),"sessionSamples" to samples.size.toString(),"observedSessions" to sessions.size.toString(),
                    "uploadBytes" to upload.toString(),"downloadBytes" to download.toString(),"totalTrafficBytes" to total.toString(),
                    "averageTrafficPerClientBytes" to if(clients.isEmpty())"N/A" else (total/java.math.BigInteger.valueOf(clients.size.toLong())).toString(),
                    "averageSessionSeconds" to if(sessions.isEmpty())"N/A" else (duration/sessions.size).toString(),
                    "peakObservedHour" to peak(hours),"peakObservedDay" to peak(days),"topObservedPlan" to peak(plans).ifBlank{"N/A"},
                    "sales" to "N/A","revenue" to "N/A","cards" to "N/A",
                    "accountEvidence" to "Observed HotSpot account IDs; not confirmed sold vouchers",
                    "salesReason" to "No verified sales-to-session attribution","quality" to "Inferred • sampled")
            }.sortedWith(compareByDescending<ApRow>{it["observedAccounts"]?.toIntOrNull()?:0}.thenByDescending{it["observedClients"]?.toIntOrNull()?:0})
            return rows.mapIndexed{i,row->row+("rankByObservedAccounts" to (i+1).toString())}
        }
    }
}
