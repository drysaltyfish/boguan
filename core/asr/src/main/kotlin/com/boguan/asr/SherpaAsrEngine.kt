package com.boguan.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * sherpa-onnx 流式 transducer 实现（ADR-0002 / ADR-0008）。
 *
 * 重要：`modelType` 必须留空 —— 交给 ONNX metadata 自动识别。
 * X-ASR 等模型的 metadata 是 `model_type=zipformer2`；若硬写 "zipformer"，
 * sherpa 会走 v1 实现并要求 `attention_dims`，随后 `exit(255)` 静默杀进程。
 */
internal class SherpaAsrEngine private constructor(
    private val recognizer: OnlineRecognizer,
    private val stream: OnlineStream,
    override val description: String,
) : AsrEngine {

    override val isLoaded: Boolean = true
    private var floatBuffer = FloatArray(4_096)

    override fun accept(samples: ShortArray, count: Int, sampleRate: Int): AsrResult {
        if (floatBuffer.size < count) floatBuffer = FloatArray(count)
        for (i in 0 until count) floatBuffer[i] = samples[i] / 32768f
        val chunk = if (floatBuffer.size == count) floatBuffer else floatBuffer.copyOf(count)

        stream.acceptWaveform(chunk, sampleRate)
        while (recognizer.isReady(stream)) recognizer.decode(stream)

        val text = recognizer.getResult(stream).text
        val isEndpoint = recognizer.isEndpoint(stream)
        if (isEndpoint) recognizer.reset(stream)
        return AsrResult(text, isEndpoint)
    }

    override fun release() {
        runCatching { stream.release() }
        runCatching { recognizer.release() }
    }

    companion object {
        private const val TAG = "SherpaAsrEngine"

        fun create(modelsRoot: File, preferChunkMs: Int): SherpaAsrEngine? {
            if (!modelsRoot.isDirectory) {
                Log.w(TAG, "models root missing: " + modelsRoot.absolutePath)
                return null
            }
            val dirs = modelsRoot.listFiles()
                ?.filter { it.isDirectory && isTransducerDir(it) }
                ?.sortedBy { it.name }
                .orEmpty()
            if (dirs.isEmpty()) {
                Log.w(TAG, "no usable model under " + modelsRoot.absolutePath)
                return null
            }
            val preferred = dirs.firstOrNull { it.name.contains(preferChunkMs.toString() + "ms") }
                ?: dirs.first()

            val config = OnlineRecognizerConfigBuilder.build(preferred)
            return try {
                val started = System.currentTimeMillis()
                val recognizer = OnlineRecognizer(null, config)
                val stream = recognizer.createStream("")
                val elapsed = System.currentTimeMillis() - started
                Log.i(TAG, "loaded in " + elapsed + "ms: " + preferred.name)
                SherpaAsrEngine(
                    recognizer = recognizer,
                    stream = stream,
                    description = preferred.name + " · 加载 " + elapsed + "ms",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "init failed", t)
                null
            }
        }

        private fun isTransducerDir(dir: File): Boolean =
            (File(dir, "encoder.int8.onnx").exists() || File(dir, "encoder.onnx").exists()) &&
                (File(dir, "decoder.onnx").exists() || File(dir, "decoder.int8.onnx").exists()) &&
                (File(dir, "joiner.int8.onnx").exists() || File(dir, "joiner.onnx").exists()) &&
                File(dir, "tokens.txt").exists()
    }
}

/** 把 sherpa 的 config 组装独立出来，方便单测与阅读。 */
internal object OnlineRecognizerConfigBuilder {

    fun build(modelDir: File, threads: Int = 2): com.k2fsa.sherpa.onnx.OnlineRecognizerConfig {
        val encoder = firstExisting(modelDir, "encoder.int8.onnx", "encoder.onnx")
        val decoder = firstExisting(modelDir, "decoder.onnx", "decoder.int8.onnx")
        val joiner = firstExisting(modelDir, "joiner.int8.onnx", "joiner.onnx")

        val config = com.k2fsa.sherpa.onnx.OnlineRecognizerConfig()
        config.featConfig = FeatureConfig().apply {
            sampleRate = 16_000
            featureDim = 80
            dither = 0f
        }
        config.modelConfig = OnlineModelConfig().apply {
            transducer = OnlineTransducerModelConfig().apply {
                this.encoder = File(modelDir, encoder).absolutePath
                this.decoder = File(modelDir, decoder).absolutePath
                this.joiner = File(modelDir, joiner).absolutePath
            }
            tokens = File(modelDir, "tokens.txt").absolutePath
            numThreads = threads
            provider = "cpu"
            debug = false
            // 必须留空：由 ONNX metadata 自动识别 zipformer / zipformer2
            modelType = ""
        }
        config.endpointConfig = EndpointConfig().apply {
            rule1 = EndpointRule(true, 2.4f, 0f)
            rule2 = EndpointRule(false, 1.2f, 0f)
            rule3 = EndpointRule(false, 0f, 20f)
        }
        config.enableEndpoint = true
        config.decodingMethod = "greedy_search"
        return config
    }

    private fun firstExisting(dir: File, vararg names: String): String =
        names.first { File(dir, it).exists() }
}
