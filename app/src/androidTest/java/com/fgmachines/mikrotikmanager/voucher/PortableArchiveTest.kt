package com.fgmachines.mikrotikmanager.voucher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.business.BusinessBackupCipher
import com.fgmachines.mikrotikmanager.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PortableArchiveTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun portableArchiveReplayWrongPasswordConflictAndCorruption()=runBlocking {
        val a="archive-a-${UUID.randomUUID()}";val b="archive-b-${UUID.randomUUID()}";val source=VoucherHistoryStore(context,a);val target=VoucherHistoryStore(context,b)
        val password="Portable archive password".toCharArray()
        try {
            val batch=VoucherGenerator().generate(VoucherBatchRequest(2,6,mode=VoucherMode.OFFLINE,profile=""));source.save(batch)
            val bytes=source.exportPortable(password);assertEquals(1,target.importPortable(bytes,password));assertEquals(0,target.importPortable(bytes,password));assertEquals(batch,target.recent().single().batch)
            assertTrue(runCatching{target.importPortable(bytes,"incorrect password".toCharArray())}.isFailure);assertEquals(1,target.count())
            val root=JSONObject(String(BusinessBackupCipher.decrypt(bytes,password)));val row=root.getJSONArray("batches").getJSONObject(0);val data=JSONObject(row.getString("batch"));data.getJSONArray("vouchers").getJSONObject(0).put("password","changed");row.put("batch",data.toString())
            assertTrue(runCatching{target.importPortable(BusinessBackupCipher.encrypt(root.toString().toByteArray(),password),password)}.isFailure);assertEquals(batch,target.recent().single().batch)
            val id=source.recent().single().id;context.getSharedPreferences(a,0).edit().putString("batch_"+id,"corrupt").commit()
            assertTrue(runCatching{source.exportPortable(password)}.isFailure)
        }finally{password.fill('\u0000');context.getSharedPreferences(a,0).edit().clear().commit();context.getSharedPreferences(b,0).edit().clear().commit()}
    }
    @Test fun profilesRoundTripWithoutPasswordsAndRejectDuplicateNames(){
        val name="profiles-${UUID.randomUUID()}";val store=RouterProfiles(context,name)
        try{
            val p=RouterProfile("1","Main","Cairo","192.168.88.1",8729,"admin",RouterProtocol.API_SSL)
            store.save(p);assertEquals(p,RouterProfiles(context,name).list().single());assertTrue(runCatching{store.save(p.copy(id="2"))}.isFailure)
            store.save(p.copy(id="2",branch="Damietta"));assertEquals(2,store.list().size)
            assertFalse(context.getSharedPreferences(name,0).getString("profiles","")!!.contains("password"))
            store.delete("1");assertEquals("2",store.list().single().id)
        }finally{context.getSharedPreferences(name,0).edit().clear().commit()}
    }
}
