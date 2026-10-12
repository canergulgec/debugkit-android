package dev.debugkit.internal

internal data class Environment(val name: String, val url: String)

internal sealed interface FlagDefinition {
    val key: String
    val title: String
    val description: String?

    /** The declared default: a Boolean for [BooleanFlag], a String otherwise. */
    val defaultValue: Any

    data class BooleanFlag(
        override val key: String,
        override val title: String,
        override val description: String?,
        val default: Boolean,
    ) : FlagDefinition {
        override val defaultValue: Any get() = default
    }

    data class ChoiceFlag(
        override val key: String,
        override val title: String,
        override val description: String?,
        val options: List<String>,
        val default: String,
    ) : FlagDefinition {
        override val defaultValue: Any get() = default
    }

    data class TextFlag(
        override val key: String,
        override val title: String,
        override val description: String?,
        val default: String,
    ) : FlagDefinition {
        override val defaultValue: Any get() = default
    }
}

internal sealed interface ActionItem {
    data class Section(val title: String) : ActionItem

    class Button(val title: String, val summary: String?, val onClick: () -> Unit) : ActionItem

    class Toggle(
        val title: String,
        val summary: String?,
        val isChecked: () -> Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : ActionItem

    class Info(val title: String, val value: () -> String?) : ActionItem
}

/** Pure selection logic, kept apart from storage so it can be unit tested. */
internal object EnvironmentResolver {

    /** Stored selection meaning "use the custom URL" rather than a named environment. */
    const val CUSTOM = "__custom__"

    /** The URL to use: the selected environment's, the custom one, or the build's own [fallback]. */
    fun resolve(selection: String?, customUrl: String, environments: List<Environment>, fallback: String): String =
        when (selection) {
            null -> fallback
            CUSTOM -> customUrl.trim().ifEmpty { fallback }
            else -> environments.firstOrNull { it.name == selection }?.url ?: fallback
        }
}
