package io.github.lozza.tellygrid

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.lozza.tellygrid.data.DiscoveryProgrammeLink
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.ProviderProgrammeIntent
import io.github.lozza.tellygrid.data.PrimeVideoProgrammeLink
import io.github.lozza.tellygrid.data.BbcIplayerProgrammeLink
import io.github.lozza.tellygrid.data.SampleGuideRepository
import io.github.lozza.tellygrid.data.ChannelCatalog
import io.github.lozza.tellygrid.data.GuideCategory
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.Programme
import io.github.lozza.tellygrid.data.mergeGuideSchedules
import io.github.lozza.tellygrid.data.mergeProviderSelections
import io.github.lozza.tellygrid.data.migratedDisclosure
import io.github.lozza.tellygrid.data.forGuideSettings
import io.github.lozza.tellygrid.data.searchGuide
import io.github.lozza.tellygrid.data.searchResultTimelineStart
import io.github.lozza.tellygrid.data.GuideReminder
import io.github.lozza.tellygrid.data.encodeReminder
import io.github.lozza.tellygrid.data.decodeReminder
import io.github.lozza.tellygrid.data.sameProgramme
import io.github.lozza.tellygrid.data.timelineStartForGuideDate
import io.github.lozza.tellygrid.data.sevenDayGuideDates
import io.github.lozza.tellygrid.playback.DiscoveryChannelAutomation
import java.time.Instant

class PlaybackSafetyTest {
    @Test
    fun `provider programme intent rejects guessed or incomplete destinations`() {
        assertTrue(!ProviderProgrammeIntent(
            packageName = "uk.co.freeview.ch4_vod",
            action = "android.intent.action.SEND",
        ).isComplete())
        assertTrue(!ProviderProgrammeIntent(
            packageName = "uk.co.freeview.ch4_vod",
            action = "android.intent.action.VIEW",
            mimeType = "text/plain",
        ).isComplete())
    }

    @Test
    fun `provider programme intent requires the selected app package`() {
        val intent = ProviderProgrammeIntent(
            packageName = "uk.co.freeview.ch4_vod",
            action = "android.intent.action.SEND",
            mimeType = "ply/request",
            stringExtras = mapOf("CONTENT_isProgram" to "true"),
        )
        assertTrue(intent.isComplete())
        assertTrue(intent.matchesSelectedPackage(null))
        assertTrue(intent.matchesSelectedPackage("uk.co.freeview.ch4_vod"))
        assertTrue(!intent.matchesSelectedPackage("com.channel4.ondemand"))
    }

    @Test
    fun `prime video accepts only provider-issued detail links`() {
        val uri = "https://www.primevideo.com/detail/0OVNAWZXI8WY7LFKU410H1ERG4"
        val intent = PrimeVideoProgrammeLink.intentOrNull(uri)

        assertEquals(PrimeVideoProgrammeLink.PACKAGE_NAME, intent?.packageName)
        assertEquals("com.amazon.ignition.IgnitionActivity", intent?.componentClassName)
        assertEquals(uri, intent?.dataUri)
        assertNull(PrimeVideoProgrammeLink.intentOrNull("https://www.primevideo.com/detail/Beetlejuice"))
        assertNull(PrimeVideoProgrammeLink.intentOrNull("https://example.com/detail/0OVNAWZXI8WY7LFKU410H1ERG4"))
    }

    @Test
    fun `bbc accepts only captured provider AIT episode links`() {
        val uri = "https://www.live.bbctvapps.co.uk/tap/iplayer/ait/launch/iplayer.aitx?deeplink=tv/playback/urn:bbc:iplayer:episode:m002y5ls&campaign=catalogue&medium=referral&partner=net.freeviewplay"
        val intent = BbcIplayerProgrammeLink.intentOrNull(uri)

        assertEquals(BbcIplayerProgrammeLink.PACKAGE_NAME, intent?.packageName)
        assertEquals(BbcIplayerProgrammeLink.PROVIDER_PACKAGE_NAME, intent?.providerPackageName)
        assertEquals(BbcIplayerProgrammeLink.ACTION, intent?.action)
        assertEquals(BbcIplayerProgrammeLink.MIME_TYPE, intent?.mimeType)
        assertEquals("FVP_AIT_URL", intent?.stringExtras?.get("Request"))
        assertEquals("CNT", intent?.stringExtras?.get("Mode"))
        assertEquals(uri, intent?.stringExtras?.get("Detail"))
        assertEquals(BbcIplayerProgrammeLink.CALLER, intent?.stringExtras?.get("Caller"))
        assertTrue(intent?.matchesSelectedPackage("uk.co.freeview.bbc") == true)
        assertTrue(intent?.matchesSelectedPackage("bbc.iplayer.android") == false)
        assertNull(BbcIplayerProgrammeLink.intentOrNull("https://www.bbc.co.uk/iplayer/episodes/m002y5ls"))
        assertNull(BbcIplayerProgrammeLink.intentOrNull("https://www.live.bbctvapps.co.uk/tap/iplayer/ait/launch/iplayer.aitx?deeplink=tv/playback/urn:bbc:iplayer:programme:m002y5ls"))
    }
    @Test
    fun `legacy provider migration imports missing choices without replacing new choices`() {
        val migrated = mergeProviderSelections(
            existing = mapOf(ProviderId.NOW to "retail.now"),
            legacy = mapOf(
                ProviderId.NOW to "old.now",
                ProviderId.BBC_IPLAYER to "legacy.bbc",
            ),
        )

        assertEquals("retail.now", migrated[ProviderId.NOW])
        assertEquals("legacy.bbc", migrated[ProviderId.BBC_IPLAYER])
    }

    @Test
    fun `legacy disclosure migration never clears an existing acknowledgement`() {
        assertTrue(migratedDisclosure(existing = true, legacy = false))
        assertTrue(migratedDisclosure(existing = null, legacy = true))
        assertTrue(!migratedDisclosure(existing = null, legacy = false))
    }

    @Test
    fun `provider filter combines safely with the visible guide`() {
        val nowOnly = SampleGuideRepository().channels().forGuideSettings(
            GuideSettings(providerFilter = ProviderId.NOW),
        )

        assertEquals(listOf("sky-atlantic"), nowOnly.map { it.id })
    }

    @Test
    fun `guide search groups current upcoming and later results without ended listings`() {
        val now = Instant.parse("2026-09-05T12:00:00Z")
        val channel = SampleGuideRepository(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)).channels().first()
        val results = searchGuide(listOf(channel), "bbc", now)

        assertEquals(listOf("On now", "Starting soon"), results.map { it.section.label })
        assertEquals("Morning Live", results.first().results.single().programme.title)
        assertEquals("BBC News at One", results.last().results.single().programme.title)
    }

    @Test
    fun `search result returns the grid to its containing half hour`() {
        val programme = Programme(
            id = "example",
            title = "Example",
            synopsis = "",
            startsAt = Instant.parse("2026-09-05T12:47:00Z"),
            endsAt = Instant.parse("2026-09-05T13:47:00Z"),
        )

        assertEquals(Instant.parse("2026-09-05T12:30:00Z").toEpochMilli(), searchResultTimelineStart(programme))
    }

    @Test
    fun `guide date starts at local midnight across UK daylight saving changes`() {
        val zone = java.time.ZoneId.of("Europe/London")

        assertEquals(
            Instant.parse("2026-03-29T00:00:00Z"),
            timelineStartForGuideDate(java.time.LocalDate.parse("2026-03-29"), zone),
        )
        assertEquals(
            Instant.parse("2026-10-24T23:00:00Z"),
            timelineStartForGuideDate(java.time.LocalDate.parse("2026-10-25"), zone),
        )
    }

    @Test
    fun `disabled provider rows are excluded without changing saved choices`() {
        val settings = GuideSettings(disabledProviderIds = setOf(ProviderId.NOW))
        val visible = SampleGuideRepository().channels().forGuideSettings(settings)

        assertTrue(visible.none { (it.playback as? PlaybackTarget.ProviderHandoff)?.provider?.id == ProviderId.NOW })
        assertEquals(setOf(ProviderId.NOW), settings.disabledProviderIds)
    }

    @Test
    fun `pluto filter is explicit rather than a generic FAST bucket`() {
        assertEquals("PLUTO TV", io.github.lozza.tellygrid.data.GuideFilter.FAST.label)
        assertTrue(ChannelCatalog.channels.any { it.provider.id == ProviderId.PLUTO_TV })
    }

    @Test
    fun `guide date strip always offers three consecutive local dates across DST`() {
        val zone = java.time.ZoneId.of("Europe/London")
        val start = java.time.LocalDate.parse("2026-10-25")

        val dates = sevenDayGuideDates(emptyList(), zone, start)

        assertEquals(3, dates.size)
        assertEquals(start, dates.first())
        assertEquals(start.plusDays(2), dates.last())
        assertEquals(
            Instant.parse("2026-10-24T23:00:00Z"),
            timelineStartForGuideDate(dates.first(), zone),
        )
    }

    @Test
    fun `reminder persistence format has a stable round trip and rejects corrupt values`() {
        val reminder = GuideReminder(
            "bbc-one",
            "bbc-one-2026-09-05T12:00",
            1_788_603_200_000L,
            "BBC One",
            "A title with tabs\tand punctuation",
        )

        assertEquals(reminder, decodeReminder(encodeReminder(reminder)))
        assertNull(decodeReminder("not-a-reminder"))
        assertNull(decodeReminder("bbc-one\tshow\tnot-a-time"))
    }

    @Test
    fun `old reminder records can still match refreshed programme metadata`() {
        val oldRecord = decodeReminder("bbc-one\tshow\t1788603200000")!!
        val refreshed = GuideReminder("bbc-one", "show", 1_788_603_200_000L, "BBC One", "The Show")

        assertTrue(oldRecord.sameProgramme(refreshed))
    }

    @Test
    fun `bundled schedule fills a new row missing from the hosted guide`() {
        val bundledChannel = SampleGuideRepository().channels().first()
        val placeholder = Programme(
            id = "${bundledChannel.id}-listings-unavailable",
            title = "Listings unavailable",
            synopsis = "",
            startsAt = Instant.EPOCH,
            endsAt = Instant.MAX,
        )
        val merged = mergeGuideSchedules(
            primary = listOf(bundledChannel.copy(programmes = listOf(placeholder))),
            bundledFallback = listOf(bundledChannel),
        )

        assertEquals(bundledChannel.programmes, merged.single().programmes)
    }

    @Test
    fun `only exact discovery plus programme links are accepted`() {
        val programmeLink = "https://play.discoveryplus.com/video/watch/cd9b5c04-9e6e-47a1-9b4f-8c0f2d933b51/aa8c4336-1e9d-4e1f-a399-fdf8148b7ce9"

        assertEquals(programmeLink, DiscoveryProgrammeLink.verifiedOrNull("discovery", programmeLink))
        assertNull(DiscoveryProgrammeLink.verifiedOrNull("bbc-one", programmeLink))
        assertNull(
            DiscoveryProgrammeLink.verifiedOrNull(
                "discovery",
                "https://play.discoveryplus.com/gb/channel/discovery-channel",
            ),
        )
    }

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
            ProviderId.HBO_MAX,
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
    fun `Pluto FAST channels use the verified Android TV entry route`() {
        val plutoChannels = ChannelCatalog.channels.filter { it.provider.id == ProviderId.PLUTO_TV }

        assertTrue("The curated Pluto row should have useful breadth", plutoChannels.size >= 25)
        plutoChannels.forEach { channel ->
            assertTrue(
                "${channel.displayName} must use Pluto's verified live-TV route",
                channel.channelUri?.startsWith("https://pluto.tv/gb_ie/watch/live-tv/") == true,
            )
            assertEquals(ProviderId.PLUTO_TV, channel.provider.id)
        }
        assertTrue(plutoChannels.any { it.category == GuideCategory.MOVIES })
        assertTrue(plutoChannels.any { it.category == GuideCategory.SPORT })
        assertTrue(plutoChannels.any { it.category == GuideCategory.KIDS })
    }

    @Test
    fun `catalogue keeps a unique stable ID separately from upstream XMLTV ID`() {
        assertEquals(ChannelCatalog.channels.size, ChannelCatalog.byId.size)
        ChannelCatalog.channels.forEach { channel ->
            assertEquals(channel, ChannelCatalog.byId.getValue(channel.id))
            assertTrue(channel.xmlTvId.isNotBlank())
        }
    }

    @Test
    fun `every advertised category filter has catalogue rows`() {
        GuideCategory.values().filter { it != GuideCategory.FAST }.forEach { category ->
            assertTrue(
                "$category filter must contain at least one channel",
                ChannelCatalog.channels.any { it.category == category },
            )
        }
        assertTrue(ChannelCatalog.channels.count { it.category == GuideCategory.MOVIES } >= 5)
        assertTrue(ChannelCatalog.channels.count { it.category == GuideCategory.SPORT } >= 8)
        assertTrue(ChannelCatalog.channels.count { it.category == GuideCategory.KIDS } >= 5)
        assertTrue(ChannelCatalog.channels.count { it.category == GuideCategory.NEWS } >= 2)
        assertTrue(ChannelCatalog.channels.count { it.category == GuideCategory.DOCUMENTARY } >= 5)
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
    fun `HBO Max lineup contains the four documented TNT Sports channels`() {
        val expected = setOf("tnt-sports-1", "tnt-sports-2", "tnt-sports-3", "tnt-sports-4")
        assertEquals(expected, ChannelCatalog.byXmlTvId.keys.intersect(expected))
        expected.forEach { id ->
            val channel = ChannelCatalog.byXmlTvId.getValue(id)
            assertEquals(ProviderId.HBO_MAX, channel.provider.id)
            assertNull("HBO Max channel links are not device-verified", channel.channelUri)
        }
    }

    @Test
    fun `discovery accessibility gestures cover channels present in the TV app row`() {
        val browseOnly = setOf("hgtv", "really")
        ChannelCatalog.channels
            .filter { it.provider.id == ProviderId.DISCOVERY_PLUS }
            .forEach { channel ->
                assertEquals(
                    "${channel.displayName} gesture availability changed",
                    channel.xmlTvId !in browseOnly,
                    DiscoveryChannelAutomation.hasDirectGesture(channel.xmlTvId),
                )
                DiscoveryChannelAutomation.planFor(channel.xmlTvId).tapXFraction?.let { x ->
                    assertTrue("${channel.displayName} tap must stay on-screen", x in 0f..1f)
                }
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
    fun `retail BBC and ITV core channels have provider-owned live links`() {
        val expectedProviders = mapOf(
            "bbc-one" to ProviderId.BBC_IPLAYER,
            "bbc-two" to ProviderId.BBC_IPLAYER,
            "bbc-three" to ProviderId.BBC_IPLAYER,
            "bbc-four" to ProviderId.BBC_IPLAYER,
            "itv1" to ProviderId.ITVX,
            "itv2" to ProviderId.ITVX,
            "itv3" to ProviderId.ITVX,
            "itv4" to ProviderId.ITVX,
        )

        expectedProviders.forEach { (id, providerId) ->
            val channel = ChannelCatalog.byXmlTvId.getValue(id)
            assertEquals(providerId, channel.provider.id)
            assertTrue("${channel.displayName} needs a retail-app channel link", channel.channelUri != null)
        }
    }

    @Test
    fun `expanded terrestrial lineup has unique Freeview channel numbers`() {
        val freeview = ChannelCatalog.channels.filter { it.terrestrialLcn != null }
        assertTrue(freeview.size >= 30)
        assertEquals(freeview.size, freeview.map { it.terrestrialLcn }.distinct().size)
    }
}
