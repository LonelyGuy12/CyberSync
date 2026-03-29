package com.lonelytrack.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lonelytrack.R
import com.lonelytrack.databinding.ItemDayTaskBinding
import com.lonelytrack.model.DailyTask

class ScheduleAdapter(
    private val onComplete: (DailyTask) -> Unit,
    private val onMiss: (DailyTask) -> Unit,
    private val onItemClick: (DailyTask) -> Unit = {}
) : ListAdapter<DailyTask, ScheduleAdapter.TaskViewHolder>(DiffCallback) {

    var updatingDays: Set<Int> = emptySet()
    var todayDay: Int = -1  // The current day (first pending day)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemDayTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val b: ItemDayTaskBinding
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(task: DailyTask) {
            val context = b.root.context
            
            b.tvDay.text = task.day.toString()
            b.tvTopic.text = task.topic
            b.tvDuration.text = "${task.durationMins} min"

            val isToday = task.day == todayDay
            val isFuture = task.status == "pending" && task.day > todayDay && todayDay > 0

            // Circle color based on status
            val circleColor = when {
                isToday     -> context.getColor(R.color.primary)
                task.status == "completed" -> context.getColor(R.color.success)
                task.status == "missed"    -> context.getColor(R.color.error)
                isFuture    -> context.getColor(R.color.border)
                else        -> context.getColor(R.color.pending)
            }
            (b.tvDay.background as? GradientDrawable)?.setColor(circleColor)

            // Dim future lessons (Duolingo style - locked look)
            val cardAlpha = if (isFuture) 0.4f else 1f
            b.root.alpha = cardAlpha

            when (task.status) {
                "pending" -> {
                    if (isToday) {
                        // Today's lesson — hide buttons here (hero card handles it)
                        b.actionButtons.visibility = View.GONE
                        b.tvStatus.visibility = View.VISIBLE
                        b.tvStatus.text = "▶"
                        b.tvStatus.setTextColor(context.getColor(R.color.primary))
                        b.tvStatus.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        b.tvTopic.alpha = 1f
                        b.tvDuration.alpha = 1f
                    } else if (isFuture) {
                        b.actionButtons.visibility = View.GONE
                        b.tvStatus.visibility = View.VISIBLE
                        b.tvStatus.text = "🔒"
                        b.tvStatus.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        b.tvTopic.alpha = 0.6f
                        b.tvDuration.alpha = 0.6f
                    } else {
                        b.actionButtons.visibility = View.VISIBLE
                        b.tvStatus.visibility = View.GONE
                        b.tvTopic.alpha = 1f
                        b.tvDuration.alpha = 1f
                    }
                }
                "completed" -> {
                    b.actionButtons.visibility = View.GONE
                    b.tvStatus.visibility = View.VISIBLE
                    b.tvStatus.text = "✓"
                    b.tvStatus.setTextColor(context.getColor(R.color.success))
                    b.tvStatus.setBackgroundColor(context.getColor(R.color.success_light))
                    b.tvTopic.alpha = 0.5f
                    b.tvDuration.alpha = 0.5f
                }
                "missed" -> {
                    b.actionButtons.visibility = View.GONE
                    b.tvStatus.visibility = View.VISIBLE
                    b.tvStatus.text = "✗"
                    b.tvStatus.setTextColor(context.getColor(R.color.error))
                    b.tvStatus.setBackgroundColor(context.getColor(R.color.error_light))
                    b.tvTopic.alpha = 0.5f
                    b.tvDuration.alpha = 0.5f
                }
            }

            val isUpdating = updatingDays.contains(task.day)
            b.btnComplete.isEnabled = !isUpdating
            b.btnMiss.isEnabled = !isUpdating
            b.btnComplete.alpha = if (isUpdating) 0.4f else 1f
            b.btnMiss.alpha = if (isUpdating) 0.4f else 1f

            b.btnComplete.setOnClickListener { onComplete(task) }
            b.btnMiss.setOnClickListener { onMiss(task) }

            // Tap on the card (day number or topic) to open tutorial
            b.root.setOnClickListener { onItemClick(task) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DailyTask>() {
        override fun areItemsTheSame(old: DailyTask, new: DailyTask) = old.day == new.day
        override fun areContentsTheSame(old: DailyTask, new: DailyTask) = old == new
    }
}
