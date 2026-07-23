package com.example.venuedate

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlin.jvm.java

class ProfileSetupActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val imageUris = arrayOfNulls<Uri>(5)
    private val currentUrls = arrayOfNulls<String>(5) // Tracks existing photos so they aren't overwritten with blanks
    private var currentSlot = 0
    private val selectedHobbies = mutableListOf<String>()

    private var isEditMode = false

    private val hobbyList = listOf(
        "Craft Beer", "Cocktails", "Wine Tasting", "Karaoke", "Dancing", "Live Music", "Pub Quizzes", "People Watching", "Electronic Music", "Jazz Clubs",
        "Hiking", "Gym", "Yoga", "Football", "Running", "Basketball", "Cycling", "Swimming", "Combat Sports", "Sports",
        "Photography", "Painting", "Writing", "Cooking", "Fashion", "Architecture", "Street Art", "Pottery", "Music", "Theater",
        "Gaming", "Board Games", "Anime", "Movies", "Reading", "Shows", "Comics", "Coding", "Cosplay", "Astrology",
        "Travel", "Coffee", "Veganism", "Outdoorsy", "Dogs", "Cats", "Meditation", "Psychology", "Indoorsy", "Volunteering"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUris[currentSlot] = uri
            val imageViewId = resources.getIdentifier("iv$currentSlot", "id", packageName)
            val imageView = findViewById<ImageView>(imageViewId)

            // 1. Set the newly picked image
            imageView.setImageURI(uri)

            // 2. THE FIX: Explicitly remove the XML imageTintList!
            imageView.imageTintList = null
            imageView.clearColorFilter()
            imageView.background = null

            // 3. Make the photo zoom in perfectly to fill the card
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        val tvTitle = findViewById<TextView>(R.id.tvProfileTitle)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogOut = findViewById<Button>(R.id.btnLogOut)
        val btnDelete = findViewById<Button>(R.id.btnDeleteAccount)
        val btnBlockedUsers = findViewById<Button>(R.id.btnBlockedUsers)

        // Setup Spinners
        val genders = arrayOf("Male", "Female", "Other")
        findViewById<Spinner>(R.id.spinnerGender).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        val interests = arrayOf("Men", "Women", "Everyone")
        findViewById<Spinner>(R.id.spinnerInterestedIn).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, interests)

        val vibes = arrayOf("Open to Chat", "Looking for a Spark", "Good Vibes Only")
        findViewById<Spinner>(R.id.spinnerVibe).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vibes)

        // Image Click Listeners
        for (i in 0..4) {
            val id = resources.getIdentifier("iv$i", "id", packageName)
            findViewById<ImageView>(id).setOnClickListener {
                currentSlot = i
                pickImageLauncher.launch("image/*")
            }
        }

        // Hobby Chips Setup
        val chipGroup = findViewById<ChipGroup>(R.id.hobbyChipGroup)
        val tvCount = findViewById<TextView>(R.id.tvHobbyCount)
        hobbyList.forEach { hobby ->
            val chip = Chip(this)
            chip.text = hobby
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedHobbies.size < 10) selectedHobbies.add(hobby)
                    else chip.isChecked = false
                } else {
                    selectedHobbies.remove(hobby)
                }
                tvCount.text = "Select Interests (${selectedHobbies.size}/10)"
            }
            chipGroup.addView(chip)
        }

        // --- EDIT MODE ACTIVATION ---
        if (isEditMode) {
            tvTitle.text = "Edit Profile"
            btnSave.text = "Save Changes"
            btnLogOut.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
            btnBlockedUsers.visibility = View.VISIBLE
            loadExistingData()
        }

        btnBlockedUsers.setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        btnSave.setOnClickListener {
            val name = findViewById<EditText>(R.id.etFirstName).text.toString().trim()
            val ageStr = findViewById<EditText>(R.id.etAge).text.toString().trim()
            val city = findViewById<EditText>(R.id.etCity).text.toString().trim()

            // You must either be uploading a new main photo, or already have one saved from before
            val hasMainPhoto = imageUris[0] != null || !currentUrls[0].isNullOrEmpty()

            if (name.isNotEmpty() && ageStr.isNotEmpty() && city.isNotEmpty() && hasMainPhoto) {
                uploadAllPhotos(name, ageStr.toInt(), city)
            } else {
                Toast.makeText(this, "Main photo, Name, Age, and City are required", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        btnDelete.setOnClickListener { confirmAndDeleteAccount() }
    }

    private fun loadExistingData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener

            findViewById<EditText>(R.id.etFirstName).setText(user.firstName)
            findViewById<EditText>(R.id.etAge).setText(user.age.toString())
            findViewById<EditText>(R.id.etCity).setText(user.city)
            findViewById<EditText>(R.id.etOccupation).setText(user.occupation)

            setSpinnerSelection(R.id.spinnerGender, user.gender)
            setSpinnerSelection(R.id.spinnerInterestedIn, user.interestedIn)
            setSpinnerSelection(R.id.spinnerVibe, user.vibeTag)

            // Re-check existing hobbies
            val chipGroup = findViewById<ChipGroup>(R.id.hobbyChipGroup)
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as Chip
                if (user.hobbies.contains(chip.text.toString())) {
                    chip.isChecked = true
                }
            }

            // Load existing images into the layout
            user.imageUrls.forEachIndexed { index, url ->
                if (index < 5) {
                    currentUrls[index] = url
                    val imageViewId = resources.getIdentifier("iv$index", "id", packageName)
                    val imageView = findViewById<ImageView>(imageViewId)

                    // Force remove the tint and background for existing downloaded photos too
                    imageView.imageTintList = null // MUST have this for app:tint
                    imageView.clearColorFilter()
                    imageView.background = null

                    Glide.with(this)
                        .load(url)
                        // THE FIX: Tell Glide to skip the cache and always fetch the newest photo
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .centerCrop()
                        .into(imageView)
                }
            }
        }
    }

    private fun setSpinnerSelection(spinnerId: Int, value: String) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun uploadAllPhotos(name: String, age: Int, city: String) {
        val uid = auth.currentUser?.uid ?: return
        val indexesToUpload = mutableListOf<Int>()

        // Check which specific slots have brand new images
        for (i in 0..4) {
            if (imageUris[i] != null) indexesToUpload.add(i)
        }

        // If they didn't pick any new images, jump straight to saving text data
        if (indexesToUpload.isEmpty()) {
            saveUserData(name, age, city)
            return
        }

        var completed = 0
        indexesToUpload.forEach { i ->
            val ref = storage.reference.child("profiles/$uid/img_$i.jpg")
            ref.putFile(imageUris[i]!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    currentUrls[i] = downloadUri.toString() // Replace the old URL with the new one
                    completed++
                    if (completed == indexesToUpload.size) {
                        saveUserData(name, age, city)
                    }
                }
            }
        }
    }

    private fun saveUserData(name: String, age: Int, city: String) {
        val uid = auth.currentUser?.uid ?: return

        // Strip out any nulls or blank slots
        val finalUrls = currentUrls.filterNotNull().filter { it.isNotEmpty() }

        val userMap = hashMapOf(
            "uid" to uid,
            "firstName" to name,
            "age" to age,
            "gender" to findViewById<Spinner>(R.id.spinnerGender).selectedItem.toString(),
            "interestedIn" to findViewById<Spinner>(R.id.spinnerInterestedIn).selectedItem.toString(),
            "city" to city,
            "occupation" to findViewById<EditText>(R.id.etOccupation).text.toString().trim(),
            "vibeTag" to findViewById<Spinner>(R.id.spinnerVibe).selectedItem.toString(),
            "hobbies" to selectedHobbies,
            "imageUrls" to finalUrls,
            "email" to auth.currentUser?.email
        )

        // SetOptions.merge() is CRITICAL here so we don't erase location logic or block lists!
        db.collection("users").document(uid).set(userMap, SetOptions.merge()).addOnSuccessListener {
            if (isEditMode) {
                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                finish() // Close edit mode and go back to Radar
            } else {
                startActivity(Intent(this, DiscoveryActivity::class.java))
                finish() // Standard onboarding forward progression
            }
        }
    }

    private fun confirmAndDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure? This action is permanent and cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val user = auth.currentUser
                val uid = user?.uid ?: return@setPositiveButton

                Toast.makeText(this, "Deleting account and cleaning data...", Toast.LENGTH_SHORT).show()

                // 1. Immediately redirect to login screen
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })

                // 2. Delete Storage Photos
                for (i in 0..4) {
                    storage.reference.child("profiles/$uid/img_$i.jpg").delete()
                }

                // 3. Clean up Matches where this user is listed
                db.collection("matches").whereArrayContains("users", uid).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }

                // 4. Clean up Reports created by this user
                db.collection("reports").whereEqualTo("reporter", uid).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }

                // 5. Clean up Taps/Interests sent by or to this user
                db.collectionGroup("taps").whereEqualTo("from", uid).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }
                db.collectionGroup("taps").whereEqualTo("to", uid).get().addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }

                // 6. Delete Main User Document & Auth Profile
                db.collection("users").document(uid).delete().addOnCompleteListener {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}