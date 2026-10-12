package dev.debugkit.internal.crash

import java.io.File
import java.util.Properties

/**
 * One folder per crash under [root]: `crash.txt`, any attachments (`logs.txt`, `network.txt`, …)
 * and `meta.properties` for the list screen. Plain files, so writing works even while the
 * process is going down and nothing else can be trusted.
 */
internal class CrashStore(private val root: File) {

    data class Crash(
        val id: String,
        val timestamp: Long,
        val thread: String,
        val exceptionClass: String,
        val message: String?,
        val seen: Boolean,
        val dir: File,
    ) {
        /** The exception's simple name, e.g. `IllegalStateException`. */
        val shortName: String get() = exceptionClass.substringAfterLast('.')

        /** Attachment file names, in a stable order; `crash.txt` itself is not included. */
        val attachments: List<String>
            get() = dir.list()?.filter { it != CRASH_FILE && it != META_FILE }?.sorted().orEmpty()
    }

    fun save(timestamp: Long, thread: String, throwable: Throwable, report: String, attachments: Map<String, String>): Crash {
        val id = "crash-$timestamp"
        val dir = File(root, id).apply { mkdirs() }
        File(dir, CRASH_FILE).writeText(report)
        attachments.forEach { (name, content) -> File(dir, name).writeText(content) }
        writeMeta(
            dir,
            Properties().apply {
                setProperty(KEY_TIMESTAMP, timestamp.toString())
                setProperty(KEY_THREAD, thread)
                setProperty(KEY_EXCEPTION, throwable.javaClass.name)
                throwable.message?.let { setProperty(KEY_MESSAGE, it) }
                setProperty(KEY_SEEN, "false")
            },
        )
        return read(dir)!!
    }

    /** Newest first. */
    fun list(): List<Crash> = root.listFiles { file -> file.isDirectory && file.name.startsWith("crash-") }
        ?.mapNotNull(::read)
        ?.sortedByDescending { it.timestamp }
        .orEmpty()

    fun read(crash: Crash, fileName: String): String? = File(crash.dir, fileName).takeIf { it.isFile }?.readText()

    fun report(crash: Crash): String = read(crash, CRASH_FILE).orEmpty()

    fun markSeen(crash: Crash) {
        val meta = loadMeta(crash.dir) ?: return
        meta.setProperty(KEY_SEEN, "true")
        writeMeta(crash.dir, meta)
    }

    fun delete(crash: Crash) {
        crash.dir.deleteRecursively()
    }

    fun deleteAll() {
        list().forEach(::delete)
    }

    /** Keeps the newest [keep] crashes. */
    fun trim(keep: Int) {
        list().drop(keep).forEach(::delete)
    }

    private fun read(dir: File): Crash? {
        val meta = loadMeta(dir) ?: return null
        return Crash(
            id = dir.name,
            timestamp = meta.getProperty(KEY_TIMESTAMP)?.toLongOrNull() ?: return null,
            thread = meta.getProperty(KEY_THREAD).orEmpty(),
            exceptionClass = meta.getProperty(KEY_EXCEPTION).orEmpty(),
            message = meta.getProperty(KEY_MESSAGE),
            seen = meta.getProperty(KEY_SEEN) == "true",
            dir = dir,
        )
    }

    private fun loadMeta(dir: File): Properties? {
        val file = File(dir, META_FILE).takeIf { it.isFile } ?: return null
        return Properties().apply { file.inputStream().use(::load) }
    }

    private fun writeMeta(dir: File, meta: Properties) {
        File(dir, META_FILE).outputStream().use { meta.store(it, null) }
    }

    companion object {
        const val CRASH_FILE = "crash.txt"
        private const val META_FILE = "meta.properties"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_THREAD = "thread"
        private const val KEY_EXCEPTION = "exception"
        private const val KEY_MESSAGE = "message"
        private const val KEY_SEEN = "seen"
    }
}
