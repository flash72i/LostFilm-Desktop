package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.CalendarEntry
import app.lostfilm.desktop.data.ImageCache
import app.lostfilm.desktop.ui.AppModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CalendarScreenNew(model: AppModel) {
    LaunchedEffect(Unit) { model.loadCalendar() }
    val groups = remember(model.calendarEntries) {
        model.calendarEntries.groupBy { it.date }.entries.toList()
    }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    val focusManager = LocalFocusManager.current
    val firstFocus = remember { FocusRequester() }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    val dateListState = rememberLazyListState()

    LaunchedEffect(groups) {
        if (selectedDate == null || groups.none { it.key == selectedDate }) {
            selectedDate = groups.firstOrNull()?.key
        }
        if (groups.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF15181D)),
        ) {
            LazyColumn(
                state = dateListState,
                modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(groups) { index, entry ->
                    val date = entry.key
                    val active = date == selectedDate
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .background(if (active) Color(0xFF2A2F38) else Color.Transparent)
                            .border(
                                3.dp,
                                if (active) app.lostfilm.desktop.ui.FocusCyan else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .onFocusChanged { if (it.isFocused) selectedDate = date }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.Enter, Key.DirectionCenter -> {
                                        selectedDate = date
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        focusManager.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        selectedDate = date
                                        focusManager.moveFocus(FocusDirection.Right)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clickable { selectedDate = date }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = if (date == today) "Сегодня" else date,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) Color.White else Color(0xFF8B9099),
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(dateListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .width(12.dp),
                style = ScrollbarStyle(
                    minimalHeight = 40.dp,
                    thickness = 10.dp,
                    shape = RoundedCornerShape(5.dp),
                    hoverDurationMillis = 0,
                    unhoverColor = Color(0xFF6E737C),
                    hoverColor = Color(0xFFE4E8EE),
                ),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (model.calendarLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            model.calendarError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = "Календарь · сегодня $today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedDate.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )

            val entries = groups.firstOrNull { it.key == selectedDate }?.value.orEmpty()
            if (entries.isEmpty() && !model.calendarLoading) {
                Text(
                    "В расписании пока нет премьер.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(entries, key = { it.seriesId + it.season + it.episode }) { entry ->
                        CalendarCard(model, entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    model: AppModel,
    entry: CalendarEntry,
    focusRequester: FocusRequester? = null,
) {
    val url = remember(entry.poster) { model.imageUrl(entry.poster) }
    var poster by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            poster = withContext(Dispatchers.IO) {
                ImageCache.load(url, model.baseUrl() + "/")
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .border(
                3.dp,
                if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    Key.DirectionRight -> {
                        focusManager.moveFocus(FocusDirection.Right)
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    Key.Enter -> {
                        model.openCalendarEntry(entry)
                        true
                    }
                    else -> false
                }
            }
            .clickable { model.openCalendarEntry(entry) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF232327)),
            ) {
                val image = poster
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = entry.titleRu,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    entry.titleRu,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "S%02dE%02d".format(entry.season, entry.episode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.episodeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
