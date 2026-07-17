package com.example.venuedate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ImageButton

class MatchesInboxActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: InboxAdapter
    private val matchPairsList = mutableListOf<Pair<String, String>>() // Holds Pair(MatchId, PartnerUid)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_matches_inbox)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val myUid = auth.currentUser?.uid ?: return

        val rv = findViewById<RecyclerView>(R.id.rvInbox)
        adapter = InboxAdapter(matchPairsList, myUid)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadActiveMatches(myUid)
    }

    private fun loadActiveMatches(myUid: String) {
        db.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshots, e ->
                if (snapshots == null || e != null) return@addSnapshotListener

                val loadedPairs = mutableListOf<Pair<String, String>>()

                snapshots.forEach { doc ->
                    val expiresAt = doc.getLong("expiresAt") ?: 0L

                    // Only show chats that still have time left on the clock!
                    if (expiresAt > System.currentTimeMillis()) {
                        val matchId = doc.id
                        val users = doc.get("users") as? List<String>
                        val partnerUid = users?.firstOrNull { it != myUid }

                        if (partnerUid != null) {
                            loadedPairs.add(Pair(matchId, partnerUid))
                        }
                    }
                }
                adapter.updateList(loadedPairs)
            }
    }
}