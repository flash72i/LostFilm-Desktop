package app.lostfilm.desktop.ui.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.EpisodeItem
import app.lostfilm.desktop.data.ImageCache
import app.lostfilm.desktop.data.SeriesItem
import app.lostfilm.desktop.platform.openInBrowser
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.Screen
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailsScreen(model: AppModel, item: SeriesItem) {
    LaunchedEffect(item.id, item.link) {
        model.loadSeriesDetails(item)
        if (item.kind == SeriesItem.Kind.MOVIE) model.loadMovieQualities(item)
    }

    val details = model.seriesDetails
    val episodes = details?.episodes.orEmpty()
    val posterPath = details?.poster?.ifBlank { item.img } ?: item.img
    var selectedSeason by remember(item.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(episodes) {
        if (selectedSeason == null || episodes.none { it.season == selectedSeason }) {
            selectedSeason = episodes.firstOrNull()?.season
        }
    }

    val seasons = episodes.map { it.season }.distinct().sortedDescending()
    val visibleEpisodes = episodes.filter { it.season == selectedSeason }
    val focusManager = LocalFocusManager.current
    val firstFocus = remember { FocusRequester() }
    var focusRequested by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(visibleEpisodes) {
        if (!focusRequested && visibleEpisodes.isNotEmpty()) {
            focusRequested = true
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        DetailsBackground(model, posterPath)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                }
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = { model.goBack() }) {
                Text("← Назад")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DetailsPoster(model, posterPath, item)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.titleOrig.isNotBlank() && item.titleOrig != item.title) {
                        Text(
                            item.titleOrig,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOf(
                            if (item.rating > 0) "Рейтинг ${item.rating}" else "",
                            item.statusText.takeIf { it.isNotBlank() },
                            item.channels.ifBlank { item.channel }.takeIf { it.isNotBlank() },
                            item.genres,
                        ).filterNotNull().filter { it.isNotBlank() }.joinToString(" / "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                when {
                                    item.kind == SeriesItem.Kind.MOVIE &&
                                        model.movieQualities.isNotEmpty() ->
                                        model.playMovieQuality(item, model.movieQualities.first())
                                    visibleEpisodes.isNotEmpty() ->
                                        model.openEpisode(visibleEpisodes.first())
                                    else -> model.watch(item)
                                }
                            },
                            enabled = !model.seriesDetailsLoading &&
                                !model.watchBusy &&
                                !model.playbackBusy,
                        ) {
                            Text(
                                if (model.watchBusy) "Загрузка..." else "Смотреть",
                            )
                        }
                        OutlinedButton(
                            onClick = { model.follow(item) },
                            enabled = !model.followBusy,
                        ) {
                            Text(
                                if (model.isFavorite(item)) "Убрать из избранного" else "В избранное",
                            )
                        }
                        if (item.kind == SeriesItem.Kind.MOVIE) {
                            OutlinedButton(onClick = { model.toggleMovieWatched(item) }) {
                                Text(
                                    if (model.isMovieWatched(item)) "Просмотрено" else "Не просмотрено",
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val opened = openInBrowser(model.baseUrl() + item.link)
                                if (!opened) model.navigate(Screen.About)
                            },
                        ) {
                            Text("Открыть на сайте")
                        }
                    }
                }
            }

            if (model.seriesDetailsLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            model.seriesDetailsError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (item.kind == SeriesItem.Kind.MOVIE) {
                if (model.movieQualitiesLoading || model.playbackBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (model.movieQualities.isNotEmpty()) {
                    Text("Качество фильма", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.movieQualities.forEach { quality ->
                            OutlinedButton(
                                onClick = { model.playMovieQuality(item, quality) },
                                enabled = !model.playbackBusy,
                            ) {
                                Text(quality.label)
                            }
                        }
                    }
                } else if (!model.movieQualitiesLoading) {
                    model.movieQualitiesError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                Text("Сезоны", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { season ->
                        app.lostfilm.desktop.ui.FocusChip(
                            text = "Сезон $season",
                            selected = season == selectedSeason,
                            onClick = { selectedSeason = season },
                        )
                    }
                }

                Text("Серии", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleEpisodes.forEachIndexed { index, episode ->
                        SeriesEpisodeCard(
                            model = model,
                            episode = episode,
                            focusRequester = if (index == 0) firstFocus else null,
                        )
                    }
                }
            } else if (!model.seriesDetailsLoading && item.kind == SeriesItem.Kind.SERIES) {
                Text(
                    "Серии не найдены на странице сериала.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (item.kind == SeriesItem.Kind.MOVIE) "О фильме" else "О сериале",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val descriptionText = details?.description?.takeIf { it.isNotBlank() }
                    if (descriptionText != null) {
                        Text(
                            descriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val facts = details?.facts.orEmpty()
                        if (item.kind == SeriesItem.Kind.MOVIE && facts.isNotEmpty()) {
                            facts.forEach { fact ->
                                Text(
                                    "• $fact",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                if (item.kind == SeriesItem.Kind.MOVIE) {
                                    "Описание фильма доступно на сайте."
                                } else {
                                    "Описание сериала доступно на сайте."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesEpisodeCard(
    model: AppModel,
    episode: EpisodeItem,
    focusRequester: FocusRequester? = null,
) {
    val imageUrl = remember(episode.thumb) { model.imageUrl(episode.thumb) }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .border(
                3.dp,
                if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                RoundedCornerShape(8.dp),
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
                        model.openEpisode(episode)
                        true
                    }
                    else -> false
                }
            }
            .clickable { model.openEpisode(episode) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF232327)),
            ) {
                val image = poster
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = episode.episodeTitleRu,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    String.format(
                        Locale.ROOT,
                        "S%02dE%02d · %s",
                        episode.season,
                        episode.episode,
                        episode.releaseDate,
                    ).trimEnd(' ', '·'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    episode.episodeTitleRu.ifBlank { episode.episodeTitleEn },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.rating > 0) {
                    Text(
                        "Рейтинг ${episode.rating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.watched) {
                Text(
                    "Просмотрено",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DetailsPoster(model: AppModel, path: String, item: SeriesItem) {
    val imageUrl = remember(path) { model.imageUrl(path) }
    var poster by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotBlank()) {
            poster = withContext(Dispatchers.IO) {
                ImageCache.load(imageUrl, model.baseUrl() + "/")
            }
        }
    }

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF232327)),
    ) {
        val image = poster
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun DetailsBackground(model: AppModel, path: String) {
    val imageUrl = remember(path) { model.imageUrl(path) }
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                ImageCache.load(imageUrl, model.baseUrl() + "/")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC0E0E10), Color(0xF20E0E10)),
                        ),
                    ),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
    }
}
