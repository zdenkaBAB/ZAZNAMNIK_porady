package sk.meetingmanual.app

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Fully local Parakeet TDT v3 transcription. The model supports Slovak and other European languages. */
object SherpaTranscriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val CHUNK_SECONDS = 60

    interface Callback {
        fun onStatus(message: String)
        fun onSuccess(text: String)
        fun onError(error: String)
    }

    @JvmStatic
    fun transcribe(context: Context, audio: File, modelDir: File, language: String, callback: Callback) {
        scope.launch {
            try {
                withContext(Dispatchers.Main) { callback.onStatus("Načítavam Parakeet model…") }
                val modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                        joiner = File(modelDir, "joiner.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 4,
                    provider = "cpu",
                    modelType = "nemo_transducer"
                )
                val recognizer = OfflineRecognizer(
                    config = OfflineRecognizerConfig(
                        modelConfig = modelConfig,
                        decodingMethod = "greedy_search"
                    )
                )
                val result = transcribeWav(audio, recognizer, language, callback)
                recognizer.release()
                withContext(Dispatchers.Main) { callback.onSuccess(result.trim()) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private suspend fun transcribeWav(audio: File, recognizer: OfflineRecognizer, language: String, callback: Callback): String {
        val info = readWavInfo(audio)
        val bytesPerSecond = info.sampleRate * info.channels * 2
        val chunkFrames = info.sampleRate * CHUNK_SECONDS
        val chunkBytes = chunkFrames * info.channels * 2
        val totalChunks = maxOf(1, (info.dataSize + chunkBytes - 1L) / chunkBytes).toInt()
        val out = StringBuilder()
        RandomAccessFile(audio, "r").use { raf ->
            raf.seek(info.dataOffset)
            val buffer = ByteArray(chunkBytes)
            var remaining = info.dataSize
            var index = 0
            while (remaining > 0) {
                val want = minOf(buffer.size.toLong(), remaining).toInt()
                raf.readFully(buffer, 0, want)
                val frameCount = want / (info.channels * 2)
                val samples = FloatArray(frameCount)
                var pos = 0
                for (frame in 0 until frameCount) {
                    var sum = 0f
                    for (ch in 0 until info.channels) {
                        val lo = buffer[pos].toInt() and 0xff
                        val hi = buffer[pos + 1].toInt()
                        val sample = ((hi shl 8) or lo).toShort().toInt() / 32768f
                        sum += sample
                        pos += 2
                    }
                    samples[frame] = sum / info.channels
                }
                val stream = recognizer.createStream()
                if (language.isNotBlank()) stream.setOption("language", language)
                stream.acceptWaveform(info.sampleRate, samples)
                recognizer.decode(stream)
                val r = recognizer.getResult(stream)
                val chunkText = r.text.trim()
                if (chunkText.isNotEmpty()) {
                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append(chunkText)
                }
                stream.release()
                remaining -= want
                index++
                val pct = (index * 100 / totalChunks)
                withContext(Dispatchers.Main) { callback.onStatus("Prepisujem lokálne cez Parakeet: $pct %") }
            }
        }
        return out.toString()
    }

    private data class WavInfo(val dataOffset: Long, val dataSize: Long, val sampleRate: Int, val channels: Int)

    private fun readWavInfo(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(4); raf.readFully(riff)
            require(String(riff, Charsets.US_ASCII) == "RIFF") { "Nie je to WAV súbor." }
            raf.skipBytes(4)
            val wave = ByteArray(4); raf.readFully(wave)
            require(String(wave, Charsets.US_ASCII) == "WAVE") { "Neplatný WAV súbor." }
            var sampleRate = 0
            var channels = 0
            var dataOffset = -1L
            var dataSize = 0L
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4); raf.readFully(id)
                val size = readLeInt(raf).toLong() and 0xffffffffL
                val pos = raf.filePointer
                when (String(id, Charsets.US_ASCII)) {
                    "fmt " -> {
                        val format = readLeShort(raf)
                        channels = readLeShort(raf)
                        sampleRate = readLeInt(raf)
                        raf.skipBytes(6)
                        val bits = readLeShort(raf)
                        require(format == 1 && bits == 16) { "Podporovaný je iba PCM 16-bit WAV." }
                        raf.seek(pos + size)
                    }
                    "data" -> {
                        dataOffset = pos
                        dataSize = size
                        raf.seek(pos + size)
                    }
                    else -> raf.seek(pos + size)
                }
                if ((size and 1L) != 0L) raf.skipBytes(1)
                if (sampleRate > 0 && channels > 0 && dataOffset >= 0) break
            }
            require(sampleRate > 0 && channels > 0 && dataOffset >= 0 && dataSize > 0) { "WAV nemá platnú zvukovú stopu." }
            return WavInfo(dataOffset, dataSize, sampleRate, channels)
        }
    }

    private fun readLeInt(raf: RandomAccessFile): Int {
        val b0 = raf.read(); val b1 = raf.read(); val b2 = raf.read(); val b3 = raf.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readLeShort(raf: RandomAccessFile): Int {
        val b0 = raf.read(); val b1 = raf.read()
        return b0 or (b1 shl 8)
    }

}
