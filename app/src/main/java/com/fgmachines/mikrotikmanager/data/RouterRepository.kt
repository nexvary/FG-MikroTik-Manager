package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.command.CommandExecutionResult
import com.fgmachines.mikrotikmanager.command.CommandExecutionStatus
import com.fgmachines.mikrotikmanager.command.ParsedRouterCommand
import com.fgmachines.mikrotikmanager.network.ApiRouterOsTransport
import com.fgmachines.mikrotikmanager.network.AutoRouterOsTransport
import com.fgmachines.mikrotikmanager.network.RestRouterOsTransport
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import com.fgmachines.mikrotikmanager.network.RouterOsException
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionItem
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionStatus
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RouterRepository private constructor(
    private val transport: RouterOsTransport,
    routerKey: String = "test-router"
) : AutoCloseable {

    val business = com.fgmachines.mikrotikmanager.business.BusinessRouter(transport)
    val advanced = com.fgmachines.mikrotikmanager.advanced.AdvancedRouterManager(transport, routerKey)
    val hotspot = com.fgmachines.mikrotikmanager.hotspot.HotspotManager(transport)

    suspend fun loadDashboard(): DashboardSnapshot = coroutineScope {
        val identityRequest = async { transport.read("system/identity") }
        val resourceRequest = async { transport.read("system/resource") }
        val interfaceRequest = async { transport.read("interface") }

        val identity = identityRequest.await().firstOrNull().orEmpty()
        val resource = resourceRequest.await().firstOrNull().orEmpty()
        val interfaces = interfaceRequest.await().map { it.toRouterInterface() }

        DashboardSnapshot(
            identity = identity["name"].orEmpty().ifBlank { "RouterOS" },
            version = resource["version"].orEmpty(),
            boardName = resource["board-name"].orEmpty(),
            architecture = resource["architecture-name"].orEmpty(),
            cpuLoadPercent = resource["cpu-load"]?.toIntOrNull(),
            freeMemoryBytes = resource["free-memory"]?.toLongOrNull(),
            totalMemoryBytes = resource["total-memory"]?.toLongOrNull(),
            uptime = resource["uptime"].orEmpty(),
            interfaces = interfaces
        )
    }

    suspend fun loadInterfaces(): List<RouterInterface> =
        transport.read("interface").map { it.toRouterInterface() }

    suspend fun loadAdminModule(module: RouterAdminModule): RouterMenuSnapshot {
        var lastError: Throwable? = null
        var emptyWifi: RouterMenuSnapshot? = null
        for (menu in module.menuCandidates) {
            try {
                val snapshot = RouterMenuSnapshot(module, menu, transport.read(menu))
                if (module == RouterAdminModule.WIFI && snapshot.rows.isEmpty()) {
                    emptyWifi = snapshot
                    continue
                }
                return snapshot
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
            }
        }
        emptyWifi?.let { return it }
        throw RouterOsException(
            message = "RouterOS menu is not available: " + module.name,
            cause = lastError
        )
    }

    suspend fun createAdminItem(
        menuPath: String,
        attributes: Map<String, String>
    ): RouterAdminActionResult =
        runCatching {
            transport.create(menuPath, attributes)
        }.fold(
            onSuccess = {
                RouterAdminActionResult(
                    success = true,
                    message = "Created successfully"
                )
            },
            onFailure = {
                RouterAdminActionResult(
                    success = false,
                    message = it.message ?: "Create failed"
                )
            }
        )

    suspend fun updateAdminItem(
        menuPath: String,
        rowId: String?,
        attributes: Map<String, String>
    ): RouterAdminActionResult =
        runCatching {
            val args = buildMap {
                if (!rowId.isNullOrBlank()) put(".id", rowId)
                putAll(attributes)
            }
            transport.execute("/" + menuPath.trim('/') + "/set", args)
        }.fold(
            onSuccess = {
                RouterAdminActionResult(
                    success = true,
                    message = "Updated successfully"
                )
            },
            onFailure = {
                RouterAdminActionResult(
                    success = false,
                    message = it.message ?: "Update failed"
                )
            }
        )

    suspend fun removeAdminItem(
        menuPath: String,
        rowId: String
    ): RouterAdminActionResult =
        runCatching {
            transport.execute(
                "/" + menuPath.trim('/') + "/remove",
                mapOf(".id" to rowId)
            )
        }.fold(
            onSuccess = {
                RouterAdminActionResult(
                    success = true,
                    message = "Deleted successfully"
                )
            },
            onFailure = {
                RouterAdminActionResult(
                    success = false,
                    message = it.message ?: "Delete failed"
                )
            }
        )

    suspend fun setAdminItemEnabled(
        menuPath: String,
        rowId: String,
        enabled: Boolean
    ): RouterAdminActionResult =
        runCatching {
            transport.execute(
                "/" + menuPath.trim('/') + "/" + (if (enabled) "enable" else "disable"),
                mapOf(".id" to rowId)
            )
        }.fold(
            onSuccess = {
                RouterAdminActionResult(
                    success = true,
                    message = if (enabled) "Enabled" else "Disabled"
                )
            },
            onFailure = {
                RouterAdminActionResult(
                    success = false,
                    message = it.message ?: "State change failed"
                )
            }
        )

    suspend fun createRouterAdmin(
        username: String,
        password: String,
        group: String = "full",
        comment: String = "Created by FG MTM"
    ): RouterAdminActionResult =
        createAdminItem(
            "user",
            buildMap {
                put("name", username)
                put("password", password)
                put("group", group)
                if (comment.isNotBlank()) put("comment", comment)
            }
        )

    suspend fun executeCommand(command: ParsedRouterCommand): CommandExecutionResult {
        if (!command.supported) {
            return CommandExecutionResult(
                command = command,
                status = CommandExecutionStatus.SKIPPED,
                message = "Unsupported command"
            )
        }

        return try {
            val rows = when (command.action) {
                "print" -> if (command.attributes.isEmpty()) transport.read(command.menu) else transport.execute("/" + command.menu.trim('/') + "/print", command.attributes)
                "add" -> transport.create(command.menu, command.attributes)
                "set", "enable", "disable", "remove", "renew", "release" -> {
                    val singleton = command.action == "set" && command.menu in setOf("system/identity", "system/clock", "system/ntp/client", "ip/dns")
                    val id = if(singleton) null else resolveCommandTarget(command)
                    val attrs = buildMap {
                        putAll(command.attributes.filterKeys { it != "numbers" && it != ".id" })
                        if(id != null) put(".id", id)
                    }
                    transport.execute(
                        "/" + command.menu.trim('/') + "/" + command.action,
                        attrs
                    )
                }
                "reboot", "save", "run" ->
                    transport.execute(
                        "/" + command.menu.trim('/') + "/" + command.action,
                        command.attributes
                    )
                else -> error("Unsupported RouterOS action")
            }

            CommandExecutionResult(
                command = command,
                status = CommandExecutionStatus.SUCCESS,
                message = when (command.action) {
                    "print" -> "Read " + rows.size + " item(s)"
                    else -> "Completed"
                },
                rows = rows
            )
        } catch (t: Throwable) {
            CommandExecutionResult(
                command = command,
                status = CommandExecutionStatus.FAILED,
                message = t.message ?: "RouterOS command failed"
            )
        }
    }

    private suspend fun resolveCommandTarget(command: ParsedRouterCommand): String {
        val selector = command.selector
            ?: command.attributes[".id"]
            ?: command.attributes["numbers"]
            ?: throw RouterOsException("This command needs a target item")

        if (selector.startsWith("*")) return selector

        val rows = transport.read(command.menu)
        val matching = rows.firstOrNull { row ->
            row[".id"] == selector ||
                row["name"] == selector ||
                row["number"] == selector ||
                row["address"] == selector ||
                row["user"] == selector
        } ?: throw RouterOsException(
            "Could not find '" + selector + "' in /" + command.menu
        )

        return matching[".id"]
            ?: throw RouterOsException("RouterOS item has no .id")
    }

    suspend fun loadVoucherProfiles(mode: VoucherMode): List<RouterVoucherProfile> {
        val menu = when (mode) {
            VoucherMode.HOTSPOT -> "ip/hotspot/user/profile"
            VoucherMode.PPPOE -> "ppp/profile"
            VoucherMode.USER_MANAGER -> "user-manager/profile"
            VoucherMode.OFFLINE -> return emptyList()
        }

        return transport.read(menu)
            .mapNotNull { record ->
                val name = record["name"].orEmpty()
                if (name.isBlank()) {
                    null
                } else {
                    RouterVoucherProfile(
                        name = name,
                        rateLimit = record["rate-limit"].orEmpty(),
                        sharedUsers = record["shared-users"]?.toIntOrNull(),
                        sessionTimeout = record["session-timeout"].orEmpty()
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun provisionVoucherBatch(batch: VoucherBatch): VoucherProvisionSummary {
        if (batch.request.mode == VoucherMode.OFFLINE) {
            val skipped = batch.vouchers.map {
                VoucherProvisionItem(
                    username = it.username,
                    status = VoucherProvisionStatus.SKIPPED,
                    message = "Offline batch"
                )
            }
            return skipped.toSummary(batch.request.mode)
        }

        val menu = when (batch.request.mode) {
            VoucherMode.HOTSPOT -> "ip/hotspot/user"
            VoucherMode.PPPOE -> "ppp/secret"
            VoucherMode.USER_MANAGER -> "user-manager/user"
            VoucherMode.OFFLINE -> error("Handled above")
        }

        if (batch.request.mode == VoucherMode.USER_MANAGER) {
            val available = runCatching {
                val packages = transport.read("system/package")
                require(packages.any { it["name"] == "user-manager" && it["disabled"] !in listOf("true", "yes") })
                require(transport.read("user-manager").firstOrNull()?.get("enabled") in listOf("true", "yes"))
                transport.read("user-manager/profile")
            }.isSuccess
            if (!available) return batch.vouchers.map { VoucherProvisionItem(it.username, VoucherProvisionStatus.FAILED, "User Manager غير متاح أو غير مجهز على هذا الراوتر — استخدم HotSpot التقليدي / User Manager is unavailable or not configured; use HotSpot") }.toSummary(batch.request.mode)
        }
        val existingNames = transport.read(menu)
            .mapNotNull { it["name"]?.takeIf(String::isNotBlank) }
            .toMutableSet()

        val results = ArrayList<VoucherProvisionItem>(batch.vouchers.size)
        val routerClock = if (batch.vouchers.any { it.absoluteExpiryEpochMs != null }) {
            runCatching { readRouterClock() }.getOrNull()
        } else {
            null
        }

        for (voucher in batch.vouchers) {
            if (voucher.username in existingNames) {
                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = VoucherProvisionStatus.DUPLICATE,
                    message = "Username already exists on router"
                )
                continue
            }

            try {
                val created = when (batch.request.mode) {
                    VoucherMode.HOTSPOT ->
                        transport.create(
                            "ip/hotspot/user",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                put("profile", voucher.profile)
                                put("server", voucher.server)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                                voucher.limitUptime?.takeIf(String::isNotBlank)?.let {
                                    put("limit-uptime", it)
                                }
                                voucher.limitBytesTotal?.let {
                                    put("limit-bytes-total", it.toString())
                                }
                            }
                        )

                    VoucherMode.PPPOE ->
                        transport.create(
                            "ppp/secret",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                put("service", "pppoe")
                                put("profile", voucher.profile)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                            }
                        )

                    VoucherMode.USER_MANAGER -> {
                        val user = transport.create(
                            "user-manager/user",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                            }
                        )

                        if (voucher.profile.isNotBlank() &&
                            !voucher.profile.equals("No Profile", ignoreCase = true)
                        ) {
                            transport.create(
                                "user-manager/user-profile",
                                mapOf(
                                    "user" to voucher.username,
                                    "profile" to voucher.profile
                                )
                            )
                        }

                        user
                    }

                    VoucherMode.OFFLINE -> emptyList()
                }

                existingNames += voucher.username

                val expiryMessage = voucher.absoluteExpiryEpochMs?.let { expiryEpoch ->
                    runCatching {
                        scheduleVoucherExpiry(
                            voucher = voucher,
                            expiryEpochMs = expiryEpoch,
                            routerClock = routerClock
                        )
                    }.fold(
                        onSuccess = { " • expires automatically" },
                        onFailure = {
                            val id = created.firstOrNull()?.get(".id") ?: transport.read(menu).firstOrNull { row -> row["name"] == voucher.username }?.get(".id")
                            if (id != null) transport.execute("/$menu/disable", mapOf(".id" to id))
                            " • created but disabled; expiry schedule failed: " + (it.message ?: "unknown error")
                        }
                    )
                }.orEmpty()

                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = if (expiryMessage.contains("failed")) {
                        VoucherProvisionStatus.FAILED
                    } else {
                        VoucherProvisionStatus.CREATED
                    },
                    message = "Created" + expiryMessage,
                    routerId = created.firstOrNull()?.get(".id")
                )
            } catch (t: Throwable) {
                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = VoucherProvisionStatus.FAILED,
                    message = t.message ?: "RouterOS rejected voucher"
                )
            }
        }

        return results.toSummary(batch.request.mode)
    }

    private suspend fun readRouterClock(): LocalDateTime? {
        val row = transport.read("system/clock").firstOrNull() ?: return null
        val date = row["date"].orEmpty()
        val time = row["time"].orEmpty()
        if (date.isBlank() || time.isBlank()) return null

        val parsedDate = parseRouterDate(date) ?: return null
        val parsedTime = runCatching {
            LocalTime.parse(time.take(8), DateTimeFormatter.ofPattern("HH:mm:ss"))
        }.getOrNull() ?: return null

        return LocalDateTime.of(parsedDate, parsedTime)
    }

    private fun parseRouterDate(value: String): LocalDate? {
        val normalized = value.trim().lowercase(Locale.ENGLISH)
        val formats = listOf(
            DateTimeFormatter.ofPattern("MMM/dd/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE
        )
        return formats.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(normalized, formatter) }.getOrNull()
        }
    }

    private suspend fun scheduleVoucherExpiry(
        voucher: com.fgmachines.mikrotikmanager.voucher.VoucherDraft,
        expiryEpochMs: Long,
        routerClock: LocalDateTime?
    ) {
        val delayMs = expiryEpochMs - System.currentTimeMillis()
        require(delayMs > 0) { "Expiry time must be in the future" }

        val routerNow = routerClock ?: error("Router clock is unavailable; cannot enforce absolute expiry")
        val target = routerNow.plusSeconds(delayMs / 1000L)

        val schedulerName = ("fg-exp-" + voucher.username)
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)

        val escapedUser = voucher.username
            .replace("\\", "\\\\")
            .replace("$", "\\$")
            .replace("\"", "\\\"")

        val disableCommand = when (voucher.mode) {
            VoucherMode.HOTSPOT ->
                "/ip hotspot user disable [find where name=\"$escapedUser\"]"
            VoucherMode.PPPOE ->
                "/ppp secret disable [find where name=\"$escapedUser\"]"
            VoucherMode.USER_MANAGER ->
                "/user-manager user disable [find where name=\"$escapedUser\"]"
            VoucherMode.OFFLINE -> return
        }

        val logout = if (voucher.mode == VoucherMode.HOTSPOT) "; /ip hotspot active remove [find where user=\"$escapedUser\"]" else ""
        val onEvent = disableCommand + logout +
            "; /system scheduler remove [find where name=\"$schedulerName\"]"

        transport.create(
            "system/scheduler",
            mapOf(
                "name" to schedulerName,
                "start-date" to target.format(
                    if (transport.read("system/clock").firstOrNull()?.get("date")?.contains('-') == true) DateTimeFormatter.ISO_LOCAL_DATE else DateTimeFormatter.ofPattern("MMM/dd/yyyy", Locale.ENGLISH)
                ).lowercase(Locale.ENGLISH),
                "start-time" to target.format(
                    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)
                ),
                "interval" to "0s",
                "on-event" to onEvent,
                "comment" to "FG MTM voucher expiry for " + voucher.username
            )
        )
    }

    override fun close() = transport.close()

    companion object {
        fun create(settings: RouterConnectionSettings,decorate:(RouterOsTransport)->RouterOsTransport={it}): RouterRepository {
            val transport = when (settings.protocol) {
                RouterProtocol.AUTO -> AutoRouterOsTransport(settings)
                RouterProtocol.API, RouterProtocol.API_SSL -> ApiRouterOsTransport(settings)
                RouterProtocol.REST_HTTP,
                RouterProtocol.REST_HTTPS -> RestRouterOsTransport(settings)
            }
            return RouterRepository(decorate(transport), settings.host + ":" + settings.username)
        }

        internal fun forTesting(transport: RouterOsTransport): RouterRepository =
            RouterRepository(transport)
    }
}

private fun List<VoucherProvisionItem>.toSummary(
    mode: VoucherMode
): VoucherProvisionSummary =
    VoucherProvisionSummary(
        mode = mode,
        total = size,
        created = count { it.status == VoucherProvisionStatus.CREATED },
        duplicates = count { it.status == VoucherProvisionStatus.DUPLICATE },
        failed = count { it.status == VoucherProvisionStatus.FAILED },
        skipped = count { it.status == VoucherProvisionStatus.SKIPPED },
        items = this
    )

private fun Map<String, String>.toRouterInterface(): RouterInterface =
    RouterInterface(
        id = this[".id"].orEmpty(),
        name = this["name"].orEmpty(),
        type = this["type"].orEmpty(),
        running = this["running"].routerBoolean(),
        disabled = this["disabled"].routerBoolean(),
        rxBytes = this["rx-byte"]?.toLongOrNull(),
        txBytes = this["tx-byte"]?.toLongOrNull()
    )


private fun String?.routerBoolean(): Boolean =
    when (this?.trim()?.lowercase()) {
        "true", "yes", "1", "on" -> true
        else -> false
    }
