package com.example.venuedate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // --- AUTO-LOGIN REMOVED FROM HERE ---
        // We no longer call checkProfileStatus() automatically in onCreate.
        // This allows you to see the screen and choose your login method.

        // 1. Google Sign-In Button
        findViewById<SignInButton>(R.id.btnGoogle).setOnClickListener {
            // If there's an old session, sign out first so the Google Account Picker pops up
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        // 2. Email Login Button
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etPassword).text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user?.isEmailVerified == true) {
                            checkProfileStatus(user.uid)
                        } else {
                            Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Manual Email Registration
        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etPassword).text.toString().trim()

            if (email.isNotEmpty() && pass.length >= 6) {
                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        auth.currentUser?.sendEmailVerification()
                        Toast.makeText(this, "Verification email sent!", Toast.LENGTH_LONG).show()
                        auth.signOut()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The loading screen is VISIBLE by default from the XML
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Keep the loading screen up and check their profile!
            checkProfileStatus(currentUser.uid)
        } else {
            // Not logged in? Hide the loading screen so they can type their email
            findViewById<View>(R.id.layoutLoading).visibility = View.GONE
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                checkProfileStatus(auth.currentUser?.uid ?: "")
            }
        }
    }

    private fun checkProfileStatus(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists() && !doc.getString("firstName").isNullOrEmpty()) {
                startActivity(Intent(this, DiscoveryActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this, ProfileSetupActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            // If the network fails, hide the loading screen so they aren't stuck
            findViewById<View>(R.id.layoutLoading).visibility = View.GONE
            Toast.makeText(this, "Network Error: Could not load profile", Toast.LENGTH_SHORT).show()
        }
    }
}