package dev.debugkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debugkit.R

/*
 * Building blocks every DebugKit screen is made of, so core and feature modules look like one
 * tool. Public for feature modules; host apps have no reason to use them.
 */

/** The large title at the top of a tab, with optional actions on the right. */
@Composable
public fun DebugKitTitle(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A quiet button for the title row, e.g. "Clear" or "Share". */
@Composable
public fun DebugKitTitleButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DebugKitColors.Border, RoundedCornerShape(12.dp))
            .background(DebugKitColors.Card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (enabled) DebugKitColors.Text else DebugKitColors.TextFaint)
    }
}

/** Switches between the pages of a tab, e.g. Requests and Mocks. */
@Composable
public fun DebugKitSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DebugKitColors.Card)
            .border(1.dp, DebugKitColors.Border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) DebugKitColors.Selected else DebugKitColors.Card)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) DebugKitColors.Text else DebugKitColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A rounded search box with a magnifier, and optional content at its end such as a match count. */
@Composable
public fun DebugKitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val textStyle = TextStyle(
        color = DebugKitColors.Text,
        fontSize = 15.sp,
        fontFamily = if (monospace) DebugKitFonts.Mono else DebugKitFonts.Sans,
    )
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DebugKitColors.Card)
            .border(1.dp, DebugKitColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.debugkit_ic_search), contentDescription = null, tint = DebugKitColors.TextSecondary, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = DebugKitColors.TextFaint), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(DebugKitColors.Accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        trailing()
    }
}

/** A grouped surface: rounded, bordered, slightly lighter than the background. */
@Composable
public fun DebugKitCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, DebugKitColors.Border)
    // Explicit, since Material would derive a dim content color from the card's surface color.
    val contentColor = DebugKitColors.Text
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = DebugKitColors.Card, contentColor = contentColor, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = DebugKitColors.Card, contentColor = contentColor, border = border) {
            Column(content = content)
        }
    }
}

/** An uppercase label above a group, e.g. "SAVED LINKS". */
@Composable
public fun DebugKitSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.6.sp),
        fontWeight = FontWeight.SemiBold,
        color = DebugKitColors.TextSecondary,
        modifier = modifier,
    )
}

/** A short status in its tone's colors: an HTTP code, a log level, "MOCK". */
@Composable
public fun DebugKitPill(text: String, tone: DebugTone, modifier: Modifier = Modifier, monospace: Boolean = true, minWidth: Int = 0) {
    Box(
        modifier = modifier
            .widthIn(min = minWidth.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(tone.container())
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = tone.color(),
            style = TextStyle(
                fontFamily = if (monospace) DebugKitFonts.Mono else DebugKitFonts.Sans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = if (monospace) 0.sp else 0.4.sp,
            ),
            maxLines = 1,
        )
    }
}

/** A filter chip: outlined when off, tinted with the accent when on. */
@Composable
public fun DebugKitChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, monospace: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) DebugKitColors.Accent.copy(alpha = 0.15f) else DebugKitColors.Card)
            .border(1.dp, if (selected) DebugKitColors.Accent.copy(alpha = 0.4f) else DebugKitColors.Border, shape)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> DebugKitColors.TextFaint
                selected -> DebugKitColors.Accent
                else -> DebugKitColors.Text
            },
            style = TextStyle(fontFamily = if (monospace) DebugKitFonts.Mono else DebugKitFonts.Sans, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** Centered guidance shown where a list has nothing yet. */
@Composable
public fun DebugKitEmpty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = DebugKitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Something QA should act on, such as a pending restart or missing permission, with the action beside it. */
@Composable
public fun DebugKitBanner(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DebugKitColors.WarningContainer)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = DebugKitColors.Warning,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        DebugKitButton(actionLabel, onClick = onAction)
    }
}

/** An action button: filled with the accent when [primary], otherwise a quiet bordered one. */
@Composable
public fun DebugKitButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        !enabled -> DebugKitColors.Card
        primary -> DebugKitColors.Accent
        else -> DebugKitColors.CardRaised
    }
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (primary && enabled) DebugKitColors.Accent else DebugKitColors.Border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> DebugKitColors.TextFaint
                primary -> DebugKitColors.OnAccent
                else -> DebugKitColors.Text
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
