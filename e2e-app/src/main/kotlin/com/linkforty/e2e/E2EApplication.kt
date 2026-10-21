package com.linkforty.e2e

import android.app.Application

/** Initializes the SDK in `Application.onCreate`, as the README does. */
class E2EApplication : Application() {

    lateinit var harness: Harness
        private set

    override fun onCreate() {
        super.onCreate()
        harness = Harness(this)
    }
}
