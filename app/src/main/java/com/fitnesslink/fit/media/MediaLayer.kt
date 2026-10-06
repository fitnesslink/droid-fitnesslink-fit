package com.fitnesslink.fit.media

import android.content.Context

/**
 * Process-wide wiring for the media layer. `FitnessLinkApp` already calls
 * `MediaCacheStore.initialize` and `MediaPrefetcher.initialize`; both route
 * through `MediaLayer.initialize`, which is idempotent, so every media
 * singleton (URL cache, upload coordinator, attachment coordinator) has an
 * application context without each needing its own call site.
 */
object MediaLayer {
    @Volatile
    private var initialized = false

    /** Application context captured on first initialize. */
    @Volatile
    internal var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            MediaCacheStore.attach(app)
            MediaURLProvider.attach(app)
            MediaPrefetcher.attach(app)
            MediaUploadCoordinator.attach(app)
            MovementVideoAttachmentCoordinator.attach(app)
        }
    }

    internal fun requireContext(): Context =
        appContext ?: error("MediaLayer.initialize(context) has not been called")
}
