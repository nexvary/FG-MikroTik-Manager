package com.fgmachines.mikrotikmanager.monitor
import org.junit.Assert.*
import org.junit.Test
class MonitorHealthTest {
    @Test fun failureNeedsConfirmationThenDeduplicatesAndResolves() {
        var health=MonitorHealth().sample(1000,20,true).first
        var result=health.sample(31000,null,false);assertNull(result.second)
        result=result.first.sample(61000,null,false);assertEquals("UNREACHABLE",result.second)
        result=result.first.sample(91000,null,false);assertNull(result.second);assertFalse(result.first.stale(91000));assertTrue(result.first.stale(91001))
        result=result.first.sample(361000,null,false);assertEquals("UNREACHABLE",result.second)
        result=result.first.sample(391000,20,true);assertEquals("RESOLVED:UNREACHABLE",result.second)
        assertNull(result.first.sample(421000,20,true).second)
    }
    @Test fun overloadRecoversAndUnknownIsNeverFresh(){
        assertTrue(MonitorHealth().stale(0))
        val high=MonitorHealth().sample(1,85,true);assertEquals("HIGH_CPU",high.second)
        assertEquals("RESOLVED:HIGH_CPU",high.first.sample(2,10,true).second)
    }
}
