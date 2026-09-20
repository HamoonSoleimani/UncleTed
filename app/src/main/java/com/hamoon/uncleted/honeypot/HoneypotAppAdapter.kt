package com.hamoon.uncleted.honeypot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hamoon.uncleted.databinding.ItemHoneypotAppBinding

data class HoneypotAppItem(
    val id: String,
    val name: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val backgroundRes: Int,
    val hasBadge: Boolean = false,
    val targetActivity: Class<*>? = null
)

class HoneypotAppAdapter(
    private val apps: List<HoneypotAppItem>,
    private val onItemClick: (HoneypotAppItem) -> Unit
) : RecyclerView.Adapter<HoneypotAppAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemHoneypotAppBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemHoneypotAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        val context = holder.itemView.context

        holder.binding.tvAppName.text = item.name
        holder.binding.containerIconBg.background = ContextCompat.getDrawable(context, item.backgroundRes)
        holder.binding.ivAppIcon.setImageResource(item.iconRes)

        if (item.hasBadge) {
            holder.binding.badgeNotificationDot.visibility = View.VISIBLE
        } else {
            holder.binding.badgeNotificationDot.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            // Tactile feedback scaling animation
            holder.binding.containerIconBg.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(90)
                .withEndAction {
                    holder.binding.containerIconBg.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(90)
                        .withEndAction {
                            onItemClick(item)
                        }
                        .start()
                }
                .start()
        }
    }

    override fun getItemCount(): Int = apps.size
}