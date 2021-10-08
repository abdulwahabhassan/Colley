package com.colley.android.view.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.colley.android.R
import com.colley.android.databinding.BottomSheetDialogFragmentPostBinding
import com.colley.android.model.Notification
import com.colley.android.view.fragment.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class PostBottomSheetDialogFragment (
    private val parentContext: Context,
    private val postView: View,
    private val actionsDialogListener: ActionsDialogListener
        ) :
    BottomSheetDialogFragment(),
    CommentOnPostBottomSheetDialogFragment.CommentListener,
    PostCommentsFragment.PostCommentsListener {

    private var postId: String? = null
    private var postUserId: String? = null
    private var _binding: BottomSheetDialogFragmentPostBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private val uid: String
        get() = currentUser.uid
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPagerAdapter: FragmentStateAdapter
    private lateinit var sheetDialogCommentOn: CommentOnPostBottomSheetDialogFragment
    interface ActionsDialogListener {
        fun onCommented(currentData: DataSnapshot?)
        fun onLiked(currentData: DataSnapshot?, liked: Boolean?)
        fun onCommentDeleted(currentData: DataSnapshot?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            //retrieve post id from bundle
            postId = it.getString(POST_ID_KEY)
            postUserId = it.getString(POST_USER_ID_KEY)
        }
        //initialize Realtime Database
        dbRef = Firebase.database.reference

        //initialize authentication
        auth = Firebase.auth

        //initialize currentUser
        currentUser = auth.currentUser!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetDialogFragmentPostBinding
            .inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize tabLayout
        tabLayout = binding.bottomSheetTabLayout
        //initialize viewpager
        viewPager = binding.bottomSheetViewPager

        //set up viewPager
        viewPagerAdapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
            private val fragments = arrayOf(
                PostCommentsFragment(
                    postId,
                    parentContext,
                    postView,
                    this@PostBottomSheetDialogFragment),
                PostLikesFragment(postId, parentContext, postView)
            )

            override fun createFragment(position: Int) = fragments[position]

            override fun getItemCount(): Int = fragments.size
        }

        //bind to adapter
        viewPager.adapter = viewPagerAdapter

        //set up and sync viewpager with tabLayout
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.comments_tab_name)
                else -> getString(R.string.likes_tab_name)
            }
        }.attach()

        //comment on post
        binding.commentImageView.setOnClickListener {
            sheetDialogCommentOn = CommentOnPostBottomSheetDialogFragment(
                requireContext(),
                requireView(),
                this
            )
            sheetDialogCommentOn.arguments =
                bundleOf("postIdKey" to postId, "postUserIdKey" to postUserId)
            sheetDialogCommentOn.show(parentFragmentManager, null)
        }

        //set likeImageView drawable based on whether the user has liked the post or not
        postId?.let {
            Firebase.database.reference.child("post-likes").child(it)
                .child(uid).get().addOnSuccessListener { snapShot ->
                    binding.likeImageView.isActivated = snapShot.getValue(Boolean::class.java) ==
                            true
                }
        }

        //click to like post
        binding.likeImageView.setOnClickListener {
            //Register like on database
            postId?.let { it1 ->
                dbRef.child("post-likes").child(it1).child(uid)
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            //retrieve the current value of like at this location
                            var liked = currentData.getValue<Boolean>()
                            //compute new value of liked
                            liked = liked == null || liked == false
                            //if false set value at location to null else true
                            if (liked == false) {
                                currentData.value = null
                            } else {
                                currentData.value = true
                            }
                            //set database liked value to the new update
                            return Transaction.success(currentData)
                        }

                        //on successful entry, update likes count
                        @SuppressLint("SimpleDateFormat")
                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            currentData: DataSnapshot?
                        ) {
                            //get current value of liked
                            val liked = currentData?.getValue(Boolean::class.java)

                            if (error != null) {
                                Toast.makeText(
                                    context,
                                    "Unable to write like to database",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                //only create notification if post was liked not if post was unliked
                                //and if itemActor(user liking the post) is not the same user that
                                //owns the post
                                if (liked == true && postUserId != uid) {
                                    //notify the user who owns the post that a like was given on
                                    //their post
                                    postUserId?.let { postUserId ->

                                        //get current time and format it
                                        //timeId will be used for sorting notification from the most
                                        //recent
                                        val df: DateFormat =
                                            SimpleDateFormat("EEE, d MMM yyyy, HH:mm:ss")
                                        val date: String = df.format(Calendar.getInstance().time)
                                        val timeId = SimpleDateFormat("yyyyMMddHHmmss")
                                            .format(Calendar.getInstance().time).toLong() * -1

                                        //create instance of notification
                                        val notification = Notification(
                                            itemId = postId,
                                            itemOwnerUserId = postUserId,
                                            itemActorUserId = uid,
                                            timeId = timeId,
                                            timeStamp = date,
                                            itemActionId = null,
                                            itemType = "post",
                                            itemActionType = "like"
                                        )

                                        //push notification, retrieve key and set as notification id
                                        dbRef.child("user-notifications").child(postUserId)
                                            .push().setValue(notification) { error, ref ->
                                                if (error == null) {
                                                    val notificationKey = ref.key
                                                    dbRef.child("user-notifications")
                                                        .child(postUserId).child(notificationKey!!)
                                                        .child("notificationId")
                                                        .setValue(notificationKey)
                                                }
                                            }
                                    }
                                }

                                //update likes count
                                dbRef.child("posts").child(postId!!)
                                    .child("likesCount")
                                    .runTransaction(
                                        object : Transaction.Handler {
                                            override fun doTransaction(currentData: MutableData):
                                                    Transaction.Result {
                                                //retrieve the current value of count at this location
                                                var count = currentData.getValue<Int>()
                                                if (count == null) {
                                                    count = 1
                                                }

                                                //if liked, increase count by 1 else decrease by 1
                                                if (liked == true) {
                                                    count++

                                                } else {
                                                    count--
                                                }
                                                currentData.value = count
                                                //set database count value to the new update
                                                return Transaction.success(currentData)
                                            }

                                            //after successfully updating likes count on database, update ui
                                            override fun onComplete(
                                                error: DatabaseError?,
                                                committed: Boolean,
                                                currentData: DataSnapshot?
                                            ) {
                                                //update likeImageView drawable depending on the
                                                //value of liked
                                                binding.likeImageView.isActivated =
                                                    !(liked == null || liked == false)
                                                actionsDialogListener.onLiked(currentData, liked)
                                            }
                                        }
                                    )
                            }
                        }

                    }
                    )
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val POST_ID_KEY = "postIdKey"
        private const val POST_USER_ID_KEY = "postUserIdKey"
    }

    override fun onComment(currentData: DataSnapshot?) {
        actionsDialogListener.onCommented(currentData)
    }

    override fun onDeleteComment(currentData: DataSnapshot?) {
        //notify parent fragment to update viewHolder comments count
        actionsDialogListener.onCommentDeleted(currentData)
    }

}