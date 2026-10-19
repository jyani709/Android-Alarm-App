package com.example.myalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AlarmItem(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var enabled: Boolean = true,
    val days: MutableSet<Int> = mutableSetOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
)

object AlarmScheduler {
    private const val PREFS = "MyAlarmPrefs"
    private const val KEY = "alarms"
    val koreaTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    // 24시간(0~23)을 "오전/오후 hh:mm" 문자열로 변환하는 도우미 함수
    fun formatAmPm(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "오전" else "오후"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.KOREA, "%s %02d:%02d", amPm, displayHour, minute)
    }

    fun load(context: Context): MutableList<AlarmItem> {
        val result = mutableListOf<AlarmItem>()
        val json = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        ).getString(KEY, null) ?: return result

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val days = mutableSetOf<Int>()

                if (item.has("days")) {
                    val savedDays = item.getJSONArray("days")
                    for (j in 0 until savedDays.length()) {
                        days.add(savedDays.getInt(j))
                    }
                } else {
                    days.addAll(
                        listOf(
                            Calendar.MONDAY, Calendar.TUESDAY,
                            Calendar.WEDNESDAY, Calendar.THURSDAY,
                            Calendar.FRIDAY, Calendar.SATURDAY,
                            Calendar.SUNDAY
                        )
                    )
                }

                result.add(
                    AlarmItem(
                        item.getInt("id"),
                        item.getInt("hour"),
                        item.getInt("minute"),
                        item.optBoolean("enabled", true),
                        days
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    fun save(context: Context, alarms: List<AlarmItem>) {
        val array = JSONArray()

        for (alarm in alarms) {
            val daysArray = JSONArray()
            alarm.days.sorted().forEach { daysArray.put(it) }

            array.put(
                JSONObject().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("enabled", alarm.enabled)
                    put("days", daysArray)
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun cancel(context: Context, alarm: AlarmItem) {
        val intent = Intent(context, AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun scheduleNext(context: Context, alarm: AlarmItem) {
        cancel(context, alarm)

        if (!alarm.enabled || alarm.days.isEmpty()) return

        val manager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !manager.canScheduleExactAlarms()
        ) return

        val now = Calendar.getInstance(koreaTimeZone)
        var nextTime: Calendar? = null

        for (offset in 0..7) {
            val candidate = Calendar.getInstance(koreaTimeZone).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (candidate.get(Calendar.DAY_OF_WEEK) in alarm.days &&
                candidate.timeInMillis > now.timeInMillis
            ) {
                nextTime = candidate
                break
            }
        }

        val time = nextTime ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            pendingIntent
        )
    }

    fun scheduleAll(context: Context) {
        load(context).forEach { scheduleNext(context, it) }
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var timeText: TextView
    private lateinit var alarmList: LinearLayout
    private val alarms = mutableListOf<AlarmItem>()
    private var nextId = 1

    private val dayNames = arrayOf(
        "월", "화", "수", "목", "금", "토", "일"
    )

    private val dayValues = intArrayOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    private val handler = android.os.Handler(
        android.os.Looper.getMainLooper()
    )

    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat(
                "yyyy년 MM월 dd일 a hh:mm:ss",
                Locale.KOREA
            ).apply {
                timeZone = AlarmScheduler.koreaTimeZone
            }
            timeText.text = sdf.format(Date())

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 기본 시간대를 한국 시간으로 고정
        TimeZone.setDefault(AlarmScheduler.koreaTimeZone)
        setContentView(R.layout.activity_main)

        timeText = findViewById(R.id.timeText)
        alarmList = findViewById(R.id.alarmList)

        findViewById<TextView>(R.id.titleText).text = "나의 알람"

        findViewById<Button>(R.id.addAlarmButton)
            .setOnClickListener { showTimePicker() }

        loadAlarms()
        handler.post(clockRunnable)
    }

    override fun onResume() {
        super.onResume()

        if (::alarmList.isInitialized) {
            AlarmScheduler.scheduleAll(this)
        }
    }

    private fun loadAlarms() {
        alarms.clear()
        alarms.addAll(AlarmScheduler.load(this))
        nextId = (alarms.maxOfOrNull { it.id } ?: 0) + 1
        renderAlarms()
    }

    private fun showTimePicker(alarm: AlarmItem? = null) {
        val now = Calendar.getInstance(AlarmScheduler.koreaTimeZone)

        // 마지막 파라미터를 false로 설정하여 AM/PM(오전/오후) 선택 모드로 표시
        TimePickerDialog(
            this,
            { _, hour, minute ->
                showDayPicker(hour, minute, alarm)
            },
            alarm?.hour ?: now.get(Calendar.HOUR_OF_DAY),
            alarm?.minute ?: now.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun showDayPicker(
        hour: Int,
        minute: Int,
        alarm: AlarmItem? = null
    ) {
        val selected = BooleanArray(7) { index ->
            if (alarm == null) {
                true
            } else {
                dayValues[index] in alarm.days
            }
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(
                if (alarm == null) "반복할 요일 선택"
                else "알람 수정 - 반복할 요일"
            )
            .setMultiChoiceItems(
                dayNames, selected
            ) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton("저장") { _, _ ->
                val days = mutableSetOf<Int>()

                for (i in selected.indices) {
                    if (selected[i]) days.add(dayValues[i])
                }

                if (days.isEmpty()) {
                    Toast.makeText(
                        this,
                        "반복할 요일을 하나 이상 선택해 주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (alarm == null) {
                    val newAlarm = AlarmItem(
                        id = nextId++,
                        hour = hour,
                        minute = minute,
                        days = days
                    )
                    alarms.add(newAlarm)
                } else {
                    alarm.hour = hour
                    alarm.minute = minute
                    alarm.days.clear()
                    alarm.days.addAll(days)
                }

                AlarmScheduler.save(this, alarms)
                renderAlarms()
                requestPermissionAndSchedule()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteAlarm(alarm: AlarmItem) {
        android.app.AlertDialog.Builder(this)
            .setTitle("알람 삭제")
            .setMessage(
                "${AlarmScheduler.formatAmPm(alarm.hour, alarm.minute)} 알람을 삭제할까요?"
            )
            .setPositiveButton("삭제") { _, _ ->
                AlarmScheduler.cancel(this, alarm)
                alarms.removeAll { it.id == alarm.id }
                AlarmScheduler.save(this, alarms)
                renderAlarms()

                Toast.makeText(
                    this,
                    "알람이 삭제되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun requestPermissionAndSchedule() {
        val manager = getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !manager.canScheduleExactAlarms()
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

        AlarmScheduler.scheduleAll(this)

        Toast.makeText(
            this,
            "알람이 저장되고 예약되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderAlarms() {
        alarmList.removeAllViews()

        for (alarm in alarms) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val timeLabel = TextView(this).apply {
                text = AlarmScheduler.formatAmPm(alarm.hour, alarm.minute)
                textSize = 24f
            }

            val selectedDays = dayNames.filterIndexed { index, _ ->
                dayValues[index] in alarm.days
            }.joinToString(" ")

            val description = TextView(this).apply {
                text = "알람 ${alarm.id} · $selectedDays"
                textSize = 14f
            }

            info.addView(timeLabel)
            info.addView(description)

            val toggle = Switch(this).apply {
                text = "사용"
                isChecked = alarm.enabled

                setOnCheckedChangeListener { _, checked ->
                    alarm.enabled = checked
                    AlarmScheduler.save(this@MainActivity, alarms)

                    if (checked) {
                        requestPermissionAndSchedule()
                    } else {
                        AlarmScheduler.cancel(
                            this@MainActivity, alarm
                        )
                    }
                }
            }

            val editButton = Button(this).apply {
                text = "수정"
                setOnClickListener {
                    showTimePicker(alarm)
                }
            }

            val deleteButton = Button(this).apply {
                text = "삭제"
                setOnClickListener {
                    deleteAlarm(alarm)
                }
            }

            row.addView(
                info,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            row.addView(toggle)
            row.addView(editButton)
            row.addView(deleteButton)

            alarmList.addView(row)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }
}