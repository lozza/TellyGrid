package io.github.lozza.tellygrid.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.PlaybackTarget
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun GuideScreen(
    channels: List<GuideChannel>,
    status: String?,
    onChannelSelected: (GuideChannel) -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 36.dp, vertical = 26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("TELLYGRID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("What’s on now", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderButton("APP SETUP", onOpenSetup)
                Spacer(Modifier.width(18.dp))
                Text(status ?: "Select a programme to watch", color = Color(0xFF9AA6B6), fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth().padding(start = 188.dp, bottom = 8.dp)) {
            Text("NOW", modifier = Modifier.weight(1.25f), color = Color(0xFF9AA6B6), fontSize = 13.sp)
            Text("NEXT", modifier = Modifier.weight(1f), color = Color(0xFF9AA6B6), fontSize = 13.sp)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(channels, key = { it.id }) { channel ->
                ChannelRow(channel = channel, onClick = { onChannelSelected(channel) })
            }
        }
    }
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Color(0xFF090D16) else MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChannelRow(channel: GuideChannel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (focused) Color(0xFF263246) else MaterialTheme.colorScheme.surface,
        label = "row background",
    )
    val accent = Color(channel.accentArgb)
    val current = channel.programmes.first()
    val next = channel.programmes.getOrNull(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(158.dp)) {
            Text("${channel.number}", color = Color(0xFF9AA6B6), fontSize = 12.sp)
            Text(channel.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(channel.providerLabel, color = Color(0xFF9AA6B6), fontSize = 11.sp, maxLines = 1)
        }
        ProgrammeCell(
            title = current.title,
            time = "${timeFormatter.format(current.startsAt)}–${timeFormatter.format(current.endsAt)}",
            modifier = Modifier.weight(1.25f),
        )
        ProgrammeCell(
            title = next?.title ?: "Schedule unavailable",
            time = next?.let { "${timeFormatter.format(it.startsAt)}–${timeFormatter.format(it.endsAt)}" } ?: "",
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when (val target = channel.playback) {
                is PlaybackTarget.ProviderHandoff -> if (target.terrestrialLcn != null) {
                    "WATCH LIVE TV"
                } else {
                    "OPEN ${target.provider.displayName.uppercase()}"
                }
                is PlaybackTarget.LicensedStream -> "WATCH"
                is PlaybackTarget.Unavailable -> "UNAVAILABLE"
            },
            color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF9AA6B6),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(128.dp),
        )
    }
}

@Composable
private fun ProgrammeCell(title: String, time: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp)) {
        Text(time, color = Color(0xFF9AA6B6), fontSize = 11.sp)
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
