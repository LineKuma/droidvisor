package com.droidvisor.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun validateIpAddress_withValidIPv4_shouldReturnTrue() {
        assertTrue(NetworkUtils.validateIpAddress("192.168.1.1"))
        assertTrue(NetworkUtils.validateIpAddress("10.0.0.1"))
        assertTrue(NetworkUtils.validateIpAddress("172.16.0.1"))
        assertTrue(NetworkUtils.validateIpAddress("0.0.0.0"))
        assertTrue(NetworkUtils.validateIpAddress("255.255.255.255"))
    }

    @Test
    fun validateIpAddress_withInvalidIPv4_shouldReturnFalse() {
        assertFalse(NetworkUtils.validateIpAddress(""))
        assertFalse(NetworkUtils.validateIpAddress("   "))
        assertFalse(NetworkUtils.validateIpAddress("192.168.1"))
        assertFalse(NetworkUtils.validateIpAddress("192.168.1.256"))
        assertFalse(NetworkUtils.validateIpAddress("192.168.1.-1"))
        assertFalse(NetworkUtils.validateIpAddress("192.168.1.1.1"))
        assertFalse(NetworkUtils.validateIpAddress("192.168.1.a"))
        assertFalse(NetworkUtils.validateIpAddress("abc.def.ghi.jkl"))
    }

    @Test
    fun validateIpAddress_withLeadingZeros_shouldReturnFalse() {
        assertFalse(NetworkUtils.validateIpAddress("192.168.01.1"))
        assertFalse(NetworkUtils.validateIpAddress("010.168.1.1"))
    }

    @Test
    fun validateIpAddress_withBoundaryValues_shouldReturnTrue() {
        assertTrue(NetworkUtils.validateIpAddress("0.0.0.0"))
        assertTrue(NetworkUtils.validateIpAddress("255.255.255.255"))
    }

    @Test
    fun validateIpAddress_withPartialOctets_shouldReturnFalse() {
        assertFalse(NetworkUtils.validateIpAddress("192.168.1"))
        assertFalse(NetworkUtils.validateIpAddress("192.168"))
        assertFalse(NetworkUtils.validateIpAddress("192"))
    }

    @Test
    fun validateIpAddress_withExtraDots_shouldReturnFalse() {
        assertFalse(NetworkUtils.validateIpAddress("192.168.1.1."))
        assertFalse(NetworkUtils.validateIpAddress(".192.168.1.1"))
        assertFalse(NetworkUtils.validateIpAddress("192..168.1.1"))
    }

    @Test
    fun validatePort_withValidPort_shouldReturnTrue() {
        assertTrue(NetworkUtils.validatePort(80))
        assertTrue(NetworkUtils.validatePort(443))
        assertTrue(NetworkUtils.validatePort(8080))
        assertTrue(NetworkUtils.validatePort(3000))
        assertTrue(NetworkUtils.validatePort(65535))
        assertTrue(NetworkUtils.validatePort(1))
    }

    @Test
    fun validatePort_withInvalidPort_shouldReturnFalse() {
        assertFalse(NetworkUtils.validatePort(0))
        assertFalse(NetworkUtils.validatePort(-1))
        assertFalse(NetworkUtils.validatePort(65536))
        assertFalse(NetworkUtils.validatePort(100000))
        assertFalse(NetworkUtils.validatePort(-100))
    }

    @Test
    fun validatePort_withBoundaryValues_shouldReturnExpected() {
        assertTrue(NetworkUtils.validatePort(1))
        assertTrue(NetworkUtils.validatePort(65535))
        assertFalse(NetworkUtils.validatePort(0))
        assertFalse(NetworkUtils.validatePort(65536))
    }

    @Test
    fun validatePortRange_withValidRange_shouldReturnTrue() {
        assertTrue(NetworkUtils.validatePortRange(80, 443))
        assertTrue(NetworkUtils.validatePortRange(3000, 4000))
        assertTrue(NetworkUtils.validatePortRange(1, 65535))
        assertTrue(NetworkUtils.validatePortRange(1024, 65535))
    }

    @Test
    fun validatePortRange_withInvalidRange_shouldReturnFalse() {
        assertFalse(NetworkUtils.validatePortRange(443, 80))
        assertFalse(NetworkUtils.validatePortRange(4000, 3000))
        assertFalse(NetworkUtils.validatePortRange(65535, 1024))
    }

    @Test
    fun validatePortRange_withSinglePortInRange_shouldReturnTrue() {
        assertTrue(NetworkUtils.validatePortRange(80, 80))
        assertTrue(NetworkUtils.validatePortRange(8080, 8080))
    }

    @Test
    fun validatePortRange_withWellKnownPorts_shouldReturnTrue() {
        assertTrue(NetworkUtils.validatePortRange(20, 21))
        assertTrue(NetworkUtils.validatePortRange(22, 22))
        assertTrue(NetworkUtils.validatePortRange(23, 23))
        assertTrue(NetworkUtils.validatePortRange(80, 80))
        assertTrue(NetworkUtils.validatePortRange(443, 443))
    }

    @Test
    fun validatePortRange_withEphemeralPorts_shouldReturnTrue() {
        assertTrue(NetworkUtils.validatePortRange(32768, 65535))
        assertTrue(NetworkUtils.validatePortRange(49152, 65535))
    }

    @Test
    fun parseIpAddress_withValidInput_shouldReturnParsedValues() {
        val result = NetworkUtils.parseIpAddress("192.168.1.1")
        assertNotNull(result)
        assertEquals(4, result!!.size)
        assertEquals(192, result[0])
        assertEquals(168, result[1])
        assertEquals(1, result[2])
        assertEquals(1, result[3])
    }

    @Test
    fun parseIpAddress_withInvalidInput_shouldReturnNull() {
        assertNull(NetworkUtils.parseIpAddress("invalid"))
        assertNull(NetworkUtils.parseIpAddress("192.168.1"))
        assertNull(NetworkUtils.parseIpAddress("192.168.1.256"))
        assertNull(NetworkUtils.parseIpAddress(""))
    }

    @Test
    fun isPrivateIpAddress_withPrivateRanges_shouldReturnTrue() {
        assertTrue(NetworkUtils.isPrivateIpAddress("10.0.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("10.255.255.255"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.16.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("172.31.255.255"))
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.0.1"))
        assertTrue(NetworkUtils.isPrivateIpAddress("192.168.255.255"))
    }

    @Test
    fun isPrivateIpAddress_withPublicRanges_shouldReturnFalse() {
        assertFalse(NetworkUtils.isPrivateIpAddress("8.8.8.8"))
        assertFalse(NetworkUtils.isPrivateIpAddress("1.1.1.1"))
        assertFalse(NetworkUtils.isPrivateIpAddress("208.67.222.222"))
        assertFalse(NetworkUtils.isPrivateIpAddress("1.2.3.4"))
    }

    @Test
    fun isPrivateIpAddress_withLoopback_shouldReturnFalse() {
        assertFalse(NetworkUtils.isPrivateIpAddress("127.0.0.1"))
        assertFalse(NetworkUtils.isPrivateIpAddress("127.0.0.2"))
    }

    @Test
    fun isLoopbackAddress_withLoopback_shouldReturnTrue() {
        assertTrue(NetworkUtils.isLoopbackAddress("127.0.0.1"))
        assertTrue(NetworkUtils.isLoopbackAddress("127.0.0.2"))
        assertTrue(NetworkUtils.isLoopbackAddress("127.1.1.1"))
    }

    @Test
    fun isLoopbackAddress_withNonLoopback_shouldReturnFalse() {
        assertFalse(NetworkUtils.isLoopbackAddress("192.168.1.1"))
        assertFalse(NetworkUtils.isLoopbackAddress("10.0.0.1"))
        assertFalse(NetworkUtils.isLoopbackAddress("8.8.8.8"))
    }

    @Test
    fun isValidSubnetMask_withValidMasks_shouldReturnTrue() {
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.255.0"))
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.0.0"))
        assertTrue(NetworkUtils.isValidSubnetMask("255.0.0.0"))
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.255.255"))
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.255.128"))
    }

    @Test
    fun isValidSubnetMask_withInvalidMasks_shouldReturnFalse() {
        assertFalse(NetworkUtils.isValidSubnetMask("255.0.255.0"))
        assertFalse(NetworkUtils.isValidSubnetMask("255.255.0.255"))
        assertFalse(NetworkUtils.isValidSubnetMask("192.168.1.1"))
        assertFalse(NetworkUtils.isValidSubnetMask(""))
    }

    @Test
    fun validateNetworkConfig_withValidConfig_shouldReturnSuccess() {
        val result = NetworkUtils.validateNetworkConfig(
            ipAddress = "192.168.1.100",
            gateway = "192.168.1.1",
            dns = "8.8.8.8"
        )
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validateNetworkConfig_withInvalidIp_shouldReturnError() {
        val result = NetworkUtils.validateNetworkConfig(
            ipAddress = "999.999.999.999",
            gateway = "192.168.1.1",
            dns = "8.8.8.8"
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("IP"))
    }

    @Test
    fun validateNetworkConfig_withInvalidGateway_shouldReturnError() {
        val result = NetworkUtils.validateNetworkConfig(
            ipAddress = "192.168.1.100",
            gateway = "invalid",
            dns = "8.8.8.8"
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("gateway"))
    }

    @Test
    fun validateNetworkConfig_withInvalidDns_shouldReturnError() {
        val result = NetworkUtils.validateNetworkConfig(
            ipAddress = "192.168.1.100",
            gateway = "192.168.1.1",
            dns = "-1.2.3.4"
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("DNS"))
    }

    @Test
    fun validateNetworkConfig_withPrivateIpAndPublicGateway_shouldReturnError() {
        val result = NetworkUtils.validateNetworkConfig(
            ipAddress = "192.168.1.100",
            gateway = "8.8.8.8",
            dns = "8.8.8.8"
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun formatPortRange_withSinglePort_shouldFormatCorrectly() {
        val result = NetworkUtils.formatPortRange(8080, 8080)
        assertEquals("8080", result)
    }

    @Test
    fun formatPortRange_withRange_shouldFormatCorrectly() {
        val result = NetworkUtils.formatPortRange(80, 443)
        assertEquals("80-443", result)
    }

    @Test
    fun parsePortRange_withSinglePort_shouldParseCorrectly() {
        val result = NetworkUtils.parsePortRange("8080")
        assertEquals(Pair(8080, 8080), result)
    }

    @Test
    fun parsePortRange_withRange_shouldParseCorrectly() {
        val result = NetworkUtils.parsePortRange("80-443")
        assertEquals(Pair(80, 443), result)
    }

    @Test
    fun parsePortRange_withInvalidFormat_shouldReturnNull() {
        assertNull(NetworkUtils.parsePortRange(""))
        assertNull(NetworkUtils.parsePortRange("abc"))
        assertNull(NetworkUtils.parsePortRange("80-"))
        assertNull(NetworkUtils.parsePortRange("-443"))
    }
}

object NetworkUtils {

    fun validateIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    fun validatePort(port: Int): Boolean {
        return port in 1..65535
    }

    fun validatePortRange(startPort: Int, endPort: Int): Boolean {
        if (!validatePort(startPort) || !validatePort(endPort)) return false
        return startPort <= endPort
    }

    fun parseIpAddress(ip: String): List<Int>? {
        if (!validateIpAddress(ip)) return null
        return ip.split(".").map { it.toInt() }
    }

    fun isPrivateIpAddress(ip: String): Boolean {
        if (!validateIpAddress(ip)) return false
        val parts = parseIpAddress(ip) ?: return false

        return when {
            parts[0] == 10 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            else -> false
        }
    }

    fun isLoopbackAddress(ip: String): Boolean {
        if (!validateIpAddress(ip)) return false
        val parts = parseIpAddress(ip) ?: return false
        return parts[0] == 127
    }

    fun isValidSubnetMask(mask: String): Boolean {
        if (!validateIpAddress(mask)) return false
        val parts = parseIpAddress(mask) ?: return false

        var foundZero = false
        for (part in parts) {
            if (foundZero && part != 0) return false
            if (part != 0 && part != 255) return false
            if (part == 0) foundZero = true
        }
        return true
    }

    data class NetworkValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )

    fun validateNetworkConfig(
        ipAddress: String,
        gateway: String,
        dns: String
    ): NetworkValidationResult {
        if (!validateIpAddress(ipAddress)) {
            return NetworkValidationResult(false, "Invalid IP address")
        }
        if (!validateIpAddress(gateway)) {
            return NetworkValidationResult(false, "Invalid gateway")
        }
        if (!validateIpAddress(dns)) {
            return NetworkValidationResult(false, "Invalid DNS")
        }

        if (isPrivateIpAddress(ipAddress) && !isPrivateIpAddress(gateway)) {
            return NetworkValidationResult(false, "Gateway should be in same network as IP")
        }

        return NetworkValidationResult(true, null)
    }

    fun formatPortRange(startPort: Int, endPort: Int): String {
        return if (startPort == endPort) {
            startPort.toString()
        } else {
            "$startPort-$endPort"
        }
    }

    fun parsePortRange(range: String): Pair<Int, Int>? {
        if (range.isBlank()) return null
        return try {
            if (range.contains("-")) {
                val parts = range.split("-")
                if (parts.size != 2) return null
                Pair(parts[0].trim().toInt(), parts[1].trim().toInt())
            } else {
                val port = range.trim().toInt()
                Pair(port, port)
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}