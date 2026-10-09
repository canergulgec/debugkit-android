package dev.debugkit.network

import android.content.Context
import dev.debugkit.network.internal.BodyReader
import dev.debugkit.network.internal.HttpTransaction
import dev.debugkit.network.internal.MockResponder
import dev.debugkit.network.internal.MockRuleStore
import dev.debugkit.network.internal.NetworkModule
import dev.debugkit.network.internal.NetworkNotification
import dev.debugkit.network.internal.TransactionStore
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Records every call made through the OkHttpClient it is added to, and answers calls that match
 * an enabled mock rule. Both show up in DebugKit under Network and Mock Responses.
 *
 * Add it with `addInterceptor` (not `addNetworkInterceptor`), after the app's own interceptors,
 * so it records the request exactly as sent and the body after OkHttp has decompressed it.
 */
public class DebugKitInterceptor @JvmOverloads constructor(
    context: Context,
    configure: NetworkConfig.() -> Unit = {},
) : Interceptor {

    private val config = NetworkConfig().apply(configure)
    private val redacted = config.redactHeaders.map { it.lowercase() }.toSet()

    init {
        NetworkModule.init(context)
        if (config.showNotification) NetworkNotification.start(context)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body
        val mockRule = MockRuleStore.match(request.method, request.url.toString())

        val id = TransactionStore.add(
            HttpTransaction(
                requestedAt = System.currentTimeMillis(),
                method = request.method,
                url = request.url.toString(),
                requestHeaders = request.headers.toList(),
                requestContentType = requestBody?.contentType()?.toString(),
                requestBody = BodyReader.readRequest(requestBody, config.maxContentLength),
                mockRuleName = mockRule?.name,
            ),
        )

        val startNs = System.nanoTime()
        val response = try {
            if (mockRule != null) MockResponder.respond(mockRule, chain) else chain.proceed(request)
        } catch (e: Exception) {
            TransactionStore.update(id) { it.copy(durationMs = elapsedMs(startNs), error = e.toString()) }
            throw e
        }
        val durationMs = elapsedMs(startNs)
        val capturedResponse = BodyReader.readResponse(response, config.maxContentLength)

        TransactionStore.update(id) {
            it.copy(
                durationMs = durationMs,
                protocol = response.protocol.toString(),
                responseCode = response.code,
                responseMessage = response.message,
                responseHeaders = response.headers.toList(),
                responseContentType = response.body?.contentType()?.toString(),
                responseBody = capturedResponse,
            )
        }
        return response
    }

    private fun Headers.toList(): List<Pair<String, String>> = (0 until size).map { i ->
        val name = name(i)
        name to if (name.lowercase() in redacted) REDACTED else value(i)
    }

    private fun elapsedMs(startNs: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

    private companion object {
        const val REDACTED = "••••"
    }
}
