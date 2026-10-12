package dev.debugkit.internal.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One event the app sent to an analytics tool, as recorded for QA. */
internal data class AnalyticsEvent(
    val id: Long,
    val timestamp: Long,
    val provider: String,
    val name: String,
    /** Values are String, Long, Double, Boolean, null, or nested List/Map (e.g. ecommerce items). */
    val params: Map<String, Any?>,
    /** Rule violations that make the tool drop or truncate the event; empty when fine. */
    val issues: List<String>,
    /** Same name and params as an event shortly before: usually a double-fired call. */
    val duplicate: Boolean,
) {
    /** Worth QA's attention: Firebase would drop or cut it, or it was sent twice. */
    val hasProblem: Boolean get() = issues.isNotEmpty() || duplicate
}

internal data class UserProperty(
    val provider: String,
    val name: String,
    val value: String?,
    val timestamp: Long,
    val issues: List<String>,
)

internal object DuplicateCheck {

    private const val WINDOW_MS = 1_000L

    /**
     * Same params as the previous event of that name, within a second: a call made twice, not two
     * genuine user actions, which cannot repeat that fast with identical data.
     */
    fun isDuplicate(previous: AnalyticsEvent, timestamp: Long, params: Map<String, Any?>): Boolean =
        timestamp - previous.timestamp <= WINDOW_MS && previous.params == params
}

internal object AnalyticsFormat {

    /** `14:32:08.120`. A new formatter per call: SimpleDateFormat is not thread-safe. */
    fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))

    /** `item_id=SKU-1, price=9.99`, for list rows. */
    fun preview(params: Map<String, Any?>): String =
        params.entries.joinToString { (key, value) -> "$key=${value(value)}" }

    fun value(value: Any?): String = when (value) {
        null -> "null"
        is String -> value
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${value(it.value)}" }
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { value(it) }
        else -> value.toString()
    }

    fun type(value: Any?): String = when (value) {
        null -> "null"
        is String -> "string"
        is Long, is Int, is Short, is Byte -> "integer"
        is Double, is Float -> "decimal"
        is Boolean -> "boolean"
        is List<*> -> "list"
        is Map<*, *> -> "object"
        else -> value.javaClass.simpleName
    }

    /** The params as pretty JSON, for copying into a bug report or a test. */
    fun json(params: Map<String, Any?>): String = buildString { appendJson(params, 0) }

    private fun StringBuilder.appendJson(value: Any?, indent: Int) {
        val pad = "  ".repeat(indent + 1)
        when (value) {
            null -> append("null")
            is String -> append(quote(value))
            is Number, is Boolean -> append(value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    append("{}")
                    return
                }
                append("{\n")
                value.entries.forEachIndexed { i, (key, item) ->
                    append(pad).append(quote(key.toString())).append(": ")
                    appendJson(item, indent + 1)
                    if (i < value.size - 1) append(',')
                    append('\n')
                }
                append("  ".repeat(indent)).append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    append("[]")
                    return
                }
                append("[\n")
                value.forEachIndexed { i, item ->
                    append(pad)
                    appendJson(item, indent + 1)
                    if (i < value.size - 1) append(',')
                    append('\n')
                }
                append("  ".repeat(indent)).append(']')
            }
            else -> append(quote(value.toString()))
        }
    }

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

/**
 * Firebase Analytics' documented limits. Firebase does not report breaking them: it drops the
 * event, the parameter or the property, or cuts the value, so the mistake surfaces weeks later as
 * missing data. Checked only for events recorded with the Firebase provider.
 */
internal object FirebaseRules {

    private const val MAX_EVENT_NAME = 40
    private const val MAX_PARAMS = 25
    private const val MAX_PARAM_NAME = 40
    private const val MAX_PARAM_VALUE = 100
    private const val MAX_PROPERTY_NAME = 24
    private const val MAX_PROPERTY_VALUE = 36

    private val NAME = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    private val RESERVED_EVENTS = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_query", "ad_reward", "adunit_exposure",
        "app_background", "app_clear_data", "app_exception", "app_remove", "app_store_refund",
        "app_store_subscription_cancel", "app_store_subscription_convert", "app_store_subscription_renew",
        "app_update", "app_upgrade", "dynamic_link_app_open", "dynamic_link_app_update",
        "dynamic_link_first_open", "error", "first_open", "first_visit", "in_app_purchase",
        "notification_dismiss", "notification_foreground", "notification_open", "notification_receive",
        "os_update", "session_start", "session_start_with_rollout", "user_engagement",
    )
    private val RESERVED_PROPERTIES = setOf("first_open_time", "last_deep_link_referrer", "user_id")

    fun appliesTo(provider: String): Boolean = provider.equals("Firebase", ignoreCase = true)

    fun eventIssues(name: String, params: Map<String, Any?>): List<String> = buildList {
        nameIssues("Event name", name, MAX_EVENT_NAME)?.let(::add)
        if (name in RESERVED_EVENTS) add("\"$name\" is reserved by Firebase; the event is dropped.")
        if (params.size > MAX_PARAMS) add("${params.size} parameters; Firebase keeps only $MAX_PARAMS.")
        params.forEach { (key, value) ->
            nameIssues("Parameter \"$key\"", key, MAX_PARAM_NAME)?.let(::add)
            if (value is String && value.length > MAX_PARAM_VALUE) {
                add("Parameter \"$key\" is ${value.length} characters; Firebase cuts it to $MAX_PARAM_VALUE.")
            }
        }
    }

    fun propertyIssues(name: String, value: String?): List<String> = buildList {
        nameIssues("User property name", name, MAX_PROPERTY_NAME)?.let(::add)
        if (name in RESERVED_PROPERTIES) add("\"$name\" is reserved by Firebase.")
        if (value != null && value.length > MAX_PROPERTY_VALUE) {
            add("Value is ${value.length} characters; Firebase cuts it to $MAX_PROPERTY_VALUE.")
        }
    }

    private fun nameIssues(what: String, name: String, max: Int): String? = when {
        name.isEmpty() -> "$what is empty."
        name.length > max -> "$what is ${name.length} characters; the limit is $max."
        !NAME.matches(name) -> "$what \"$name\" must start with a letter and use only letters, digits and _."
        RESERVED_PREFIXES.any { name.startsWith(it) } -> "$what \"$name\" uses a prefix reserved by Firebase."
        else -> null
    }
}
