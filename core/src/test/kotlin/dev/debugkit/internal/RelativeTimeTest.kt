package dev.debugkit.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RelativeTimeTest {

    private val now = 1_760_000_000_000L // 2025-10-09
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private val original = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun `recent times count back in the largest whole unit`() {
        assertEquals("just now", RelativeTime.format(now - 30_000, now))
        assertEquals("1 minute ago", RelativeTime.format(now - minute, now))
        assertEquals("59 minutes ago", RelativeTime.format(now - 59 * minute - 59_000, now))
        assertEquals("1 hour ago", RelativeTime.format(now - hour, now))
        assertEquals("23 hours ago", RelativeTime.format(now - 23 * hour, now))
        assertEquals("4 days ago", RelativeTime.format(now - 4 * day, now))
    }

    @Test
    fun `a week or more back shows the date`() {
        assertEquals("on Oct 2", RelativeTime.format(now - 7 * day, now))
    }

    @Test
    fun `stays English on a Turkish phone`() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        assertEquals("4 days ago", RelativeTime.format(now - 4 * day, now))
        assertEquals("on Oct 2", RelativeTime.format(now - 7 * day, now))
    }
}
