package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BusinessCloudHttpTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun actualHttpsClientPullsAndPushesWithoutSendingTenantInLogin()=exercise(false)
    @Test fun failedServerAcknowledgementDoesNotImportFinancialData()=exercise(true)
    @Test fun httpsOnlyAccessErrorsAndTimeoutDoNotWriteOrRetry() {
        val name="cloud-errors-${UUID.randomUUID()}"
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server=MockWebServer();server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false);server.start()
        try {BusinessStore(BusinessDatabase(context,name)).use{store->
            var factories=0
            val cloud=BusinessCloud(store){factories++;OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).callTimeout(2,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).build()}
            assertTrue(runCatching{cloud.sync("http://localhost/","owner","password")}.isFailure)
            assertTrue(runCatching{cloud.sync(server.url("/path").toString(),"owner","password")}.isFailure)
            assertEquals(0,factories)
            val before=BusinessReplica.canonical(BusinessReplica(store).snapshot())
            for(code in listOf(401,403,503)){
                server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
                val failure=runCatching{cloud.sync(server.url("/").toString(),"owner","password")}.exceptionOrNull()
                assertEquals(if(code==503)"CLOUD_REQUEST_FAILED" else "CLOUD_ACCESS_DENIED",failure?.message)
                assertNotNull(server.takeRequest(3,TimeUnit.SECONDS))
            }
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(5,TimeUnit.SECONDS))
            assertTrue(runCatching{cloud.sync(server.url("/").toString(),"owner","password")}.isFailure)
            assertNotNull(server.takeRequest(3,TimeUnit.SECONDS));assertEquals(4,server.requestCount)
            assertEquals(before,BusinessReplica.canonical(BusinessReplica(store).snapshot()))
        }}finally{server.shutdown();context.deleteDatabase(name)}
    }
    private fun exercise(fail:Boolean) {
        val a="cloud-http-a-${UUID.randomUUID()}";val b="cloud-http-b-${UUID.randomUUID()}"
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server=MockWebServer();server.useHttps(serverTls.sslSocketFactory(),false);server.start()
        try {BusinessStore(BusinessDatabase(context,a)).use{source->BusinessStore(BusinessDatabase(context,b)).use{target->
            val scope=source.defaultScope();source.addSubscriber(scope,"s","Subscriber","","HOTSPOT","user","EGP");source.post(scope,"s","c",LedgerKind.CHARGE,100,"charge")
            fun enqueue(body:JSONObject,code:Int=200){server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(body.toString()))}
            enqueue(JSONObject().put("token","abcdefghijklmnopqrstuvwxyz012345").put("expires_in",900))
            enqueue(JSONObject().put("tenant",scope.organizationId).put("branch",scope.branchId).put("role","owner"))
            enqueue(JSONObject().put("revision",1).put("records",BusinessReplica(source).snapshot()))
            enqueue(JSONObject().put("accepted",true).put("revision",1),if(fail)503 else 200)
            val cloud=BusinessCloud(target){OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()}
            val result=runCatching{cloud.sync(server.url("/").toString(),"owner","test password",true)}
            if(fail){assertTrue(result.isFailure);assertEquals(0,target.subscribers(target.defaultScope()).items.size)}
            else{assertEquals(1L,result.getOrThrow().revision);assertEquals(100L,target.balance(scope,"s"))}
            val login=server.takeRequest();assertEquals("/v1/login",login.path)
            assertEquals(setOf("username","password"),JSONObject(login.body.readUtf8()).keys().asSequence().toSet())
            assertEquals("/v1/identity",server.takeRequest().path);assertEquals("/v1/business/sync",server.takeRequest().path)
            val sent=server.takeRequest();assertEquals("POST",sent.method);assertEquals("/v1/business/sync",sent.path)
            val body=JSONObject(sent.body.readUtf8());assertEquals(1L,body.getLong("revision"));assertTrue(body.getJSONArray("records").length()>0)
            assertFalse(body.toString().contains("test password"))
        }}}finally{server.shutdown();context.deleteDatabase(a);context.deleteDatabase(b)}
    }
}
