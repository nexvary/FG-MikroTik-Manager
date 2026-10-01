package com.fgmachines.mikrotikmanager.command

object RouterCommandParser {
    private val actions = setOf(
        "print", "add", "set", "remove", "enable", "disable"
    )

    fun parseScript(text: String): List<ParsedRouterCommand> =
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .map(::parseLine)
            .toList()

    fun parseLine(line: String): ParsedRouterCommand {
        val words = tokenize(line.trim())
        if (words.isEmpty() || !words.first().startsWith("/")) {
            return unsupported(line, "صيغة الأمر غير معروفة", "Unsupported command syntax")
        }

        val actionIndex = words.indexOfFirst { it.lowercase() in actions }
        if (actionIndex <= 0) {
            return unsupported(
                line,
                "لم أجد عملية واضحة مثل print أو add أو set أو disable",
                "No supported action such as print, add, set or disable was found"
            )
        }

        val menu = words.subList(0, actionIndex)
            .joinToString("/") { it.trim('/') }
            .trim('/')

        val action = words[actionIndex].lowercase()
        val tail = words.drop(actionIndex + 1)
        val attributes = linkedMapOf<String, String>()
        var selector: String? = null

        tail.forEach { token ->
            when {
                token.contains("=") -> {
                    val split = token.indexOf('=')
                    if (split > 0) {
                        attributes[token.substring(0, split)] = token.substring(split + 1)
                    }
                }
                selector == null -> selector = token
            }
        }

        val risk = classify(menu, action)
        val supported = menu.isNotBlank() && action in actions

        return ParsedRouterCommand(
            original = line,
            menu = menu,
            action = action,
            attributes = attributes,
            selector = selector,
            risk = risk,
            explanationAr = explain(menu, action, selector, true),
            explanationEn = explain(menu, action, selector, false),
            supported = supported
        )
    }

    fun tokenize(input: String): List<String> {
        val output = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        fun flush() {
            if (current.isNotEmpty()) {
                output += current.toString()
                current.clear()
            }
        }

        input.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == '\\' -> escaping = true
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '"' || char == '\'' -> quote = char
                char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }
        flush()
        return output
    }

    private fun classify(menu: String, action: String): CommandRisk {
        if (action == "print") return CommandRisk.SAFE

        val dangerousMenu = menu.startsWith("system/reset") ||
            menu.startsWith("system/routerboard") ||
            menu.startsWith("partition") ||
            menu.startsWith("disk") ||
            menu.startsWith("ip/firewall")

        if (action == "remove" || dangerousMenu) return CommandRisk.DANGEROUS
        return CommandRisk.CHANGE
    }

    private fun explain(
        menu: String,
        action: String,
        selector: String?,
        arabic: Boolean
    ): String {
        val subject = selector?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return if (arabic) {
            when (action) {
                "print" -> "يعرض بيانات /$menu بدون تعديل."
                "add" -> "سيضيف عنصرًا جديدًا داخل /$menu."
                "set" -> "سيعدّل العنصر$subject داخل /$menu."
                "enable" -> "سيُفعّل العنصر$subject داخل /$menu."
                "disable" -> "سيُعطّل العنصر$subject داخل /$menu."
                "remove" -> "سيحذف العنصر$subject من /$menu."
                else -> "أمر RouterOS داخل /$menu."
            }
        } else {
            when (action) {
                "print" -> "Reads /$menu without making changes."
                "add" -> "Adds a new item under /$menu."
                "set" -> "Changes$subject under /$menu."
                "enable" -> "Enables$subject under /$menu."
                "disable" -> "Disables$subject under /$menu."
                "remove" -> "Removes$subject from /$menu."
                else -> "RouterOS command under /$menu."
            }
        }
    }

    private fun unsupported(
        line: String,
        ar: String,
        en: String
    ) = ParsedRouterCommand(
        original = line,
        menu = "",
        action = "",
        attributes = emptyMap(),
        selector = null,
        risk = CommandRisk.UNSUPPORTED,
        explanationAr = ar,
        explanationEn = en,
        supported = false
    )
}

object CommandTemplates {
    val items = listOf(
        CommandTemplate(
            titleAr = "عرض عناوين IP",
            titleEn = "Show IP addresses",
            command = "/ip address print",
            risk = CommandRisk.SAFE
        ),
        CommandTemplate(
            titleAr = "عرض خدمات الراوتر",
            titleEn = "Show router services",
            command = "/ip service print",
            risk = CommandRisk.SAFE
        ),
        CommandTemplate(
            titleAr = "عرض DHCP",
            titleEn = "Show DHCP servers",
            command = "/ip dhcp-server print",
            risk = CommandRisk.SAFE
        ),
        CommandTemplate(
            titleAr = "عرض Firewall",
            titleEn = "Show firewall rules",
            command = "/ip firewall filter print",
            risk = CommandRisk.SAFE
        ),
        CommandTemplate(
            titleAr = "عرض HotSpot Users",
            titleEn = "Show HotSpot users",
            command = "/ip hotspot user print",
            risk = CommandRisk.SAFE
        ),
        CommandTemplate(
            titleAr = "إيقاف Telnet",
            titleEn = "Disable Telnet",
            command = "/ip service disable telnet",
            risk = CommandRisk.CHANGE
        )
    )
}
