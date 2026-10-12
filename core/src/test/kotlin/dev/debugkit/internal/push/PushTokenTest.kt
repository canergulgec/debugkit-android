package dev.debugkit.internal.push

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// PushToken lives for the process, so each test starts with a load that sets a known state.
class PushTokenTest {

    @Test
    fun `a loaded token is kept, so showing the page again does not ask the app`() = runBlocking {
        PushToken.load { "token-1" }
        var asked = false
        PushToken.loadIfNeeded {
            asked = true
            "token-2"
        }
        assertFalse(asked)
        assertEquals(TokenState.Loaded("token-1"), PushToken.state)
    }

    @Test
    fun `refresh asks the app again`() = runBlocking {
        PushToken.load { "token-1" }
        PushToken.load { "token-2" }
        assertEquals(TokenState.Loaded("token-2"), PushToken.state)
    }

    @Test
    fun `a failed load is retried the next time the page is shown`() = runBlocking {
        PushToken.load { error("Firebase is not set up") }
        assertEquals(TokenState.Failed("Firebase is not set up"), PushToken.state)
        PushToken.loadIfNeeded { "token-1" }
        assertEquals(TokenState.Loaded("token-1"), PushToken.state)
    }

    @Test
    fun `no token and an empty token both read as no token`() = runBlocking {
        PushToken.load { null }
        assertEquals(TokenState.Loaded(null), PushToken.state)
        PushToken.load { "" }
        assertEquals(TokenState.Loaded(null), PushToken.state)
    }
}
