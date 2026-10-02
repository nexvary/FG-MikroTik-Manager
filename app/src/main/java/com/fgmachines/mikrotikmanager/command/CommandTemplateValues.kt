package com.fgmachines.mikrotikmanager.command

object CommandTemplateValues {
    fun fill(template: String, values: Map<String, String>): String? {
        val pattern = Regex("\\{\\{([a-z]+)\\}\\}")
        val keys = pattern.findAll(template).map { it.groupValues[1] }.toList()
        if (keys.any { values[it].isNullOrBlank() || values[it]!!.any { ch -> ch == '\n' || ch == '\r' } }) return null
        val command = pattern.replace(template) {
            "\"" + values.getValue(it.groupValues[1]).replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        return command.takeIf { RouterCommandParser.parseLine(it).supported }
    }
}
