package com.linkforty.e2e

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

/**
 * Hands incoming links to the SDK from `onCreate` and `onNewIntent`, as the
 * README's `MainActivity` does. The harness starts on the first `onCreate`, so it
 * can read that launch's `lf*` extras.
 */
class MainActivity : Activity() {

    private val harness get() = (application as E2EApplication).harness

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "LinkForty E2E" })

        if (!started) {
            started = true
            val extras = intent.extras?.keySet().orEmpty()
                .filter { it.startsWith("lf") }
                .associateWith { intent.getStringExtra(it).orEmpty() }
            harness.start(extras)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { harness.open(it) }
    }

    private companion object {
        var started = false
    }
}
