package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.command.CommandExecutionResult
import com.fgmachines.mikrotikmanager.command.CommandExecutionStatus
import com.fgmachines.mikrotikmanager.command.CommandRisk
import com.fgmachines.mikrotikmanager.command.CommandTemplates
import com.fgmachines.mikrotikmanager.command.ParsedRouterCommand
import com.fgmachines.mikrotikmanager.command.RouterCommandParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterSheet(
    arabic: Boolean,
    running: Boolean,
    results: List<CommandExecutionResult>,
    history: List<CommandExecutionResult>,
    onRun: (List<ParsedRouterCommand>) -> Unit,
    onClearResults: () -> Unit,
    onDismiss: () -> Unit,
    initialText: String = ""
) {
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var libraryOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(initialText) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var dangerousConfirmed by remember { mutableStateOf(false) }
    var confirmationOpen by remember { mutableStateOf(false) }
    val clipboardCandidate = remember {
        clipboard.getText()?.text
            ?.takeIf { text ->
                text.lineSequence().any { it.trim().startsWith("/") }
            }
            .orEmpty()
    }

    val parsed = remember(input) {
        RouterCommandParser.parseScript(input)
    }
    val hasDangerous = parsed.any { it.risk == CommandRisk.DANGEROUS }
    val allSupported = parsed.isNotEmpty() && parsed.all { it.supported }

    LaunchedEffect(input) {
        dangerousConfirmed = false
        onClearResults()
    }

    if (libraryOpen) CommandLibraryDialog(arabic, { input = it; libraryOpen = false }, { libraryOpen = false })

    if (confirmationOpen) {
        AlertDialog(
            onDismissRequest = { confirmationOpen = false },
            title = { Text(if (arabic) "تأكيد الأوامر الحساسة" else "Confirm sensitive commands") },
            text = { Text(parsed.filter { it.risk == CommandRisk.DANGEROUS }.joinToString("\n") { if (arabic) it.explanationAr else it.explanationEn }) },
            confirmButton = { Button(onClick = { confirmationOpen = false; onRun(parsed) }) { Text(if (arabic) "تنفيذ" else "Execute") } },
            dismissButton = { TextButton(onClick = { confirmationOpen = false }) { Text(if (arabic) "إلغاء" else "Cancel") } }
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FgDeepNavy,
        contentColor = FgWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(FgMint.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = FgMint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Command Center",
                        color = FgWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (arabic) {
                            "الصق أوامر RouterOS وسأشرحها قبل التنفيذ"
                        } else {
                            "Paste RouterOS commands and review them before execution"
                        },
                        color = FgSilver,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(onClick = { showHistory = !showHistory }) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = FgCyan)
                    Spacer(Modifier.width(5.dp))
                    Text(if (arabic) "السجل" else "History")
                }
            }

            TextButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (arabic) "رجوع للصفحة السابقة" else "Back to previous page")
            }
            Spacer(Modifier.height(10.dp))

            if (input.isBlank() && clipboardCandidate.isNotBlank()) {
                OutlinedButton(
                    onClick = { input = clipboardCandidate },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.2.dp, FgMint)
                ) {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        tint = FgMint
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (arabic) {
                            "وجدت أوامر RouterOS في الحافظة — لصق الآن"
                        } else {
                            "RouterOS commands found in clipboard — paste now"
                        },
                        color = FgMint
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showHistory && history.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FgPanel),
                    border = BorderStroke(1.dp, FgCyan),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(history.take(20)) { _, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.command.original,
                                    modifier = Modifier.weight(1f),
                                    color = statusColor(item.status),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = { input = item.command.original }
                                ) {
                                    Text(if (arabic) "إعادة" else "Reuse")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) input = text
                    },
                    border = BorderStroke(1.dp, FgMint)
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = FgMint)
                    Spacer(Modifier.width(5.dp))
                    Text(if (arabic) "لصق" else "Paste", color = FgMint)
                }

                OutlinedButton(onClick = { libraryOpen = true }) { Text(if (arabic) "مكتبة الأوامر (" + CommandTemplates.items.size + ")" else "Command library (" + CommandTemplates.items.size + ")") }
                CommandTemplates.items.take(3).forEach { template ->
                    FilterChip(
                        selected = false,
                        onClick = { input = template.command },
                        label = {
                            Text(if (arabic) template.titleAr else template.titleEn)
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                label = {
                    Text(
                        if (arabic) "الأوامر — كل أمر في سطر"
                        else "Commands — one command per line"
                    )
                },
                placeholder = {
                    Text("/ip address print\n/ip service disable telnet")
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = androidx.compose.ui.text.style.TextDirection.Ltr
                ),
                visualTransformation = RouterSyntaxTransformation

            )

            Spacer(Modifier.height(8.dp))

            if (parsed.isEmpty()) {
                Text(
                    if (arabic) {
                        "اكتب أو الصق أوامر لعرض المعاينة والشرح."
                    } else {
                        "Enter or paste commands to preview and explain them."
                    },
                    color = FgSilverMuted
                )
            } else {
                Text(
                    if (arabic) {
                        "تم التعرف على " + parsed.size + " أمر"
                    } else {
                        "Detected " + parsed.size + " command(s)"
                    },
                    color = FgCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                itemsIndexed(parsed) { index, command ->
                    CommandPreviewCard(
                        index = index,
                        command = command,
                        arabic = arabic,
                        result = results.getOrNull(index)
                    )
                }
            }

            if (hasDangerous) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            FgAmber.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = FgAmber)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (arabic) {
                            "توجد أوامر حذف/Firewall أو تغييرات حساسة. راجعها قبل التنفيذ."
                        } else {
                            "This batch contains delete/firewall or sensitive changes."
                        },
                        modifier = Modifier.weight(1f),
                        color = FgAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Checkbox(
                        checked = dangerousConfirmed,
                        onCheckedChange = { dangerousConfirmed = it }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { if (hasDangerous) confirmationOpen = true else onRun(parsed) },
                enabled = !running &&
                    allSupported &&
                    (!hasDangerous || dangerousConfirmed),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) {
                        if (arabic) "جاري التنفيذ سطرًا بسطر..." else "Running line by line..."
                    } else {
                        if (arabic) "تنفيذ الأوامر" else "Run commands"
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CommandPreviewCard(
    index: Int,
    command: ParsedRouterCommand,
    arabic: Boolean,
    result: CommandExecutionResult?
) {
    var showDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val details = remember(result?.rows) {
        result?.rows.orEmpty().mapIndexed { rowIndex, row ->
            (rowIndex + 1).toString() + ". " + row.entries.joinToString("\n") { (key, value) ->
                val secret = listOf("password", "secret", "private-key", "passphrase", "contents").any { key.contains(it, true) }
                key + "=" + if (secret) "[hidden]" else value
            }
        }
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(if (arabic) "نتيجة الأمر كاملة" else "Complete command result") },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(details) { _, row -> Text(row, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)) }
            }
        },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(details.joinToString("\n\n"))) }) { Text(if (arabic) "نسخ النتيجة" else "Copy result") } },
        dismissButton = { TextButton(onClick = { showDetails = false }) { Text(if (arabic) "إغلاق" else "Close") } }
    )
    val accent = when (command.risk) {
        CommandRisk.SAFE -> FgMint
        CommandRisk.CHANGE -> FgBlue
        CommandRisk.DANGEROUS -> FgAmber
        CommandRisk.UNSUPPORTED -> MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.2.dp, accent),
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (index + 1).toString(),
                    color = accent,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    highlightedCommand(command),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                if (arabic) command.explanationAr else command.explanationEn,
                color = FgSilver,
                style = MaterialTheme.typography.bodySmall
            )

            result?.let {
                HorizontalDivider(color = accent.copy(alpha = 0.25f))
                Text(
                    statusLabel(it, arabic),
                    color = statusColor(it.status),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (it.rows.isNotEmpty()) {
                    TextButton(onClick = { showDetails = true }) {
                        Text(if (arabic) "عرض كل النتائج (" + it.rows.size + ")" else "Show all results (" + it.rows.size + ")")
                    }

                    val sample = it.rows.take(2).joinToString("  •  ") { row ->
                        row["name"]
                            ?: row["address"]
                            ?: row[".id"]
                            ?: row.entries.take(2).joinToString { e -> e.key + "=" + e.value }
                    }
                    Text(
                        sample,
                        color = FgSilverMuted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun highlightedCommand(command: ParsedRouterCommand): AnnotatedString =
    buildAnnotatedString {
        val original = command.original
        val tokens = RouterCommandParser.tokenize(original)
        val action = command.action

        tokens.forEachIndexed { index, token ->
            if (index > 0) append(" ")
            val color = when {
                token.startsWith("/") -> FgBlue
                token.equals(action, ignoreCase = true) -> when (command.risk) {
                    CommandRisk.DANGEROUS -> FgAmber
                    CommandRisk.CHANGE -> FgMint
                    CommandRisk.SAFE -> FgCyan
                    CommandRisk.UNSUPPORTED -> Color(0xFFFF7B7B)
                }
                token.contains("=") -> FgPurple
                else -> FgSilver
            }
            pushStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold))
            append(token)
            pop()
        }
    }

private fun statusColor(status: CommandExecutionStatus): Color =
    when (status) {
        CommandExecutionStatus.SUCCESS -> FgMint
        CommandExecutionStatus.FAILED -> Color(0xFFFF7B7B)
        CommandExecutionStatus.SKIPPED -> FgAmber
        CommandExecutionStatus.RUNNING -> FgCyan
        CommandExecutionStatus.PENDING -> FgSilverMuted
    }

private fun statusLabel(
    result: CommandExecutionResult,
    arabic: Boolean
): String {
    val prefix = when (result.status) {
        CommandExecutionStatus.SUCCESS -> if (arabic) "تم" else "Success"
        CommandExecutionStatus.FAILED -> if (arabic) "فشل" else "Failed"
        CommandExecutionStatus.SKIPPED -> if (arabic) "تم التخطي" else "Skipped"
        CommandExecutionStatus.RUNNING -> if (arabic) "جاري" else "Running"
        CommandExecutionStatus.PENDING -> if (arabic) "انتظار" else "Pending"
    }
    return prefix + " — " + result.message
}

private object RouterSyntaxTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val colored = buildAnnotatedString {
            append(text.text)
            Regex("[/][a-zA-Z0-9_/-]+|\\b(print|add|set|remove|enable|disable|reboot)\\b|[a-zA-Z0-9-]+=").findAll(text.text).forEach { match ->
                val color = when {
                    match.value.startsWith("/") -> FgBlue
                    match.value.endsWith("=") -> FgSilver
                    match.value in setOf("remove", "reboot", "disable") -> FgAmber
                    else -> FgMint
                }
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }
        }
        return androidx.compose.ui.text.input.TransformedText(colored, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
}
