package com.example.venuedate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileSetupActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val imageUris = arrayOfNulls<Uri>(5)
    private var currentSlot = 0
    private val selectedHobbies = mutableListOf<String>()

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
            findViewById<ImageView>(imageViewId).setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        for (i in 0..4) {
            val id = resources.getIdentifier("iv$i", "id", packageName)
            findViewById<ImageView>(id).setOnClickListener {
                currentSlot = i
                pickImageLauncher.launch("image/*")
            }
        }

        // Setup Spinners
        val genders = arrayOf("Male", "Female", "Other")
        findViewById<Spinner>(R.id.spinnerGender).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        val interests = arrayOf("Men", "Women", "Everyone")
        findViewById<Spinner>(R.id.spinnerInterestedIn).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, interests)

        val vibes = arrayOf("Open to Chat", "Looking for a Spark", "Good Vibes Only")
        findViewById<Spinner>(R.id.spinnerVibe).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vibes)

        // Hobby Chips
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

        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val name = findViewById<EditText>(R.id.etFirstName).text.toString().trim()
            val age = findViewById<EditText>(R.id.etAge).text.toString().trim()
            val city = findViewById<EditText>(R.id.etCity).text.toString().trim()

            if (name.isNotEmpty() && age.isNotEmpty() && city.isNotEmpty() && imageUris[0] != null) {
                uploadAllPhotos(name, age.toInt(), city)
            } else {
                Toast.makeText(this, "Main photo, Name, Age, and City are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadAllPhotos(name: String, age: Int, city: String) {
        val uid = auth.currentUser?.uid ?: return
        val uploadedUrls = mutableListOf<String>()
        val activeUris = imageUris.filterNotNull()
        var completed = 0

        activeUris.forEachIndexed { index, uri ->
            val ref = storage.reference.child("profiles/$uid/img_$index.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    uploadedUrls.add(downloadUri.toString())
                    completed++
                    if (completed == activeUris.size) saveUserData(name, age, city, uploadedUrls)
                }
            }
        }
    }

    private fun saveUserData(name: String, age: Int, city: String, urls: List<String>) {
        val uid = auth.currentUser?.uid ?: return
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
            "imageUrls" to urls,
            "email" to auth.currentUser?.email
        )

        db.collection("users").document(uid).set(userMap).addOnSuccessListener {
            startActivity(Intent(this, DiscoveryActivity::class.java))
            finish()
        }
    }
}