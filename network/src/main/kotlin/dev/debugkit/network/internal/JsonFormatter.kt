package dev.debugkit.network.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Re-indents JSON by walking its characters rather than parsing it, so numbers keep their exact
 * digits, key order is untouched and slightly malformed bodies still come out readable.
 */
internal object JsonFormatter {

    private const val INDENT = "  "

    /** Returns null when [raw] does not look like JSON, so callers can show it as plain text. */
    fun prettyPrint(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || (text[0] != '{' && text[0] != '[')) return null

        val out = StringBuilder(text.length * 2)
        var depth = 0
        var inString = false
        var escaped = false
        var i = 0

        fun newLine() {
            out.append('\n')
            repeat(depth.coerceAtLeast(0)) { out.append(INDENT) }
        }

        while (i < text.length) {
            val c = text[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> {
                    inString = true
                    out.append(c)
                }
                '{', '[' -> {
                    val next = nextNonWhitespace(text, i + 1)
                    if (next != -1 && text[next] == closing(c)) {
                        // Keep empty containers on one line: {} and [].
                        out.append(c).append(text[next])
                        i = next
                    } else {
                        out.append(c)
                        depth++
                        newLine()
                    }
                }
                '}', ']' -> {
                    depth--
                    newLine()
                    out.append(c)
                }
                ',' -> {
                    out.append(c)
                    newLine()
                }
                ':' -> out.append(": ")
                else -> if (!c.isWhitespace()) out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** Colors one line of [prettyPrint] output. Keys are strings directly followed by a colon. */
    fun highlight(line: String, colors: JsonColors): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    val end = stringEnd(line, i)
                    val isKey = line.getOrNull(end) == ':'
                    withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) { append(line, i, end) }
                    i = end
                }
                c == '-' || c.isDigit() -> {
                    var end = i + 1
                    while (end < line.length && (line[end].isDigit() || line[end] in "+-.eE")) end++
                    withStyle(SpanStyle(color = colors.number)) { append(line, i, end) }
                    i = end
                }
                else -> {
                    val literal = LITERALS.firstOrNull { line.startsWith(it, i) }
                    if (literal != null) {
                        withStyle(SpanStyle(color = colors.literal)) { append(literal) }
                        i += literal.length
                    } else {
                        withStyle(SpanStyle(color = colors.punctuation)) { append(c) }
                        i++
                    }
                }
            }
        }
    }

    /** Index just past the closing quote of the string starting at [start], or the line end. */
    private fun stringEnd(line: String, start: Int): Int {
        var i = start + 1
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return line.length
    }

    private fun nextNonWhitespace(text: String, from: Int): Int =
        (from until text.length).firstOrNull { !text[it].isWhitespace() } ?: -1

    private fun closing(open: Char) = if (open == '{') '}' else ']'

    private val LITERALS = listOf("true", "false", "null")
}

internal data class JsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val punctuation: Color,
)
