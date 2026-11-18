package com.boguan.asr

/** 一次喂音频后的识别结果。isEndpoint=true 表示该段已定稿。 */
data class AsrResult(val text: String, val isEndpoint: Boolean)

/**
 * ASR 引擎边界（ADR-0001）。
 * 目前只有一个实现（sherpa-onnx 流式 zipformer），桌面端二期可替换。
 */
interface AsrEngine {
    val isLoaded: Boolean

    /** 引擎描述，用于界面显示（模型名、线程数等）。 */
    val description: String

    fun accept(samples: ShortArray, count: Int, sampleRate: Int = 16_000): AsrResult

    fun release()

    companion object {
        /**
         * 创建引擎。modelsRoot 下应有一或多个模型目录（encoder/decoder/joiner/tokens）。
         * 找不到可用模型时返回 null。
         */
        fun create(modelsRoot: java.io.File, preferChunkMs: Int = 480): AsrEngine? =
            SherpaAsrEngine.create(modelsRoot, preferChunkMs)
    }
}
