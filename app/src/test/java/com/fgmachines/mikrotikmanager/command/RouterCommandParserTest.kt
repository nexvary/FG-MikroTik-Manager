package com.fgmachines.mikrotikmanager.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterCommandParserTest {

    @Test
    fun parsesMultipleCommandsAndQuotedValues() {
        val parsed = RouterCommandParser.parseScript(
            """
            # note
            /ip address print
            /ip hotspot user add name=user1 password="hello world" profile=default
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals("ip/address", parsed[0].menu)
        assertEquals("print", parsed[0].action)
        assertEquals(CommandRisk.SAFE, parsed[0].risk)

        assertEquals("ip/hotspot/user", parsed[1].menu)
        assertEquals("add", parsed[1].action)
        assertEquals("user1", parsed[1].attributes["name"])
        assertEquals("hello world", parsed[1].attributes["password"])
        assertEquals(CommandRisk.CHANGE, parsed[1].risk)
    }

    @Test
    fun firewallChangesAreFlaggedDangerous() {
        val parsed = RouterCommandParser.parseLine(
            "/ip firewall filter remove *A"
        )

        assertTrue(parsed.supported)
        assertEquals(CommandRisk.DANGEROUS, parsed.risk)
        assertEquals("*A", parsed.selector)
    }

    @Test
    fun unknownSyntaxIsNotExecutable() {
        val parsed = RouterCommandParser.parseLine("system reboot now")
        assertFalse(parsed.supported)
        assertEquals(CommandRisk.UNSUPPORTED, parsed.risk)
    }
}
