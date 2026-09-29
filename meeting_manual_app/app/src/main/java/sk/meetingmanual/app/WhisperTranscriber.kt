package sk.meetingmanual.app

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Local Whisper transcription. Audio and model stay on the device. */
object WhisperTranscriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    interface Callback {
        fun onStatus(message: String)
        fun onSuccess(text: String)
        fun onError(error: String)
    }

    @JvmStatic
    fun transcribe(context: Context, audio: File, model: File, language: String, callback: Callback) {
        scope.launch {
            var loaded: WhisperModel? = null
            try {
                withContext(Dispatchers.Main) { callback.onStatus("Načítavam lokálny Whisper model…") }
                val whisperModel = Whisper.loadModel(context, model.absolutePath)
                loaded = whisperModel
                withContext(Dispatchers.Main) { callback.onStatus("Prepisujem nahrávku lokálne…") }
                val result = Whisper.transcribe(
                    whisperModel,
                    audio.absolutePath,
                    WhisperConfig(language = language)
                )
                val out = buildString {
                    result.segments.forEach { segment ->
                        append("[")
                        append(formatMs(segment.startMs))
                        append("] ")
                        append(segment.text.trim())
                        append("\n")
                    }
                    if (isBlank()) append(result.text.trim())
                }.trim()
                Whisper.releaseModel(whisperModel)
                withContext(Dispatchers.Main) { callback.onSuccess(out) }
            } catch (e: Exception) {
                try {
                    if (loaded != null) Whisper.releaseModel(loaded)
                } catch (_: Exception) { }
                withContext(Dispatchers.Main) { callback.onError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun formatMs(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
