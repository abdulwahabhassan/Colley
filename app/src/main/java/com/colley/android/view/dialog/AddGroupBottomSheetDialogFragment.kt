package com.colley.android.view.dialog

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.colley.android.adapter.AddGroupMembersRecyclerAdapter
import com.colley.android.contract.OpenDocumentContract
import com.colley.android.databinding.BottomSheetDialogFragmentAddGroupBinding
import com.colley.android.model.GroupChat
import com.colley.android.model.GroupMessage
import com.colley.android.model.NewGroup
import com.colley.android.model.User
import com.colley.android.view.fragment.GroupInfoFragment
import com.colley.android.wrapper.WrapContentLinearLayoutManager
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage

class AddGroupBottomSheetDialogFragment (
    private val groupContext: Context,
    private val groupView: View,
    private val homeFabListener: AddGroupFabListener
        ) : BottomSheetDialogFragment(), AddGroupMembersRecyclerAdapter.ItemClickedListener {

    private var _binding: BottomSheetDialogFragmentAddGroupBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private lateinit var recyclerView: RecyclerView
    private var adapter: AddGroupMembersRecyclerAdapter? = null
    private var selectedMembersCount = 0
    private val selectedMembersList = arrayListOf<String>()
    private val uid: String
        get() = currentUser.uid
    private var groupImageUri: Uri? = null
    private val openDocument = registerForActivityResult(OpenDocumentContract()) { uri ->
        if(uri != null) {
            groupImageUri = uri
            displaySelectedPhoto(groupImageUri!!)
        }
    }
    interface AddGroupFabListener {
        fun enableFab(enabled: Boolean)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        //on dismiss of dialog, re-enable fab button
        homeFabListener.enableFab(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetDialogFragmentAddGroupBinding
            .inflate(inflater, container, false)
        recyclerView = binding.addGroupMembersRecyclerView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize database and current user
        dbRef = Firebase.database.reference
        currentUser = Firebase.auth.currentUser!!

        //automatically add user to the list of members by default
        selectedMembersList.add(uid)

        //get a query reference to group members
        val usersRef =  dbRef.child("users")

        //the FirebaseRecyclerAdapter class and options come from the FirebaseUI library
        //build an options to configure adapter. setQuery takes firebase query to listen to and a
        //model class to which snapShots should be parsed
        val options = FirebaseRecyclerOptions.Builder<User>()
            .setQuery(usersRef, User::class.java)
            .setLifecycleOwner(viewLifecycleOwner)
            .build()

        adapter = AddGroupMembersRecyclerAdapter(
            options,
            currentUser,
            this,
            groupContext)

        recyclerView.layoutManager = WrapContentLinearLayoutManager(
            groupContext,
            LinearLayoutManager.VERTICAL,
            false)
        recyclerView.adapter = adapter


        with(binding) {
            selectGroupPhotoButton.setOnClickListener {
                openDocument.launch(arrayOf("image/*"))
            }

            createGroupButton.setOnClickListener {
                val groupName = binding.addGroupNameEditText.text.toString()
                val groupDescription = binding.addGroupDescriptionEditText.text.toString()

                if(TextUtils.isEmpty(groupName.trim())) {
                    Toast.makeText(requireContext(),
                        "Group name cannot be empty",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                } else {
                    createGroup(groupName, groupDescription, groupImageUri)
                }

            }
        }

    }

    private fun createGroup(groupName: String, groupDescription: String, groupImageUri: Uri?) {

        //Disable editing during creation
        setEditingEnabled(false)

        //make instance of new group
       val newGroup = NewGroup(
           name = groupName,
           description = groupDescription,
           groupAdmins = arrayListOf(uid),
           members = selectedMembersList
       )

        //create and push new group to database, retrieve key and add it as groupId
        dbRef.child("groups").push()
            .setValue(newGroup, DatabaseReference.CompletionListener { error, ref ->
            //disable home fab button while writing to database
            homeFabListener.enableFab(false)
        //in case of error
            if (error != null) {
                Toast.makeText(context, "Unable to create group", Toast.LENGTH_LONG).show()
                setEditingEnabled(true)
                return@CompletionListener
            }
            //after creating group, retrieve its key on the database and set it as its id
                val key = ref.key
                dbRef.child("groups").child(key!!).child("groupId").setValue(key)

            //create a reference to group-messages on database and set initial message to "Welcome
                //to the group"
                dbRef.child("group-messages").child(key).push()
                    .setValue(GroupMessage(uid, "I welcome everyone to the group"))
            //update group's recent message
                dbRef.child("group-messages").child("recent-message").child(key)
                    .setValue(GroupMessage(uid, "I welcome everyone to the group"))

            //if a groupPhoto is selected, retrieve its uri and define a storage path for it
            if (groupImageUri != null) {
                val storageReference = Firebase.storage
                    .getReference(key)
                    .child("$uid-group-photo")

                //upload the photo to storage
                putImageInStorage(storageReference, groupImageUri, key, groupName)
            } else {
                //simply update database without group photo
                    val url = null
                dbRef.child("groups-id-name-photo").child(key)
                    .setValue(GroupChat(key, groupName, url))
            }
                Snackbar.make(
                    groupView,
                    "Group created successfully! Uploading to database..",
                    Snackbar.LENGTH_LONG).show()

            //update each users list of groups they are a member of
            selectedMembersList.forEach {
                //run a transaction to update each user's list of groups they are a member of
                dbRef.child("user-groups").child(it).runTransaction(
                    object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            //retrieve the database list
                            val listOfGroups = currentData.getValue<ArrayList<String>>()
                            //if the database list returns null, set it to an array containing
                            //the group's id
                            return if (listOfGroups == null) {
                                currentData.value = arrayListOf(key)
                                Transaction.success(currentData)
                            } else {
                                //add group's id to the list of group's this members belongs to
                                listOfGroups.add(key)
                                //set database list to this update list and return it
                                currentData.value = listOfGroups
                                Transaction.success(currentData)
                            }

                        }

                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            currentData: DataSnapshot?
                        ) {}

                    }
                )
            }
            //dismiss bottom sheet dialog
            this.dismiss()
        })
    }

    //used to disable fields during creation
    private fun setEditingEnabled(enabled: Boolean) {
        with(binding) {
            addGroupNameEditText.isEnabled = enabled
            addGroupDescriptionEditText.isEnabled = enabled
            selectGroupPhotoButton.isEnabled = enabled
            createGroupButton.isEnabled = enabled
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onItemClick(user: User) {
        
    }

    //interface method to update selected group members count when a member is selected
    //this method also updates the selected members list that will be sent to the database
    override fun onItemSelected(userId: String, view: CheckBox) {
        if (view.isChecked) {
            selectedMembersCount++
            binding.selectedMemberCountTextView.text = selectedMembersCount.toString()
            if (!selectedMembersList.contains(userId)) {
                selectedMembersList.add(userId)
            }
        } else {
            selectedMembersCount--
            binding.selectedMemberCountTextView.text = selectedMembersCount.toString()
            if (selectedMembersList.contains(userId)) {
                selectedMembersList.remove(userId)
            }
        }
        when (selectedMembersCount) {
            0 -> binding.selectedMemberCountTextView.visibility = GONE
            else -> binding.selectedMemberCountTextView.visibility = VISIBLE
        }
    }

    private fun putImageInStorage(
        storageReference: StorageReference,
        groupImageUri: Uri?,
        key: String,
        groupName: String
    ) {
        // First upload the image to Cloud Storage
        storageReference.putFile(groupImageUri!!)
            .addOnSuccessListener(
                requireActivity()
            ) { taskSnapshot -> // After the image loads, get a public downloadUrl for the image
                // and add it to database
                taskSnapshot.metadata!!.reference!!.downloadUrl
                    .addOnSuccessListener { uri ->
                        dbRef.child("groups-id-name-photo").child(key)
                            .setValue(GroupChat(key, groupName, uri.toString()))
                            .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(
                                    groupContext,
                                    "Group photo uploaded successfully",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(
                                    groupContext,
                                    "Photo uploaded failed",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
            }
    }

    //Display selected photo
    private fun displaySelectedPhoto(groupImageUri: Uri) {
        Glide.with(groupContext).load(groupImageUri).into(binding.addGroupImageView)
    }
}