package io.github.lozza.tellygrid.ui

import io.github.lozza.tellygrid.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideDensity
import io.github.lozza.tellygrid.data.GuideFilter
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.GuideReminder
import io.github.lozza.tellygrid.data.timelineStartForGuideDate
import io.github.lozza.tellygrid.data.sevenDayGuideDates
import io.github.lozza.tellygrid.data.sameProgramme
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.Programme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF21162E)
private val InkRaised = Color(0xFF422E4E)
private val InkQuiet = Color(0xFF18131F)
private val Paper = Color(0xFFF7F3FF)
private val Muted = Color(0xFFB4A9CC)
private val Signal = Color(0xFFA88CFF)
private val FocusRed = Color(0xFFFF365B)
private val Live = Color(0xFFFFB52E)
private val stationWidth = 200.dp
private const val windowMinutes = 120L
private val zone: ZoneId = ZoneId.systemDefault()
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM").withZone(zone)

@Composable
fun GuideScreen(
    channels: List<GuideChannel>,
    status: String?,
    onProgrammeSelected: (GuideChannel, Programme) -> Unit,
    onOpenHome: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSearch: () -> Unit,
    settings: GuideSettings,
    onOpenFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onTimelineChanged: (Long) -> Unit,
    onSelectionChanged: (String?, String?) -> Unit,
    onProgrammeDetails: (GuideChannel, Programme) -> Unit,
) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    var windowStartEpochMillis by rememberSaveable {
        mutableLongStateOf(settings.timelineStartEpochMillis ?: now.floorToHalfHour().toEpochMilli())
    }
    LaunchedEffect(settings.timelineStartEpochMillis) {
        windowStartEpochMillis = settings.timelineStartEpochMillis
            ?: now.floorToHalfHour().toEpochMilli()
    }
    val windowStart = Instant.ofEpochMilli(windowStartEpochMillis)
    val windowEnd = remember(windowStart) { windowStart.plus(Duration.ofMinutes(windowMinutes)) }
    val availableDates = remember(channels, now.atZone(zone).toLocalDate()) {
        sevenDayGuideDates(channels, zone, now.atZone(zone).toLocalDate())
    }
    val firstChannel = channels.firstOrNull { it.id == settings.selectedChannelId } ?: channels.firstOrNull()
    val firstProgramme = firstChannel?.programmes?.firstOrNull {
        it.id == settings.selectedProgrammeId && it.endsAt > windowStart && it.startsAt < windowEnd
    }
        ?: firstChannel?.programmes?.firstOrNull { it.endsAt > windowStart }
        ?: firstChannel?.programmes?.firstOrNull()
    var selectedChannel by remember(channels) { mutableStateOf(firstChannel) }
    var selectedProgramme by remember(channels) { mutableStateOf(firstProgramme) }
    val startingSoonReminder = remember(channels, settings.reminders, now) {
        channels.asSequence()
            .flatMap { channel -> channel.programmes.asSequence().map { channel to it } }
            .filter { (_, programme) ->
                programme.startsAt > now && programme.startsAt <= now.plus(Duration.ofMinutes(30))
            }
            .firstOrNull { (channel, programme) ->
                settings.reminders.any { it.sameProgramme(GuideReminder.forProgramme(channel, programme)) }
            }
    }
    val gridFocus = remember { FocusRequester() }
    val gridListState = rememberLazyListState()
    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }
    var focusGeneration by rememberSaveable { mutableIntStateOf(0) }
    val rowHeight = if (settings.density == GuideDensity.COMPACT) 42.dp else 50.dp
    var navigationAnchor by remember { mutableStateOf(firstProgramme?.startsAt ?: now) }
    var verticalMovePending by remember { mutableStateOf(false) }

    fun setSelected(channel: GuideChannel, programme: Programme?) {
        selectedChannel = channel
        selectedProgramme = programme
    }

    fun moveVertical(fromIndex: Int, direction: Int): Boolean {
        val nextChannel = channels.getOrNull(fromIndex + direction) ?: return false
        val available = nextChannel.programmes.filter { it.endsAt > windowStart && it.startsAt < windowEnd }
        val nextProgramme = available.firstOrNull { it.startsAt <= navigationAnchor && navigationAnchor < it.endsAt }
            ?: available.minByOrNull { kotlin.math.abs(Duration.between(navigationAnchor, it.startsAt).toMillis()) }
        verticalMovePending = true
        setSelected(nextChannel, nextProgramme)
        onSelectionChanged(nextChannel.id, nextProgramme?.id)
        focusGeneration++
        return true
    }

    fun moveWindow(minutes: Long) {
        val next = windowStart.plus(Duration.ofMinutes(minutes)).floorToHalfHour()
        windowStartEpochMillis = next.toEpochMilli()
        onTimelineChanged(windowStartEpochMillis)
        restoreGridSelection(channels, selectedChannel?.id, next, ::setSelected, onSelectionChanged)
        focusGeneration++
    }

    fun jumpToNow() {
        val next = now.floorToHalfHour()
        windowStartEpochMillis = next.toEpochMilli()
        onTimelineChanged(windowStartEpochMillis)
        restoreGridSelection(channels, selectedChannel?.id, next, ::setSelected, onSelectionChanged)
        focusGeneration++
    }

    fun jumpToDate(date: LocalDate) {
        val next = timelineStartForGuideDate(date, zone)
        windowStartEpochMillis = next.toEpochMilli()
        onTimelineChanged(windowStartEpochMillis)
        restoreGridSelection(channels, selectedChannel?.id, next, ::setSelected, onSelectionChanged)
        focusGeneration++
    }

    LaunchedEffect(channels.isNotEmpty(), focusGeneration) {
        if (channels.isNotEmpty()) {
            // Some TV launchers grant window focus noticeably later than phones.
            delay(if (initialFocusRequested) 16 else 450)
            runCatching { gridFocus.requestFocus() }
            initialFocusRequested = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF21162E), Color(0xFF32223E), Color(0xFF21162E)),
                ),
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == androidx.compose.ui.input.key.Key.Info &&
                    selectedChannel != null &&
                    selectedProgramme != null
                ) {
                    onProgrammeDetails(selectedChannel!!, selectedProgramme!!)
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        GuideHeader(
            channel = selectedChannel,
            programme = selectedProgramme,
            status = status,
            startingSoonReminder = startingSoonReminder,
            now = now,
            onOpenSetup = onOpenSetup,
            onOpenHome = onOpenHome,
            onOpenSearch = onOpenSearch,
            filterLabel = settings.headerFilterLabel,
            onOpenFilter = onOpenFilter,
        )
        Spacer(Modifier.height(8.dp))
        GuideDateStrip(
            dates = availableDates,
            selectedDate = windowStart.atZone(zone).toLocalDate(),
            today = now.atZone(zone).toLocalDate(),
            gridFocus = gridFocus,
            onDateSelected = { date ->
                if (date == now.atZone(zone).toLocalDate()) jumpToNow() else jumpToDate(date)
            },
        )
        Spacer(Modifier.height(8.dp))
        TimelineHeader(windowStart = windowStart, now = now)
        Spacer(Modifier.height(4.dp))
        if (channels.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = "NO CHANNELS MATCH THIS FILTER",
                    color = Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                HeaderButton(text = "CLEAR FILTER", onClick = onClearFilters)
            }
        } else {
            LazyColumn(
                state = gridListState,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { channelIndex, channel ->
                    ChannelTimelineRow(
                        channel = channel,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        now = now,
                        rowHeight = rowHeight,
                        selectedChannelId = selectedChannel?.id,
                        selectedProgrammeId = selectedProgramme?.id,
                        gridFocus = gridFocus,
                        fallbackFocus = channelIndex == 0,
                        onMoveVertical = { direction -> moveVertical(channelIndex, direction) },
                        onMoveWindow = ::moveWindow,
                        onFocused = { programme ->
                            setSelected(channel, programme)
                            if (verticalMovePending) verticalMovePending = false
                            else navigationAnchor = programme.startsAt.plus(Duration.between(programme.startsAt, programme.endsAt).dividedBy(2))
                            onSelectionChanged(channel.id, programme.id)
                        },
                        onClick = { programme -> onProgrammeSelected(channel, programme) },
                        onDetails = { programme -> onProgrammeDetails(channel, programme) },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun GuideDateStrip(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    gridFocus: FocusRequester,
    onDateSelected: (LocalDate) -> Unit,
) {
    if (dates.size <= 1) return
    val selectedFocus = remember { FocusRequester() }
    LazyRow(
        // Entering the strip from above or below always lands on the selected day,
        // and leaving downwards always returns to the selected programme.
        modifier = Modifier.focusProperties { enter = { selectedFocus } },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(dates, key = { it.toEpochDay() }) { date ->
            var focused by remember(date) { mutableStateOf(false) }
            val selected = date == selectedDate
            val label = when (date) {
                today -> "TODAY"
                today.plusDays(1) -> "TOMORROW"
                else -> date.format(DateTimeFormatter.ofPattern("EEE d")).uppercase()
            }
            Text(
                label,
                color = when {
                    focused -> Ink
                    selected -> Paper
                    else -> Muted
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            focused -> FocusRed
                            selected -> InkQuiet
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(4.dp),
                    )
                    .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                    .focusProperties { down = gridFocus }
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onDateSelected(date) }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun GuideHeader(
    channel: GuideChannel?,
    programme: Programme?,
    status: String?,
    startingSoonReminder: Pair<GuideChannel, Programme>?,
    now: Instant,
    onOpenSetup: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSearch: () -> Unit,
    filterLabel: String,
    onOpenFilter: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TellyGridLogo()
            Spacer(Modifier.width(20.dp))
            GuideClock(now)
            Spacer(Modifier.weight(1f))
            HeaderButton(text = "HOME", onClick = onOpenHome)
            Spacer(Modifier.width(9.dp))
            HeaderButton(text = "SEARCH", onClick = onOpenSearch)
            Spacer(Modifier.width(9.dp))
            HeaderButton(text = filterLabel, onClick = onOpenFilter)
            Spacer(Modifier.width(9.dp))
            HeaderButton(text = "SETTINGS", onClick = onOpenSetup)
            Spacer(Modifier.width(16.dp))
            Text("INFO / HOLD OK", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF463052), Color(0xFF32223F)),
                    ),
                )
            .padding(start = 18.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                Text(
                    text = programme?.title ?: "Select a programme",
                    color = Paper,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel != null && programme != null) {
                        Text(
                            text = "${channel.name.uppercase()}  ${timeFormatter.format(programme.startsAt)}–${timeFormatter.format(programme.endsAt)}",
                            color = Signal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        if (!programme.episodeLabel.isNullOrBlank()) {
                            Text("  /  ${programme.episodeLabel}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                }
                startingSoonReminder?.let { (reminderChannel, reminderProgramme) ->
                    Text(
                        text = "REMINDER · STARTING SOON: ${reminderProgramme.title} ON ${reminderChannel.name.uppercase()}",
                        color = Signal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = programme?.synopsis?.takeIf { it.isNotBlank() } ?: status.orEmpty(),
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ProgrammeArtwork(
                imageUri = programme?.imageUri,
                accent = Signal,
                action = actionLabel(channel, programme),
            )
        }
    }
}

@Composable
private fun TellyGridLogo() {
    Image(
        painter = painterResource(R.drawable.logo_full),
        contentDescription = "TellyGrid",
        modifier = Modifier.height(18.dp),
    )
}

@Composable
private fun GuideClock(now: Instant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(27.dp).background(Signal))
        Spacer(Modifier.width(9.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dateFormatter.format(now).uppercase(),
                color = Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = timeFormatter.format(now),
                color = Paper,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun ProgrammeArtwork(imageUri: String?, accent: Color, action: String) {
    val image by rememberRemoteImage(imageUri)
    Box(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF33256D)),
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Ink,
                            0.32f to Ink.copy(alpha = 0.72f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        } else {
            Box(Modifier.width(7.dp).fillMaxHeight().background(accent))
            Text(
                text = "TELLY\nGRID",
                color = Color(0xFF8D78D5),
                fontSize = 31.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            text = action,
            color = Ink,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 0.7.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Signal)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TimelineHeader(windowStart: Instant, now: Instant) {
    Row(Modifier.fillMaxWidth().height(25.dp), verticalAlignment = Alignment.Bottom) {
        Row(
            modifier = Modifier.width(stationWidth).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CHANNEL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(22.dp).height(2.dp).background(Signal))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            repeat(5) { index ->
                val time = windowStart.plus(Duration.ofMinutes(index * 30L))
                val x = if (index == 4) maxWidth - 31.dp else maxWidth * (index / 4f)
                Text(
                    text = timeFormatter.format(time),
                    color = if (index == 0) Paper else Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = x),
                )
            }
            if (!now.isBefore(windowStart) && now.isBefore(windowStart.plus(Duration.ofMinutes(windowMinutes)))) {
                val fraction = Duration.between(windowStart, now).toMinutes().toFloat() / windowMinutes.toFloat()
                Box(
                    Modifier
                        .offset(x = maxWidth * fraction)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Live),
                )
            }
        }
    }
}

/** Select a real tile in the new time window, retaining the channel where possible. */
private fun restoreGridSelection(
    channels: List<GuideChannel>,
    preferredChannelId: String?,
    windowStart: Instant,
    setSelected: (GuideChannel, Programme?) -> Unit,
    onSelectionChanged: (String?, String?) -> Unit,
) {
    val windowEnd = windowStart.plus(Duration.ofMinutes(windowMinutes))
    val ordered = channels.sortedBy { if (it.id == preferredChannelId) 0 else 1 }
    val channelWithProgramme = ordered.firstOrNull { channel ->
        channel.programmes.any { it.endsAt > windowStart && it.startsAt < windowEnd }
    }
    val channel = channelWithProgramme ?: ordered.firstOrNull() ?: return
    val programme = channel.programmes.firstOrNull { it.endsAt > windowStart && it.startsAt < windowEnd }
    setSelected(channel, programme)
    onSelectionChanged(channel.id, programme?.id)
}

@Composable
private fun ChannelTimelineRow(
    channel: GuideChannel,
    windowStart: Instant,
    windowEnd: Instant,
    now: Instant,
    rowHeight: Dp,
    selectedChannelId: String?,
    selectedProgrammeId: String?,
    gridFocus: FocusRequester,
    fallbackFocus: Boolean,
    onMoveVertical: (Int) -> Boolean,
    onMoveWindow: (Long) -> Unit,
    onFocused: (Programme) -> Unit,
    onClick: (Programme) -> Unit,
    onDetails: (Programme) -> Unit,
) {
    val visible = channel.programmes.filter { it.endsAt > windowStart && it.startsAt < windowEnd }
    var activeProgramme by remember(channel.id, windowStart, visible) { mutableStateOf(visible.firstOrNull()) }
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        ChannelIdentity(
            channel = channel,
            // Keep the channel rail focusable even when an EPG row is empty;
            // otherwise D-pad Down gets trapped above a "schedule unavailable"
            // placeholder and the user cannot reach the rest of the guide.
            enabled = true,
            focusRequester = gridFocus.takeIf { channel.id == selectedChannelId && selectedProgrammeId == null },
            onMoveEarlier = { onMoveWindow(-30) },
            onMoveVertical = onMoveVertical,
            onFocused = { activeProgramme?.let(onFocused) },
            onClick = { activeProgramme?.let(onDetails) },
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(InkRaised),
        ) {
            visible.forEachIndexed { index, programme ->
                val clippedStart = maxOf(programme.startsAt, windowStart)
                val clippedEnd = minOf(programme.endsAt, windowEnd)
                val startMinutes = Duration.between(windowStart, clippedStart).toMillis() / 60_000f
                val durationMinutes = Duration.between(clippedStart, clippedEnd).toMillis() / 60_000f
                val x = maxWidth * (startMinutes / windowMinutes.toFloat())
                val width = maxOf(66.dp, maxWidth * (durationMinutes / windowMinutes.toFloat()) - 3.dp)
                ProgrammeTile(
                    programme = programme,
                    isLive = !now.isBefore(programme.startsAt) && now.isBefore(programme.endsAt),
                    focusRequester = gridFocus.takeIf {
                        (channel.id == selectedChannelId && programme.id == selectedProgrammeId) ||
                            (selectedProgrammeId == null && fallbackFocus && index == 0)
                    },
                    movesLater = index == visible.lastIndex,
                    onMoveWindow = onMoveWindow,
                    onMoveVertical = onMoveVertical,
                    modifier = Modifier.offset(x = x).width(width).fillMaxHeight(),
                    onFocused = {
                        activeProgramme = programme
                        onFocused(programme)
                    },
                    onClick = { onClick(programme) },
                    onLongClick = { onDetails(programme) },
                )
            }
            if (visible.isEmpty()) {
                Text(
                    text = "SCHEDULE UNAVAILABLE",
                    color = Muted,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
            }
            NowMarker(windowStart = windowStart, windowEnd = windowEnd, now = now, maxWidth = maxWidth)
        }
    }
}

@Composable
private fun ChannelIdentity(
    channel: GuideChannel,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onMoveEarlier: () -> Unit,
    onMoveVertical: (Int) -> Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val logo by rememberRemoteImage(channel.logoUri, maxDimension = 320)
    var focused by remember(channel.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .width(stationWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
            .background(if (focused) FocusRed.copy(alpha = 0.8f) else InkQuiet)
            .border( if (focused) 2.dp else 0.dp, if (focused) FocusRed else Color.Transparent, RoundedCornerShape(2.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> { onMoveEarlier(); true }
                    androidx.compose.ui.input.key.Key.DirectionUp -> onMoveVertical(-1)
                    androidx.compose.ui.input.key.Key.DirectionDown -> onMoveVertical(1)
                    else -> false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.number.toString(),
            color = if (focused) Paper else Signal,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(35.dp),
        )
        Box(
            modifier = Modifier
                .width(62.dp)
                .fillMaxHeight()
                .padding(horizontal = 5.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            logo?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "${channel.name} logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } ?: Text(
                text = channel.name.take(8).uppercase(),
                color = Paper,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = channel.name,
                color = Paper,
                fontSize = if (channel.name.length > 18) 11.sp else 13.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (focused) "PLAYS HERE" else channel.providerLabel.uppercase(),
                color = if (focused) Signal else Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgrammeTile(
    programme: Programme,
    isLive: Boolean,
    focusRequester: FocusRequester?,
    movesLater: Boolean,
    onMoveWindow: (Long) -> Unit,
    onMoveVertical: (Int) -> Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember(programme.id) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (focused) FocusRed.copy(alpha = 0.86f) else InkRaised)
            .border( if (focused) 2.dp else 0.dp, if (focused) FocusRed else Color.Transparent, RoundedCornerShape(2.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> false
                    androidx.compose.ui.input.key.Key.DirectionRight -> movesLater.also { if (it) onMoveWindow(30) }
                    androidx.compose.ui.input.key.Key.DirectionUp -> onMoveVertical(-1)
                    androidx.compose.ui.input.key.Key.DirectionDown -> onMoveVertical(1)
                    else -> false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .focusable(),
    ) {
        Box(
            Modifier
                .width(if (focused) 6.dp else 3.dp)
                .fillMaxHeight()
                .background(if (isLive) Live else Signal),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 13.dp, end = 10.dp, top = 5.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = programme.title,
                color = Paper,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = if (focused) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isLive) "● LIVE  ${timeFormatter.format(programme.endsAt)}" else timeFormatter.format(programme.startsAt),
                color = when {
                    isLive -> Live
                    focused -> Signal
                    else -> Muted
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NowMarker(windowStart: Instant, windowEnd: Instant, now: Instant, maxWidth: Dp) {
    if (now.isBefore(windowStart) || !now.isBefore(windowEnd)) return
    val elapsed = Duration.between(windowStart, now).toMillis() / 60_000f
    val fraction = elapsed / windowMinutes.toFloat()
    Box(
        Modifier
            .offset(x = maxWidth * fraction)
            .width(2.dp)
            .fillMaxHeight()
            .background(Live),
    )
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Ink else Signal,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 0.9.sp,
        modifier = Modifier
            // Square-ish corners to match the guide grid's cells, not pills.
            .clip(RoundedCornerShape(2.dp))
            .background(if (focused) Signal else InkQuiet)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

private fun actionLabel(channel: GuideChannel?, programme: Programme?): String = when (val target = channel?.playback) {
    is PlaybackTarget.ProviderHandoff -> when {
        programme?.contentUri != null -> "OPEN EPISODE  ›"
        target.channelUri != null -> "WATCH LIVE  ›"
        target.terrestrialLcn != null -> "WATCH LIVE  ›"
        else -> "OPEN ${target.provider.displayName.uppercase()}  ›"
    }
    is PlaybackTarget.LicensedStream -> "WATCH  ›"
    is PlaybackTarget.Unavailable -> "UNAVAILABLE"
    null -> "SELECT A PROGRAMME"
}

private fun Instant.floorToHalfHour(): Instant {
    val local = atZone(zone)
    val minute = if (local.minute < 30) 0 else 30
    return ZonedDateTime.of(local.toLocalDate(), local.toLocalTime().withMinute(minute).withSecond(0).withNano(0), zone)
        .toInstant()
}

private val GuideFilter.compactLabel: String
    get() = when (this) {
        GuideFilter.ALL -> "ALL"
        GuideFilter.FAVOURITES -> "FAV"
        GuideFilter.FREEVIEW -> "TV"
        GuideFilter.FAST -> "PLUTO TV"
        GuideFilter.ENTERTAINMENT -> "ENT"
        GuideFilter.MOVIES -> "FILMS"
        GuideFilter.SPORT -> "SPORT"
        GuideFilter.KIDS -> "KIDS"
        GuideFilter.NEWS -> "NEWS"
        GuideFilter.DOCUMENTARY -> "DOCS"
    }

private val GuideSettings.headerFilterLabel: String
    get() {
        val category = filter.compactLabel.takeUnless { filter == GuideFilter.ALL }
        val provider = providerFilter?.let {
            when (it.name) {
                "BBC_IPLAYER" -> "BBC"
                "ITVX" -> "ITVX"
                "CHANNEL_4" -> "C4"
                "FIVE" -> "5"
                "U" -> "U"
                "NOW" -> "NOW"
                "DISCOVERY_PLUS" -> "D+"
                "HBO_MAX" -> "HBO"
                "PLUTO_TV" -> "PLUTO"
                else -> "APP"
            }
        }
        return listOfNotNull(category, provider).joinToString("/").ifBlank { "FILTER" }
    }
