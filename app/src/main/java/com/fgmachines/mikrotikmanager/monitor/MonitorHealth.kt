package com.fgmachines.mikrotikmanager.monitor

data class MonitorHealth(val lastSuccess:Long?=null,val failures:Int=0,val incident:String?=null,val lastAlert:Long?=null) {
    fun stale(now:Long)=lastSuccess==null || now-lastSuccess>90000
    fun sample(now:Long,cpu:Int?,success:Boolean):Pair<MonitorHealth,String?> {
        val count=if(success)0 else failures+1
        val next=when {count>=2->"UNREACHABLE";!success->incident;cpu!=null && cpu>=85->"HIGH_CPU";else->null}
        val changed=next!=incident
        val repeat=next!=null && lastAlert!=null && now-lastAlert>=300000
        val event=when { changed && next==null && incident!=null->"RESOLVED:$incident";changed && next!=null->next;repeat->next;else->null }
        return copy(lastSuccess=if(success)now else lastSuccess,failures=count,incident=next,lastAlert=if(event!=null)now else lastAlert) to event
    }
}
