package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.testAskProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.io.IOException
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnstileClientTest {

    companion object {
        private val PROPERTIES = testAskProperties()
        private val VERIFY_URL = PROPERTIES.turnstile.verifyUrl
        private const val TOKEN = "token-from-the-widget"
        private val VISITOR: InetAddress = InetAddress.getByName("203.0.113.7")
    }

    // MockRestServiceServer replaces the HTTP layer of the RestClient built from this builder:
    // no request leaves the machine, and each test says what "Cloudflare" answers.
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()

    private fun client(properties: AskProperties = PROPERTIES) = TurnstileClient(builder.build(), properties)

    private fun expectSiteverify(): ResponseActions =
        server.expect(requestTo(VERIFY_URL)).andExpect(method(HttpMethod.POST))

    private fun json(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    @Test
    fun testVerify_Success() {
        expectSiteverify().andRespond(json("""{"success":true,"error-codes":[],"hostname":"opisuomea.org"}"""))

        assertTrue(client().verify(TOKEN, VISITOR))
        server.verify()
    }

    @Test
    fun testVerify_SendsRemoteIp() {
        // Exactly these form fields: the secret, the visitor's token and the visitor's IP.
        val expectedForm = LinkedMultiValueMap<String, String>().apply {
            add("secret", PROPERTIES.turnstile.secretKey)
            add("response", TOKEN)
            add("remoteip", "203.0.113.7")
        }
        expectSiteverify()
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formData(expectedForm))
            .andRespond(json("""{"success":true}"""))

        client().verify(TOKEN, VISITOR)
        server.verify()
    }

    @Test
    fun testVerify_Rejected() {
        // A token that was already used: the visitor's problem, not ours.
        expectSiteverify().andRespond(json("""{"success":false,"error-codes":["timeout-or-duplicate"]}"""))

        assertFalse(client().verify(TOKEN, VISITOR))
    }

    @Test
    fun testVerify_RejectedWithClientErrorStatus() {
        // Even with a 4xx status, the body's error codes decide.
        expectSiteverify().andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"success":false,"error-codes":["invalid-input-response"]}"""),
        )

        assertFalse(client().verify(TOKEN, VISITOR))
    }

    @Test
    fun testVerify_WrongSecretThrows() {
        // Every visitor would fail with this secret: a server problem, not a failed bot check.
        // Cloudflare answers a wrong secret with HTTP 400.
        expectSiteverify().andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"success":false,"error-codes":["invalid-input-secret"]}"""),
        )

        assertThrows<TurnstileVerificationException> { client().verify(TOKEN, VISITOR) }
    }

    @Test
    fun testVerify_UpstreamError() {
        expectSiteverify().andRespond(withServerError())

        assertThrows<TurnstileVerificationException> { client().verify(TOKEN, VISITOR) }
    }

    @Test
    fun testVerify_NetworkFailure() {
        expectSiteverify().andRespond(withException(IOException("Connection reset")))

        assertThrows<TurnstileVerificationException> { client().verify(TOKEN, VISITOR) }
    }

    @Test
    fun testVerify_MissingSecretThrowsWithoutCalling() {
        val noSecret = PROPERTIES.copy(turnstile = PROPERTIES.turnstile.copy(secretKey = ""))

        assertThrows<TurnstileVerificationException> { client(noSecret).verify(TOKEN, VISITOR) }
        server.verify() // no request was expected, and none was sent
    }

    @Test
    fun testVerify_InvalidTokenRejectedWithoutCalling() {
        assertThrows<IllegalArgumentException> { client().verify(" ", VISITOR) }
        assertThrows<IllegalArgumentException> {
            client().verify("x".repeat(TurnstileClient.MAX_TOKEN_LENGTH + 1), VISITOR)
        }
        server.verify()
    }
}