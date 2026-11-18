package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.Screen


@Composable
fun LoginScreen(model: AppModel) {
    var email by remember { mutableStateOf(model.savedCredentials.email) }
    var password by remember { mutableStateOf(model.savedCredentials.password) }
    var captcha by remember { mutableStateOf("") }
    var remember by remember {
        mutableStateOf(model.savedCredentials.password.isNotBlank())
    }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        model.loadCaptcha()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { model.navigate(Screen.Home) }) {
            Text("← Назад")
        }

        Text("Вход в аккаунт", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Зеркало", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            model.settings.mirrors.forEach { mirror ->
                val selected = mirror.equals(model.settings.host, ignoreCase = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            if (!selected) model.chooseMirrorOnLogin(mirror)
                        },
                    )
                    Text(
                        mirror,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    when {
                        model.isMirrorChecking(mirror) -> Text(
                            "  проверяю…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        model.mirrorStatusOf(mirror) == true -> Text(
                            "  доступно",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF66BB6A),
                        )
                        model.mirrorStatusOf(mirror) == false -> Text(
                            "  недоступно",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.navigate(Screen.Settings) }) {
                Text("Все настройки")
            }
            OutlinedButton(onClick = { model.checkMirror(model.settings.host) }) {
                Text("Проверить зеркало")
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (showPassword) {
                            "Скрыть пароль"
                        } else {
                            "Показать пароль"
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = remember,
                onCheckedChange = { remember = it },
            )
            Text("Сохранить пароль")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            CaptchaImage(model.captchaBytes)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { model.loadCaptcha() }) {
                Text("Обновить капчу")
            }
        }

        OutlinedTextField(
            value = captcha,
            onValueChange = { captcha = it },
            label = { Text("Код с картинки") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        model.loginError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { model.login(email, password, captcha, remember) },
                enabled = !model.loginBusy,
            ) {
                Text(if (model.loginBusy) "Вход..." else "Войти")
            }
            OutlinedButton(onClick = { model.saveCredentials(email, password) }) {
                Text("Запомнить")
            }
            if (model.savedCredentials.password.isNotBlank()) {
                TextButton(onClick = { model.clearCredentials() }) {
                    Text("Удалить сохранённое")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Пароль хранится в открытом виде в файле credentials.properties рядом с приложением.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Сессия хранится в cookies.tsv и переживает перезапуск.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CaptchaImage(bytes: ByteArray?) {
    if (bytes == null) {
        Text(
            "Капча загружается...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bitmap = remember(bytes) {
        runCatching {
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) {
        Text("Не удалось показать капчу", style = MaterialTheme.typography.bodySmall)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Капча",
            modifier = Modifier.height(64.dp),
        )
    }
}
