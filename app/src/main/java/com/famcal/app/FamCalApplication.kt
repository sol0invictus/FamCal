package com.famcal.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.famcal.app.notifications.createNotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * creates the app-level dependency container. Also provides the WorkManager
 * configuration so Hilt can inject dependencies into workers.
 */
@HiltAndroidApp
class FamCalApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }
}
