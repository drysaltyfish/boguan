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
 * LLM 接入设置（ADR-0003）：OpenAI 兼容协议，模型名可填任意供应商的模型。
 * 注意：key 仅保存在本机；当前用 SharedPreferences，后续须迁到 Android Keystore（ADR-0006）。
 */
@Composable
fun SettingsScreen(
    initialBaseUrl: String,
    initialApiKey: String,
    initialModel: String,
    onSave: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }
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
                    "提示：只保存到本机；摘要时只把转写文本发往该端点，音频永不外发。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    onSave(baseUrl, apiKey, model)
                    savedHint = "已保存，下一段摘要立即生效"
                }) { Text("保存") }
                if (savedHint.isNotEmpty()) {
                    Text(savedHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
