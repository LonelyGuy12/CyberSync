package com.lonelytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lonelytrack.adapter.HistoryAdapter
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private var historyAdapter: HistoryAdapter? = null
    private var userId: String = "default_user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        userId = intent.getStringExtra("user_id") ?: "default_user"

        binding.rvHistory.layoutManager = LinearLayoutManager(this)

        loadHistory(userId)
    }

    private fun loadHistory(userId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getHistory(userId)
                binding.progressBar.visibility = View.GONE

                if (response.plans.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    historyAdapter = HistoryAdapter(
                        response.plans.toMutableList(),
                        onPlanClick = { plan ->
                            val intent = Intent(this@HistoryActivity, MainActivity::class.java)
                            intent.putExtra("plan_id", plan.planId)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(intent)
                            finish()
                        },
                        onDeleteClick = { plan, position ->
                            confirmDelete(plan.planId, plan.topic, position)
                        }
                    )
                    binding.rvHistory.adapter = historyAdapter
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvErrorMessage.text = e.message ?: "Failed to load history"
            }
        }
    }

    private fun confirmDelete(planId: String, topic: String, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Plan")
            .setMessage("Delete your \"$topic\" plan? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deletePlan(planId, position) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePlan(planId: String, position: Int) {
        lifecycleScope.launch {
            try {
                RetrofitClient.api.deletePlan(planId, userId)
                historyAdapter?.removeAt(position)
                if (historyAdapter?.itemCount == 0) {
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                Toast.makeText(this@HistoryActivity, "Plan deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
