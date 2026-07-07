package com.example.venuedate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class NearbyAdapter(
    private var users: List<User>,
    private var myHobbies: List<String> = emptyList(), // ADDED: to calculate matches
    private val onIntentClick: (User) -> Unit
) : RecyclerView.Adapter<NearbyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivUserThumb)
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvVibe: TextView = view.findViewById(R.id.tvUserVibe)
        val tvBadge: TextView = view.findViewById(R.id.tvCompatibilityBadge) // ADDED
        val btnTap: Button = view.findViewById(R.id.btnInterested)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nearby_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = "${user.firstName}, ${user.age}"
        holder.tvVibe.text = user.vibeTag

        // FIXED: Calculate compatibility and show/hide badge
        if (myHobbies.isNotEmpty() && user.isCompatibilityModeActive) {
            val sharedCount = user.hobbies.intersect(myHobbies.toSet()).size
            if (sharedCount >= 7) {
                holder.tvBadge.text = "🔥 Top Match ($sharedCount Shared)"
                holder.tvBadge.visibility = View.VISIBLE
            } else {
                holder.tvBadge.visibility = View.GONE
            }
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        if (user.imageUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.imageUrls[0])
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumb)
        }

        holder.btnTap.setOnClickListener { onIntentClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    // ADDED: Update myHobbies when the switch state changes
    fun updateMyHobbies(hobbies: List<String>) {
        myHobbies = hobbies
        notifyDataSetChanged()
    }
}