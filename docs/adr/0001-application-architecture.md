# ADR-0001：应用架构 —— 共享核心 + 薄 UI 壳

- **状态**：已接受
- **日期**：2026-11-17
- **决策者**：项目所有者
- **相关**：ADR-0002（ASR）、ADR-0004（报告抽取）

## 背景

目标平台是 Android → Windows/Linux → 三端一致。核心逻辑（分句、摘要分层、报告抽取、存储、调用重放）与 UI 无关，却是最难写、最需要测试的部分。

环境事实：JDK 21 + Android SDK（platform-tools / platforms android-36·37 / build-tools 35·36 / emulator）齐备；Gradle wrapper 缓存在；**未装 NDK**；Rust 只装了 `x86_64-pc-windows-msvc` 一个 target。`sherpa-onnx` 官方为 Android 提供 **Kotlin API + AAR**。

## 决策

1. **语言与 UI**：Kotlin + Jetpack Compose。
2. **核心为纯 Kotlin module**，**不依赖 Android SDK**：
   `audio` / `asr` / `summarize` / `store` / `report`（外加 `core-model` 放领域类型）。
3. **UI 是薄壳**：只做采集调度、前台服务、通知、Compose 页面、权限。
4. **ASR 接入**：`sherpa-onnx` 的 Kotlin API；**官方没有 Maven 包，AAR 自托管**并锁定版本进仓库。
5. **两个关键接口**：`AsrEngine`（实时引擎 / 离线引擎）、`LlmProvider`（OpenAI 兼容端点）。二者都是可替换边界，不是插件系统。
6. **桌面二期**：用 Compose Multiplatform 复用核心与 UI；ASR 适配层可能需要第二份 binding。

## 后果

**正面**

- Android 首版零 FFI、零 NDK、零交叉编译，能最快跑通「能录 → 能转 → 能出报告」。
- 核心逻辑可单测、可迁移；UI 换掉不影响业务。

**负面 / 代价**

- 桌面二期若 `sherpa-onnx` 没有可用的 JVM binding，**ASR 适配层要重写一份**（C API 或 Rust）。核心与 UI 仍复用。
- AAR 自托管意味着升级需要手动搬包，必须锁版本并记录来源。

## 备选方案

| 方案 | 否决理由 |
|---|---|
| Rust 核心 + Kotlin 壳（UniFFI/JNI） | 需先装 NDK + Rust android target + 搭 FFI 桥；在产品价值尚未验证时是纯负债 |
| C++ 核心 + Kotlin 壳 | 直接吃 sherpa C API 可行，但开发效率最低，且团队只有一人 |
| Flutter / KMP 统一 UI | Android 的后台录音、前台服务、通知仍要写原生代码，等于多背一层框架约束 |
| 三平台各自独立实现 | 核心逻辑分叉是三个 bug 源，长期维护成本最高 |
