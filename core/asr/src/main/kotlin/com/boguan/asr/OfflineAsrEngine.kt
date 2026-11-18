package com.boguan.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.File
import java.util.Locale

/**
 * 离线（非流式）ASR，用于会后的「终稿二遍」（ADR-0002 / ADR-0008 / ADR-0010）。
 *
 * 支持两种模型目录（按目录名识别，模型文件需为 model.int8.onnx 或 model.onnx + tokens.txt）：
 *  - 名称含 paraformer：Paraformer 中文模型（v0.1 默认，真机实测更稳）
 *  - 名称含 sense-voice：SenseVoice-Small（保留作为备选/对照）
 *
 * 调用方按实时段的起止时间切好音频再送进来，因此这里不需要再做 VAD 分段。
 */
class OfflineAsrEngine private constructor(
    private val recognizer: OfflineRecognizer,
    val description: String,
) {

    /** 一次最多解码 20 秒，超过由调用方切分。 */
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

    private enum class Family { PARAFORMER, SENSE_VOICE }

    companion object {
        private const val TAG = "OfflineAsrEngine"
        private const val SAMPLE_RATE = 16_000
        const val MAX_WINDOW_SECONDS = 20

        fun create(modelsRoot: File): OfflineAsrEngine? {
            if (!modelsRoot.isDirectory) return null
            val candidates = modelsRoot.listFiles()
                ?.filter { it.isDirectory && modelFileOf(it) != null }
                .orEmpty()
            if (candidates.isEmpty()) {
                Log.w(TAG, "no offline model under " + modelsRoot.absolutePath)
                return null
            }
            // 优先 Paraformer（ADR-0010 的实测结论），其次 SenseVoice
            val preferred = candidates.firstOrNull { familyOf(it) == Family.PARAFORMER }
            val dir = preferred ?: candidates.first()
            val family = familyOf(dir)
            val modelFile = modelFileOf(dir) ?: return null

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
                when (family) {
                    Family.PARAFORMER -> paraformer = OfflineParaformerModelConfig().apply {
                        model = modelFile.absolutePath
                    }
                    Family.SENSE_VOICE -> senseVoice = OfflineSenseVoiceModelConfig().apply {
                        model = modelFile.absolutePath
                        language = "auto"
                        useInverseTextNormalization = false
                    }
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

        private fun familyOf(dir: File): Family =
            if (dir.name.lowercase(Locale.ROOT).contains("paraformer")) {
                Family.PARAFORMER
            } else {
                Family.SENSE_VOICE
            }

        private fun modelFileOf(dir: File): File? {
            val int8 = File(dir, "model.int8.onnx")
            val fp32 = File(dir, "model.onnx")
            val file = if (int8.exists()) int8 else if (fp32.exists()) fp32 else return null
            return if (File(dir, "tokens.txt").exists()) file else null
        }
    }
}
