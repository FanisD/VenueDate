package com.example.venuedate

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val firstName: String = "",
    val age: Int = 0,
    val gender: String = "",
    val interestedIn: String = "",
    val city: String = "",
    val occupation: String = "",
    val vibeTag: String = "",
    val hobbies: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val email: String = "",
    val currentVenueId: String? = null,
    val lastLat: Double = 0.0,
    val lastLng: Double = 0.0,
    val availableUntil: Long = 0,
    val isCompatibilityModeActive: Boolean = false,
    val blockedUsers: List<String> = emptyList()
)