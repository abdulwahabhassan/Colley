package com.colley.android.model

data class PrivateMessage (
    var fromUserId: String? = null,
    var toUserId: String? = null,
    var text: String? = null,
    var image: String? = null,
    var messageId: String? = null
        )