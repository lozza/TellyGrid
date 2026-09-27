package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.Programme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProgrammeDetailsScreen(
    channel: GuideChannel,
    programme: Programme,
    isFavourite: Boolean,
    hasReminder: Boolean,
    canRemind: Boolean,
    notificationsAllowed: Boolean,
    onWatch: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onToggleReminder: () -> Unit,
    onHideChannel: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm").withZone(ZoneId.systemDefault()) }
    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF21162E), Color(0xFF4B3159), Color(0xFF21162E)))).padding(horizontal = 56.dp, vertical = 42.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PROGRAMME", color = Color(0xFFE0B5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(programme.title, color = Color(0xFFF3F6F8), fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${channel.name.uppercase()}  ·  ${formatter.format(programme.startsAt)}–${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(programme.endsAt)}",
            color = Color(0xFFE0B5FF), fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
        programme.episodeLabel?.takeIf(String::isNotBlank)?.let {
            Text(it, color = Color(0xFFCDBFDB), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            programme.synopsis.ifBlank { "Programme information is unavailable for this listing." },
            color = Color(0xFFD3D9DF), fontSize = 20.sp, lineHeight = 28.sp,
            modifier = Modifier.fillMaxWidth(0.76f),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailAction(label = detailWatchLabel(channel, programme), primary = true, initialFocus = true, onClick = onWatch)
            DetailAction(label = if (isFavourite) "REMOVE FAVOURITE" else "ADD FAVOURITE", onClick = onToggleFavourite)
            if (canRemind) {
                DetailAction(label = if (hasReminder) "CANCEL REMINDER" else "SET REMINDER", onClick = onToggleReminder)
                if (!notificationsAllowed) {
                    DetailAction(label = "ENABLE TV ALERTS", onClick = onRequestNotificationPermission)
                }
            }
            DetailAction(label = "HIDE CHANNEL", onClick = onHideChannel)
            DetailAction(label = "BACK", onClick = onBack)
        }
        Spacer(Modifier.weight(1f))
        Text(
            detailAvailability(channel, programme), color = Color(0xFFCDBFDB), fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(0.76f),
        )
        if (canRemind) {
            Text(
                when {
                    hasReminder && notificationsAllowed -> "A TV notification is scheduled locally for this programme."
                    hasReminder -> "This reminder is saved in TellyGrid. Enable TV alerts to receive a notification."
                    notificationsAllowed -> "Set a local reminder; TellyGrid will alert this TV around the start time."
                    else -> "Set an in-app reminder, then choose Enable TV alerts if you want a notification."
                },
                color = Color(0xFFCDBFDB), fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DetailAction(label: String, primary: Boolean = false, initialFocus: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (initialFocus) LaunchedEffect(Unit) { requester.requestFocus() }
    Text(
        text = label,
        color = Color(0xFFF3F6F8),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .background(if (focused) Color(0xFFFF365B) else if (primary) Color(0xFF6E477C) else Color(0xFF35263F), RoundedCornerShape(4.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

private fun detailWatchLabel(channel: GuideChannel, programme: Programme): String = when (val target = channel.playback) {
    is PlaybackTarget.ProviderHandoff -> when {
        programme.contentUri != null -> "OPEN EPISODE"
        target.terrestrialLcn != null -> "WATCH LIVE"
        else -> "OPEN ${target.provider.displayName.uppercase()}"
    }
    is PlaybackTarget.LicensedStream -> "WATCH"
    is PlaybackTarget.Unavailable -> "UNAVAILABLE"
}

private fun detailAvailability(channel: GuideChannel, programme: Programme): String = when (val target = channel.playback) {
    is PlaybackTarget.ProviderHandoff -> when {
        programme.contentUri != null -> "TellyGrid has a provider-owned programme link. Playback and sign-in remain in ${target.provider.displayName}."
        target.terrestrialLcn != null -> "This channel may use the local TV tuner on compatible televisions; otherwise TellyGrid opens ${target.provider.displayName}."
        else -> "TellyGrid opens the installed ${target.provider.displayName} app. A channel-specific route has not yet been verified on this TV."
    }
    is PlaybackTarget.LicensedStream -> "Licensed in-app playback."
    is PlaybackTarget.Unavailable -> target.reason
}
