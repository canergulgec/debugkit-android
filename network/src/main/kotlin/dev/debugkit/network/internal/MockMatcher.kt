package dev.debugkit.network.internal

import java.util.concurrent.ConcurrentHashMap

/**
 * URL patterns are globs where `*` matches anything, and must match the whole URL.
 * The query string is ignored unless the pattern contains `?`, so
 * `https://api.example.com/cart` matches `.../cart?page=2` but not `.../cart/items`,
 * and `*cart*` matches both.
 */
internal object MockMatcher {

    private val compiled = ConcurrentHashMap<String, Regex>()

    fun matches(rule: MockRule, method: String, url: String): Boolean {
        if (rule.method != null && !rule.method.equals(method, ignoreCase = true)) return false
        val pattern = rule.urlPattern.trim()
        if (pattern.isEmpty()) return false
        val target = if ('?' in pattern) url else url.substringBefore('?')
        return compiled.getOrPut(pattern) { globToRegex(pattern) }.matches(target)
    }

    private fun globToRegex(glob: String): Regex =
        Regex(glob.split('*').joinToString(".*") { Regex.escape(it) })
}
