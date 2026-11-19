package com.boguan.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * LLM 接入设置（ADR-0003 / ADR-0011）：OpenAI 兼容协议，模型名可填任意供应商的模型。
 * 注意：key 仅保存在本机；当前用 SharedPreferences，后续须迁到 Android Keystore（ADR-0006）。
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
            Text("设置", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("返回") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LLM 接入", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（OpenAI 兼容）") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "提示：只保存到本机；音频永不外发。转写文本会在生成摘要与终稿校正时发往该端点。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("实时转写档位", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("更准（1920 ms 分块）", style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected = realtimeChunkMs == 1920, onClick = { realtimeChunkMs = 1920 })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("更低延迟（480 ms 分块）", style = MaterialTheme.typography.bodyMedium)
                    RadioButton(selected = realtimeChunkMs == 480, onClick = { realtimeChunkMs = 480 })
                }
                Text(
                    "实测同一段课堂录音：1920 ms 版错误率 10.6%，480 ms 版 15.9%（以终稿校正稿为参照）。" +
                        "代价是首字延迟约多 0.5 秒。改动后重启应用生效。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("终稿校正", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("用 LLM 校正识别错误", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = correctionEnabled, onCheckedChange = { correctionEnabled = it })
                }
                Text(
                    "把「实时稿 + 离线稿」交给 LLM，按上下文与术语表修掉同音字错误（如「女子是」→「余子式」）。" +
                        "只发送转写文本，不发送音频；每 90 分钟约 ¥0.03。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = glossary,
                    onValueChange = { glossary = it },
                    label = { Text("术语表（可选）") },
                    placeholder = { Text("行列式、余子式、代数余子式、初等变换…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "术语表用于校正与摘要；建议把本节课的专业名词、人名、缩写填进来。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(onClick = {
            onSave(baseUrl, apiKey, model, glossary, correctionEnabled, realtimeChunkMs)
            savedHint = "已保存，下一段摘要立即生效"
        }) { Text("保存") }
        if (savedHint.isNotEmpty()) {
            Text(savedHint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
