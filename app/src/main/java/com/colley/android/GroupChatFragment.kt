package com.colley.android

import android.os.Bundle
import android.view.*
import android.view.View.GONE
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.colley.android.adapter.GroupChatFragmentRecyclerAdapter
import com.colley.android.databinding.FragmentGroupChatBinding
import com.colley.android.model.GroupMessage
import com.colley.android.model.ScrollToBottomObserver
import com.colley.android.model.SendButtonObserver
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class GroupChatFragment : Fragment(), GroupChatFragmentRecyclerAdapter.BindViewHolderListener {

    val args: GroupChatFragmentArgs by navArgs()
    private var _binding: FragmentGroupChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseDatabase
    private lateinit var adapter: GroupChatFragmentRecyclerAdapter
    private lateinit var manager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //allows this fragment to be able to modify it's containing activity's toolbar menu
        setHasOptionsMenu(true);

    }

    //since we have set hasOptionsMenu to true, our fragment can now override this call to allow us
    //modify the menu
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        //this clears the original main activity menu
        menu.clear()
        //this inflates a new menu
        inflater.inflate(R.menu.group_chat_menu, menu)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentGroupChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //set action bar title
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = args.groupName

        // Initialize Realtime Database
        db = Firebase.database
        val messagesRef = db.reference.child("messages")

// The FirebaseRecyclerAdapter class and options come from the FirebaseUI library
        val options = FirebaseRecyclerOptions.Builder<GroupMessage>()
            .setQuery(messagesRef, GroupMessage::class.java)
            .build()
        adapter = GroupChatFragmentRecyclerAdapter(options, getCurrentUser(), this)
        manager = LinearLayoutManager(requireContext())
        manager.stackFromEnd = true
        binding.messageRecyclerView.layoutManager = manager
        binding.messageRecyclerView.adapter = adapter

//scroll down when a new message arrives
        adapter.registerAdapterDataObserver(
            ScrollToBottomObserver(binding.messageRecyclerView, adapter, manager)
        )
//disable the send button when there's no text in the input field
        binding.messageEditText.addTextChangedListener(SendButtonObserver(binding.sendButton))

//when the send button is clicked, send a text message
        binding.sendButton.setOnClickListener {
            val groupMessage = GroupMessage(
                binding.messageEditText.text.toString(),
                getUserName(),
                null,
                getPhotoUrl()
            )
            db.reference.child("messages").push().setValue(groupMessage)
            binding.messageEditText.setText("")
        }

    }

    private fun getCurrentUser(): FirebaseUser? {
        return Firebase.auth.currentUser
    }

    private fun getUserName(): String? {
        val user = Firebase.auth.currentUser
        return if (user != null) {
            user.displayName
        } else "Anonymous"
    }

    private fun getPhotoUrl(): String? {
        val user = Firebase.auth.currentUser
        return user?.photoUrl?.toString()
    }

    override fun onResume() {
        super.onResume()
        //hide support action bar
//        (activity as AppCompatActivity?)!!.supportActionBar!!.hide()
        adapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        //undo hiding of support action bar
//        (activity as AppCompatActivity?)!!.supportActionBar!!.show()
        adapter.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onBind() {
        binding.progressBar.visibility = GONE
    }

}