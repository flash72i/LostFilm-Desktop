package app.lostfilm.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.ui.screens.PlayerIcon
import java.io.File

private const val KEY_GUARD_MS = 350L

@Composable
fun PlayerPickerDialog(model: AppModel) {
    val pending = model.pendingPlayback ?: return
    var dontAsk by remember(pending) { mutableStateOf(false) }
    var selected by remember(pending) { mutableIntStateOf(0) }
    val openedAt = remember(pending) { System.currentTimeMillis() }
    val players = model.settings.players.filter { File(it.path).isFile }
    val focusRequesters = remember(players.size) { List(players.size.coerceAtLeast(1)) { FocusRequester() } }

    LaunchedEffect(pending, players.size) {
        if (players.isNotEmpty()) {
            val preferred = players.indexOfFirst {
                it.path.equals(model.settings.defaultPlayer, true)
            }
            selected = if (preferred >= 0) preferred else 0
            runCatching { focusRequesters[selected].requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = { model.cancelPlayerDialog() },
        title = { Text("Выберите плеер") },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (System.currentTimeMillis() - openedAt < KEY_GUARD_MS) return@onPreviewKeyEvent true
            when (event.key) {
                Key.DirectionDown -> {
                    if (players.isNotEmpty()) {
                        selected = (selected + 1).coerceAtMost(players.lastIndex)
                        runCatching { focusRequesters[selected].requestFocus() }
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (players.isNotEmpty()) {
                        selected = (selected - 1).coerceAtLeast(0)
                        runCatching { focusRequesters[selected].requestFocus() }
                    }
                    true
                }
                Key.Enter, Key.DirectionCenter -> {
                    players.getOrNull(selected)?.let { model.choosePlayer(it.path, dontAsk) }
                    true
                }
                Key.Escape -> {
                    model.cancelPlayerDialog()
                    true
                }
                else -> false
            }
        },
        text = {
            Column {
                Text(
                    model.pendingPlayerTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "↑ ↓ — выбор, Enter — открыть",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                players.forEachIndexed { index, player ->
                    val isSel = index == selected
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSel || focused -> Color(0xFF3A2A55)
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                2.dp,
                                if (isSel || focused) FocusCyan else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .then(if (index < focusRequesters.size) Modifier.focusRequester(focusRequesters[index]) else Modifier)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) selected = index
                            }
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (System.currentTimeMillis() - openedAt < KEY_GUARD_MS) {
                                    return@onPreviewKeyEvent true
                                }
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    model.choosePlayer(player.path, dontAsk)
                                    true
                                } else {
                                    false
                                }
                            }
                            .clickable { model.choosePlayer(player.path, dontAsk) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerIcon(player)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(player.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                player.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.Spacebar)
                            ) {
                                dontAsk = !dontAsk
                                true
                            } else {
                                false
                            }
                        }
                        .clickable { dontAsk = !dontAsk }
                        .padding(top = 6.dp),
                ) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text("Больше не спрашивать")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.cancelPlayerDialog() }) {
                Text("Отмена")
            }
        },
    )
}
