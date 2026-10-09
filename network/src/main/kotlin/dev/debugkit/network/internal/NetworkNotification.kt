package dev.debugkit.network.internal

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.debugkit.DebugKit
import dev.debugkit.network.R
import dev.debugkit.network.ui.NetworkPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Silent notification with this session's call count and the latest calls, like Chucker's.
 * Tapping it opens the Network screen; its Clear action empties the list.
 */
internal object NetworkNotification {

    private const val CHANNEL_ID = "debugkit_network"
    private const val NOTIFICATION_ID = 0x0DEB
    private const val MAX_LINES = 5

    /** Android drops updates beyond a few per second, so bursts of calls are coalesced. */
    private const val MIN_UPDATE_INTERVAL_MS = 500L

    @Volatile
    var isEnabled: Boolean = false
        private set

    @Volatile
    private var latest: List<TransactionSummary> = emptyList()

    @Synchronized
    fun start(context: Context) {
        if (isEnabled) return
        isEnabled = true
        val app = context.applicationContext
        createChannel(app)

        // Only calls from this launch count; older ones are kept for history but would make the
        // number meaningless.
        val sessionStart = System.currentTimeMillis()
        NetworkModule.scope.launch {
            TransactionStore.summaries
                .map { all -> all.filter { it.requestedAt >= sessionStart } }
                .distinctUntilChanged()
                .conflate()
                .collect { session ->
                    latest = session
                    show(app, session)
                    delay(MIN_UPDATE_INTERVAL_MS)
                }
        }
    }

    /** Re-posts the current state, e.g. right after the user grants the permission. */
    fun refresh(context: Context) {
        if (isEnabled) show(context.applicationContext, latest)
    }

    fun line(tx: TransactionSummary): String {
        val mock = if (tx.mockRuleName != null) "[MOCK] " else ""
        return "$mock${tx.statusLabel} ${tx.method} ${tx.pathWithQuery}"
    }

    // canPost() checks the permission; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    private fun show(context: Context, session: List<TransactionSummary>) {
        val manager = NotificationManagerCompat.from(context)
        if (session.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPost(context)) return

        val style = NotificationCompat.InboxStyle()
        session.take(MAX_LINES).forEach { style.addLine(line(it)) }
        val count = session.size

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.debugkit_ic_notification)
            .setContentTitle("DebugKit · $count ${if (count == 1) "request" else "requests"}")
            .setContentText(line(session.first()))
            .setStyle(style)
            .setContentIntent(openNetworkIntent(context))
            .addAction(0, "Clear", clearIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "DebugKit network activity", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Live count of HTTP calls, from DebugKit (debug builds only)." }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openNetworkIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        DebugKit.intent(context, pluginId = NetworkPlugin.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun clearIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ClearTransactionsReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
