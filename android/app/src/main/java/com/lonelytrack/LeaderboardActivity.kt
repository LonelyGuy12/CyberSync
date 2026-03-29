package com.lonelytrack

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lonelytrack.adapter.LeaderboardAdapter
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityLeaderboardBinding
import kotlinx.coroutines.launch

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvLeaderboard.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getLeaderboard()
                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                binding.rvLeaderboard.adapter = LeaderboardAdapter(response.leaderboard, currentUserId)
                binding.rvLeaderboard.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(this@LeaderboardActivity, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
