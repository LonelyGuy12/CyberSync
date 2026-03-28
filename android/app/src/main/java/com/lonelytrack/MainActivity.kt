package com.lonelytrack

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            }
        )

        binding.rvSchedule.layoutManager = LinearLayoutManager(this)
        binding.rvSchedule.adapter = adapter

        // ── Generate plan button ────────────────────────────────────────
        binding.btnGeneratePlan.setOnClickListener {
            val topic = binding.etTopic.text.toString().trim()
            val minutes = binding.etMinutes.text.toString().toIntOrNull() ?: 30
            val level = binding.etSkillLevel.text.toString().trim().ifEmpty { "beginner" }

            if (topic.isEmpty()) {
                binding.etTopic.error = "Enter a topic"
                return@setOnClickListener
            }

            viewModel.generatePlan(userId, topic, minutes, level)
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
