# VenueDate 📍🔥

VenueDate is a real-time, location-based social discovery and dating app for Android. It allows users to "Go Live" in their current vicinity, discover highly compatible profiles nearby, and engage in ephemeral 20-minute chat sessions before deciding to meet up in person.

## 🚀 Key Features

* **Live Radar Discovery:** Users toggle a "Go Live" switch to broadcast their fuzzed location (safe ~110m radius) for exactly 20 minutes.
* **Compatibility Matching:** An intelligent sorting algorithm that analyzes user hobbies. If two users share 7+ interests and both have Compatibility Mode active, they are mutually alerted and pinned to the top of the radar feed.
* **Ephemeral Chat Rooms:** Matches open a secure chat room governed by a live countdown timer. When the 20 minutes expire, the chat locks to encourage real-world interaction.
* **Trust & Safety First:** Built-in reporting and blocking mechanics. Locations are mathematically truncated to prevent precise stalking, and database rules securely restrict data access.

## 🛠️ Tech Stack

* **Platform:** Native Android (Kotlin)
* **Architecture:** Minimum SDK 26, Target SDK 36
* **Backend:** Firebase (Authentication, Firestore Database, Cloud Storage)
* **Location:** Google Play Services Fused Location Provider
* **UI/Images:** Material Design 3, Glide Image Loading

## 🔒 Security & Privacy Notes
* **Data Obfuscation:** ProGuard/R8 is enabled for release builds to prevent reverse engineering.
* **Firestore Security:** Strict rules are enforced limiting read/write access to mutual match documents and user-owned data.
* **No Hard GPS Tracking:** Coordinates are truncated to 3 decimal places before being written to the database.
