package io.github.lozza.tellygrid

import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.SampleGuideRepository
import io.github.lozza.tellygrid.data.ChannelCatalog

class PlaybackSafetyTest {
    @Test
    fun `sample broadcaster channels never claim direct playback rights`() {
        val broadcasterIds = setOf(
            ProviderId.BBC_IPLAYER,
            ProviderId.ITVX,
            ProviderId.CHANNEL_4,
            ProviderId.FIVE,
            ProviderId.NOW,
            ProviderId.DISCOVERY_PLUS,
        )

        SampleGuideRepository().channels().forEach { channel ->
            val handoff = channel.playback as? PlaybackTarget.ProviderHandoff
            assertTrue("${channel.name} must hand off to its provider app", handoff != null)
            assertTrue(handoff!!.provider.id in broadcasterIds)
        }
    }

    @Test
    fun `Sky catalog always hands off to an authorised provider app`() {
        ChannelCatalog.channels.forEach { channel ->
            assertTrue(channel.provider.id != ProviderId.OWNED_STREAM)
        }
    }
}
