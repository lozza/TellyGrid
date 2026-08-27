package io.github.lozza.tellygrid.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.lozza.tellygrid.data.PlaybackTarget

@Composable
fun PlayerScreen(target: PlaybackTarget.LicensedStream, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(target) {
        ExoPlayer.Builder(context).build().apply {
            val builder = MediaItem.Builder().setUri(target.streamUri)
            target.drmLicenseUri?.let { licenseUri ->
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(licenseUri)
                        .build(),
                )
            }
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    isFocusable = true
                    requestFocus()
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            onBack()
                            true
                        } else false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Licensed direct stream",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
        )
    }
}
