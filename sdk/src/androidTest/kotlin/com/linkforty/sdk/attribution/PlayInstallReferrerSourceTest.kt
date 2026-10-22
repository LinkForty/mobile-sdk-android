package com.linkforty.sdk.attribution

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a device, reading the Play install referrer finishes within its timeout and
 * never throws, with or without Play. A test APK isn't installed from Play, so
 * there is no LinkForty click in whatever Play answers.
 */
@RunWith(AndroidJUnit4::class)
class PlayInstallReferrerSourceTest {

    @Test
    fun readsNothingForAnAppNotInstalledFromPlay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = SystemClock.elapsedRealtime()

        val referrer = PlayInstallReferrerSource(context, timeoutMs = 2_000).read()

        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue("took ${elapsed}ms", elapsed < 3_000)
        assertTrue("referrer: $referrer", referrer == null || !referrer.contains("lf_click"))
    }
}
