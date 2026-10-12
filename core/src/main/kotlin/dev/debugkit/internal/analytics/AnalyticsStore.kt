package dev.debugkit.internal.analytics

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import dev.debugkit.internal.CrashAttachments
import dev.debugkit.internal.PluginRegistry
import dev.debugkit.plugin.AnalyticsPlugin
import java.util.concurrent.atomic.AtomicLong

/**
 * Recorded events, newest first, and the latest value of each user property, in memory.
 * Apps record from any thread; state changes happen on the main thread so the screen can
 * observe them safely.
 */
internal object AnalyticsStore {

    private const val MAX_EVENTS = 500
    private const val MAX_ATTACHED_EVENTS = 100

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextId = AtomicLong()

    val events = mutableStateListOf<AnalyticsEvent>()

    /** Keyed by "provider/name". */
    val userProperties = mutableStateMapOf<String, UserProperty>()

    @Volatile
    private var registered = false

    fun recordEvent(provider: String, name: String, params: Map<String, Any?>) {
        val timestamp = System.currentTimeMillis()
        val issues = if (FirebaseRules.appliesTo(provider)) FirebaseRules.eventIssues(name, params) else emptyList()
        onMain {
            val duplicate = events.firstOrNull { it.provider == provider && it.name == name }
                ?.let { DuplicateCheck.isDuplicate(it, timestamp, params) } == true
            events.add(0, AnalyticsEvent(nextId.incrementAndGet(), timestamp, provider, name, params, issues, duplicate))
            while (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
        }
    }

    fun recordUserProperty(provider: String, name: String, value: String?) {
        val issues = if (FirebaseRules.appliesTo(provider)) FirebaseRules.propertyIssues(name, value) else emptyList()
        val property = UserProperty(provider, name, value, System.currentTimeMillis(), issues)
        onMain { userProperties["$provider/$name"] = property }
    }

    fun clear() {
        events.clear()
    }

    /**
     * Firebase hands events over as Bundles. Nested Bundles and Bundle arrays (ecommerce items)
     * become Maps and Lists so they display and compare like everything else.
     */
    fun bundleToMap(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()
        return bundle.keySet().sorted().associateWith { key ->
            @Suppress("DEPRECATION")
            normalize(bundle.get(key))
        }
    }

    /** Same shapes as [bundleToMap] produces, so events recorded either way compare equal. */
    fun normalize(params: Map<String, Any?>): Map<String, Any?> = params.mapValues { normalize(it.value) }

    private fun normalize(value: Any?): Any? = when (value) {
        is Bundle -> bundleToMap(value)
        is Array<*> -> value.map(::normalize)
        is Map<*, *> -> value.entries.associate { it.key.toString() to normalize(it.value) }
        is Collection<*> -> value.map(::normalize)
        is Int -> value.toLong()
        is Float -> value.toDouble()
        is CharSequence -> value.toString()
        else -> value
    }

    private fun onMain(block: () -> Unit) {
        if (!registered) register()
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** The screen appears with the first recording, so apps without analytics never see it. */
    @Synchronized
    private fun register() {
        if (registered) return
        registered = true
        PluginRegistry.register(AnalyticsPlugin)
        CrashAttachments.add("analytics.txt") {
            // Oldest first, so it reads in the order the events happened.
            events.toList().take(MAX_ATTACHED_EVENTS).asReversed().joinToString("\n") { event ->
                "${AnalyticsFormat.time(event.timestamp)} ${event.provider} ${event.name} ${AnalyticsFormat.preview(event.params)}"
            }
        }
    }
}
