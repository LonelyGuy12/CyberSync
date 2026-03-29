package com.lonelytrack.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lonelytrack.R
import com.lonelytrack.model.Trophy

class TrophyAdapter(private val trophies: List<Trophy>) :
    RecyclerView.Adapter<TrophyAdapter.TrophyViewHolder>() {

    inner class TrophyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvTrophyIcon)
        val tvName: TextView = view.findViewById(R.id.tvTrophyName)
        val tvDesc: TextView = view.findViewById(R.id.tvTrophyDesc)
        val tvStatus: TextView = view.findViewById(R.id.tvTrophyStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrophyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trophy, parent, false)
        return TrophyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrophyViewHolder, position: Int) {
        val trophy = trophies[position]
        holder.tvIcon.text = trophy.icon
        holder.tvName.text = trophy.name
        holder.tvDesc.text = trophy.desc

        if (trophy.unlocked) {
            holder.tvStatus.text = "✅"
            holder.itemView.alpha = 1.0f
            holder.tvName.setTextColor(0xFFFFFFFF.toInt())
        } else {
            holder.tvStatus.text = "🔒"
            holder.itemView.alpha = 0.5f
            holder.tvName.setTextColor(0xFF888888.toInt())
        }
    }

    override fun getItemCount() = trophies.size
}
