package com.lonelytrack

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lonelytrack.databinding.ActivitySettingsBinding
import com.lonelytrack.notification.ReminderWorker
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("lonelytrack_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val soundEnabled = prefs.getBoolean("notification_sound", true)
        val autoAdjust = prefs.getBoolean("auto_adjust_difficulty", true)
        val quizEnabled = prefs.getBoolean("quiz_after_lesson", true)
        val hour = prefs.getInt("reminder_hour", 9)
        val minute = prefs.getInt("reminder_minute", 0)

        binding.switchNotifications.isChecked = notificationsEnabled
        binding.switchSound.isChecked = soundEnabled
        binding.switchAutoAdjust.isChecked = autoAdjust
        binding.switchQuiz.isChecked = quizEnabled

        updateTimeDisplay(hour, minute)
        updateTimePickerEnabled(notificationsEnabled)
    }

    private fun setupListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            updateTimePickerEnabled(isChecked)

            if (isChecked) {
                ReminderWorker.schedule(this)
            } else {
                ReminderWorker.cancel(this)
            }
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notification_sound", isChecked).apply()
        }

        binding.switchAutoAdjust.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_adjust_difficulty", isChecked).apply()
        }

        binding.switchQuiz.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("quiz_after_lesson", isChecked).apply()
        }

        binding.layoutReminderTime.setOnClickListener {
            if (!binding.switchNotifications.isChecked) return@setOnClickListener

            val curHour = prefs.getInt("reminder_hour", 9)
            val curMin = prefs.getInt("reminder_minute", 0)

            TimePickerDialog(this, { _, hour, minute ->
                prefs.edit()
                    .putInt("reminder_hour", hour)
                    .putInt("reminder_minute", minute)
                    .apply()
                updateTimeDisplay(hour, minute)
                ReminderWorker.schedule(this)
            }, curHour, curMin, false).show()
        }
    }

    private fun updateTimeDisplay(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        binding.tvReminderTime.text = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
    }

    private fun updateTimePickerEnabled(notificationsEnabled: Boolean) {
        binding.layoutReminderTime.alpha = if (notificationsEnabled) 1.0f else 0.4f
        binding.switchSound.isEnabled = notificationsEnabled
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
