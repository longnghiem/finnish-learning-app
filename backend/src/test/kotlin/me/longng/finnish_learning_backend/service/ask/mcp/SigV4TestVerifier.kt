package me.longng.finnish_learning_backend.service.ask.mcp

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Checks an AWS SigV4 signature the way AWS does, for tests: it rebuilds the signed text from what
 * actually arrived (method, path, the signed headers and the hash of the received body) and
 * computes the signature again with [secretKey].
 */
class SigV4TestVerifier(private val secretKey: String) {

    /**
     * @param headers the received headers, names in lower case, including `host`
     * @return null if the signature is right, otherwise why not
     */
    fun verify(method: String, path: String, headers: Map<String, String>, body: ByteArray): String? {
        val authorization = headers["authorization"] ?: return "no Authorization header"
        val match = AUTHORIZATION.matchEntire(authorization) ?: return "bad Authorization format"
        val (date, region, service, signedHeaders, signature) = match.destructured
        val amzDate = headers["x-amz-date"] ?: return "no X-Amz-Date header"

        val canonicalHeaders = signedHeaders.split(";").joinToString("") { name ->
            "$name:${headers[name].orEmpty().trim().replace(WHITESPACE, " ")}\n"
        }
        val canonicalRequest = listOf(method, path, "", canonicalHeaders, signedHeaders, sha256Hex(body)).joinToString("\n")
        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf("AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest.toByteArray())).joinToString("\n")

        val signingKey = listOf(date, region, service, "aws4_request")
            .fold("AWS4$secretKey".toByteArray()) { key, part -> hmac(key, part) }
        val expected = hmac(signingKey, stringToSign).toHex()
        return if (expected == signature) null else "signature mismatch"
    }

    companion object {
        private val AUTHORIZATION = Regex(
            """AWS4-HMAC-SHA256 Credential=[^/]+/(\d{8})/([^/]+)/([^/]+)/aws4_request, ?SignedHeaders=([^,]+), ?Signature=([0-9a-f]{64})""",
        )
        private val WHITESPACE = Regex("""\s+""")

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun hmac(key: ByteArray, data: String): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray())

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}