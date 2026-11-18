package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.FilterCatalog
import app.lostfilm.desktop.data.ImageCache
import app.lostfilm.desktop.data.SeriesItem
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.FocusCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FilterCategory(val title: String, val dialogTitle: String) {
    CHANNEL("КАНАЛ", "Канал"),
    GENRE("ЖАНР", "Жанр"),
    YEAR("ГОД", "Года выпуска"),
    TYPE("ТИП", "Тип"),
}

private val PanelBackground = Color(0xFF0D0F12)
private val PanelText = Color(0xFFEDEFF2)
private val CardBackground = Color(0xFF1B1C21)

@Composable
fun FiltersScreenNew(model: AppModel) {
    var channel by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var genre by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var year by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var group by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var openCategory by remember { mutableStateOf<FilterCategory?>(null) }

    val focusManager = LocalFocusManager.current
    val panelFocus = remember { List(FilterCategory.entries.size) { FocusRequester() } }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        runCatching { panelFocus.first().requestFocus() }
    }

    LaunchedEffect(channel, genre, year, group) {
        model.applyFilter(
            genres = genre.toList(),
            years = year.toList(),
            channels = channel.toList(),
            groups = group.toList(),
        )
    }

    fun selectionOf(category: FilterCategory): Set<Int> = when (category) {
        FilterCategory.CHANNEL -> channel
        FilterCategory.GENRE -> genre
        FilterCategory.YEAR -> year
        FilterCategory.TYPE -> group
    }

    fun setSelection(category: FilterCategory, ids: Set<Int>) {
        when (category) {
            FilterCategory.CHANNEL -> channel = ids
            FilterCategory.GENRE -> genre = ids
            FilterCategory.YEAR -> year = ids
            FilterCategory.TYPE -> group = ids
        }
    }

    fun toggle(category: FilterCategory, id: Int) {
        val current = selectionOf(category)
        setSelection(category, if (id in current) current - id else current + id)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101216))) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .background(PanelBackground),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterCategory.entries.forEachIndexed { index, category ->
                        CategoryItem(
                            title = category.title,
                            activeCount = selectionOf(category).size,
                            focusRequester = panelFocus[index],
                            onOpen = { openCategory = category },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 24.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Найдено: ${model.filterResults.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFB9BFC8),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        channel = emptySet()
                        genre = emptySet()
                        year = emptySet()
                        group = emptySet()
                    }) {
                        Text("Сбросить")
                    }
                }

                if (model.filterLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                model.filterError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }

                if (model.filterResults.isEmpty() && !model.filterLoading) {
                    Text(
                        text = if (channel.isEmpty() && genre.isEmpty() && year.isEmpty() && group.isEmpty()) {
                            "Выберите категорию слева: КАНАЛ, ЖАНР, ГОД или ТИП."
                        } else {
                            "Ничего не найдено."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B9099),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(end = 28.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            gridItems(model.filterResults, key = { it.id }) { item ->
                                FilterCard(model, item) { model.open(item) }
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(gridState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(14.dp),
                            style = ScrollbarStyle(
                                minimalHeight = 48.dp,
                                thickness = 12.dp,
                                shape = RoundedCornerShape(6.dp),
                                hoverDurationMillis = 0,
                                unhoverColor = Color(0xFF9AA0A8),
                                hoverColor = Color(0xFFE4E8EE),
                            ),
                        )
                    }
                }
            }
        }

        openCategory?.let { category ->
            val values = valuesOf(category)
            FilterValuesDialog(
                title = category.dialogTitle,
                values = values,
                selected = selectionOf(category),
                onToggle = { id -> toggle(category, id) },
                onClear = { setSelection(category, emptySet()) },
                onClose = { openCategory = null },
            )
        }
    }
}

private fun valuesOf(category: FilterCategory): List<Pair<String, Int>> = when (category) {
    FilterCategory.CHANNEL -> FilterCatalog.channels
    FilterCategory.GENRE -> FilterCatalog.genres
    FilterCategory.YEAR -> FilterCatalog.years
    FilterCategory.TYPE -> FilterCatalog.groups
}

@Composable
private fun CategoryItem(
    title: String,
    activeCount: Int,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> { onOpen(); true }
                    Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                    Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                    Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                    Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                    else -> false
                }
            }
            .clickable(onClick = onOpen)
            .padding(horizontal = 26.dp, vertical = 12.dp)
            .scale(if (focused) 1.1f else 1f),
    ) {
        Text(
            text = if (activeCount > 0) "$title  ·$activeCount" else title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (activeCount > 0) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (activeCount > 0) TextDecoration.Underline else TextDecoration.None,
            color = if (focused) FocusCyan else PanelText,
        )
    }
}

@Composable
private fun FilterValuesDialog(
    title: String,
    values: List<Pair<String, Int>>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val firstRequester = remember { FocusRequester() }
    val doneRequester = remember { FocusRequester() }
    val swallow = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        runCatching { firstRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(540.dp)
                .heightIn(min = 360.dp, max = 650.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF191B20))
                .border(1.dp, Color(0xFF2C3038), RoundedCornerShape(14.dp))
                .clickable(interactionSource = swallow, indication = null) {}
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${selected.size} выбрано",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8B9099),
                )
            }
            Spacer(Modifier.size(10.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 22.dp),
                ) {
                    itemsIndexed(values) { index, entry ->
                        ValueRow(
                            label = entry.first,
                            checked = entry.second in selected,
                            firstRequester = if (index == 0) firstRequester else null,
                            onToggle = { onToggle(entry.second) },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(14.dp),
                    style = ScrollbarStyle(
                        minimalHeight = 40.dp,
                        thickness = 12.dp,
                        shape = RoundedCornerShape(6.dp),
                        hoverDurationMillis = 0,
                        unhoverColor = Color(0xFF9AA0A8),
                        hoverColor = Color(0xFFE4E8EE),
                    ),
                )
            }

            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClear) { Text("Очистить") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.focusRequester(doneRequester).focusable(),
                ) {
                    Text("Готово")
                }
            }
        }
    }
}

@Composable
private fun ValueRow(
    label: String,
    checked: Boolean,
    firstRequester: FocusRequester?,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (firstRequester != null) Modifier.focusRequester(firstRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionCenter -> { onToggle(); true }
                    Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                    Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF262A31) else Color.Transparent)
            .border(2.dp, if (focused) FocusCyan else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) FocusCyan else Color.Transparent)
                .border(2.dp, if (checked) FocusCyan else Color(0xFF5A6068), RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE8EAEE),
        )
    }
}

@Composable
private fun FilterCard(model: AppModel, item: SeriesItem, onClick: () -> Unit) {
    val url = remember(item.id) { model.seriesWide(item) }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(6.dp))
            .background(CardBackground)
            .border(
                3.dp,
                if (focused) FocusCyan else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                    Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                    Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                    Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                    Key.Enter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .clickable(onClick = onClick),
    ) {
        poster?.let { image ->
            Image(
                bitmap = image,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0xD6000000))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = item.cardTitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
