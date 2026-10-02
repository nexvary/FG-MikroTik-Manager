package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterRepositoryAdminTest {

    @Test
    fun createsFullAdminUserThroughRouterOsUserMenu() = runTest {
        val transport = FakeTransport()
        val repository = RouterRepository.forTesting(transport)

        val result = repository.createRouterAdmin(
            username = "fg-admin",
            password = "secret123",
            group = "full",
            comment = "FG MTM"
        )

        assertTrue(result.success)
        assertEquals("user", transport.created.single().first)
        assertEquals("fg-admin", transport.created.single().second["name"])
        assertEquals("full", transport.created.single().second["group"])
        assertEquals("secret123", transport.created.single().second["password"])
    }

    @Test
    fun updateDeleteAndToggleUseRouterItemId() = runTest {
        val transport = FakeTransport()
        val repository = RouterRepository.forTesting(transport)

        repository.updateAdminItem(
            menuPath = "ip/firewall/filter",
            rowId = "*A",
            attributes = mapOf("comment" to "updated")
        )
        repository.setAdminItemEnabled(
            menuPath = "ip/firewall/filter",
            rowId = "*A",
            enabled = false
        )
        repository.removeAdminItem(
            menuPath = "ip/firewall/filter",
            rowId = "*A"
        )

        assertEquals("/ip/firewall/filter/set", transport.executed[0].first)
        assertEquals("*A", transport.executed[0].second[".id"])
        assertEquals("/ip/firewall/filter/disable", transport.executed[1].first)
        assertEquals("/ip/firewall/filter/remove", transport.executed[2].first)
    }

    private class FakeTransport : RouterOsTransport {
        val created = mutableListOf<Pair<String, Map<String, String>>>()
        val executed = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun read(menu: String): List<Map<String, String>> =
            emptyList()

        override suspend fun create(
            menu: String,
            attributes: Map<String, String>
        ): List<Map<String, String>> {
            created += menu to attributes
            return listOf(mapOf(".id" to "*NEW"))
        }

        override suspend fun execute(
            command: String,
            attributes: Map<String, String>
        ): List<Map<String, String>> {
            executed += command to attributes
            return emptyList()
        }

        override fun close() = Unit
    }
}
