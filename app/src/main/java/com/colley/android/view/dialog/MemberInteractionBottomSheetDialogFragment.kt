package com.colley.android.view.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.colley.android.R
import com.colley.android.databinding.BottomSheetDialogFragmentMemberInteractionBinding
import com.colley.android.glide.GlideImageLoader
import com.colley.android.model.PrivateMessage
import com.colley.android.model.Profile
import com.colley.android.view.fragment.GroupInfoFragmentDirections
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class MemberInteractionBottomSheetDialogFragment(
    private val parentContext: Context,
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDialogFragmentMemberInteractionBinding? = null
    private val binding get() = _binding
    private lateinit var dbRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private val uid: String
        get() = currentUser.uid

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        _binding = BottomSheetDialogFragmentMemberInteractionBinding
            .inflate(inflater, container, false)
        return binding?.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //retrieve member id from arguments
        val bundledMemberId = arguments?.getString("memberIdKey")

        //initialize database and current user
        dbRef = Firebase.database.reference
        currentUser = Firebase.auth.currentUser!!

        if (bundledMemberId != null) {

            //get and set member name
            dbRef.child("profiles").child(bundledMemberId).get()
                .addOnSuccessListener { dataSnapshot ->
                    val memberProfile = dataSnapshot.getValue<Profile>()
                    if (memberProfile != null) {
                        binding?.groupMemberName?.text =
                            "${memberProfile.name}${
                                if (memberProfile.school.isNullOrBlank()
                                        .not()
                                ) " , ${memberProfile.school}" else ""
                            }"
                    }
                }

            //get and load member photo
            dbRef.child("photos").child(bundledMemberId).get()
                .addOnSuccessListener { dataSnapshot ->
                    val memberPhoto = dataSnapshot.getValue<String>()
                    if (memberPhoto == null) {
                        binding?.groupMemberImageView?.let {
                            Glide.with(parentContext).load(R.drawable.ic_person_light_pearl)
                                .into(it)
                            binding?.photoProgressBar?.visibility = GONE
                        }
                    } else {
                        val options = RequestOptions()
                            .error(R.drawable.ic_downloading)
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

                        binding?.groupMemberImageView?.visibility = View.VISIBLE
                        //using custom glide image loader to indicate progress in time
                        GlideImageLoader(
                            binding?.groupMemberImageView, binding
                                ?.photoProgressBar
                        ).load(memberPhoto, options);
                    }
                }

        }

        //show message text box when user click "send message"
        binding?.sendMessageTextView?.setOnClickListener {
            binding?.editMessageTextInputLayout?.visibility = VISIBLE
            binding?.sendButton?.visibility = VISIBLE
            binding?.sendMessageTextView?.visibility = GONE
        }

        //show profile when clicked
        binding?.viewProfileTextView?.setOnClickListener {
            val action = bundledMemberId?.let { it1 ->
                GroupInfoFragmentDirections.actionGroupInfoFragmentToUserInfoFragment(
                    bundledMemberId
                )
            }
            if (action != null) {
                findNavController().navigate(action)
            }
        }

        //send message when clicked
        binding?.sendButton?.setOnClickListener {

            if (bundledMemberId != null) {
                if (binding?.editMMessageEditText?.text?.trim()?.toString() != "") {
                    //create a reference for the message on user's messages location and retrieve its
                    //key with which to update other locations that should have a ref to the message
                    val key = dbRef.child("user-messages").child(uid).child(bundledMemberId)
                        .push().key

                    if (key != null) {
                        val privateMessage = PrivateMessage(
                            fromUserId = uid,
                            toUserId = bundledMemberId,
                            text = binding?.editMMessageEditText?.text.toString(),
                            messageId = key
                        )

                        //used to update multiple paths in the database
                        //here we save a copy of the message to both the sender and receiver's path
                        val childUpdates = hashMapOf<String, Any>(
                            "/user-messages/$uid/$bundledMemberId/$key" to privateMessage,
                            "/user-messages/recent-message/$uid/$bundledMemberId" to privateMessage,
                            "/user-messages/$bundledMemberId/$uid/$key" to privateMessage,
                            "/user-messages/recent-message/$bundledMemberId/$uid" to privateMessage
                        )

                        //the value of the recent message is used when displaying a user's private
                        //messages from different colleagues, so we make a reference for this to
                        //make it easier to retrieve from the database

                        dbRef.updateChildren(childUpdates).addOnSuccessListener {
                            Toast.makeText(parentContext, "Message sent", Toast.LENGTH_SHORT)
                                .show()
                        }

                        binding?.editMMessageEditText?.setText("")
                    } else {
                        Toast.makeText(parentContext, "Unsuccessful", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(parentContext, "Can't send empty message", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            //dismiss dialog fragment
            this.dismiss()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}