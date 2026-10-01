package com.fgmachines.mikrotikmanager.command

enum class CommandRisk {
    SAFE,
    CHANGE,
    DANGEROUS,
    UNSUPPORTED
}

enum class CommandExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

data class ParsedRouterCommand(
    val original: String,
    val menu: String,
    val action: String,
    val attributes: Map<String, String>,
    val selector: String? = null,
    val risk: CommandRisk,
    val explanationAr: String,
    val explanationEn: String,
    val supported: Boolean
)

data class CommandExecutionResult(
    val command: ParsedRouterCommand,
    val status: CommandExecutionStatus,
    val message: String,
    val rows: List<Map<String, String>> = emptyList()
)

data class CommandTemplate(
    val titleAr: String,
    val titleEn: String,
    val command: String,
    val risk: CommandRisk
)
