package dev.debugkit.network.internal

import android.app.Application
import android.content.Context
import dev.debugkit.DebugKit
import dev.debugkit.network.internal.db.NetworkDatabase
import dev.debugkit.network.ui.MockRulesPlugin
import dev.debugkit.network.ui.NetworkPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

/** One-time setup shared by every DebugKitInterceptor instance. */
internal object NetworkModule {

    /** Background work that outlives any screen: database writes and keeping caches in sync. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val MAX_ATTACHED_CALLS = 100

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val database = NetworkDatabase.get(context)
        TransactionStore.init(database)
        MockRuleStore.init(database)

        DebugKit.registerPlugin(NetworkPlugin)
        DebugKit.registerPlugin(MockRulesPlugin)
        DebugKit.addCrashAttachment("network.txt") {
            // Oldest first, so it reads in the order the calls happened.
            TransactionStore.latest.take(MAX_ATTACHED_CALLS).asReversed().joinToString("\n", transform = TransactionExport::callLine)
        }
        DebugKit.addCrashAttachment("mocks.txt") {
            MockRuleStore.enabledRules.joinToString("\n") { rule ->
                "${rule.name}: ${rule.method ?: "ANY"} ${rule.urlPattern} -> ${rule.type}" +
                    (if (rule.type == MockType.Replace) " ${rule.statusCode}" else "") +
                    (if (rule.delayMs > 0) " after ${rule.delayMs} ms" else "")
            }
        }

        (context.applicationContext as? Application)?.let { app ->
            MockIndicator.install(app, MockRuleStore.rules.map { rules -> rules.count { it.enabled } })
        }
    }
}
