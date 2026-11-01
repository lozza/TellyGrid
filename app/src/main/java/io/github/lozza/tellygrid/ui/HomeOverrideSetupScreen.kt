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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import android.os.Build
import io.github.lozza.tellygrid.R

/**
 * Full-screen setup gate shown whenever the "TellyGrid Home button override" accessibility
 * service is off: on first install, and again if the TV switches it off later (some TVs do
 * after a restart or an app update). Without it the remote's Home button opens the stock
 * launcher instead of TellyGrid, but the user can choose "Skip for now" and carry on.
 *
 * On Android 13+ a sideloaded app's accessibility service is a "restricted setting": the switch
 * flips straight back off until the user picks Allow restricted settings on TellyGrid's App info
 * page, so those steps are shown on newer Android versions.
 *
 * MainActivity re-checks the service every time it resumes, so returning from Android's
 * settings with the service on moves straight past this screen.
 */
@Composable
fun HomeOverrideSetupScreen(
    onOpenAccessibilitySettings: () -> Boolean,
    onOpenAppInfo: () -> Boolean,
    onCheckAgain: () -> Boolean,
    onSkip: () -> Unit,
) {
    val restricted = Build.VERSION.SDK_INT >= 33
    var stillOff by remember { mutableStateOf(false) }
    var settingsFailed by remember { mutableStateOf(false) }
    var appInfoFailed by remember { mutableStateOf(false) }
    // TellyGrid is the launcher, so Back has nowhere sensible to go from here.
    BackHandler {}
    MaterialTheme(typography = TellyGridTypography) {
        Box(Modifier.fillMaxSize().background(ScreenBackground)) {
            Column(
                Modifier.align(Alignment.CenterStart)
                    .verticalScroll(rememberScrollState())
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
                if (restricted) {
                    // Android 13+: allow restricted settings first, or the switch flips back off.
                    SetupStep(1, "Select Open TellyGrid app info below")
                    SetupStep(2, "Press ⋮ (top right), choose Allow restricted settings, then press Back")
                    SetupStep(3, "Select Open Accessibility settings and choose TellyGrid Home button override")
                    SetupStep(4, "Switch it on, confirm, then press Back to return here")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android 13 and newer blocks this switch for apps installed outside the Play Store, " +
                            "which is why it flips back off. No ⋮ menu? From a computer run: " +
                            "adb shell appops set io.github.lozza.tellygrid ACCESS_RESTRICTED_SETTINGS allow",
                        color = ScreenMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    SetupStep(1, "Select Open Accessibility settings below")
                    SetupStep(2, "Choose TellyGrid Home button override")
                    SetupStep(3, "Switch it on, confirm, then press Back to return here")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Can't find it? Open your TV's Settings and look for Accessibility. It's usually under " +
                        "Device Preferences, System or General (on Philips TVs: Android settings, Device Preferences).",
                    color = ScreenMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (restricted) {
                        SetupButton("Open TellyGrid app info", { appInfoFailed = !onOpenAppInfo() }, primary = true)
                    }
                    SetupButton("Open Accessibility settings", { settingsFailed = !onOpenAccessibilitySettings() }, primary = !restricted)
                    SetupButton("I've turned it on", { stillOff = !onCheckAgain() }, primary = false)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetupButton("Skip for now", onSkip, primary = false)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "TellyGrid still works, but your remote's Home button opens the TV's own home screen. " +
                            "Turn it on later from Settings, Home override.",
                        color = ScreenMuted,
                        fontSize = 13.sp,
                    )
                }
                if (appInfoFailed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This TV wouldn't open TellyGrid's app info from here. Open Settings, Apps, TellyGrid instead.",
                        color = FocusRing,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (settingsFailed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This TV wouldn't open its settings from here. Press the Settings button on your remote " +
                            "(or the gear icon), then find Accessibility.",
                        color = FocusRing,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (stillOff) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (restricted) {
                            "It's still switched off. If the switch flips back off, allow restricted settings " +
                                "in TellyGrid's app info first, then try again."
                        } else {
                            "It's still switched off. Open Accessibility settings and turn on TellyGrid Home button override."
                        },
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
