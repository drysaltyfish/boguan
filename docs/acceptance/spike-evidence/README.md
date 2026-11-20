# 尖峰证据（从已删除的 .spike/ 归档）

2026-11-18 的技术尖峰（端上实时转写可行性、实时档位选型、终稿 LLM 校正、二遍稿文本策略）当时使用
`.spike/` 作为一次性工作目录（已 gitignore，含 2 GB 模型/音频/日志）。工作目录按“用完即弃”原则清理，
只保留 ADR 直接引用的测量脚本与数据，作为结论的可复核来源：

| 文件 | 用途 | 引用处 |
|---|---|---|
| `eval.py` / `eval.json` | 实时转写输出可读性评测（桌面 sherpa-onnx 2.0.1，17 段） | ADR-0010 |
| `fix_eval.py` / `fix-eval.json` | 终稿 LLM 校正效果实验（真实课堂录音 16 段） | ADR-0011 |
| `cer_cmp.py` / `cer-compare.json` | 实时档位 CER 对照（480ms 15.9% / 1920ms 10.6%） | ADR-0012、ADR-0013 |
| `SPIKE-RESULTS.md` | 尖峰总报告：真机 RTF、首字延迟、模型选型、两个坑（modelType 留空、目录 chmod 0777） | ADR-0008、ADR-0012 |

注意事项：

- 脚本里的模型/音频路径指向当时 `.spike/models`、`.spike/*.wav`，这些大文件已删除，重跑需重新下载模型（
  `tools/fetch_models.py` 或 sherpa-onnx 官方发布）并用设备重录音频。
- 结论与数据未改动，原样归档；新的验收数据请走 `tools/acceptance_report.py`（ADR-0016）与 `docs/acceptance/`。
