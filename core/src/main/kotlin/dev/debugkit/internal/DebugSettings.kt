package dev.debugkit.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.debugkit.DebugKitConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Environment selection and flag overrides, persisted in SharedPreferences and mirrored in
 * snapshot state so DebugKit's screens (and any composable reading a flag) update immediately.
 */
internal object DebugSettings {

    /** Shared with other core stores, such as recent deep links. */
    const val PREFS_NAME = "debugkit_settings"
    private const val KEY_ENVIRONMENT = "environment.selected"
    private const val KEY_CUSTOM_URL = "environment.custom_url"
    private const val FLAG_PREFIX = "flag."

    private lateinit var prefs: SharedPreferences

    var environments: List<Environment> = emptyList()
        private set
    var flagDefinitions: List<FlagDefinition> = emptyList()
        private set
    var actions: List<ActionItem> = emptyList()
        private set

    /** Environment name, [EnvironmentResolver.CUSTOM], or null for the build's own URL. */
    var selectedEnvironment by mutableStateOf<String?>(null)
        private set
    var customUrl by mutableStateOf("")
        private set

    /** What the app read when it started. A different selection only applies after a restart. */
    var environmentAtLaunch: Pair<String?, String> = null to ""
        private set

    /** Boolean or String values, keyed by flag key. Absent means "use the app's default". */
    val overrides = mutableStateMapOf<String, Any>()

    /**
     * The default the app passed on its last read of each flag, which may come from remote config
     * rather than the declaration. Plain map: written from wherever the app reads flags, including
     * during composition, where writing snapshot state is not allowed.
     */
    val lastDefaults = ConcurrentHashMap<String, Any>()

    fun init(context: Context, config: DebugKitConfig) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        environments = config.environments.toList()
        flagDefinitions = config.flags.toList()
        actions = config.actions.toList()

        selectedEnvironment = prefs.getString(KEY_ENVIRONMENT, null)
        customUrl = prefs.getString(KEY_CUSTOM_URL, "").orEmpty()
        environmentAtLaunch = selectedEnvironment to customUrl

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(FLAG_PREFIX) && (value is Boolean || value is String)) {
                overrides[key.removePrefix(FLAG_PREFIX)] = value
            }
        }
    }

    // Environment

    fun environmentUrl(fallback: String): String =
        EnvironmentResolver.resolve(selectedEnvironment, customUrl, environments, fallback)

    val needsRestart: Boolean get() = (selectedEnvironment to customUrl) != environmentAtLaunch

    fun selectEnvironment(selection: String?) {
        selectedEnvironment = selection
        prefs.edit { if (selection == null) remove(KEY_ENVIRONMENT) else putString(KEY_ENVIRONMENT, selection) }
    }

    fun updateCustomUrl(url: String) {
        customUrl = url
        prefs.edit { putString(KEY_CUSTOM_URL, url) }
    }

    // Flags

    fun booleanFlag(key: String, default: Boolean): Boolean {
        lastDefaults[key] = default
        return overrides[key] as? Boolean ?: default
    }

    fun stringFlag(key: String, default: String): String {
        lastDefaults[key] = default
        return overrides[key] as? String ?: default
    }

    /**
     * What the Feature Flags screen calls when QA picks a value. Picking the app's own value
     * removes the override instead of storing a copy of it, so the flag reads as "not overridden"
     * again and keeps following the app if its value later changes (e.g. via remote config).
     */
    fun applyOverride(key: String, value: Any, appValue: Any) {
        if (value == appValue) clearOverride(key) else setOverride(key, value)
    }

    private fun setOverride(key: String, value: Any) {
        overrides[key] = value
        prefs.edit {
            when (value) {
                is Boolean -> putBoolean(FLAG_PREFIX + key, value)
                is String -> putString(FLAG_PREFIX + key, value)
            }
        }
    }

    fun clearOverride(key: String) {
        overrides.remove(key)
        prefs.edit { remove(FLAG_PREFIX + key) }
    }

    fun clearAllOverrides() {
        val keys = overrides.keys.toList()
        overrides.clear()
        prefs.edit { keys.forEach { remove(FLAG_PREFIX + it) } }
    }

    /**
     * Declared flags, plus any the app reads without declaring, so nothing is hidden from QA.
     * Undeclared ones are typed from the default the app passed.
     */
    fun allFlags(): List<FlagDefinition> {
        val declared = flagDefinitions.map { it.key }.toSet()
        val discovered = lastDefaults.entries
            .filter { it.key !in declared }
            .sortedBy { it.key }
            .mapNotNull { (key, default) ->
                when (default) {
                    is Boolean -> FlagDefinition.BooleanFlag(key, key, "Not declared in DebugKit.install", default)
                    is String -> FlagDefinition.TextFlag(key, key, "Not declared in DebugKit.install", default)
                    else -> null
                }
            }
        return flagDefinitions + discovered
    }
}
