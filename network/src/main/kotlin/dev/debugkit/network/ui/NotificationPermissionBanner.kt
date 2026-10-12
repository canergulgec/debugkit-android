package dev.debugkit.network.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.debugkit.network.internal.NetworkNotification
import dev.debugkit.ui.DebugKitBanner

/**
 * Offers to turn on the request notification when it is enabled in config but cannot be shown.
 * After one denial Android stops showing the prompt, so the button switches to app settings.
 */
@Composable
internal fun NotificationPermissionBanner() {
    if (!NetworkNotification.isEnabled) return
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(NetworkNotification.canPost(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }

    // The user may come back from system settings with notifications switched on.
    LifecycleResumeEffect(Unit) {
        canPost = NetworkNotification.canPost(context)
        if (canPost) NetworkNotification.refresh(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = granted && NetworkNotification.canPost(context)
        denied = !granted
        if (canPost) NetworkNotification.refresh(context)
    }

    if (canPost) return

    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !denied
    DebugKitBanner(
        message = "Turn on notifications to see a live request count.",
        actionLabel = if (canAsk) "Allow" else "Settings",
        onAction = { if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context) },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
