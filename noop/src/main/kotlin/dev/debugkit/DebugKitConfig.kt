@file:Suppress("UNUSED_PARAMETER")

package dev.debugkit

/**
 * Release stand-ins for `:core`'s configuration DSL. Every call is accepted and ignored, and the
 * builder blocks are never run, so app lambdas passed to them are never invoked in release.
 */
@DebugKitDsl
public class DebugKitConfig internal constructor() {

    public var shakeToOpen: Boolean = true

    public var captureCrashes: Boolean = true

    public var showCrashDialog: Boolean = true

    public fun environments(block: EnvironmentsBuilder.() -> Unit): Unit = Unit

    public fun flags(block: FlagsBuilder.() -> Unit): Unit = Unit

    public fun actions(block: ActionsBuilder.() -> Unit): Unit = Unit

    public fun deepLinks(block: DeepLinksBuilder.() -> Unit): Unit = Unit

    public fun push(block: PushBuilder.() -> Unit): Unit = Unit
}

@DebugKitDsl
public class PushBuilder internal constructor() {

    public fun token(provider: suspend () -> String?): Unit = Unit

    public fun onTestMessage(handler: (data: Map<String, String>) -> Unit): Unit = Unit

    public fun template(title: String, body: String, data: Map<String, String> = emptyMap(), link: String? = null): Unit = Unit
}

@DebugKitDsl
public class DeepLinksBuilder internal constructor() {

    public fun link(title: String, uri: String): Unit = Unit
}

@DslMarker
public annotation class DebugKitDsl

@DebugKitDsl
public class EnvironmentsBuilder internal constructor() {

    public fun environment(name: String, url: String): Unit = Unit
}

@DebugKitDsl
public class FlagsBuilder internal constructor() {

    public fun boolean(key: String, title: String = key, description: String? = null, default: Boolean = false): Unit = Unit

    public fun choice(
        key: String,
        title: String = key,
        options: List<String>,
        description: String? = null,
        default: String = "",
    ): Unit = Unit

    public fun text(key: String, title: String = key, description: String? = null, default: String = ""): Unit = Unit
}

@DebugKitDsl
public class ActionsBuilder internal constructor() {

    public fun section(title: String): Unit = Unit

    public fun button(title: String, summary: String? = null, onClick: () -> Unit): Unit = Unit

    public fun toggle(
        title: String,
        summary: String? = null,
        isChecked: () -> Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ): Unit = Unit

    public fun info(title: String, value: () -> String?): Unit = Unit
}
