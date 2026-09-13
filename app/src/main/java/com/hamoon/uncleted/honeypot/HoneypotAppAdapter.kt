package com.hamoon.uncleted.honeypot

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hamoon.uncleted.databinding.ItemHoneypotAppBinding

class HoneypotAppAdapter(
    private val apps: List<FakeApp>,
    private val onClick: (FakeApp) -> Unit
) : RecyclerView.Adapter<HoneypotAppAdapter.AppViewHolder>() {

    class AppViewHolder(val binding: ItemHoneypotAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemHoneypotAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.tvAppName.text = app.name
        holder.binding.ivAppIcon.setImageResource(app.iconRes)
        holder.binding.root.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = apps.size
}