package dev.debugkit.network.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.debugkit.network.internal.MockDrafts
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.MockRuleStore
import dev.debugkit.network.internal.TransactionStore
import dev.debugkit.plugin.DebugPlugin
import dev.debugkit.plugin.DebugSection
import dev.debugkit.plugin.DebugTile
import dev.debugkit.ui.DebugTone

/** The Network screen: a live list of calls, one call's details, and "mock this response". */
internal object NetworkPlugin : DebugPlugin {

    override val id: String = "debugkit.network"
    override val title: String = "Requests"
    override val summary: String = "HTTP requests and responses"
    override val order: Int = 10
    override val section: DebugSection = DebugSection.Network

    @Composable
    override fun tile(): DebugTile {
        val calls by TransactionStore.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
        val failed = calls.count { it.error != null || (it.responseCode ?: 0) >= 400 }
        return DebugTile(
            label = "Network",
            value = calls.size.toString(),
            note = if (failed == 0) "no failures" else "$failed failed",
            tone = if (failed == 0) DebugTone.Neutral else DebugTone.Error,
        )
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val summaries by TransactionStore.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
        var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
        var mockDraft by remember { mutableStateOf<MockRule?>(null) }

        // Later handlers win, so back steps out of the editor, then the detail, then the plugin.
        BackHandler(enabled = selectedId != null) { selectedId = null }
        BackHandler(enabled = mockDraft != null) { mockDraft = null }

        val draft = mockDraft
        val id = selectedId
        when {
            draft != null -> MockRuleEditor(
                initial = draft,
                onSave = {
                    MockRuleStore.save(it)
                    mockDraft = null
                    Toast.makeText(context, "Mock rule saved and enabled", Toast.LENGTH_SHORT).show()
                },
                onCancel = { mockDraft = null },
            )
            id != null -> {
                val transaction by remember(id) { TransactionStore.observe(id) }.collectAsStateWithLifecycle(initialValue = null)
                transaction?.let { tx ->
                    TransactionDetailScreen(tx, onCreateMock = { mockDraft = MockDrafts.fromTransaction(tx) })
                }
            }
            else -> TransactionListScreen(
                transactions = summaries,
                onSelect = { selectedId = it.id },
                onClear = TransactionStore::clear,
            )
        }
    }
}
