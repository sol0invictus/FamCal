package com.famcal.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** On device boot, re-arms reminder alarms (which don't survive a restart). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderRescheduleWorker.refreshNow(context)
        }
    }
}
