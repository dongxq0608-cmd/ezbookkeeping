package com.ezbook.mobile

import android.content.Context
import com.ezbook.mobile.data.EzBookRepository
import com.ezbook.mobile.data.SessionStore
import com.ezbook.mobile.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 轻量手写依赖容器，避免引入 DI 框架。
 */
object Graph {

    lateinit var store: SessionStore
        private set

    lateinit var api: ApiClient
        private set

    lateinit var repo: EzBookRepository
        private set

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return

        store = SessionStore(context.applicationContext)
        api = ApiClient(store)
        repo = EzBookRepository(api)

        api.onSessionExpired = {
            repo.invalidate()
            _sessionExpired.tryEmit(Unit)
        }

        initialized = true
    }
}
