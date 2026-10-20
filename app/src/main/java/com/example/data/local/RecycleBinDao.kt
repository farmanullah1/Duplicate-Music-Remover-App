package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM recycle_bin ORDER BY deletedTimestampMs DESC")
    fun getAllTrashedItems(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin WHERE id = :id")
    suspend fun getById(id: String): RecycleBinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearAll()
}
