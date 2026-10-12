package dev.debugkit.plugin

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import dev.debugkit.internal.isDebuggable
import dev.debugkit.internal.packageInfo
import dev.debugkit.ui.copyToClipboard
import java.text.DateFormat
import java.util.Date

/** Built-in plugin: app, device and display details. Tapping a row copies its value. */
internal class AppInfoPlugin : DebugPlugin {

    override val id: String = "debugkit.app-info"
    override val title: String = "App Info"
    override val summary: String = "Version, device and display details"
    override val order: Int = 90
    override val section: DebugSection = DebugSection.App

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val sections = remember { collectSections(context) }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            sections.forEach { (header, rows) ->
                item(key = header) {
                    SectionHeader(header)
                    CardGroup(rows) { (label, value) -> ValueRow(label, value, onClick = { copyToClipboard(context, label, value) }) }
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
                "Debuggable" to context.isDebuggable.toString(),
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
