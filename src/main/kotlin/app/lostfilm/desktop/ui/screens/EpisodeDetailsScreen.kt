package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.EpisodeItem
import app.lostfilm.desktop.data.ImageCache
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.Screen
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EpisodeDetailsScreen(model: AppModel, item: EpisodeItem) {
    LaunchedEffect(item.id) { model.loadEpisodeInfo(item) }
    val info = model.episodeInfo
    val background = info?.photos?.firstOrNull()
        .orEmpty()
        .ifBlank { item.widePoster.ifBlank { item.seasonPoster } }
    val actionCount = 3
    val totalTargets = actionCount + model.qualities.size
    val targets = remember(totalTargets) { List(totalTargets) { FocusRequester() } }
    var targetIndex by remember(totalTargets) { mutableStateOf(0) }

    fun focusTarget(index: Int) {
        if (targets.isEmpty()) return
        val next = index.coerceIn(0, targets.lastIndex)
        targetIndex = next
        runCatching { targets[next].requestFocus() }
    }

    LaunchedEffect(totalTargets) {
        focusTarget(targetIndex)
    }

    Box(Modifier.fillMaxSize()) {
        EpisodeBackground(model, background)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> focusTarget(targetIndex - 1)
                        Key.DirectionRight -> focusTarget(targetIndex + 1)
                        Key.DirectionUp -> focusTarget(if (targetIndex >= actionCount) 0 else targetIndex)
                        Key.DirectionDown -> focusTarget(
                            if (targetIndex < actionCount && model.qualities.isNotEmpty()) {
                                actionCount
                            } else {
                                targetIndex
                            },
                        )
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { model.goBack() }) {
                Text("← Назад")
            }

            Text(
                item.episodeTitleRu.ifBlank { item.episodeTitleEn }.ifBlank { "Серия" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                item.seriesTitleRu,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE6E6EA),
            )

            val code = String.format(Locale.ROOT, "S%02dE%02d", item.season, item.episode)
            val meta = buildList {
                add(code)
                if (item.rating > 0) add(item.rating.toString())
                info?.runtime?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.releaseDate.isNotBlank()) {
                Text(
                    "Дата выхода: ${item.releaseDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusActionButton(
                    text = if (model.qualitiesLoading || model.playbackBusy) "Загрузка..." else "Смотреть",
                    onClick = { model.loadQualities(item) },
                    enabled = !model.qualitiesLoading && !model.playbackBusy,
                    primary = true,
                    focusRequester = targets[0],
                )
                FocusActionButton(
                    text = if (model.isWatched(item)) "Просмотрено" else "Не просмотрено",
                    onClick = { model.toggleWatched(item) },
                    focusRequester = targets[1],
                )
                FocusActionButton(
                    text = "Перейти к сериалу",
                    onClick = { model.goToSeries(item) },
                    focusRequester = targets[2],
                )
            }

            if (model.episodeInfoLoading || model.qualitiesLoading || model.playbackBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            model.episodeInfoError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            model.qualitiesError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (model.qualities.isNotEmpty()) {
                Text("Выберите качество", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.qualities.forEachIndexed { index, quality ->
                        FocusActionButton(
                            text = quality.label,
                            onClick = { model.playQuality(item, quality) },
                            enabled = !model.playbackBusy,
                            focusRequester = targets[actionCount + index],
                        )
                    }
                }
                Text(
                    model.playbackHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("О серии", style = MaterialTheme.typography.titleMedium)
                    val descriptionText = info?.description?.takeIf { it.isNotBlank() }
                    if (descriptionText != null) {
                        Text(
                            descriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val facts = info?.facts.orEmpty()
                        if (facts.isNotEmpty()) {
                            facts.forEach { fact ->
                                Text(
                                    "• $fact",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                "Описание серии доступно на сайте.",
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
private fun FocusActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusModifier = Modifier
        .onFocusChanged { focused = it.isFocused }
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .border(
            3.dp,
            if (focused) Color(0xFF4FC3F7) else Color.Transparent,
            RoundedCornerShape(50),
        )
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = focusModifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = focusModifier) {
            Text(text)
        }
    }
}

@Composable
private fun EpisodeBackground(model: AppModel, path: String) {
    val url = remember(path) { model.imageUrl(path) }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                ImageCache.load(url, model.baseUrl() + "/")
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
