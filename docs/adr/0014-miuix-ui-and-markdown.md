# ADR-0014：界面组件统一用 Miuix，报告用 Markdown 渲染

- **状态**：已接受；**版本锁定结论（Miuix 0.8.8 / markdown-renderer 0.41.0 / compileSdk 36）已被 [ADR-0015](0015-toolchain-agp9-minsdk31.md) 取代**（选型本身仍有效）
- **日期**：2026-11-19
- **相关**：ADR-0004（报告投影）、ADR-0011（终稿校正）

## 背景

v0.1 的界面一直是 Material 3 默认样式，报告则是把原始 Markdown 直接塞进 `Text`——标题层级、列表、引用全丢了，一段 2000 字的报告看起来像一坨文本。用户要求：

1. 上网找 Compose 的 Markdown 渲染组件；
2. 使用 **Miuix** 主题库；
3. **所有组件优先用 Miuix 库内的**。

## 选型过程（关键约束：compileSdk 36）

| 库 | 最新版 | 直接可用？ |
|---|---|---|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | 0.9.5-beta01 | ❌ 0.9.x 全部要求 `minCompileSdk=37`（需 AGP 9.x） |
| Miuix | **0.8.8** | ✅ `minCompileSdk=36`，依赖 Compose foundation 1.10.3、Kotlin 2.3.20 |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | 0.46.0 | ❌ 0.42+ 要求 `minCompileSdk=37` |
| multiplatform-markdown-renderer | **0.41.0** | ✅ `minCompileSdk=36`，Markdown 解析用 JetBrains markdown 0.7.3 |

实测验证方式：下载各版本 AAR，读 `META-INF/com/android/build/gradle/aar-metadata.properties` 的 `minCompileSdk`（只信文件，不信文档）。

**结论**：Miuix **0.8.8** + markdown-renderer **0.41.0**。两者都兼容当前的 Compose 1.11.2 / Kotlin 2.4.21（库用更旧的 Compose 编译，向上兼容），因此**不必升级 AGP/compileSdk**。

## 决策

1. **全部界面组件改用 Miuix**：`MiuixTheme(ThemeController(ColorSchemeMode.System))` 包住根组合；`Card / Text / Button / TextButton / TextField / Switch / RadioButton / HorizontalDivider / SmallTitle` 全部来自 `top.yukonga.miuix.kmp.basic`；移除 `material3` 依赖（代码中已无 import）。
2. **视觉规则**：根背景用 `colorScheme.surface`（灰），卡片默认白（`surfaceContainer`）——这是 Miuix/HyperOS 的「灰底白卡」层次；卡片内边距用 `insideMargin = 12.dp`。
3. **报告渲染**：`ReportMarkdown` 组合使用 mikepenz 渲染器，`MarkdownColors` / `MarkdownTypography` **全部由 Miuix 主题值构造**（不引入 m3 默认样式），因此报告与界面同一套字体层级与配色。
   - H1→title2、H2→title3、H3→subtitle、H4+→body1（报告自身已有页面标题，不让 Markdown H1 压过整页）。
4. **窗口内边距**：Miuix 只管主题不管 insets，根内容用 `WindowInsets.safeDrawing` 避开状态栏/挖孔。
5. **版本锁**：`miuix-android:0.8.8`、`multiplatform-markdown-renderer:0.41.0`。注释写在 `app/build.gradle.kts` 里说明「升到 0.9.x / 0.42+ 需要 compileSdk 37」。

## 后果

**正面**

- 界面是统一的 HyperOS 风格（灰底白卡、胶囊按钮、Miuix 字体层级），不再是 Material 默认样。
- 报告可读性大幅提升：标题层级、无序列表、引用块、加粗都按 Markdown 语义渲染，`待确认清单` 的引用提示也能正确显示。
- 不升级工具链，构建风险为零。

**负面 / 代价**

- 被锁在两个库的旧版本：Miuix 0.9.x 的新组件与渲染器 0.42+ 的新特性用不上，直到升级 compileSdk 37 / AGP 9.x。
- Miuix 0.8.8 的 `TextField` 用 `value/onValueChange` 旧签名（0.9.x 改为 `TextFieldState`），将来升级要改调用点。
- 增加约 2 MB 依赖体积（Miuix + 渲染器）。

## 复审触发条件

1. 决定升级 AGP 9.x / compileSdk 37 时，一并升到 Miuix 0.9.x + renderer 0.46。
2. 报告需要代码高亮/表格/图片时，评估渲染器的 `-code` / `-coil3` 模块（同样受 compileSdk 约束）。

## 参考

- Miuix 文档：https://github.com/compose-miuix-ui/miuix/tree/main/docs/zh_CN
- Miuix Maven：https://central.sonatype.com/artifact/top.yukonga.miuix.kmp/miuix-ui
- markdown-renderer：https://github.com/mikepenz/multiplatform-markdown-renderer
- AAR 元数据检查方法：`META-INF/com/android/build/gradle/aar-metadata.properties` → `minCompileSdk`
