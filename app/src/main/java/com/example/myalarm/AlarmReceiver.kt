package com.example.myalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val alarmId = intent.getIntExtra("alarmId", 0)

        // 알람 끄기 버튼을 눌렀을 경우
        if (intent.action == ACTION_STOP) {
            AlarmSoundManager.stop()
            manager.cancel(alarmId)
            return
        }

        val channelId = "my_alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "알람",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "알람 시간 알림"
            manager.createNotificationChannel(channel)
        }

        // 알람 소리 재생
        AlarmSoundManager.play(context)

        // 알람 끄기 버튼의 실행 동작
        val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_STOP
            putExtra("alarmId", alarmId)
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId + 10000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            channelId
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("알람 시간이 되었습니다!")
            .setContentText("알람을 끄려면 버튼을 누르세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "알람 끄기",
                stopPendingIntent
            )
            .build()

        manager.notify(alarmId, notification)
    }

    companion object {
        const val ACTION_STOP =
            "com.example.myalarm.ACTION_STOP"
    }
}