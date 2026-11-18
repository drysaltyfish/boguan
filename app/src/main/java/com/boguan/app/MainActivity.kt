package com.boguan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
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
            onSave = { b, k, m -> vm.saveSettings(b, k, m) },
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
                    Text("博观约取", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                }
                Text(vm.engineDescription, style = MaterialTheme.typography.bodySmall)
                Text(vm.statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vm.sessionType == SessionType.LECTURE, { vm.setType(SessionType.LECTURE) }, { Text("课堂") })
                FilterChip(vm.sessionType == SessionType.MEETING, { vm.setType(SessionType.MEETING) }, { Text("会议") })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = hasPermission && vm.canRecord && vm.state == SessionState.IDLE,
                    onClick = { vm.start() },
                ) { Text("开始") }
                Button(enabled = vm.state == SessionState.RECORDING, onClick = { vm.pause() }) { Text("暂停") }
                Button(
                    enabled = vm.state == SessionState.RECORDING || vm.state == SessionState.PAUSED,
                    onClick = { vm.stop() },
                ) { Text("结束") }
                if (!hasPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("授权麦克风") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("实时", style = MaterialTheme.typography.labelMedium)
                    Text(vm.partialText.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("实时摘要", style = MaterialTheme.typography.labelMedium)
                    if (vm.summaryTitle.isNotBlank()) {
                        Text(vm.summaryTitle, style = MaterialTheme.typography.titleSmall)
                    }
                    if (vm.summaryBullets.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        vm.summaryBullets.forEach { b -> Text("· " + b, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (vm.summaryStatus.isNotBlank()) {
                        Text(vm.summaryStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            val flagged = vm.liveSegments.count { it.needsReview }
            Text(
                "本场段落（" + vm.liveSegments.size + "）· 点击右侧可回听" +
                    if (flagged > 0) " · 待确认 " + flagged + " 段" else "",
                style = MaterialTheme.typography.labelMedium,
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
                Text("历史记录（" + vm.history.size + "）", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { vm.refreshHistory() }) { Text("刷新") }
            }
        }
        items(vm.history, key = { "hist-" + it.id }) { s ->
            HistoryRow(s, onClick = { vm.openSessionWithReport(s) })
        }
    }
}

@Composable
private fun HistoryDetailScreen(vm: MainViewModel, session: Session) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("历史场次", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.closeSession() }) { Text("返回") }
            }
        }
        item { SessionMeta(session) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("结构化报告", style = MaterialTheme.typography.labelLarge)
                        TextButton(
                            enabled = !vm.reportStatus.startsWith("生成中"),
                            onClick = { vm.generateReport(session) },
                        ) { Text("生成报告") }
                    }
                    if (vm.reportStatus.isNotBlank()) {
                        Text(vm.reportStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    if (vm.reportText.isNotBlank()) {
                        Text(
                            vm.reportText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                    audioPath = session.audioPath,
                    onPlay = { seg -> vm.togglePlayback(seg, session.audioPath) },
                )
            }
        }
        item {
            Text(
                "段落（" + vm.openedSegments.size + "）" +
                    if (review.isNotEmpty()) " · 待确认 " + review.size + " 段" else "",
                style = MaterialTheme.typography.labelMedium,
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
 * 待确认清单（ADR-0004 的一部分）：实时稿与终稿二遍分歧最大的段落，
 * 两份文本并排给出，点「回听」直接听原音频核对。
 */
@Composable
private fun ReviewCard(
    segments: List<Segment>,
    total: Int,
    audioPath: String?,
    onPlay: (Segment) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("待确认（" + total + " 段）", style = MaterialTheme.typography.labelLarge)
            Text(
                "实时稿与终稿二遍不一致，建议回听核对" +
                    if (total > segments.size) "（此处先列 " + segments.size + " 段）" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            segments.forEach { seg ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "[" + fmtMs(seg.startMs) + " – " + fmtMs(seg.endMs) + "]",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text("实时：" + seg.text, style = MaterialTheme.typography.bodySmall)
                    if (seg.offlineText.isNotBlank()) {
                        Text("二遍：" + seg.offlineText, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onPlay(seg) }) { Text("回听") }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(session: Session, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (session.type == SessionType.LECTURE) "课堂" else "会议") + " · " + fmtTime(session.startedAtMs),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "时长 " + fmtDuration(session) + " · 音频 " + audioSizeLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onClick) { Text("查看") }
        }
    }
}

@Composable
private fun SessionMeta(session: Session) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (session.type == SessionType.LECTURE) "课堂" else "会议") + " · " + fmtTime(session.startedAtMs),
                style = MaterialTheme.typography.titleSmall,
            )
            Text("时长 " + fmtDuration(session) + " · 音频 " + audioSizeLabel(session), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SegmentRow(segment: Segment, playing: Boolean, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "[" + fmtMs(segment.startMs) + " – " + fmtMs(segment.endMs) + "]",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(segment.displayText, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onPlay) { Text(if (playing) "停止" else "回听") }
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
