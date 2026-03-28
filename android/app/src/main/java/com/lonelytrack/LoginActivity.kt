package com.lonelytrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lonelytrack.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // If already signed in, skip login
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnGuest.setOnClickListener { continueAsGuest() }
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validation
        if (email.isEmpty()) {
            showError("Please enter your email")
            return
        }
        if (password.isEmpty()) {
            showError("Please enter your password")
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when {
                    e.message?.contains("no user record") == true -> "No account found with this email"
                    e.message?.contains("password is invalid") == true -> "Incorrect password"
                    e.message?.contains("badly formatted") == true -> "Invalid email format"
                    else -> e.localizedMessage ?: "Login failed"
                }
                showError(msg)
            }
    }

    private fun continueAsGuest() {
        setLoading(true)
        binding.btnGuest.isEnabled = false
        binding.btnGuest.alpha = 0.5f

        auth.signInAnonymously()
            .addOnSuccessListener {
                setLoading(false)
                goToMain()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                binding.btnGuest.isEnabled = true
                binding.btnGuest.alpha = 1f
                showError(e.localizedMessage ?: "Guest login failed")
            }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.alpha = if (loading) 0.5f else 1f
        if (loading) binding.tvError.visibility = View.GONE
    }
}
