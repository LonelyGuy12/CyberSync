package com.lonelytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.lonelytrack.adapter.ScheduleAdapter
import com.lonelytrack.databinding.ActivityMainBinding
import com.lonelytrack.viewmodel.LearningViewModel
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LearningViewModel by viewModels()

    // Simple anonymous user ID (in production, use Firebase Auth)
    private val userId: String by lazy {
        val prefs = getSharedPreferences("lonelytrack", MODE_PRIVATE)
        prefs.getString("user_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("user_id", it).apply()
        }
    }

    private var currentPlanId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                R.id.nav_about -> {
                    Toast.makeText(this, "LonelyTrack — AI-powered learning consistency agent", Toast.LENGTH_LONG).show()
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
                // dropdown is read-only, value is always valid
                val intent = Intent(this, TutorialActivity::class.java).apply {
                    putExtra(TutorialActivity.EXTRA_TOPIC, task.topic)
                    putExtra(TutorialActivity.EXTRA_SKILL_LEVEL, skillLevel)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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

                // Collapse the form, show the plan summary
                binding.formContainer.visibility = View.GONE
                binding.planSummary.visibility = View.VISIBLE
                binding.tvGoal.text = plan.goal
            }
        }

        // ── Observe schedule (real-time from Firestore) ─────────────────
        viewModel.schedule.observe(this) { tasks ->
            adapter.submitList(tasks.toList())

            // Update progress text
            if (tasks.isNotEmpty()) {
                val completed = tasks.count { it.status == "completed" }
                val total = tasks.size
                binding.tvProgress.text = "$completed / $total days completed"
            }
        }

        // ── Observe in-flight updates (disable buttons while updating) ──
        viewModel.updatingDays.observe(this) { days ->
            adapter.updatingDays = days
            adapter.notifyDataSetChanged()
        }
    }
}
