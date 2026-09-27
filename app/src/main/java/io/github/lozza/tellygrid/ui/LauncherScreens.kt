package io.github.lozza.tellygrid.ui

import io.github.lozza.tellygrid.R
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideCategory
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.Programme
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.ProviderRegistry
import io.github.lozza.tellygrid.data.TvPublishedProgramme
import io.github.lozza.tellygrid.data.BbcIplayerEpisode
import io.github.lozza.tellygrid.data.TvPublishedRecommendations
import io.github.lozza.tellygrid.playback.InstalledTvApp
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val LauncherInk = Color(0xFF0D0B18)
private val LauncherPanel = Color(0xFF1B172C)
private val LauncherQuiet = Color(0xFF28213F)
private val LauncherPaper = Color(0xFFF7F3FF)
private val LauncherMuted = Color(0xFFB4A9CC)
private val LauncherSignal = Color(0xFFA88CFF)
private val LauncherPink = Color(0xFFFF4FB3)
private val LauncherFocus = Color(0xFFFF365B)

private data class LauncherShelfItem(
    val channel: GuideChannel,
    val programme: Programme,
    val app: InstalledTvApp?,
)

/** Soft dark shadow so text stays readable where banner artwork runs behind it. */
private val TextOverArtwork = Shadow(color = Color.Black.copy(alpha = 0.9f), offset = Offset(0f, 2f), blurRadius = 12f)

/** Scrolls only as far as needed to reveal the focused item; no scrolling if it's already visible. */
@OptIn(ExperimentalFoundationApi::class)
private val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Don't move if the item is already on screen; otherwise reveal it with a small margin
        // so it doesn't sit hard against the screen edge.
        val margin = (containerSize * 0.06f).coerceAtMost(64f)
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset - margin
            else -> trailing - containerSize + margin
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherHomeScreen(
    guideStatus: String,
    featuredChannel: GuideChannel?,
    featuredProgramme: Programme?,
    guideChannels: List<GuideChannel>,
    linkedProviderApps: Map<ProviderId, InstalledTvApp>,
    installedApps: List<InstalledTvApp>,
    publishedRecommendations: TvPublishedRecommendations,
    tvListingsAllowed: Boolean,
    onRequestTvListingsPermission: () -> Unit,
    onOpenGuide: () -> Unit,
    onWatchProgramme: (GuideChannel, Programme) -> Unit,
    onLaunchApp: (InstalledTvApp) -> Unit,
    onOpenPublishedProgramme: (TvPublishedProgramme) -> Unit,
    bbcIplayerEpisodes: List<BbcIplayerEpisode>,
    onOpenBbcIplayerEpisode: (BbcIplayerEpisode) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onEnableHomeOverride: () -> Unit,
    launcherAppOrder: List<String>,
    hiddenWatchNextPackages: Set<String>,
) {
    val now = remember { Instant.now() }
    val featuredTitle = featuredProgramme?.title ?: "Your guide at a glance"
    val featuredSynopsis = featuredProgramme?.synopsis?.takeIf { it.isNotBlank() }
        ?: "Live channels, apps and everything worth watching in one place."
    val watchItems = remember(guideChannels, now) {
        guideChannels.take(8).map { channel ->
            channel to (channel.programmes.firstOrNull { it.endsAt.isAfter(now) } ?: channel.programmes.firstOrNull())
        }
    }
    val allShelfItems = remember(guideChannels, linkedProviderApps, now) {
        guideChannels.flatMap { channel ->
            // Keep a representative card for channels whose short EPG slice
            // has already expired. Otherwise a provider shelf collapses to
            // whichever single channel happened to have fresh data.
            val programmes = channel.programmes.filter { it.endsAt.isAfter(now) }
                .ifEmpty { channel.programmes.take(1) }
            programmes.map { programme ->
                LauncherShelfItem(channel, programme, channel.providerId()?.let(linkedProviderApps::get))
            }
        }
    }
    val watchNextItems = spreadAcrossApps(allShelfItems.sortedWith(compareBy<LauncherShelfItem> { it.programme.startsAt.isAfter(now) }.thenBy { it.programme.startsAt }))
    // Live channel listings are not on-demand recommendations. Only populate
    // these shelves with programmes that have an exact provider-issued link.
    val linkedProgrammeItems = allShelfItems.filter { it.programme.programmeIntent != null || it.programme.contentUri != null }
    val seriesItems = spreadAcrossApps(linkedProgrammeItems.filter { it.channel.category != GuideCategory.MOVIES })
    val movieItems = spreadAcrossApps(linkedProgrammeItems.filter { it.channel.category == GuideCategory.MOVIES })
    val providerShelves = linkedProviderApps.entries.mapNotNull { (providerId, app) ->
        val items = allShelfItems
            // Free-to-air channels (e.g. Sky Mix on Freeview) belong to the TV's tuner, not the app shelf.
            .filter { it.channel.providerId() == providerId && (it.channel.playback as? PlaybackTarget.ProviderHandoff)?.terrestrialLcn == null }
            .groupBy { it.channel.id }
            .values
            .mapNotNull { it.firstOrNull() }
            .take(8)
        if (items.isEmpty()) null else Triple(providerId, app, items)
    }
    val streamingApps = remember(installedApps, launcherAppOrder) {
        homeAppsFor(installedApps, launcherAppOrder)
    }
    val installedByPackage = remember(installedApps) { installedApps.associateBy { it.packageName } }
    // Build nothing until the TV's recommendations have been read, so the banner doesn't
    // flash the live-TV slide and then jump to an app pick once they arrive.
    val heroSlides = remember(publishedRecommendations, bbcIplayerEpisodes, installedByPackage, hiddenWatchNextPackages, featuredProgramme) {
        if (!publishedRecommendations.loaded) return@remember null
        buildHeroSlides(
            publishedRecommendations = publishedRecommendations,
            bbcIplayerEpisodes = bbcIplayerEpisodes,
            installedByPackage = installedByPackage,
            hiddenWatchNextPackages = hiddenWatchNextPackages,
            live = featuredChannel?.let { channel -> featuredProgramme?.let { channel to it } },
            onOpenPublishedProgramme = onOpenPublishedProgramme,
            onOpenBbcIplayerEpisode = onOpenBbcIplayerEpisode,
            onWatchProgramme = onWatchProgramme,
        )
    }

    // Android TV's default scrolling pins the focused row near the top of the screen, which
    // pushed the banner away as soon as the apps row was focused. Only scroll when needed.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(LauncherInk),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        item {
            // Like Virgin Media's home: the banner artwork fills the screen behind the
            // navigation bar, and the first row of apps sits over its lower edge.
            // Coming back up to the banner, the menu or Your apps: show the whole top screen,
            // not just enough of it to reveal the focused row.
            Box(
                (if (heroSlides?.isEmpty() == true) Modifier else Modifier.fillMaxWidth().height(540.dp))
                    .onFocusChanged {
                        // Short wait so this runs after the focus change's own scroll, not before it.
                        if (it.hasFocus) scope.launch { delay(150); listState.animateScrollToItem(0) }
                    },
            ) {
                if (heroSlides?.isEmpty() == true) {
                    Box(Modifier.padding(top = 84.dp)) {
                        FeaturedHero(featuredChannel, featuredProgramme, featuredTitle, featuredSynopsis, guideStatus, onOpenGuide)
                    }
                } else {
                    RotatingHero(heroSlides.orEmpty(), Modifier.fillMaxSize())
                    if (streamingApps.isNotEmpty()) Column(
                        Modifier.align(Alignment.BottomStart).padding(start = 50.dp, end = 50.dp, bottom = 20.dp),
                    ) {
                        SectionHeading("YOUR APPS")
                        Spacer(Modifier.height(14.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(streamingApps) { app -> StreamingAppTile(app, onClick = { onLaunchApp(app) }) }
                        }
                    }
                }
                CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(shadow = TextOverArtwork)) {
                LauncherTopBar(selected = "HOME", onHome = {}, onApps = onOpenApps, onGuide = onOpenGuide, onSettings = onOpenSettings)
                }
            }
        }
        if (heroSlides?.isEmpty() == true && streamingApps.isNotEmpty()) item {
            HomeSection {
                SectionHeading("YOUR APPS")
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(streamingApps) { app -> StreamingAppTile(app, onClick = { onLaunchApp(app) }) }
                }
            }
        }
        if (!tvListingsAllowed) item {
            HomeSection {
                SectionHeading("APP RECOMMENDATIONS", "Allow TellyGrid to show the programmes your TV apps publish")
                Spacer(Modifier.height(14.dp))
                LauncherNavItem("ENABLE APP RECOMMENDATIONS", onClick = onRequestTvListingsPermission)
            }
        }
        val publishedWatchNext = publishedRecommendations.watchNext
            .filter { it.packageName in installedByPackage && it.packageName !in hiddenWatchNextPackages }
            .take(12)
        if (publishedWatchNext.isNotEmpty()) item {
            HomeSection {
                SectionHeading("WATCH NEXT")
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(publishedWatchNext) { recommendation ->
                        PublishedRecommendationCard(recommendation, installedByPackage.getValue(recommendation.packageName), onOpenPublishedProgramme)
                    }
                }
            }
        }
        // BBC publishes no Android TV rows, so its shelf comes from BBC's own catalogue.
        val bbcApp = ProviderRegistry.bbc.packageCandidates.firstNotNullOfOrNull(installedByPackage::get)
        if (bbcApp != null && bbcIplayerEpisodes.isNotEmpty()) item {
            HomeSection {
                SectionHeading("BBC IPLAYER")
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(bbcIplayerEpisodes, key = { it.id }) { episode ->
                        RecommendationCard(
                            title = episode.title,
                            caption = episode.subtitle,
                            artworkUri = episode.artworkUri,
                            appLabel = "BBC iPlayer",
                            onClick = { onOpenBbcIplayerEpisode(episode) },
                        )
                    }
                }
            }
        }
        streamingApps.sortedBy { if (streamingRecommendationKey(it) == "hbo") 0 else 1 }.forEach { app ->
            val recommendations = publishedRecommendations.preview
                .filter { it.packageName == app.packageName }
                .take(12)
            if (recommendations.isNotEmpty()) item {
                HomeSection {
                    SectionHeading(app.label.uppercase())
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(recommendations) { recommendation ->
                            PublishedRecommendationCard(recommendation, app, onOpenPublishedProgramme)
                        }
                    }
                }
            }
        }
        if (watchNextItems.isNotEmpty()) item {
            HomeSection {
                LauncherShelf("ON NOW", "", watchNextItems, onWatchProgramme)
            }
        }
        if (seriesItems.isNotEmpty()) item {
            HomeSection {
                LauncherShelf("RECOMMENDED SERIES", "", seriesItems, onWatchProgramme)
            }
        }
        if (movieItems.isNotEmpty()) item {
            HomeSection {
                LauncherShelf("RECOMMENDED MOVIES", "", movieItems, onWatchProgramme)
            }
        }
        providerShelves.forEach { (providerId, app, items) ->
            item {
                HomeSection {
                    // Keep the shelf identity tied to the catalogue provider. The
                    // installed app label may be a wrapper such as "Freeview Play"
                    // and must not rename NOW/Sky recommendations.
                    val providerName = ProviderRegistry.all
                        .firstOrNull { it.id == providerId }
                        ?.displayName
                        ?: app.label
                    val shelfName = if (providerId == ProviderId.NOW) "NOW TV" else "${providerName.uppercase()} LIVE TV"
                    LauncherShelf(shelfName, "", items, onWatchProgramme)
                }
            }
        }
        item {
            HomeSection {
                SectionHeading("BROWSE")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("LIVE TV" to LauncherSignal, "MOVIES" to Color(0xFF9B73FF), "SPORT" to Color(0xFF3D9DFF), "KIDS" to LauncherPink).forEach { (label, color) ->
                        BrowseTile(label, color, onOpenGuide, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 50.dp, vertical = 20.dp)) {
                Text(guideStatus, color = LauncherMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                LauncherAction("SET AS DEFAULT", onSetDefaultLauncher)
                Spacer(Modifier.width(12.dp))
                LauncherAction("HOME OVERRIDE", onEnableHomeOverride)
            }
        }
    }
    }
}

@Composable
private fun StreamingAppTile(app: InstalledTvApp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(178.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(LauncherPanel)
            .border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(app.packageName, size = 100.dp, preferBanner = true)
    }
}

@Composable
private fun PublishedRecommendationCard(
    recommendation: TvPublishedProgramme,
    app: InstalledTvApp,
    onOpenProgramme: (TvPublishedProgramme) -> Unit,
) {
    RecommendationCard(
        title = recommendation.title,
        caption = null,
        artworkUri = recommendation.artworkUri,
        appLabel = app.label,
        onClick = { onOpenProgramme(recommendation) },
    )
}

@Composable
private fun RecommendationCard(
    title: String,
    caption: String?,
    artworkUri: String?,
    appLabel: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val image by rememberRemoteImage(artworkUri, maxDimension = 480)
    Column(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            Modifier.fillMaxWidth().height(124.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF59356A), Color(0xFF24203D))))
                .border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(6.dp)),
        ) {
            if (image != null) Image(image!!, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, contentScale = ContentScale.Crop)
            else Text(title, color = LauncherPaper, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, modifier = Modifier.align(Alignment.CenterStart).padding(16.dp))
            Text(appLabel, color = LauncherPaper, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).background(LauncherInk.copy(alpha = 0.72f)).padding(horizontal = 7.dp, vertical = 4.dp))
        }
        Text(title, color = LauncherPaper, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (caption != null) {
            Text(caption, color = LauncherMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun streamingRecommendationKey(app: InstalledTvApp): String? {
    val searchable = "${app.label} ${app.packageName}".lowercase()
    return when {
        searchable.contains("netflix") -> "netflix"
        searchable.contains("hbo max") || searchable.contains("hbomax") || searchable.contains("com.wbd.stream") -> "hbo"
        searchable.contains("disney") -> "disney"
        searchable.contains("prime video") || searchable.contains("primevideo") || searchable.contains("amazonvideo") -> "prime"
        else -> null
    }
}

private fun spreadAcrossApps(items: List<LauncherShelfItem>, limit: Int = 8): List<LauncherShelfItem> {
    val groups = items.groupBy { it.app?.packageName ?: "guide" }.values.map { it.toMutableList() }
    val result = mutableListOf<LauncherShelfItem>()
    while (result.size < limit && groups.any { it.isNotEmpty() }) {
        groups.forEach { group ->
            if (result.size < limit) group.removeFirstOrNull()?.let(result::add)
        }
    }
    return result
}

private fun GuideChannel.providerId(): ProviderId? = (playback as? PlaybackTarget.ProviderHandoff)?.provider?.id

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppsScreen(
    installedApps: List<InstalledTvApp>,
    appOrder: List<String>,
    onAppOrderChanged: (List<String>) -> Unit,
    watchNextPublishers: Set<String>,
    hiddenWatchNextPackages: Set<String>,
    onToggleWatchNextApp: (String) -> Unit,
    onLaunchApp: (InstalledTvApp) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val watchNextApps = installedApps.filter { it.packageName in watchNextPublishers }.sortedBy { it.label.lowercase() }
    var showHomeSettings by remember { mutableStateOf(false) }
    val orderedHomeApps = homeAppsFor(installedApps, appOrder)
    val orderedApps = orderLauncherApps(installedApps, emptyList())
    val streamingApps = orderedApps.filter(::isHomeStreamingApp)
    val otherApps = orderedApps.filterNot(::isHomeStreamingApp)
    val homePackageNames = orderedHomeApps.map { it.packageName }.toSet()
    val availableHomeApps = orderedApps.filterNot { it.packageName in homePackageNames }
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LauncherInk, Color(0xFF21162E))))) {
        LauncherTopBar(selected = "APPS", onHome = onBack, onApps = {}, onGuide = onOpenGuide, onSettings = onOpenSettings)
        Column(Modifier.weight(1f).padding(horizontal = 50.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LauncherTab("ALL APPS", selected = !showHomeSettings, onClick = { showHomeSettings = false }, requestInitialFocus = true)
            LauncherTab("HOME APP SETTINGS", selected = showHomeSettings, onClick = { showHomeSettings = true })
        }
        Spacer(Modifier.height(10.dp))
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // When a row gains focus, scroll its whole section (heading included) to the top,
        // so headings are never left half-hidden under the tabs.
        fun Modifier.snapSection(index: Int) = onFocusChanged {
            if (it.hasFocus) scope.launch { delay(250); listState.animateScrollToItem(index) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(36.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 420.dp),
        ) {
            if (showHomeSettings) item {
                SectionHeading("HOME ROW ORDER", "All selected apps appear on Home")
                Spacer(Modifier.height(12.dp))
                LazyRow(Modifier.snapSection(0), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(orderedHomeApps) { app ->
                        val index = orderedHomeApps.indexOfFirst { it.packageName == app.packageName }
                        Column(Modifier.width(178.dp)) {
                            AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp))
                            // The Home row runs left to right, so move controls point left and right.
                            Row(Modifier.width(178.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LauncherIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", enabled = index > 0) {
                                    onAppOrderChanged(orderedHomeApps.map { it.packageName }.move(index, index - 1))
                                }
                                LauncherIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", enabled = index < orderedHomeApps.lastIndex) {
                                    onAppOrderChanged(orderedHomeApps.map { it.packageName }.move(index, index + 1))
                                }
                                Spacer(Modifier.weight(1f))
                                LauncherIconButton(Icons.Filled.Close, "Remove from Home") {
                                    onAppOrderChanged(orderedHomeApps.map { it.packageName }.filterNot { it == app.packageName })
                                }
                            }
                        }
                    }
                }
            }
            if (showHomeSettings) item {
                SectionHeading("ADD TO HOME", "Choose any installed TV app")
                Spacer(Modifier.height(12.dp))
                LazyRow(Modifier.snapSection(1), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(availableHomeApps) { app ->
                        Column(Modifier.width(178.dp)) {
                            AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp))
                            Row(Modifier.padding(top = 8.dp)) {
                                LauncherIconButton(Icons.Filled.Add, "Add to Home") {
                                    onAppOrderChanged(orderedHomeApps.map { it.packageName } + app.packageName)
                                }
                            }
                        }
                    }
                }
            }
            if (showHomeSettings && watchNextApps.isNotEmpty()) item {
                SectionHeading("WATCH NEXT APPS", "Choose which apps can add programmes to Watch Next on Home")
                Spacer(Modifier.height(12.dp))
                LazyRow(Modifier.snapSection(2), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(watchNextApps, key = { it.packageName }) { app ->
                        val shown = app.packageName !in hiddenWatchNextPackages
                        Column(Modifier.width(178.dp)) {
                            AppCard(app, { onToggleWatchNextApp(app.packageName) }, Modifier.width(178.dp).alpha(if (shown) 1f else 0.4f))
                            Row(Modifier.padding(top = 8.dp)) {
                                LauncherIconButton(
                                    if (shown) Icons.Filled.Check else Icons.Filled.Add,
                                    if (shown) "Shown in Watch Next, select to hide" else "Hidden from Watch Next, select to show",
                                ) { onToggleWatchNextApp(app.packageName) }
                            }
                        }
                    }
                }
            }
            if (!showHomeSettings) {
                if (streamingApps.isNotEmpty()) item {
                    Column {
                        SectionHeading("STREAMING")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(Modifier.snapSection(0), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(streamingApps) { app -> AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp)) }
                        }
                    }
                }
                if (otherApps.isNotEmpty()) item {
                    Column {
                        SectionHeading("MORE APPS")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(Modifier.snapSection(1), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(otherApps) { app -> AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp)) }
                        }
                    }
                }
            }
        }
        }
    }
    }
}

private fun isHomeStreamingApp(app: InstalledTvApp): Boolean {
    val searchable = "${app.label} ${app.packageName}".lowercase()
    return searchable.contains("netflix") ||
        searchable.contains("prime video") || searchable.contains("primevideo") ||
        searchable.contains("now tv") || searchable.contains("nowtv") ||
        searchable.contains("hbo max") || searchable.contains("hbomax") || searchable.contains("com.wbd.stream") ||
        searchable.contains("disney+") || searchable.contains("disneyplus")
}

private fun homeAppsFor(installedApps: List<InstalledTvApp>, order: List<String>): List<InstalledTvApp> {
    val apps = if (order.isEmpty()) installedApps.filter(::isHomeStreamingApp) else {
        installedApps.filter { it.packageName in order }
    }
    return orderLauncherApps(apps.distinctBy { it.packageName }, order)
}

private fun orderLauncherApps(apps: List<InstalledTvApp>, order: List<String>): List<InstalledTvApp> {
    val indices = order.withIndex().associate { it.value to it.index }
    return apps.sortedWith(compareBy<InstalledTvApp> { indices[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
}

private fun List<String>.move(from: Int, to: Int): List<String> = toMutableList().also {
    val item = it.removeAt(from)
    it.add(to.coerceIn(0, it.size), item)
}

private data class HeroSlide(
    val label: String,
    val title: String,
    val synopsis: String,
    val imageUri: String?,
    val logoUri: String?,
    val action: String,
    val onSelect: () -> Unit,
)

/**
 * Up to 12 banner slides, alternating between sources so no single app or the live
 * guide dominates: app recommendations, BBC iPlayer, recently watched, then live TV.
 */
private fun buildHeroSlides(
    publishedRecommendations: TvPublishedRecommendations,
    bbcIplayerEpisodes: List<BbcIplayerEpisode>,
    installedByPackage: Map<String, InstalledTvApp>,
    hiddenWatchNextPackages: Set<String>,
    live: Pair<GuideChannel, Programme>?,
    onOpenPublishedProgramme: (TvPublishedProgramme) -> Unit,
    onOpenBbcIplayerEpisode: (BbcIplayerEpisode) -> Unit,
    onWatchProgramme: (GuideChannel, Programme) -> Unit,
): List<HeroSlide> {
    fun TvPublishedProgramme.toSlide(app: InstalledTvApp, subtitle: String) = HeroSlide(
        label = app.label.uppercase(),
        title = title,
        synopsis = description ?: subtitle,
        imageUri = artworkUri,
        logoUri = null,
        action = "OPEN IN ${app.label.uppercase()}  ›",
        onSelect = { onOpenPublishedProgramme(this) },
    )
    val groups = mutableListOf<MutableList<HeroSlide>>()
    publishedRecommendations.preview
        .filter { it.artworkUri?.startsWith("http://") == false && it.packageName in installedByPackage }
        .groupBy { it.packageName }
        .forEach { (packageName, programmes) ->
            val app = installedByPackage.getValue(packageName)
            groups += programmes.take(3).map { it.toSlide(app, "Recommended by ${app.label}") }.toMutableList()
        }
    bbcIplayerEpisodes.filter { it.artworkUri != null }.take(3).map { episode ->
        HeroSlide(
            label = "BBC IPLAYER",
            title = episode.title,
            synopsis = episode.subtitle ?: "Popular on BBC iPlayer",
            imageUri = episode.artworkUri,
            logoUri = null,
            action = "WATCH ON IPLAYER  ›",
            onSelect = { onOpenBbcIplayerEpisode(episode) },
        )
    }.takeIf { it.isNotEmpty() }?.let { groups += it.toMutableList() }
    publishedRecommendations.watchNext
        .filter { it.artworkUri != null && it.packageName in installedByPackage && it.packageName !in hiddenWatchNextPackages }
        .take(3)
        .map { it.toSlide(installedByPackage.getValue(it.packageName), "Continue watching") }
        .takeIf { it.isNotEmpty() }?.let { groups += it.toMutableList() }
    live?.let { (channel, programme) ->
        groups += mutableListOf(
            HeroSlide(
                label = "LIVE ON ${channel.name.uppercase()}",
                title = programme.title,
                synopsis = programme.synopsis.ifBlank { "On now" },
                imageUri = programme.imageUri,
                logoUri = channel.logoUri,
                action = "WATCH LIVE  ›",
                onSelect = { onWatchProgramme(channel, programme) },
            ),
        )
    }
    val slides = mutableListOf<HeroSlide>()
    while (slides.size < 12 && groups.any { it.isNotEmpty() }) {
        groups.forEach { group -> if (slides.size < 12) group.removeFirstOrNull()?.let(slides::add) }
    }
    return slides
}

@Composable
private fun RotatingHero(slides: List<HeroSlide>, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    var shownTitle by remember { mutableStateOf<String?>(null) }
    // When the list changes (e.g. iPlayer arrives), stay on the title already showing.
    LaunchedEffect(slides) {
        val same = slides.indexOfFirst { it.title == shownTitle }
        if (same >= 0) index = same
    }
    // Keyed on index so a manual Left/Right change restarts the 8-second timer.
    LaunchedEffect(slides, index) {
        if (slides.size > 1) {
            delay(8_000)
            index = (index + 1) % slides.size
        }
    }
    val slide = slides.getOrNull(index.coerceIn(0, (slides.size - 1).coerceAtLeast(0)))
    shownTitle = slide?.title
    Box(
        modifier = modifier.background(LauncherInk),
    ) {
        if (slide != null) Crossfade(targetState = slide, animationSpec = tween(700), label = "hero", modifier = Modifier.fillMaxSize()) { current ->
            Box(Modifier.fillMaxSize()) {
                // Try a larger copy first; the app's own (often thumbnail) image is the fallback.
                val large by rememberRemoteImage(largerArtworkUri(current.imageUri), maxDimension = 1920)
                val original by rememberRemoteImage(current.imageUri, maxDimension = 1920)
                val logo by rememberRemoteImage(current.logoUri, maxDimension = 320)
                val image = large ?: original
                if (image != null) {
                    Image(
                        image,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (logo != null) {
                    Image(logo!!, null, Modifier.size(width = 260.dp, height = 130.dp).align(Alignment.CenterEnd).padding(end = 54.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to LauncherInk.copy(alpha = 0.92f),
                    0.4f to LauncherInk.copy(alpha = 0.72f),
                    0.65f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to LauncherInk.copy(alpha = 0.92f),
                    0.16f to LauncherInk.copy(alpha = 0.65f),
                    0.34f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.85f to LauncherInk.copy(alpha = 0.85f),
                    1f to LauncherInk,
                ),
            ),
        )
        if (slide != null) CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(shadow = TextOverArtwork)) { Column(
            modifier = Modifier
                .width(535.dp)
                .padding(start = 56.dp, top = 114.dp),
        ) {
            Text(slide.label, color = LauncherPaper, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Text(slide.title, color = LauncherPaper, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            // Two lines max: with a two-line title, a third would push the button out of the banner.
            Text(slide.synopsis, color = LauncherPaper.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(17.dp))
            // Left/Right on the button steps through the slides by hand.
            Box(
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || slides.size < 2) false else when (event.key) {
                        Key.DirectionRight -> { index = (index + 1) % slides.size; true }
                        Key.DirectionLeft -> { index = (index - 1 + slides.size) % slides.size; true }
                        else -> false
                    }
                },
            ) {
                LauncherAction(slide.action, { slide.onSelect() })
            }
        } }
        if (slides.size > 1) Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 196.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slides.indices.forEach { i ->
                Box(
                    Modifier.size(width = if (i == index) 18.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i == index) LauncherPaper else LauncherPaper.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun FeaturedHero(channel: GuideChannel?, programme: Programme?, title: String, synopsis: String, guideStatus: String, onWatch: () -> Unit) {
    val image by rememberRemoteImage(programme?.imageUri)
    val logo by rememberRemoteImage(channel?.logoUri, maxDimension = 320)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF21162E), Color(0xFF513266), Color(0xFF8D5D9D)))),
    ) {
        if (image != null) {
            Image(
                image!!,
                null,
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.73f)
                    .align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
            )
        } else if (logo != null) {
            Image(logo!!, null, Modifier.size(width = 260.dp, height = 130.dp).align(Alignment.CenterEnd).padding(end = 54.dp), contentScale = ContentScale.Fit)
        } else {
            Text("TELLY\nGRID", color = LauncherSignal.copy(alpha = 0.4f), fontSize = 52.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 110.dp))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xFF21162E),
                    0.24f to Color(0xFF2C1C3B).copy(alpha = 0.99f),
                    0.44f to Color(0xFF392347).copy(alpha = 0.9f),
                    0.66f to Color(0xFF392347).copy(alpha = 0.28f),
                    0.88f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to LauncherInk.copy(alpha = 0.72f),
                    0.14f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to LauncherInk,
                ),
            ),
        )
        Column(
            modifier = Modifier
                .width(535.dp)
                .padding(start = 56.dp, top = 48.dp, bottom = 28.dp),
        ) {
            Text(
                text = channel?.providerLabel?.uppercase() ?: "FEATURED",
                color = LauncherPaper,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(22.dp))
            Text(title, color = LauncherPaper, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            Text(synopsis, color = LauncherPaper.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(17.dp))
            LauncherAction("OPEN GUIDE  ›", onWatch)
        }
    }
}

/**
 * One Home row (heading plus cards) with the standard side margins. When anything inside
 * gains focus the whole section, heading included, is scrolled fully into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSection(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 50.dp)
            .bringIntoViewRequester(requester)
            .onFocusChanged { if (it.hasFocus) scope.launch { delay(60); requester.bringIntoView() } },
        content = content,
    )
}

@Composable
private fun LauncherShelf(
    title: String,
    subtitle: String,
    items: List<LauncherShelfItem>,
    onWatch: (GuideChannel, Programme) -> Unit,
) {
    Column {
        SectionHeading(title, subtitle)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items) { item ->
                ShelfCard(item, onClick = { onWatch(item.channel, item.programme) })
            }
        }
    }
}

@Composable
private fun ShelfCard(item: LauncherShelfItem, onClick: () -> Unit) {
    val image by rememberRemoteImage(item.programme.imageUri, maxDimension = 480)
    val logo by rememberRemoteImage(item.channel.logoUri, maxDimension = 320)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF33256D)).border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(6.dp))) {
            if (image != null) Image(image!!, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, contentScale = ContentScale.Crop)
            else if (logo != null) Image(logo!!, null, Modifier.fillMaxSize().padding(28.dp), contentScale = ContentScale.Fit)
            else Text(item.programme.title, color = LauncherPaper, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.align(Alignment.CenterStart).padding(14.dp))
            item.app?.let { app ->
                Text(app.label, color = LauncherPaper, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).background(LauncherInk.copy(alpha = 0.75f)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Text(item.programme.title, color = LauncherPaper, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(item.channel.name, color = LauncherMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WatchCard(channel: GuideChannel, programme: Programme?, onClick: () -> Unit) {
    val image by rememberRemoteImage(programme?.imageUri, maxDimension = 480)
    val logo by rememberRemoteImage(channel.logoUri, maxDimension = 320)
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(220.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()) {
        Box(Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF33256D)).border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(6.dp))) {
            if (image != null) Image(image!!, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, contentScale = ContentScale.Crop)
            else if (logo != null) Image(logo!!, null, Modifier.fillMaxSize().padding(28.dp), contentScale = ContentScale.Fit)
            Text(channel.name, color = LauncherPaper, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).background(LauncherInk.copy(alpha = 0.75f)).padding(horizontal = 7.dp, vertical = 4.dp))
        }
        Text(programme?.title ?: "No listing", color = LauncherPaper, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BrowseTile(label: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.height(76.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(color.copy(alpha = 0.75f), LauncherPanel))).border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(6.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(15.dp)) {
        Text(label, color = LauncherPaper, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String = "") {
    Row {
        Text(title, color = LauncherPaper, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
        // Share the title's baseline so the smaller hint text lines up with it.
        if (subtitle.isNotBlank()) Text(subtitle, color = LauncherMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp).alignByBaseline())
    }
}

/** Logo, main menu and clock: identical on Home and Apps so the menu never moves between them. */
@Composable
private fun LauncherTopBar(selected: String, onHome: () -> Unit, onApps: () -> Unit, onGuide: () -> Unit, onSettings: () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(shadow = TextOverArtwork)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 50.dp, end = 50.dp, top = 26.dp, bottom = 18.dp),
        ) {
            // Fixed-width slot so the menu stays in the same place whatever the logo's size.
            Box(Modifier.width(133.dp)) { LauncherBrand() }
            Spacer(Modifier.width(34.dp))
            LauncherNavItem("HOME", selected = selected == "HOME", onClick = onHome)
            LauncherNavItem("APPS", selected = selected == "APPS", onClick = onApps)
            LauncherNavItem("GUIDE", selected = selected == "GUIDE", onClick = onGuide)
            LauncherNavItem("SETTINGS", selected = selected == "SETTINGS", onClick = onSettings)
            Spacer(Modifier.weight(1f))
            Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), color = LauncherPaper, fontSize = 18.sp)
        }
    }
}

/** Secondary tabs inside a page (e.g. All apps / Home app settings): same style as the main menu, smaller. */
@Composable
private fun LauncherTab(label: String, selected: Boolean, onClick: () -> Unit, requestInitialFocus: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (requestInitialFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(modifier = Modifier.padding(end = 28.dp).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()) {
        Text(label, color = if (selected || focused) LauncherPaper else LauncherMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 6.dp))
        Box(Modifier.width(32.dp).height(2.dp).background(if (focused) LauncherFocus else if (selected) LauncherSignal else Color.Transparent))
    }
}

@Composable
private fun LauncherNavItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()) {
        Text(label, color = if (selected || focused) LauncherPaper else LauncherMuted, fontSize = 12.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 7.dp))
        Box(Modifier.width(48.dp).height(2.dp).background(if (focused) LauncherFocus else if (selected) LauncherSignal else Color.Transparent))
    }
}

@Composable
private fun LauncherBrand() {
    Image(
        painter = painterResource(R.drawable.logo_full),
        contentDescription = "TellyGrid",
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun AppCard(app: InstalledTvApp, onLaunch: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.height(100.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(LauncherPanel)
            .border(3.dp, if (focused) LauncherFocus else Color.Transparent, RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onLaunch)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(app.packageName, size = 100.dp, preferBanner = true)
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    preferBanner: Boolean = false,
) {
    val context = LocalContext.current
    val icon: Pair<ImageBitmap, Boolean>? = remember(packageName, preferBanner) {
        runCatching {
            val packageManager = context.packageManager
            val banner = if (preferBanner) {
                val leanbackActivity = packageManager.getLeanbackLaunchIntentForPackage(packageName)?.component
                leanbackActivity?.let { component ->
                    runCatching { packageManager.getActivityBanner(component) }.getOrNull()
                } ?: packageManager.getApplicationBanner(packageName)
            } else null
            val drawable = banner ?: packageManager.getApplicationIcon(packageName)
            if (banner != null) {
                drawable.toBitmap(320, 180).asImageBitmap() to true
            } else {
                drawable.toBitmap(160, 160).asImageBitmap() to false
            }
        }.getOrNull()
    }
    val imageModifier = if (preferBanner) {
        Modifier.fillMaxWidth().height(size)
    } else {
        Modifier.size(size)
    }
    if (icon != null) Image(icon.first, packageName, imageModifier, contentScale = if (icon.second) ContentScale.Crop else ContentScale.Fit)
    else Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(LauncherQuiet), contentAlignment = Alignment.Center) { Text("APP", color = LauncherMuted, fontSize = 9.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun LauncherIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) LauncherFocus else LauncherQuiet)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = LauncherPaper.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LauncherAction(label: String, onClick: () -> Unit, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = LauncherPaper,
        fontSize = if (compact) 20.sp else 10.sp,
        lineHeight = if (compact) 20.sp else 12.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        softWrap = false,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .then(if (compact) Modifier.size(38.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) LauncherFocus else LauncherQuiet)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .then(if (compact) Modifier.padding(top = 7.dp) else Modifier.padding(horizontal = 15.dp, vertical = 9.dp)),
    )
}
