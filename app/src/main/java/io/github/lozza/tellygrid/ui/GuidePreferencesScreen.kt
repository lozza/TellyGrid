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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideDensity
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.PlaybackTarget

@Composable
fun GuidePreferencesScreen(
    channels: List<GuideChannel>,
    settings: GuideSettings,
    onDensityChanged: (GuideDensity) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    onResetOrder: () -> Unit,
    onResetChannelManagement: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val hiddenChannels = channels.filter { it.id in settings.hiddenChannelIds }.sortedBy { it.number }
    val orderedChannels = remember(channels, settings.channelOrder) {
        val orderIndex = settings.channelOrder.withIndex().associate { it.value to it.index }
        channels.sortedWith(compareBy<GuideChannel> { orderIndex[it.id] ?: Int.MAX_VALUE }.thenBy { it.number })
    }
    var movingChannelId by remember { mutableStateOf<String?>(null) }

    fun moveChannel(delta: Int) {
        val id = movingChannelId ?: return
        val index = orderedChannels.indexOfFirst { it.id == id }
        val next = index + delta
        if (index < 0 || next !in orderedChannels.indices) return
        val ids = orderedChannels.map { it.id }.toMutableList()
        ids[index] = ids[next]
        ids[next] = id
        onOrderChanged(ids)
    }

    Column(
        Modifier.fillMaxSize().background(ScreenBackground).then(ScreenPadding),
    ) {
        ScreenTitle("GUIDE PREFERENCES", "These choices stay on this TV. They never change your broadcaster apps or subscriptions.")
        Spacer(Modifier.height(22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
            item {
                PreferenceRow(
                    title = "Guide density",
                    detail = if (settings.density == GuideDensity.COMFORTABLE) "Comfortable — easier to read" else "Compact — more channels on screen",
                    action = "CHANGE",
                    onClick = {
                        onDensityChanged(
                            if (settings.density == GuideDensity.COMFORTABLE) GuideDensity.COMPACT else GuideDensity.COMFORTABLE,
                        )
                    },
                )
            }
            item {
                PreferenceRow(
                    title = "Restore all hidden channels",
                    detail = if (hiddenChannels.isEmpty()) "No channels are hidden" else "Restore ${hiddenChannels.size} hidden channel${if (hiddenChannels.size == 1) "" else "s"}",
                    action = "RESTORE",
                    onClick = onResetChannelManagement,
                )
            }
            item {
                PreferenceRow(
                    title = "Reset custom channel order",
                    detail = if (settings.channelOrder.isEmpty()) "Using logical channel numbers" else "Return channels to their default order",
                    action = "RESET",
                    onClick = { movingChannelId = null; onResetOrder() },
                )
            }
            item {
                PreferenceRow(title = "Channel order", detail = "Select a row, then use Up/Down to move it", action = "MOVE", onClick = { })
            }
            items(orderedChannels, key = { it.id }) { channel ->
                val hidden = channel.id in settings.hiddenChannelIds
                PreferenceRow(
                    title = if (movingChannelId == channel.id) "↕ ${channel.name}  (MOVING)" else channel.name,
                    detail = "${if (hidden) "Hidden" else "Visible"} • Channel ${channel.number} • ${(channel.playback as? PlaybackTarget.ProviderHandoff)?.provider?.displayName ?: "Provider"}",
                    action = if (hidden) "RESTORE" else if (movingChannelId == channel.id) "DONE" else "MOVE",
                    onClick = {
                        if (hidden && movingChannelId != channel.id) onToggleHidden(channel.id)
                        else movingChannelId = if (movingChannelId == channel.id) null else channel.id
                    },
                    onPreviewKeyEvent = if (movingChannelId == channel.id) { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            androidx.compose.ui.input.key.Key.DirectionUp -> moveChannel(-1).let { true }
                            androidx.compose.ui.input.key.Key.DirectionDown -> moveChannel(1).let { true }
                            else -> false
                        }
                    } else null,
                )
            }
            if (hiddenChannels.isNotEmpty()) {
                item {
                    Text("Hidden channels remain stored and ordered; restore them above.", color = ScreenMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
    onPreviewKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
) = SettingsRow(title, detail, action, onClick, onPreviewKeyEvent = onPreviewKeyEvent)
