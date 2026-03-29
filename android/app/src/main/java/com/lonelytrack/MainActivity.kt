package com.lonelytrack

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.lonelytrack.adapter.ScheduleAdapter
import com.lonelytrack.databinding.ActivityMainBinding
import com.lonelytrack.model.DailyTask
import com.lonelytrack.notification.ReminderWorker
import com.lonelytrack.viewmodel.LearningViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LearningViewModel by viewModels()
    private lateinit var auth: FirebaseAuth

    private val userId: String
        get() = auth.currentUser?.uid ?: ""

    private var currentPlanId: String? = null
    private var todayTask: DailyTask? = null

    private val prefs by lazy {
        getSharedPreferences("lonelytrack_prefs", MODE_PRIVATE)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we proceed either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Enable edge-to-edge display
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Skill level dropdown ────────────────────────────────────────
        val skillLevels = arrayOf("Beginner", "Intermediate", "Pro")
        val skillAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, skillLevels)
        binding.etSkillLevel.setAdapter(skillAdapter)
        binding.etSkillLevel.setText("Beginner", false)

        // ── Hamburger menu ──────────────────────────────────────────────
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set user info in nav header
        val headerView = binding.navView.getHeaderView(0)
        val tvUserInfo = headerView.findViewById<android.widget.TextView>(R.id.tvUserInfo)
        val user = auth.currentUser
        tvUserInfo.text = when {
            user == null -> ""
            user.isAnonymous -> "Signed in as Guest"
            user.displayName?.isNotEmpty() == true -> user.displayName
            else -> user.email ?: ""
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already home — just close drawer
                }
                R.id.nav_new_plan -> {
                    // Show form again
                    binding.formContainer.visibility = View.VISIBLE
                    binding.planSummary.visibility = View.GONE
                    currentPlanId = null
                    prefs.edit().remove("last_plan_id").apply()
                    viewModel.clearPlan()
                }
                R.id.nav_progress -> {
                    // Scroll to plan summary if visible
                    if (binding.planSummary.visibility == View.VISIBLE) {
                        binding.planSummary.requestFocus()
                    } else {
                        Toast.makeText(this, "Generate a plan first", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    intent.putExtra("user_id", userId)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.nav_leaderboard -> {
                    startActivity(Intent(this, LeaderboardActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val adapter = ScheduleAdapter(
            onComplete = { task ->
                currentPlanId?.let { planId ->
                    viewModel.updateDayStatus(userId, planId, task.day, "completed")
                }
            },
            onMiss = { task ->
                currentPlanId?.let { planId ->
                    viewModel.updateDayStatus(userId, planId, task.day, "missed")
                }
            },
            onItemClick = { task ->
                val skillLevel = binding.etSkillLevel.text.toString().trim().ifEmpty { "beginner" }
                if (task.status == "completed") {
                    // Launch quiz for completed lessons
                    val intent = Intent(this, QuizActivity::class.java).apply {
                        putExtra(QuizActivity.EXTRA_TOPIC, task.topic)
                        putExtra(QuizActivity.EXTRA_SKILL_LEVEL, skillLevel)
                        putExtra(QuizActivity.EXTRA_USER_ID, userId)
                        putExtra(QuizActivity.EXTRA_PLAN_ID, currentPlanId ?: "")
                        putExtra(QuizActivity.EXTRA_DAY, task.day)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                } else {
                    // Launch tutorial for pending/missed
                    val intent = Intent(this, TutorialActivity::class.java).apply {
                        putExtra(TutorialActivity.EXTRA_TOPIC, task.topic)
                        putExtra(TutorialActivity.EXTRA_SKILL_LEVEL, skillLevel)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
        )

        binding.rvSchedule.layoutManager = LinearLayoutManager(this)
        binding.rvSchedule.adapter = adapter

        // ── Generate plan button ────────────────────────────────────────
        binding.btnGeneratePlan.setOnClickListener {
            val topic = binding.etTopic.text.toString().trim()
            val minutes = binding.etMinutes.text.toString().toIntOrNull() ?: 30
            val totalDays = binding.etTotalDays.text.toString().toIntOrNull() ?: 14
            val level = binding.etSkillLevel.text.toString().trim().ifEmpty { "beginner" }

            if (topic.isEmpty()) {
                binding.etTopic.error = "Enter a topic"
                return@setOnClickListener
            }

            viewModel.generatePlan(userId, topic, minutes, totalDays, level)
        }

        // ── Observe loading ─────────────────────────────────────────────
        viewModel.loading.observe(this) { loading ->
            binding.loadingCard.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnGeneratePlan.isEnabled = !loading
        }

        // ── Observe errors ──────────────────────────────────────────────
        viewModel.error.observe(this) { err ->
            if (err != null) {
                binding.tvError.text = err
                binding.errorCard.visibility = View.VISIBLE
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
            } else {
                binding.errorCard.visibility = View.GONE
            }
        }

        // ── Observe plan creation ───────────────────────────────────────
        viewModel.plan.observe(this) { plan ->
            if (plan != null) {
                currentPlanId = plan.planId
                prefs.edit().putString("last_plan_id", plan.planId).apply()

                // Collapse the form, show the plan summary
                binding.formContainer.visibility = View.GONE
                binding.planSummary.visibility = View.VISIBLE
                binding.tvGoal.text = plan.goal
            }
        }

        // ── Observe schedule (real-time from Firestore) ─────────────────
        viewModel.schedule.observe(this) { tasks ->
            // Find today's lesson (first pending task)
            val today = tasks.firstOrNull { it.status == "pending" }
            todayTask = today
            adapter.todayDay = today?.day ?: -1
            adapter.submitList(tasks.toList())

            // Update progress text
            if (tasks.isNotEmpty()) {
                val completed = tasks.count { it.status == "completed" }
                val total = tasks.size
                binding.tvProgress.text = "$completed / $total days completed"

                // Show today's lesson hero card
                if (today != null) {
                    binding.todayLessonCard.visibility = View.VISIBLE
                    binding.tvTodayTopic.text = today.topic
                    binding.tvTodayDuration.text = "${today.durationMins} min lesson"
                    binding.tvTodayDay.text = "Day ${today.day} of $total"
                    binding.todayCompleteRow.visibility = View.VISIBLE
                    binding.reminderRow.visibility = View.VISIBLE
                    binding.tvUpcomingLabel.visibility = View.VISIBLE

                    // Streak display
                    val streak = tasks.takeWhile { it.status == "completed" }.size
                    if (streak >= 2) {
                        binding.tvTodayStreak.visibility = View.VISIBLE
                        binding.tvTodayStreak.text = "🔥 $streak day streak"
                    } else {
                        binding.tvTodayStreak.visibility = View.GONE
                    }
                } else if (completed == total && total > 0) {
                    // All done!
                    binding.todayLessonCard.visibility = View.VISIBLE
                    binding.tvTodayTopic.text = "🎉 Course Complete!"
                    binding.tvTodayDuration.text = "You finished all $total lessons"
                    binding.tvTodayDay.text = ""
                    binding.btnStartLesson.text = "View Certificate"
                    binding.todayCompleteRow.visibility = View.GONE
                    binding.reminderRow.visibility = View.GONE
                    binding.tvUpcomingLabel.visibility = View.VISIBLE
                } else {
                    binding.todayLessonCard.visibility = View.GONE
                }
            }
        }

        // ── Observe in-flight updates (disable buttons while updating) ──
        viewModel.updatingDays.observe(this) { days ->
            adapter.updatingDays = days
            adapter.notifyDataSetChanged()
        }

        // ── Observe points ──────────────────────────────────────────────
        viewModel.totalPoints.observe(this) { points ->
            if (points > 0) {
                binding.pointsBadge.visibility = View.VISIBLE
                binding.tvPoints.text = points.toString()
            }
        }

        // ── Animate points earned ───────────────────────────────────────
        viewModel.pointsEarned.observe(this) { earned ->
            if (earned != null && earned > 0) {
                showPointsAnimation(earned)
                viewModel.clearPointsAnimation()
            }
        }

        // ── Load user's points on startup ───────────────────────────────
        if (userId.isNotEmpty()) {
            viewModel.loadPoints(userId)
        }

        // ── Schedule daily reminders ────────────────────────────────────
        ReminderWorker.createChannel(this)
        ReminderWorker.schedule(this)

        // ── Request notification permission (Android 13+) ───────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // ── Today's lesson buttons ──────────────────────────────────────
        binding.btnStartLesson.setOnClickListener {
            val task = todayTask ?: return@setOnClickListener
            val skillLevel = binding.etSkillLevel.text.toString().trim().ifEmpty { "Beginner" }
            val intent = Intent(this, TutorialActivity::class.java).apply {
                putExtra(TutorialActivity.EXTRA_TOPIC, task.topic)
                putExtra(TutorialActivity.EXTRA_SKILL_LEVEL, skillLevel)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnTodayComplete.setOnClickListener {
            val task = todayTask ?: return@setOnClickListener
            currentPlanId?.let { planId ->
                viewModel.updateDayStatus(userId, planId, task.day, "completed")
            }
        }

        binding.btnTodayMiss.setOnClickListener {
            val task = todayTask ?: return@setOnClickListener
            currentPlanId?.let { planId ->
                viewModel.updateDayStatus(userId, planId, task.day, "missed")
            }
        }

        // ── Reminder time picker ────────────────────────────────────────
        val savedHour = prefs.getInt("reminder_hour", 9)
        val savedMinute = prefs.getInt("reminder_minute", 0)
        updateReminderTimeDisplay(savedHour, savedMinute)

        binding.tvReminderTime.setOnClickListener {
            val curHour = prefs.getInt("reminder_hour", 9)
            val curMin = prefs.getInt("reminder_minute", 0)
            TimePickerDialog(this, { _, hour, minute ->
                prefs.edit()
                    .putInt("reminder_hour", hour)
                    .putInt("reminder_minute", minute)
                    .apply()
                updateReminderTimeDisplay(hour, minute)
                ReminderWorker.schedule(this)  // Reschedule with new time
                Toast.makeText(this, "Reminder set!", Toast.LENGTH_SHORT).show()
            }, curHour, curMin, false).show()
        }

        // ── Load plan from History or restore last session ─────────────
        val incomingPlanId = intent.getStringExtra("plan_id")
        if (incomingPlanId != null) {
            viewModel.loadPlan(incomingPlanId)
        } else {
            val savedPlanId = prefs.getString("last_plan_id", null)
            if (savedPlanId != null) {
                viewModel.loadPlan(savedPlanId)
            }
        }
    }

    private fun updateReminderTimeDisplay(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        binding.tvReminderTime.text = String.format("%d:%02d %s", displayHour, minute, amPm)
    }

    private fun showPointsAnimation(points: Int) {
        val anim = binding.tvPointsAnim
        val streakText = viewModel.streak.value?.let {
            if (it >= 3) "\n🔥 $it day streak!" else ""
        } ?: ""
        anim.text = "+$points XP$streakText"
        anim.visibility = View.VISIBLE
        anim.alpha = 1f
        anim.scaleX = 0.5f
        anim.scaleY = 0.5f
        anim.translationY = 0f

        anim.animate()
            .alpha(0f)
            .scaleX(1.5f)
            .scaleY(1.5f)
            .translationY(-200f)
            .setDuration(1200)
            .setInterpolator(AccelerateInterpolator(0.5f))
            .withEndAction { anim.visibility = View.GONE }
            .start()
    }
}
