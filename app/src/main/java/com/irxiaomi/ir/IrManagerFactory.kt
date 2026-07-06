package com.irxiaomi.ir

import android.content.Context
import android.util.Log
import com.irxiaomi.db.IrCodeEntity

/**
 * Factory che seleziona automaticamente la migliore implementazione IR disponibile.
 * Ordine di preferenza:
 * 1. XiaomiIrManagerImpl  (se device Xiaomi/MIUI con IR)
 * 2. ConsumerIrManagerImpl (API standard Android)
 * 3. SysfsIrManagerImpl    (root/sysfs)
 */
class IrManagerFactory {
    companion object {
        private const val TAG = "IrManagerFactory"

        @Volatile
        private var instance: IrManager? = null

        /**
         * Crea e restituisce il miglior IrManager disponibile.
         * La cache persiste per l'intero ciclo di vita del processo.
         */
        fun create(context: Context): IrManager {
            if (instance != null) return instance!!

            synchronized(this) {
                if (instance != null) return instance!!

                val managers = listOf(
                    XiaomiIrManagerImpl(context),
                    ConsumerIrManagerImpl(context),
                    SysfsIrManagerImpl(context)
                )

                val selected = managers.firstOrNull { it.isSupported() }

                if (selected != null) {
                    Log.i(TAG, "Selected IR manager: ${selected.name}")
                    instance = selected
                } else {
                    Log.w(TAG, "No IR manager available! Using fallback (ConsumerIrManager)")
                    instance = ConsumerIrManagerImpl(context) // Fallback anche se non supportato
                }

                return instance!!
            }
        }

        /** Resetta la cache (utile per test) */
        fun reset() {
            instance = null
        }
    }
}
