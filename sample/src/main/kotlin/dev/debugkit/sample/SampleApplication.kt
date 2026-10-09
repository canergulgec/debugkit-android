package dev.debugkit.sample

import android.app.Application
import android.widget.Toast
import dev.debugkit.DebugKit
import dev.debugkit.logs.DebugKitTree
import dev.debugkit.network.DebugKitInterceptor
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.UUID

class SampleApplication : Application() {

    lateinit var httpClient: OkHttpClient
        private set

    /** Stands in for the app's own state that Quick Actions shows and changes. */
    var sessionId: String? = UUID.randomUUID().toString()
    var verboseLogging = false

    override fun onCreate() {
        super.onCreate()
        DebugKit.install(this) {
            shakeToOpen = true
            environments {
                environment("JSONPlaceholder", "https://jsonplaceholder.typicode.com")
                environment("httpbin", "https://httpbin.org")
            }
            flags {
                boolean("new_checkout", "New checkout", description = "Shows the redesigned checkout")
                choice("onboarding_variant", "Onboarding variant", options = listOf("A", "B", "C"))
                text("promo_code", "Promo code")
            }
            actions {
                section("Session")
                info("Session ID") { sessionId }
                button("Log out", summary = "Clears the session") {
                    sessionId = null
                    Toast.makeText(this@SampleApplication, "Logged out", Toast.LENGTH_SHORT).show()
                }
                section("Diagnostics")
                toggle("Verbose logging", isChecked = { verboseLogging }, onCheckedChange = { verboseLogging = it })
                button("Send sample requests") { SampleRequests.sendAll(httpClient) }
                button("Write sample logs", summary = "One of each level, plus an exception") { writeSampleLogs() }
                button("Crash the app", summary = "Throws on the main thread; reopen the app to see the report") {
                    Timber.w("About to crash on purpose")
                    throw IllegalStateException("Sample crash from Quick Actions", IllegalArgumentException("Cart is empty"))
                }
            }
        }
        // DebugKitTree resolves to :logs in debug and :noop in release.
        Timber.plant(DebugKitTree())
        Timber.i("App started, session %s", sessionId)
        // Built at startup, like most apps do through DI, so the Network and Mock screens and the
        // MOCK ON indicator are ready from the first screen. DebugKitInterceptor resolves to
        // :network in debug and :noop in release.
        httpClient = OkHttpClient.Builder()
            .addInterceptor(DebugKitInterceptor(this))
            .build()
    }

    private fun writeSampleLogs() {
        Timber.v("Verbose: cache lookup for key=user_42")
        Timber.d("Debug: rendering %d items", 12)
        Timber.tag("Checkout").i("Info: order placed, total=%.2f", 149.90)
        Timber.tag("Checkout").w("Warning: payment took %d ms", 4200)
        Timber.e(IllegalStateException("Cart is empty"), "Error: checkout failed")
    }

    /** The URL the app was built against; DebugKit can point debug builds elsewhere. */
    val baseUrl: String get() = DebugKit.environmentUrl(fallback = "https://api.example.com")
}
