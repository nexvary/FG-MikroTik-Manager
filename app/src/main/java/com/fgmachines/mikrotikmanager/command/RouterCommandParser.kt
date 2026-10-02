package com.fgmachines.mikrotikmanager.command

object RouterCommandParser {
    private val actions = setOf(
        "print", "add", "set", "remove", "enable", "disable",
        "reboot", "save", "run", "renew", "release"
    )

    fun parseScript(text: String): List<ParsedRouterCommand> =
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .map(::parseLine)
            .toList()

    fun parseLine(line: String): ParsedRouterCommand {
        var quote: Char? = null
        var escaped = false
        for (ch in line) {
            if (escaped) { escaped = false; continue }
            if (ch == '\\') { escaped = true; continue }
            if (quote != null) { if (ch == quote) quote = null; continue }
            if (ch == '\"' || ch == '\'') { quote = ch; continue }
            if (ch in ";[]{}$") return unsupported(line, "الأوامر المركبة غير مدعومة؛ استخدم أمرًا واحدًا واضحًا في كل سطر", "Compound expressions are unsupported; use one explicit command per line")
        }
        if (quote != null || escaped) return unsupported(line, "علامات الاقتباس غير مكتملة", "Unclosed quote or escape")
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

        if (tail.count { !it.contains("=") } > 1 || tail.any { !it.contains("=") && action in setOf("print", "add", "reboot", "save", "run") }) {
            return unsupported(line, "وسائط الأمر غير مدعومة", "Unsupported command arguments")
        }
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
        if (action == "reboot" || action == "remove" || action in setOf("disable", "release") || menu == "ip/service" || menu == "user" || menu.startsWith("interface") || menu == "ip/address" || menu == "ip/route") return CommandRisk.DANGEROUS

        val dangerousMenu = menu.startsWith("system/reset") ||
            menu.startsWith("system/routerboard") ||
            menu.startsWith("partition") ||
            menu.startsWith("disk") ||
            menu.startsWith("ip/firewall")

        if (dangerousMenu) return CommandRisk.DANGEROUS
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
                "reboot" -> "سيعيد تشغيل الراوتر."
                "save" -> "سيحفظ البيانات أو النسخة الاحتياطية داخل /$menu."
                "run" -> "سيشغّل الإجراء داخل /$menu."
                "renew" -> "سيطلب تجديد الإعداد داخل /$menu."
                "release" -> "سيحرر الإعداد داخل /$menu."
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
                "reboot" -> "Reboots the router."
                "save" -> "Saves data or a backup under /$menu."
                "run" -> "Runs the action under /$menu."
                "renew" -> "Renews the selected item under /$menu."
                "release" -> "Releases the selected item under /$menu."
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
