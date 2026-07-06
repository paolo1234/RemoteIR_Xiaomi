package com.irxiaomi.sync

import android.util.Log
import com.irxiaomi.db.IrCodeEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Sincronizzazione con server remoto per database IR condiviso.
 *
 * Endpoint API:
 * - GET  /api/codes/search?brand=X&device=Y  -> ricerca
 * - GET  /api/codes/:id                        -> dettaglio
 * - POST /api/codes                            -> upload nuovo codice
 * - POST /api/codes/bulk                       -> upload multipli
 * - GET  /api/codes/popular                    -> codici più usati
 * - POST /api/codes/:id/vote                   -> vota codice
 */
interface IrCodeApi {

    @GET("api/codes/search")
    suspend fun search(
        @Query("brand") brand: String? = null,
        @Query("device_type") deviceType: String? = null,
        @Query("model") model: String? = null,
        @Query("query") query: String? = null,
        @Query("protocol") protocol: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<IrCodeEntity>

    @GET("api/codes/{id}")
    suspend fun getById(@Path("id") id: Long): IrCodeEntity?

    @POST("api/codes")
    suspend fun upload(@Body code: IrCodeEntity): IrCodeEntity

    @POST("api/codes/bulk")
    suspend fun uploadBulk(@Body codes: List<IrCodeEntity>): List<IrCodeEntity>

    @GET("api/codes/popular")
    suspend fun getPopular(@Query("limit") limit: Int = 100): List<IrCodeEntity>

    @GET("api/brands")
    suspend fun getBrands(): List<String>

    @GET("api/stats")
    suspend fun getStats(): Map<String, Any>
}

/**
 * Gestisce la sincronizzazione del database locale con il server remoto.
 */
class RemoteSync(private val baseUrl: String = "https://irxdb.example.com/") {

    companion object {
        private const val TAG = "RemoteSync"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
    }

    data class SyncState(
        val isSyncing: Boolean = false,
        val lastSyncTime: Long = 0,
        val uploadedCount: Int = 0,
        val downloadedCount: Int = 0,
        val errors: Int = 0,
        val message: String = ""
    )

    private val _state = MutableStateFlow(SyncState())
    val state: Flow<SyncState> = _state

    private val api: IrCodeApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(IrCodeApi::class.java)
    }

    /** Cerca codici sul server remoto */
    suspend fun searchRemote(
        brand: String? = null,
        deviceType: String? = null,
        query: String? = null
    ): List<IrCodeEntity> {
        return try {
            api.search(brand = brand, deviceType = deviceType, query = query)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            emptyList()
        }
    }

    /** Carica un codice locale sul server */
    suspend fun uploadCode(code: IrCodeEntity): IrCodeEntity? {
        return try {
            val uploaded = api.upload(code)
            _state.value = _state.value.copy(uploadedCount = _state.value.uploadedCount + 1)
            uploaded
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            null
        }
    }

    /** Sincronizzazione completa: carica codici nuovi/modificati, scarica aggiornamenti */
    suspend fun sync(
        localCodes: List<IrCodeEntity>,
        localDao: com.irxiaomi.db.IrCodeDao
    ) {
        _state.value = SyncState(isSyncing = true, message = "Avvio sincronizzazione...")

        try {
            // 1. Carica codici locali non ancora sul server (source = "user_created" o "learned")
            val toUpload = localCodes.filter { 
                it.source in listOf("user_created", "learned", "variant") 
            }
            
            if (toUpload.isNotEmpty()) {
                _state.value = _state.value.copy(message = "Caricamento ${toUpload.size} codici...")
                val batchSize = 50
                toUpload.chunked(batchSize).forEach { batch ->
                    try {
                        api.uploadBulk(batch)
                        _state.value = _state.value.copy(
                            uploadedCount = _state.value.uploadedCount + batch.size
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Bulk upload error", e)
                        _state.value = _state.value.copy(
                            errors = _state.value.errors + batch.size
                        )
                    }
                }
            }

            // 2. Scarica codici popolari/verificati dal server
            _state.value = _state.value.copy(message = "Download codici popolari...")
            val popular = try {
                api.getPopular(200)
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                emptyList()
            }

            if (popular.isNotEmpty()) {
                // Inserisci solo codici non presenti localmente
                val existingIds = localCodes.map { "${it.brand}-${it.deviceType}-${it.command}-${it.protocol}" }.toSet()
                val newCodes = popular.filter { 
                    "${it.brand}-${it.deviceType}-${it.command}-${it.protocol}" !in existingIds
                }
                
                if (newCodes.isNotEmpty()) {
                    localDao.insertAll(newCodes)
                    _state.value = _state.value.copy(
                        downloadedCount = newCodes.size
                    )
                }
            }

            _state.value = _state.value.copy(
                isSyncing = false,
                lastSyncTime = System.currentTimeMillis(),
                message = "Sincronizzazione completata!"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            _state.value = _state.value.copy(
                isSyncing = false,
                errors = _state.value.errors + 1,
                message = "Errore: ${e.message}"
            )
        }
    }
}
