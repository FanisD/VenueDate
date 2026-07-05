package com.example.venuedate

import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class InboxAdapter(
    private var matchMaps: List<Pair<String, String>>, // Pair(MatchId, PartnerUid)
    private val currentUserId: String
) : RecyclerView.Adapter<InboxAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivInboxUserThumb)
        val tvName: TextView = view.findViewById(R.id.tvInboxUserName)
        val tvTimer: TextView = view.findViewById(R.id.tvInboxTimer)
        val btnOpen: Button = view.findViewById(R.id.btnOpenChat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inbox_match, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (matchId, partnerUid) = matchMaps[position]

        // 1. Fetch Partner Information
        db.collection("users").document(partnerUid).get().addOnSuccessListener { doc ->
            val name = doc.getString("firstName") ?: "User"
            holder.tvName.text = name

            val urls = doc.get("imageUrls") as? List<String>
            if (!urls.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(urls[0])
                    .circleCrop()
                    .into(holder.ivThumb)
            }
        }

        // 2. Fetch/Handle live room time limits
        db.collection("matches").document(matchId).get().addOnSuccessListener { doc ->
            val expiresAt = doc.getLong("expiresAt") ?: 0L
            val remaining = expiresAt - System.currentTimeMillis()

            if (remaining > 0) {
                val mins = (remaining / 1000) / 60
                holder.tvTimer.text = "Closes in: ${mins}m"
            } else {
                holder.tvTimer.text = "Expired"
                holder.btnOpen.isEnabled = false
            }
        }

        // 3. Launch Chat Room
        holder.btnOpen.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra("MATCH_ID", matchId)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = matchMaps.size

    fun updateList(newList: List<Pair<String, String>>) {
        matchMaps = newList
        notifyDataSetChanged()
    }
}