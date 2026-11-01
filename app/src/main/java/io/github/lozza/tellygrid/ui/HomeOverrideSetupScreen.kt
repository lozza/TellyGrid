package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.R

/**
 * Full-screen setup gate shown whenever the "TellyGrid Home button override" accessibility
 * service is off: on first install, and again if the TV switches it off later (some TVs do
 * after a restart or an app update). TellyGrid cannot continue until it is enabled, because
 * without it the remote's Home button opens the stock launcher instead of TellyGrid.
 *
 * MainActivity re-checks the service every time it resumes, so returning from Android's
 * settings with the service on moves straight past this screen.
 */
@Composable
fun HomeOverrideSetupScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onCheckAgain: () -> Boolean,
) {
    var stillOff by remember { mutableStateOf(false) }
    // TellyGrid is the launcher, so Back has nowhere sensible to go from here.
    BackHandler {}
    MaterialTheme(typography = TellyGridTypography) {
        Box(Modifier.fillMaxSize().background(ScreenBackground)) {
            Column(
                Modifier.align(Alignment.CenterStart)
                    .padding(horizontal = 72.dp, vertical = 40.dp)
                    .widthIn(max = 820.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_full),
                    contentDescription = "TellyGrid",
                    modifier = Modifier.height(28.dp),
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    "ONE-TIME SETUP",
                    color = ScreenAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turn on the TellyGrid Home button",
                    color = ScreenPaper,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "TellyGrid needs its accessibility service switched on so the Home button on your " +
                        "remote always brings you back here, instead of the TV's built-in home screen. " +
                        "It only listens for the Home button. It does not read, record or change anything on screen.",
                    color = ScreenMuted,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(24.dp))
                SetupStep(1, "Select Open Accessibility settings below")
                SetupStep(2, "Choose TellyGrid Home button override")
                SetupStep(3, "Switch it on, confirm, then press Back to return here")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Can't find it? Open your TV's Settings, then Device Preferences (on Philips TVs: " +
                        "Android settings, Device Preferences), then Accessibility.",
                    color = ScreenMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SetupButton("Open Accessibility settings", onOpenAccessibilitySettings, primary = true)
                    SetupButton("I've turned it on", { stillOff = !onCheckAgain() }, primary = false)
                }
                if (stillOff) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "It's still switched off. Open Accessibility settings and turn on TellyGrid Home button override.",
                        color = FocusRing,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(RowSelected),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = ScreenPaper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = ScreenPaper, fontSize = 17.sp)
    }
}

@Composable
private fun SetupButton(label: String, onClick: () -> Unit, primary: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (primary) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Box(
        Modifier.height(56.dp)
            .clip(RowShape)
            .background(if (focused) RowFocused else RowIdle)
            .then(if (focused) Modifier.border(2.dp, FocusRing, RowShape) else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ScreenPaper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
