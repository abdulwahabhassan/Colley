package com.colley.android.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getColor
import com.colley.android.R
import com.colley.android.databinding.ActivitySignInBinding
import com.colley.android.model.Profile
import com.colley.android.model.User
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private val signInIntent = AuthUI.getInstance()
        .createSignInIntentBuilder()
        .setAvailableProviders(listOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )) //these are lists of authentication providers we defined for the app as specified in
        //the firebase project and allow firebase auth UI to build respective UI for each signup screen.
        //Each of these will represent a button
        .setTheme(R.style.Theme_Colley)
        .build()

    //register a callback onSignInResult for the result that is returned from a launched contract with
    //with a given intent
    //FirebaseAuthUIActivityResultContract().This is a contract to return an activity result
    //from an intent and pass the result to the registered callback
    private val signIn: ActivityResultLauncher<Intent> =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { result ->
            this.onSignInResult(result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //init Realtime Database
        dbRef = Firebase.database.reference

        //init Firebase Auth
        auth = Firebase.auth

        ///authenticate user
        authenticateUser()

        binding.signInButton.setOnClickListener {
            signIn.launch(signInIntent)
            it.visibility = INVISIBLE
        }


        binding.continueButton.setOnClickListener {
            it.isEnabled = false
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun authenticateUser() {
        if (auth.currentUser == null) {
            signIn.launch(signInIntent)
        } else {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
//            with(binding) {
//                continueButton.visibility = VISIBLE
//            }
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            Snackbar.make(
                binding.root,
                "Signed in as ${auth.currentUser?.email}",
                Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(this, R.color.pearl))
                .show()
            addUserToDataBase()
        } else {
            binding.signInButton.visibility = VISIBLE
        }
    }

    //add user to database only if user doesn't already exist
    private fun addUserToDataBase() {

        dbRef.child("users").child(auth.currentUser?.uid!!).get().addOnSuccessListener {
            dataSnapshot ->
            val user = dataSnapshot.getValue(User::class.java)
            if ( user == null) {
                dbRef.child("users").child(auth.currentUser?.uid!!)
                    .setValue(User(auth.currentUser?.uid, auth.currentUser?.email))
//                  with(binding) {
//                        welcomeTextView.text = getString(R.string.welcome_text)
//                        welcomeTextView.visibility = VISIBLE
//                        continueButton.visibility = VISIBLE
//                    }
            }
//            else {
//                with(binding) {
//                    welcomeTextView.text = getString(R.string.welcome_back_text)
//                    welcomeTextView.visibility = VISIBLE
//                    continueButton.visibility = VISIBLE
//                }
//            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }


        dbRef.child("profiles").child(auth.currentUser?.uid!!).addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profile = snapshot.getValue<Profile>()
                    if (profile == null) {
                        dbRef.child("profiles").child(auth.currentUser?.uid!!)
                            .setValue(Profile(auth.currentUser?.displayName))
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
        )
    }

}