package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.SeriesItem
import app.lostfilm.desktop.platform.openInBrowser
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.Screen

@Composable
fun CalendarScreen(model: AppModel) {
    val year = java.time.Year.now().value
    val upcoming = remember(model.series, model.movies) {
        (model.series + model.movies)
            .filter { (it.date.take(4).toIntOrNull() ?: 0) >= year }
            .sortedByDescending { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Календарь", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Премьеры $year года и позже",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (upcoming.isEmpty()) {
            Text(
                "В каталоге нет будущих премьер. Откройте календарь на сайте.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(upcoming) { item ->
                    CalendarRow(item) { model.open(item) }
                }
            }
        }

        OutlinedButton(onClick = { openInBrowser(model.baseUrl() + "/calendar/") }) {
            Text("Календарь на сайте")
        }
    }
}

@Composable
private fun CalendarRow(item: SeriesItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleSmall)
        Text(
            listOf(item.date, item.genres).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FiltersScreen(model: AppModel) {
    var kind by remember { mutableStateOf("Все") }
    var genre by remember { mutableStateOf("Все") }
    var genreMenu by remember { mutableStateOf(false) }

    val all = remember(model.series, model.movies) { model.series + model.movies }
    val genres = remember(all) {
        listOf("Все") + all
            .flatMap { it.genres.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filtered = remember(all, kind, genre) {
        all.filter { item ->
            val kindOk = when (kind) {
                "Сериалы" -> item.kind == SeriesItem.Kind.SERIES
                "Фильмы" -> item.kind == SeriesItem.Kind.MOVIE
                else -> true
            }
            val genreOk = genre == "Все" || item.genres.contains(genre, ignoreCase = true)
            kindOk && genreOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Фильтры", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "Сериалы", "Фильмы").forEach { value ->
                if (kind == value) {
                    Button(onClick = { kind = value }) { Text(value) }
                } else {
                    OutlinedButton(onClick = { kind = value }) { Text(value) }
                }
            }
        }

        Box {
            OutlinedButton(onClick = { genreMenu = true }) {
                Text("Жанр: $genre")
            }
            DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false }) {
                genres.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            genre = value
                            genreMenu = false
                        },
                    )
                }
            }
        }

        Text(
            "Найдено: ${filtered.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { model.open(item) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(item.date, item.genres).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(model: AppModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "LostFilm Desktop",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Неофициальный клиент LostFilm.TV для Windows",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Автор: Рэйф_72",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Каталог сериалов и фильмов, просмотр эпизодов через TorrServer и выбранный плеер. " +
                        "Приложение не хранит видео и не распространяет контент: для работы нужна ваша учётная запись LostFilm.TV.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Возможности", style = MaterialTheme.typography.titleLarge)
                Text("• Каталог сериалов, фильмов и новых эпизодов", style = MaterialTheme.typography.bodyLarge)
                Text("• Просмотрено / не просмотрено по вашему аккаунту", style = MaterialTheme.typography.bodyLarge)
                Text("• Выбор качества и плеера", style = MaterialTheme.typography.bodyLarge)
                Text("• Поиск, фильтры и календарь", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Данные и версия", style = MaterialTheme.typography.titleLarge)
                Text("Версия ${app.lostfilm.desktop.core.APP_VERSION}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Папка данных: ${model.dataDir().absolutePath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Настройки, обложки и TorrServer находятся рядом с программой.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { model.navigate(Screen.Home) }) {
                Text("На главную")
            }
            OutlinedButton(onClick = { openInBrowser(model.baseUrl()) }) {
                Text("Открыть LostFilm.TV")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

