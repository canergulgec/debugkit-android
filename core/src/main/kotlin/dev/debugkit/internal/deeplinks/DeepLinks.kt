package dev.debugkit.internal.deeplinks

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import dev.debugkit.internal.DebugSettings

internal data class DeepLink(val title: String, val uri: String)

/**
 * What the Deep Links screen shows about a link while it is typed. [handler] is the activity in
 * this app that opens it as a link tap from another app would (VIEW + BROWSABLE).
 * [handlerWithoutBrowsable] is set when only a non-browsable intent filter matches: the link then
 * works from inside the app but not from a browser or a chat app.
 */
internal data class LinkCheck(
    val parts: List<Pair<String, String>>,
    val handler: String?,
    val handlerWithoutBrowsable: String?,
)

/** Keeps the most recent links first, without repeats, capped at [max]. */
internal object LinkHistory {
    fun add(history: List<String>, uri: String, max: Int): List<String> =
        (listOf(uri) + history.filter { it != uri }).take(max)
}

/** Splits a link into the parts QA checks: a wrong host or an unencoded parameter is easy to spot. */
internal object LinkParts {
    fun of(uri: Uri): List<Pair<String, String>> = buildList {
        add("Scheme" to uri.scheme.orEmpty())
        uri.host?.let { add("Host" to it) }
        if (uri.port != -1) add("Port" to uri.port.toString())
        uri.path?.takeIf { it.isNotEmpty() }?.let { add("Path" to it) }
        if (uri.isHierarchical) {
            uri.queryParameterNames.forEach { name -> add("?$name" to uri.getQueryParameters(name).joinToString()) }
        }
        uri.fragment?.let { add("Fragment" to it) }
    }
}

internal object DeepLinkStore {

    private const val KEY_RECENT = "deeplinks.recent"
    private const val MAX_RECENT = 10

    private lateinit var prefs: SharedPreferences

    var saved: List<DeepLink> = emptyList()
        private set

    /** Newest first. Snapshot state, so the screen updates as links are opened. */
    val recent = mutableStateListOf<String>()

    fun init(context: Context, links: List<DeepLink>) {
        prefs = context.getSharedPreferences(DebugSettings.PREFS_NAME, Context.MODE_PRIVATE)
        saved = links
        recent.clear()
        recent.addAll(prefs.getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    }

    fun remember(uri: String) {
        val updated = LinkHistory.add(recent.toList(), uri, MAX_RECENT)
        recent.clear()
        recent.addAll(updated)
        prefs.edit { putString(KEY_RECENT, updated.joinToString("\n")) }
    }

    fun clearRecent() {
        recent.clear()
        prefs.edit { remove(KEY_RECENT) }
    }
}

internal object DeepLinkOpener {

    /** Null when [text] is not a link with a scheme. */
    fun check(context: Context, text: String): LinkCheck? {
        val uri = parse(text) ?: return null
        val browsable = viewIntent(uri, browsable = true).setPackage(context.packageName)
        val plain = viewIntent(uri, browsable = false).setPackage(context.packageName)
        val handler = firstActivity(context, browsable)
        return LinkCheck(
            parts = LinkParts.of(uri),
            handler = handler,
            handlerWithoutBrowsable = if (handler == null) firstActivity(context, plain) else null,
        )
    }

    /** Opens the link with this app's own routing. False when no screen of the app handles it. */
    fun openInApp(context: Context, text: String): Boolean {
        val uri = parse(text) ?: return false
        return start(context, viewIntent(uri, browsable = true).setPackage(context.packageName))
    }

    /**
     * Opens the link the way a tap in another app would: the system decides between this app,
     * a browser or a chooser, depending on intent filters and App Links verification.
     */
    fun openAsLinkTap(context: Context, text: String): Boolean {
        val uri = parse(text) ?: return false
        return start(context, viewIntent(uri, browsable = true))
    }

    private fun parse(text: String): Uri? =
        text.trim().takeIf { it.isNotEmpty() }?.let(Uri::parse)?.takeIf { !it.scheme.isNullOrEmpty() }

    private fun viewIntent(uri: Uri, browsable: Boolean) = Intent(Intent.ACTION_VIEW, uri).apply {
        if (browsable) addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    // Only this app's own activities are queried, which needs no package visibility declaration.
    @SuppressLint("QueryPermissionsNeeded")
    private fun firstActivity(context: Context, intent: Intent): String? {
        val pm = context.packageManager
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return matches.firstOrNull()?.activityInfo?.name
    }
}

/** How Android 12+ treats this app's `https` links, per domain. */
internal data class AppLinksStatus(val linkHandlingAllowed: Boolean, val domains: List<Pair<String, DomainState>>)

internal enum class DomainState { Verified, SelectedByUser, NotVerified }

@RequiresApi(Build.VERSION_CODES.S)
internal fun appLinksStatus(context: Context): AppLinksStatus? {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return null
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull() ?: return null
    return AppLinksStatus(
        linkHandlingAllowed = state.isLinkHandlingAllowed,
        domains = state.hostToStateMap.entries.sortedBy { it.key }.map { (host, value) ->
            host to when (value) {
                DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> DomainState.Verified
                DomainVerificationUserState.DOMAIN_STATE_SELECTED -> DomainState.SelectedByUser
                else -> DomainState.NotVerified
            }
        },
    )
}
