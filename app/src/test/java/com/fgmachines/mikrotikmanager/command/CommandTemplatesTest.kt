package com.fgmachines.mikrotikmanager.command

import org.junit.Assert.*
import org.junit.Test

class CommandTemplatesTest {
    @Test fun libraryHasUniqueSupportedCommandsAndMatchingRisk() {
        assertTrue(CommandTemplates.items.size >= 130)
        assertEquals(CommandTemplates.items.size, CommandTemplates.items.map { it.command }.distinct().size)
        CommandTemplates.items.forEach { template ->
            val keys = Regex("\\{\\{([a-z]+)\\}\\}").findAll(template.command).map { it.groupValues[1] }.toList()
            val values = keys.associateWith { if(it == "id") "*A" else "test" }
            val command = CommandTemplateValues.fill(template.command,values)
            assertNotNull(template.command,command)
            val parsed = RouterCommandParser.parseLine(command!!)
            assertEquals(command, template.risk,parsed.risk)
            assertTrue(template.descriptionAr.isNotBlank())
            assertTrue(template.descriptionEn.isNotBlank())
        }
    }
    @Test fun parametersCannotBecomeAdditionalCommands() {
        val template="/ip hotspot user set {{user}} profile={{profile}}"
        assertNull(CommandTemplateValues.fill(template,mapOf("user" to "abc")))
        assertNull(CommandTemplateValues.fill(template,mapOf("user" to "abc\n/system reboot", "profile" to "default")))
        val command=CommandTemplateValues.fill(template,mapOf("user" to "abc\"; /system reboot", "profile" to "plan with spaces"))!!
        val parsed=RouterCommandParser.parseLine(command)
        assertEquals("abc\"; /system reboot",parsed.selector)
        assertEquals("plan with spaces",parsed.attributes["profile"])
        assertEquals(1,RouterCommandParser.parseScript(command).size)
    }
    @Test fun sensitiveChangesRequireConfirmation() {
        listOf("/ip service disable telnet", "/ip address set *A address=192.168.1.1/24", "/interface bridge port remove *A").forEach {
            assertEquals(CommandRisk.DANGEROUS, RouterCommandParser.parseLine(it).risk)
        }
    }
}
