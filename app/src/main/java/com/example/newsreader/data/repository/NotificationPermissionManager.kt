package com.example.newsreader.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object NotificationPermissionManager {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests

    fun requestPermission() {
        _requests.tryEmit(Unit)
    }
}
