package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.command.CommandExecutionStatus
import com.fgmachines.mikrotikmanager.command.RouterCommandParser
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RouterRepositoryCommandTest {

    @Test
    fun disableByServiceNameResolvesRouterIdBeforeExecution() = runTest {
        val transport = FakeTransport()
        val repository = RouterRepository.forTesting(transport)
        val command = RouterCommandParser.parseLine(
            "/ip service disable telnet"
        )

        val result = repository.executeCommand(command)

        assertEquals(CommandExecutionStatus.SUCCESS, result.status)
        assertEquals(1, transport.executed.size)
        val execution = transport.executed.single()
        assertEquals("/ip/service/disable", execution.first)
        assertEquals("*1", execution.second[".id"])
    }

    @Test
    fun printReturnsRowsWithoutMutation() = runTest {
        val transport = FakeTransport()
        val repository = RouterRepository.forTesting(transport)
        val command = RouterCommandParser.parseLine("/ip service print")

        val result = repository.executeCommand(command)

        assertEquals(CommandExecutionStatus.SUCCESS, result.status)
        assertEquals(2, result.rows.size)
        assertEquals(0, transport.executed.size)
    }

    @Test
    fun singletonSettingsDoNotRequireAnItemId() = runTest {
        val transport=FakeTransport()
        val repository=RouterRepository.forTesting(transport)
        listOf("/system identity set name=FG", "/system clock set time-zone-autodetect=no time-zone-name=Africa/Cairo", "/system ntp client set enabled=yes").forEach {
            assertEquals(CommandExecutionStatus.SUCCESS,repository.executeCommand(RouterCommandParser.parseLine(it)).status)
        }
        assertEquals(3,transport.executed.size)
        transport.executed.forEach { assertEquals(null,it.second[".id"]) }
    }

    private class FakeTransport : RouterOsTransport {
        val executed = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun read(menu: String): List<Map<String, String>> =
            when (menu) {
                "ip/service" -> listOf(
                    mapOf(".id" to "*1", "name" to "telnet", "port" to "23"),
                    mapOf(".id" to "*6", "name" to "winbox", "port" to "8291")
                )
                else -> emptyList()
            }

        override suspend fun create(
            menu: String,
            attributes: Map<String, String>
        ): List<Map<String, String>> = emptyList()

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
