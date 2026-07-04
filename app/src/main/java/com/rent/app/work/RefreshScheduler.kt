package com.rent.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules background refresh work. */
object RefreshScheduler {

    private const val PERIODIC_NAME = "rent_periodic_refresh"
    private const val ONE_TIME_NAME = "rent_manual_refresh"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Twice-daily refresh (every ~12h) to conserve battery. Kept unique and uses
     * UPDATE so re-scheduling adopts the current interval instead of stacking.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Cancels the periodic refresh (Auto Update turned off — manual tap only). */
    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }

    /** Applies the Auto Update preference: schedule twice-daily or cancel. */
    fun applyAutoUpdate(context: Context, enabled: Boolean) {
        if (enabled) schedulePeriodic(context) else cancelPeriodic(context)
    }

    /** Immediate one-shot refresh (widget tap / "Save & Refresh"). */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraint)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
