package dev.debugkit

import dev.debugkit.internal.ActionItem
import dev.debugkit.internal.Environment
import dev.debugkit.internal.FlagDefinition

/** Options for [DebugKit.install]. Mirrored in `:noop`. */
@DebugKitDsl
public class DebugKitConfig internal constructor() {

    /** Opens DebugKit when the device is shaken while the app is in the foreground. */
    public var shakeToOpen: Boolean = true

    /**
     * Records uncaught exceptions with recent logs and network calls, then passes them on to the
     * previous handler, so Crashlytics keeps working. Install DebugKit after Crashlytics.
     */
    public var captureCrashes: Boolean = true

    /** After a crash, shows a dialog on the first screen of the next launch. */
    public var showCrashDialog: Boolean = true

    internal val environments = mutableListOf<Environment>()
    internal val flags = mutableListOf<FlagDefinition>()
    internal val actions = mutableListOf<ActionItem>()


    /** Backend environments QA can switch between. Read the choice with [DebugKit.environmentUrl]. */
    public fun environments(block: EnvironmentsBuilder.() -> Unit) {
        EnvironmentsBuilder(environments).block()
    }

    /** Feature flags QA can override. Read them with [DebugKit.flag]. */
    public fun flags(block: FlagsBuilder.() -> Unit) {
        FlagsBuilder(flags).block()
    }

    /** App-specific buttons, toggles and values shown under Quick Actions. */
    public fun actions(block: ActionsBuilder.() -> Unit) {
        ActionsBuilder(actions).block()
    }
}

/** Keeps nested DebugKit builders from calling into each other's scopes. */
@DslMarker
public annotation class DebugKitDsl

@DebugKitDsl
public class EnvironmentsBuilder internal constructor(private val target: MutableList<Environment>) {

    /** Adds an environment. [name] must be unique; it is what DebugKit stores as the selection. */
    public fun environment(name: String, url: String) {
        require(target.none { it.name == name }) { "Duplicate DebugKit environment: $name" }
        target += Environment(name, url)
    }
}

@DebugKitDsl
public class FlagsBuilder internal constructor(private val target: MutableList<FlagDefinition>) {

    /** An on/off flag, read with `DebugKit.flag(key, default = ...)`. */
    public fun boolean(key: String, title: String = key, description: String? = null, default: Boolean = false) {
        add(FlagDefinition.BooleanFlag(key, title, description, default))
    }

    /** A flag with a fixed set of values, such as an A/B variant. */
    public fun choice(
        key: String,
        title: String = key,
        options: List<String>,
        description: String? = null,
        default: String = options.first(),
    ) {
        require(options.isNotEmpty()) { "DebugKit choice flag $key needs at least one option" }
        add(FlagDefinition.ChoiceFlag(key, title, description, options, default))
    }

    /** A free-text flag, such as a promo code or a URL. */
    public fun text(key: String, title: String = key, description: String? = null, default: String = "") {
        add(FlagDefinition.TextFlag(key, title, description, default))
    }

    private fun add(flag: FlagDefinition) {
        require(target.none { it.key == flag.key }) { "Duplicate DebugKit flag: ${flag.key}" }
        target += flag
    }
}

@DebugKitDsl
public class ActionsBuilder internal constructor(private val target: MutableList<ActionItem>) {

    /** A heading that groups the items after it. */
    public fun section(title: String) {
        target += ActionItem.Section(title)
    }

    /** Runs [onClick] on the main thread. */
    public fun button(title: String, summary: String? = null, onClick: () -> Unit) {
        target += ActionItem.Button(title, summary, onClick)
    }

    /** A switch whose state lives in the app: DebugKit reads [isChecked] and reports changes. */
    public fun toggle(
        title: String,
        summary: String? = null,
        isChecked: () -> Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        target += ActionItem.Toggle(title, summary, isChecked, onCheckedChange)
    }

    /** A read-only value, such as a user id or push token. Tapping it copies the value. */
    public fun info(title: String, value: () -> String?) {
        target += ActionItem.Info(title, value)
    }
}
