# 技术尖峰结果（一次性，已 gitignore）

日期：2026-11-18 ｜ 桌面：Windows 11 / Intel Ultra X7 358H（16 核）/ JDK 21
真机：一加 12（PJD110）/ Android 16 (SDK 36) / 骁龙 8 Gen 3 (SM8650) / arm64-v8a

## 结论：端上实时转写可行，且余量充足

真机实测（200.71 s 中文课堂音频，2 线程，CPU int8）：

| 模型变体 | 加载 | 解码 | RTF | 首字延迟 | 实时余量 |
|---|---|---|---|---|---|
| x-asr-1920ms int8 2026-06-05 | 3.37 s | 9.29 s | **0.046** | 2.10 s | 21.6× |
| **x-asr-480ms int8 2026-06-05** | 3.43 s | 17.08 s | **0.085** | **1.60 s** | 11.8× |

对照桌面（同模型、2 线程）：480ms RTF 0.057 → 真机只慢约 1.5 倍，骁龙 8 Gen 3 表现好于预期。

验收门槛对照（ADR-0007）：

| 门槛 | 要求 | 实测 | 结果 |
|---|---|---|---|
| 实时延迟（首字） | < 2.0 s | 1.60 s（480ms 变体） | 通过 |
| 端上实时 RTF | 远小于 1.0 | 0.085 | 通过（余量 11.8×） |
| 输出可读性 | — | 自带标点 + 中英混说正确 | 通过 |
| 90 分钟稳定性/耗电 | 见 ADR | **未测** | 待补 |

## 遇到并解决的两个坑（必须写进 ADR）

1. **`modelType` 必须留空**。X-ASR 与 2025 模型的 metadata 都是 `model_type=zipformer2`；若在 config 里硬写 `modelType="zipformer"`，sherpa 会走 v1 zipformer 实现，报 `'attention_dims' does not exist in the metadata` 后 **`exit(255)` 静默杀进程**（Android 丢弃 stderr，logcat 里看不到任何异常，只能从 `dumpsys activity exit-info` 看到 `EXIT_SELF status=255`）。
2. **模型目录权限**。`adb shell mkdir` 创建的目录属主是 `shell`、权限 0770，App 作为 other 无法遍历（`File.listFiles()` 返回 null）。推送后必须 `adb shell chmod -R 0777 <dir>`。

## 模型选型（桌面 + 真机双向确认）

| 模型 | 桌面 RTF(2线程) | 真机 RTF | 首字 | 标点/混说 | 许可 |
|---|---|---|---|---|---|
| streaming-zipformer-zh-int8-2025-06-30（ADR 原选） | 0.084 | 未测（metadata 不兼容 2.x AAR） | 0.8 s | 无 | 未声明 |
| **x-asr-480ms int8 2026-06-05** | 0.057 | **0.085** | **1.60 s** | 有 | **Apache-2.0** |
| x-asr-1920ms int8 2026-06-05 | 0.028 | 0.046 | 2.10 s | 有 | Apache-2.0 |

建议：默认 480ms（首字 1.60 s，满足 <2 s 门槛）；省电/低端机档用 1920ms。两者都 Apache-2.0，**R1 关闭**。

## 工程产物

- APK：`.spike/asr-spike/app/build/outputs/apk/debug/app-debug.apk`（28.9 MB）
- 依赖：`sherpa-onnx-2.0.1.aar`（自托管于 `app/libs`）
- 真机基准：App 启动自动跑 `SPIKE_BENCH`（tag），读 `/sdcard/Android/data/com.boguan.spike/files/test.wav`
- 模型推送：`.spike/push-models.ps1`（**记得 chmod 0777**）

## 镜像与工具链

| 用途 | 方案 |
|---|---|
| Gradle 发行版 | mirrors.cloud.tencent.com/gradle |
| Maven | 阿里云 public/google/gradle-plugin（项目 settings + `~/.gradle/init.d/mirrors.init.gradle.kts`） |
| PyPI | 阿里云镜像 |
| GitHub Releases | **gh-proxy.com 前缀**（直连 0.03 MB/s → 代理 2–14 MB/s） |
| HuggingFace | HF_ENDPOINT=https://hf-mirror.com |
| Windows curl | 不可用（schannel 吊销检查离线）→ 用 Node fetch |
