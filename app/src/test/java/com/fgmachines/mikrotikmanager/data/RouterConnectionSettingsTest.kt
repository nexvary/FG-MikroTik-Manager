package com.fgmachines.mikrotikmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouterConnectionSettingsTest {

    @Test
    fun normalizesHttpsHost() {
        val settings = RouterConnectionSettings(
            host = "https://192.168.88.1/",
            port = 443,
            username = "admin",
            password = "secret",
            protocol = RouterProtocol.REST_HTTPS
        )

        assertEquals("192.168.88.1", settings.normalizedHost())
        assertEquals("https://192.168.88.1:443/rest", settings.restBaseUrl())
    }

    @Test
    fun keepsCustomHttpsPort() {
        val settings = RouterConnectionSettings(
            host = "router.example.net",
            port = 8443,
            username = "admin",
            password = "secret",
            protocol = RouterProtocol.REST_HTTPS
        )

        assertEquals("https://router.example.net:8443/rest", settings.restBaseUrl())
    }
}
