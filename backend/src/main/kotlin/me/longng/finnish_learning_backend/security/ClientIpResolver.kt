package me.longng.finnish_learning_backend.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Finds the IP address of the visitor who sent a request.
 *
 * In production the backend sits behind nginx on the same host, so [HttpServletRequest.getRemoteAddr]
 * is always the loopback address. nginx passes the real address in the `X-Real-IP` header, set
 * to `$remote_addr` (the TCP peer nginx itself saw), which overwrites any `X-Real-IP` the client
 * sent. The header is therefore trusted **only when the request comes from loopback**, i.e.
 * through the local nginx. A request from anywhere else uses its own TCP address, so a client
 * that reaches the backend directly can't pick its identity by sending the header.
 *
 * `X-Forwarded-For` is never read: nginx *appends* to it, so its first entries are whatever the
 * client sent. For the same reason `server.forward-headers-strategy` is deliberately not set:
 * reading one header that nginx overwrites is simpler to reason about and to test.
 *
 * Header text is checked before it is parsed: [InetAddress.getByName] falls back to a DNS
 * lookup for text that isn't an IP literal, and the header must never trigger one.
 */
@Component
class ClientIpResolver {

    private val logger = LoggerFactory.getLogger(ClientIpResolver::class.java)

    /**
     * The visitor's IP address: `X-Real-IP` for requests through the local nginx, otherwise the
     * TCP peer. If a loopback request has no valid `X-Real-IP` (local development without nginx,
     * or a broken nginx config), the loopback address itself is returned.
     */
    fun resolve(request: HttpServletRequest): InetAddress {
        val peer = checkNotNull(parseIpLiteral(request.remoteAddr)) {
            "The servlet container reported a remote address that isn't an IP literal"
        }
        if (!peer.isLoopbackAddress) {
            return peer
        }
        val realIp = request.getHeader(REAL_IP_HEADER) ?: return peer
        return parseIpLiteral(realIp) ?: peer.also {
            // The value itself isn't logged: it may be a visitor's IP address.
            logger.warn("Ignoring an invalid {} header on a request from loopback", REAL_IP_HEADER)
        }
    }

    /**
     * Parses an IPv4 or IPv6 literal without ever doing a DNS lookup, or returns null. An IPv6
     * zone suffix (`%eth0`) is dropped: it names a local network interface, not a visitor.
     */
    private fun parseIpLiteral(text: String?): InetAddress? {
        val candidate = text?.trim()?.substringBefore('%') ?: return null
        val looksLikeLiteral = when {
            candidate.contains(':') -> IPV6_CHARS.matches(candidate)
            else -> IPV4.matches(candidate) && candidate.split('.').all { it.toInt() <= 255 }
        }
        if (!looksLikeLiteral) {
            return null
        }
        return try {
            InetAddress.getByName(candidate)
        } catch (_: UnknownHostException) {
            null
        }
    }

    private companion object {
        const val REAL_IP_HEADER = "X-Real-IP"

        /** Four dot-separated numbers of up to three digits; each is checked to be at most 255. */
        val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        /**
         * Only the characters an IPv6 literal can contain (hex digits, colons, and dots for an
         * embedded IPv4 address), at most 45 of them, starting with a hex digit or a colon. The
         * JDK parses text of this shape as a literal and never looks it up; text starting with
         * anything else (even a dot) can fall through to DNS.
         */
        val IPV6_CHARS = Regex("""[0-9A-Fa-f:][0-9A-Fa-f:.]{1,44}""")
    }
}