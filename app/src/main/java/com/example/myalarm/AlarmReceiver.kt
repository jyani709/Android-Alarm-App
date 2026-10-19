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
        val alarmId = intent.getIntExtra("alarmId", 0)
        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (intent.action == ACTION_STOP) {
            AlarmSoundManager.stop()
            manager.cancel(alarmId)
            return
        }

        // 알람을 울린 뒤 다음 선택 요일의 알람 예약
        val alarms = AlarmScheduler.load(context)
        val alarm = alarms.find { it.id == alarmId }

        if (alarm != null && alarm.enabled) {
            AlarmScheduler.scheduleNext(context, alarm)
        }

        val channelId = "my_alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "알람 시간 알림"
            }

            manager.createNotificationChannel(channel)
        }

        AlarmSoundManager.play(context)

        val stopIntent = Intent(
            context,
            AlarmReceiver::class.java
        ).apply {
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
