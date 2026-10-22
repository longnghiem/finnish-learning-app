package me.longng.finnish_learning_backend.security

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.net.InetAddress
import kotlin.test.assertEquals

class ClientIpResolverTest {

    private val resolver = ClientIpResolver()

    private fun request(remoteAddr: String, vararg headers: Pair<String, String>) =
        MockHttpServletRequest().apply {
            this.remoteAddr = remoteAddr
            headers.forEach { (name, value) -> addHeader(name, value) }
        }

    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    @Test
    fun testResolve_LoopbackUsesXRealIp() {
        val resolved = resolver.resolve(request("127.0.0.1", "X-Real-IP" to "203.0.113.7"))

        assertEquals(ip("203.0.113.7"), resolved)
    }

    @Test
    fun testResolve_Ipv6LoopbackUsesXRealIp() {
        // Tomcat reports the IPv6 loopback in its long form.
        val resolved = resolver.resolve(request("0:0:0:0:0:0:0:1", "X-Real-IP" to "2001:db8::7"))

        assertEquals(ip("2001:db8::7"), resolved)
    }

    @Test
    fun testResolve_NonLoopbackIgnoresHeaders() {
        // A client reaching the backend directly can't choose its identity.
        val resolved = resolver.resolve(
            request("198.51.100.4", "X-Real-IP" to "203.0.113.7", "X-Forwarded-For" to "203.0.113.8"),
        )

        assertEquals(ip("198.51.100.4"), resolved)
    }

    @Test
    fun testResolve_IgnoresXForwardedFor() {
        val resolved = resolver.resolve(request("127.0.0.1", "X-Forwarded-For" to "203.0.113.8, 10.0.0.1"))

        assertEquals(ip("127.0.0.1"), resolved)
    }

    @Test
    fun testResolve_LoopbackWithoutHeaderUsesLoopback() {
        // Local development: the frontend calls the backend directly, without nginx.
        assertEquals(ip("127.0.0.1"), resolver.resolve(request("127.0.0.1")))
    }

    @Test
    fun testResolve_InvalidXRealIpFallsBackToLoopback() {
        // None of these may be looked up in DNS; all are rejected before parsing.
        listOf("example.com", "zz:zz", ".:", "1.2.3.256", "1.2.3", "", "203.0.113.7, 10.0.0.1").forEach { header ->
            assertEquals(ip("127.0.0.1"), resolver.resolve(request("127.0.0.1", "X-Real-IP" to header)), header)
        }
    }

    @Test
    fun testResolve_Ipv4MappedIpv6IsIpv4() {
        val resolved = resolver.resolve(request("127.0.0.1", "X-Real-IP" to "::ffff:203.0.113.7"))

        assertEquals(ip("203.0.113.7"), resolved)
    }
}