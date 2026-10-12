package dev.debugkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/** The title of the sub-page shown in the current tab, or null when the tab shows its main page. */
internal val LocalSubpage = staticCompositionLocalOf<MutableState<String?>?> { null }

/**
 * Marks the page being composed as a sub-page, such as one request's details. While it is shown,
 * the tab's title and page switcher give way to a back arrow and [title], and the bottom bar
 * hides. The back arrow goes through the back dispatcher, so the page's own BackHandler closes it.
 */
@Composable
public fun DebugKitSubpage(title: String) {
    val subpage = LocalSubpage.current ?: return
    DisposableEffect(subpage, title) {
        subpage.value = title
        onDispose { if (subpage.value == title) subpage.value = null }
    }
}
