package com.lonelytrack.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lonelytrack.databinding.ItemHistoryPlanBinding
import com.lonelytrack.model.HistoryPlanSummary

class HistoryAdapter(
    private val plans: MutableList<HistoryPlanSummary>,
    private val onPlanClick: (HistoryPlanSummary) -> Unit,
    private val onDeleteClick: (HistoryPlanSummary, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryPlanBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryPlanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val plan = plans[position]
        holder.binding.apply {
            tvTopic.text = plan.topic.uppercase()
            tvGoal.text = plan.goal

            val pct = if (plan.totalDays > 0) (plan.completedDays * 100) / plan.totalDays else 0
            progressPlan.progress = pct
            tvProgress.text = "${plan.completedDays} / ${plan.totalDays} days completed"

            // Format date — show just the date part of the ISO string
            tvDate.text = plan.createdAt.take(10)

            root.setOnClickListener { onPlanClick(plan) }
            btnDelete.setOnClickListener { onDeleteClick(plan, holder.adapterPosition) }
        }
    }

    fun removeAt(position: Int) {
        plans.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, plans.size)
    }

    override fun getItemCount() = plans.size
}
