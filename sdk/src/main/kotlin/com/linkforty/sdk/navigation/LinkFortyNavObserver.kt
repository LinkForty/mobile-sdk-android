package com.linkforty.sdk.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.linkforty.sdk.LinkForty
import com.linkforty.sdk.LinkFortyLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [NavController.OnDestinationChangedListener] that reports a `screen_view` to
 * LinkForty whenever the navigation destination changes, so you get screen-flow
 * tracking without instrumenting every screen.
 *
 * Requires Jetpack Navigation (`androidx.navigation`), which your app already
 * provides if you use it — the SDK depends on it only as `compileOnly`. Attach
 * the observer to your `NavController`:
 *
 * ```kotlin
 * navController.addOnDestinationChangedListener(LinkFortyNavObserver())
 * ```
 *
 * By default the destination's `route` (Compose navigation) or `label` (XML nav
 * graphs) is used as the screen name; destinations with neither are skipped.
 * Provide [screenNameExtractor] to customize naming. Each reported `screen_view`
 * is stamped with the active last-click attribution context.
 */
class LinkFortyNavObserver(
    private val screenNameExtractor: (NavDestination) -> String? = ::defaultScreenName,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : NavController.OnDestinationChangedListener {

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val name = screenNameExtractor(destination)?.takeIf { it.isNotBlank() } ?: return

        // Fire-and-forget; navigation must not block on network/queue work. If the
        // SDK isn't initialized yet, trackScreenView (or `shared`) throws and we
        // simply skip this screen view.
        scope.launch {
            try {
                LinkForty.shared.trackScreenView(name)
            } catch (e: Exception) {
                LinkFortyLogger.log("Auto screen-view skipped: ${e.message}")
            }
        }
    }
}

/** Default screen name: the destination route (Compose nav) or its label (XML nav). */
internal fun defaultScreenName(destination: NavDestination): String? =
    destination.route ?: destination.label?.toString()
