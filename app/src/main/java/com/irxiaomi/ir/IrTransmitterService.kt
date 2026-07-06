package com.irxiaomi.ir

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service per trasmettere IR in background.
 * Utile per:
 * - Ripetizione di codici (tasto tenuto premuto)
 * - Sequenze di comandi (macro)
 * - Scheduling (accendi TV alle 8:00)
 */
class IrTransmitterService : Service() {

    companion object {
        private const val TAG = "IrTransmitterService"
        const val ACTION_TRANSMIT = "com.irxiaomi.action.TRANSMIT"
        const val ACTION_CANCEL = "com.irxiaomi.action.CANCEL"
        const val EXTRA_FREQUENCY = "frequency"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_INTERVAL = "interval"  // ms tra ripetizioni
    }

    private var irManager: IrManager? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        irManager = IrManagerFactory.create(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRANSMIT -> {
                val frequency = intent.getIntExtra(EXTRA_FREQUENCY, 38000)
                val pattern = intent.getIntArrayExtra(EXTRA_PATTERN) ?: return START_NOT_STICKY
                val repeat = intent.getIntExtra(EXTRA_REPEAT, 1)
                val interval = intent.getLongExtra(EXTRA_INTERVAL, 100L)

                if (repeat > 1) {
                    // Ripetizione in background thread
                    Thread {
                        for (i in 0 until repeat) {
                            if (!isRunning) break
                            irManager?.transmit(frequency, pattern)
                            if (i < repeat - 1) {
                                try { Thread.sleep(interval) } catch (_: InterruptedException) { break }
                            }
                        }
                    }.start()
                } else {
                    irManager?.transmit(frequency, pattern)
                }
            }
            ACTION_CANCEL -> {
                isRunning = false
                irManager?.cancelTransmission()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        irManager?.cancelTransmission()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}
