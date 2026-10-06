package com.fitnesslink.fit.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port of iOS `AppRouter` (FTNSSLNK-261): app-level programmatic navigation.
 *
 * Routes are Navigation-Compose route strings (`"workoutDetail/{id}"` already
 * filled in). Non-UI code (a push-notification tap, the Get Started screen)
 * calls [open]; `MainTabNavigation` — the owner of the `NavController` —
 * observes [pendingRoute] and navigates.
 *
 * The "pending" indirection is deliberate: on a cold-start tap the route
 * arrives before the main UI exists (splash → session restore → tabs), so it
 * parks here until `MainTabNavigation` appears and [consume]s it.
 */
object AppRouter {
    /** Bottom-tab root routes, in display order (matches iOS). */
    const val HOME_TAB = "home"
    const val CATALOG_TAB = "catalog"
    const val CALENDAR_TAB = "calendar"
    const val NUTRITION_TAB = "nutrition"
    const val PROFILE_TAB = "profile"
    val TAB_ROUTES = listOf(HOME_TAB, CATALOG_TAB, CALENDAR_TAB, NUTRITION_TAB, PROFILE_TAB)

    /** Routes that select a tab rather than push a screen. */
    fun isTabSwitch(route: String): Boolean = route in TAB_ROUTES

    private val _pendingRoute = MutableStateFlow<String?>(null)
    /** Route waiting to be pushed. Set by [open], consumed by `MainTabNavigation`. */
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun open(route: String) {
        _pendingRoute.value = route
    }

    /** Take the pending route (and clear it). */
    fun consume(): String? {
        val route = _pendingRoute.value
        _pendingRoute.value = null
        return route
    }

    // Handlers registered by MainTabNavigation (the only NavController owner).

    /** Pushes `route` onto the current stack (tab routes select their tab instead). */
    var pushHandler: ((String) -> Unit)? = null
    /** Pops the top screen and pushes `route` in its place. */
    var replaceTopHandler: ((String) -> Unit)? = null
    /** Clears the stack to the tab root and pushes `route` (null = just pop to root). */
    var resetStackHandler: ((String?) -> Unit)? = null
    /** Pops `count` screens (clamped to the stack depth). */
    var popHandler: ((Int) -> Unit)? = null

    /**
     * Push on top of whatever the user is on — a plain Button's way to
     * navigate where a nav callback isn't available. Falls back to [open]
     * when no tab host is registered.
     */
    fun push(route: String) {
        pushHandler?.invoke(route) ?: open(route)
    }

    /**
     * Swap the screen on top of the active stack for `route` — e.g. the
     * workout editor handing off to the just-created workout's detail so
     * Back returns to where the editor was opened. Returns false when no
     * tab host is registered; the caller then falls back to a plain pop.
     */
    fun replaceTop(route: String): Boolean {
        val handler = replaceTopHandler ?: return false
        handler(route)
        return true
    }

    /** Leave the whole flow behind and land on `route` at the top of the active tab. */
    fun resetStack(route: String?) {
        val handler = resetStackHandler
        if (handler != null) handler(route) else if (route != null) open(route)
    }

    /** Pop several screens at once. Returns false when no tab host is registered. */
    fun pop(count: Int): Boolean {
        val handler = popHandler ?: return false
        handler(count)
        return true
    }

    fun clearHandlers() {
        pushHandler = null
        replaceTopHandler = null
        resetStackHandler = null
        popHandler = null
    }
}
