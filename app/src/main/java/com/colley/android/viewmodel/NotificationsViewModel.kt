package com.colley.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.colley.android.repository.DatabaseRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import kotlinx.coroutines.flow.Flow

class NotificationsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DatabaseRepository
) : ViewModel() {

    fun fetchNotifications(query: Query): Flow<PagingData<DataSnapshot>> =
        repository.getDataStream(query)
            .cachedIn(viewModelScope)

}