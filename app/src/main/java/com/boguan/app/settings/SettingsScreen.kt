package com.boguan.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LLM 接入设置（ADR-0003 / ADR-0011 / ADR-0012）。
 * 组件全部用 Miuix（ADR-0014）；key 仅保存在本机，发布前迁 Android Keystore（ADR-0006）。
 */
@Composable
fun SettingsScreen(
    initialBaseUrl: String,
    initialApiKey: String,
    initialModel: String,
    initialGlossary: String,
    initialCorrectionEnabled: Boolean,
    initialRealtimeChunkMs: Int,
    onSave: (baseUrl: String, apiKey: String, model: String, glossary: String, correctionEnabled: Boolean, realtimeChunkMs: Int) -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }
    var glossary by remember { mutableStateOf(initialGlossary) }
    var correctionEnabled by remember { mutableStateOf(initialCorrectionEnabled) }
    var realtimeChunkMs by remember { mutableStateOf(initialRealtimeChunkMs) }
    var savedHint by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("设置", style = MiuixTheme.textStyles.title2)
            TextButton(text = "返回", onClick = onBack)
        }

        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LLM 接入", style = MiuixTheme.textStyles.subtitle)
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = "Base URL（OpenAI 兼容）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    label = "模型名",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "提示：只保存到本机；音频永不外发。转写文本会在生成摘要与终稿校正时发往该端点。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("实时转写档位", style = MiuixTheme.textStyles.subtitle)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("更准（1920 ms 分块）", style = MiuixTheme.textStyles.body1)
                    RadioButton(selected = realtimeChunkMs == 1920, onClick = { realtimeChunkMs = 1920 })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("更低延迟（480 ms 分块）", style = MiuixTheme.textStyles.body1)
                    RadioButton(selected = realtimeChunkMs == 480, onClick = { realtimeChunkMs = 480 })
                }
                Text(
                    "实测同一段课堂录音：1920 ms 版错误率 10.6%，480 ms 版 15.9%（以终稿校正稿为参照）。" +
                        "代价是首字延迟约多 0.5 秒。改动后重启应用生效。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("终稿校正", style = MiuixTheme.textStyles.subtitle)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("用 LLM 校正识别错误", style = MiuixTheme.textStyles.body1)
                    Switch(checked = correctionEnabled, onCheckedChange = { correctionEnabled = it })
                }
                Text(
                    "把「实时稿 + 上下文 + 术语表」交给 LLM，按上下文修掉同音字错误（如「女子是」→「余子式」）。" +
                        "只发送转写文本，不发送音频；每 90 分钟约 ¥0.03。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                TextField(
                    value = glossary,
                    onValueChange = { glossary = it },
                    label = "术语表（可选，一行一个或用顿号分隔）",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Text(
                    "术语表用于校正与摘要；建议把本节课的专业名词、人名、缩写填进来。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }

        Button(onClick = {
            onSave(baseUrl, apiKey, model, glossary, correctionEnabled, realtimeChunkMs)
            savedHint = "已保存，下一段摘要立即生效"
        }) {
            Text("保存", style = MiuixTheme.textStyles.button)
        }
        if (savedHint.isNotEmpty()) {
            Text(savedHint, style = MiuixTheme.textStyles.footnote2)
        }
    }
}