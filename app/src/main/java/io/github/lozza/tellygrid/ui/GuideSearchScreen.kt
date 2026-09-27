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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideSearchResult
import io.github.lozza.tellygrid.data.Programme
import io.github.lozza.tellygrid.data.searchGuide
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** D-pad first local search for programmes and channel names. */
@Composable
fun GuideSearchScreen(
    channels: List<GuideChannel>,
    onResultSelected: (GuideChannel, Programme) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val results = remember(channels, query) { searchGuide(channels, query, Instant.now()) }

    LaunchedEffect(Unit) { inputFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize()
            .background(ScreenBackground).then(ScreenPadding),
    ) {
        Text("SEARCH", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 25.sp, letterSpacing = 1.sp)
        Text(
            "Search titles, episodes, channels and providers. Results stay on this TV.",
            color = Color(0xFFCDBFDB), fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        SearchInput(
            query = query,
            onQueryChanged = { query = it },
            focusRequester = inputFocus,
            onMoveToResults = {
                if (results.isNotEmpty()) runCatching { firstResultFocus.requestFocus() }.isSuccess else false
            },
        )
        Spacer(Modifier.height(18.dp))
        if (query.isBlank()) {
            Text("START TYPING TO SEARCH", color = Color(0xFFCDBFDB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else if (results.isEmpty()) {
            Text("NO UPCOMING RESULTS FOR \"${query.uppercase()}\"", color = Color(0xFFCDBFDB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                results.forEachIndexed { groupIndex, group ->
                    item(key = "section-${group.section.name}") {
                        Text(
                            group.section.label.uppercase(),
                            color = Color(0xFFCDBFDB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    items(group.results, key = { "${it.channel.id}-${it.programme.id}" }) { result ->
                        SearchResultRow(
                            result = result,
                            onClick = { onResultSelected(result.channel, result.programme) },
                            focusRequester = firstResultFocus.takeIf { groupIndex == 0 && result == group.results.first() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChanged: (String) -> Unit,
    focusRequester: FocusRequester,
    onMoveToResults: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(if (focused) Color(0xFF6D4C7A) else Color(0xFF35263F))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onMoveToResults()
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        decorationBox = { field ->
            if (query.isBlank()) Text("Search programmes or channels", color = Color(0xFFCDBFDB), fontSize = 20.sp)
            field()
        },
    )
}

@Composable
private fun SearchResultRow(
    result: GuideSearchResult,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var focused by remember(result.programme.id) { mutableStateOf(false) }
    val times = remember {
        DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault())
    }
    Row(
        Modifier.fillMaxWidth()
            .height(76.dp)
            .background(if (focused) Color(0xFF8D294D) else Color(0xFF35263F))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            result.channel.number.toString().padStart(3, '0'),
            color = Color(0xFFE0B5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(result.programme.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${result.channel.name}  ·  ${times.format(result.programme.startsAt)}",
                color = Color(0xFFCDBFDB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(if (focused) "SHOW IN GUIDE" else "SELECT", color = if (focused) Color.White else Color(0xFFCDBFDB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
