package dev.debugkit.network.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.network.internal.BodySearch
import dev.debugkit.network.internal.CapturedBody
import dev.debugkit.network.internal.HttpTransaction
import dev.debugkit.network.internal.JsonColors
import dev.debugkit.network.internal.JsonFormatter
import dev.debugkit.network.internal.TransactionExport
import dev.debugkit.network.internal.TransactionStatus
import dev.debugkit.network.internal.host
import dev.debugkit.network.internal.pathWithQuery
import dev.debugkit.network.internal.status
import dev.debugkit.ui.DebugKitButton
import dev.debugkit.ui.DebugKitCard
import dev.debugkit.ui.DebugKitColors
import dev.debugkit.ui.DebugKitFonts
import dev.debugkit.ui.DebugKitPill
import dev.debugkit.ui.DebugKitSearchField
import dev.debugkit.ui.DebugKitSectionLabel
import dev.debugkit.ui.DebugKitSegments
import dev.debugkit.ui.DebugKitSubpage
import dev.debugkit.ui.DebugTone
import dev.debugkit.ui.copyToClipboard
import dev.debugkit.ui.shareText
import kotlinx.coroutines.launch

@Composable
internal fun TransactionDetailScreen(tx: HttpTransaction, onCreateMock: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable(tx.id) { mutableIntStateOf(0) }
    DebugKitSubpage("Request")

    Column(Modifier.fillMaxSize()) {
        DebugKitCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(tx, label = TransactionExport.statusText(tx))
                    DebugKitPill(tx.method, DebugTone.Info)
                    tx.protocol?.let { DebugKitPill(it, DebugTone.Neutral) }
                    if (tx.mockRuleName != null) MockTag()
                }
                Text(tx.pathWithQuery, style = MONO.copy(fontSize = 15.sp, lineHeight = 21.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(
                        tx.host,
                        TransactionExport.formatTime(tx.requestedAt),
                        tx.durationMs?.let { "$it ms" },
                        tx.responseBody?.let { TransactionExport.formatBytes(it.sizeBytes) },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DebugKitColors.TextSecondary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugKitButton("Mock", onClick = onCreateMock, primary = true)
            DebugKitButton("Copy cURL", onClick = { copyToClipboard(context, "cURL", TransactionExport.toCurl(tx)) })
            DebugKitButton("Copy URL", onClick = { copyToClipboard(context, "URL", tx.url) })
            DebugKitButton(
                "Share",
                onClick = { shareText(context, TransactionExport.toShareText(tx), chooserTitle = "Share request", subject = "${tx.method} ${tx.url}") },
            )
        }
        DebugKitSegments(
            options = TABS,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when (tab) {
            0 -> OverviewTab(tx)
            1 -> MessageTab(tx.requestHeaders, tx.requestBody)
            else -> MessageTab(tx.responseHeaders, tx.responseBody, pending = tx.status == TransactionStatus.Pending)
        }
    }
}

@Composable
private fun OverviewTab(tx: HttpTransaction) {
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(TransactionExport.overview(tx), key = { it.first }) { (label, value) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = DebugKitColors.TextSecondary)
                    Text(value, style = MONO)
                }
                HorizontalDivider(color = DebugKitColors.Divider)
            }
        }
    }
}

@Composable
private fun MessageTab(headers: List<Pair<String, String>>, body: CapturedBody?, pending: Boolean = false) {
    val formatted = remember(body) {
        body?.text?.let { text ->
            val pretty = JsonFormatter.prettyPrint(text)
            FormattedBody(lines = (pretty ?: text).lines(), isJson = pretty != null)
        }
    }
    val colors = remember { jsonColors() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var query by remember(body) { mutableStateOf("") }
    var current by remember(body) { mutableIntStateOf(0) }
    val matches = remember(formatted, query) { formatted?.let { BodySearch.find(it.lines, query) }.orEmpty() }
    val matchesByLine = remember(matches) { matches.withIndex().groupBy { it.value.line } }
    // Body lines come after the headers section: its title, one row per header (or a
    // placeholder), then the body title.
    val firstBodyItem = 2 + headers.size.coerceAtLeast(1)
    // Wide enough for the largest line number.
    val gutter = (formatted?.lines?.size?.toString()?.length ?: 1).let { (it * 8 + 14).dp }

    // A few lines of context above the match.
    fun scrollToLine(line: Int) {
        scope.launch { listState.animateScrollToItem((firstBodyItem + line - 3).coerceAtLeast(0)) }
    }

    fun goTo(index: Int) {
        if (matches.isEmpty()) return
        current = (index + matches.size) % matches.size
        scrollToLine(matches[current].line)
    }

    Column(Modifier.fillMaxSize()) {
        if (formatted != null) {
            SearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    current = 0
                    // Jump to the first match as the user types.
                    BodySearch.find(formatted.lines, it).firstOrNull()?.let { first -> scrollToLine(first.line) }
                },
                position = if (matches.isEmpty()) null else current + 1,
                total = matches.size,
                onPrevious = { goTo(current - 1) },
                onNext = { goTo(current + 1) },
            )
        }
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState) {
                sectionHeader("Headers")
                if (headers.isEmpty()) {
                    item { Placeholder(if (pending) "Waiting for response…" else "(none)") }
                }
                items(headers) { (name, value) ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = DebugKitColors.Accent)) { append(name) }
                            append(": ")
                            append(value)
                        },
                        style = CODE,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                sectionHeader(if (body != null) "Body (${TransactionExport.formatBytes(body.sizeBytes)})" else "Body")
                when {
                    body == null -> item { Placeholder(if (pending) "Waiting for response…" else "(no body)") }
                    formatted == null -> item { Placeholder("(binary content, not shown)") }
                    else -> {
                        items(formatted.lines.size) { index ->
                            Row(Modifier.fillMaxWidth().background(DebugKitColors.Code)) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = CODE,
                                    color = DebugKitColors.TextFaint,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(gutter).padding(end = 10.dp),
                                )
                                val line = formatted.lines[index]
                                val base = if (formatted.isJson) JsonFormatter.highlight(line, colors) else AnnotatedString(line)
                                val lineMatches = matchesByLine[index]
                                Text(
                                    text = if (lineMatches == null) {
                                        base
                                    } else {
                                        buildAnnotatedString {
                                            append(base)
                                            lineMatches.forEach { (matchIndex, match) ->
                                                addStyle(
                                                    SpanStyle(background = if (matchIndex == current) CURRENT_MATCH else OTHER_MATCH, color = DebugKitColors.OnAccent),
                                                    match.start,
                                                    match.start + query.length,
                                                )
                                            }
                                        }
                                    },
                                    style = CODE,
                                )
                            }
                        }
                        if (body.truncated) {
                            item { Placeholder("(truncated: only the first part of the body is shown)") }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** Stays above the scrolling body, so moving between matches never scrolls it away. */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    position: Int?,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    DebugKitSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search body",
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onNext() }),
    ) {
        if (query.isNotBlank()) {
            Text(
                text = if (position == null) "0/0" else "$position/$total",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = DebugKitFonts.Mono),
                color = if (total == 0) DebugKitColors.Error else DebugKitColors.TextSecondary,
            )
            SearchArrow("▲", "Previous match", enabled = total > 0, onClick = onPrevious)
            SearchArrow("▼", "Next match", enabled = total > 0, onClick = onNext)
        }
    }
}

@Composable
private fun SearchArrow(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DebugKitColors.CardRaised)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 11.sp, color = if (enabled) DebugKitColors.Text else DebugKitColors.TextFaint)
    }
}

/** Amber for every match and the accent for the current one, on dark text. */
private val OTHER_MATCH = DebugKitColors.Warning.copy(alpha = 0.55f)
private val CURRENT_MATCH = DebugKitColors.Accent

private fun LazyListScope.sectionHeader(title: String) = item {
    DebugKitSectionLabel(title, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Placeholder(text: String) {
    Text(text, color = DebugKitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
}

private val CODE = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 12.sp, lineHeight = 18.sp)

private val MONO = TextStyle(fontFamily = DebugKitFonts.Mono, fontSize = 13.sp, lineHeight = 18.sp)

/** Keys, strings, numbers and literals, tuned for the dark code background. */
private fun jsonColors(): JsonColors =
    JsonColors(DebugKitColors.Accent, DebugKitColors.Good, DebugKitColors.Info, DebugKitColors.Warning, DebugKitColors.Text)

private class FormattedBody(val lines: List<String>, val isJson: Boolean)

private val TABS = listOf("Overview", "Request", "Response")
