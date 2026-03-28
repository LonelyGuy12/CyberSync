package com.lonelytrack

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityTutorialBinding
import com.lonelytrack.model.TutorialRequest
import kotlinx.coroutines.launch

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding

    companion object {
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_SKILL_LEVEL = "extra_skill_level"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: "Unknown"
        val skillLevel = intent.getStringExtra(EXTRA_SKILL_LEVEL) ?: "beginner"

        binding.tvToolbarTitle.text = topic
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.btnRetry.setOnClickListener { fetchTutorial(topic, skillLevel) }

        fetchTutorial(topic, skillLevel)
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun fetchTutorial(topic: String, skillLevel: String) {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.generateTutorial(
                    TutorialRequest(topic, skillLevel)
                )

                binding.loadingContainer.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
                binding.tvTutorialTopic.text = response.topic
                binding.tvTutorialBody.text = response.tutorial
            } catch (e: Exception) {
                binding.loadingContainer.visibility = View.GONE
                binding.errorContainer.visibility = View.VISIBLE
                binding.tvTutorialError.text = e.message ?: "Failed to load tutorial"
            }
        }
    }
}
