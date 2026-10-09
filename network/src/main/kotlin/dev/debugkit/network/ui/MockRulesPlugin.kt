package dev.debugkit.network.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.debugkit.network.internal.MockDrafts
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.MockRuleStore
import dev.debugkit.plugin.DebugPlugin

/** The Mock Responses screen: every rule with an on/off switch, and the rule editor. */
internal object MockRulesPlugin : DebugPlugin {

    override val id: String = "debugkit.mocks"
    override val title: String = "Mock Responses"
    override val summary: String = "Replace responses, simulate errors and slow calls"
    override val order: Int = 11

    @Composable
    override fun Content() {
        val rules by MockRuleStore.rules.collectAsStateWithLifecycle(initialValue = emptyList())
        var editing by remember { mutableStateOf<MockRule?>(null) }

        BackHandler(enabled = editing != null) { editing = null }

        val rule = editing
        if (rule != null) {
            key(rule.id) {
                MockRuleEditor(
                    initial = rule,
                    onSave = {
                        MockRuleStore.save(it)
                        editing = null
                    },
                    onCancel = { editing = null },
                    onDelete = if (rule.id != 0L) {
                        {
                            MockRuleStore.delete(rule.id)
                            editing = null
                        }
                    } else {
                        null
                    },
                )
            }
        } else {
            MockRulesScreen(
                rules = rules,
                onAdd = { editing = MockDrafts.newRule() },
                onEdit = { editing = it },
                onToggle = { r, enabled -> MockRuleStore.setEnabled(r.id, enabled) },
            )
        }
    }
}
