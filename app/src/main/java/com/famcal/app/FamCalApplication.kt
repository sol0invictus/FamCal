package com.famcal.app

import android.app.Application
import com.famcal.app.notifications.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * creates the app-level dependency container.
 */
@HiltAndroidApp
class FamCalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }
}
