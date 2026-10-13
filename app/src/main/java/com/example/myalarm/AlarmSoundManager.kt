package com.example.myalarm

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager

object AlarmSoundManager {

    private var ringtone: Ringtone? = null

    fun play(context: Context) {
        stop()

        val uri = RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_ALARM
        ) ?: RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_RINGTONE
        )

        ringtone = uri?.let {
            RingtoneManager.getRingtone(
                context.applicationContext,
                it
            )
        }

        ringtone?.play()
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}