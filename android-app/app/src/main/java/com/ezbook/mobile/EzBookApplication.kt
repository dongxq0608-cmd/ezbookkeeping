package com.ezbook.mobile

import android.app.Application

class EzBookApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
