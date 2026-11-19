package com.boguan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boguan.app.settings.SettingsScreen
import com.boguan.app.ui.ReportMarkdown
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Miuix：HyperOS 风格主题（ADR-0014）。跟随系统深色模式。
            val themeController = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = themeController) {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                    // 让内容避开状态栏 / 挖孔（Miuix 只管主题，不管窗口内边距）
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        RootScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun RootScreen(vm: MainViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    val opened = vm.openedSession
    when {
        showSettings -> SettingsScreen(
            initialBaseUrl = vm.baseUrl,
            initialApiKey = vm.apiKey,
            initialModel = vm.model,
            initialGlossary = vm.glossary,
            initialCorrectionEnabled = vm.correctionEnabled,
            initialRealtimeChunkMs = vm.realtimeChunkMs,
            onSave = { b, k, m, glossary, correction, chunk ->
                vm.saveSettings(b, k, m, glossary, correction, chunk)
            },
            onBack = { showSettings = false },
        )
        opened != null -> HistoryDetailScreen(vm, opened)
        else -> LiveScreen(vm, onOpenSettings = { showSettings = true })
    }
}

@Composable
private fun LiveScreen(vm: MainViewModel = viewModel(), onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(hasPermission) { if (hasPermission) vm.preload() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
        vm.refreshHistory()
    }
    // 结束录音后（回到 IDLE）刷新历史，否则刚录完那场仍显示「时长 —」
    LaunchedEffect(vm.state) {
        if (vm.state == SessionState.IDLE) vm.refreshHistory()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("博观约取", style = MiuixTheme.textStyles.title1)
                    TextButton(text = "设置", onClick = onOpenSettings)
                }
                Text(vm.engineDescription, style = MiuixTheme.textStyles.footnote2)
                Text(vm.statusMessage, style = MiuixTheme.textStyles.footnote2)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeButton("课堂", vm.sessionType == SessionType.LECTURE) { vm.setType(SessionType.LECTURE) }
                TypeButton("会议", vm.sessionType == SessionType.MEETING) { vm.setType(SessionType.MEETING) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.start() },
                    enabled = hasPermission && vm.canRecord && vm.state == SessionState.IDLE,
                ) { Text("开始", style = MiuixTheme.textStyles.button) }
                Button(
                    onClick = { vm.pause() },
                    enabled = vm.state == SessionState.RECORDING,
                ) { Text("暂停", style = MiuixTheme.textStyles.button) }
                Button(
                    onClick = { vm.stop() },
                    enabled = vm.state == SessionState.RECORDING || vm.state == SessionState.PAUSED,
                ) { Text("结束", style = MiuixTheme.textStyles.button) }
                if (!hasPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("授权麦克风", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("实时", style = MiuixTheme.textStyles.footnote2)
                    Text(vm.partialText.ifEmpty { "—" }, style = MiuixTheme.textStyles.main)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("实时摘要", style = MiuixTheme.textStyles.footnote2)
                    if (vm.summaryTitle.isNotBlank()) {
                        Text(vm.summaryTitle, style = MiuixTheme.textStyles.subtitle)
                    }
                    if (vm.summaryBullets.isEmpty()) {
                        Text("—", style = MiuixTheme.textStyles.body1)
                    } else {
                        vm.summaryBullets.forEach { b ->
                            Text("· " + b, style = MiuixTheme.textStyles.body1)
                        }
                    }
                    if (vm.summaryStatus.isNotBlank()) {
                        Text(vm.summaryStatus, style = MiuixTheme.textStyles.footnote2)
                    }
                    if (vm.correctedStatus.isNotBlank()) {
                        Text(vm.correctedStatus, style = MiuixTheme.textStyles.footnote2)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("阶段小结（每 10 分钟）", style = MiuixTheme.textStyles.footnote2)
                    if (vm.consolidatedTitle.isNotBlank()) {
                        Text(vm.consolidatedTitle, style = MiuixTheme.textStyles.subtitle)
                    }
                    if (vm.consolidatedLines.isEmpty()) {
                        Text("—", style = MiuixTheme.textStyles.body1)
                    } else {
                        vm.consolidatedLines.forEach { line ->
                            Text("· " + line, style = MiuixTheme.textStyles.body1)
                        }
                    }
                    if (vm.consolidatedStatus.isNotBlank()) {
                        Text(vm.consolidatedStatus, style = MiuixTheme.textStyles.footnote2)
                    }
                }
            }
        }
        item {
            val flagged = vm.liveSegments.count { it.needsReview }
            Text(
                "本场段落（" + vm.liveSegments.size + "）· 点击右侧可回听" +
                    if (flagged > 0) " · 待确认 " + flagged + " 段" else "",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        items(vm.liveSegments, key = { "live-" + it.id }) { seg ->
            SegmentRow(seg, vm.playingSegmentId == seg.id) {
                vm.togglePlayback(seg, controllerAudioPath(vm))
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("历史记录（" + vm.history.size + "）", style = MiuixTheme.textStyles.subtitle)
                TextButton(text = "刷新", onClick = { vm.refreshHistory() })
            }
        }
        items(vm.history, key = { "hist-" + it.id }) { s ->
            HistoryRow(s, onClick = { vm.openSessionWithReport(s) })
        }
    }
}

@Composable
private fun TypeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) { Text(label, style = MiuixTheme.textStyles.button) }
}

@Composable
private fun HistoryDetailScreen(vm: MainViewModel, session: Session) {
    // 终稿跑完后自动刷新段落展示（校正稿优先显示）
    LaunchedEffect(vm.finalPassStatus) {
        if (vm.finalPassStatus.startsWith("终稿完成") || vm.finalPassStatus.startsWith("终稿失败")) {
            vm.refreshOpenedSession()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("历史场次", style = MiuixTheme.textStyles.title2)
                TextButton(text = "返回", onClick = { vm.closeSession() })
            }
        }
        item { SessionMeta(session) }

        if (vm.openedConsolidations.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "阶段小结（" + vm.openedConsolidations.size + "）",
                            style = MiuixTheme.textStyles.subtitle,
                        )
                        vm.openedConsolidations.forEach { c ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "[" + fmtMs(c.startMs) + " – " + fmtMs(c.endMs) + "] " + c.title,
                                    style = MiuixTheme.textStyles.subtitle,
                                )
                                c.lines.forEach { line ->
                                    Text("· " + line, style = MiuixTheme.textStyles.footnote1)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("结构化报告", style = MiuixTheme.textStyles.subtitle)
                        Row {
                            TextButton(
                                text = "重跑终稿",
                                onClick = { vm.reprocessSession(session) },
                                enabled = !vm.finalPassStatus.contains("处理中") &&
                                    !vm.finalPassStatus.contains("校正中"),
                            )
                            TextButton(
                                text = "生成报告",
                                onClick = { vm.generateReport(session) },
                                enabled = !vm.reportStatus.startsWith("生成中"),
                            )
                        }
                    }
                    if (vm.finalPassStatus.isNotBlank()) {
                        Text(vm.finalPassStatus, style = MiuixTheme.textStyles.footnote2)
                    }
                    if (vm.reportStatus.isNotBlank()) {
                        Text(vm.reportStatus, style = MiuixTheme.textStyles.footnote2)
                    }
                    if (vm.reportText.isNotBlank()) {
                        // 报告是 Markdown，用 mikepenz 渲染器 + Miuix 主题排版（ADR-0014）
                        ReportMarkdown(vm.reportText)
                    }
                }
            }
        }
        val review = vm.openedSegments.filter { it.needsReview }
        if (review.isNotEmpty()) {
            item {
                ReviewCard(
                    segments = review.sortedBy { it.offlineSimilarity }.take(5),
                    total = review.size,
                    onPlay = { seg -> vm.togglePlayback(seg, session.audioPath) },
                )
            }
        }
        item {
            Text(
                "段落（" + vm.openedSegments.size + "）" +
                    if (review.isNotEmpty()) " · 待确认 " + review.size + " 段" else "",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        items(vm.openedSegments, key = { "hist-seg-" + it.id }) { seg ->
            SegmentRow(seg, vm.playingSegmentId == seg.id) {
                vm.togglePlayback(seg, session.audioPath)
            }
        }
    }
}

/**
 * 待确认清单（ADR-0004 的一部分）：实时稿与终稿（校正稿）分歧最大的段落，
 * 两份文本并排给出，点「回听」直接听原音频核对。
 */
@Composable
private fun ReviewCard(
    segments: List<Segment>,
    total: Int,
    onPlay: (Segment) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("待确认（" + total + " 段）", style = MiuixTheme.textStyles.subtitle)
            Text(
                "实时稿与终稿不一致，建议回听核对" +
                    if (total > segments.size) "（此处先列 " + segments.size + " 段）" else "",
                style = MiuixTheme.textStyles.footnote2,
            )
            segments.forEach { seg ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "[" + fmtMs(seg.startMs) + " – " + fmtMs(seg.endMs) + "]",
                        style = MiuixTheme.textStyles.footnote2,
                    )
                    Text("实时：" + seg.text, style = MiuixTheme.textStyles.footnote1)
                    if (seg.finalGuess.isNotBlank()) {
                        Text("终稿：" + seg.finalGuess, style = MiuixTheme.textStyles.footnote1)
                    }
                    TextButton(text = "回听", onClick = { onPlay(seg) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(session: Session, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (session.type == SessionType.LECTURE) "课堂" else "会议") + " · " + fmtTime(session.startedAtMs),
                    style = MiuixTheme.textStyles.footnote2,
                )
                Text(
                    "时长 " + fmtDuration(session) + " · 音频 " + audioSizeLabel(session),
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            TextButton(text = "查看", onClick = onClick)
        }
    }
}

@Composable
private fun SessionMeta(session: Session) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (session.type == SessionType.LECTURE) "课堂" else "会议") + " · " + fmtTime(session.startedAtMs),
                style = MiuixTheme.textStyles.subtitle,
            )
            Text(
                "时长 " + fmtDuration(session) + " · 音频 " + audioSizeLabel(session),
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

@Composable
private fun SegmentRow(segment: Segment, playing: Boolean, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(
                    "[" + fmtMs(segment.startMs) + " – " + fmtMs(segment.endMs) + "]",
                    style = MiuixTheme.textStyles.footnote2,
                )
                Text(segment.displayText, style = MiuixTheme.textStyles.body2)
            }
            TextButton(text = if (playing) "停止" else "回听", onClick = onPlay)
        }
    }
}

/** 当前实时会话的音频路径（历史会话由 Session.audioPath 提供）。 */
private fun controllerAudioPath(vm: MainViewModel): String? =
    vm.openedSession?.audioPath ?: com.boguan.app.recording.RecordingController.currentAudioPath

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

private fun fmtTime(wallMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(wallMs))

private fun fmtDuration(session: Session): String {
    val end = session.endedAtMs ?: return "—"
    val sec = (end - session.startedAtMs) / 1000
    return String.format(Locale.US, "%d分%02d秒", sec / 60, sec % 60)
}

private fun audioSizeLabel(session: Session): String {
    val path = session.audioPath ?: return "—"
    val f = java.io.File(path)
    if (!f.isFile) return "—"
    val kb = f.length() / 1024
    return if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else kb.toString() + " KB"
}