package io.github.lozza.tellygrid

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
            ProviderId.U,
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

    @Test
    fun `NOW entertainment lineup includes advertised channels`() {
        val expected = setOf("sky-one", "u-gold", "u-alibi", "mtv", "comedy-central", "sky-kids", "sky-mix")
        assertEquals(expected, ChannelCatalog.byXmlTvId.keys.intersect(expected))
        expected.forEach { id ->
            assertEquals(ProviderId.NOW, ChannelCatalog.byXmlTvId.getValue(id).provider.id)
        }
    }

    @Test
    fun `every NOW channel has a channel-specific deep link`() {
        ChannelCatalog.channels
            .filter { it.provider.id == ProviderId.NOW }
            .forEach { channel ->
                assertTrue("${channel.displayName} needs a NOW channel link", channel.channelUri != null)
                assertTrue(channel.channelUri!!.contains("%22serviceKey%22"))
            }
    }

    @Test
    fun `discovery plus lineup includes its UK entertainment channels`() {
        val expected = setOf(
            "discovery", "tlc", "quest", "quest-red", "investigation-discovery",
            "food-network", "hgtv", "dmax", "animal-planet", "discovery-science",
            "discovery-history", "discovery-turbo",
        )
        assertEquals(expected, ChannelCatalog.byXmlTvId.keys.intersect(expected))
        expected.forEach { id ->
            assertEquals(ProviderId.DISCOVERY_PLUS, ChannelCatalog.byXmlTvId.getValue(id).provider.id)
        }
    }

    @Test
    fun `main terrestrial channels carry their Freeview logical channel numbers`() {
        val expected = mapOf(
            "bbc-one" to 1,
            "bbc-two" to 2,
            "itv1" to 3,
            "channel-4" to 4,
            "five" to 5,
        )

        expected.forEach { (id, lcn) ->
            assertEquals(lcn, ChannelCatalog.byXmlTvId.getValue(id).terrestrialLcn)
        }
    }

    @Test
    fun `expanded terrestrial lineup has unique Freeview channel numbers`() {
        val freeview = ChannelCatalog.channels.filter { it.terrestrialLcn != null }
        assertTrue(freeview.size >= 30)
        assertEquals(freeview.size, freeview.map { it.terrestrialLcn }.distinct().size)
    }
}
