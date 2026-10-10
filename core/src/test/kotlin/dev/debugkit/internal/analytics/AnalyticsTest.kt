package dev.debugkit.internal.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {

    @Test
    fun `valid Firebase event has no issues`() {
        assertTrue(FirebaseRules.eventIssues("add_to_cart", mapOf("item_id" to "SKU-1", "price" to 9.99)).isEmpty())
    }

    @Test
    fun `event names that Firebase drops are flagged`() {
        assertEquals(1, FirebaseRules.eventIssues("Add To Cart", emptyMap()).size)
        assertEquals(1, FirebaseRules.eventIssues("1st_purchase", emptyMap()).size)
        assertEquals(1, FirebaseRules.eventIssues("firebase_custom", emptyMap()).size)
        assertEquals(1, FirebaseRules.eventIssues("a".repeat(41), emptyMap()).size)
        assertTrue(FirebaseRules.eventIssues("first_open", emptyMap()).single().contains("reserved"))
        assertTrue(FirebaseRules.eventIssues("a".repeat(40), emptyMap()).isEmpty())
    }

    @Test
    fun `parameters over the limits are flagged`() {
        val tooMany = (1..26).associate { "p$it" to it.toLong() }
        assertTrue(FirebaseRules.eventIssues("screen", tooMany).single().contains("26 parameters"))
        assertTrue(FirebaseRules.eventIssues("screen", mapOf("ga_session" to 1L)).single().contains("prefix"))
        assertTrue(FirebaseRules.eventIssues("screen", mapOf("title" to "x".repeat(101))).single().contains("101 characters"))
        assertTrue(FirebaseRules.eventIssues("screen", mapOf("title" to "x".repeat(100))).isEmpty())
    }

    @Test
    fun `user properties over the limits are flagged`() {
        assertTrue(FirebaseRules.propertyIssues("plan", "premium").isEmpty())
        assertEquals(1, FirebaseRules.propertyIssues("a".repeat(25), "x").size)
        assertEquals(1, FirebaseRules.propertyIssues("plan", "x".repeat(37)).size)
        assertEquals(1, FirebaseRules.propertyIssues("user_id", "42").size)
        assertTrue(FirebaseRules.propertyIssues("plan", null).isEmpty())
    }

    @Test
    fun `rules apply to Firebase only`() {
        assertTrue(FirebaseRules.appliesTo("firebase"))
        assertFalse(FirebaseRules.appliesTo("Adjust"))
    }

    @Test
    fun `same event and params within a second is a duplicate`() {
        val previous = AnalyticsEvent(1, 1_000, "Firebase", "purchase", mapOf("value" to 10L), emptyList(), false)
        assertTrue(DuplicateCheck.isDuplicate(previous, 1_800, mapOf("value" to 10L)))
        assertFalse(DuplicateCheck.isDuplicate(previous, 2_500, mapOf("value" to 10L)))
        assertFalse(DuplicateCheck.isDuplicate(previous, 1_200, mapOf("value" to 11L)))
    }

    @Test
    fun `formats values and nested items`() {
        val params = linkedMapOf<String, Any?>(
            "currency" to "TRY",
            "items" to listOf(mapOf("item_id" to "SKU-1", "quantity" to 2L)),
        )
        assertEquals("currency=TRY, items=[{item_id=SKU-1, quantity=2}]", AnalyticsFormat.preview(params))
        assertEquals("list", AnalyticsFormat.type(params["items"]))
        assertEquals(
            """
            {
              "currency": "TRY",
              "items": [
                {
                  "item_id": "SKU-1",
                  "quantity": 2
                }
              ]
            }
            """.trimIndent(),
            AnalyticsFormat.json(params),
        )
    }
}
