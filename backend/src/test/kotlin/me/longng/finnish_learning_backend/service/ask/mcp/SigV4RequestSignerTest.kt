package me.longng.finnish_learning_backend.service.ask.mcp

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.URI
import java.net.http.HttpRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SigV4RequestSignerTest {

    private val endpoint = URI.create("https://abc123.lambda-url.eu-north-1.on.aws/mcp")
    private val clock = Clock.fixed(Instant.parse("2098-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""

    private fun signer(withSessionToken: Boolean = false): SigV4RequestSigner {
        val credentials = if (withSessionToken) {
            AwsSessionCredentials.create(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN)
        } else {
            AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
        }
        return SigV4RequestSigner(StaticCredentialsProvider.create(credentials), "eu-north-1", clock)
    }

    /** Signs a request the way the MCP SDK prepares one, and returns the headers it ends up with. */
    private fun sign(method: String, body: String?, signer: SigV4RequestSigner = signer()): Map<String, String> {
        val builder = HttpRequest.newBuilder(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .apply { if (body != null) header("Content-Type", "application/json") }
            .method(method, if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody())
        signer.customize(builder, method, endpoint, body, null)
        return builder.build().headers().map().entries.associate { (name, values) -> name.lowercase() to values.single() }
    }

    /** What the server receives: the request's headers plus the `host` that Java's HttpClient adds. */
    private fun arrived(headers: Map<String, String>) = headers + ("host" to endpoint.authority)

    @Test
    fun testSign_ServiceNameLambda() {
        val headers = sign("POST", body)

        val authorization = assertNotNull(headers["authorization"])
        assertTrue(
            authorization.startsWith("AWS4-HMAC-SHA256 Credential=$ACCESS_KEY/20981015/eu-north-1/lambda/aws4_request"),
            authorization,
        )
        assertEquals("20981015T120000Z", headers["x-amz-date"])
    }

    @Test
    fun testSign_IncludesPayloadHash() {
        val headers = sign("POST", body)

        assertEquals(SigV4TestVerifier.sha256Hex(body.toByteArray()), headers["x-amz-content-sha256"])
    }

    @Test
    fun testSign_NonAsciiBodyHashedAsUtf8() {
        val finnish = """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_entry","arguments":{"id":"Hyvää päivää ✓"}}}"""

        val headers = sign("POST", finnish)

        assertEquals(SigV4TestVerifier.sha256Hex(finnish.toByteArray(Charsets.UTF_8)), headers["x-amz-content-sha256"])
    }

    @Test
    fun testSign_ContentTypeSignedAcceptNot() {
        val authorization = assertNotNull(sign("POST", body)["authorization"])

        val signedHeaders = authorization.substringAfter("SignedHeaders=").substringBefore(",").split(";")
        assertTrue("content-type" in signedHeaders, authorization)
        assertTrue("host" in signedHeaders, authorization)
        assertFalse("accept" in signedHeaders, authorization)
    }

    @Test
    fun testSign_SessionTokenAdded() {
        val headers = sign("POST", body, signer(withSessionToken = true))

        assertEquals(SESSION_TOKEN, headers["x-amz-security-token"])
        assertTrue(assertNotNull(headers["authorization"]).contains("x-amz-security-token"))
    }

    @Test
    fun testSign_NoSessionTokenWithLongTermKeys() {
        assertNull(sign("POST", body)["x-amz-security-token"])
    }

    @Test
    fun testSign_GetWithoutBody() {
        val headers = sign("GET", null)

        assertNotNull(headers["authorization"])
        assertEquals(SigV4TestVerifier.sha256Hex(ByteArray(0)), headers["x-amz-content-sha256"])
        assertNull(SigV4TestVerifier(SECRET_KEY).verify("GET", "/mcp", arrived(headers), ByteArray(0)))
    }

    @Test
    fun testSign_VerifiedByAwsAlgorithm() {
        val headers = sign("POST", body, signer(withSessionToken = true))

        assertNull(SigV4TestVerifier(SECRET_KEY).verify("POST", "/mcp", arrived(headers), body.toByteArray()))
    }

    @Test
    fun testSign_TamperedBodyRejected() {
        val headers = sign("POST", body)

        // One extra space in the body that arrives: the signature no longer matches.
        assertEquals("signature mismatch", SigV4TestVerifier(SECRET_KEY).verify("POST", "/mcp", arrived(headers), "$body ".toByteArray()))
    }

    private companion object {
        // Made up for the tests: not real AWS credentials.
        const val ACCESS_KEY = "AKIDTESTONLY00000000"
        const val SECRET_KEY = "test-only-secret-not-a-real-key"
        const val SESSION_TOKEN = "test-only-session-token"
    }
}