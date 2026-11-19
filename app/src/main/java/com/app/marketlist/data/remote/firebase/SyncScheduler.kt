package com.app.marketlist.data.remote.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.app.marketlist.data.repository.SyncRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encola la sincronización con WorkManager. Cualquier fallo se ignora: la app
 * funciona offline con Room y la sincronización es solo respaldo.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val syncRepository: SyncRepositoryImpl,
    @ApplicationContext private val context: Context
) {
    private var started = false

    fun requestSync() {
        runCatching { syncRepository.scheduleSyncWork() }
    }

    /** Encola una sincronización al arrancar y otra cada vez que se recupera la conectividad. */
    fun start() {
        if (started) return
        started = true
        requestSync()
        runCatching {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    requestSync()
                }
            })
        }
    }
}
