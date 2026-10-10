# DebugKit for Android

An in-app debugging toolkit for QA and developers: open it with a shake or a button while
your app is running.

> Early development. The roadmap covers a network inspector, response mocking, crash and log
> viewers, shake-to-report, environment switching, feature flags and a 3D layer inspector.

## Setup

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation("com.github.debugkit-dev.debugkit-android:core:<version>")
    debugImplementation("com.github.debugkit-dev.debugkit-android:network:<version>")
    debugImplementation("com.github.debugkit-dev.debugkit-android:logs:<version>")
    debugImplementation("com.github.debugkit-dev.debugkit-android:layers:<version>")
    releaseImplementation("com.github.debugkit-dev.debugkit-android:noop:<version>")
}
```

`Application.onCreate`:

```kotlin
DebugKit.install(this) {
    shakeToOpen = true
}
```

Open it from your own UI with `DebugKit.open(context)`. In release builds the `noop` module
provides the same API with empty implementations, so nothing ships to users.

### Environments, feature flags and quick actions

```kotlin
DebugKit.install(this) {
    environments {
        environment("Staging", "https://staging.api.example.com")
        environment("Dev", "https://dev.api.example.com")
    }
    flags {
        boolean("new_checkout", "New checkout")
        choice("onboarding", "Onboarding variant", options = listOf("A", "B", "C"))
        text("promo_code", "Promo code")
    }
    actions {
        section("Session")
        info("User ID") { session.userId }
        button("Log out") { session.logout() }
        toggle("Verbose logging", isChecked = { logger.verbose }, onCheckedChange = { logger.verbose = it })
    }
}

val baseUrl = DebugKit.environmentUrl(fallback = BuildConfig.API_URL)
val newCheckout = DebugKit.flag("new_checkout", default = remoteConfig.getBoolean("new_checkout"))
```

- **Environment**: QA picks a listed environment, a custom URL, or the build default. Changes
  apply after a restart, which the screen offers to do.
- **Feature Flags**: QA overrides any declared flag, and any flag the app reads with
  `DebugKit.flag` even if it is not declared. Reads inside a composable recompose on change.
- **Quick Actions**: the app's own buttons, toggles and copyable values.

In release builds `environmentUrl` always returns `fallback` and `flag` always returns `default`,
and the `actions` lambdas are never run.

### Analytics

Record each event next to the real analytics call, typically in the app's analytics wrapper:

```kotlin
fun logEvent(name: String, params: Bundle) {
    firebaseAnalytics.logEvent(name, params)
    DebugKit.recordAnalyticsEvent(name, params)          // or a Map; provider = "Adjust" for other tools
}

DebugKit.recordUserProperty("plan", "premium")
```

The **Analytics** screen lists events with their parameters (nested ecommerce items included),
lets you copy them as JSON, and shows the current user properties. Two kinds of problem are
flagged: events sent twice with the same parameters within a second, and, for Firebase, names
and values that break Firebase's limits (spaces in names, reserved prefixes, more than 25
parameters, strings over 100 characters, …), which Firebase drops or cuts without any error.
DebugKit sends nothing anywhere; in release builds these calls do nothing.

### Crashes

On by default. When the app crashes, DebugKit saves the stack trace with the app, device,
environment, flag overrides and current screen, plus the last 1,000 logs and 100 network calls,
then passes the crash on to the previous handler, so Crashlytics keeps working. Call
`DebugKit.install` after Crashlytics has initialised (it does so before `Application.onCreate`).
The next launch shows a dialog linking to the **Crashes** screen, which keeps the last 20.
Turn it off with `captureCrashes = false`, or keep recording without the dialog with
`showCrashDialog = false`.

### Layers

No setup code: adding the `layers` dependency is enough. Each time DebugKit opens over an app
screen (shake, or `DebugKit.open`), that screen's screenshot and element tree are captured.
The **Layers** screen then shows:

- **2D**: tap an element to see its properties. Views show class, id, fragment, size and
  position in px and dp, padding, margin, background, text, text size and color. Compose nodes
  show test tag, role, text, content description, click handling and modifiers.
- **3D**: every element as its own slice, lifted by its depth; drag to rotate, pinch to zoom,
  and adjust spacing and the visible depth range.
- **Measure**: tap two elements to see the gap between them in dp, or the inner one's distance
  to each edge when one contains the other (handy for checking padding against the design).
- **Color**: touch and drag to read the exact color under your finger as hex and RGB; recent
  colors stay one tap away for copying.
- **Tree**: the full hierarchy. XML Views are blue, Compose nodes green, and Views placed in
  Compose with `AndroidView` orange, each under the Compose node that hosts it.

In the flat modes, pinch to zoom in on small elements (two fingers in Color). A tap that lands on
background snaps to the nearest small element, so a near miss does not select the whole screen.

Compose content is read from its semantics tree, so give important composables a
`Modifier.testTag` to see meaningful names. Only the activity's main window is captured, not
dialogs or bottom sheets.

### Logs

```kotlin
Timber.plant(DebugKitTree())
```

The **Logs** screen keeps the last 5,000 Timber logs in memory: filter by minimum level, tap a
tag to show only that tag, search, tap an entry for the full message and stack trace,
long-press to copy, or share the filtered list. Untagged logs get the calling class's name, as
with Timber's `DebugTree`. DebugKitTree does not write to logcat, so keep a `DebugTree` planted
for that.

### Network inspector

```kotlin
OkHttpClient.Builder()
    .addInterceptor(
        DebugKitInterceptor(context) {
            redactHeaders = redactHeaders + "X-Api-Key"   // Authorization and cookies are redacted by default
            maxContentLength = 250_000L
        },
    )
    .build()
```

Add it with `addInterceptor`, after your own interceptors, so it records the request exactly as
it is sent. Calls show up under **Network**: status, timing, size, headers and pretty-printed
JSON, with copy-as-cURL and share. The last 500 calls are kept across restarts, so the calls
before a crash are still there on the next launch.

A silent notification shows this session's call count and the latest calls; tap it to open
Network, or use **Clear**. On Android 13+ the Network screen offers to grant the notification
permission, which is added to debug builds only. Turn it off with `showNotification = false`.

### Mock responses

Under **Mock Responses**, or from any call's **Mock response** button, create rules that answer
matching calls without hitting the server:

- **Custom response**: any status code, content type and body.
- **Network error**: timeout or no connection.
- **Real response, delayed**: the server's response after a delay.

URL patterns are globs (`*` matches anything) and must match the whole URL; the query string
is ignored unless the pattern contains `?`. Mocked calls are tagged **MOCK** in the list, and a
red **MOCK ON** pill sits on every app screen while any rule is enabled. Release builds use the
`noop` interceptor, so rules can never affect them.

## Modules

| Module | Purpose |
|---|---|
| `core` | Plugin API, DebugKit screen, shake-to-open, built-in App Info plugin |
| `network` | OkHttp interceptor and Network screen |
| `logs` | Timber tree and Logs screen |
| `layers` | Layers inspector for XML and Compose screens |
| `noop` | Release stand-in with the same public API (including `DebugKitInterceptor` and `DebugKitTree`) |
| `sample` | Demo app (not published) |

## Custom screens

Add your own screen from debug-only code:

```kotlin
DebugKit.registerPlugin(object : DebugPlugin {
    override val id = "my-app.session"
    override val title = "Session"

    @Composable
    override fun Content() { /* ... */ }
})
```

## Developing against a local app

In the app's `settings.gradle.kts`:

```kotlin
val debugKit = file("../debugkit-android")
if (debugKit.exists()) includeBuild(debugKit)
```

Gradle then builds DebugKit from source instead of downloading it.

## Releasing

Push a git tag (e.g. `0.1.0`). JitPack builds it on the first request.

## License

Apache 2.0. See [LICENSE](LICENSE).
