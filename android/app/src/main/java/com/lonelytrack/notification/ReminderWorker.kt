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
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
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
                val streakText = if (currentStreak > 0) " Don't break your $currentStreak-day streak!" else ""
                val message = if (nextTopic.isNotEmpty()) {
                    "Today's topic: $nextTopic.$streakText"
                } else {
                    "You have $pendingCount lessons waiting.$streakText"
                }
                showNotification("Time to learn! 📚", message)
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
