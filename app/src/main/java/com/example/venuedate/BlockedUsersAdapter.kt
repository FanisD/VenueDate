package com.example.venuedate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop

class BlockedUsersAdapter(
    private var users: MutableList<User>,
    private val onUnblockClick: (User, Int) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPic: ImageView = view.findViewById(R.id.ivBlockedPic)
        val tvName: TextView = view.findViewById(R.id.tvBlockedName)
        val btnUnblock: Button = view.findViewById(R.id.btnUnblock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.firstName

        if (user.imageUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.imageUrls[0])
                .transform(CircleCrop())
                .into(holder.ivPic)
        }

        holder.btnUnblock.setOnClickListener {
            onUnblockClick(user, holder.adapterPosition)
        }
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun removeUser(position: Int) {
        users.removeAt(position)
        notifyItemRemoved(position)
    }
}