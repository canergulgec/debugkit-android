package dev.debugkit.internal.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.debugkit.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

internal data class PushTemplate(
    val title: String,
    val body: String,
    val data: Map<String, String>,
    /** Opened when the notification is tapped, instead of launching the app with [data]. */
    val link: String? = null,
)

internal data class ReceivedPush(
    val timestamp: Long,
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
)

/** What tapping a test notification does, matching the ways apps route push taps. */
internal enum class PushDelivery {
    /** Launches the app's start screen with the data as intent extras, as FCM does in the background. */
    LaunchWithData,

    /** Opens [PushTemplate.link], for apps that put a deep link in their pushes. */
    OpenLink,

    /** No notification: hands the data to the app's own handler, as if it arrived in the foreground. */
    AppHandler,
}

/** The `push { }` configuration from DebugKit.install. */
internal class PushSetup(
    val token: (suspend () -> String?)?,
    val handler: ((Map<String, String>) -> Unit)?,
    val templates: List<PushTemplate>,
)

internal sealed interface TokenState {
    data object Loading : TokenState
    data class Loaded(val token: String?) : TokenState
    data class Failed(val message: String) : TokenState
}

/**
 * The push token, kept for the process once loaded: asking Firebase again each time the Push
 * page or its token row is shown would flash "Loading…" for nothing. Refresh loads it anew.
 */
internal object PushToken {

    /** Null until first requested. */
    var state by mutableStateOf<TokenState?>(null)
        private set

    /** Loads unless a token is already here; also retries a load cut short by leaving the page. */
    suspend fun loadIfNeeded(provider: suspend () -> String?) {
        if (state !is TokenState.Loaded) load(provider)
    }

    suspend fun load(provider: suspend () -> String?) {
        state = TokenState.Loading
        state = runCatching { provider() }.fold(
            onSuccess = { TokenState.Loaded(it?.takeIf(String::isNotEmpty)) },
            onFailure = { TokenState.Failed(it.message ?: it.toString()) },
        )
    }
}

/** `key=value` per line, the form QA types data in. Blank lines and lines without `=` are skipped. */
internal object PushData {

    fun parse(text: String): Map<String, String> = text.lines()
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }
        .filter { it.first.isNotEmpty() }
        .toMap(LinkedHashMap())

    fun format(data: Map<String, String>): String = data.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

/**
 * Received pushes and QA's saved templates, kept in SharedPreferences: a push can start the
 * process just to deliver it, so an in-memory list would lose it when that process ends.
 */
internal object PushStore {

    private const val PREFS_NAME = "debugkit_push"
    private const val KEY_RECEIVED = "received"
    private const val KEY_TEMPLATES = "templates"
    private const val MAX_RECEIVED = 50

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    var setup: PushSetup = PushSetup(null, null, emptyList())
        private set

    /** Newest first. */
    val received = mutableStateListOf<ReceivedPush>()

    /** Templates QA saved on the device, after the app's own. */
    val savedTemplates = mutableStateListOf<PushTemplate>()

    fun init(context: Context, setup: PushSetup) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.setup = setup
        received.clear()
        received.addAll(readReceived())
        savedTemplates.clear()
        savedTemplates.addAll(readTemplates())
    }

    /** Any thread: Firebase calls onMessageReceived on a background thread. */
    fun record(push: ReceivedPush) {
        mainHandler.post {
            received.add(0, push)
            while (received.size > MAX_RECEIVED) received.removeAt(received.lastIndex)
            prefs.edit { putString(KEY_RECEIVED, JSONArray(received.map(::toJson)).toString()) }
        }
    }

    fun clearReceived() {
        received.clear()
        prefs.edit { remove(KEY_RECEIVED) }
    }

    fun saveTemplate(template: PushTemplate) {
        savedTemplates.removeAll { it.title == template.title }
        savedTemplates.add(0, template)
        writeTemplates()
    }

    fun deleteTemplate(template: PushTemplate) {
        savedTemplates.remove(template)
        writeTemplates()
    }

    private fun writeTemplates() {
        prefs.edit { putString(KEY_TEMPLATES, JSONArray(savedTemplates.map(::toJson)).toString()) }
    }

    private fun readReceived(): List<ReceivedPush> = readArray(KEY_RECEIVED) { json ->
        ReceivedPush(json.getLong("time"), json.optStringOrNull("title"), json.optStringOrNull("body"), readMap(json.getJSONObject("data")))
    }

    private fun readTemplates(): List<PushTemplate> = readArray(KEY_TEMPLATES) { json ->
        PushTemplate(json.getString("title"), json.getString("body"), readMap(json.getJSONObject("data")), json.optStringOrNull("link"))
    }

    private fun <T> readArray(key: String, read: (JSONObject) -> T): List<T> = runCatching {
        val array = JSONArray(prefs.getString(key, null) ?: return emptyList())
        (0 until array.length()).map { read(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun toJson(push: ReceivedPush) = JSONObject()
        .put("time", push.timestamp)
        .put("title", push.title)
        .put("body", push.body)
        .put("data", JSONObject(push.data))

    private fun toJson(template: PushTemplate) = JSONObject()
        .put("title", template.title)
        .put("body", template.body)
        .put("data", JSONObject(template.data))
        .put("link", template.link)

    private fun readMap(json: JSONObject): Map<String, String> =
        json.keys().asSequence().associateWith { json.optString(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}

/** Posts test notifications that behave like real pushes when tapped. */
internal object TestPushSender {

    private const val CHANNEL_ID = "debugkit_test_push"
    private val nextId = AtomicInteger(0x0DEB_1000)

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    /** Returns an error to show, or null on success. */
    // canPost() checks the permission; lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    fun send(context: Context, template: PushTemplate, delivery: PushDelivery): String? {
        if (delivery == PushDelivery.AppHandler) {
            val handler = PushStore.setup.handler ?: return "The app has no onTestMessage handler."
            return runCatching { handler(template.data) }.exceptionOrNull()?.let { "The app's handler threw: $it" }
        }
        if (!canPost(context)) return "Notifications are off for this app. Allow them first."

        val target = when (delivery) {
            PushDelivery.LaunchWithData -> context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.apply { template.data.forEach { (key, value) -> putExtra(key, value) } }
                ?: return "The app has no launcher activity to open."
            PushDelivery.OpenLink -> {
                val link = template.link?.takeIf { it.isNotBlank() } ?: return "Enter a link to open."
                Intent(Intent.ACTION_VIEW, link.trim().toUri())
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PushDelivery.AppHandler -> error("handled above")
        }

        createChannel(context)
        val id = nextId.incrementAndGet()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.debugkit_ic_push)
            .setContentTitle(template.title.ifBlank { "Test notification" })
            .setContentText(template.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(template.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // A distinct request code per notification, so each keeps its own extras.
            .setContentIntent(PendingIntent.getActivity(context, id, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return null
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "DebugKit test notifications", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Notifications sent from DebugKit's Push screen (debug builds only)." }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
