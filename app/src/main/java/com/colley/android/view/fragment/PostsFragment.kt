package com.colley.android.view.fragment

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.colley.android.R
import com.colley.android.adapter.PostsRecyclerAdapter
import com.colley.android.databinding.FragmentPostsBinding
import com.colley.android.model.Post
import com.colley.android.observer.PostsScrollToBottomObserver
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.ObservableSnapshotArray
import com.firebase.ui.database.paging.FirebaseRecyclerPagingAdapter
import com.firebase.ui.database.paging.LoadingState

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class PostsFragment : Fragment(),
    PostsRecyclerAdapter.ItemClickedListener,
    PostsRecyclerAdapter.DataChangedListener {

    private var _binding: FragmentPostsBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private var adapter: PostsRecyclerAdapter? = null
    private var manager: LinearLayoutManager? = null
    private lateinit var pagingAdapter: FirebaseRecyclerPagingAdapter<Post, PostsRecyclerAdapter.PostViewHolder>
    private lateinit var recyclerView: RecyclerView
    private val uid: String
        get() = currentUser.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.posts_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search_posts_menu_item -> {
                Toast.makeText(context, "Searching posts", Toast.LENGTH_LONG).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPostsBinding.inflate(inflater, container, false)
        recyclerView = binding.postRecyclerView
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

        //get a query reference to chats //order by endorsementsCount
        //appears on top
        val postsRef = dbRef.child("posts").orderByChild("likes")

        //the FirebaseRecyclerAdapter class and options come from the FirebaseUI library
        //build an options to configure adapter. setQuery takes firebase query to listen to and a
        //model class to which snapShots should be parsed
        val options = FirebaseRecyclerOptions.Builder<Post>()
            .setQuery(postsRef, Post::class.java)
            .build()

        adapter = PostsRecyclerAdapter(
            options, requireContext(), currentUser,
            this,
            this)

        manager = LinearLayoutManager(requireContext())
        //reversing and stacking is actually counterintuitive as used in this scenario, the purpose
        //of the manipulation is such that most recent items appear at the top since firebase does
        //not provide a method to sort queries in descending order
        manager?.reverseLayout = true
        manager?.stackFromEnd = true
        recyclerView.layoutManager = manager

        recyclerView.adapter = adapter

    }

    override fun onStart() {
        super.onStart()
        adapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter?.stopListening()
        _binding = null
    }

    override fun onDataAvailable(snapshotArray: ObservableSnapshotArray<Post>) {
        binding.noPostsProgressBar.visibility = View.GONE
        if (snapshotArray.isEmpty()) {
            binding.noPostsLayout.visibility = View.VISIBLE
        } else {
            binding.noPostsLayout.visibility = View.GONE
        }
    }

    override fun onItemClick(postId: String, view: View) {

    }

    override fun onItemLongCLicked(postId: String, view: View) {

    }

    override fun onUserClicked(userId: String, view: View) {
        val action = HomeFragmentDirections.actionHomeFragmentToUserInfoFragment(userId)
        parentFragment?.findNavController()?.navigate(action)
    }

}