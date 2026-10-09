package dev.debugkit.network.internal

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tells the interceptor to answer matching calls itself. Stored as-is in Room; every type uses
 * [delayMs], and the other fields apply only to the type that names them.
 */
@Entity(tableName = "mock_rules")
internal data class MockRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean = true,
    val name: String,
    /** HTTP method to match, or null for any. */
    val method: String?,
    /** Glob over the URL; see [MockMatcher]. */
    val urlPattern: String,
    val type: MockType,
    val statusCode: Int = 200,
    val contentType: String = "application/json",
    val body: String = "",
    val errorKind: MockErrorKind = MockErrorKind.Timeout,
    val delayMs: Long = 0,
)

internal enum class MockType {
    /** Never hits the server; returns [MockRule.statusCode] and [MockRule.body]. */
    Replace,

    /** Never hits the server; fails the call as [MockRule.errorKind]. */
    NetworkError,

    /** Calls the server as usual after [MockRule.delayMs]. */
    DelayOnly,
}

internal enum class MockErrorKind { Timeout, NoConnection }
