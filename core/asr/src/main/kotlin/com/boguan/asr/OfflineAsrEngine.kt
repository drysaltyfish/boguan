package com.boguan.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.File

/**
 * 离线（非流式）ASR，用于会后的「终稿二遍」（ADR-0002 / ADR-0008）。
 * 模型：SenseVoice-Small int8。
 *
 * 调用方按实时段的起止时间切好音频再送进来，因此这里不需要再做 VAD 分段。
 */
class OfflineAsrEngine private constructor(
    private val recognizer: OfflineRecognizer,
    val description: String,
) {

    /** 一次最多解码 20 秒，超过由调用方切分（SenseVoice 的最佳输入窗口）。 */
    fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            runCatching { stream.release() }
        }
    }

    fun release() = runCatching { recognizer.release() }

    companion object {
        private const val TAG = "OfflineAsrEngine"
        private const val SAMPLE_RATE = 16_000
        const val MAX_WINDOW_SECONDS = 20

        fun create(modelsRoot: File): OfflineAsrEngine? {
            if (!modelsRoot.isDirectory) return null
            val dir = modelsRoot.listFiles()
                ?.filter { it.isDirectory }
                ?.firstOrNull {
                    (File(it, "model.int8.onnx").exists() || File(it, "model.onnx").exists()) &&
                        File(it, "tokens.txt").exists()
                }
            if (dir == null) {
                Log.w(TAG, "no offline model under " + modelsRoot.absolutePath)
                return null
            }
            val modelName = if (File(dir, "model.int8.onnx").exists()) "model.int8.onnx" else "model.onnx"
            val config = OfflineRecognizerConfig()
            config.featConfig = FeatureConfig().apply {
                sampleRate = SAMPLE_RATE
                featureDim = 80
                dither = 0f
            }
            config.modelConfig = OfflineModelConfig().apply {
                numThreads = 2
                provider = "cpu"
                debug = false
                modelType = ""
                tokens = File(dir, "tokens.txt").absolutePath
                senseVoice = OfflineSenseVoiceModelConfig().apply {
                    model = File(dir, modelName).absolutePath
                    language = "auto"
                    useInverseTextNormalization = false
                }
            }
            config.decodingMethod = "greedy_search"
            return try {
                val started = System.currentTimeMillis()
                val recognizer = OfflineRecognizer(null, config)
                val ms = System.currentTimeMillis() - started
                Log.i(TAG, "loaded " + dir.name + " in " + ms + "ms")
                OfflineAsrEngine(recognizer, dir.name + " · 加载 " + ms + "ms")
            } catch (t: Throwable) {
                Log.e(TAG, "init failed", t)
                null
            }
        }
    }
}
