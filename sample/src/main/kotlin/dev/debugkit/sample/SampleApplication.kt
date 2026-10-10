package dev.debugkit.sample

import android.app.Application
import android.widget.Toast
import dev.debugkit.DebugKit
import dev.debugkit.logs.DebugKitTree
import dev.debugkit.network.DebugKitInterceptor
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlinx.coroutines.delay
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
            push {
                // A real app returns FirebaseMessaging.getInstance().token.await().
                token {
                    delay(300)
                    "sample-fcm-token-6f1c2a9e:APA91bH_example"
                }
                onTestMessage { data -> Toast.makeText(this@SampleApplication, "Push handler got $data", Toast.LENGTH_LONG).show() }
                template("Order shipped", "Your order is on its way", mapOf("type" to "order", "orderId" to "123"))
                template("Product on sale", "Tap to see the product", link = "debugkit-sample://product/77?source=push")
            }
            deepLinks {
                link("Product 123", "debugkit-sample://product/123?source=push")
                link("Product page on the web", "https://debugkit.example.com/product/123")
                link("Internal screen (not browsable)", "debugkit-sample://internal/settings")
                link("Unknown screen", "debugkit-sample://cart")
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
                button("Send sample analytics", summary = "A purchase, a mis-named event and a double-fired one") { sendSampleAnalytics() }
                button("Simulate a received push", summary = "What FirebaseMessagingService would record") {
                    DebugKit.recordPush("Folder shared", "Ayşe shared a folder with you", mapOf("type" to "shared_folder", "folderId" to "abc"))
                    Toast.makeText(this@SampleApplication, "Push recorded. See Push → Received.", Toast.LENGTH_SHORT).show()
                }
                button("Crash the app", summary = "Throws on the main thread; reopen the app to see the report") {
                    Timber.w("About to crash on purpose")
                    throw IllegalStateException("Sample crash from Quick Actions", IllegalArgumentException("Cart is empty"))
                }
            }
        }
        // DebugKitTree resolves to :logs in debug and :noop in release.
        Timber.plant(DebugKitTree())
        Timber.i("App started, session %s", sessionId)
        DebugKit.recordUserProperty("plan", "premium")
        // Built at startup, like most apps do through DI, so the Network and Mock screens and the
        // MOCK ON indicator are ready from the first screen. DebugKitInterceptor resolves to
        // :network in debug and :noop in release.
        httpClient = OkHttpClient.Builder()
            .addInterceptor(DebugKitInterceptor(this))
            .build()
    }

    /** What an app's analytics wrapper would send; each call stands next to the real SDK call. */
    private fun sendSampleAnalytics() {
        DebugKit.recordAnalyticsEvent(
            "purchase",
            mapOf(
                "currency" to "TRY",
                "value" to 149.90,
                "items" to listOf(mapOf("item_id" to "SKU-1", "item_name" to "Cloud 1 TB", "quantity" to 1)),
            ),
        )
        // Breaks Firebase's naming rules: Firebase would drop it without an error.
        DebugKit.recordAnalyticsEvent("Add To Cart", mapOf("item_id" to "SKU-1"))
        // Fired twice by mistake.
        repeat(2) { DebugKit.recordAnalyticsEvent("share_click", mapOf("target" to "whatsapp")) }
        DebugKit.recordAnalyticsEvent("install_attributed", mapOf("network" to "organic"), provider = "Adjust")
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
