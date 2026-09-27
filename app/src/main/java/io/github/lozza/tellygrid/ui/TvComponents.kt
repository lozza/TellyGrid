package io.github.lozza.tellygrid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Shared look for TellyGrid's full-screen pages (Settings, Guide filters, Guide
 * preferences, Search), so they match each other and the Home/Apps screens.
 */

val ScreenBackground = Brush.verticalGradient(listOf(Color(0xFF21162E), Color(0xFF4B3159), Color(0xFF21162E)))
val ScreenPaper = Color.White
val ScreenMuted = Color(0xFFCDBFDB)
val ScreenAccent = Color(0xFFE0B5FF)
val RowIdle = Color(0xFF35263F)
val RowSelected = Color(0xFF50345E)
val RowFocused = Color(0xFF8D294D)
val FocusRing = Color(0xFFFF365B)
val RowShape = RoundedCornerShape(4.dp)

/** Standard page padding, matching Home's 50dp side margins. */
val ScreenPadding = Modifier.padding(horizontal = 50.dp, vertical = 28.dp)

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(title, color = ScreenPaper, fontWeight = FontWeight.Medium, fontSize = 25.sp, letterSpacing = 1.sp)
    if (!subtitle.isNullOrBlank()) Text(subtitle, color = ScreenMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
fun SettingsSection(title: String) {
    Text(title.uppercase(), color = ScreenAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** One list row: title, optional detail, action label on the right, all vertically centred. */
@Composable
fun SettingsRow(
    title: String,
    detail: String?,
    action: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    requestInitialFocus: Boolean = false,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (requestInitialFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val background by animateColorAsState(
        when { focused -> RowFocused; selected -> RowSelected; else -> RowIdle },
        label = "row background",
    )
    Row(
        Modifier.fillMaxWidth()
            .height(64.dp)
            .clip(RowShape)
            .background(background)
            .then(if (focused) Modifier.border(2.dp, FocusRing, RowShape) else Modifier)
            .then(if (requestInitialFocus) Modifier.focusRequester(focusRequester) else Modifier)
            .then(onPreviewKeyEvent?.let { Modifier.onPreviewKeyEvent(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ScreenPaper, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, color = ScreenMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(action, color = if (focused) ScreenPaper else ScreenMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 16.dp))
    }
}
