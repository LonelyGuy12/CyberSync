package com.lonelytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lonelytrack.adapter.HistoryAdapter
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val userId = intent.getStringExtra("user_id") ?: "default_user"

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
                    val adapter = HistoryAdapter(response.plans) { plan ->
                        // Navigate back to MainActivity with this plan
                        val intent = Intent(this@HistoryActivity, MainActivity::class.java)
                        intent.putExtra("plan_id", plan.planId)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        finish()
                    }
                    binding.rvHistory.adapter = adapter
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvErrorMessage.text = e.message ?: "Failed to load history"
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
