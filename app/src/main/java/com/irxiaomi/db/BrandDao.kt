package com.irxiaomi.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY code_count DESC")
    fun getAllBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE name LIKE '%' || :query || '%' OR display_name LIKE '%' || :query || '%'")
    fun searchBrands(query: String): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE name = :name")
    suspend fun getBrand(name: String): BrandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(brand: BrandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(brands: List<BrandEntity>)

    @Query("UPDATE brands SET code_count = (SELECT COUNT(*) FROM ir_codes WHERE ir_codes.brand = brands.name)")
    suspend fun updateCodeCounts()

    @Query("SELECT COUNT(*) FROM brands")
    suspend fun count(): Int
}
