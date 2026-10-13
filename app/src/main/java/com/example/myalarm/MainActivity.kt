package com.example.myalarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    var enabled: Boolean = true
)

class MainActivity : AppCompatActivity() {

    private lateinit var timeText: TextView
    private lateinit var alarmList: LinearLayout
    private val alarms = mutableListOf<AlarmItem>()
    private var nextId = 1
    private fun saveAlarms() {
        val jsonArray = JSONArray()

        for (alarm in alarms) {
            val jsonObject = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("enabled", alarm.enabled)
            }

            jsonArray.put(jsonObject)
        }

        getSharedPreferences("MyAlarmPrefs", MODE_PRIVATE)
            .edit()
            .putString("alarms", jsonArray.toString())
            .apply()
    }
    private fun loadAlarms() {
        val json = getSharedPreferences(
            "MyAlarmPrefs",
            MODE_PRIVATE
        ).getString("alarms", null) ?: return

        val jsonArray = JSONArray(json)

        alarms.clear()
        var maxId = 0

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            val alarm = AlarmItem(
                id = item.getInt("id"),
                hour = item.getInt("hour"),
                minute = item.getInt("minute"),
                enabled = item.getBoolean("enabled")
            )

            alarms.add(alarm)
            maxId = maxOf(maxId, alarm.id)
        }

        nextId = maxId + 1
        renderAlarms()
    }

    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            timeText.text = SimpleDateFormat(
                "yyyy년 MM월 dd일 HH:mm:ss",
                Locale.KOREA
            ).format(Date())

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timeText = findViewById(R.id.timeText)
        alarmList = findViewById(R.id.alarmList)

        findViewById<TextView>(R.id.titleText).text = "나의 알람"

        findViewById<Button>(R.id.addAlarmButton)
            .setOnClickListener {
                showTimePicker()
            }

        loadAlarms()

        handler.post(clockRunnable)
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val alarm = AlarmItem(
                    id = nextId++,
                    hour = hour,
                    minute = minute
                )

                alarms.add(alarm)
                saveAlarms()
                renderAlarms()
                scheduleAlarm(alarm)
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        ).show()
    }
    private fun scheduleAlarm(alarm: AlarmItem) {
        val alarmManager = getSystemService(
            ALARM_SERVICE
        ) as AlarmManager

        // Android 12 이상에서는 정확한 알람 권한을 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            Toast.makeText(
                this,
                "정확한 알람 권한을 허용해 주세요.",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 선택한 시각을 오늘 또는 내일의 시각으로 계산
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
            set(java.util.Calendar.MINUTE, alarm.minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Toast.makeText(
            this,
            "알람이 예약되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderAlarms() {
        alarmList.removeAllViews()

        for (alarm in alarms) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val timeLabel = TextView(this).apply {
                text = String.format(
                    Locale.KOREA,
                    "%02d:%02d",
                    alarm.hour,
                    alarm.minute
                )
                textSize = 28f
            }

            val description = TextView(this).apply {
                text = "알람 ${alarm.id}"
                textSize = 14f
            }

            info.addView(timeLabel)
            info.addView(description)

            val toggle = Switch(this).apply {
                text = "사용"
                isChecked = alarm.enabled

                setOnCheckedChangeListener { _, checked ->
                    alarm.enabled = checked
                    saveAlarms()
                }
            }
            row.addView(
                info,
                LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            row.addView(toggle)

            alarmList.addView(row)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }
}