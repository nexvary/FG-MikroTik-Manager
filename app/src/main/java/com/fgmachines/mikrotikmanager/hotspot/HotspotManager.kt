package com.fgmachines.mikrotikmanager.hotspot

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class PortalDesign(
    val networkName: String = "FG Machines WiFi",
    val supportPhone: String = "",
    val welcome: String = "أهلاً بك — اتصل بالواي فاي ثم أدخل كود الكارت",
    val color: String = "#159DFF",
    val terms: String = "",
    val website: String = "",
    val logoDataUri: String = ""
)

object PortalTemplates {
    fun render(assets: Map<String, String>, design: PortalDesign): Map<String, String> {
        require(Regex("#[a-fA-F0-9]{6}").matches(design.color)) { "Use a six-digit color such as #159DFF" }
        val link = design.website.takeIf { it.startsWith("https://") }.orEmpty()
        val logo = design.logoDataUri.takeIf { Regex("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+").matches(it) }.orEmpty()
        return assets.mapValues { (_, content) ->
            content.replace("@@NETWORK@@", html(design.networkName))
                .replace("@@PHONE@@", html(design.supportPhone))
                .replace("@@WELCOME@@", html(design.welcome))
                .replace("@@COLOR@@", design.color)
                .replace("@@TERMS@@", html(design.terms))
                .replace("@@WEBSITE@@", html(link))
                .replace("@@LOGO@@", logo)
        }
    }
    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}

data class HotspotSnapshot(
    val users: List<Map<String, String>>,
    val active: List<Map<String, String>>,
    val profiles: List<Map<String, String>>,
    val servers: List<Map<String, String>>,
    val interfaces: List<Map<String, String>>
)

data class HotspotSetup(
    val interfaceName: String,
    val gatewayCidr: String,
    val networkCidr: String,
    val poolRange: String,
    val dnsName: String,
    val configureDhcp: Boolean = true
)

/** Uses the existing authenticated RouterOS transport; no customer-facing API or credentials. */
class HotspotManager(private val transport: RouterOsTransport) {
    suspend fun load() = HotspotSnapshot(
        transport.read("ip/hotspot/user"), transport.read("ip/hotspot/active"),
        transport.read("ip/hotspot/user/profile"), transport.read("ip/hotspot"), transport.read("interface")
    )

    suspend fun update(id: String, attributes: Map<String, String>) {
        require(id.isNotBlank())
        transport.execute("/ip/hotspot/user/set", attributes + (".id" to id))
    }

    suspend fun setEnabled(id: String, username: String, enabled: Boolean) {
        transport.execute("/ip/hotspot/user/" + if (enabled) "enable" else "disable", mapOf(".id" to id))
        if (!enabled) disconnect(username)
    }

    suspend fun delete(id: String, username: String) {
        disconnect(username)
        transport.execute("/ip/hotspot/user/remove", mapOf(".id" to id))
        transport.read("system/scheduler").filter { it["comment"] == "FG MTM voucher expiry for $username" }.forEach {
            it[".id"]?.let { scheduler -> transport.execute("/system/scheduler/remove", mapOf(".id" to scheduler)) }
        }
    }

    private suspend fun disconnect(username: String) {
        transport.read("ip/hotspot/active").filter { it["user"] == username }.forEach {
            it[".id"]?.let { active -> transport.execute("/ip/hotspot/active/remove", mapOf(".id" to active)) }
        }
    }

    suspend fun addTime(id: String, seconds: Long) {
        require(seconds in 60..31_536_000) { "Invalid duration" }
        val row = transport.read("ip/hotspot/user").firstOrNull { it[".id"] == id } ?: error("Voucher no longer exists")
        val current = RouterDuration.seconds(row["limit-uptime"].orEmpty()) ?: error("Unknown RouterOS duration")
        require(current > 0) { "This voucher has unlimited usage time; set a duration first" }
        // Add to the total allowance, never reset uptime or absolute expiry.
        update(id, mapOf("limit-uptime" to "${Math.addExact(current, seconds)}s"))
    }

    suspend fun installPortal(profileId: String, files: Map<String, String>): String {
        val profile = transport.read("ip/hotspot/profile").firstOrNull { it[".id"] == profileId } ?: error("HotSpot profile not found")
        require(files.keys.containsAll(listOf("login.html", "status.html", "md5.js")))
        require(files.values.all { it.toByteArray().size < 60_000 }) { "Portal file exceeds RouterOS API content limit" }
        val hasFlash = transport.read("file").any { PortalPaths.normalize(it["name"].orEmpty()) == "flash" }
        val directory = (if (hasFlash) "flash/" else "") + "fg-mtm-" + System.currentTimeMillis()
        transport.create("file", mapOf("name" to directory, "type" to "directory"))
        for ((name, contents) in files) {
            require(name.matches(Regex("[A-Za-z0-9.-]+")))
            val path = "$directory/$name"
            transport.create("file", mapOf("name" to path, "type" to "file"))
            val id = transport.read("file").firstOrNull { it["name"] == path }?.get(".id") ?: error("Could not create $name")
            transport.execute("/file/set", mapOf(".id" to id, "contents" to contents))
            val saved = transport.read("file").firstOrNull { it[".id"] == id }?.get("contents")
            require(saved == contents) { "Upload verification failed for $name; existing portal is unchanged" }
        }
        // Save an explicit rollback pointer before switching. Never overwrite the owner's old files.
        val pointer = "$directory/rollback.txt"
        transport.create("file", mapOf("name" to pointer, "type" to "file"))
        val pointerId = transport.read("file").firstOrNull { it["name"] == pointer }?.get(".id") ?: error("Cannot save rollback pointer")
        transport.execute("/file/set", mapOf(".id" to pointerId, "contents" to "profile=${profile["name"]}\nhtml-directory=${profile["html-directory"]}\nhtml-directory-override=${profile["html-directory-override"].orEmpty()}"))
        transport.execute("/ip/hotspot/profile/set", mapOf(".id" to profileId, "html-directory" to directory, "html-directory-override" to ""))
        try {
            var verified = false
            for (attempt in 0..3) {
                if (attempt > 0) kotlinx.coroutines.delay(300)
                val current = transport.read("ip/hotspot/profile").firstOrNull { it[".id"] == profileId }
                if (current != null && PortalPaths.configured(current) == directory &&
                    PortalPaths.present(current, transport.read("file"))) {
                    verified = true
                    break
                }
            }
            check(verified) { "HotSpot portal binding could not be verified" }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            val rollback = runCatching { transport.execute("/ip/hotspot/profile/set", mapOf(
                ".id" to profileId, "html-directory" to profile["html-directory"].orEmpty(),
                "html-directory-override" to profile["html-directory-override"].orEmpty()
            )) }
            throw IllegalStateException(if (rollback.isSuccess)
                "Portal verification failed; previous page restored"
                else "Portal verification failed; review profile binding before retrying", failure)
        }
        return directory
    }

    suspend fun activeServerProfiles(): List<Map<String, String>> {
        val names = transport.read("ip/hotspot").filter {
            it["disabled"] !in listOf("yes", "true") && it["invalid"] !in listOf("yes", "true")
        }.mapNotNull { it["profile"] }.toSet()
        return serverProfiles().filter { it["name"] in names }
    }

    suspend fun serverProfiles(): List<Map<String, String>> = transport.read("ip/hotspot/profile")

    suspend fun setup(plan: HotspotSetup): String {
        require(plan.interfaceName.isNotBlank() && plan.dnsName.matches(Regex("[a-zA-Z0-9.-]+")))
        require(validCidr(plan.gatewayCidr) && validCidr(plan.networkCidr)) { "Invalid IPv4/CIDR" }
        val addresses = transport.read("ip/address")
        require(addresses.any { it["interface"] == plan.interfaceName && it["address"] == plan.gatewayCidr }) {
            "Add the gateway address to this interface first in Network > IP Addresses. Existing network addresses will not be changed."
        }
        require(transport.read("ip/hotspot").none { it["interface"] == plan.interfaceName }) { "This interface already has a HotSpot" }
        require(transport.read("ip/dhcp-server").none { it["interface"] == plan.interfaceName } || !plan.configureDhcp) { "DHCP already exists; turn off Create DHCP to preserve it" }
        val gateway = plan.gatewayCidr.substringBefore('/')
        val tag = "fg-mtm-" + System.currentTimeMillis()
        val created = mutableListOf<Pair<String, String>>()
        suspend fun create(menu: String, args: Map<String, String>) {
            val response = transport.create(menu, args)
            val id = response.firstOrNull()?.get(".id") ?: transport.read(menu).firstOrNull { it["name"] == args["name"] || (menu == "ip/dhcp-server/network" && it["address"] == args["address"]) }?.get(".id") ?: error("Creation could not be verified: $menu")
            created += menu to id
        }
        try {
            val parts = plan.poolRange.split('-')
            require(parts.size == 2 && parts.all { validIpv4(it) }) { "Invalid IP pool range" }
            create("ip/pool", mapOf("name" to tag, "ranges" to plan.poolRange))
            create("ip/hotspot/profile", mapOf("name" to tag, "hotspot-address" to gateway, "dns-name" to plan.dnsName, "login-by" to "http-chap"))
            if (plan.configureDhcp) {
                if (transport.read("ip/dhcp-server/network").none { it["address"] == plan.networkCidr }) {
                    create("ip/dhcp-server/network", mapOf("address" to plan.networkCidr, "gateway" to gateway, "dns-server" to gateway))
                }
                create("ip/dhcp-server", mapOf("name" to tag, "interface" to plan.interfaceName, "address-pool" to tag, "disabled" to "no"))
            }
            create("ip/hotspot", mapOf("name" to tag, "interface" to plan.interfaceName, "profile" to tag, "address-pool" to tag, "disabled" to "no"))
            return tag
        } catch (error: Throwable) {
            val failures = mutableListOf<String>()
            for ((menu, id) in created.asReversed()) {
                try { transport.execute("/$menu/remove", mapOf(".id" to id)) } catch (_: Throwable) { failures += "$menu $id" }
            }
            if (error is CancellationException) throw error
            throw IllegalStateException((error.message ?: "Setup failed") + if (failures.isEmpty()) " — changes rolled back" else " — rollback incomplete: " + failures.joinToString(), error)
        }
    }
}

object RouterDuration {
    fun seconds(value: String): Long? {
        if (value.isBlank() || value == "0") return 0
        if (value.matches(Regex("[0-9]+:[0-9]{2}:[0-9]{2}"))) {
            val p = value.split(':').map { it.toLong() }
            return p[0] * 3600 + p[1] * 60 + p[2]
        }
        val matches = Regex("([0-9]+)(w|d|h|m|s)").findAll(value).toList()
        if (matches.joinToString("") { it.value } != value) return null
        return matches.sumOf { it.groupValues[1].toLong() * when(it.groupValues[2]) { "w" -> 604800L; "d" -> 86400L; "h" -> 3600L; "m" -> 60L; else -> 1L } }
    }
}
private fun validIpv4(s: String) = s.split('.').let { it.size == 4 && it.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } }
private fun validCidr(s: String) = s.split('/').let { it.size == 2 && validIpv4(it[0]) && it[1].toIntOrNull()?.let { prefix -> prefix in 1..32 } == true }
