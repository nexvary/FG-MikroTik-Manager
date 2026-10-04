package com.fgmachines.mikrotikmanager.voucher

data class HotspotAuthorityPreset(
    val name: String,
    val uploadRate: String,
    val downloadRate: String
) {
    val routerOsRateLimit: String
        get() = "$uploadRate/$downloadRate"
}

/**
 * Presets observed in the reference WiFi Cards Generator v7.6 workflow.
 *
 * The UI calls them "Limited 1MB/2MB/..." although RouterOS applies them
 * as Mbit/s rate-limit values. We expose Mbps in the Android UI to avoid
 * confusing speed with megabytes.
 */
object HotspotAuthorityPresets {
    val limited1Mbps = HotspotAuthorityPreset("Limited 1MB", "1M", "1M")
    val limited2Mbps = HotspotAuthorityPreset("Limited 2MB", "1M", "2M")
    val limited3Mbps = HotspotAuthorityPreset("Limited 3MB", "1M", "3M")
    val limited5Mbps = HotspotAuthorityPreset("Limited 5MB", "2M", "5M")
    val limited10Mbps = HotspotAuthorityPreset("Limited 10MB", "3M", "10M")

    val all: List<HotspotAuthorityPreset> = listOf(
        limited1Mbps,
        limited2Mbps,
        limited3Mbps,
        limited5Mbps,
        limited10Mbps
    )
}

class HotspotAuthorityScriptBuilder {
    fun buildProfile(preset: HotspotAuthorityPreset): String =
        buildString {
            append("/ip hotspot user profile add")
            append(" name=\"").append(preset.name).append("\"")
            append(" session-timeout=6h")
            append(" keepalive-timeout=2m")
            append(" status-autorefresh=1m")
            append(" shared-users=1")
            append(" rate-limit=\"").append(preset.routerOsRateLimit).append("\"")
        }
}
