package com.lonelytrack

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityAnalyticsBinding
import kotlinx.coroutines.launch

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        loadAnalytics(userId)
    }

    private fun loadAnalytics(userId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val data = RetrofitClient.api.getAnalytics(userId)

                binding.tvCompletionRate.text = "${data.completionRate}%"
                binding.tvMinutesStudied.text = "${data.totalMinutesStudied}"
                binding.tvCompleted.text = "${data.totalCompleted}"
                binding.tvMissed.text = "${data.totalMissed}"
                binding.tvPending.text = "${data.totalPending}"

                // Course breakdown
                binding.coursesContainer.removeAllViews()
                for (plan in data.planSummaries) {
                    val card = CardView(this@AnalyticsActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12.dp }
                        radius = 12f.dp.toFloat()
                        setCardBackgroundColor(0xFF16213E.toInt())
                        cardElevation = 2f.dp.toFloat()
                    }

                    val content = LinearLayout(this@AnalyticsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16.dp, 12.dp, 16.dp, 12.dp)
                    }

                    val tvTopic = TextView(this@AnalyticsActivity).apply {
                        text = plan.topic.uppercase()
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    content.addView(tvTopic)

                    // Progress bar
                    val progressBar = ProgressBar(
                        this@AnalyticsActivity, null,
                        android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            8.dp
                        ).apply { topMargin = 8.dp; bottomMargin = 4.dp }
                        max = plan.totalDays
                        progress = plan.completed
                        progressDrawable = resources.getDrawable(android.R.drawable.progress_horizontal, null)
                    }
                    content.addView(progressBar)

                    val tvStats = TextView(this@AnalyticsActivity).apply {
                        text = "${plan.completed}/${plan.totalDays} done · ${plan.completionRate}% · ${plan.missed} missed"
                        setTextColor(0xFFAAAAAA.toInt())
                        textSize = 12f
                    }
                    content.addView(tvStats)

                    card.addView(content)
                    binding.coursesContainer.addView(card)
                }

                binding.scrollContent.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(this@AnalyticsActivity, "Failed to load analytics", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
