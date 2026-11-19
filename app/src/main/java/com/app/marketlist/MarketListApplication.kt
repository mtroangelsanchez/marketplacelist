package com.app.marketlist

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.app.marketlist.data.remote.firebase.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MarketListApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Lazy: evita crear WorkManager antes de que workerFactory esté inyectado.
    @Inject
    lateinit var syncScheduler: dagger.Lazy<SyncScheduler>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Sincronización automática al iniciar y al recuperar conectividad (falla en silencio si no hay Firebase).
        syncScheduler.get().start()
    }
}
