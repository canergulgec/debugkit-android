package dev.debugkit.logs

import dev.debugkit.logs.internal.LogEntry
import dev.debugkit.logs.internal.LogStore
import dev.debugkit.logs.internal.LogsModule
import timber.log.Timber

/**
 * Records Timber logs for DebugKit's Logs screen. Plant it next to the app's own trees:
 * `Timber.plant(DebugKitTree())`. It does not write to logcat, so keep a `DebugTree` planted
 * if you also want logcat output.
 */
public class DebugKitTree @JvmOverloads constructor(configure: LogsConfig.() -> Unit = {}) : Timber.Tree() {

    private val config = LogsConfig().apply(configure)

    init {
        LogsModule.init()
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= config.minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        LogStore.add(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                priority = priority,
                tag = tag ?: callerTag(),
                // Timber has already appended the stack trace of [t] to the message.
                message = message,
                thread = Thread.currentThread().name,
            ),
        )
    }

    /**
     * Same idea as Timber's DebugTree: without an explicit tag, use the calling class's simple
     * name, skipping Timber's and this tree's own frames and anonymous-class suffixes.
     */
    private fun callerTag(): String? = Throwable().stackTrace
        .firstOrNull { frame -> frame.className != javaClass.name && !frame.className.startsWith("timber.log.") }
        ?.className
        ?.substringAfterLast('.')
        ?.substringBefore('$')
}
