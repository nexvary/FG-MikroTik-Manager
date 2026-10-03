package com.fgmachines.mikrotikmanager.business

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** IO stays off the main thread; UI writes are serialized and never silently retried. */
data class BusinessToolsState(
    val sales: List<BusinessSale> = emptyList(),val catalog: NetworkCatalog?=null,val networkJob: NetworkJob?=null,val receipt: String?=null,
    val busy: Boolean=false,val error: String?=null,val saved: Int=0,val message: String?=null,
    val tab: String="home",val branch: String="",val branchId: String="",val branches: List<BusinessBranch> = emptyList(),
    val plans: List<BusinessPlan> = emptyList(),val invoices: List<BusinessInvoice> = emptyList(),val expenses: List<BusinessExpense> = emptyList(),
    val audit: List<BusinessAudit> = emptyList(),val totals: List<BusinessTotals> = emptyList(),val more: Boolean=false,val page: Int=1,
    val from: String=LocalDate.now().withDayOfMonth(1).toString(),val to: String=LocalDate.now().toString(),
    val importPreview: SubscriberImportPreview?=null
)
class BusinessToolsViewModel(app: Application,private val savedState: SavedStateHandle): AndroidViewModel(app) {
    private val store=BusinessStore(BusinessDatabase(app));private val ops=BusinessOperations(store)
    var router: BusinessRouter?=null
    private val network=BusinessNetworkStore(store)
    private val transfer=BusinessTransfer(store);private var scope: BusinessScope?=null
    private val mutable=MutableStateFlow(BusinessToolsState(saved=savedState["toolsSaved"] ?: 0,tab=savedState["toolsTab"] ?: "home"))
    val state=mutable.asStateFlow();private val cursors=mutableListOf<Long>(Long.MAX_VALUE)
    private var importText: String?=null
    init { refresh() }
    private fun run(block: suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null,message=null)
        viewModelScope.launch {
            try {
                if(scope==null) withContext(Dispatchers.IO) { scope=store.defaultScope() }
                block()
            } catch(c: CancellationException) { throw c }
            catch(e: Exception) {
                val code=e.message.orEmpty()
                val known=listOf("ROUTER_REQUIRED","WRONG_ROUTER","NOT_BOUND","BINDING_CONFLICT","NETWORK_INVOICE_LOCKED","LATEST_INVOICE_REQUIRED","ROUTER_CLOCK_MISMATCH","MODERN_CLOCK_REQUIRED","PROFILE_NOT_FOUND","ACCOUNT_CHANGED","INVALID_AMOUNT","INVALID_TEXT","IDEMPOTENCY_CONFLICT","PLAN_MISMATCH","CANCEL_LATEST_FIRST","ALREADY_REVERSED","CSV_HEADER","INVALID_CSV","IMPORT_LIMIT","IMPORT_CONFLICT","EMPTY_IMPORT","FILE_TOO_LARGE","RESTORE_NEEDS_EMPTY_STORE","PASSWORD_SHORT","INVALID_DATE","DUPLICATE_BRANCH")
                mutable.value=mutable.value.copy(error=known.firstOrNull { code==it } ?: "OPERATION_FAILED")
            } finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    private suspend fun load() {
        val s=scope!!;val tab=mutable.value.tab;val before=cursors.last();val offset=(cursors.size-1)*30
        val next=withContext(Dispatchers.IO) {
            val branches=ops.branches(s)
            var result=mutable.value.copy(branches=branches,branchId=s.branchId,branch=branches.first { it.id==s.branchId }.name,page=cursors.size,more=false)
            result=when(tab) {
                "sales" -> BusinessSales(store).page(s,before).let { result.copy(sales=it.items,more=it.hasMore) }
                "plans" -> ops.plans(s,offset).let { result.copy(plans=it.items,more=it.hasMore) }
                "invoices" -> ops.invoices(s,before).let { result.copy(invoices=it.items,more=it.hasMore) }
                "expenses" -> ops.expenses(s,before).let { result.copy(expenses=it.items,more=it.hasMore) }
                "audit" -> ops.audit(s,before).let { result.copy(audit=it.items,more=it.hasMore) }
                "reports" -> dates(result.from,result.to).let { result.copy(totals=ops.totals(s,it.first,it.second)) }
                else -> result
            };result
        };mutable.value=next
    }
    private fun dates(from: String,to: String): Pair<Long,Long> = try {
        val a=LocalDate.parse(from);val b=LocalDate.parse(to);require(!a.isAfter(b))
        val zone=ZoneId.systemDefault();a.atStartOfDay(zone).toInstant().toEpochMilli() to b.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    } catch(_: Exception) { throw IllegalArgumentException("INVALID_DATE") }
    fun refresh()=run { load() }
    fun tab(tab: String)=run { savedState["toolsTab"]=tab;mutable.value=mutable.value.copy(tab=tab);cursors.clear();cursors+=Long.MAX_VALUE;load() }
    fun page(next: Boolean)=run {
        if(next && mutable.value.more) cursors+=when(mutable.value.tab) { "sales"->mutable.value.sales.last().sequence;"invoices"->mutable.value.invoices.last().sequence;"expenses"->mutable.value.expenses.last().sequence;"audit"->mutable.value.audit.last().sequence;else->Long.MAX_VALUE }
        else if(!next && cursors.size>1) cursors.removeAt(cursors.lastIndex)
        load()
    }
    private fun saved(message: String="SAVED") { val n=mutable.value.saved+1;savedState["toolsSaved"]=n;mutable.value=mutable.value.copy(saved=n,message=message) }
    fun addPlan(id: String,name: String,service: String,currency: String,price: Long,days: Int)=run { withContext(Dispatchers.IO) { ops.addPlan(scope!!,id,name,service,currency,price,days) };load();saved() }
    fun renew(id: String,sub: String,plan: String,paid: Long,method: PaymentMethod=PaymentMethod.CASH,reference: String="")=run { withContext(Dispatchers.IO) { ops.renew(scope!!,id,sub,plan,paid,method=method,reference=reference) };load();saved("RENEWED") }
    fun cancelInvoice(invoice: String,id: String,reason: String)=run { withContext(Dispatchers.IO) { ops.cancelInvoice(scope!!,invoice,id,reason) };load();saved() }
    fun expense(id: String,category: String,amount: Long,currency: String,note: String)=run { withContext(Dispatchers.IO) { ops.expense(scope!!,id,category,amount,currency,note) };load();saved() }
    fun reverseExpense(entry: String,id: String,reason: String)=run { withContext(Dispatchers.IO) { ops.reverseExpense(scope!!,entry,id,reason) };load();saved() }
    fun addBranch(id: String,name: String)=run { withContext(Dispatchers.IO) { ops.addBranch(scope!!,id,name) };load();saved() }
    fun switchBranch(id: String)=run {
        withContext(Dispatchers.IO) { ops.selectBranch(scope!!,id);scope=store.defaultScope() }
        importText=null;cursors.clear();cursors+=Long.MAX_VALUE
        mutable.value=mutable.value.copy(importPreview=null,networkJob=null,catalog=null,sales=emptyList(),plans=emptyList(),invoices=emptyList(),expenses=emptyList(),audit=emptyList(),totals=emptyList())
        load();saved("BRANCH_CHANGED")
    }
    fun report(from: String,to: String)=run {
        dates(from,to);mutable.value=mutable.value.copy(from=from,to=to,totals=emptyList());load()
    }
    fun readImport(uri: Uri)=run {
        val result=withContext(Dispatchers.IO) {
            val text=getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(BusinessBackupCipher.readBounded(it,BusinessCsv.MAX_CHARS))).toString() }
            text to transfer.preview(scope!!,text)
        };importText=result.first;mutable.value=mutable.value.copy(importPreview=result.second)
    }
    fun importSubscribers()=run {
        val text=importText ?: error("EMPTY_IMPORT")
        withContext(Dispatchers.IO) { transfer.import(scope!!,text) }
        importText=null;mutable.value=mutable.value.copy(importPreview=null);saved("IMPORTED")
    }
    fun exportCsv(uri: Uri,template: Boolean=false)=run {
        val s=scope!!;val range=dates(mutable.value.from,mutable.value.to)
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter(Charsets.UTF_8).use { if(template) it.write(BusinessTransfer.TEMPLATE) else transfer.export(s,range.first,range.second,it) } };saved("EXPORTED")
    }
    fun backup(uri: Uri,password: CharArray,restore: Boolean) {
        if(mutable.value.busy) { password.fill('\u0000');return }
        run {
            try {
                withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    if(restore) {
                        val bytes=resolver.openInputStream(uri)!!.use { BusinessBackupCipher.readBounded(it) }
                        BusinessBackup(store).restore(bytes,password);scope=store.defaultScope()
                    } else {
                        val bytes=BusinessBackup(store).export(password)
                        resolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }
                    }
                }
                importText=null;mutable.value=mutable.value.copy(importPreview=null);load();saved(if(restore) "RESTORED" else "BACKED_UP")
            } finally { password.fill('\u0000') }
        }
    }
    fun scanRouter()=run {
        mutable.value=mutable.value.copy(catalog=null)
        val r=router ?: error("ROUTER_REQUIRED")
        val catalog=withContext(Dispatchers.IO) { r.catalog() }
        mutable.value=mutable.value.copy(catalog=catalog)
    }
    fun importRouter(selected: Set<String>,currency: String)=run {
        val c=mutable.value.catalog ?: error("ROUTER_REQUIRED");val r=router ?: error("ROUTER_REQUIRED")
        withContext(Dispatchers.IO) {
            require(r.identity().first==c.fingerprint) { "WRONG_ROUTER" }
            network.importAccounts(scope!!,c,currency,c.accounts.filter { (it.service+":"+it.id) in selected })
        };saved("IMPORTED")
    }
    fun prepareNetwork(invoice: String,profile: String,megabytes: Long)=run {
        val r=router ?: error("ROUTER_REQUIRED")
        val job=withContext(Dispatchers.IO) {
            network.validateLatest(scope!!,invoice)
            network.job(scope!!,invoice) ?: run {
                val inv=ops.invoice(scope!!,invoice)!!;val b=network.binding(scope!!,inv.subscriberId) ?: error("NOT_BOUND")
                val target=r.target(b.fingerprint,b.accountId,b.account,b.service,b.id,profile,inv.end,Math.multiplyExact(megabytes,1024L*1024L))
                network.save(scope!!,invoice,target)
            }
        };mutable.value=mutable.value.copy(networkJob=job)
    }
    fun applyNetwork(suspendAccount: Boolean)=run {
        val r=router ?: error("ROUTER_REQUIRED");val job=mutable.value.networkJob ?: error("JOB_NOT_FOUND")
        withContext(Dispatchers.IO) {
            network.validateLatest(scope!!,job.invoice)
            network.result(scope!!,job.invoice,"REVIEW")
            if(suspendAccount) r.suspendAccount(job.target) else r.apply(job.target)
            network.result(scope!!,job.invoice,if(suspendAccount)"SUSPENDED" else "VERIFIED")
        }
        mutable.value=mutable.value.copy(networkJob=withContext(Dispatchers.IO) { network.job(scope!!,job.invoice) });saved("NETWORK_VERIFIED")
    }
    fun sell(id: String,sub: String,lines: List<SaleLine>,paid: Long,method: PaymentMethod,reference: String)=run {
        withContext(Dispatchers.IO) { BusinessSales(store).sell(scope!!,id,sub,lines,paid,method,reference) };load();saved()
    }
    fun cancelSale(sale: String,id: String,reason: String)=run { withContext(Dispatchers.IO) { BusinessSales(store).cancel(scope!!,sale,id,reason) };load();saved() }
    fun receipt(id: String,sale: Boolean,arabic: Boolean)=run {
        mutable.value=mutable.value.copy(receipt=withContext(Dispatchers.IO) { BusinessReceipts(store).html(scope!!,id,sale,arabic) })
    }
    fun receiptConsumed() { mutable.value=mutable.value.copy(receipt=null) }
    override fun onCleared() { store.close();super.onCleared() }
}
