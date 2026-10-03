package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase

/** Local personnel directory and reseller subledger; roles are descriptive, not login permissions. */
data class TeamMember(val id:String,val name:String,val phone:String,val role:String,val currency:String,val commissionBps:Int,val active:Boolean)
data class ResellerEntry(val id:String,val amount:Long,val note:String,val kind:String,val sale:String?,val reversed:Boolean)
internal object BusinessSchemaV4 {
    val tables=listOf("team_members","reseller_entries")
    fun install(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE team_members(id TEXT PRIMARY KEY NOT NULL,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,
            name TEXT NOT NULL CHECK(length(name)>0),phone TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('ADMIN','MANAGER','TECHNICIAN','CASHIER','RESELLER','READ_ONLY')),
            currency TEXT NOT NULL CHECK(currency IN ('EGP','USD','EUR','SAR','AED','TRY')),commission_bps INTEGER NOT NULL CHECK(commission_bps BETWEEN 0 AND 10000),
            active INTEGER NOT NULL CHECK(active IN (0,1)),created_at INTEGER NOT NULL,UNIQUE(organization_id,branch_id,id),
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TABLE reseller_entries(sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,
            member_id TEXT NOT NULL,kind TEXT NOT NULL CHECK(kind IN ('DEPOSIT','WITHDRAWAL','COMMISSION','REVERSAL')),amount_minor INTEGER NOT NULL CHECK(typeof(amount_minor)='integer' AND amount_minor!=0 AND abs(amount_minor)<=999999999999),
            note TEXT NOT NULL CHECK(length(note)>0),sale_id TEXT REFERENCES sales(id),reversal_of TEXT UNIQUE REFERENCES reseller_entries(id),created_at INTEGER NOT NULL,
            CHECK((kind IN ('DEPOSIT','COMMISSION') AND amount_minor>0 AND reversal_of IS NULL) OR (kind='WITHDRAWAL' AND amount_minor<0 AND reversal_of IS NULL) OR (kind='REVERSAL' AND reversal_of IS NOT NULL)),
            CHECK((kind='COMMISSION' AND sale_id IS NOT NULL) OR (kind!='COMMISSION' AND sale_id IS NULL)),
            FOREIGN KEY(organization_id,branch_id,member_id) REFERENCES team_members(organization_id,branch_id,id))""")
        db.execSQL("CREATE INDEX reseller_scope ON reseller_entries(organization_id,branch_id,member_id,sequence DESC)")
        db.execSQL("CREATE UNIQUE INDEX reseller_sale_commission ON reseller_entries(sale_id) WHERE kind='COMMISSION'")
        db.execSQL("""CREATE TRIGGER reseller_reversal BEFORE INSERT ON reseller_entries WHEN NEW.kind='REVERSAL' AND NOT EXISTS(SELECT 1 FROM reseller_entries e WHERE e.id=NEW.reversal_of AND e.member_id=NEW.member_id AND e.organization_id=NEW.organization_id AND e.branch_id=NEW.branch_id AND e.kind!='REVERSAL' AND e.amount_minor=-NEW.amount_minor) BEGIN SELECT RAISE(ABORT,'INVALID_REVERSAL'); END""")
        createGuards(db)
        db.execSQL("CREATE TRIGGER reseller_no_update BEFORE UPDATE ON reseller_entries BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        db.execSQL("CREATE TRIGGER reseller_no_delete BEFORE DELETE ON reseller_entries BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        db.execSQL("CREATE TRIGGER team_no_delete BEFORE DELETE ON team_members BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        db.execSQL("CREATE TRIGGER team_fixed BEFORE UPDATE ON team_members WHEN NEW.id!=OLD.id OR NEW.organization_id!=OLD.organization_id OR NEW.branch_id!=OLD.branch_id OR NEW.currency!=OLD.currency OR NEW.role!=OLD.role OR NEW.commission_bps!=OLD.commission_bps BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        createAudit(db)
    }
    val guards=listOf("reseller_member","reseller_nonnegative","reseller_commission_valid","sale_commission_guard")
    fun createGuards(db:SQLiteDatabase) {
        db.execSQL("""CREATE TRIGGER reseller_member BEFORE INSERT ON reseller_entries WHEN NOT EXISTS(SELECT 1 FROM team_members m WHERE m.id=NEW.member_id AND m.role='RESELLER' AND (m.active=1 OR NEW.kind='REVERSAL')) BEGIN SELECT RAISE(ABORT,'INACTIVE_RESELLER'); END""")
        db.execSQL("""CREATE TRIGGER reseller_nonnegative BEFORE INSERT ON reseller_entries WHEN NEW.amount_minor + COALESCE((SELECT SUM(amount_minor) FROM reseller_entries WHERE member_id=NEW.member_id),0)<0 BEGIN SELECT RAISE(ABORT,'INSUFFICIENT_WALLET'); END""")
        db.execSQL("""CREATE TRIGGER reseller_commission_valid BEFORE INSERT ON reseller_entries WHEN NEW.kind='COMMISSION' AND NOT EXISTS(SELECT 1 FROM sales s JOIN team_members m ON m.id=NEW.member_id WHERE s.id=NEW.sale_id AND s.organization_id=NEW.organization_id AND s.branch_id=NEW.branch_id AND s.currency=m.currency AND NEW.amount_minor=(s.paid_minor*m.commission_bps)/10000 AND NOT EXISTS(SELECT 1 FROM sale_voids v WHERE v.sale_id=s.id)) BEGIN SELECT RAISE(ABORT,'INVALID_COMMISSION'); END""")
        db.execSQL("""CREATE TRIGGER sale_commission_guard BEFORE INSERT ON sale_voids WHEN EXISTS(SELECT 1 FROM reseller_entries e WHERE e.sale_id=NEW.sale_id AND NOT EXISTS(SELECT 1 FROM reseller_entries r WHERE r.reversal_of=e.id)) BEGIN SELECT RAISE(ABORT,'REVERSE_COMMISSION_FIRST'); END""")
    }
    fun createAudit(db:SQLiteDatabase) {
        for(t in tables) db.execSQL("""CREATE TRIGGER audit_$t AFTER INSERT ON $t BEGIN INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(NEW.organization_id,NEW.branch_id,'$t',NEW.id,'CREATE',NEW.created_at); END""")
        db.execSQL("""CREATE TRIGGER audit_team_update AFTER UPDATE ON team_members BEGIN INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(NEW.organization_id,NEW.branch_id,'team_members',NEW.id,CASE WHEN NEW.active=1 THEN 'ENABLE' ELSE 'DISABLE' END,CAST(strftime('%s','now') AS INTEGER)*1000); END""")
    }
}
class BusinessTeam(private val store:BusinessStore) {
    private fun <T> walletRead(s:BusinessScope,member:String,work:()->T):T {
        if(!store.identity.ownWallet(s,member))store.authorize(s,BusinessPermission.TEAM)
        return work()
    }
    fun members(s:BusinessScope):List<TeamMember> {
        val principal=store.identity.principal()
        if(principal?.role!="RESELLER")store.authorize(s,BusinessPermission.TEAM)
        else require(principal.scope==s){"ACCESS_DENIED"}
        return store.helper.readableDatabase.rawQuery("SELECT id,name,phone,role,currency,commission_bps,active FROM team_members WHERE organization_id=? AND branch_id=? ORDER BY name COLLATE NOCASE,id LIMIT 1000",arrayOf(s.organizationId,s.branchId)).use { c->buildList { while(c.moveToNext()) add(TeamMember(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getInt(6)==1)) } }.filter{principal?.role!="RESELLER" || it.id==principal.member}
    }
    fun add(s:BusinessScope,id:String,name:String,phone:String,role:String,currency:String,bps:Int) = store.transaction { db->
        store.authorize(s,BusinessPermission.TEAM)

        val n=businessText(name,120,true);val p=businessText(phone,40);require(bps in 0..10000);require(id.isNotBlank() && id.length<=80)
        val old=members(s).firstOrNull { it.id==id }
        if(old!=null) { require(old.name==n && old.phone==p && old.role==role && old.currency==currency && old.commissionBps==bps){"IDEMPOTENCY_CONFLICT"} }
        else { require(members(s).size<1000){"TEAM_LIMIT"};db.execSQL("INSERT INTO team_members VALUES(?,?,?,?,?,?,?,?,1,?)",arrayOf(id,s.organizationId,s.branchId,n,p,role,currency,bps,System.currentTimeMillis())) }
    }
    fun activate(s:BusinessScope,id:String,active:Boolean) = store.transaction { db->
        store.authorize(s,BusinessPermission.TEAM)

        require(members(s).any { it.id==id }){"MEMBER_NOT_FOUND"}
        db.execSQL("UPDATE team_members SET active=? WHERE organization_id=? AND branch_id=? AND id=?",arrayOf(if(active)1 else 0,s.organizationId,s.branchId,id))
    }
    fun balance(s:BusinessScope,member:String):Long =walletRead(s,member) {  store.helper.readableDatabase.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM reseller_entries WHERE organization_id=? AND branch_id=? AND member_id=?",arrayOf(s.organizationId,s.branchId,member)).use { it.moveToFirst();it.getLong(0) } }
    fun entries(s:BusinessScope,member:String):List<ResellerEntry> =walletRead(s,member) {  store.helper.readableDatabase.rawQuery("SELECT e.id,e.amount_minor,e.note,e.kind,e.sale_id,EXISTS(SELECT 1 FROM reseller_entries r WHERE r.reversal_of=e.id) FROM reseller_entries e WHERE e.organization_id=? AND e.branch_id=? AND e.member_id=? ORDER BY sequence DESC LIMIT 100",arrayOf(s.organizationId,s.branchId,member)).use { c->buildList { while(c.moveToNext())add(ResellerEntry(c.getString(0),c.getLong(1),c.getString(2),c.getString(3),if(c.isNull(4))null else c.getString(4),c.getInt(5)==1)) } } }
    fun post(s:BusinessScope,member:String,id:String,kind:String,amount:Long,note:String,sale:String?=null,reversal:String?=null) = store.transaction { db->
        store.authorize(s,BusinessPermission.WALLET)

        require(id.isNotBlank() && id.length<=80);val n=businessText(note,500,true)
        val m=members(s).firstOrNull { it.id==member } ?: error("MEMBER_NOT_FOUND")
        val value=when(kind) {
            "DEPOSIT" -> { require(amount in 1..BusinessMoney.MAX_MINOR);amount }
            "WITHDRAWAL" -> { require(amount in 1..BusinessMoney.MAX_MINOR);-amount }
            "COMMISSION" -> { val v=BusinessSales(store).sale(s,sale ?: error("SALE_REQUIRED")) ?: error("SALE_REQUIRED");require(v.currency==m.currency && !v.voided){"INVALID_COMMISSION"};Math.multiplyExact(v.paid,m.commissionBps.toLong())/10000 }
            "REVERSAL" -> db.rawQuery("SELECT amount_minor FROM reseller_entries WHERE id=? AND member_id=? AND kind!='REVERSAL'",arrayOf(reversal,member)).use { require(it.moveToFirst()){"ENTRY_NOT_FOUND"};-it.getLong(0) }
            else -> error("INVALID_KIND")
        }
        require(value!=0L && kotlin.math.abs(value)<=BusinessMoney.MAX_MINOR){"INVALID_AMOUNT"}
        db.rawQuery("SELECT member_id,kind,amount_minor,note,sale_id,reversal_of FROM reseller_entries WHERE id=?",arrayOf(id)).use { c->if(c.moveToFirst()) { require(c.getString(0)==member && c.getString(1)==kind && c.getLong(2)==value && c.getString(3)==n && c.getString(4)==sale && c.getString(5)==reversal){"IDEMPOTENCY_CONFLICT"};return@transaction } }
        require(Math.addExact(balance(s,member),value) in 0..BusinessMoney.MAX_MINOR){"INSUFFICIENT_WALLET"}
        db.execSQL("INSERT INTO reseller_entries(id,organization_id,branch_id,member_id,kind,amount_minor,note,sale_id,reversal_of,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",arrayOf(id,s.organizationId,s.branchId,member,kind,value,n,sale,reversal,System.currentTimeMillis()))
    }
}
