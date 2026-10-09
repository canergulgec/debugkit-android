package dev.debugkit.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentResolverTest {

    private val environments = listOf(
        Environment("Staging", "https://staging.api.com"),
        Environment("Dev", "https://dev.api.com"),
    )
    private val fallback = "https://api.com"

    @Test
    fun `no selection uses the build's own url`() {
        assertEquals(fallback, EnvironmentResolver.resolve(null, "", environments, fallback))
    }

    @Test
    fun `named selection uses that environment`() {
        assertEquals("https://dev.api.com", EnvironmentResolver.resolve("Dev", "", environments, fallback))
    }

    @Test
    fun `selection no longer declared falls back`() {
        assertEquals(fallback, EnvironmentResolver.resolve("Removed", "", environments, fallback))
    }

    @Test
    fun `custom selection uses the trimmed custom url, or falls back when blank`() {
        assertEquals(
            "http://10.0.2.2:8080",
            EnvironmentResolver.resolve(EnvironmentResolver.CUSTOM, " http://10.0.2.2:8080 ", environments, fallback),
        )
        assertEquals(fallback, EnvironmentResolver.resolve(EnvironmentResolver.CUSTOM, "  ", environments, fallback))
    }
}
