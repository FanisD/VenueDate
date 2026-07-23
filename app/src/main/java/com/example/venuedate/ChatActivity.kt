package com.example.venuedate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class ChatActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private var matchId: String? = null
    private var partnerUid: String? = null
    private var messageListener: ListenerRegistration? = null

    private val chatChannelId = "venue_date_chat_alerts"
    private val appOpenTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        matchId = intent.getStringExtra("MATCH_ID")
        if (matchId == null) finish()

        createChatNotificationChannel()

        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)

        // NEW HEADER BUTTONS
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnOptions = findViewById<ImageButton>(R.id.btnOptions)

        adapter = MessageAdapter(messages, auth.currentUser?.uid ?: "")
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        // Fetch partner details and start services
        matchId?.let { id ->
            db.collection("matches").document(id).get().addOnSuccessListener { doc ->
                val users = doc.get("users") as? List<*>
                val stringUsers = users?.filterIsInstance<String>() ?: emptyList()
                partnerUid = stringUsers.firstOrNull { it != auth.currentUser?.uid }

                // Start tracking updates
                startTimer(tvCountdown)
                listenForMessages()
            }
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.text.clear()
            }
        }

        // 1. Back Button Action
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Options Menu (3-dot icon) Action
        btnOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Report User")
            popup.menu.add(0, 2, 0, "Block User")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        reportUser()
                        true
                    }
                    2 -> {
                        blockUser()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun startTimer(tv: TextView) {
        matchId?.let { id ->
            db.collection("matches").document(id).get().addOnSuccessListener { doc ->
                val expiresAt = doc.getLong("expiresAt") ?: 0L
                val remaining = expiresAt - System.currentTimeMillis()

                if (remaining > 0) {
                    object : CountDownTimer(remaining, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val mins = (millisUntilFinished / 1000) / 60
                            val secs = (millisUntilFinished / 1000) % 60
                            tv.text = String.format("Time left: %02d:%02d", mins, secs)
                        }

                        override fun onFinish() {
                            tv.text = "Match Expired"
                            findViewById<ImageButton>(R.id.btnSend).isEnabled = false
                        }
                    }.start()
                } else {
                    tv.text = "Match Expired"
                    findViewById<ImageButton>(R.id.btnSend).isEnabled = false
                }
            }
        }
    }

    private fun listenForMessages() {
        val myUid = auth.currentUser?.uid ?: return

        matchId?.let { id ->
            messageListener = db.collection("matches").document(id).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) return@addSnapshotListener

                    val isInitialLoad = snapshots.metadata.isFromCache

                    snapshots.documentChanges.forEach { dc ->
                        val msg = dc.document.toObject(Message::class.java)

                        if (dc.type == DocumentChange.Type.ADDED) {
                            // Local rendering inside RecyclerView list
                            if (!messages.contains(msg)) {
                                messages.add(msg)
                            }

                            // Trigger system tray alert IF message is incoming and fresh
                            if (!isInitialLoad && msg.timestamp > appOpenTime && msg.senderId != myUid) {
                                triggerChatNotification(msg.text)
                            }
                        }
                    }

                    adapter.notifyDataSetChanged()
                    findViewById<RecyclerView>(R.id.rvMessages).scrollToPosition(messages.size - 1)
                }
        }
    }

    private fun sendMessage(text: String) {
        val myUid = auth.currentUser?.uid ?: return
        val target = partnerUid ?: return

        // Formatted to track targets cleanly
        val msg = hashMapOf(
            "senderId" to myUid,
            "receiverId" to target,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )

        matchId?.let { id ->
            db.collection("matches").document(id).collection("messages").add(msg)
        }
    }

    private fun triggerChatNotification(body: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("MATCH_ID", matchId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, chatChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New Message")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, builder.build())
    }

    private fun createChatNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Chat Messages"
            val channel = NotificationChannel(chatChannelId, name, NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ------------------------------------------------------------------------
    // TRUST & SAFETY FUNCTIONS (BLOCK / REPORT)
    // ------------------------------------------------------------------------

    private fun reportUser() {
        val myUid = auth.currentUser?.uid ?: return
        val targetUid = partnerUid ?: return

        // Fetch their email first so we can ban them if they hit 3 strikes
        db.collection("users").document(targetUid).get().addOnSuccessListener { doc ->
            val targetEmail = doc.getString("email") ?: "unknown"

            // 1. Log the report in the database for admins to see
            val reportData = hashMapOf(
                "reporter" to myUid,
                "reportedUser" to targetUid,
                "reason" to "Inappropriate Behavior",
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("reports").add(reportData)

            // 2. Increment the bad user's strike counter
            val userRef = db.collection("users").document(targetUid)
            userRef.update("reportCount", FieldValue.increment(1)).addOnFailureListener {
                // If they have 0 reports so far, initialize it to 1
                userRef.set(hashMapOf("reportCount" to 1), SetOptions.merge())
            }

            // 3. Check if they hit 3 strikes
            userRef.get().addOnSuccessListener { strikeDoc ->
                val currentReports = strikeDoc.getLong("reportCount") ?: 1L
                if (currentReports >= 3L && targetEmail != "unknown") {
                    // STRIKE 3: Add their email to the blacklist and vaporize their profile
                    db.collection("banned_emails").document(targetEmail).set(hashMapOf("bannedAt" to System.currentTimeMillis()))
                    userRef.delete()
                }

                Toast.makeText(this, "User reported. Thank you for keeping the community safe.", Toast.LENGTH_SHORT).show()
                // Auto-block the user so they disappear immediately
                blockUser()
            }
        }
    }

    private fun blockUser() {
        val myUid = auth.currentUser?.uid ?: return
        val target = partnerUid ?: return

        // FieldValue.arrayUnion safely adds the UID to the list without erasing existing ones
        db.collection("users").document(myUid)
            .update("blockedUsers", FieldValue.arrayUnion(target))
            .addOnSuccessListener {
                Toast.makeText(this, "User blocked. You will no longer see them.", Toast.LENGTH_LONG).show()
                finish() // Kicks the user out of the chat screen and back to safety
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.remove()
    }
}