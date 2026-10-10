package dev.debugkit.network.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySearchTest {

    private val lines = listOf(
        """{""",
        """  "name": "budget.xlsx",""",
        """  "isShared": true,""",
        """  "owner": { "name": "Ayşe" }""",
        """}""",
    )

    @Test
    fun `finds every occurrence in reading order, ignoring case`() {
        assertEquals(
            listOf(SearchMatch(1, 3), SearchMatch(3, 14)),
            BodySearch.find(lines, "NAME"),
        )
    }

    @Test
    fun `finds several matches on one line without overlapping`() {
        assertEquals(listOf(SearchMatch(0, 0), SearchMatch(0, 2)), BodySearch.find(listOf("aaaa"), "aa"))
    }

    @Test
    fun `blank query or no match gives nothing`() {
        assertTrue(BodySearch.find(lines, "  ").isEmpty())
        assertTrue(BodySearch.find(lines, "missing").isEmpty())
    }
}
