package dev.debugkit.network.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockMatcherTest {

    private fun rule(pattern: String, method: String? = null) =
        MockRule(name = "r", method = method, urlPattern = pattern, type = MockType.Replace)

    @Test
    fun `exact pattern ignores the query string`() {
        val r = rule("https://api.example.com/cart")
        assertTrue(MockMatcher.matches(r, "GET", "https://api.example.com/cart"))
        assertTrue(MockMatcher.matches(r, "GET", "https://api.example.com/cart?page=2"))
        assertFalse(MockMatcher.matches(r, "GET", "https://api.example.com/cart/items"))
        assertFalse(MockMatcher.matches(r, "GET", "https://api.example.com/cartx"))
    }

    @Test
    fun `star matches any run of characters`() {
        val r = rule("*/cart*")
        assertTrue(MockMatcher.matches(r, "GET", "https://a.com/cart"))
        assertTrue(MockMatcher.matches(r, "GET", "https://b.com/v2/cart/items"))
        assertFalse(MockMatcher.matches(r, "GET", "https://a.com/basket"))
    }

    @Test
    fun `pattern with a question mark matches the query too`() {
        val r = rule("https://a.com/search?q=shoes")
        assertTrue(MockMatcher.matches(r, "GET", "https://a.com/search?q=shoes"))
        assertFalse(MockMatcher.matches(r, "GET", "https://a.com/search?q=hats"))
    }

    @Test
    fun `regex characters in the pattern are literal`() {
        val r = rule("https://a.com/v1.0/items")
        assertTrue(MockMatcher.matches(r, "GET", "https://a.com/v1.0/items"))
        assertFalse(MockMatcher.matches(r, "GET", "https://a.com/v1x0/items"))
    }

    @Test
    fun `method must match unless the rule accepts any`() {
        assertTrue(MockMatcher.matches(rule("*", method = "post"), "POST", "https://a.com/x"))
        assertFalse(MockMatcher.matches(rule("*", method = "POST"), "GET", "https://a.com/x"))
        assertTrue(MockMatcher.matches(rule("*"), "DELETE", "https://a.com/x"))
    }

    @Test
    fun `blank pattern never matches`() {
        assertFalse(MockMatcher.matches(rule("  "), "GET", "https://a.com/"))
    }
}
