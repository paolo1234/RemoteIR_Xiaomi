package com.irxiaomi

import android.app.Application
import com.irxiaomi.db.AppDatabase
import com.irxiaomi.ir.IrManager
import com.irxiaomi.ir.IrManagerFactory

/**
 * Application class. Inizializza il database e l'IR Manager all'avvio.
 */
class IRXiaomiApp : Application() {

    /** Database instance (lazy) */
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** IR Manager instance (lazy) */
    val irManager: IrManager by lazy { IrManagerFactory.create(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        IrManagerFactory.reset()
        AppDatabase.destroyInstance()
    }

    companion object {
        @Volatile
        private var instance: IRXiaomiApp? = null

        fun getInstance(): IRXiaomiApp = instance ?: throw IllegalStateException("IRXiaomiApp non inizializzata")
    }
}
