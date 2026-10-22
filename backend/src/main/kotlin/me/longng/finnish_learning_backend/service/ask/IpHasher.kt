package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskSession
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a visitor's IP address into the keyed hash that is stored and compared instead of the
 * address ([AskSession.ipHash]). Raw IP addresses are never stored or logged.
 *
 * **Why a keyed hash (HMAC-SHA256) and not plain SHA-256:** there are only about 4.3 billion
 * IPv4 addresses, so a plain hash can be reversed by hashing all of them, which takes minutes.
 * Without the secret key ([AskProperties.ipHashKey]) that is impossible.
 *
 * **IPv6:** an internet connection usually gets a whole /64 block of IPv6 addresses and can
 * switch between them freely. So for IPv6 only the first 64 bits (the network prefix) are
 * hashed: every address of one connection gets the same hash, and per-IP limits can't be
 * dodged by switching addresses. IPv4 addresses are hashed in full.
 *
 * **Changing the key** gives every visitor a new hash: running sessions stop matching their
 * visitor (who then starts a new one), and per-IP counts start again from zero. Old hashes can
 * no longer be linked to anyone.
 *
 * @throws IllegalStateException at construction (so at application startup) if the key is
 *   shorter than [MIN_KEY_LENGTH] characters, e.g. because `ASK_IP_HASH_KEY` isn't set
 */
@Component
class IpHasher(properties: AskProperties) {

    private val key: SecretKeySpec

    init {
        val keyText = properties.ipHashKey
        // The message never contains the key itself.
        check(keyText.length >= MIN_KEY_LENGTH) {
            "app.ask.ip-hash-key (env ASK_IP_HASH_KEY) must be at least $MIN_KEY_LENGTH characters, " +
                    "got ${keyText.length}. Generate one with: openssl rand -hex 32"
        }
        key = SecretKeySpec(keyText.toByteArray(Charsets.UTF_8), ALGORITHM)
    }

    /** The hash of [address], as [AskSession.IP_HASH_LENGTH] lowercase hex characters. */
    fun hash(address: InetAddress): String {
        val identity = when (address) {
            is Inet4Address -> address.address
            is Inet6Address -> address.address.copyOf(IPV6_PREFIX_BYTES)
        }
        // A new Mac per call: Mac objects aren't thread-safe, and creating one takes microseconds.
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return HexFormat.of().formatHex(mac.doFinal(identity))
    }

    companion object {
        /** Shortest accepted key. `openssl rand -hex 32` gives 64 characters (256 bits). */
        const val MIN_KEY_LENGTH = 32

        private const val ALGORITHM = "HmacSHA256"

        /** 64 bits: the network prefix of an IPv6 address. */
        private const val IPV6_PREFIX_BYTES = 8
    }
}