package dev.debugkit.internal.crash

import android.app.Application
import android.os.Process
import androidx.compose.runtime.mutableIntStateOf
import dev.debugkit.internal.CrashAttachments
import dev.debugkit.internal.appSummary
import java.io.File
import kotlin.system.exitProcess

/**
 * Records uncaught exceptions with the context that led to them, then hands them on to the
 * handler that was installed before (Crashlytics, or Android's default), so crash reporting
 * and the system crash dialog keep working unchanged.
 */
internal object CrashReporter {

    private const val MAX_CRASHES = 20

    lateinit var store: CrashStore
        private set

    /** False when the app turned crash capture off. */
    val isInstalled: Boolean get() = ::store.isInitialized

    /** Bumped on every change, so the Crashes screen knows to re-read the folder. */
    val version = mutableIntStateOf(0)

    fun install(application: Application, showDialogOnNextLaunch: Boolean) {
        store = CrashStore(File(application.filesDir, "debugkit/crashes"))
        store.trim(MAX_CRASHES)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(application, thread, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }

        if (showDialogOnNextLaunch) {
            store.list().firstOrNull()?.takeIf { !it.seen }?.let { CrashDialog.showOnNextScreen(application, it) }
        }
    }

    fun markSeen(crash: CrashStore.Crash) {
        store.markSeen(crash)
        version.intValue++
    }

    fun delete(crash: CrashStore.Crash) {
        store.delete(crash)
        version.intValue++
    }

    fun deleteAll() {
        store.deleteAll()
        version.intValue++
    }

    /** Never throws: a failure here must not hide the original crash from the next handler. */
    private fun record(application: Application, thread: Thread, throwable: Throwable) {
        try {
            val now = System.currentTimeMillis()
            val details = runCatching { appSummary(application) }.getOrDefault(emptyList())
            val report = CrashFormatter.format(now, thread.name, throwable, details)
            store.save(now, thread.name, throwable, report, CrashAttachments.collect())
        } catch (_: Throwable) {
            // Nothing sensible to do while the process is dying.
        }
    }
}
