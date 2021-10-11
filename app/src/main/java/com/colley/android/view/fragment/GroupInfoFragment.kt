package com.colley.android.view.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.colley.android.R
import com.colley.android.adapter.GroupMembersRecyclerAdapter
import com.colley.android.contract.OpenDocumentContract
import com.colley.android.databinding.FragmentGroupInfoBinding
import com.colley.android.glide.GlideImageLoader
import com.colley.android.model.Profile
import com.colley.android.view.dialog.AddMoreGroupMemberBottomSheetDialogFragment
import com.colley.android.view.dialog.EditGroupAboutBottomSheetDialogFragment
import com.colley.android.view.dialog.EditGroupNameBottomSheetDialogFragment
import com.colley.android.view.dialog.MemberInteractionBottomSheetDialogFragment
import com.colley.android.wrapper.WrapContentLinearLayoutManager
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage


class GroupInfoFragment :
    Fragment(),
    GroupMembersRecyclerAdapter.ItemClickedListener,
    EditGroupAboutBottomSheetDialogFragment.EditGroupAboutListener,
    EditGroupNameBottomSheetDialogFragment.EditGroupNameListener{

    private val args: GroupInfoFragmentArgs by navArgs()
    private var _binding: FragmentGroupInfoBinding? = null
    private val binding get() = _binding!!
    lateinit var recyclerView: RecyclerView
    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private var adapter: GroupMembersRecyclerAdapter? = null
    private var editGroupAboutBottomSheetDialog: EditGroupAboutBottomSheetDialogFragment? = null
    private var addMoreGroupMemberSheetDialog: AddMoreGroupMemberBottomSheetDialogFragment? = null
    private var memberInteractionSheetDialog: MemberInteractionBottomSheetDialogFragment? = null
    private var editGroupNameBottomSheetDialog: EditGroupNameBottomSheetDialogFragment? = null
    val uid: String
        get() = currentUser.uid
    private val openDocument = registerForActivityResult(OpenDocumentContract()) { groupImageUri ->
        if(groupImageUri != null) {
            onImageSelected(groupImageUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentGroupInfoBinding.inflate(inflater, container, false)
        recyclerView = binding.groupMembersRecyclerView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize Realtime Database
        dbRef = Firebase.database.reference

        //initialize authentication
        auth = Firebase.auth

        //initialize currentUser
        currentUser = auth.currentUser!!

        //get and set group name to textview
        getGroupName()

        //get and load group photo to imageview
        getGroupPhoto()

        //get and set group description to textview
        getGroupDescription()

        //get a query reference to group members
        val messagesRef = dbRef.child("groups").child(args.groupId)
            .child("members")

        //the FirebaseRecyclerAdapter class and options come from the FirebaseUI library
        //build an options to configure adapter. setQuery takes firebase query to listen to and a
        //model class to which snapShots should be parsed
        val options = FirebaseRecyclerOptions.Builder<String>()
            .setQuery(messagesRef, String::class.java)
            .setLifecycleOwner(viewLifecycleOwner)
            .build()

        adapter = GroupMembersRecyclerAdapter(
            options,
            currentUser,
            this,
            requireContext(),
            args.groupId)

        recyclerView.layoutManager =
            WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = adapter

        //open dialog with the current group description
        binding.editAboutTextView.setOnClickListener {
            editGroupAboutBottomSheetDialog = EditGroupAboutBottomSheetDialogFragment(
                requireContext(), this)
            editGroupAboutBottomSheetDialog?.arguments = bundleOf(
                "aboutKey" to binding.groupDescriptionTextView.text.toString(),
                "groupIdKey" to args.groupId
            )
            editGroupAboutBottomSheetDialog?.show(childFragmentManager, null)
        }

        //update group photo
        binding.addPhotoButton.setOnClickListener {
            openDocument.launch(arrayOf("image/*"))
        }

        //edit group name
        binding.editGroupNameButton.setOnClickListener {
            editGroupNameBottomSheetDialog =
                EditGroupNameBottomSheetDialogFragment(requireContext(), this)
            editGroupNameBottomSheetDialog?.arguments = bundleOf(
                "groupNameKey" to binding.groupNameTextView.text.toString(),
                "groupIdKey" to args.groupId
            )
            editGroupNameBottomSheetDialog?.show(childFragmentManager, null)
        }

        binding.addGroupMemberTextView.setOnClickListener {
        //show dialog to add group member
                addMoreGroupMemberSheetDialog = AddMoreGroupMemberBottomSheetDialogFragment(
                    requireContext()
                )

                addMoreGroupMemberSheetDialog?.arguments =
                    bundleOf("groupIdKey" to args.groupId)
                addMoreGroupMemberSheetDialog?.show(childFragmentManager, null)
        }

        //to leave a group
        binding.leaveGroupTextView.setOnClickListener {
            dbRef.child("groups").child(args.groupId).child("groupAdmins")
                .addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val admins = snapshot.getValue<ArrayList<String>>()
                        if (admins != null && admins.contains(uid)) {
                            context?.let { context -> Toast.makeText(context,
                                "You cannot leave the group while an admin",
                                Toast.LENGTH_LONG).show()
                            }
                        } else {
                                //check if context is not null to prevent NullPointerException in cases
                                    //where user clicks "leave group" button and back button simultaneously
                            context?.let { context ->
                                //open dialog
                                AlertDialog.Builder(context)
                                    .setMessage("Are you sure you want to leave this group?")
                                    .setPositiveButton("Yes") { dialog, _ ->
                                        //run transaction on database list of group members
                                        dbRef.child("groups").child(args.groupId)
                                            .child("members").runTransaction(
                                            object : Transaction.Handler {
                                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                                    //retrieve the database list which is a mutable data and store in list else
                                                    //return the same data back to database if null
                                                    val list = currentData.getValue<ArrayList<String>>()
                                                        ?: return Transaction.success(currentData)
                                                    //remove the current user from the list if they exist
                                                    if (list.contains(uid)) {
                                                        list.remove(uid)
                                                    }
                                                    //set the value of the database members to the new list
                                                    currentData.value = list
                                                    //return updated list to database
                                                    return Transaction.success(currentData)
                                                }

                                                override fun onComplete(
                                                    error: DatabaseError?,
                                                    committed: Boolean,
                                                    currentData: DataSnapshot?
                                                ) {
                                                    if (committed && error == null) {
                                                        //run transaction to remove group from user's list of groups they belong to
                                                        dbRef.child("user-groups").child(uid).runTransaction(
                                                            object : Transaction.Handler {
                                                                override fun doTransaction(
                                                                    currentData: MutableData
                                                                ): Transaction.Result {
                                                                    //retrieve the database list, if null, return same null value to database
                                                                    val listOfGroups = currentData.getValue<ArrayList<String>>()
                                                                        ?: return Transaction.success(currentData)
                                                                    //remove group's id in the list of group's this members belongs to
                                                                    if (listOfGroups.contains(args.groupId)) {
                                                                        listOfGroups.remove(args.groupId)
                                                                    }
                                                                    //set database list to this update list and return it
                                                                    currentData.value = listOfGroups
                                                                    return Transaction.success(currentData)
                                                                }

                                                                override fun onComplete(
                                                                    error: DatabaseError?,
                                                                    committed: Boolean,
                                                                    currentData: DataSnapshot?
                                                                ) {}

                                                            }
                                                        )
                                                        Snackbar.make(requireView(), "You are no longer a member of this group", Snackbar.LENGTH_LONG).show()
                                                    } else { }
                                                }

                                            }
                                        )
                                        dialog.dismiss()
                                    }.setNegativeButton("No") { dialog, _ -> dialog.dismiss()
                                    }.show()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
            )
        }


    }

    private fun onImageSelected(groupImageUri: Uri) {
        binding.photoProgressBar.visibility = VISIBLE
        val storageReference = Firebase.storage
            .getReference(args.groupId)
            .child("${auth.currentUser?.uid!!}-group-photo")
        putImageInStorage(storageReference, groupImageUri)
    }

    private fun putImageInStorage(storageReference: StorageReference, groupImageUri: Uri) {
        // First upload the image to Cloud Storage
        storageReference.putFile(groupImageUri)
            .addOnSuccessListener(
                requireActivity()
            ) { taskSnapshot -> // After the image loads, get a public downloadUrl for the image
                // and add it to database
                taskSnapshot.metadata!!.reference!!.downloadUrl
                    .addOnSuccessListener { uri ->
                        dbRef.child("groups-id-name-photo").child(args.groupId)
                            .child("groupPhoto").setValue(uri.toString())
                            .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(
                                    requireContext(),
                                    "Unsuccessful",
                                    Toast.LENGTH_SHORT).show()
                                //load group photo
                                getGroupPhoto()
                            } else {
                               Toast.makeText(
                                   requireContext(),
                                   "Unsuccessful",
                                   Toast.LENGTH_LONG).show()
                            }
                        }
                    }
            }
            .addOnFailureListener(requireActivity()) { e ->
                Toast.makeText(requireContext(), "Unsuccessful", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    //retrieve user profile, open bottom sheet dialog fragment to display user profile
    override fun onItemClick(memberId: String) {
        if (memberId != uid) {
            memberInteractionSheetDialog =
                MemberInteractionBottomSheetDialogFragment(requireContext())
            memberInteractionSheetDialog?.arguments = bundleOf("memberIdKey" to memberId)
            memberInteractionSheetDialog?.show(childFragmentManager, null)
        }
    }

    //retrieve user profile and open alert dialog to remove the member from the group
    override fun onItemLongCLicked(memberId: String) {
        if(memberId == uid) {
            Toast.makeText(
                requireContext(),
                "Leave the group instead, you cannot remove yourself",
                Toast.LENGTH_LONG).show()
        } else  {
            dbRef.child("groups").child(args.groupId).child("groupAdmins")
                .addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        //retrieve list of group admins
                        val groupAdmins = snapshot.getValue<ArrayList<String>>()
                        //check if current user is an admin. Only admins can remove group members
                        if (groupAdmins?.contains(currentUser.uid) == true) {
                            dbRef.child("profiles").child(memberId).addListenerForSingleValueEvent(
                                object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        val profile = snapshot.getValue<Profile>()
                                        if (profile != null) {
                                            //open dialog
                                            AlertDialog.Builder(requireContext())
                                                .setMessage("Remove ${profile.name} from this group?")
                                                .setPositiveButton("Yes") {
                                                        dialog, _ ->
                                                    //run transaction on database list of group members
                                                    dbRef.child("groups").child(args.groupId).child("members").runTransaction(
                                                        object : Transaction.Handler {
                                                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                                                //retrieve the database list which is a mutable data and store in list else
                                                                //return the same data back to database if null
                                                                val list = currentData.getValue<ArrayList<String>>()
                                                                    ?: return Transaction.success(currentData)
                                                                //remove the specified member from the list if they exist
                                                                if (list.contains(memberId)) {
                                                                    list.remove(memberId)
                                                                }
                                                                //set value the value of the database members to the new list
                                                                currentData.value = list
                                                                //return updated list to database
                                                                return Transaction.success(currentData)
                                                            }

                                                            override fun onComplete(
                                                                error: DatabaseError?,
                                                                committed: Boolean,
                                                                currentData: DataSnapshot?
                                                            ) {
                                                                if (committed && error == null) {

                                                                    dbRef.child("user-groups").child(memberId).runTransaction(
                                                                        object : Transaction.Handler {
                                                                            override fun doTransaction(
                                                                                currentData: MutableData
                                                                            ): Transaction.Result {
                                                                                //retrieve the database list, if null, return same null value to database
                                                                                val listOfGroups = currentData.getValue<ArrayList<String>>()
                                                                                    ?: return Transaction.success(currentData)
                                                                                //remove group's id in the list of group's this members belongs to
                                                                                if (listOfGroups.contains(args.groupId)) {
                                                                                    listOfGroups.remove(args.groupId)
                                                                                }
                                                                                //set database list to this update list and return it
                                                                                currentData.value = listOfGroups
                                                                                return Transaction.success(currentData)
                                                                            }

                                                                            override fun onComplete(
                                                                                error: DatabaseError?,
                                                                                committed: Boolean,
                                                                                currentData: DataSnapshot?
                                                                            ) {
                                                                                if (committed && error == null) {
                                                                                   if(groupAdmins.contains(memberId)) {
                                                                                       dbRef.child("groups").child(args.groupId).child("groupAdmins").runTransaction(
                                                                                           object :
                                                                                               Transaction.Handler {
                                                                                               override fun doTransaction(
                                                                                                   currentData: MutableData
                                                                                               ): Transaction.Result {
                                                                                                   //retrieve the database list, if null, return same null value to database
                                                                                                   val listOfAdmins = currentData.getValue<ArrayList<String>>()
                                                                                                       ?: return Transaction.success(currentData)
                                                                                                   //remove member from admin list if they exist
                                                                                                   if (listOfAdmins.contains(memberId)) {
                                                                                                       listOfAdmins.remove(memberId)
                                                                                                   }
                                                                                                   //set database list to this updated list and return it
                                                                                                   currentData.value = listOfAdmins
                                                                                                   return Transaction.success(currentData)
                                                                                               }

                                                                                               override fun onComplete(
                                                                                                   error: DatabaseError?,
                                                                                                   committed: Boolean,
                                                                                                   currentData: DataSnapshot?
                                                                                               ) {}

                                                                                           }
                                                                                       )
                                                                                   }

                                                                                }
                                                                            }

                                                                        }
                                                                    )
                                                                    Snackbar.make(requireView(), "${profile.name} removed successfully", Snackbar.LENGTH_LONG).show()
                                                                }
                                                            }

                                                        }
                                                    )
                                                    dialog.dismiss()
                                                }.setNegativeButton("No") {
                                                        dialog, _ -> dialog.dismiss()
                                                }.show()
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {}
                                }
                            )
                        } else {
                            Toast.makeText(requireContext(), "Only group admins can remove members", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
            )
        }

    }


    override fun onGroupAboutChanged() {
        getGroupDescription()
    }

    private fun getGroupDescription() {
        //get and set group description
        dbRef.child("groups").child(args.groupId).child("description").get()
            .addOnSuccessListener {  dataSnapShot ->
                val description = dataSnapShot.getValue(String::class.java)
                if (description != null) {
                    binding.groupDescriptionTextView.text = description
                } else {
                    binding.groupDescriptionTextView.hint = "Describe this group"
                }
            }
    }

    private fun getGroupName() {
        //get and set group name
        dbRef.child("groups-id-name-photo").child(args.groupId).child("name")
            .get().addOnSuccessListener {  dataSnapShot ->
                val groupName = dataSnapShot.getValue(String::class.java)
                if (groupName != null) {
                    binding.groupNameTextView.text = groupName
                }
            }
    }

    private fun getGroupPhoto() {
        //get and load photo
        dbRef.child("groups-id-name-photo").child(args.groupId)
            .child("groupPhoto").get().addOnSuccessListener { dataSnapShot ->
                val photoUrl = dataSnapShot.getValue(String::class.java)
                if (photoUrl == null) {
                    Glide.with(requireContext()).load(R.drawable.ic_group)
                        .into(binding.groupPhotoImageView)
                    binding.photoProgressBar.visibility = GONE
                } else {
                    val options = RequestOptions()
                        .error(R.drawable.ic_downloading)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

                    binding.groupPhotoImageView.visibility = VISIBLE
                    //using custom glide image loader to indicate progress in time
                    GlideImageLoader(binding.groupPhotoImageView, binding.photoProgressBar)
                        .load(photoUrl, options);
                }
            }
    }

    override fun onGroupNameChanged() {
        getGroupName()
    }


}