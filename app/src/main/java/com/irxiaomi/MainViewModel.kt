package com.irxiaomi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irxiaomi.db.AppDatabase
import com.irxiaomi.db.IrCodeDao
import com.irxiaomi.db.IrCodeEntity
import com.irxiaomi.ir.IrManager
import com.irxiaomi.ir.IrManagerFactory
import com.irxiaomi.learning.AudioIrLearner
import com.irxiaomi.sync.LircImporter
import com.irxiaomi.sync.RemoteSync
import com.irxiaomi.clone.VariantGenerator
import com.irxiaomi.clone.CodeCloneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel principale dell'app.
 * Gestisce stato, database, trasmissione IR e operazioni asincrone.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as IRXiaomiApp
    val database: AppDatabase = app.database
    private val irManager: IrManager = app.irManager

    // DAO
    val codeDao: IrCodeDao = database.irCodeDao()

    // Sotto-moduli
    val learner = AudioIrLearner(application)
    val lircImporter = LircImporter(application)
    val remoteSync = RemoteSync()
    val variantGenerator = VariantGenerator(codeDao)
    val codeCloneManager = CodeCloneManager(application)

    // Stato UI
    private val _irReady = MutableStateFlow(false)
    val irReady: StateFlow<Boolean> = _irReady

    private val _irManagerInfo = MutableStateFlow<Map<String, Any>>(emptyMap())
    val irManagerInfo: StateFlow<Map<String, Any>> = _irManagerInfo

    private val _databaseSize = MutableStateFlow(0)
    val databaseSize: StateFlow<Int> = _databaseSize

    private val _lastTransmittedCode = MutableStateFlow<IrCodeEntity?>(null)
    val lastTransmittedCode: StateFlow<IrCodeEntity?> = _lastTransmittedCode

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding

    private val _seedProgress = MutableStateFlow(0)
    val seedProgress: StateFlow<Int> = _seedProgress

    // Flow di tutti i codici
    val allCodes: Flow<List<IrCodeEntity>> = codeDao.getAllFlow()
    val searchResults = MutableStateFlow<List<IrCodeEntity>>(emptyList())

    init {
        viewModelScope.launch {
            _irReady.value = irManager.isSupported()
            _irManagerInfo.value = irManager.getDeviceInfo()
            _databaseSize.value = codeDao.count()
        }
    }

    /** Invia un codice IR */
    fun sendCode(code: IrCodeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = irManager.transmitCode(code)
            if (success && code.id > 0) {
                codeDao.incrementUsage(code.id)
                _lastTransmittedCode.value = code
            }
        }
    }

    /** Invia un codice con frequency e pattern raw */
    fun transmitRaw(freq: Int, pattern: IntArray) {
        viewModelScope.launch(Dispatchers.IO) {
            irManager.transmit(freq, pattern)
        }
    }

    /** Cerca codici nel database */
    fun searchCodes(query: String) {
        if (query.isBlank()) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            codeDao.search(query).collect { results ->
                searchResults.value = results
            }
        }
    }

    /** Trova codici mancanti e li inserisce */
    fun findMissingCodes(brand: String, deviceType: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val missing = variantGenerator.findMissingCodes(brand, deviceType)
            if (missing.isNotEmpty()) {
                codeDao.insertAll(missing)
                _databaseSize.value = codeDao.count()
            }
        }
    }

    /** Clona codici da un brand all'altro */
    fun cloneCodes(sourceBrand: String, targetBrand: String, deviceType: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val sourceCodes = codeDao.getByBrandAndDevice(sourceBrand, deviceType)
            val cloned = sourceCodes.map { codeCloneManager.cloneCode(it, targetBrand, deviceType) }
            if (cloned.isNotEmpty()) {
                codeDao.insertAll(cloned)
                _databaseSize.value = codeDao.count()
            }
        }
    }

    /** Importa database LIRC */
    fun importLirc() {
        viewModelScope.launch {
            lircImporter.importFromAssets(codeDao) { progress ->
                if (progress.isComplete) {
                    _databaseSize.value = codeDao.count()
                }
            }
        }
    }

    /** Sincronizza con server remoto */
    fun syncRemote() {
        viewModelScope.launch {
            val localCodes = codeDao.getAll()
            remoteSync.sync(localCodes, codeDao)
            _databaseSize.value = codeDao.count()
        }
    }

    /** Esporta database in file JSON */
    fun exportDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val codes = codeDao.getAll()
            codeCloneManager.createExportFile(codes)
        }
    }

    /** Cancella tutto il database */
    fun clearDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            codeDao.deleteAll()
            _databaseSize.value = 0
        }
    }

    /** Genera database seed con migliaia di codici predefiniti */
    fun seedDatabase() {
        viewModelScope.launch(Dispatchers.Default) {
            _isSeeding.value = true
            _seedProgress.value = 0
            val count = DatabaseSeed.generateAll(codeDao)
            _seedProgress.value = count
            _isSeeding.value = false
            refreshDatabaseSize()
        }
    }

    /** Aggiorna dimensione database */
    fun refreshDatabaseSize() {
        viewModelScope.launch {
            _databaseSize.value = codeDao.count()
        }
    }
}
