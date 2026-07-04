package com.rent.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rent.app.data.ContributionRepository
import com.rent.app.widget.RentWidget

/**
 * Fetches latest data -> computes state -> saves to DataStore -> updates all
 * widget instances. Used both by the hourly PeriodicWorkRequest and by the
 * one-time request kicked off when the widget is tapped.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ContributionRepository.get(applicationContext).refresh()
            RentWidget.updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            // Never crash the worker; let WorkManager retry with backoff.
            Result.retry()
        }
    }
}
