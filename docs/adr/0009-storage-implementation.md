# ADR-0009：存储实现 —— 首版用 SQLiteOpenHelper，Room 作为迁移目标

- **状态**：已接受
- **日期**：2026-11-18
- **关系**：细化 [ADR-0005](0005-local-data-and-recovery.md)（表结构不变，仅实现方式变更）

## 背景

ADR-0005 指定「SQLite（Room）+ 六张表」。在实际落工程骨架时遇到两个现实约束：

1. Room 需要 **KSP**（或 KAPT）代码生成。当前工具链是 Kotlin 2.4.21，而 KSP 的版本线（仓库里可见 2.3.x）与 Kotlin 版本的匹配关系未经验证。
2. 本项目在同一天已经因为 **AGP / Compose / 依赖版本不匹配**返工过两次（Compose BOM 2026.09 要求 AGP 9.1 + compileSdk 37，而机器上只有 android-36 平台）。骨架阶段再引入一个未验证的代码生成链路，风险与收益不成比例。

## 决策

首版存储实现使用 **`android.database.sqlite.SQLiteOpenHelper`**，表结构与 ADR-0005 **完全一致**（session / segment / micro_summary / qa_pair / report / llm_call）。

上层只依赖 `SessionRepository`，不直接接触 SQLite API。因此将来替换为 Room 时，UI 与业务层零改动。

## 迁移触发条件（任一满足即迁移）

1. 出现需要复杂查询/关联/全文检索的场景（当前都是单表按 session 查）。
2. v0.2 开始有真实用户数据，需要正式的 schema 迁移管理（`onUpgrade` 手写迁移开始变危险）。
3. KSP 与 Kotlin 版本的匹配经过验证，且构建稳定。

## 后果

**正面**

- 骨架阶段零代码生成依赖，构建链路简单、可预测。
- 表结构仍严格遵循 ADR-0005，迁移时不需要改数据模型。

**负面**

- 手写 SQL 无编译期校验，字段名拼错只能运行期发现。
- 迁移到 Room 时需重写 `SessionRepository` 的实现（接口不变）。

## 备选方案

| 方案 | 否决理由 |
|---|---|
| 直接上 Room + KSP | 版本匹配未验证，骨架阶段不值得赌 |
| SQLDelight（纯 Kotlin、可跨桌面） | 同样需要 Gradle 插件与代码生成；且增加一门前置 DSL，当前团队只有一人 |
| 直接嵌 `org.sqlite` JDBC | Android 上不可用 |
