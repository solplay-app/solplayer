package com.solplay.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.ivChannelLogo)
        val name: TextView = view.findViewById(R.id.tvChannelName)
        val group: TextView = view.findViewById(R.id.tvChannelGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.name.text = channel.name
        holder.group.text = channel.groupTitle ?: ""
        if (!channel.logoUrl.isNullOrEmpty()) {
            Picasso.get().load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.ic_channel_placeholder)
        }
        holder.itemView.setOnClickListener { onClick(channel) }
    }

    override fun getItemCount(): Int = channels.size
}
