package dev.debugkit

import dev.debugkit.internal.ActionItem
import dev.debugkit.internal.Environment
import dev.debugkit.internal.FlagDefinition
import dev.debugkit.internal.deeplinks.DeepLink
import dev.debugkit.internal.push.PushTemplate

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
    internal val deepLinks = mutableListOf<DeepLink>()
    internal var pushConfigured = false
    internal var pushToken: (suspend () -> String?)? = null
    internal var pushHandler: ((Map<String, String>) -> Unit)? = null
    internal val pushTemplates = mutableListOf<PushTemplate>()

    /** Backend environments QA can switch between. Read the choice with [DebugKit.environmentUrl]. */
    public fun environments(block: EnvironmentsBuilder.() -> Unit) {
        EnvironmentsBuilder(environments).block()
    }

    /** Feature flags QA can override. Read them with [DebugKit.flag]. */
    public fun flags(block: FlagsBuilder.() -> Unit) {
        FlagsBuilder(flags).block()
    }

    /**
     * Push notification tools: the device token, ready-made test notifications, and a way to hand
     * test data to the app's own push code. Received pushes are shown when the app calls
     * [DebugKit.recordPush].
     */
    public fun push(block: PushBuilder.() -> Unit) {
        pushConfigured = true
        PushBuilder(this).block()
    }

    /** Links QA can open with one tap under Deep Links, e.g. one per screen the app links to. */
    public fun deepLinks(block: DeepLinksBuilder.() -> Unit) {
        DeepLinksBuilder(deepLinks).block()
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
public class PushBuilder internal constructor(private val config: DebugKitConfig) {

    /** Supplies the push token to show and copy, e.g. `FirebaseMessaging.getInstance().token.await()`. */
    public fun token(provider: suspend () -> String?) {
        config.pushToken = provider
    }

    /**
     * Receives a test notification's data as if the push arrived while the app is open; call
     * the same code the app's push service runs. Enables the "App's handler" option.
     */
    public fun onTestMessage(handler: (data: Map<String, String>) -> Unit) {
        config.pushHandler = handler
    }

    /**
     * A ready-made test notification. Tapping it launches the app with [data] as intent extras,
     * as Firebase does in the background, or opens [link] when given.
     */
    public fun template(title: String, body: String, data: Map<String, String> = emptyMap(), link: String? = null) {
        config.pushTemplates += PushTemplate(title, body, data, link)
    }
}

@DebugKitDsl
public class DeepLinksBuilder internal constructor(private val target: MutableList<DeepLink>) {

    /** A link such as `myapp://product/123` or `https://example.com/campaign`. */
    public fun link(title: String, uri: String) {
        target += DeepLink(title, uri)
    }
}

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

    /**
     * A single-choice list, shown as radio buttons. DebugKit reads [selected] to show the current
     * option and calls [onSelected] on the main thread when QA picks another one. Picking the
     * option that is already selected does nothing. With [pinToHome], the choice also shows on the
     * Home tab as a segmented control, for settings QA changes on almost every session.
     */
    public fun choice(
        title: String,
        options: List<String>,
        summary: String? = null,
        selected: () -> String?,
        pinToHome: Boolean = false,
        onSelected: (String) -> Unit,
    ) {
        require(options.isNotEmpty()) { "DebugKit choice \"$title\" needs at least one option" }
        target += ActionItem.Choice(title, summary, options, selected, onSelected, pinToHome)
    }
}
