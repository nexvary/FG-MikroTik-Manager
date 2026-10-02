package com.fgmachines.mikrotikmanager.business

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BusinessState(
    val busy: Boolean = false, val error: String? = null, val query: String = "",
    val subscribers: List<Subscriber> = emptyList(), val moreSubscribers: Boolean = false, val subscriberPage: Int = 1,
    val selected: Subscriber? = null, val entries: List<LedgerEntry> = emptyList(),
    val moreEntries: Boolean = false, val ledgerPage: Int = 1, val balance: Long? = null, val saved: Int = 0
)
class BusinessViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val store=BusinessStore(BusinessDatabase(application))
    private var scope: BusinessScope? = null
    private val mutable=MutableStateFlow(BusinessState())
    val state=mutable.asStateFlow()
    private val subscriberCursors=mutableListOf<Pair<String,String>?>(null)
    private val ledgerCursors=mutableListOf<Long?>(null)
    init {
        val selectedId=savedState.get<String>("subscriber")
        if(selectedId==null) search(savedState.get<String>("query") ?: "") else select(selectedId)
    }
    private fun run(action: suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try { if(scope==null) scope=withContext(Dispatchers.IO) { store.defaultScope() }; action() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { mutable.value=mutable.value.copy(error=when(e.message) {
                "INVALID_AMOUNT", "INVALID_TEXT", "IDEMPOTENCY_CONFLICT", "ALREADY_REVERSED", "SUBSCRIBER_NOT_FOUND" -> e.message
                else -> "SAVE_OR_LOAD_FAILED"
            }) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    private suspend fun loadSubscribers() {
        val cursor=subscriberCursors.last(); val query=mutable.value.query
        val page=withContext(Dispatchers.IO) { store.subscribers(scope!!,query,cursor?.first,cursor?.second) }
        mutable.value=mutable.value.copy(subscribers=page.items,moreSubscribers=page.hasMore,subscriberPage=subscriberCursors.size)
    }
    fun search(query: String) = run {
        subscriberCursors.clear(); subscriberCursors.add(null)
        savedState["query"]=query.trim().take(120); savedState.remove<String>("subscriber")
        mutable.value=mutable.value.copy(query=query.trim().take(120),selected=null)
        loadSubscribers()
    }
    fun subscriberPage(next: Boolean) = run {
        if(next) { if(!mutable.value.moreSubscribers) return@run
            mutable.value.subscribers.lastOrNull()?.let { subscriberCursors.add(it.name to it.id) }
        } else if(subscriberCursors.size>1) subscriberCursors.removeAt(subscriberCursors.lastIndex)
        loadSubscribers()
    }
    fun select(id: String) = run {
        val sub=withContext(Dispatchers.IO) { store.subscriber(scope!!,id) }
        savedState["subscriber"]=sub.id
        ledgerCursors.clear(); ledgerCursors.add(null)
        // Clear the previous subscriber's financial state before loading the new one.
        mutable.value=mutable.value.copy(selected=sub,entries=emptyList(),balance=null,moreEntries=false,ledgerPage=1)
        loadLedger()
    }
    fun back() = run { savedState.remove<String>("subscriber"); mutable.value=mutable.value.copy(selected=null); loadSubscribers() }
    private suspend fun loadLedger() {
        val id=mutable.value.selected!!.id
        mutable.value=mutable.value.copy(balance=null)
        val result=withContext(Dispatchers.IO) { store.ledger(scope!!,id,ledgerCursors.last()) to store.balance(scope!!,id) }
        mutable.value=mutable.value.copy(entries=result.first.items,moreEntries=result.first.hasMore,balance=result.second,ledgerPage=ledgerCursors.size)
    }
    fun refresh() = run { if(mutable.value.selected==null) loadSubscribers() else loadLedger() }
    fun ledgerPage(next: Boolean) = run {
        if(next) { if(!mutable.value.moreEntries) return@run; mutable.value.entries.lastOrNull()?.let { ledgerCursors.add(it.sequence) } }
        else if(ledgerCursors.size>1) ledgerCursors.removeAt(ledgerCursors.lastIndex)
        loadLedger()
    }
    fun addSubscriber(id: String,name: String,phone: String,service: String,account: String,currency: String) = run {
        val sub=withContext(Dispatchers.IO) { store.addSubscriber(scope!!,id,name,phone,service,account,currency) }
        savedState["subscriber"]=sub.id
        mutable.value=mutable.value.copy(selected=sub,entries=emptyList(),balance=null)
        ledgerCursors.clear(); ledgerCursors.add(null); loadLedger()
        mutable.value=mutable.value.copy(saved=mutable.value.saved+1)
    }
    fun post(id: String,kind: LedgerKind,amount: Long,note: String) = run {
        val sub=mutable.value.selected!!.id
        withContext(Dispatchers.IO) { store.post(scope!!,sub,id,kind,amount,note) }
        ledgerCursors.clear(); ledgerCursors.add(null); loadLedger()
        mutable.value=mutable.value.copy(saved=mutable.value.saved+1)
    }
    fun reverse(entry: String,id: String,reason: String) = run {
        val sub=mutable.value.selected!!.id
        withContext(Dispatchers.IO) { store.reverse(scope!!,sub,entry,id,reason) }
        ledgerCursors.clear(); ledgerCursors.add(null); loadLedger()
        mutable.value=mutable.value.copy(saved=mutable.value.saved+1)
    }
    override fun onCleared() { store.close(); super.onCleared() }
}
