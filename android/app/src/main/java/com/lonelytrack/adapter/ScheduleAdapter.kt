package com.lonelytrack.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lonelytrack.databinding.ItemDayTaskBinding
import com.lonelytrack.model.DailyTask

class ScheduleAdapter(
    private val onComplete: (DailyTask) -> Unit,
    private val onMiss: (DailyTask) -> Unit
) : ListAdapter<DailyTask, ScheduleAdapter.TaskViewHolder>(DiffCallback) {

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
            b.tvDay.text = task.day.toString()
            b.tvTopic.text = task.topic
            b.tvDuration.text = "${task.durationMins} min"

            // Circle color based on status
            val circleColor = when (task.status) {
                "completed" -> Color.parseColor("#4CAF50")
                "missed"    -> Color.parseColor("#F44336")
                else        -> Color.parseColor("#6200EE")
            }
            (b.tvDay.background as? GradientDrawable)?.setColor(circleColor)

            when (task.status) {
                "pending" -> {
                    b.btnComplete.visibility = View.VISIBLE
                    b.btnMiss.visibility = View.VISIBLE
                    b.tvStatus.visibility = View.GONE
                    b.tvTopic.alpha = 1f
                }
                "completed" -> {
                    b.btnComplete.visibility = View.GONE
                    b.btnMiss.visibility = View.GONE
                    b.tvStatus.visibility = View.VISIBLE
                    b.tvStatus.text = "✓ Done"
                    b.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    b.tvTopic.alpha = 0.6f
                }
                "missed" -> {
                    b.btnComplete.visibility = View.GONE
                    b.btnMiss.visibility = View.GONE
                    b.tvStatus.visibility = View.VISIBLE
                    b.tvStatus.text = "✗ Missed"
                    b.tvStatus.setTextColor(Color.parseColor("#F44336"))
                    b.tvTopic.alpha = 0.6f
                }
            }

            b.btnComplete.setOnClickListener { onComplete(task) }
            b.btnMiss.setOnClickListener { onMiss(task) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DailyTask>() {
        override fun areItemsTheSame(old: DailyTask, new: DailyTask) = old.day == new.day
        override fun areContentsTheSame(old: DailyTask, new: DailyTask) = old == new
    }
}
