package dev.debugkit.network.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonFormatterTest {

    @Test
    fun `indents nested objects and arrays`() {
        val pretty = JsonFormatter.prettyPrint("""{"a":1,"b":[true,null],"c":{"d":"x"}}""")
        assertEquals(
            """
            {
              "a": 1,
              "b": [
                true,
                null
              ],
              "c": {
                "d": "x"
              }
            }
            """.trimIndent(),
            pretty,
        )
    }

    @Test
    fun `keeps empty containers on one line`() {
        assertEquals("{\n  \"a\": [],\n  \"b\": {}\n}", JsonFormatter.prettyPrint("""{"a":[ ],"b":{}}"""))
    }

    @Test
    fun `leaves structural characters inside strings alone`() {
        val pretty = JsonFormatter.prettyPrint("""{"q":"a, b: {c}","e":"say \"hi\", ok"}""")
        assertEquals("{\n  \"q\": \"a, b: {c}\",\n  \"e\": \"say \\\"hi\\\", ok\"\n}", pretty)
    }

    @Test
    fun `keeps number digits exactly`() {
        assertEquals("[\n  12345678901234567890,\n  1.10\n]", JsonFormatter.prettyPrint("[12345678901234567890,1.10]"))
    }

    @Test
    fun `returns null for non-JSON text`() {
        assertNull(JsonFormatter.prettyPrint("<html></html>"))
        assertNull(JsonFormatter.prettyPrint("   "))
    }

    @Test
    fun `highlighting keeps the text unchanged`() {
        val line = """  "name": "Leanne", "id": 1, "ok": true,"""
        val colors = JsonColors(
            key = androidx.compose.ui.graphics.Color.Red,
            string = androidx.compose.ui.graphics.Color.Green,
            number = androidx.compose.ui.graphics.Color.Blue,
            literal = androidx.compose.ui.graphics.Color.Yellow,
            punctuation = androidx.compose.ui.graphics.Color.Black,
        )
        val highlighted = JsonFormatter.highlight(line, colors)
        assertEquals(line, highlighted.text)
        val keySpan = highlighted.spanStyles.first { highlighted.text.substring(it.start, it.end) == "\"name\"" }
        assertEquals(colors.key, keySpan.item.color)
    }
}
