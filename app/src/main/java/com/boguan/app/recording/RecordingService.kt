package com.boguan.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.boguan.app.MainActivity
import com.boguan.app.R

/**
 * 前台服务：让录音在 Activity 退到后台 / 熄屏后继续（ADR-0005）。
 *
 * 服务本身不持有录音逻辑 —— 它只负责两件事：
 * 1) 以 microphone 类型保持前台，避免被系统杀；
 * 2) 提供常驻通知（计时 + 停止按钮）。
 * 录音状态在 [RecordingController] 里，UI 与服务共享。
 */
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat()
                RecordingController.ensureInit(applicationContext)
                RecordingController.start()
            }
            ACTION_STOP -> {
                RecordingController.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startForegroundCompat()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 服务被系统回收时不要丢音频：停采集并封盘
        RecordingController.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopRecording = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("博观约取 · 正在记录")
            .setContentText("点按返回应用，或在此停止")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "停止", stopRecording)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "录音", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "记录进行中的常驻状态" }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.boguan.app.action.START_RECORDING"
        const val ACTION_STOP = "com.boguan.app.action.STOP_RECORDING"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
