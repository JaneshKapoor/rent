package com.rent.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.rent.app.data.RentDataStore
import com.rent.app.work.RefreshScheduler

/**
 * Tapping the widget (when configured) kicks off an immediate one-time
 * WorkManager refresh that fetches, recomputes, and updates the widget in place
 * — rather than just opening the app.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val settings = RentDataStore(context).getSettings()
        if (settings.username.isBlank()) {
            // Not set up yet — let the launcher's default open-app handle it.
            return
        }
        RefreshScheduler.refreshNow(context)
    }
}
