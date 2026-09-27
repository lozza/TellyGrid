package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccessibilityDisclosureScreen(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
) {
    BackHandler(onBack = onNotNow)
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(horizontal = 56.dp, vertical = 42.dp),
    ) {
        Text("OPTIONAL FEATURE", color = Color(0xFF35E0A1), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Open discovery+ live channels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "discovery+ does not provide direct live-channel links. If you enable TellyGrid in Android Accessibility settings, TellyGrid can tap the discovery+ Browse screen after you select a channel.",
            color = Color(0xFFD6DCE6),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "The service is restricted to the discovery+ app. It does not read or record screen content, collect passwords, or act unless you select a discovery+ channel in TellyGrid. You must enable it yourself in Android settings, and you can disable it at any time.",
            color = Color(0xFF9AA6B6),
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text("HOW TO ENABLE IT", color = Color(0xFF35E0A1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open your TV's Settings app, then go to Device Preferences (sometimes called Android settings) → Accessibility → TellyGrid discovery+ channel control → Enable.",
            color = Color.White,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Philips TVs: Settings → Android settings → Device Preferences → Accessibility.",
            color = Color(0xFF9AA6B6),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        DisclosureAction(
            "Got it — continue to TellyGrid",
            "Open TV settings manually, then return to TellyGrid",
            onContinue,
            true,
        )
    }
}

@Composable
fun AccessibilityUnsupportedScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(horizontal = 56.dp, vertical = 42.dp),
    ) {
        Text("DEVICE LIMITATION", color = Color(0xFFFFB454), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Discovery+ direct channels are unavailable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "This TV has no touchscreen input source. Android therefore cancels accessibility touch gestures, even when the service is enabled.",
            color = Color(0xFFD6DCE6),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "The discovery+ TV app also exposes no individual Browse or channel controls to Android Accessibility, and Android does not let accessibility services send D-pad keys. TellyGrid will safely open discovery+ normally on this device.",
            color = Color(0xFF9AA6B6),
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(30.dp))
        DisclosureAction("Return to App Setup", "No accessibility control will be attempted", onBack, true)
    }
}

@Composable
private fun DisclosureAction(
    title: String,
    detail: String,
    onClick: () -> Unit,
    initialFocus: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (initialFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        Modifier.fillMaxWidth()
            .height(76.dp)
            .background(if (focused) Color(0xFF263246) else Color(0xFF151B28), RoundedCornerShape(12.dp))
            .then(if (focused) Modifier.border(2.dp, Color(0xFF35E0A1), RoundedCornerShape(12.dp)) else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Color(0xFF9AA6B6), fontSize = 13.sp)
    }
}
