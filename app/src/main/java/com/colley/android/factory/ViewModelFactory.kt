package com.colley.android.factory

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.colley.android.repository.DatabaseRepository
import com.colley.android.viewmodel.*

//Factory for ViewModels
class ViewModelFactory(
        owner: SavedStateRegistryOwner,
        private val repository: DatabaseRepository
) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        @Suppress("UNCHECKED_CAST")
        when {
            modelClass.isAssignableFrom(PostsViewModel::class.java) -> {
                return PostsViewModel(handle, repository) as T
            }
            modelClass.isAssignableFrom(IssuesViewModel::class.java) -> {
                return IssuesViewModel(handle, repository) as T
            }
            modelClass.isAssignableFrom(ViewIssueViewModel::class.java) -> {
                return ViewIssueViewModel(handle, repository) as T
            }
            modelClass.isAssignableFrom(PostCommentsViewModel::class.java) -> {
                return PostCommentsViewModel(handle, repository) as T
            }
            modelClass.isAssignableFrom(PostLikesViewModel::class.java) -> {
                return PostLikesViewModel(handle, repository) as T
            }
            modelClass.isAssignableFrom(NotificationsViewModel::class.java) -> {
                return NotificationsViewModel(handle, repository) as T
            }
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}