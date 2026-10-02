package com.fgmachines.mikrotikmanager.hotspot

/** RouterOS profile paths can be absolute while file names are relative. */
object PortalPaths {
    fun normalize(path: String): String = path.trim().split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")
    fun configured(profile: Map<String, String>): String =
        normalize(profile["html-directory-override"].orEmpty().ifBlank {
            profile["html-directory"].orEmpty().ifBlank { "hotspot" }
        })
    fun present(profile: Map<String, String>, files: List<Map<String, String>>): Boolean {
        val path = configured(profile)
        val names = files.map { normalize(it["name"].orEmpty()) }.toSet()
        val roots = listOf(path, if (path.startsWith("flash/")) path else "flash/" + path)
        return roots.any { root -> listOf("login.html", "status.html").all { root + "/" + it in names } }
    }
}
