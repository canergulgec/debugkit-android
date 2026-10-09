package dev.debugkit.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import dev.debugkit.internal.packageInfo
import java.text.DateFormat
import java.util.Date

/** Built-in plugin: app, device and display details. Tapping a row copies its value. */
internal class AppInfoPlugin : DebugPlugin {

    override val id: String = "debugkit.app-info"
    override val title: String = "App Info"
    override val summary: String = "Version, device and display details"
    override val order: Int = 90

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val sections = remember { collectSections(context) }

        LazyColumn {
            sections.forEach { (header, rows) ->
                item(key = header) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                items(rows, key = { "$header/${it.first}" }) { (label, value) ->
                    ListItem(
                        overlineContent = { Text(label) },
                        headlineContent = { Text(value) },
                        modifier = Modifier.clickable { copyToClipboard(context, label, value) },
                    )
                }
            }
        }
    }

    private fun collectSections(context: Context): List<Pair<String, List<Pair<String, String>>>> {
        val packageInfo = context.packageInfo()
        val appInfo = context.applicationInfo
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val dateFormat = DateFormat.getDateTimeInstance()

        return listOf(
            "Application" to listOf(
                "Package" to context.packageName,
                "Version name" to (packageInfo.versionName ?: "-"),
                "Version code" to PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
                "Debuggable" to (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0).toString(),
                "Target SDK" to appInfo.targetSdkVersion.toString(),
                "Min SDK" to appInfo.minSdkVersion.toString(),
                "First installed" to dateFormat.format(Date(packageInfo.firstInstallTime)),
                "Last updated" to dateFormat.format(Date(packageInfo.lastUpdateTime)),
            ),
            "Device" to listOf(
                "Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "ABI" to Build.SUPPORTED_ABIS.joinToString(),
            ),
            "Display" to listOf(
                "Resolution" to "${metrics.widthPixels} × ${metrics.heightPixels} px",
                "Density" to "${metrics.densityDpi} dpi (${metrics.density}x)",
                "Font scale" to configuration.fontScale.toString(),
                "Locale" to configuration.locales[0].toLanguageTag(),
            ),
        )
    }
}
