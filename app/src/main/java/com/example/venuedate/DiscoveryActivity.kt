package com.example.venuedate

import android.Manifest
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log

class DiscoveryActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var adapter: NearbyAdapter
    private var radarListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var inboundTapListener: ListenerRegistration? = null
    private var globalChatListener: ListenerRegistration? = null
    private val nearbyUsersList = mutableListOf<User>()

    private val channelId = "venue_date_radar"
    private val appSessionStartTime = System.currentTimeMillis()

    private val notifiedCompatibleUsers = mutableSetOf<String>()

    // ADDED: Local cache tracking parameters for compatibility calculation matching
    private var myHobbies = listOf<String>()
    private var isCompatibilityModeActive = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) checkLocationPermission() else findViewById<SwitchMaterial>(R.id.switchAmHere).isChecked = false
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        startLiveStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discovery)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // 1. Setup RecyclerView
        adapter = NearbyAdapter(nearbyUsersList, myHobbies) { selectedUser -> sendInterest(selectedUser) }
        val rv = findViewById<RecyclerView>(R.id.rvNearby)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // 2. Setup Radar Controls
        val slider = findViewById<Slider>(R.id.rangeSlider)
        val tvRange = findViewById<TextView>(R.id.tvRangeLabel)
        slider.addOnChangeListener { _, value, _ -> tvRange.text = "Search Range: ${value.toInt()}m" }

        findViewById<SwitchMaterial>(R.id.switchAmHere).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkLocationPermission() else stopLiveStatus()
        }

        // ADDED: Compatibility Toggle Switch UI Binding Action
        val switchComp = findViewById<SwitchMaterial>(R.id.switchCompatibilityMode)
        switchComp.setOnCheckedChangeListener { _, isChecked ->
            isCompatibilityModeActive = isChecked

            // Update Firestore instantly so other phones know you are looking
            auth.currentUser?.uid?.let { uid ->
                db.collection("users").document(uid).update("isCompatibilityModeActive", isChecked)
            }
        }

        // 3. Persistent Inbox Intent Bindings
        findViewById<ImageButton>(R.id.btnInbox).setOnClickListener {
            startActivity(Intent(this, MatchesInboxActivity::class.java))
        }

        // Fetch user preferences configuration on startup to validate compatibility switch conditions
        checkUserHobbiesEligibility()

        // 4. Global system tracers
        listenForMatches()
        listenForInboundInterests()
        listenForGlobalMessages()
    }

    // ADDED: Verification algorithm gate confirming eligibility for Compatibility Mode
    private fun checkUserHobbiesEligibility() {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            if (user != null) {
                myHobbies = user.hobbies
                adapter.updateMyHobbies(myHobbies)
                val switchComp = findViewById<SwitchMaterial>(R.id.switchCompatibilityMode)

                if (myHobbies.size >= 10) {
                    switchComp.isEnabled = true
                    switchComp.text = "Compatibility Matching Mode (Active)"
                } else {
                    switchComp.isEnabled = false
                    switchComp.isChecked = false
                    switchComp.text = "Compatibility Mode Locked (${myHobbies.size}/10 Hobbies Picked)"
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startLiveStatus()
    }

    private fun startLiveStatus() {
        val uid = auth.currentUser?.uid ?: return
        val contextText = findViewById<EditText>(R.id.etContext).text.toString().trim()
        val rangeMeters = findViewById<Slider>(R.id.rangeSlider).value.toInt()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val updates = hashMapOf<String, Any>(
                    "isAvailable" to true,
                    "lastLat" to location.latitude,
                    "lastLng" to location.longitude,
                    "locationContext" to contextText,
                    "availableUntil" to System.currentTimeMillis() + (20 * 60 * 1000),
                    "isCompatibilityModeActive" to isCompatibilityModeActive // Broadcast your mode
                )
                db.collection("users").document(uid).update(updates).addOnSuccessListener {
                    listenForNearbyUsers(location.latitude, location.longitude, rangeMeters)
                }
            }
        }
    }

    private fun listenForNearbyUsers(myLat: Double, myLng: Double, rangeMeters: Int) {
        radarListener?.remove()

        radarListener = db.collection("users")
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("RADAR", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val superMatches = mutableListOf<User>()
                val regularUsers = mutableListOf<User>()

                snapshots?.forEach { doc ->
                    val user = doc.toObject(User::class.java)
                    if (user != null && user.uid != auth.currentUser?.uid) {
                        val dist = FloatArray(1)
                        Location.distanceBetween(myLat, myLng, user.lastLat, user.lastLng, dist)

                        if (dist[0] <= rangeMeters) {

                            // Check if BOTH users have Compatibility Mode ON
                            if (isCompatibilityModeActive && user.isCompatibilityModeActive) {
                                val sharedInterestsCount = user.hobbies.intersect(myHobbies.toSet()).size

                                if (sharedInterestsCount >= 7) {
                                    // Put them in the VIP list!
                                    superMatches.add(user)

                                    // Send a notification if we haven't already alerted the user about them
                                    if (!notifiedCompatibleUsers.contains(user.uid)) {
                                        notifiedCompatibleUsers.add(user.uid)
                                        triggerLocalNotification("High Compatibility! ${user.firstName} is nearby with $sharedInterestsCount shared interests!")
                                    }
                                    return@forEach // Skip adding to regular list
                                }
                            }

                            // If they aren't a super match, they go to the regular list
                            regularUsers.add(user)
                        }
                    }
                }

                // Combine the lists: Super Matches go exactly at the top of the feed
                val combinedList = superMatches + regularUsers
                adapter.updateList(combinedList)
            }
    }

    private fun sendInterest(targetUser: User) {
        val myUid = auth.currentUser?.uid ?: return
        val targetUid = targetUser.uid
        val matchId = if (myUid < targetUid) "${myUid}_${targetUid}" else "${targetUid}_${myUid}"

        val interestData = hashMapOf(
            "from" to myUid,
            "to" to targetUid,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("interests").document(matchId)
            .collection("taps").document(myUid).set(interestData)
            .addOnSuccessListener { checkForMatch(matchId, targetUser) }
    }

    private fun checkForMatch(matchId: String, targetUser: User) {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("interests").document(matchId).collection("taps").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.size() >= 2) {
                    val matchData = hashMapOf(
                        "users" to listOf(myUid, targetUser.uid),
                        "timestamp" to System.currentTimeMillis(),
                        "expiresAt" to System.currentTimeMillis() + (20 * 60 * 1000)
                    )
                    db.collection("matches").document(matchId).set(matchData)
                } else {
                    Toast.makeText(this, "Interest sent to ${targetUser.firstName}!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun listenForMatches() {
        val myUid = auth.currentUser?.uid ?: return
        matchListener = db.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val matchTimestamp = dc.document.getLong("timestamp") ?: 0L
                        if (matchTimestamp > appSessionStartTime) {
                            val matchId = dc.document.id
                            val users = dc.document["users"] as? List<*>
                            val stringUsers = users?.filterIsInstance<String>() ?: emptyList()
                            val partnerUid = stringUsers.firstOrNull { it != myUid }

                            if (partnerUid != null) {
                                showMatchOverlay(partnerUid, matchId)
                            }
                        }
                    }
                }
            }
    }

    private fun listenForInboundInterests() {
        val myUid = auth.currentUser?.uid ?: return

        inboundTapListener = db.collectionGroup("taps")
            .whereEqualTo("to", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (snapshots.metadata.isFromCache) return@addSnapshotListener

                snapshots.documentChanges.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        if (!snapshots.metadata.hasPendingWrites()) {
                            val senderUid = dc.document.getString("from") ?: return@forEach

                            if (senderUid != myUid) {
                                db.collection("users").document(senderUid).get().addOnSuccessListener { uDoc ->
                                    val senderName = uDoc.getString("firstName") ?: "Someone"
                                    triggerLocalNotification(senderName)
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun triggerLocalNotification(name: String) {
        val intent = Intent(this, DiscoveryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Interest!")
            .setContentText("$name is interested in your profile right now!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Radar Activity Alerts"
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showMatchOverlay(partnerUid: String, matchId: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.match_overlay)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val ivPartner = dialog.findViewById<ImageView>(R.id.ivMatchUser2)
        val ivMe = dialog.findViewById<ImageView>(R.id.ivMatchUser1)
        val btnChat = dialog.findViewById<Button>(R.id.btnStartChat)
        val btnDismiss = dialog.findViewById<Button>(R.id.btnMatchDismiss)

        db.collection("users").document(partnerUid).get().addOnSuccessListener { doc ->
            val partner = doc.toObject(User::class.java)
            if (partner?.imageUrls?.isNotEmpty() == true) {
                Glide.with(this).load(partner.imageUrls[0]).circleCrop().into(ivPartner)
            }
        }

        auth.currentUser?.uid?.let { myUid ->
            db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
                val me = doc.toObject(User::class.java)
                if (me?.imageUrls?.isNotEmpty() == true) {
                    Glide.with(this).load(me.imageUrls[0]).circleCrop().into(ivMe)
                }
            }
        }

        btnChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply { putExtra("MATCH_ID", matchId) }
            startActivity(intent)
            dialog.dismiss()
        }

        btnDismiss.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun stopLiveStatus() {
        radarListener?.remove()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("isAvailable", false)
        adapter.updateList(emptyList())
    }

    override fun onDestroy() {
        super.onDestroy()
        radarListener?.remove()
        matchListener?.remove()
        inboundTapListener?.remove()
        globalChatListener?.remove()
    }

    private fun listenForGlobalMessages() {
        val myUid = auth.currentUser?.uid ?: return

        globalChatListener = db.collectionGroup("messages")
            .whereEqualTo("receiverId", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (snapshots.metadata.isFromCache) return@addSnapshotListener

                snapshots.documentChanges.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val timestamp = dc.document.getLong("timestamp") ?: 0L
                        val senderId = dc.document.getString("senderId") ?: ""
                        val messageText = dc.document.getString("text") ?: "Sent a message"

                        if (timestamp > appSessionStartTime && senderId != myUid) {
                            triggerLocalNotification("Your match: $messageText")
                        }
                    }
                }
            }
    }
}