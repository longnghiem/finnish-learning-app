package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskSession
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IpHasherTest {

    private fun hasher(key: String) = IpHasher(
        AskProperties(
            ipHashKey = key,
            limits = AskProperties.Limits(chatPerIpPerDay = 30, fitChecksPerIpPerDay = 3, sessionsPerIpPerHour = 5),
        ),
    )

    private val keyA = "a".repeat(IpHasher.MIN_KEY_LENGTH)
    private val keyB = "b".repeat(IpHasher.MIN_KEY_LENGTH)

    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    @Test
    fun testHash_StableForSameKey() {
        assertEquals(hasher(keyA).hash(ip("203.0.113.7")), hasher(keyA).hash(ip("203.0.113.7")))
    }

    @Test
    fun testHash_DiffersAcrossKeys() {
        assertNotEquals(hasher(keyA).hash(ip("203.0.113.7")), hasher(keyB).hash(ip("203.0.113.7")))
    }

    @Test
    fun testHash_DiffersAcrossIpv4Addresses() {
        assertNotEquals(hasher(keyA).hash(ip("203.0.113.7")), hasher(keyA).hash(ip("203.0.113.8")))
    }

    @Test
    fun testHash_LowercaseHexOfStoredLength() {
        val hash = hasher(keyA).hash(ip("2001:db8::1"))

        assertEquals(AskSession.IP_HASH_LENGTH, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, hash)
    }

    @Test
    fun testHash_Ipv6SamePrefixSameHash() {
        val hasher = hasher(keyA)

        // Same /64 network prefix (2001:db8:1:2), different interface parts.
        assertEquals(hasher.hash(ip("2001:db8:1:2::1")), hasher.hash(ip("2001:db8:1:2:ffff:ffff:ffff:9")))
        // Another /64.
        assertNotEquals(hasher.hash(ip("2001:db8:1:2::1")), hasher.hash(ip("2001:db8:1:3::1")))
    }

    @Test
    fun testHasher_ShortKeyFailsFast() {
        val error = assertThrows<IllegalStateException> { hasher("x".repeat(IpHasher.MIN_KEY_LENGTH - 1)) }
        assertThrows<IllegalStateException> { hasher("") }

        // The message tells what to do, and never shows the key.
        assertTrue(error.message.orEmpty().contains("ASK_IP_HASH_KEY"))
        assertFalse(error.message.orEmpty().contains("x".repeat(IpHasher.MIN_KEY_LENGTH - 1)))
    }
}