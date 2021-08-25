package com.colley.android.view.dialog

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.colley.android.R
import com.colley.android.listener.SaveButtonListener
import com.colley.android.databinding.FragmentEditGroupNameBottomSheetDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase


class EditGroupNameBottomSheetDialogFragment(
    private var saveButtonListener: SaveButtonListener,
    private val requiredContext: Context
) : BottomSheetDialogFragment() {


    private var _binding: FragmentEditGroupNameBottomSheetDialogBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private val uid: String
        get() = currentUser.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentEditGroupNameBottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRef = Firebase.database.reference
        currentUser = Firebase.auth.currentUser!!

        val bundledGroupName = arguments?.getString("groupNameKey")
        binding.editGroupNameEditText.setText(bundledGroupName)

        binding.saveGroupNameButton.setOnClickListener {
            //disable button to prevent multiple clicks
            it.isEnabled = false

            val newGroupName = binding.editGroupNameEditText.text.toString().trim()
            //check if user is an admin, only admins can change group name
            arguments?.getString("groupIdKey")?.let { groupId ->
                dbRef.child("groups").child(groupId).child("groupAdmins").addListenerForSingleValueEvent(
                    object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val admins = snapshot.getValue<ArrayList<String>>()
                            if (admins != null && admins.contains(uid)) {
                                saveGroupName(newGroupName, it)
                            } else {
                                Toast.makeText(requiredContext, "Only admins can change group name", Toast.LENGTH_LONG).show()
                                //re-enable button to allow for interaction
                                it.isEnabled = true
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.w(TAG, "getAdmins:OnCancelled", error.toException())
                            it.isEnabled = true
                        }
                    }
                )
            }

        }
    }

    private fun saveGroupName(newGroupName: String, button: View) {

        //retrieve group id from bundle arguments and update group name on database
        arguments?.getString("groupIdKey")?.let{ groupId ->
            dbRef.child("groups").child(groupId).child("name").setValue(newGroupName).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    dbRef.child("groups-id-name-photo").child(groupId).child("name").setValue(newGroupName).addOnCompleteListener {
                        if (task.isSuccessful) {
                            parentFragment?.requireView()?.let { view -> Snackbar.make(view, "Successfully updated group name", Snackbar.LENGTH_LONG)
                                .show() }
                            saveButtonListener.onSave()
                        } else{
                            Toast.makeText(requiredContext, "Failed to complete updating group name", Toast.LENGTH_LONG).show()
                            //re-enable button for interaction
                            button.isEnabled = true
                            binding.saveGroupNameButton.text = getString(R.string.retry_text)
                        }
                    }
                } else {
                    Toast.makeText(requiredContext, "Failed to update group name", Toast.LENGTH_LONG).show()
                    //re-enable button for interaction
                    button.isEnabled = true
                    binding.saveGroupNameButton.text = getString(R.string.retry_text)
                }
            }
        }
    }

    companion object {
        const val TAG = "EditGroupNameFragment"
    }
}