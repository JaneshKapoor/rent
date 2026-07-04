package com.rent.app

import android.app.Application
import com.rent.app.data.RentDataStore
import com.rent.app.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RentApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Honor the stored Auto Update preference at process start.
        scope.launch {
            val settings = RentDataStore(this@RentApp).getSettings()
            RefreshScheduler.applyAutoUpdate(this@RentApp, settings.autoUpdate)
        }
    }
}
