package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BusinessAuditDetailsTest {
    @Test fun snapshotsAreImmutableAndPortableWithoutReattributingOldEvents() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first="audit-${UUID.randomUUID()}.db";val second="audit-${UUID.randomUUID()}.db"
        val source=BusinessStore(BusinessDatabase(context,first));val target=BusinessStore(BusinessDatabase(context,second))
        try {
            val scope=source.defaultScope();val team=BusinessTeam(source)
            team.add(scope,"staff","Audit staff","","RESELLER","EGP",0)
            source.helper.writableDatabase.execSQL("UPDATE team_members SET active=0 WHERE id='staff'")
            val event=BusinessOperations(source).audit(scope).items.first()
            assertNotNull(event.device);assertTrue(event.before!!.contains("active=1"));assertTrue(event.after!!.contains("active=0"))
            assertTrue(runCatching { source.helper.writableDatabase.execSQL("UPDATE audit_details SET device='forged'") }.isFailure)
            val password="Portable audit password".toCharArray()
            BusinessBackup(target).restore(BusinessBackup(source).export(password),password)
            val restored=BusinessOperations(target).audit(target.defaultScope()).items.first { it.sequence==event.sequence }
            assertEquals(event,restored)
            BusinessTeam(target).add(target.defaultScope(),"second","Second staff","","RESELLER","EGP",0)
            val newEvent=BusinessOperations(target).audit(target.defaultScope()).items.first()
            assertNotEquals(event.device,newEvent.device)
            assertFalse(newEvent.after!!.contains("verifier"))
        } finally {source.close();target.close();context.deleteDatabase(first);context.deleteDatabase(second)}
    }
}
