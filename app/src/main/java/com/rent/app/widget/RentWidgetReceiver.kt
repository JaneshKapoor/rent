package com.rent.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.rent.app.data.RentDataStore
import com.rent.app.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The AppWidgetProvider that hosts [RentWidget]. Registered in the manifest.
 * On first placement it applies the Auto Update preference and kicks a one-time
 * refresh so the widget isn't stale.
 */
class RentWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = RentWidget()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        scope.launch {
            val settings = RentDataStore(appContext).getSettings()
            RefreshScheduler.applyAutoUpdate(appContext, settings.autoUpdate)
        }
        RefreshScheduler.refreshNow(appContext)
    }
}
