package com.app.marketlist.data.camera

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages a 5-second timeout for barcode scanning.
 *
 * When the timeout elapses without a barcode being detected,
 * the provided callback is invoked to trigger fallback to manual entry.
 */
class BarcodeTimeoutHandler {

    private var timeoutJob: Job? = null

    /**
     * Starts the timeout countdown. Calls [onTimeout] after [TIMEOUT_MILLIS]
     * if not cancelled beforehand.
     *
     * This function launches the timeout in the current coroutine scope
     * and returns immediately.
     */
    suspend fun startTimeout(onTimeout: () -> Unit) {
        cancel()
        coroutineScope {
            timeoutJob = launch {
                delay(TIMEOUT_MILLIS)
                onTimeout()
            }
        }
    }

    /**
     * Cancels the current timeout if one is active.
     * Call this when a barcode is successfully detected before the timeout.
     */
    fun cancel() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Returns whether the timeout is currently active (not cancelled, not completed).
     */
    fun isActive(): Boolean {
        return timeoutJob?.isActive == true
    }

    companion object {
        /**
         * Timeout duration in milliseconds (5 seconds as per requirement 5.6).
         */
        const val TIMEOUT_MILLIS = 5_000L
    }
}
