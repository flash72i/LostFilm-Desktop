package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.EpisodeItem
import app.lostfilm.desktop.data.ImageCache
import app.lostfilm.desktop.data.SeriesItem
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.PageScrollbar
import app.lostfilm.desktop.ui.rowWheel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val FocusCyan = Color(0xFF4FC3F7)
private val InfoBg = Color(0xD6000000)
private val CardBg = Color(0xFF232327)

@Composable
fun HomeScreen(model: AppModel) {
    val backgroundPath = model.backgroundUrl
        ?: model.allSeries.firstOrNull()?.img
        ?: model.newEpisodes.firstOrNull()?.thumb
    var background by remember(backgroundPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(backgroundPath) {
        if (!backgroundPath.isNullOrBlank()) {
            background = withContext(Dispatchers.IO) {
                ImageCache.load(model.imageUrl(backgroundPath), model.baseUrl() + "/")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (model.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val hasData = model.series.isNotEmpty() || model.movies.isNotEmpty() ||
            model.newEpisodes.isNotEmpty()
        if (!hasData && !model.homeLoading) {
            EmptyState(model)
            return@Column
        }

        val firstFocus = remember { FocusRequester() }
        val pageState = rememberLazyListState()

        LaunchedEffect(hasData, model.homeFocusTick, model.newEpisodesRow.size) {
            if (!hasData) return@LaunchedEffect
            val reset = model.consumeHomeReset()
            if (reset) {
                runCatching { pageState.scrollToItem(0) }
                repeat(4) {
                    delay(150)
                    if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                }
            } else if (model.homeScrollIndex > 0) {
                runCatching { pageState.scrollToItem(model.homeScrollIndex) }
            }
        }

        DisposableEffect(Unit) {
            onDispose { model.saveHomeScroll(pageState.firstVisibleItemIndex) }
        }
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                state = pageState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                model.settings.homeRows.forEachIndexed { index, key ->
                    when (key) {
                        "episodes" -> {
                            item { SectionHeader("Новые эпизоды") }
                            item {
                                EpisodeRow(
                                    model,
                                    model.newEpisodesRow,
                                    if (index == 0) firstFocus else null,
                                    onEndReached = { model.loadMoreNewEpisodes() },
                                    loadingMore = model.newEpisodesLoadingMore,
                                )
                            }
                        }
                        "favorites" -> {
                            if (model.settings.logged) {
                                item { SectionHeader("Избранные сериалы") }
                                item { SeriesRow(model, model.favorites, useWide = false) }
                            }
                        }
                        "series" -> {
                            item { SectionHeader("Все сериалы") }
                            item { SeriesRow(model, model.allSeries, useWide = false) }
                        }
                        "newSeries" -> {
                            item { SectionHeader("Новые сериалы") }
                            item {
                                SeriesRow(
                                    model,
                                    model.newSeries,
                                    useWide = model.settings.chserieOnlyWidePoster,
                                )
                            }
                        }
                        "history" -> {
                            if (model.history.isNotEmpty()) {
                                item { SectionHeader("История просмотра") }
                                item { EpisodeRow(model, model.history) }
                            }
                        }
                        "movies" -> {
                            item { SectionHeader("Фильмы") }
                            item {
                                SeriesRow(
                                    model,
                                    model.moviesRow,
                                    useWide = false,
                                    movies = true,
                                )
                            }
                        }
                    }
                }
            }
            PageScrollbar(
                state = pageState,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFE8EAEE),
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeRow(
    model: AppModel,
    items: List<EpisodeItem>,
    focusRequester: FocusRequester? = null,
    onEndReached: (() -> Unit)? = null,
    loadingMore: Boolean = false,
) {
    if (items.isEmpty()) {
        if (model.homeLoading) SkeletonRow(model, count = 4, episode = true) else RowText("Нет данных")
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            last >= items.lastIndex - 1
        }
    }
    LaunchedEffect(nearEnd, items.size, loadingMore) {
        if (nearEnd && !loadingMore) onEndReached?.invoke()
    }
    LazyRow(
        state = listState,
        modifier = Modifier.rowWheel(listState, scope),
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items) { index, item ->
            EpisodeCard(model, item, if (index == 0) focusRequester else null)
        }
        if (loadingMore) {
            item { SkeletonCard(model, episode = true) }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesRow(
    model: AppModel,
    items: List<SeriesItem>,
    useWide: Boolean,
    movies: Boolean = false,
) {
    if (items.isEmpty()) {
        if (model.homeLoading) {
            SkeletonRow(model, count = 6, episode = false, wide = useWide)
        } else {
            RowText("Нет данных")
        }
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyRow(
        state = listState,
        modifier = Modifier.rowWheel(listState, scope),
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items) { _, item ->
            SeriesCard(model, item, useWide = useWide, movies = movies)
        }
    }
}

@Composable
private fun RowText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun SkeletonRow(
    model: AppModel,
    count: Int,
    episode: Boolean,
    wide: Boolean = false,
) {
    val scale = model.settings.cardScale
    val width = when {
        episode -> 395f
        wide -> 400f
        else -> 300f
    } * scale
    val height = 325f * scale
    LazyRow(
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) {
            item { SkeletonCard(model, episode = episode, wide = wide) }
        }
    }
}

@Composable
private fun SkeletonCard(model: AppModel, episode: Boolean, wide: Boolean = false) {
    val scale = model.settings.cardScale
    val width = when {
        episode -> 395f
        wide -> 400f
        else -> 300f
    } * scale
    val height = 325f * scale
    Box(
        Modifier
            .width(width.dp)
            .height(height.dp)
            .background(Color(0xFF2A2E36), RoundedCornerShape(12.dp)),
    )
}

/** Android EpisodePresenter: wide 16:9 card, title = episode name, content = "N Эпизод. Сериал". */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeCard(
    model: AppModel,
    item: EpisodeItem,
    focusRequester: FocusRequester? = null,
) {
    val scale = model.settings.cardScale
    val width = 395f * scale
    val height = 325f * scale
    val url = remember(item.thumb) {
        model.imageUrl(item.thumb.ifBlank { item.widePoster.ifBlank { item.seasonPoster } })
    }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                ImageCache.load(url, model.baseUrl() + "/")
            }
        }
    }

    val title: String
    val content: String
    when {
        item.isFullSeason -> {
            title = "Полный сезон"
            content = ""
        }
        item.hidden -> {
            title = if (item.isMovie) {
                "ДАТА РЕЛИЗА ${item.releaseDate}".uppercase()
            } else {
                "${item.episode} Эпизод. Дата релиза: ${item.releaseDate}".uppercase()
            }
            content = title
        }
        item.isMovie -> {
            title = item.seriesTitleRu
            content = "Фильм"
        }
        else -> {
            title = item.displayTitle
            content = "${item.episode} Эпизод. ${item.seriesTitleRu}"
        }
    }

    Card(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .border(
                3.dp,
                if (focused) FocusCyan else Color.Transparent,
                RoundedCornerShape(8.dp),
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
                    Key.Enter -> { model.openEpisode(item); true }
                    else -> false
                }
            }
            .clickable { model.openEpisode(item) }
            .onPointerEvent(PointerEventType.Enter) { model.setBackground(item.thumb) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1C21),
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height * 0.62f).dp)
                .background(CardBg),
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (model.isWatched(item)) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(InfoBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.isNotBlank() && content != title) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC5CAD1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Android SeriesPresenter / MoviesPresenter: portrait 300x325, title (year), rating / каналы / статус / жанры. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeriesCard(
    model: AppModel,
    item: SeriesItem,
    useWide: Boolean,
    movies: Boolean = false,
) {
    val scale = model.settings.cardScale
    val width = if (useWide) 400f * scale else 300f * scale
    val height = 325f * scale
    val imageUrl = remember(item.id, useWide) {
        if (useWide) model.seriesWide(item) else model.seriesPoster(item)
    }
    var poster by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotBlank()) {
            poster = withContext(Dispatchers.IO) {
                ImageCache.load(imageUrl, model.baseUrl() + "/")
            }
        }
    }

    val content = if (movies) {
        listOfNotNull(
            item.rating.takeIf { it > 0 }?.toString(),
            item.genres.takeIf { it.isNotBlank() },
        ).joinToString(" / ")
    } else {
        listOfNotNull(
            item.rating.takeIf { it > 0 }?.toString(),
            item.channels.ifBlank { item.channel }.takeIf { it.isNotBlank() },
            item.statusText.takeIf { it.isNotBlank() },
            item.genres.takeIf { it.isNotBlank() },
        ).joinToString(" / ")
    }

    Card(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                3.dp,
                if (focused) FocusCyan else Color.Transparent,
                RoundedCornerShape(8.dp),
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
                    Key.Enter -> { model.open(item); true }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Enter) { model.setBackground(item.img) }
            .clickable { model.open(item) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1C21),
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height * 0.68f).coerceAtLeast(72f).dp)
                .background(CardBg),
        ) {
            val bitmap = poster
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (model.isFavorite(item)) {
                FavBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (movies && item.watched) {
                WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(InfoBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                item.cardTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                content,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC5CAD1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC745EC5))
            .padding(4.dp),
    ) {
        Text("★", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC745EC5))
            .padding(4.dp),
    ) {
        Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun EmptyState(model: AppModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Каталог не загружен", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Проверьте зеркало в настройках и повторите загрузку.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { model.refreshCatalog() }) {
            Text("Обновить каталог")
        }
    }
}
