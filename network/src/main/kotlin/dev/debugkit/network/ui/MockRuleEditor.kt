package dev.debugkit.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.network.internal.JsonFormatter
import dev.debugkit.network.internal.MockErrorKind
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.MockType
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitSubpage
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Creates or edits a mock rule. Callers wrap it in `key(rule.id)` so switching rules resets the form.
 * The body uses plain `remember`: it can be hundreds of kB, too large for saved instance state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MockRuleEditor(
    initial: MockRule,
    onSave: (MockRule) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var method by rememberSaveable { mutableStateOf(initial.method) }
    var pattern by rememberSaveable { mutableStateOf(initial.urlPattern) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var statusCode by rememberSaveable { mutableStateOf(initial.statusCode.toString()) }
    var contentType by rememberSaveable { mutableStateOf(initial.contentType) }
    var body by remember { mutableStateOf(initial.body) }
    var errorKind by rememberSaveable { mutableStateOf(initial.errorKind) }
    var delay by rememberSaveable { mutableStateOf(initial.delayMs.takeIf { it > 0 }?.toString().orEmpty()) }

    val code = statusCode.toIntOrNull()
    val delayMs = delay.ifBlank { "0" }.toLongOrNull()
    val problems = buildList {
        if (pattern.isBlank()) add("Enter a URL pattern.")
        if (type == MockType.Replace && (code == null || code !in 100..599)) add("Status code must be between 100 and 599.")
        if (delayMs == null || delayMs < 0) add("Delay must be a whole number of milliseconds.")
    }
    val bodyIsJson = remember(body) { isJson(body) }
    val showJsonWarning = type == MockType.Replace && "json" in contentType && body.isNotBlank() && !bodyIsJson
    DebugKitSubpage(if (onDelete == null) "New mock rule" else "Edit mock rule")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            placeholder = { Text("e.g. Payment fails") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Method")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            METHODS.forEach { (label, value) ->
                FilterChip(selected = method == value, onClick = { method = value }, label = { Text(label) })
            }
        }

        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = { Text("URL pattern") },
            supportingText = { Text("* matches anything. The query string is ignored unless the pattern contains ?.") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DebugKitFonts.Mono),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Respond with")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == MockType.Replace, { type = MockType.Replace }, { Text("Custom response") })
            FilterChip(type == MockType.NetworkError, { type = MockType.NetworkError }, { Text("Network error") })
            FilterChip(type == MockType.DelayOnly, { type = MockType.DelayOnly }, { Text("Real response, delayed") })
        }

        when (type) {
            MockType.Replace -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = statusCode,
                        onValueChange = { statusCode = it.filter(Char::isDigit).take(3) },
                        label = { Text("Status") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f),
                    )
                    OutlinedTextField(
                        value = contentType,
                        onValueChange = { contentType = it },
                        label = { Text("Content-Type") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_STATUS_CODES.forEach { quick ->
                        FilterChip(statusCode == quick, { statusCode = quick }, { Text(quick) })
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    minLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = DebugKitFonts.Mono, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { JsonFormatter.prettyPrint(body)?.let { body = it } }) { Text("Format JSON") }
                    if (showJsonWarning) {
                        Text("Body is not valid JSON", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            MockType.NetworkError -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(errorKind == MockErrorKind.Timeout, { errorKind = MockErrorKind.Timeout }, { Text("Timeout") })
                FilterChip(errorKind == MockErrorKind.NoConnection, { errorKind = MockErrorKind.NoConnection }, { Text("No connection") })
            }
            MockType.DelayOnly -> Text(
                "The call goes to the server as usual and the real response is returned after the delay below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = delay,
            onValueChange = { delay = it.filter(Char::isDigit).take(6) },
            label = { Text("Delay (ms)") },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = problems.isEmpty(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { pattern.trim() },
                            method = method,
                            urlPattern = pattern.trim(),
                            type = type,
                            statusCode = code ?: initial.statusCode,
                            contentType = contentType.trim(),
                            body = body,
                            errorKind = errorKind,
                            delayMs = delayMs ?: 0,
                        ),
                    )
                },
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun isJson(text: String): Boolean = try {
    val value = JSONTokener(text).nextValue()
    value is JSONObject || value is JSONArray
} catch (_: Exception) {
    false
}

/** Label to stored value; null matches any method. */
private val METHODS = listOf("Any" to null, "GET" to "GET", "POST" to "POST", "PUT" to "PUT", "PATCH" to "PATCH", "DELETE" to "DELETE")

private val QUICK_STATUS_CODES = listOf("200", "400", "401", "403", "404", "500", "503")
