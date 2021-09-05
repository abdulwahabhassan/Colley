package com.colley.android.view.dialog

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import com.bumptech.glide.Glide
import com.colley.android.contract.OpenDocumentContract
import com.colley.android.databinding.FragmentNewPostBottomShetDialogBinding
import com.colley.android.model.Post
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class NewPostBottomSheetDialogFragment(
    private val parentContext: Context,
    private val postsView: View
) : BottomSheetDialogFragment() {

    private var _binding: FragmentNewPostBottomShetDialogBinding? = null
    private val binding get() = _binding
    private lateinit var dbRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private var postImageUri: Uri? = null
    private var body: String? = null
    private var location: String? = null
    private val uid: String
        get() = currentUser.uid
    private val openDocument = registerForActivityResult(OpenDocumentContract()) { uri ->
        if(uri != null) {
            postImageUri = uri
            displaySelectedPhoto(postImageUri!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNewPostBottomShetDialogBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize database and current user
        dbRef = Firebase.database.reference
        currentUser = Firebase.auth.currentUser!!

        binding?.addPhotoButton?.setOnClickListener {
            openDocument.launch(arrayOf("image/*"))
        }

        binding?.postButton?.setOnClickListener {


            //Disable editing during creation
            setEditingEnabled(false)

            //if location is not empty, set to trimmed text
            if (binding?.locationEditText?.text.toString().trim() != "") {
                location = binding?.locationEditText?.text.toString().trim()
            }

            if (binding?.postBodyEditText?.text.toString().trim() != "") {
                body = binding?.postBodyEditText?.text.toString().trim()
            }

            val df: DateFormat = SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss")
            val date: String = df.format(Calendar.getInstance().time)

            //if fields are empty, do not upload issue to database
            if (body == null && postImageUri == null) {
                Toast.makeText(parentContext, "You cannot make an empty post",
                    Toast.LENGTH_SHORT).show()
                setEditingEnabled(true)
                return@setOnClickListener
            } else {
                //make instance of new post
                val post = Post(
                    userId = uid,
                    timeStamp = date,
                    location = location,
                    text = body
                )
                createNewPost(post)
            }

        }
    }

    private fun createNewPost(post: Post) {

        //create and push new post to database, retrieve key and add it as postId
        dbRef.child("posts").push().setValue(post, DatabaseReference.CompletionListener {
                error, ref ->
            //in case of error
            if (error != null) {
                Toast.makeText(parentContext, "Unable to create post", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Unable to write post to database.", error.toException())
                setEditingEnabled(true)
                return@CompletionListener
            }
            //after creating post, retrieve its key on the database and set it as its id
            val key = ref.key
            dbRef.child("posts").child(key!!).child("postId").setValue(key)

            //if a post image is selected, retrieve its uri and define a storage path for it
            if (postImageUri != null) {
                val storageReference = Firebase.storage
                    .getReference(key)
                    .child("$uid-post-photo")

                //upload the photo to storage
                putImageInStorage(storageReference, postImageUri, key)
            }

            Snackbar.make(postsView, "Post created successfully!", Snackbar.LENGTH_LONG).show()

            //dismiss bottom sheet dialog
            this.dismiss()
        })

    }

    private fun putImageInStorage(
        storageReference: StorageReference,
        postImageUri: Uri?,
        key: String
    ) {
        // First upload the image to Cloud Storage
        storageReference.putFile(postImageUri!!)
            .addOnSuccessListener(
                requireActivity()
            ) { taskSnapshot -> // After the image loads, get a public downloadUrl for the image
                // and set it as the post image
                taskSnapshot.metadata!!.reference!!.downloadUrl
                    .addOnSuccessListener { uri ->
                        dbRef.child("posts").child(key).child("image").setValue(uri.toString())
                    }
            }
            .addOnFailureListener(requireActivity()) { e ->
                Log.w(TAG, "Image upload task was unsuccessful.", e)
            }
    }

    //Display selected photo
    private fun displaySelectedPhoto(postImageUri: Uri) {
        //make view visible
        binding?.postImageView?.visibility = VISIBLE
        //load selected image into view
        binding?.postImageView?.let { Glide.with(parentContext).load(postImageUri).into(it) }
    }


    //used to disable fields during creation
    private fun setEditingEnabled(enabled: Boolean) {
        binding?.postBodyEditText?.isEnabled = enabled
        binding?.locationEditText?.isEnabled = enabled
        binding?.postButton?.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG = "NewPostDialog"
    }
}