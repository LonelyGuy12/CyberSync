package com.lonelytrack.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lonelytrack.MainActivity
import com.lonelytrack.R
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "lonelytrack_reminders"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "daily_reminder"

        fun schedule(context: Context) {
            val prefs = context.getSharedPreferences("lonelytrack_prefs", Context.MODE_PRIVATE)
            val hour = prefs.getInt("reminder_hour", 9)
            val minute = prefs.getInt("reminder_minute", 0)

            // Calculate delay until next reminder time
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.DAYS
            ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Study Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminders to keep your learning streak alive"
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        // Check if user has pending tasks
        // Check if notifications are enabled
        val prefs = applicationContext.getSharedPreferences("lonelytrack_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return Result.success()

        try {
            val docs = FirebaseFirestore.getInstance()
                .collection("learning_plans")
                .whereEqualTo("user_id", userId)
                .get()
                .await()

            var pendingCount = 0
            var nextTopic = ""
            var currentStreak = 0

            for (doc in docs) {
                val schedule = doc.get("schedule") as? List<*> ?: continue
                for (task in schedule) {
                    val map = task as? Map<*, *> ?: continue
                    if (map["status"] == "pending") {
                        pendingCount++
                        if (nextTopic.isEmpty()) {
                            nextTopic = map["topic"] as? String ?: ""
                        }
                    }
                    if (map["status"] == "completed") currentStreak++
                }
            }

            if (pendingCount > 0) {
                val motivations = listOf(
                    "Your brain is waiting! 🧠",
                    "Just ${nextTopic.split(":").firstOrNull()?.trim() ?: "one lesson"} today!",
                    "5 minutes is all it takes to start 💪",
                    "Champions don't skip days! 🏆",
                    "Your future self will thank you ⚡"
                )
                val title = if (currentStreak >= 3) {
                    "🔥 $currentStreak-day streak! Keep it alive!"
                } else {
                    motivations.random()
                }
                val message = if (nextTopic.isNotEmpty()) {
                    "Today: $nextTopic"
                } else {
                    "You have $pendingCount lessons waiting for you."
                }
                showNotification(title, message)
            }
        } catch (_: Exception) {
            showNotification("Time to learn! 📚", "Keep your streak alive — open LonelyTrack!")
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        createChannel(applicationContext)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
