package com.irxiaomi.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object per i codici IR.
 * Supporta operazioni CRUD, ricerca avanzata, import/export.
 */
@Dao
interface IrCodeDao {

    // ============ OPERAZIONI BASE ============

    @Query("SELECT * FROM ir_codes ORDER BY usage_count DESC, last_used_at DESC")
    fun getAllFlow(): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes ORDER BY usage_count DESC, last_used_at DESC")
    suspend fun getAll(): List<IrCodeEntity>

    @Query("SELECT * FROM ir_codes WHERE id = :id")
    suspend fun getById(id: Long): IrCodeEntity?

    @Query("SELECT * FROM ir_codes WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<IrCodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(code: IrCodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(codes: List<IrCodeEntity>): List<Long>

    @Update
    suspend fun update(code: IrCodeEntity)

    @Delete
    suspend fun delete(code: IrCodeEntity)

    @Query("DELETE FROM ir_codes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ir_codes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ir_codes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ir_codes WHERE is_verified = 1")
    suspend fun countVerified(): Int

    // ============ RICERCA PER DISPOSITIVO ============

    @Query("SELECT * FROM ir_codes WHERE brand = :brand AND device_type = :deviceType ORDER BY name")
    suspend fun getByBrandAndDevice(brand: String, deviceType: String): List<IrCodeEntity>

    @Query("SELECT * FROM ir_codes WHERE brand = :brand AND device_type = :deviceType AND model = :model ORDER BY name")
    suspend fun getByBrandDeviceModel(brand: String, deviceType: String, model: String): List<IrCodeEntity>

    @Query("SELECT * FROM ir_codes WHERE brand = :brand AND device_type = :deviceType ORDER BY name")
    fun getByBrandAndDeviceFlow(brand: String, deviceType: String): Flow<List<IrCodeEntity>>

    @Query("SELECT DISTINCT brand FROM ir_codes WHERE device_type = :deviceType ORDER BY brand")
    fun getBrandsByDeviceType(deviceType: String): Flow<List<String>>

    @Query("SELECT DISTINCT brand FROM ir_codes ORDER BY brand")
    suspend fun getAllBrands(): List<String>

    @Query("SELECT DISTINCT model FROM ir_codes WHERE brand = :brand AND device_type = :deviceType ORDER BY model")
    suspend fun getModelsByBrandAndDevice(brand: String, deviceType: String): List<String>

    @Query("SELECT DISTINCT device_type FROM ir_codes ORDER BY device_type")
    fun getDeviceTypes(): Flow<List<String>>

    // ============ RICERCA ============

    @Query("""
        SELECT * FROM ir_codes 
        WHERE brand LIKE '%' || :query || '%' 
           OR display_name LIKE '%' || :query || '%'
           OR model LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
           OR device_type LIKE '%' || :query || '%'
        ORDER BY usage_count DESC
        LIMIT :limit
    """)
    fun search(query: String, limit: Int = 100): Flow<List<IrCodeEntity>>

    @Query("""
        SELECT * FROM ir_codes 
        WHERE brand = :brand 
           AND (display_name LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')
        ORDER BY name
    """)
    fun searchByBrand(brand: String, query: String): Flow<List<IrCodeEntity>>

    // ============ FILTRI AVANZATI ============

    @Query("SELECT * FROM ir_codes WHERE protocol = :protocol ORDER BY brand, name")
    fun getByProtocol(protocol: String): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes WHERE category LIKE :category ORDER BY name")
    fun getByCategory(category: String): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes WHERE is_favorite = 1 ORDER BY name")
    fun getFavorites(): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes WHERE is_verified = 1 ORDER BY usage_count DESC")
    fun getVerified(): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes WHERE source = :source ORDER BY name")
    fun getBySource(source: String): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes ORDER BY usage_count DESC LIMIT :limit")
    fun getMostUsed(limit: Int = 50): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes ORDER BY updated_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<IrCodeEntity>>

    @Query("SELECT * FROM ir_codes WHERE address = :address AND command = :command AND protocol = :protocol LIMIT 1")
    suspend fun findByAddressCommand(address: Long, command: Long, protocol: String): IrCodeEntity?

    // ============ STATISTICHE ============

    @Query("SELECT COUNT(*) FROM ir_codes WHERE brand = :brand")
    suspend fun countByBrand(brand: String): Int

    @Query("SELECT COUNT(*) FROM ir_codes WHERE device_type = :deviceType")
    suspend fun countByDeviceType(deviceType: String): Int

    @Query("SELECT COUNT(*) FROM ir_codes WHERE protocol = :protocol")
    suspend fun countByProtocol(protocol: String): Int

    @Query("SELECT COUNT(*) FROM ir_codes WHERE brand = :brand AND device_type = :deviceType")
    suspend fun countByBrandAndDevice(brand: String, deviceType: String): Int

    // ============ GESTIONE CODICI MANCANTI ============

    /** Trova brand che hanno pochi codici per un device type (candidati per learning) */
    @Query("""
        SELECT brand, COUNT(*) as cnt FROM ir_codes 
        WHERE device_type = :deviceType 
        GROUP BY brand 
        HAVING cnt < :minCount
        ORDER BY cnt ASC
    """)
    suspend fun getBrandsWithFewCodes(deviceType: String, minCount: Int = 10): List<BrandCodeCount>

    /** Trova comandi 'mancanti' per un brand/device (es. TV ha power ma non volume) */
    @Query("""
        SELECT * FROM ir_codes 
        WHERE brand = :brand AND device_type = :deviceType
    """)
    suspend fun getCodesForMissingAnalysis(brand: String, deviceType: String): List<IrCodeEntity>

    // ============ BULK OPERATIONS ============

    @Query("UPDATE ir_codes SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE ir_codes SET usage_count = usage_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE ir_codes SET is_verified = 1 WHERE id = :id")
    suspend fun verify(id: Long)
}

/** POJO per conteggi */
data class BrandCodeCount(
    val brand: String,
    val cnt: Int
)
