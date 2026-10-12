package dev.debugkit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/*
 * Copy and share, the two ways QA gets something out of DebugKit and into a bug report.
 * Public for feature modules; host apps have no reason to use them.
 */

/** Copies [text]; [label] names it in the confirmation, e.g. "URL copied". */
public fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}

/** Opens the share sheet for plain [text], titled [chooserTitle]. */
public fun shareText(context: Context, text: String, chooserTitle: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
