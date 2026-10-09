package dev.debugkit.internal

/**
 * Text files feature modules add to crash records, e.g. `logs.txt` from the logs module.
 * Providers run on the crashing thread, often the main thread, so they must be fast and must not
 * touch the database.
 */
internal object CrashAttachments {

    private val providers = LinkedHashMap<String, () -> String>()

    @Synchronized
    fun add(fileName: String, provider: () -> String) {
        providers[fileName] = provider
    }

    /** Every attachment that could be produced. Failing or empty ones are skipped, never thrown. */
    fun collect(): Map<String, String> {
        val snapshot = synchronized(this) { providers.toMap() }
        return snapshot.mapNotNull { (name, provider) ->
            runCatching { provider() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { name to it }
        }.toMap()
    }
}
