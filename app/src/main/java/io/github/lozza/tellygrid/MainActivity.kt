package io.github.lozza.tellygrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.lozza.tellygrid.data.SampleGuideRepository
import io.github.lozza.tellygrid.data.SkyGuideRepository
import io.github.lozza.tellygrid.playback.AppHandoffLauncher
import io.github.lozza.tellygrid.ui.UnifiedGuideApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = AppHandoffLauncher(this)
        val sampleChannels = SampleGuideRepository().channels()

        setContent {
            var channels by remember { mutableStateOf(sampleChannels) }
            var guideStatus by remember { mutableStateOf("Loading Sky listings…") }

            LaunchedEffect(Unit) {
                runCatching {
                    withContext(Dispatchers.IO) { SkyGuideRepository(this@MainActivity).load() }
                }.onSuccess { result ->
                    channels = result.channels
                    guideStatus = result.status
                }.onFailure {
                    guideStatus = "Sky listings unavailable — showing demo data"
                }
            }

            UnifiedGuideApp(
                channels = channels,
                guideStatus = guideStatus,
                onHandoff = launcher::launch,
            )
        }
    }
}
