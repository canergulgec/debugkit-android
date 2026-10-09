package dev.debugkit.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Release stand-in for `:network`'s DebugKitInterceptor. Passes every call straight through,
 * so neither recording nor mock rules can ever affect a release build.
 */
public class DebugKitInterceptor @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") configure: NetworkConfig.() -> Unit = {},
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
