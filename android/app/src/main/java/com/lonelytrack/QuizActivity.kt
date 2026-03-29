package com.lonelytrack

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.databinding.ActivityQuizBinding
import com.lonelytrack.model.QuizQuestion
import com.lonelytrack.model.QuizRequest
import com.lonelytrack.model.QuizSubmission
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOPIC = "topic"
        const val EXTRA_SKILL_LEVEL = "skill_level"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_DAY = "day"
    }

    private lateinit var binding: ActivityQuizBinding
    private var questions: List<QuizQuestion> = emptyList()
    private val userAnswers = mutableMapOf<Int, Int>() // question index -> selected option

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: "General"
        val skillLevel = intent.getStringExtra(EXTRA_SKILL_LEVEL) ?: "beginner"

        binding.tvTitle.text = "Quiz: $topic"
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSubmit.setOnClickListener { submitQuiz() }
        binding.btnDone.setOnClickListener { finish() }

        loadQuiz(topic, skillLevel)
    }

    private fun loadQuiz(topic: String, skillLevel: String) {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.generateQuiz(
                    QuizRequest(topic, skillLevel, 5)
                )
                questions = response.questions
                displayQuestions()
            } catch (e: Exception) {
                Toast.makeText(this@QuizActivity, "Failed to load quiz: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun displayQuestions() {
        binding.loadingContainer.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.VISIBLE
        binding.tvQuestionCount.text = "${questions.size} questions"

        binding.questionsContainer.removeAllViews()

        questions.forEachIndexed { index, question ->
            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16.dp }
                radius = 12f.dp.toFloat()
                setCardBackgroundColor(0xFF16213E.toInt())
                cardElevation = 2f.dp.toFloat()
            }

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dp, 16.dp, 20.dp, 16.dp)
            }

            // Question number + text
            val tvQuestion = TextView(this).apply {
                text = "${index + 1}. ${question.question}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                setPadding(0, 0, 0, 12.dp)
            }
            cardContent.addView(tvQuestion)

            // Radio group for options
            val radioGroup = RadioGroup(this).apply {
                tag = index
            }

            question.options.forEachIndexed { optIndex, option ->
                val rb = RadioButton(this).apply {
                    text = option
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 14f
                    id = View.generateViewId()
                    tag = optIndex
                    setPadding(8.dp, 4.dp, 0, 4.dp)
                }
                radioGroup.addView(rb)
            }

            radioGroup.setOnCheckedChangeListener { group, checkedId ->
                val rb = group.findViewById<RadioButton>(checkedId)
                val optIndex = rb?.tag as? Int ?: return@setOnCheckedChangeListener
                userAnswers[index] = optIndex
            }

            cardContent.addView(radioGroup)
            card.addView(cardContent)
            binding.questionsContainer.addView(card)
        }
    }

    private fun submitQuiz() {
        if (userAnswers.size < questions.size) {
            Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate score locally
        var correct = 0
        questions.forEachIndexed { index, q ->
            if (userAnswers[index] == q.correctAnswer) correct++
        }

        val scorePercent = (correct.toDouble() / questions.size * 100).toInt()

        // Show results with explanations
        binding.scrollContent.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE

        // Color code correct/wrong in the questions
        showResults(correct, scorePercent)

        // Submit to backend for XP
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
        val day = intent.getIntExtra(EXTRA_DAY, 0)

        if (userId != null && planId != null && day > 0) {
            lifecycleScope.launch {
                try {
                    val answers = (0 until questions.size).map { userAnswers[it] ?: -1 }
                    val result = RetrofitClient.api.submitQuiz(
                        QuizSubmission(userId, planId, day, answers)
                    )
                    binding.tvBonusXp.text = "+${result.bonusXp} XP"
                } catch (_: Exception) {
                    binding.tvBonusXp.text = "+5 XP"
                }
            }
        } else {
            val bonusXp = when {
                scorePercent >= 80 -> 15
                scorePercent >= 60 -> 10
                else -> 5
            }
            binding.tvBonusXp.text = "+$bonusXp XP"
        }
    }

    private fun showResults(correct: Int, scorePercent: Int) {
        binding.resultContainer.visibility = View.VISIBLE
        binding.tvScore.text = "$correct/${questions.size}"
        binding.tvScoreLabel.text = when {
            scorePercent >= 80 -> "Excellent! 🎉"
            scorePercent >= 60 -> "Good job! 👍"
            scorePercent >= 40 -> "Keep practicing! 📚"
            else -> "Review the material 🔄"
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
