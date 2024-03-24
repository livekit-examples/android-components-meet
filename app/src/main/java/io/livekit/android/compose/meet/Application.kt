package io.livekit.android.compose.meet

import com.github.ajalt.timberkt.Timber
import io.livekit.android.LiveKit
import io.livekit.android.util.LoggingLevel
import timber.log.Timber.DebugTree


class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(DebugTree())

        // Change logging level, defaults to OFF
        LiveKit.loggingLevel = LoggingLevel.INFO
    }
}