package com.lonelytrack.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lonelytrack.databinding.ItemLeaderboardBinding
import com.lonelytrack.model.LeaderboardEntry

class LeaderboardAdapter(
    private val entries: List<LeaderboardEntry>,
    private val currentUserId: String?
) : RecyclerView.Adapter<LeaderboardAdapter.VH>() {

    inner class VH(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        val rank = position + 1

        holder.binding.tvRank.text = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "#$rank"
        }

        holder.binding.tvName.text = entry.displayName
        holder.binding.tvXp.text = "${entry.totalPoints} XP"
        holder.binding.tvStreak.text = if (entry.streak > 0) "🔥 ${entry.streak} day streak" else ""

        // Highlight current user
        if (entry.userId == currentUserId) {
            holder.binding.root.setCardBackgroundColor(0xFF1A3A5C.toInt())
        } else {
            holder.binding.root.setCardBackgroundColor(0xFF16213E.toInt())
        }
    }

    override fun getItemCount() = entries.size
}
