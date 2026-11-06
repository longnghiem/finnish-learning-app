package me.longng.finnish_learning_backend.service.ask.mcp

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer
import io.modelcontextprotocol.common.McpTransportContext
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.ContentStreamProvider
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner
import java.net.URI
import java.net.http.HttpRequest
import java.time.Clock

/**
 * Signs every request to the profile server with AWS Signature Version 4, so the Lambda function
 * URL (auth type `AWS_IAM`) lets it through. AWS refuses an unsigned or wrongly signed request
 * with 403 before the Lambda runs.
 *
 * The MCP SDK calls [customize] for every request it sends, with the **exact body** it is about to
 * send, so the signature covers the body: a request changed on the way is refused. The GET the
 * client sends after connecting has no body; it is signed too.
 *
 * Signed: `Content-Type` and the headers the signer adds itself (`Host`, `X-Amz-Date`,
 * `x-amz-content-sha256`, and `X-Amz-Security-Token` with temporary credentials). The other MCP
 * headers (`Accept`, `Cache-Control`, `MCP-Protocol-Version`) are left unsigned; AWS doesn't
 * require them to be signed.
 *
 * The SDK may call [customize] on one of its own threads. Getting credentials can block briefly
 * (a refresh from the EC2 instance metadata), which is fine there.
 *
 * @param credentialsProvider where the AWS credentials come from: [DefaultCredentialsProvider] in
 *   the app (the EC2 instance profile in production, the local AWS profile in development)
 * @param region the Lambda's region, part of the signature
 * @param clock the time put into the signature. AWS refuses a signature more than 15 minutes off
 */
class SigV4RequestSigner(
    private val credentialsProvider: AwsCredentialsProvider,
    private val region: String,
    private val clock: Clock = Clock.systemUTC(),
) : McpSyncHttpClientRequestCustomizer {

    private val signer = AwsV4HttpSigner.create()

    override fun customize(
        builder: HttpRequest.Builder,
        method: String,
        endpoint: URI,
        body: String?,
        context: McpTransportContext?,
    ) {
        val contentType = builder.copy().build().headers().firstValue(CONTENT_TYPE).orElse(null)
        val unsigned = SdkHttpRequest.builder()
            .method(SdkHttpMethod.fromValue(method))
            .uri(endpoint)
            .apply { if (contentType != null) putHeader(CONTENT_TYPE, contentType) }
            .build()

        val signed = signer.sign { request ->
            request.identity(credentialsProvider.resolveCredentials())
                .request(unsigned)
                .apply { if (body != null) payload(ContentStreamProvider.fromUtf8String(body)) }
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE_SIGNING_NAME)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock)
        }

        // Copy the signing headers onto the real request, except Host (Java's HttpClient refuses to
        // have it set and derives the same value from the URL) and Content-Type (already there).
        signed.request().headers()
            .filterKeys { !it.equals(HOST, ignoreCase = true) && !it.equals(CONTENT_TYPE, ignoreCase = true) }
            .forEach { (name, values) -> values.forEach { builder.setHeader(name, it) } }
    }

    companion object {
        /** The AWS service a Lambda function URL belongs to, for the signature. */
        const val SERVICE_SIGNING_NAME = "lambda"

        private const val CONTENT_TYPE = "Content-Type"
        private const val HOST = "Host"
    }
}