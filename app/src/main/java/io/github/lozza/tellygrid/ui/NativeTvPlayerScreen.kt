package io.github.lozza.tellygrid.ui

import android.media.tv.TvView
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.github.lozza.tellygrid.playback.NativeTvChannel

@Composable
fun NativeTvPlayerScreen(
    channel: NativeTvChannel,
    onBack: () -> Unit,
    onTuneFailed: () -> Unit,
) {
    val callback = remember(channel) {
        object : TvView.TvInputCallback() {
            override fun onConnectionFailed(inputId: String) = onTuneFailed()
            override fun onDisconnected(inputId: String) = onTuneFailed()
        }
    }
    val tvViewHolder = remember(channel) { arrayOfNulls<TvView>(1) }

    DisposableEffect(channel) {
        onDispose {
            tvViewHolder[0]?.reset()
            tvViewHolder[0] = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                TvView(context).apply {
                    tvViewHolder[0] = this
                    setCallback(callback)
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                        if (event.action == KeyEvent.ACTION_UP) onBack()
                        true
                    }
                    isFocusable = true
                    requestFocus()
                    tune(channel.inputId, channel.channelUri)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
