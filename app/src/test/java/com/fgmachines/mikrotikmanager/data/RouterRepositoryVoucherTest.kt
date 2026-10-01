package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherBatchRequest
import com.fgmachines.mikrotikmanager.voucher.VoucherDraft
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterRepositoryVoucherTest {

    @Test
    fun skipsDuplicateAndCreatesOnlyNewHotspotVoucher() = runTest {
        val transport = FakeTransport()
        val repository = RouterRepository.forTesting(transport)

        val batch = VoucherBatch(
            request = VoucherBatchRequest(
                quantity = 2,
                usernameLength = 6,
                profile = "5M"
            ),
            vouchers = listOf(
                voucher("existing"),
                voucher("new001")
            )
        )

        val result = repository.provisionVoucherBatch(batch)

        assertEquals(1, result.created)
        assertEquals(1, result.duplicates)
        assertEquals(0, result.failed)
        assertEquals(1, transport.created.size)
        assertEquals("ip/hotspot/user", transport.created.single().first)
        assertEquals("new001", transport.created.single().second["name"])
        assertTrue(transport.created.single().second.containsKey("limit-uptime"))
    }

    private fun voucher(username: String) =
        VoucherDraft(
            username = username,
            password = username,
            profile = "5M",
            server = "all",
            comment = "batch-test",
            limitUptime = "1d",
            limitBytesTotal = 1024,
            mode = VoucherMode.HOTSPOT
        )

    private class FakeTransport : RouterOsTransport {
        val created = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun read(menu: String): List<Map<String, String>> =
            when (menu) {
                "ip/hotspot/user" -> listOf(mapOf("name" to "existing"))
                else -> emptyList()
            }

        override suspend fun create(
            menu: String,
            attributes: Map<String, String>
        ): List<Map<String, String>> {
            created += menu to attributes
            return listOf(mapOf(".id" to "*A"))
        }

        override suspend fun execute(
            command: String,
            attributes: Map<String, String>
        ): List<Map<String, String>> = emptyList()

        override fun close() = Unit
    }
}
