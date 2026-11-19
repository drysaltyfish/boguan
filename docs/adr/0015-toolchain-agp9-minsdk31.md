# ADR-0015：升级到 AGP 9 / compileSdk 37 / minSdk 31，并用上最新 UI 依赖

- **状态**：已接受；**取代 [ADR-0014](0014-miuix-ui-and-markdown.md) 中的版本锁定结论**（Miuix 0.8.8 / renderer 0.41.0 → 0.9.4 / 0.46.0）
- **日期**：2026-11-19
- **相关**：ADR-0014（UI 选型）、ADR-0001（架构）、ADR-0007（范围与验收）

## 背景

ADR-0014 时因为 compileSdk 只能到 36，被迫锁在 Miuix 0.8.8 + markdown-renderer 0.41.0。用户要求：

1. **最低支持 Android 12（API 31）**；
2. **依赖用最新**；
3. **全面适配**；
4. 下载优先走镜像源。

「用最新」的硬约束在编译期：Miuix 0.9.x 与 markdown-renderer 0.42+ 的 AAR 元数据都是 `minCompileSdk=37`（逐个读 `aar-metadata.properties` 确认），必须升级到 AGP 9.x。运行时下限反而很低：Miuix 0.9.4 `minSdkVersion=24`、markdown-renderer 0.46.0 `minSdkVersion=21`、Compose 1.12 `minSdkVersion=23`——所以 minSdk 31 是**产品选择**而非依赖强制。

## 决策

| 项 | 旧 | 新 |
|---|---|---|
| Gradle | 8.14.5 | **9.6.0** |
| AGP | 8.13.2 | **9.5.1** |
| compileSdk / targetSdk | 36 / 36 | **37 / 37** |
| minSdk | 29 | **31（Android 12）** |
| Kotlin | 2.4.21 | 2.4.21（不变） |
| Compose BOM | 2026.05.01 | **2026.09.00** |
| Miuix | 0.8.8 | **0.9.4**（最新稳定） |
| markdown-renderer | 0.41.0 | **0.46.0** |

**版本依据**

- AGP 9.5.1 的最低 Gradle 是 **9.6.0**（从 AGP jar 里 `VersionCheckPlugin` 常量读出，不是猜的）；
- 其余版本以 Maven 元数据的最新稳定版为准。

**镜像源（下载加速）**

- Gradle 发行版：`mirrors.cloud.tencent.com/gradle/gradle-9.6.0-bin.zip`（`gradle-wrapper.properties`）；
- Maven：沿用 `settings.gradle.kts` 里的阿里云 `google` / `public` 镜像（实测 AGP 9.5.1、BOM 2026.09.00、Miuix 0.9.4、renderer 0.46.0、Kotlin 2.4.21 全部 HTTP 200，无需回源）。

**全面适配（API 31 基线）**

- `minSdk` 提到 31 后，Android 10/11 的旧兼容分支不再需要；仍保留 `Build.VERSION.SDK_INT >= 33` 的 `POST_NOTIFICATIONS` 判断（31/32 没有该权限）。
- targetSdk 37 在 Android 16 真机上安装、录音、前台服务、通知、麦克风均验证通过。

**AGP 9 的内置 Kotlin**

AGP 9 起 Kotlin 支持内置：移除 `org.jetbrains.kotlin.android`（保留会直接报错），纯 Kotlin 模块继续用 `org.jetbrains.kotlin.jvm`，Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose` 保留。构建脚本里 `kotlin { jvmToolchain(17) }` 不变。

**代码改动**

- markdown-renderer 0.46 的 `DefaultMarkdownColors` 新增 `highlightText` / `highlightBackground`，`ReportMarkdown` 用 Miuix 的 `onBackground` / `secondaryContainer` 补齐；
- Miuix 0.9.4 保留了现有用到的组件签名（`Card/Text/Button/TextButton/TextField(value,onValueChange)/Switch/RadioButton/HorizontalDivider`），无需改调用点。

## 后果

**正面**

- 用上了最新 UI 依赖，Miuix 后续 0.9.x 组件（glass/nav/preference 等）可随时接入；
- 少了 Android 10/11 的兼容负担；
- 构建缓存与镜像都换了，下载速度稳定。

**负面 / 代价**

- **放弃 Android 10 / 11 设备**（minSdk 29 → 31），与 ADR-0007 的目标设备范围相比是收紧；若将来要支持旧机型，需回退到 Miuix 0.8.8 / compileSdk 36（ADR-0014 的配置）；
- AGP 9 + Gradle 9 是大版本升级，后续小版本可能有行为变更，CI 需固定版本；
- Android 12/13 用户虽可安装，但尚未在真机验证（只在 Android 16 验证过）。

## 复审触发条件

1. 需要覆盖 Android 10/11 用户时（回退方案见上）；
2. AGP/Gradle 出现阻断性 bug 或安全修复需再升级时，按「AGP 最低 Gradle」重新配对。

## 参考

- AGP 9.5 发布说明：https://developer.android.google.cn/build/releases/agp-9-1-0-release-notes
- AGP 内置 Kotlin：https://kotl.in/gradle/agp-built-in-kotlin
- 阿里云 Maven 镜像：https://maven.aliyun.com/mvn/guide
- 腾讯 Gradle 镜像：https://mirrors.cloud.tencent.com/gradle/
