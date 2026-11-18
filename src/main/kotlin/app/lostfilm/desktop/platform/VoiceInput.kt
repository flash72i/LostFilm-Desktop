package app.lostfilm.desktop.platform

import java.util.concurrent.TimeUnit

object VoiceInput {
    data class Result(
        val text: String?,
        val error: String?,
    )

    private const val SCRIPT = """
Add-Type -AssemblyName System.Speech
${'$'}recognizers = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers()
if (${'$'}recognizers.Count -eq 0) {
    [Console]::Error.WriteLine('NO_RECOGNIZER')
    exit 2
}
${'$'}engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine
${'$'}engine.SetInputToDefaultAudioDevice()
${'$'}engine.InitialSilenceTimeout = [TimeSpan]::FromSeconds(5)
${'$'}engine.BabbleTimeout = [TimeSpan]::FromSeconds(3)
${'$'}engine.EndSilenceTimeout = [TimeSpan]::FromSeconds(1)
${'$'}engine.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
${'$'}result = ${'$'}engine.Recognize()
if (${'$'}result) { [Console]::Out.Write(${'$'}result.Text) } else { [Console]::Error.WriteLine('NO_SPEECH') }
${'$'}engine.Dispose()
"""

    fun listen(): Result {
        val process = runCatching {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                SCRIPT,
            ).redirectErrorStream(false).start()
        }.getOrElse {
            return Result(null, "Голосовой ввод недоступен: ${it.message}")
        }

        val finished = process.waitFor(18, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return Result(null, "Голосовой ввод не ответил")
        }

        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
        return when {
            process.exitValue() == 0 && text.isNotBlank() -> Result(text, null)
            error.contains("NO_RECOGNIZER") ->
                Result(null, "В Windows не установлен распознаватель речи")
            else -> Result(null, "Речь не распознана")
        }
    }
}
