package com.lonelytrack

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.lonelytrack.adapter.TrophyAdapter
import com.lonelytrack.api.RetrofitClient
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return

        // Set user info
        val tvName = findViewById<TextView>(R.id.tvUserName)
        val tvEmail = findViewById<TextView>(R.id.tvUserEmail)
        val tvInitial = findViewById<TextView>(R.id.tvAvatarInitial)

        if (user.isAnonymous) {
            tvName.text = "Guest"
            tvEmail.text = "Anonymous account"
            tvInitial.text = "G"
        } else {
            val name = user.displayName ?: user.email?.substringBefore("@") ?: "User"
            tvName.text = name
            tvEmail.text = user.email ?: ""
            tvInitial.text = name.first().uppercase()
        }

        val progress = findViewById<ProgressBar>(R.id.progressLoading)
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val profile = RetrofitClient.api.getProfile(userId)

                progress.visibility = View.GONE

                // Stats
                findViewById<TextView>(R.id.tvStars).text = "⭐ ${profile.stars}"
                findViewById<TextView>(R.id.tvTotalXP).text = "${profile.totalPoints}"
                findViewById<TextView>(R.id.tvCurrentStreak).text = "🔥 ${profile.currentStreak}"
                findViewById<TextView>(R.id.tvCompleted).text = "${profile.totalCompleted}"
                findViewById<TextView>(R.id.tvBestStreak).text = "⚡ ${profile.bestStreak}"
                findViewById<TextView>(R.id.tvTotalPlans).text = "📋 ${profile.totalPlans}"

                // Trophies
                val rv = findViewById<RecyclerView>(R.id.rvTrophies)
                rv.layoutManager = LinearLayoutManager(this@ProfileActivity)
                rv.adapter = TrophyAdapter(profile.trophies)

                // Topics
                val tvTopics = findViewById<TextView>(R.id.tvTopics)
                if (profile.topicsStudied.isNotEmpty()) {
                    tvTopics.text = profile.topicsStudied.joinToString("  •  ") { it.replaceFirstChar { c -> c.uppercase() } }
                } else {
                    tvTopics.text = "No topics studied yet. Start a plan!"
                }

            } catch (e: Exception) {
                progress.visibility = View.GONE
                Toast.makeText(this@ProfileActivity, "Failed to load profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
