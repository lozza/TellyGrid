package io.github.lozza.tellygrid.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
            .filter { it.channel.providerId() == providerId }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LauncherInk),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 50.dp, vertical = 18.dp),
                ) {
                    LauncherBrand()
                    Spacer(Modifier.width(34.dp))
                    LauncherNavItem("HOME", selected = true, onClick = {})
                    LauncherNavItem("APPS", onClick = onOpenApps)
                    LauncherNavItem("GUIDE", onClick = onOpenGuide)
                    LauncherNavItem("SETTINGS", onClick = onOpenSettings)
                    Spacer(Modifier.weight(1f))
                    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), color = LauncherPaper, fontSize = 18.sp)
                }
                FeaturedHero(featuredChannel, featuredProgramme, featuredTitle, featuredSynopsis, guideStatus, onOpenGuide)
            }
        }
        if (streamingApps.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 50.dp)) {
                SectionHeading("YOUR APPS", "Your chosen order")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(streamingApps) { app -> StreamingAppTile(app, onClick = { onLaunchApp(app) }) }
                }
            }
        }
        if (!tvListingsAllowed) item {
            Column(Modifier.padding(horizontal = 50.dp)) {
                SectionHeading("APP RECOMMENDATIONS", "Allow TellyGrid to show the programmes your TV apps publish")
                Spacer(Modifier.height(10.dp))
                LauncherNavItem("ENABLE APP RECOMMENDATIONS", onClick = onRequestTvListingsPermission)
            }
        }
        val publishedWatchNext = publishedRecommendations.watchNext
            .filter { it.packageName in installedByPackage }
            .take(12)
        if (publishedWatchNext.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 50.dp)) {
                SectionHeading("WATCH NEXT", "From your TV apps")
                Spacer(Modifier.height(12.dp))
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
            Column(Modifier.padding(horizontal = 50.dp)) {
                SectionHeading("BBC IPLAYER", "Most popular on iPlayer")
                Spacer(Modifier.height(12.dp))
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
                Column(Modifier.padding(horizontal = 50.dp)) {
                    SectionHeading(app.label, "Recommended by ${app.label}")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(recommendations) { recommendation ->
                            PublishedRecommendationCard(recommendation, app, onOpenPublishedProgramme)
                        }
                    }
                }
            }
        }
        if (watchNextItems.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = 50.dp)) {
                LauncherShelf("ON NOW", "Live programmes across your linked services", watchNextItems, onWatchProgramme)
            }
        }
        if (seriesItems.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = 50.dp)) {
                LauncherShelf("RECOMMENDED SERIES", "Shows from your connected apps", seriesItems, onWatchProgramme)
            }
        }
        if (movieItems.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = 50.dp)) {
                LauncherShelf("RECOMMENDED MOVIES", "Films and movie channels", movieItems, onWatchProgramme)
            }
        }
        providerShelves.forEach { (providerId, app, items) ->
            item {
                Box(Modifier.padding(horizontal = 50.dp)) {
                    // Keep the shelf identity tied to the catalogue provider. The
                    // installed app label may be a wrapper such as "Freeview Play"
                    // and must not rename NOW/Sky recommendations.
                    val providerName = ProviderRegistry.all
                        .firstOrNull { it.id == providerId }
                        ?.displayName
                        ?: app.label
                    val shelfName = if (providerId == ProviderId.NOW) "NOW TV" else "${providerName.uppercase()} LIVE TV"
                    LauncherShelf(shelfName, "Available through this installed app", items, onWatchProgramme)
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 50.dp)) {
                Column {
                    SectionHeading("ONES TO WATCH", "Live now and coming up")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(watchItems) { (channel, programme) ->
                            WatchCard(channel, programme) {
                                if (programme != null) onWatchProgramme(channel, programme) else onOpenGuide()
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 50.dp)) {
                SectionHeading("BROWSE", "Jump into a part of the guide")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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

@Composable
fun LauncherAppsScreen(
    installedApps: List<InstalledTvApp>,
    appOrder: List<String>,
    onAppOrderChanged: (List<String>) -> Unit,
    onLaunchApp: (InstalledTvApp) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showHomeSettings by remember { mutableStateOf(false) }
    val orderedHomeApps = homeAppsFor(installedApps, appOrder)
    val orderedApps = orderLauncherApps(installedApps, emptyList())
    val streamingApps = orderedApps.filter(::isHomeStreamingApp)
    val otherApps = orderedApps.filterNot(::isHomeStreamingApp)
    val homePackageNames = orderedHomeApps.map { it.packageName }.toSet()
    val availableHomeApps = orderedApps.filterNot { it.packageName in homePackageNames }
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LauncherInk, Color(0xFF21162E)))).padding(horizontal = 50.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("APPS", color = LauncherPaper, fontSize = 24.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), color = LauncherPaper, fontSize = 18.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LauncherNavItem("HOME", onClick = onBack)
            LauncherNavItem("ALL APPS", selected = !showHomeSettings, onClick = { showHomeSettings = false })
            LauncherNavItem("HOME APP SETTINGS", selected = showHomeSettings, onClick = { showHomeSettings = true })
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showHomeSettings) item {
                Text("HOME ROW ORDER", color = LauncherPaper, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("All selected apps appear on Home", color = LauncherMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(orderedHomeApps) { app ->
                        val index = orderedHomeApps.indexOfFirst { it.packageName == app.packageName }
                        Column(Modifier.width(176.dp)) {
                            AppCard(app, { onLaunchApp(app) }, Modifier.width(176.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LauncherAction("↑", compact = true, onClick = {
                                    if (index > 0) onAppOrderChanged(orderedHomeApps.map { it.packageName }.move(index, index - 1))
                                })
                                LauncherAction("↓", compact = true, onClick = {
                                    if (index < orderedHomeApps.lastIndex) onAppOrderChanged(orderedHomeApps.map { it.packageName }.move(index, index + 1))
                                })
                                LauncherAction("×", compact = true, onClick = {
                                    onAppOrderChanged(orderedHomeApps.map { it.packageName }.filterNot { it == app.packageName })
                                })
                            }
                        }
                    }
                }
            }
            if (showHomeSettings) item {
                Text("ADD TO HOME", color = LauncherPaper, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Choose any installed TV app", color = LauncherMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(availableHomeApps) { app ->
                        Column(Modifier.width(176.dp)) {
                            AppCard(app, { onLaunchApp(app) }, Modifier.width(176.dp))
                            LauncherAction("ADD", onClick = {
                                onAppOrderChanged(orderedHomeApps.map { it.packageName } + app.packageName)
                            })
                        }
                    }
                }
            }
            if (!showHomeSettings) {
                if (streamingApps.isNotEmpty()) item {
                    Column {
                        SectionHeading("STREAMING", "")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                            items(streamingApps) { app -> AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp)) }
                        }
                    }
                }
                if (otherApps.isNotEmpty()) item {
                    Column {
                        SectionHeading("MORE APPS", "")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                            items(otherApps) { app -> AppCard(app, { onLaunchApp(app) }, Modifier.width(178.dp)) }
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

@Composable
private fun LauncherShelf(
    title: String,
    subtitle: String,
    items: List<LauncherShelfItem>,
    onWatch: (GuideChannel, Programme) -> Unit,
) {
    Column {
        SectionHeading(title, subtitle)
        Spacer(Modifier.height(12.dp))
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
private fun SectionHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, color = LauncherPaper, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(11.dp))
        Text(subtitle, color = LauncherMuted, fontSize = 10.sp)
    }
}

@Composable
private fun LauncherNavItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 10.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()) {
        Text(label, color = if (selected || focused) LauncherPaper else LauncherMuted, fontSize = 12.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 7.dp))
        Box(Modifier.width(48.dp).height(2.dp).background(if (focused) LauncherFocus else if (selected) LauncherSignal else Color.Transparent))
    }
}

@Composable
private fun LauncherBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Box(Modifier.width(18.dp).height(6.dp).background(LauncherSignal)); Box(Modifier.width(7.dp).height(6.dp).background(LauncherPaper)) }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Box(Modifier.width(7.dp).height(12.dp).background(LauncherPaper)); Box(Modifier.width(18.dp).height(12.dp).background(LauncherSignal.copy(alpha = 0.38f))) }
        }
        Spacer(Modifier.width(10.dp))
        Text("TELLY", color = LauncherPaper, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("GRID", color = LauncherSignal, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
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
            .clip(RoundedCornerShape(if (compact) 6.dp else 5.dp))
            .background(if (focused) LauncherFocus else LauncherQuiet)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .then(if (compact) Modifier.padding(top = 7.dp) else Modifier.padding(horizontal = 15.dp, vertical = 9.dp)),
    )
}
