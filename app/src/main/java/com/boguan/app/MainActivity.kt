package com.boguan.app

import android.Manifest
import android.content.pm.PackageManager
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
import com.boguan.model.Segment
import com.boguan.model.SessionState
import com.boguan.model.SessionType
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SessionScreen()
                }
            }
        }
    }
}

@Composable
private fun SessionScreen(vm: MainViewModel = viewModel()) {
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

    LaunchedEffect(hasPermission) { if (hasPermission) vm.preload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("博观约取", style = MaterialTheme.typography.headlineSmall)
                Text(vm.engineDescription, style = MaterialTheme.typography.bodySmall)
                Text(vm.statusMessage + " · 音频 " + (vm.recordedBytes / 1024) + " KB",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.sessionType == SessionType.LECTURE,
                    onClick = { vm.setType(SessionType.LECTURE) },
                    label = { Text("课堂") },
                )
                FilterChip(
                    selected = vm.sessionType == SessionType.MEETING,
                    onClick = { vm.setType(SessionType.MEETING) },
                    label = { Text("会议") },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = hasPermission && vm.canRecord && vm.state == SessionState.IDLE,
                    onClick = { vm.start() },
                ) { Text("开始") }
                Button(
                    enabled = vm.state == SessionState.RECORDING,
                    onClick = { vm.pause() },
                ) { Text("暂停") }
                Button(
                    enabled = vm.state == SessionState.RECORDING || vm.state == SessionState.PAUSED,
                    onClick = { vm.stop() },
                ) { Text("结束") }
                if (!hasPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("授权麦克风")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("实时", style = MaterialTheme.typography.labelMedium)
                    Text(vm.partialText.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            HorizontalDivider()
            Text(
                "已定稿段落（" + vm.segments.size + "）· 点击右侧可回听",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        items(vm.segments, key = { it.id }) { segment ->
            SegmentRow(
                segment = segment,
                playing = vm.playingSegmentId == segment.id,
                onPlay = { vm.togglePlayback(segment) },
            )
        }
    }
}

@Composable
private fun SegmentRow(segment: Segment, playing: Boolean, onPlay: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "[" + fmtMs(segment.startMs) + " – " + fmtMs(segment.endMs) + "]",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(segment.text, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onPlay) { Text(if (playing) "停止" else "回听") }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
}
